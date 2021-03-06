/*
 * MIT License
 *
 * Copyright (c) 2019 emufog contributors
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
package emufog.fog

import emufog.graph.AS
import emufog.graph.Edge
import emufog.graph.EdgeDeviceNode
import emufog.graph.EdgeNode
import emufog.graph.Node
import emufog.util.ConversionsUtils.formatTimeInterval
import org.slf4j.LoggerFactory
import java.util.PriorityQueue

/**
 * This class isolates the fog node placement algorithm of one of the autonomous systems to run it independent of
 * others. Requires the autonomous system to process.
 */
internal class FogWorker(
    /**
     * the autonomous system this worker processes
     */
    private val system: AS,
    /**
     * the fog node classifier this worker is associated
     */
    private val classifier: FogNodeClassifier
) {

    companion object {

        private val LOG = LoggerFactory.getLogger(FogWorker::class.java)

        /**
         * Calculates the costs for a given edge of the graph.
         *
         * @param edge edge to calculate the costs for
         * @return costs of the given edge
         */
        private fun calculateCosts(edge: Edge): Float {
            // currently using delay as a cost function
            return edge.latency
        }
    }

    /**
     * mapping of nodes from the underlying graph to their respective base nodes
     */
    private val nodes: MutableMap<Node, BaseNode> = HashMap()

    /**
     * Runs the fog node placement algorithm on the associated autonomous system and returns the overall result object.
     *
     * @return result of the fog node placement
     */
    internal fun findFogNodes(): FogResult {
        // map edge device nodes to their respective wrappers
        val startingNodes = system.edgeNodes
            .filter { it.hasDevices() }
            .map { createStartingNode(it) }
            .toMutableList()

        var start = System.nanoTime()
        // calculate connection costs from the edge device nodes
        startingNodes.forEach { calculateConnectionCosts(it) }
        LOG.debug(
            "Time to calculate connection costs for edge devices for {}: {}",
            system,
            formatTimeInterval(start, System.nanoTime())
        )

        // initialize empty result set
        val result = FogResult()
        result.setSuccess()

        while (startingNodes.isNotEmpty()) {
            // check if there are still fog nodes left to place
            if (!classifier.fogNodesLeft()) {
                result.setFailure()
                return result
            }

            start = System.nanoTime()
            // find the next fog node for the remaining starting nodes
            val fogNode = getNextFogNode(startingNodes)
            LOG.debug("Time to find next fog node for {}: {}", system, formatTimeInterval(start, System.nanoTime()))

            // reduce the remaining fog nodes available
            classifier.reduceRemainingNodes()

            start = System.nanoTime()
            // remove all covered nodes from the graph
            removeAllCoveredNodes(fogNode, startingNodes)
            LOG.debug(
                "Time to remove the covered nodes for {}: {}", system, formatTimeInterval(start, System.nanoTime())
            )
            // add the new fog node to the partial result
            result.addPlacement(FogNodePlacement(fogNode))
        }
        return result
    }

    /**
     * Calculates and returns the next fog node to place based on the given list of starting nodes that needs to be
     * covered.
     *
     * @param startingNodes starting nodes that needs to be covered
     * @return the next fog node to place
     */
    private fun getNextFogNode(startingNodes: List<StartingNode>): BaseNode {
        LOG.debug("Remaining starting nodes to cover for {}: {}", system, startingNodes.size)

        var start = System.nanoTime()
        // find the optimal fog type for the remaining nodes in the graph
        val fogNodes = nodes.values.toList()
        fogNodes.forEach { it.findFogType(classifier.config.fogNodeTypes) }
        LOG.debug("Time to find possible fog types for {}: {}", system, formatTimeInterval(start, System.nanoTime()))

        start = System.nanoTime()
        // sort the possible fog nodes with a FogComparator
        val next = fogNodes.sortedWith(FogComparator()).first()
        LOG.debug(
            "Time to find the fog node placement for {}: {}", system, formatTimeInterval(start, System.nanoTime())
        )

        return next
    }

    /**
     * Removes the not required nodes from the node mapping and the given starting nodes prevents unnecessary
     * iterations.
     *
     * @param fogNode next fog node found
     * @param startingNodes list of starting nodes to update
     */
    private fun removeAllCoveredNodes(fogNode: BaseNode, startingNodes: MutableList<StartingNode>) {
        // get covered nodes by the fog node placement
        val coveredNodes = fogNode.getCoveredStartingNodes()
        coveredNodes.forEach {
            val coveredStartingNode = it.first
            coveredStartingNode.decreaseDeviceCount(it.second)

            // node is fully covered
            if (coveredStartingNode.deviceCount <= 0) {
                coveredStartingNode.notifyPossibleNodes()
                nodes.remove(coveredStartingNode.node)
            }

            coveredStartingNode.reachableNodes
                .filter { n -> !n.hasConnections() }
                .forEach { n -> nodes.remove(n.node) }
        }

        fogNode.startingNodes.forEach { it.removePossibleNode(fogNode) }
        nodes.remove(fogNode.node)

        // remove all covered nodes from the edge nodes set
        startingNodes.removeAll(coveredNodes.map { it.first })
    }

    /**
     * Calculates the connection costs to all nodes that are within the cost threshold defined in the associated config
     * [.config]. To calculate the costs the function uses the dijkstra algorithm starting from the given node.
     *
     * @param startingNode node to calculate the connection costs for
     */
    private fun calculateConnectionCosts(startingNode: StartingNode) {
        // push the starting node as a starting point in the queue
        startingNode.setCosts(startingNode, 0F)
        val queue = PriorityQueue(CostComparator(startingNode))
        queue.add(startingNode)

        // using the dijkstra algorithm to iterate the graph
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            val currentCosts = current.getCosts(startingNode)
            checkNotNull(currentCosts) { "No costs associated with this node in the graph." }

            // check all edges leaving the current node
            current.node.edges
                .filterNot { it.isCrossASEdge() }
                .forEach {
                    val neighbor = it.getDestinationForSource(current.node)

                    // ignore host devices as they are not considered to be possible nodes
                    if (neighbor == null || neighbor is EdgeDeviceNode) {
                        return
                    }

                    // abort on costs above the threshold
                    val nextCosts = currentCosts + calculateCosts(it)
                    if (nextCosts > classifier.config.costThreshold) {
                        return
                    }

                    val neighborNode = getBaseNode(neighbor)
                    val neighborCosts = neighborNode.getCosts(startingNode)
                    if (neighborCosts == null) {
                        // newly discovered node
                        neighborNode.setCosts(startingNode, nextCosts)
                        queue.add(neighborNode)
                    } else if (nextCosts < neighborCosts) {
                        // update an already discovered node
                        neighborNode.setCosts(startingNode, nextCosts)
                    }
                }
        }
    }

    /**
     * Returns the base node based on the given node object. If it not yet mapped in [.nodes] the call will create new
     * instance and return it.
     *
     * @param node node to get the base node for
     * @return base node instance for the given node
     */
    private fun getBaseNode(node: Node): BaseNode = nodes.computeIfAbsent(node) { BaseNode(it) }

    /**
     * Creates and returns a new starting node based on the given edge node. Adds the newly created node to the mapping
     * of nodes [.nodes]
     *
     * @param node edge node to create a starting node for
     * @return the newly created starting node
     */
    private fun createStartingNode(node: EdgeNode): StartingNode {
        val startingNode = StartingNode(node)
        nodes[node] = startingNode
        return startingNode
    }
}
