import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.util.*;

/**
 * http://www.timl.id.au/SCC/
 * https://doi.org/10.1016/j.ipl.2015.08.010
 * https://github.com/DavePearce/StronglyConnectedComponents/blob/master/src/PeaFindScc2.java
 * https://github.com/gonum/gonum/blob/master/graph/topo/tarjan.go
 * http://homepages.ecs.vuw.ac.nz/~djp/files/IPL15-preprint.pdf
 * https://habr.com/ru/post/451208/
 */
public final class StableTopoSort {

    /**
     * Performs stable "topological" sort of directed graphs (including graphs with cycles).
     * Possible optimization: instead of counting sort just put vertices on rindex[v] positions if there were no SCCs detected.
     */
    static void stableTopoSort(Node[] nodes) {
        // 0. Remember where each node was
        for (int i = 0; i < nodes.length; ++i) {
            nodes[i].index = i;
        }

        // 1. Sort edges according to node indices
        for (int i = 0; i < nodes.length; ++i) {
            nodes[i].edges.sort(Comparator.comparingInt(o -> o.index));
        }

        // 2. Perform Tarjan SCC
        final PeaSCC scc = new PeaSCC(nodes);
        scc.visit();

        // 3. Perform *reverse* counting sort
        reverseCountingSort(nodes, scc.rindex);
    }

    static final class PeaSCC {
        final Node[] graph;
        final int[] rindex;
        DoubleStack vS;
        DoubleStack iS;
        boolean[] root;
        int index;
        int c;

        PeaSCC(Node[] g) {
            this.graph = g;
            this.rindex = new int[g.length];
            this.index = 1;
            this.c = g.length - 1;

            vS = new DoubleStack(graph.length);
            iS = new DoubleStack(graph.length);
            root = new boolean[graph.length];
        }

        void visit() {
            // Attn! We're walking nodes in reverse
            for (int i = graph.length - 1; i >= 0; --i) {
                if (rindex[i] == 0) {
                    visit(i);
                }
            }
        }

        void visit(int v) {
            beginVisiting(v);

            while (!vS.isEmptyFront()) {
                visitLoop();
            }
        }

        void visitLoop() {
            int v = vS.topFront();
            int i = iS.topFront();

            int numEdges = graph[v].edges.size();

            // Continue traversing out-edges until none left.
            while (i <= numEdges) {
                // Continuation
                if (i > 0) {
                    // Update status for previously traversed out-edge
                    finishEdge(v, i - 1);
                }
                if (i < numEdges && beginEdge(v, i)) {
                    return;
                }
                i = i + 1;
            }

            // Finished traversing out edges, update component info
            finishVisiting(v);
        }

        void beginVisiting(int v) {
            // First time this node encountered
            vS.pushFront(v);
            iS.pushFront(0);
            root[v] = true;
            rindex[v] = index;
            ++index;
        }

        void finishVisiting(int v) {
            // Take this vertex off the call stack
            vS.popFront();
            iS.popFront();
            // Update component information
            if (root[v]) {
                --index;
                while (!vS.isEmptyBack() && rindex[v] <= rindex[vS.topBack()]) {
                    int w = vS.popBack();
                    rindex[w] = c;
                    --index;
                }
                rindex[v] = c;
                --c;
            } else {
                vS.pushBack(v);
            }
        }

        boolean beginEdge(int v, int k) {
            int w = graph[v].edges.get(k).index;

            if (rindex[w] == 0) {
                iS.popFront();
                iS.pushFront(k + 1);
                beginVisiting(w);
                return true;
            } else {
                return false;
            }
        }

        void finishEdge(int v, int k) {
            int w = graph[v].edges.get(k).index;

            if (rindex[w] < rindex[v]) {
                rindex[v] = rindex[w];
                root[v] = false;
            }
        }
    }

    static void reverseCountingSort(Node[] nodes, int[] rindex) {
        final int[] count = new int[nodes.length];

        for (int i = 0; i < rindex.length; ++i) {
            final int cindex = nodes.length - 1 - rindex[i];
            ++count[cindex];
        }

        for (int i = 1; i < count.length; ++i) {
            count[i] += count[i - 1];
        }

        final Node[] output = new Node[nodes.length];
        for (int i = 0; i < output.length; ++i) {
            final int cindex = nodes.length - 1 - rindex[i];

            // Attn! We're sorting in reverse
            final int outputIndex = output.length - count[cindex];

            output[outputIndex] = nodes[i];
            --count[cindex];
        }

        System.arraycopy(output, 0, nodes, 0, nodes.length);
    }

