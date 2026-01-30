# Neo4j Modular Decomposition Plugin

A Neo4j plugin that computes the modular decomposition of graphs using the Corneil-Habib-Paul-Tedder (CHPT) algorithm.

## Overview

Modular decomposition identifies the hierarchical structure of a graph by finding modules (groups of vertices with identical relationships to the rest of the graph). The decomposition tree contains:

- **SERIES**: All children are pairwise adjacent (complete subgraph)
- **PARALLEL**: No children are adjacent (independent set)
- **PRIME**: Cannot be further decomposed
- **NORMAL**: Individual vertices (leaves)

This is a pure Java implementation with O(n + m) time complexity, ported from the SageMath Cython implementation.

## Requirements

- Neo4j 5.24.0
- Java 21
- Gradle 8.7 (included via wrapper)

## Installation

Build the plugin:

```bash
cd neo4j-md-plugin
./gradlew clean build
```

Install to Neo4j:

```bash
cp build/libs/neo4j-md-plugin-0.2.0.jar $NEO4J_HOME/plugins/
neo4j restart
```

Verify installation:

```cypher
CALL dbms.procedures() YIELD name
WHERE name STARTS WITH 'md.'
RETURN name
```

## Usage

### Basic Call

```cypher
CALL md.compute('NodeLabel', 'RELATIONSHIP_TYPE')
YIELD treeJson, nodeCount, edgeCount
RETURN treeJson, nodeCount, edgeCount
```

### Parameters

- `label`: Node label to filter which nodes to include
- `relType`: Relationship type to consider as edges

### Example

```cypher
// Create a complete triangle (K3)
CREATE
  (n0:Test {id: 0}),
  (n1:Test {id: 1}),
  (n2:Test {id: 2}),
  (n0)-[:CONNECTED]->(n1),
  (n1)-[:CONNECTED]->(n2),
  (n2)-[:CONNECTED]->(n0);

// Compute modular decomposition
CALL md.compute('Test', 'CONNECTED')
YIELD treeJson, nodeCount, edgeCount
RETURN treeJson;
```

Output:

```json
{
  "nodeCount": 3,
  "edgeCount": 3,
  "mdTree": {
    "type": "SERIES",
    "children": [
      {"type": "NORMAL", "vertex": 0},
      {"type": "NORMAL", "vertex": 1},
      {"type": "NORMAL", "vertex": 2}
    ]
  }
}
```

## Testing with Docker

```bash
./gradlew clean build
docker-compose up -d
```

Open http://localhost:7474 (credentials: neo4j / testpassword)

Run example queries from `examples/load_simple_graphs.cypher`.

Stop:

```bash
docker-compose down
```

## Project Structure

```
src/main/java/org/example/md/
  NodeType.java                 - Tree node types enum
  NodeLabel.java                - Marking labels enum
  MDNode.java                   - Public tree representation
  Graph.java                    - Graph data structure
  LexBFS.java                   - Lexicographic BFS algorithm
  ModularDecomposition.java     - Core CHPT algorithm
  ModularDecompositionProcedures.java - Neo4j procedure interface
```

## Running Tests

```bash
./gradlew test
```

## Algorithm Reference

"A Simple Linear-Time Modular Decomposition Algorithm"
by Corneil, Habib, Lanlignel, Reed, and Rotics
SIAM Journal on Discrete Mathematics, 2008

## Team

- Ameur Wassim
- Fady Iben Habel
- Youssef Ben Hariz

---

Universite Lyon 1 - Master Informatique
January 2026
