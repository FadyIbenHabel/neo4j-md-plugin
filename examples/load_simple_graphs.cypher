// Example graphs for testing modular decomposition

// Complete Graph K4 - Expected: SERIES with 4 children
CREATE
  (n0:K4 {id: 0}), (n1:K4 {id: 1}),
  (n2:K4 {id: 2}), (n3:K4 {id: 3}),
  (n0)-[:E]->(n1), (n0)-[:E]->(n2), (n0)-[:E]->(n3),
  (n1)-[:E]->(n2), (n1)-[:E]->(n3), (n2)-[:E]->(n3);

CALL md.compute('K4', 'E') YIELD treeJson RETURN treeJson;
MATCH (n:K4) DETACH DELETE n;


// Independent Set - Expected: PARALLEL with 4 children
CREATE
  (n0:IndSet {id: 0}), (n1:IndSet {id: 1}),
  (n2:IndSet {id: 2}), (n3:IndSet {id: 3});

CALL md.compute('IndSet', 'E') YIELD treeJson RETURN treeJson;
MATCH (n:IndSet) DETACH DELETE n;


// Path P4 - Expected: PRIME (cannot be decomposed)
CREATE
  (n0:Path {id: 0}), (n1:Path {id: 1}),
  (n2:Path {id: 2}), (n3:Path {id: 3}),
  (n0)-[:E]->(n1), (n1)-[:E]->(n2), (n2)-[:E]->(n3);

CALL md.compute('Path', 'E') YIELD treeJson RETURN treeJson;
MATCH (n:Path) DETACH DELETE n;


// Star Graph - One center connected to 4 leaves
CREATE
  (c:Star {id: 0}),
  (n1:Star {id: 1}), (n2:Star {id: 2}),
  (n3:Star {id: 3}), (n4:Star {id: 4}),
  (c)-[:E]->(n1), (c)-[:E]->(n2),
  (c)-[:E]->(n3), (c)-[:E]->(n4);

CALL md.compute('Star', 'E') YIELD treeJson RETURN treeJson;
MATCH (n:Star) DETACH DELETE n;


// Two Triangles Connected - Hierarchical structure
CREATE
  (a:Mod {id: 0}), (b:Mod {id: 1}), (c:Mod {id: 2}),
  (d:Mod {id: 3}), (e:Mod {id: 4}), (f:Mod {id: 5}),
  (a)-[:E]->(b), (b)-[:E]->(c), (c)-[:E]->(a),
  (d)-[:E]->(e), (e)-[:E]->(f), (f)-[:E]->(d),
  (c)-[:E]->(d);

CALL md.compute('Mod', 'E') YIELD treeJson RETURN treeJson;
MATCH (n:Mod) DETACH DELETE n;


// Petersen Graph
CREATE
  (n0:Pet {id: 0}), (n1:Pet {id: 1}), (n2:Pet {id: 2}),
  (n3:Pet {id: 3}), (n4:Pet {id: 4}), (n5:Pet {id: 5}),
  (n6:Pet {id: 6}), (n7:Pet {id: 7}), (n8:Pet {id: 8}),
  (n9:Pet {id: 9}),
  (n0)-[:E]->(n1), (n1)-[:E]->(n2), (n2)-[:E]->(n3),
  (n3)-[:E]->(n4), (n4)-[:E]->(n0),
  (n5)-[:E]->(n7), (n7)-[:E]->(n9), (n9)-[:E]->(n6),
  (n6)-[:E]->(n8), (n8)-[:E]->(n5),
  (n0)-[:E]->(n5), (n1)-[:E]->(n6), (n2)-[:E]->(n7),
  (n3)-[:E]->(n8), (n4)-[:E]->(n9);

CALL md.compute('Pet', 'E') YIELD treeJson RETURN treeJson;
MATCH (n:Pet) DETACH DELETE n;
