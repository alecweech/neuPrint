package org.janelia.flyem.neuprintprocedures.functions;

import apoc.convert.Json;
import apoc.create.Create;
import apoc.refactor.GraphRefactoring;
import org.janelia.flyem.neuprinter.Neo4jImporter;
import org.janelia.flyem.neuprinter.NeuPrinterMain;
import org.janelia.flyem.neuprinter.SynapseMapper;
import org.janelia.flyem.neuprinter.model.BodyWithSynapses;
import org.janelia.flyem.neuprinter.model.Neuron;
import org.janelia.flyem.neuprinter.model.SortBodyByNumberOfSynapses;
import org.janelia.flyem.neuprintprocedures.analysis.AnalysisProcedures;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.harness.junit.Neo4jRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.neo4j.driver.v1.Values.parameters;

public class RoiInfoNameTest {

    @ClassRule
    public static Neo4jRule neo4j;
    private static Driver driver;

    static {
        neo4j = new Neo4jRule()
                .withFunction(Json.class)
                .withFunction(NeuPrintUserFunctions.class);
    }

    @BeforeClass
    public static void before() {

        List<Neuron> neuronList = NeuPrinterMain.readNeuronsJson("src/test/resources/smallNeuronList.json");
        SynapseMapper mapper = new SynapseMapper();
        List<BodyWithSynapses> bodyList = mapper.loadAndMapBodies("src/test/resources/smallBodyListWithExtraRois.json");
        HashMap<String, Set<String>> preToPost = mapper.getPreToPostMap();
        bodyList.sort(new SortBodyByNumberOfSynapses());

        driver = GraphDatabase.driver(neo4j.boltURI(), Config.build().withoutEncryption().toConfig());

        String dataset = "test";

        Neo4jImporter neo4jImporter = new Neo4jImporter(driver);
        neo4jImporter.prepDatabase(dataset);

        neo4jImporter.addSegments(dataset, neuronList);

        neo4jImporter.addConnectsTo(dataset, bodyList);
        neo4jImporter.addSynapsesWithRois(dataset, bodyList);
        neo4jImporter.addSynapsesTo(dataset, preToPost);
        neo4jImporter.addSegmentRois(dataset, bodyList);
        neo4jImporter.addSynapseSets(dataset, bodyList);
        neo4jImporter.createMetaNodeWithDataModelNode(dataset, 1.0F);
        neo4jImporter.addAutoNamesAndNeuronLabels(dataset, 0);

        Session session = driver.session();

        session.writeTransaction(tx -> tx.run(" MATCH (m:Meta{dataset:\"test\"}) WITH keys(apoc.convert.fromJsonMap(m.roiInfo)) AS rois MATCH (n:`test-Neuron`) SET n.clusterName=neuprint.roiInfoAsName(n.roiInfo, n.pre, n.post, 0.10, rois) RETURN n.bodyId, n.clusterName"));


    }

    @Test
    public void shouldProduceCorrectRoiBasedName() {
        Session session = driver.session();

        List<String> superLevelRois = new ArrayList<>();
        superLevelRois.add("roiA");

        String name5percent = session.readTransaction(tx -> tx.run("MATCH (n:`test-Neuron`{bodyId:8426959}) WITH neuprint.roiInfoAsName(n.roiInfo,n.pre,n.post,.05,$superLevelRois) AS name RETURN name ", parameters("superLevelRois", superLevelRois))).single().get(0).asString();

        Assert.assertEquals("roiA-roiA", name5percent);

        String name100percent = session.readTransaction(tx -> tx.run("MATCH (n:`test-Neuron`{bodyId:8426959}) WITH neuprint.roiInfoAsName(n.roiInfo,n.pre,n.post,1.0,$superLevelRois) AS name RETURN name ", parameters("superLevelRois", superLevelRois))).single().get(0).asString();

        Assert.assertEquals("none-none", name100percent);

    }


//    @Test
//    public void shouldGetTopXConnectionClusterNames() {
//        Session session = driver.session();
//
//        String resultJson = session.readTransaction(tx -> tx.run("WITH neuprint.getClusterNamesOfTopXConnections(8426959, \"test\", 5) AS result RETURN result")).single().get(0).asString();
//
//        System.out.println(resultJson);
//
//    }
//


}