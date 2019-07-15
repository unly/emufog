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
import emufog.graph.Graph;
import emufog.graph.SwitchConverter;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static emufog.util.ConversionsUtils.intervalToString;

/**
 * This class runs the backbone classification algorithm on a graph instance.
 */
public class BackboneClassifier {

    private static final Logger LOG = LoggerFactory.getLogger(BackboneClassifier.class);

    /**
     * Starts the backbone classification algorithm on the given graph.
     * Modifies the graph including backbone and edge of the network.
     *
     * @throws IllegalArgumentException thrown if graph is {@code null}
     */
    public static void identifyBackbone(final Graph graph) throws IllegalArgumentException {
        if (graph == null) {
            throw new IllegalArgumentException("The graph object is not initialized.");
        }

        // 1st step sequentially
        LOG.debug("Start Backbone Classification");
        long start = System.nanoTime();
        markASEdgeNodes(graph);
        long stop = System.nanoTime();
        if (graph.getSettings().timeMeasuring) {
            LOG.info("Graph Step 1 - Time: {}", intervalToString(start, stop));
        }
        LOG.debug("Backbone Size: {}", graph.getSwitches().size());
        LOG.debug("Edge Size: {}", graph.getRouters().size());

        Collection<AS> systems = graph.getSystems();

        // 2nd step in parallel
        start = System.nanoTime();
        systems.parallelStream().forEach(as -> {
            BackboneWorker worker = new BackboneWorker(as);
            worker.run();
        });
        stop = System.nanoTime();
        if (graph.getSettings().timeMeasuring) {
            LOG.info("Graph Step 2 - Time: {}", intervalToString(start, stop));
        }
        LOG.debug("Backbone Size: {}", graph.getSwitches().size());
        LOG.debug("Edge Size: {}", graph.getRouters().size());

        LOG.info("Finished Backbone Classification.");
    }

    /**
     * This methods marks all cross-AS edge's endpoints as backbone nodes.
     */
    private static void markASEdgeNodes(Graph graph) {
        SwitchConverter converter = new SwitchConverter();

        for (Edge e : graph.getEdges()) {
            if (e.isCrossASEdge()) {
                converter.convert(e.getSource());
                converter.convert(e.getDestination());
            }
        }
    }
}