    public static class DoubleStack {
        final int[] items;
        int fp; // front pointer
        int bp; // back pointer

        DoubleStack(int capacity) {
            this.fp = 0;
            this.bp = capacity;
            this.items = new int[capacity];
        }

        boolean isEmptyFront() {
            return fp == 0;
        }

        int topFront() {
            return items[fp - 1];
        }

        int popFront() {
            return items[--fp];
        }

        void pushFront(int item) {
            items[fp++] = item;
        }

        boolean isEmptyBack() {
            return bp == items.length;
        }

        int topBack() {
            return items[bp];
        }

        int popBack() {
            return items[bp++];
        }

        void pushBack(int item) {
            items[--bp] = item;
        }
    }

    static final Node[] emptyNodes = {};

    @Test
    public void testLoop3() {
        node("D");
        node("E");
        node("C");
        node("B");
        node("A");

        edge("A", "B");
        edge("B", "C");
        edge("C", "A");

        assertSort("D", "E", "C", "B", "A");
    }

    @Test
    public void testReverseLoop() {
        node("B");
        node("A");

        edge("A", "B");
        edge("B", "A");

        assertSort("B", "A");
    }

    List<Node> graph = new ArrayList<>();
    Map<String, Node> nodes = new HashMap<>();

    @AfterMethod
    void afterMethod() {
        nodes.clear();
        graph.clear();
    }

    void node(String name) {
        Node node = new Node(name);
        Assert.assertNull(nodes.put(name, node));
        graph.add(node);
    }

    void edge(String from, String... tos) {
        Node fromNode = nodes.get(from);
        Assert.assertNotNull(fromNode);

        for (String to : tos) {
            Node toNode = nodes.get(to);
            Assert.assertNotNull(toNode);
            fromNode.addEdgeTo(toNode);
        }
    }

    @Test
    public void testSingle() {
        node("A");
        assertSort("A");
    }

    @Test
    public void testEmpty() {
        assertSort();
    }

    @Test
    public void testUnconnected() {
        node("A");
        node("B");
        node("C");
        assertSort("A", "B", "C");
    }

    @Test
    public void testTrivial() {
        node("A");
        node("B");

        edge("A", "B");

        assertSort("A", "B");
    }

    void assertSort(String... expected) {
        Node[] graphArr = graph.toArray(emptyNodes);
        doAssertSort(graphArr, expected);
        // Since our algorithm is stable, subsequent sorts must not change order
        doAssertSort(graphArr, expected);
    }

    private void doAssertSort(Node[] graphArr, String[] expected) {
        stableTopoSort(graphArr);

        Assert.assertEquals(graphArr.length, expected.length);

        final String[] actualNames = Arrays.stream(graphArr).map(node -> node.name).toArray(String[]::new);
        Assert.assertEquals(Arrays.toString(actualNames), Arrays.toString(expected));
    }

    @Test
    public void testReverse() {
        node("A");
        node("B");

        edge("B", "A");

        assertSort("B", "A");
    }

    @Test
    public void testTransitive() {
        node("A");
        node("B");
        node("C");

        edge("B", "A");
        edge("C", "B");

        assertSort("C", "B", "A");
    }

    /**
     * https://sourceware.org/bugzilla/show_bug.cgi?id=17645#c8
     */
    @Test
    public void testInitorder() {
        node("main");
        node("a2");
        node("a1");
        node("b2");
        node("b1");
        node("a3");
        node("a4");

        edge("a2", "a1");
        edge("b2", "b1", "a2");
        edge("a3", "b2", "b1");
        edge("a4", "a3");
        edge("main", "a4", "a1", "b2");

        assertSort("main", "a4", "a3", "b2", "b1", "a2", "a1");
    }

    static final class Node {
        String name;
        List<Node> edges = new ArrayList<>();
        Set<Node> uniqueEdges = new HashSet<>();

        int index;

        Node(String name) {
            this.name = name;
        }

        void addEdgeTo(Node node) {
            Assert.assertTrue(uniqueEdges.add(node));
            edges.add(node);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
