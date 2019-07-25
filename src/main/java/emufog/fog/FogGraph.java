/*
 * MIT License
 *
 * Copyright (c) 2018 emufog contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package emufog.fog;

import emufog.container.FogType;
import emufog.graph.AS;
import emufog.graph.Node;
import emufog.graph.EdgeNode;
import emufog.graph.BackboneNode;
import emufog.util.Tuple;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static emufog.util.ConversionsUtils.intervalToString;

/**
 * The graph represents a sub graph for the fog placement algorithm. It maps the nodes
 * of the underlying graph to the fog nodes.
 */
class FogGraph {

    private static final Logger LOG = LoggerFactory.getLogger(FogGraph.class);

    /* list of possible container types for fog nodes */
    final List<FogType> containerTypes;

    /* list of all edge nodes still to cover */
    private final List<emufog.fog.EdgeNode> edgeNodes;

    /* mapping of nodes of the underlying graph to their respective fog nodes equivalent */
    private final Map<Node, FogNode> nodeMapping;

    /* fog comparator to sort the possible fog nodes optimal */
    private final Comparator<FogNode> comparator;

    /**
     * Creates a new sub graph with the given list of possible container images for fog nodes.
     *
     * @param containerTypes list of container images
     */
    FogGraph(List<FogType> containerTypes) {
        this.containerTypes = containerTypes;
        edgeNodes = new ArrayList<>();
        nodeMapping = new HashMap<>();
        comparator = new FogComparator();
    }

    /**
     * Returns the node of the sub graph for the given node. In case there is none so far
     * a new SwitchNode will be created and returned instead.
     *
     * @param node node of the original graph
     * @return sub graph equivalent
     */
    FogNode getNode(Node node) {
        return nodeMapping.get(node);
    }

    /**
     * Removes the given node from the graph.
     *
     * @param node node to remove
     */
    void removeNode(FogNode node) {
        nodeMapping.remove(node.oldNode);
    }

    /**
     * Initializes the node equivalents of the given AS and adds them to the mapping.
     *
     * @param startNodes list of nodes with their respective edge nodes
     * @param as         AS instance to work on
     */
    void initNodes(List<Tuple<Node, List<emufog.fog.EdgeNode>>> startNodes, AS as) {
        for (EdgeNode r : as.getEdgeNodes()) {
            nodeMapping.put(r, new SwitchNode(this, r));
        }

        for (BackboneNode s : as.getBackboneNodes()) {
            nodeMapping.put(s, new SwitchNode(this, s));
        }

        for (Tuple<Node, List<emufog.fog.EdgeNode>> t : startNodes) {
            emufog.fog.EdgeNode edgeNode;
            if (t.getKey() instanceof EdgeNode && t.getValue() == null) {
                edgeNode = new emufog.fog.EdgeNode(this, (emufog.graph.EdgeNode) t.getKey());
            } else {
                edgeNode = new emufog.fog.EdgeNode(this, t.getKey(), t.getValue());
            }
            nodeMapping.put(t.getKey(), edgeNode);
            edgeNodes.add(edgeNode);
        }
    }

    /**
     * Removes all unused nodes from the graph.
     */
    void trimNodes() {
        nodeMapping.values().stream().filter(n -> n.connectedNodes.isEmpty()).forEach(n -> nodeMapping.remove(n.oldNode));
    }

    /**
     * Indicates if there are edge nodes to cover in the fog graph left.
     *
     * @return true if there are still nodes, false otherwise
     */
    boolean hasEdgeNodes() {
        return !edgeNodes.isEmpty();
    }

    /**
     * Returns the nextLevels node of the fog placement algorithm.
     * Possible nodes get sorted with the FogComparator and the graph updated according to the node picked.
     *
     * @return nextLevels node picked
     */
    FogNode getNext() {
        LOG.info("Remaining Edge Nodes to cover: " + edgeNodes.size());
        long start = System.nanoTime();
        List<FogNode> fogNodes = new ArrayList<>(nodeMapping.values());
        assert !fogNodes.isEmpty() : "there are no more possible fog nodes available";

        for (FogNode n : fogNodes) {
            n.findFogType();
        }
        long end = System.nanoTime();
        LOG.info("Find Types Time: " + intervalToString(start, end));

        //TODO debug
        for (emufog.fog.EdgeNode edgeNode : edgeNodes) {
            assert edgeNode != null : "edge node in the list is null";
            assert edgeNode.isMappedToItself() : "edge node is not mapped to itself";
        }

        start = System.nanoTime();
        // sort the possible fog nodes with a FogComparator
        fogNodes.sort(comparator);
        end = System.nanoTime();
        LOG.info("Sort Time: " + intervalToString(start, end));

        // retrieve the nextLevels optimal node
        FogNode next = fogNodes.get(0);

        // get covered nodes by the fog node placement
        Collection<emufog.fog.EdgeNode> coveredNodes = next.getCoveredEdgeNodes();

        //TODO debug
        if (next instanceof emufog.fog.EdgeNode && edgeNodes.contains(next)) {
            assert coveredNodes.contains(next) : "covered nodes set doesn't contain nextLevels node";
        }

        start = System.nanoTime();
        // remove the nextLevels node from the mapping
        nodeMapping.remove(next.oldNode);

        // update all edge nodes connected to nextLevels
        for (emufog.fog.EdgeNode edgeNode : next.getConnectedEdgeNodes()) {
            edgeNode.removePossibleNode(next);
        }
        next.clearAllEdgeNodes();

        // update all fog nodes connected to the selected node set
        for (emufog.fog.EdgeNode coveredNode : coveredNodes) {
            coveredNode.notifyPossibleNodes();
            coveredNode.clearPossibleNodes();
            nodeMapping.remove(coveredNode.oldNode);
        }

        // remove all covered nodes from the edge nodes set
        edgeNodes.removeAll(coveredNodes);
        end = System.nanoTime();
        LOG.info("remove nodes Time: " + intervalToString(start, end));

        assert edgeNodes.size() <= nodeMapping.size() : "weniger edge nodes als gesamt";

        end = System.nanoTime();
        LOG.info("GetNext() Time: " + intervalToString(start, end));

        return next;
    }
}
