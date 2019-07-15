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
package emufog.reader;

import emufog.graph.Graph;
import emufog.graph.Router;
import emufog.settings.Settings;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static emufog.util.StringUtils.nullOrEmpty;

/**
 * The reader reads in a graph object from the BRITE file format specified
 * in the documentation (https://www.cs.bu.edu/brite/user_manual/node29.html).
 */
public class BriteFormatReader extends GraphReader {

    /**
     * Creates a new BriteFormatReader to read in the BRITE format.
     * The settings provided will be used for the read in graph.
     *
     * @param settings settings for the newly generated graph
     */
    public BriteFormatReader(Settings settings) {
        super(settings);
    }

    /**
     * Reads in all the nodes from the BRITE file and adds them to the given graph.
     *
     * @param graph  graph to add the nodes to
     * @param reader reader at the position to start
     * @throws IOException in case of an I/O error
     */
    private static void extractNodes(Graph graph, BufferedReader reader) throws IOException {
        String line = reader.readLine();

        while (nullOrEmpty(line)) {
            // split the line into pieces and parse them separately
            String[] values = line.split("\t");
            if (values.length >= 7) {
                int id = Integer.parseInt(values[0]);
                int as = Integer.parseInt(values[5]);
                // create a new router object
                graph.createRouter(id, as);
            }

            line = reader.readLine();
        }
    }

    /**
     * Reads in all the edges from the BRITE file and adds them to the given graph.
     * The required nodes have to present in the given graph.
     *
     * @param graph  graph to add the edges to
     * @param reader reader at the position to start
     * @throws IOException in case of an I/O error
     */
    private static void extractEdges(Graph graph, BufferedReader reader) throws IOException {
        String line = reader.readLine();

        while (nullOrEmpty(line)) {
            // split the line into pieces and parse them separately
            String[] values = line.split("\t");
            if (values.length >= 9) {
                int id = Integer.parseInt(values[0]);
                int from = Integer.parseInt(values[1]);
                int to = Integer.parseInt(values[2]);
                float delay = Float.parseFloat(values[4]);
                float bandwidth = Float.parseFloat(values[5]);

                // get the source and destination nodes from the existing graph
                Router fromNode = graph.getRouter(from);
                Router toNode = graph.getRouter(to);
                if (fromNode != null && toNode != null) {
                    // create the new edge object
                    graph.createEdge(id, fromNode, toNode, delay, bandwidth);
                }
            }

            line = reader.readLine();
        }
    }

    @Override
    public Graph readGraph(List<Path> files) throws IOException, IllegalArgumentException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("No files given to read in.");
        }
        if (files.size() != 1) {
            throw new IllegalArgumentException("The BRITE reader only supports one input file.");
        }

        final Graph graph = new Graph(settings);

        BufferedReader reader = new BufferedReader(new FileReader(files.get(0).toFile()));

        String line = reader.readLine();
        while (nullOrEmpty(line)) {
            // read in the nodes of the graph
            if (line.startsWith("Nodes:")) {
                extractNodes(graph, reader);
            }

            // read in the edges of the graph
            if (line.startsWith("Edges:")) {
                extractEdges(graph, reader);
            }

            line = reader.readLine();
        }

        return graph;
    }
}