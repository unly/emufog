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
package emufog.backbone;

import emufog.graph.AS;
import emufog.graph.Edge;
import emufog.graph.Node;
import emufog.graph.Router;
import emufog.graph.Switch;
import emufog.graph.SwitchConverter;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static emufog.util.ConversionsUtils.intervalToString;

/**
 * This worker class operates on a single AS of the graph so it can used in parallel.
 * Executes the 2nd and 3rd step of the classification algorithm.
 */
class BackboneWorker implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(BackboneWorker.class);

    /* percentage of the average degree to compare to */
    private static final float BACKBONE_DEGREE_PERCENTAGE = 0.6f;

    /* AS associated with this worker */
    private final AS as;

    /* converter to mark backbone nodes */
    private final SwitchConverter converter;

    /**
     * Creates a new worker instance to compute the backbone classification algorithms on a given AS.
     * Only converts the given as from the graph instance.
     *
     * @param as AS to operate on
     */
    BackboneWorker(AS as) {
        this.as = as;
        converter = new SwitchConverter();
    }

    @Override
    public void run() {
        //2nd step
        long start = System.nanoTime();
        convertHighDegrees();
        long end = System.nanoTime();
        LOG.info("{} Step 2 - Time: {}", as, intervalToString(start, end));
        LOG.info("{} Backbone Size: {}", as, as.getSwitches().size());
        LOG.info("{} Edge Size: {}", as, as.getRouters().size());

        // 3rd step
        start = System.nanoTime();
        buildSingleBackbone();
        end = System.nanoTime();
        LOG.info("{} Step 3 - Time: {}", as, intervalToString(start, end));
        LOG.info("{} Backbone Size: {}", as, as.getSwitches().size());
        LOG.info("{} Edge Size: {}", as, as.getRouters().size());
    }

    /**
     * Converts nodes with an above average degree to a backbone node.
     */
    private void convertHighDegrees() {
        final double averageDegree = calculateAverageDegree() * BACKBONE_DEGREE_PERCENTAGE;
        List<Router> toConvert = as.getRouters().parallelStream().filter(r -> r.getDegree() >= averageDegree).collect(Collectors.toList());

        for (Router r : toConvert) {
            converter.convert(r);
        }
    }

    /**
     * Creates a single connected backbone by using the Breadth-First-Algorithm.
     */
    private void buildSingleBackbone() {
        Collection<Switch> switches = as.getSwitches();
        if (switches.isEmpty()) {
            return;
        }

        // bit sets to check for visited nodes and nodes in the queue
        BitSet visited = new BitSet();
        BitSet seen = new BitSet();
        Queue<Node> queue = new ArrayDeque<>();
        // map nodes to their respective predecessors
        Map<Node, Node> predecessors = new HashMap<>();

        // start with any backbone node
        Node node = switches.iterator().next();
        predecessors.put(node, null);
        queue.add(node);

        while (!queue.isEmpty()) {
            node = queue.poll();
            if (visited.get(node.getID())) {
                continue;
            }
            visited.set(node.getID());

            // follow a trace via the predecessor to convert all on this way
            if (node instanceof Switch && predecessors.get(node) instanceof Router) {
                Node predecessor = predecessors.get(node);
                while (predecessor instanceof Router) {
                    converter.convert(predecessor);

                    predecessor = predecessors.get(predecessor);
                }
            }

            // add or update neighborhood
            for (Edge e : node.getEdges()) {
                if (e.isCrossASEdge()) {
                    continue;
                }

                Node neighbor = e.getDestinationForSource(node);
                // avoid visiting twice
                if (visited.get(neighbor.getID())) {
                    continue;
                }

                if (seen.get(neighbor.getID())) {
                    // update the predecessor if necessary
                    if (node instanceof Switch && predecessors.get(neighbor) instanceof Router) {
                        predecessors.put(neighbor, node);
                    }
                } else {
                    // push a new node to the queue
                    predecessors.put(neighbor, node);
                    queue.add(neighbor);
                    seen.set(neighbor.getID());
                }
            }
        }
    }

    /**
     * Returns the average degree of the autonomous system based on the router and switch nodes.
     *
     * @return the average degree
     */
    private double calculateAverageDegree() {
        long sum = 0;
        Collection<Router> routers = as.getRouters();
        Collection<Switch> switches = as.getSwitches();

        for (Node n : routers) {
            sum += n.getDegree();
        }
        for (Node n : switches) {
            sum += n.getDegree();
        }
        int n = switches.size() + routers.size();

        if (n == 0) {
            return 0.f;
        }

        return (double) sum / n;
    }
}