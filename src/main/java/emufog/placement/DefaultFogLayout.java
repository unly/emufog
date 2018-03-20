package emufog.placement;

import com.google.common.graph.MutableNetwork;
import emufog.nodeconfig.FogNodeType;
import emufog.settings.Settings;
import emufog.topology.*;
import emufog.util.Logger;
import emufog.util.LoggerLevel;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkNotNull;
import static emufog.settings.Settings.getSettings;
import static emufog.topology.Topology.getTopology;
import static emufog.topology.Types.RouterType.EDGE_ROUTER;

public class DefaultFogLayout implements IFogLayout {

    Logger logger = Logger.getInstance();

    private List<Router> edgeRouters = new ArrayList<>();

    private List<FogNodeType> fogNodeTypes = new ArrayList<>();

    private AtomicInteger remainingNodes = new AtomicInteger(getSettings().getMaxFogNodes());

    private float threshold = getSettings().getCostThreshold();

    Map<FogNodeType, List<FogNode>> fogPlacements;

    @Override
    public void identifyFogNodes(MutableNetwork topology) throws Exception {

        // get fog types from settings
        fogNodeTypes = (getSettings().getFogNodeTypes());

        //get edgeRouters from stream of nodes
        topology.nodes()
                .stream()
                .filter(n -> n instanceof Router && ((Router) n).getType().equals(EDGE_ROUTER))
                .forEach(n -> edgeRouters.add((Router) n));

        logger.log("# Edge Routers: " + edgeRouters.size(), LoggerLevel.ADVANCED);
        logger.log("# Edge Routers with connected devices: " +
                edgeRouters.stream()
                        .filter(node -> node.hasDevices())
                        .count(), LoggerLevel.ADVANCED);
        logger.log("# Edge devices: " +
                topology.nodes()
                        .stream()
                        .filter(node -> node instanceof Device)
                        .count(), LoggerLevel.ADVANCED);

        FogResult fogResult = determinePossibleFogNodes();
        if (fogResult.success) {
            for (Map.Entry<Node, FogNode> nodeMapping : fogResult.nodeMap.entrySet()) {
                placeFogNode(nodeMapping.getKey(), nodeMapping.getValue());
            }
        }
    }

    private FogResult determinePossibleFogNodes() {

        FogResult fogResult = new FogResult();
        for (Router node : edgeRouters) {
            long start = System.nanoTime();

            FogNode fogNode = new FogNode();
            PriorityQueue<Node> nodePriorityQueue = new PriorityQueue<>();

            nodePriorityQueue.add(node);

        }


        return fogResult;
    }

    private void placeFogNode(Node node, FogNode fogNode) {
        getTopology().addNode(fogNode);
        Link link = new Link(fogNode.getFogNodeType().getNodeLatency(), fogNode.getFogNodeType().getNodeBandwidth());
        getTopology().addEdge(node, fogNode, link);
    }

    static class FogResult {

        private boolean success;

        final Map<Node, FogNode> nodeMap = new HashMap<>();

        void clearFogNodes() {
            nodeMap.clear();
        }

        void success(boolean value) {
            this.success = value;
        }


    }

    private boolean fogNodesLeft() {
        return remainingNodes.get() > 0;
    }

    void decrementRemainingNodes() {
        remainingNodes.decrementAndGet();
    }

}
