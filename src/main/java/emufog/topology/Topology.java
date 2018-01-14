package emufog.topology;

import com.google.common.graph.MutableNetwork;

import com.google.common.graph.NetworkBuilder;
import emufog.export.ITopologyExporter;
import emufog.export.MaxinetExporter;
import emufog.placement.*;
import emufog.reader.*;
import emufog.settings.Settings;
import emufog.util.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class Topology {

    private static MutableNetwork<Node, Link> INSTANCE;

    private Settings settings;

    private Map<Integer, AS> systems;

    public static MutableNetwork<Node, Link> getTopology(){
        if (INSTANCE == null) {
            INSTANCE = NetworkBuilder.undirected().allowsParallelEdges(true).build();
        }
        return INSTANCE;
    }

    private Topology(TopologyBuilder builder) throws IOException {

        Logger logger = Logger.getInstance();

        this.settings = builder.settings;
        try {
            long start = System.nanoTime();
            read();
            long end = System.nanoTime();
            logger.log("It took " + Logger.convertToMs(start,end) + "ms to read the topology");
            logger.log("Number of nodes: " + getTopology().nodes().size());
            logger.log("Number of edges: " + getTopology().edges().size());

            start = System.nanoTime();
            identifyEdge();
            end = System.nanoTime();
            logger.log("It took " + Logger.convertToMs(start,end) + "ms to identify the Edge");

            start = System.nanoTime();
            assignEdgeDevices();
            end = System.nanoTime();
            logger.log("It took " + Logger.convertToMs(start,end) + "ms to place the Devices");

            start = System.nanoTime();
            createFogLayout();
            end = System.nanoTime();
            logger.log("It took " + Logger.convertToMs(start,end) + "ms to create the FogLayout");

           /* start = System.nanoTime();
            placeFogNodes();
            end = System.nanoTime();
            logger.log("It took " + Logger.convertToMs(start,end) + "ms to place the FogNodes");*/

            start = System.nanoTime();
            assignApplications();
            end = System.nanoTime();
            logger.log("It took " + Logger.convertToMs(start,end) + "ms to assignApplications to devices and fog nodes");

        } catch (Exception e){
            e.printStackTrace();
        }

    }

    private void read() throws IOException {

        TopologyReader reader = new BriteReader();
        this.INSTANCE = reader.parse(settings.getInputGraphFilePath());

    }

    private void identifyEdge(){

        IEdgeIdentifier edgeIdentifier = new DefaultEdgeIdentifier();

        edgeIdentifier.identifyEdge(getTopology());

    }

    private void assignEdgeDevices() throws Exception {

        IDevicePlacement devicePlacement = new DefaultDevicePlacement();

        devicePlacement.assignEdgeDevices(getTopology(), settings.getDeviceNodeTypes());

    }

    private void createFogLayout() throws Exception {
        IFogLayout fogLayout = new DefaultFogLayout();
        fogLayout.identifyFogNodes(getTopology());
    }

    private void placeFogNodes(){

        IFogPlacement fogPlacement = new DefaultFogPlacement();
        fogPlacement.placeFogNodes(getTopology());

    }

    private void assignApplications(){

        IApplicationAssignmentPolicy applicationAssignmentPolicy = new DefaultApplicationAssignment();

        applicationAssignmentPolicy.generateDeviceApplicationMapping(getTopology());
        applicationAssignmentPolicy.generateFogApplicationMapping(getTopology());

    }


    public static class TopologyBuilder{

        private Settings settings;

        public Topology build() throws IOException {
            return new Topology(this);
        }

        public TopologyBuilder setup(Settings settings){
            this.settings = settings;
            return this;
        }

    }

    public void export() throws IOException {

        final Path exportPath = settings.getExportFilePath();

        ITopologyExporter exporter = new MaxinetExporter();

        exporter.exportTopology(getTopology(), exportPath);

    }

}
