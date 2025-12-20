package org.example.md;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ModularDecompositionProcedures {

    @Context
    public Transaction tx;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static class MdResult {
        public String treeJson;
        public long nodeCount;
        public long edgeCount;

        public MdResult(String treeJson, long nodeCount, long edgeCount) {
            this.treeJson = treeJson;
            this.nodeCount = nodeCount;
            this.edgeCount = edgeCount;
        }
    }

    @Procedure(name = "md.compute", mode = Mode.READ)
    @Description("CALL md.compute(label, relType) YIELD treeJson, nodeCount, edgeCount")
    public Stream<MdResult> compute(
            @Name("label") String label,
            @Name("relType") String relType
    ) {
        try {
            GraphExtract extract = extractUndirectedSubgraph(label, relType);
            String resultJson = runSageModularDecomposition(extract);
            return Stream.of(new MdResult(resultJson, extract.n, extract.edges.size()));
        } catch (Exception e) {
            throw new RuntimeException("md.compute failed: " + e.getMessage(), e);
        }
    }

    // Graph extraction from Neo4j
    private static final class GraphExtract {
        final int n;
        final List<int[]> edges; // each {u,v} with u<v

        GraphExtract(int n, List<int[]> edges) {
            this.n = n;
            this.edges = edges;
        }
    }

    private GraphExtract extractUndirectedSubgraph(String label, String relType) {
        Label L = Label.label(label);
        RelationshipType R = RelationshipType.withName(relType);

        // 1) Collect nodes with that label
        List<Node> nodes = new ArrayList<>();
        try (ResourceIterator<Node> it = tx.findNodes(L)) {
            while (it.hasNext()) nodes.add(it.next());
        }

        // Map Neo4j node id -> index 0..n-1
        Map<Long, Integer> idToIdx = new HashMap<>(nodes.size() * 2);
        for (int i = 0; i < nodes.size(); i++) {
            idToIdx.put(nodes.get(i).getId(), i);
        }

        // 2) Collect undirected edges among selected nodes
        // Use a long key (minIdx, maxIdx) to deduplicate
        Set<Long> edgeKeys = new HashSet<>();
        for (Node u : nodes) {
            int ui = idToIdx.get(u.getId());
            for (Relationship rel : u.getRelationships(Direction.BOTH, R)) {
                Node v = rel.getOtherNode(u);
                Integer viObj = idToIdx.get(v.getId());
                if (viObj == null) continue;
                int vi = viObj;

                if (ui == vi) continue;
                int a = Math.min(ui, vi);
                int b = Math.max(ui, vi);
                long key = (((long) a) << 32) | (b & 0xffffffffL);
                edgeKeys.add(key);
            }
        }

        List<int[]> edges = new ArrayList<>(edgeKeys.size());
        for (long key : edgeKeys) {
            int a = (int) (key >>> 32);
            int b = (int) (key & 0xffffffffL);
            edges.add(new int[]{a, b});
        }

        return new GraphExtract(nodes.size(), edges);
    }

    // Sage integration 

    private String runSageModularDecomposition(GraphExtract extract) throws Exception {
        // 1) Extract Sage resources to temp dir
        Path workDir = Files.createTempDirectory("neo4j-md-sage-");
        Path sageRepoDir = workDir.resolve("sage-md");
        Files.createDirectories(sageRepoDir);

        // Required repo files (packaged under src/main/resources/sage-md/)
        copyResourceToFile("/sage-md/corneil_habib_paul_tedder_algorithm.spyx",
                sageRepoDir.resolve("corneil_habib_paul_tedder_algorithm.spyx"));
        copyResourceToFile("/sage-md/corneil_habib_paul_tedder_algorithm_utils.sage",
                sageRepoDir.resolve("corneil_habib_paul_tedder_algorithm_utils.sage"));

        // 2) Write input graph JSON
        Map<String, Object> input = new HashMap<>();
        input.put("n", extract.n);
        input.put("edges", extract.edges);

        Path inputJson = workDir.resolve("graph.json");
        Files.writeString(inputJson, MAPPER.writeValueAsString(input), StandardCharsets.UTF_8);

        // 3) Write a runner script executed by "sage -python"
        // It will:
        // - load the .spyx
        // - build a Sage Graph
        // - compute modular decomposition tree
        // - convert it to JSON
        Path runnerPy = workDir.resolve("runner.py");
        Files.writeString(runnerPy, buildRunnerScript(), StandardCharsets.UTF_8);

        // 4) Run Sage
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");

        List<String> cmd = new ArrayList<>();

        if (windows) {
            String condaPrefix = "/home/fady/.local/share/mamba/envs/sage";
            String wslSage = condaPrefix + "/bin/sage";
            
            String command =
                "CONDA_PREFIX=" + condaPrefix + " " +
                "PATH=" + condaPrefix + "/bin:$PATH " +
                "PKG_CONFIG_PATH=" + condaPrefix + "/lib/pkgconfig:/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig " +
                wslSage + " -python " +
                shellQuote(toWslPath(runnerPy)) + " " +
                shellQuote(toWslPath(sageRepoDir)) + " " +
                shellQuote(toWslPath(inputJson));
            
            cmd.add("wsl.exe");
            cmd.add("bash");
            cmd.add("-lc");
            cmd.add(command);

        } else {
            // Neo4j running on Linux
            String sageBin = Optional.ofNullable(System.getenv("SAGE_BIN")).orElse("sage");
            cmd.add(sageBin);
            cmd.add("-python");
            cmd.add(runnerPy.toAbsolutePath().toString());
            cmd.add(sageRepoDir.toAbsolutePath().toString());
            cmd.add(inputJson.toAbsolutePath().toString());
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        
        // Hard timeout to avoid hanging Neo4j
        boolean finished = p.waitFor(90, TimeUnit.SECONDS);
        String output = readAll(p.getInputStream());

        if (!finished) {
            p.destroyForcibly();
            throw new RuntimeException("Sage execution timed out");
        }
        if (p.exitValue() != 0) {
            throw new RuntimeException("Sage failed (exit=" + p.exitValue() + "): " + output);
        }

        // Output is JSON on stdout
        return output.trim();
    }
    private static String toWslPath(java.nio.file.Path p) {
        String s = p.toAbsolutePath().toString();
    
        // If already a WSL path, keep as is
        if (s.startsWith("/")) return s;
    
        // Convert Windows path like C:\Users\X\AppData\Local\Temp\...
        // to /mnt/c/Users/X/AppData/Local/Temp/...
        // Works for standard drive-letter paths.
        if (s.length() >= 2 && s.charAt(1) == ':') {
            char drive = Character.toLowerCase(s.charAt(0));
            String rest = s.substring(2).replace('\\', '/');
            return "/mnt/" + drive + rest;
        }
    
        return s;
    }
    private static String shellQuote(String s) {
        // Safe single-quote for bash
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }
    private static String buildRunnerScript() {
        // We use Sage’s Node / NodeType structure and traverse children recursively.
        // For NORMAL leaves, we try several attribute names for safety.
        return """
import json
import sys

from sage.all import Graph, load

def node_type_name(node):
    nt = getattr(node, "node_type", None)
    if nt is None:
        nt = getattr(node, "type", None)
    s = str(nt)
    # common formats: 'NodeType.SERIES' or 'SERIES'
    if "." in s:
        s = s.split(".")[-1]
    return s

def leaf_label(node):
    for attr in ("vertex", "label", "value", "name"):
        if hasattr(node, attr):
            v = getattr(node, attr)
            if callable(v):
                try:
                    v = v()
                except Exception:
                    continue
            return v
    # fallback
    return str(node)

def node_to_dict(node):
    children = getattr(node, "children", None)
    if children is None:
        children = []
    # if no children -> leaf
    if len(children) == 0:
        return {"type": "NORMAL", "label": leaf_label(node)}

    return {
        "type": node_type_name(node),
        "children": [node_to_dict(c) for c in children]
    }

def main():
    sage_repo_dir = sys.argv[1]
    input_json = sys.argv[2]

    # Make sure the repo dir is on Sage load path
    import os
    os.chdir(sage_repo_dir)

    # Load the implementation from the paper repo
    load("corneil_habib_paul_tedder_algorithm.spyx")

    with open(input_json, "r", encoding="utf-8") as f:
        data = json.load(f)

    n = int(data["n"])
    edges = data["edges"]

    G = Graph(n)
    for e in edges:
        u = int(e[0])
        v = int(e[1])
        if u != v:
            G.add_edge(u, v)

    root = corneil_habib_paul_tedder_algorithm(G)
    out = {
        "n": n,
        "edge_count": len(edges),
        "md_tree": node_to_dict(root)
    }
    print(json.dumps(out, ensure_ascii=False))

if __name__ == "__main__":
    main()
""";
    }

    private static void copyResourceToFile(String resourcePath, Path target) throws IOException {
        try (InputStream is = ModularDecompositionProcedures.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new FileNotFoundException("Missing resource in JAR: " + resourcePath);
            }
            Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readAll(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = is.read(buf)) != -1) {
            baos.write(buf, 0, r);
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
