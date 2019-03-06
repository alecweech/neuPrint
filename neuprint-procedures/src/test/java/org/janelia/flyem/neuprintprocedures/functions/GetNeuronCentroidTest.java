package org.janelia.flyem.neuprintprocedures.functions;

import org.janelia.flyem.neuprinter.Neo4jImporter;
import org.janelia.flyem.neuprinter.SynapseMapper;
import org.janelia.flyem.neuprinter.model.BodyWithSynapses;
import org.janelia.flyem.neuprintloadprocedures.procedures.LoadingProcedures;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.neo4j.driver.v1.Config;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Session;
import org.neo4j.harness.junit.Neo4jRule;

import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class GetNeuronCentroidTest {

    @ClassRule
    public static Neo4jRule neo4j;
    private static Driver driver;

    static {
        neo4j = new Neo4jRule()
                .withFunction(NeuPrintUserFunctions.class)
                .withProcedure(LoadingProcedures.class);
    }

    @BeforeClass
    public static void before() {

        SynapseMapper mapper = new SynapseMapper();
        List<BodyWithSynapses> bodyList = mapper.loadAndMapBodies("src/test/resources/smallBodyListWithExtraRois.json");
        HashMap<String, Set<String>> preToPost = mapper.getPreToPostMap();

        driver = GraphDatabase.driver(neo4j.boltURI(), Config.build().withoutEncryption().toConfig());

        String dataset = "test";

        Neo4jImporter neo4jImporter = new Neo4jImporter(driver);
        neo4jImporter.prepDatabase(dataset);

        neo4jImporter.addConnectsTo(dataset, bodyList);
        neo4jImporter.addSynapsesWithRois(dataset, bodyList);
        neo4jImporter.addSynapsesTo(dataset, preToPost);
        neo4jImporter.addSegmentRois(dataset, bodyList);
        neo4jImporter.addSynapseSets(dataset, bodyList);

    }

    @AfterClass
    public static void after() {
        driver.close();
    }

    @Test
    public void shouldReturnCentroid() {

        Session session = driver.session();

        List<Object> centroid = session.readTransaction(tx -> tx.run("WITH neuprint.getNeuronCentroid(8426959,'test') AS centroid RETURN centroid")).single().get(0).asList();

        Assert.assertEquals(4222L, centroid.get(0));
        Assert.assertEquals(2402L, centroid.get(1));
        Assert.assertEquals(1688L, centroid.get(2));

    }
}
