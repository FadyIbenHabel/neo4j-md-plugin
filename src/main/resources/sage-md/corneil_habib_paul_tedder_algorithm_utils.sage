load("corneil_habib_paul_tedder_algorithm.spyx")

import random
import time

from sage.graphs.graph_decompositions.modular_decomposition import NodeType,\
                                                        habib_maurer_algorithm,\
                                                        equivalent_trees,\
                                                        get_vertices,\
                                                        print_md_tree,\
                                                        random_md_tree

################################################################################
def random_prime_graph(nverts):
    if nverts < 4:
        raise ValueError("A prime graph must have at least 4 vertices")
    elif nverts == 4:
        return graphs.PathGraph(4)
    elif nverts == 5:
        return random.choice((lambda n: graphs.HouseGraph(),
                              lambda n: graphs.BullGraph(),
                              graphs.PathGraph, graphs.CycleGraph))(5)
    else:
        return random.choice((graphs.PathGraph, graphs.CycleGraph))(nverts)

################################################################################
def md_tree_to_graph(root, prime_node_generator=None):
    r"""
    Copy from sage/graphs/graph_decompositions/modular_decomposition.py.
    Added the prime_node_generator to be able to use any callable that returns
    prime graphs.
    """
    from itertools import product, combinations
    from sage.graphs.graph import Graph

    if prime_node_generator is None:
        prime_node_generator = graphs.PathGraph

    def tree_to_vertices_and_edges(root):
        if root.node_type == NodeType.NORMAL:
            return (root.children, [])
        children_ve = [tree_to_vertices_and_edges(child) for child in root.children]
        vertices = [v for vs, es in children_ve for v in vs]
        edges = [e for vs, es in children_ve for e in es]
        vertex_lists = [vs for vs, es in children_ve]
        if root.node_type == NodeType.PRIME:
            G = random_prime_graph(len(vertex_lists))
            G.relabel(range(len(vertex_lists)))
            for i1, i2 in G.edge_iterator(labels=False):
                for v1, v2 in product(vertex_lists[i1], vertex_lists[i2]):
                    edges.append((v1, v2))
        elif root.node_type == NodeType.SERIES:
            for vs1, vs2 in combinations(vertex_lists, 2):
                for v1, v2 in product(vs1, vs2):
                    edges.append((v1, v2))
        return (vertices, edges)

    vs, es = tree_to_vertices_and_edges(root)
    return Graph([vs, es], format='vertices_and_edges')

################################################################################
def md_tree_diff (r1, r2):
    if r1.node_type != r2.node_type:
        print(f'different type: {r1.node_type} / {r2.node_type}')
        print(' ', r1)
        print(' ', r2)
        return

    if len(r1.children) != len(r2.children):
        print(f'different #child: {len(r1.children)} != {len(r2.children)}')
        print('#', set(get_vertices(r1)))
        print_md_tree (r1)
        print('#', set(get_vertices(r2)))
        print_md_tree (r2)
        return

    if r1.node_type == NodeType.NORMAL:
        if r1.children[0] != r2.children[0]:
            print(f'different leaf: {r1.children[0]} != {r2.children[0]}')
        return

    node_id = lambda r: (r.node_type, frozenset(get_vertices(r)))

    child_map = {}
    for node in r1.children:
        child_map[node_id(node)] = node

    for node in r2.children:
        key = node_id(node)
        if key not in child_map:
            print('not found', key)
            print(' ', r1)
            print(' ', r2)
            return
        md_tree_diff (child_map[key], node)

################################################################################
def bench_one_graph (G, niter_max=10000, step=10, timeout_sec=5,
                        result_line=True):
    timeout_ns = ZZ(timeout_sec*1000000000)
    ms_in_ns = 1000000

    i, told = 0, 0
    while told < timeout_ns and i < niter_max:
        try:
            alarm(timeout_sec)
            t = time.perf_counter_ns()
            for _ in range(step):
                habib_maurer_algorithm(G)
            told += time.perf_counter_ns()-t
            i += step
        except AlarmInterrupt:
            told = RR(NaN)
            i = niter_max
        except KeyboardInterrupt:
            cancel_alarm()
            raise
        else:
            cancel_alarm()
    told = float(told/i/ms_in_ns)

    i, tnew = 0, 0
    while tnew < timeout_ns and i < niter_max:
        try:
            alarm(timeout_sec)
            t = time.perf_counter_ns()
            for _ in range(step):
                corneil_habib_paul_tedder_algorithm(G)
            tnew += time.perf_counter_ns()-t
            i += step
        except AlarmInterrupt:
            tnew = RR(NaN)
            i = niter_max
        except KeyboardInterrupt:
            cancel_alarm()
            raise
        else:
            cancel_alarm()
    tnew = float(tnew/i/ms_in_ns)

    if result_line:
        print(' #V   |   #E    | Sage current |   CHPT08    |  ratio')
        print (f"{G.order():5d} | {G.size():7d} | {told:9.3f} ms | "
                f"{tnew:8.3f} ms | {float(told/tnew):7.2f}")
    return told, tnew
