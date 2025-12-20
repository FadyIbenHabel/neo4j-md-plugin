load("corneil_habib_paul_tedder_algorithm_utils.sage")

################################################################################
# Graph from fig. 3 of [TCHP2008]_
G3 = Graph(['xoabcdefguvwyz', [('b', 'a'), ('e', 'a'), ('a', 'f'), ('f', 'g'),
                              ('w', 'u'), ('v', 'u'), ('u', 'c'), ('u', 'd'),
                              ('d', 'c'), ('c', 'z'), ('d', 'z'), ('c', 'x'),
                              ('d', 'x'), ('c', 'y'), ('d', 'y') ]],
                              pos={'a': [0, 0], 'b': [-1, 0.5], 'c': [1, 4.5],
                                   'd': [1, 3.5], 'e': [-1, -0.5], 'f': [1, 0],
                                   'g': [2, 0], 'u': [0, 4], 'v': [-1, 3.5],
                                   'w': [-1, 4.5], 'x': [2, 4.5], 'y': [2, 3.5],
                                   'z': [2, 4] },
                             format='vertices_and_edges')
for u in 'ebafg':
  for v in 'vwucdxyz':
    G3.add_edge ((u, v))

G3.delete_vertex('o')

################################################################################
test_graphs = [
  G3,
  Graph(['xabcdefg', [('a', 'b'), ('a', 'c'), ('a', 'd'), ('a', 'e'),
                      ('a', 'x'), ('f', 'b'), ('f', 'c'), ('f', 'd'),
                      ('f', 'e'), ('f', 'x'), ('f', 'g'), ('b', 'e'),
                      ('b', 'x'), ('b', 'c'), ('b', 'd'), ('c', 'e'),
                      ('c', 'x'), ('d', 'e'), ('d', 'x'), ]],
                      pos = { 'x': [0, 1], 'a': [-2, 0], 'b': [-1, 0],
                              'c': [0, -1], 'd': [1, -1], 'e': [1, 1],
                              'f': [2, 0], 'g': [4, 0], },
                      format='vertices_and_edges'),
  Graph(['xabcdefghijyz', [ ('x', 'a'), ('x', 'b'), ('x', 'c'), ('x', 'd'),
                            ('x', 'e'), ('x', 'f'), ('x', 'g'), ('x', 'h'),
                            ('x', 'i'), ('x', 'j'), ('y', 'c'), ('y', 'd'),
                            ('y', 'e'), ('y', 'f'), ('y', 'g'), ('y', 'h'),
                            ('y', 'i'), ('y', 'j'), ('z', 'e'), ('z', 'f'),
                            ('a', 'c'), ('a', 'd'), ('a', 'e'), ('a', 'f'),
                            ('a', 'g'), ('a', 'h'), ('a', 'i'), ('a', 'j'),
                            ('b', 'c'), ('b', 'd'), ('b', 'e'), ('b', 'f'),
                            ('b', 'g'), ('b', 'h'), ('b', 'i'), ('b', 'j'),
                            ('c', 'd'), ('c', 'g'), ('c', 'h'), ('c', 'i'),
                            ('c', 'j'), ('d', 'g'), ('d', 'h'), ('d', 'i'),
                            ('d', 'j'), ('e', 'f'), ('e', 'g'), ('e', 'h'),
                            ('e', 'i'), ('e', 'j'), ('f', 'g'), ('f', 'h'),
                            ('f', 'i'), ('f', 'j'), ('g', 'i'), ('g', 'j'),
                            ('h', 'i'), ('h', 'j'), ]],
                        format='vertices_and_edges'),
]

################################################################################
def test_one_graph (G, ref=None, **kwargs):
    try:
        MD = corneil_habib_paul_tedder_algorithm(G, **kwargs)
        if ref is None: # for the case where the MD tree is already known
            ref = habib_maurer_algorithm(G)
        b = equivalent_trees(MD, ref)
        if not b:
            print(f'\n\n# Error with graph {G} of order {G.order()}:')
            print(f'Gdebug = Graph({G.graph6_string()!a}, format=\'graph6\')')
            print('diff:')
            md_tree_diff(ref, MD)
    except Exception as e:
        print(f'\n\n# Error with graph {G}:')
        print(f'G = Graph({G.to_dictionary()}, format=\'dict_of_lists\')')
        print(f'Exception "{e!r}" was raised')
        raise e from None

    return b

################################################################################
def tests (s=None, **kwargs):
    ret = True
    with seed(s):
        print(f'# seed={initial_seed()}')

        # Test on specific graphs that triggered some bugs to check that they
        # are fixed
        for G in test_graphs:
            ret = ret and test_one_graph(G, **kwargs)

        # Test on Turan graphs, which are co-graphs
        for _ in range(10):
            n = 10 + ZZ.random_element(100)
            r = 1 + ZZ.random_element(n)
            G = graphs.TuranGraph(n, r)
            ret = ret and test_one_graph(G, **kwargs)


        ## Test on IntervalGraph
        for _ in range(10):
            G = graphs.RandomIntervalGraph(10 + ZZ.random_element(100))
            ret = ret and test_one_graph(G, **kwargs)

        # Generate random md_tree
        for max_depth, max_fan_out, leaf_prob in ((3,4,0.2), (4,5,0.2),
                                                  (6,5,0.2)):
            for _ in range(5):
                md_tree = random_md_tree(max_depth, max_fan_out, leaf_prob)
                G = md_tree_to_graph(md_tree,
                                     prime_node_generator=random_prime_graph)
                ret = ret and test_one_graph(G, ref=md_tree, **kwargs)

    if not ret:
        print('ERROR, some tests failed')
    return ret

tests()
