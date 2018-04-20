package connconvert;


import java.io.BufferedReader;
import java.io.FileReader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.FieldNamingPolicy;

import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.File;
import java.util.Collections;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.neo4j.driver.v1.*;
import static org.neo4j.driver.v1.Values.parameters;

// TODO: Add ROI information using column names from neurons file? how? Also, rostral vs. caudal rois? what z divides distal vs. prox medulla
// FIB25 names often include column info (7 columns)  - pnas paper.
public class ConnConvert implements AutoCloseable {
    private final Driver driver;
    public static Neuron[] neuronsArray;
    public static List<Neuron> neurons;
    public static BodyWithSynapses[] bodiesArray;
    public static List<BodyWithSynapses> bodies;
    final static Properties properties = new Properties();
    public static String dataset;

    public ConnConvert (String uri, String user, String password) {
        driver = GraphDatabase.driver(uri,AuthTokens.basic(user, password));
    }

    @Override
    public void close() throws Exception {
        driver.close();
        System.out.println("Driver closed.");
    }

    public void prepDatabase() throws Exception {
        try (Session session = driver.session()) {
            try (Transaction tx = session.beginTransaction()) {
                //TODO: might need to change with more datasets
                tx.run("CREATE CONSTRAINT ON (n:Neuron) ASSERT n.bodyId IS UNIQUE");
                tx.success();

            }
//            try (Transaction tx = session.beginTransaction()) {
//                tx.run("CREATE INDEX ON :fib25(bodyId)");
//                tx.success();
//            }
//            try (Transaction tx = session.beginTransaction()) {
//
//                // can't create constraint on multiple labels
//                tx.run("CREATE CONSTRAINT ON (n:mb6) ASSERT n.sId IS UNIQUE");
//                tx.success();
//
//            }
//            try (Transaction tx = session.beginTransaction()) {
//                tx.run("CREATE INDEX ON :mb6(bodyId)");
//                tx.success();
//            }
            try(Transaction tx= session.beginTransaction()) {
                tx.run("CREATE CONSTRAINT ON (s:Synapse) ASSERT s.location IS UNIQUE");
                tx.success();
            }

            try(Transaction tx=session.beginTransaction()) {
                tx.run("CREATE CONSTRAINT ON (p:NeuronPart) ASSERT p.neuronPartId IS UNIQUE");
                tx.success();
            }

        }

    }

    public void addNeurons() throws Exception {
        try (Session session = driver.session()) {
            for (Neuron neuron : neurons) {
                try (Transaction tx = session.beginTransaction()) {
                    // TODO: Index name and status

                    tx.run("MERGE (n:Neuron {bodyId:$bodyId}) " +
                                    "ON CREATE SET n.bodyId = $bodyId," +
                                    " n.name = $name," +
                                    " n.type = $type," +
                                    " n.status = $status," +
                                    " n.size = $size \n " +
                                    "WITH n \n" +
                                    "CALL apoc.create.addLabels(id(n),[$dataset]) YIELD node \n" +
                                    "RETURN node",
                            parameters("bodyId", neuron.getId(),
                                    "name", neuron.getName(),
                                    "type", neuron.getType(),
                                    "status", neuron.getStatus(),
                                    "size", neuron.getSize(),
                                    "dataset", dataset));


                    tx.success();

                }

            }
            System.out.println("Added neurons.");
        }

    }

    public void addConnectsTo() throws Exception {
        try (Session session = driver.session()) {
            for (BodyWithSynapses bws : bodies) {
                for (Integer postsynapticBodyId : bws.connectsTo.keySet()) {
                    try (Transaction tx = session.beginTransaction()) {
                        // TODO: Incorporate confidence values for ConnectsTo

                        tx.run("MERGE (n:Neuron {bodyId:$bodyId1}) ON CREATE SET n.bodyId = $bodyId1, n:notinneurons \n" +
                                        "MERGE (m:Neuron {bodyId:$bodyId2}) ON CREATE SET m.bodyId = $bodyId2, m:notinneurons \n" +
                                        "MERGE (n)-[:ConnectsTo{weight:$weight}]->(m) \n" +
                                        "WITH n,m \n" +
                                        "CALL apoc.create.addLabels([id(n),id(m)],[$dataset]) YIELD node \n" +
                                        "RETURN node",
                                parameters("bodyId1", bws.getBodyId(),
                                        "bodyId2", postsynapticBodyId,
                                        "weight", bws.connectsTo.get(postsynapticBodyId),
                                        "dataset",dataset));

                        tx.success();

                    } catch (Exception e) {
                        System.out.println(bws);
                        System.out.println(postsynapticBodyId);
                        System.out.println(bws.connectsTo.keySet());
                    }
                    try (Transaction tx = session.beginTransaction()) {
                        tx.run("MATCH (n:Neuron {bodyId:$bodyId1} ) SET n.pre = $pre, n.post = $post",
                                parameters("bodyId1", bws.getBodyId(),
                                        "pre", bws.getPre(),
                                        "post", bws.getPost()));
                        tx.success();

                    }

                }

                for (Integer presynapticBodyId : bws.connectsFrom.keySet()) {
                    try (Transaction tx = session.beginTransaction()) {
                        tx.run("MATCH (n:Neuron {bodyId:$bodyId1} ) SET n.pre = $pre, n.post = $post",
                                parameters("bodyId1", bws.getBodyId(),
                                        "pre", bws.getPre(),
                                        "post", bws.getPost()));
                        tx.success();

                    }

                }
            }
            System.out.println("Added ConnectsTo relations.");
            System.out.println("Added pre and post counts.");
        }
    }

    public void addSynapses() throws Exception {
        try (Session session = driver.session()) {
            for (BodyWithSynapses bws : bodies) {
                for (Synapse synapse : bws.getSynapseSet()) {
                    try (Transaction tx = session.beginTransaction()) {
                        // requires APOC: need to add apoc to neo4j plugins
                        if (synapse.getType().equals("pre")) {
                            tx.run("MERGE (s:Synapse:PreSyn {location:$location}) " +
                                            "ON CREATE SET s.location = $location," +
                                            " s.confidence = $confidence," +
                                            " s.type = $type," +
                                            " s.x=$x," +
                                            " s.y=$y," +
                                            " s.z=$z" ,
//                                            " WITH s \n" +
//                                            " CALL apoc.create.addLabels(id(s),$rois) YIELD node \n" +
//                                            " RETURN node",
                                    parameters("location", synapse.getLocationString(),
                                            "confidence", synapse.getConfidence(),
                                            "type", synapse.getType(),
                                            "x",synapse.getLocation().get(0),
                                            "y",synapse.getLocation().get(1),
                                            "z",synapse.getLocation().get(2)));
                            tx.success();
                        } else if (synapse.getType().equals("post")) {
                            tx.run("MERGE (s:Synapse:PostSyn {location:$location}) " +
                                            "ON CREATE SET s.location = $location," +
                                            " s.confidence = $confidence," +
                                            " s.type = $type," +
                                            " s.x=$x," +
                                            " s.y=$y," +
                                            " s.z=$z" ,
//                                            " WITH s \n" +
//                                            " CALL apoc.create.addLabels(id(s),$rois) YIELD node \n" +
//                                            " RETURN node",
                                    parameters("location", synapse.getLocationString(),
                                            "confidence", synapse.getConfidence(),
                                            "type", synapse.getType(),
                                            "x",synapse.getLocation().get(0),
                                            "y",synapse.getLocation().get(1),
                                            "z",synapse.getLocation().get(2)));

                            tx.success();
                        }
                    }

                    }

                }

            System.out.println("Synapse nodes added.");
        }

    }

    public void addRois() {
        try (Session session = driver.session()) {
            for (BodyWithSynapses bws : bodies) {
                for (Synapse synapse : bws.getSynapseSet()) {
                    try (Transaction tx = session.beginTransaction()) {
                        tx.run("MERGE (s:Synapse {location:$location}) ON CREATE SET s.location = $location \n" +
                                        "WITH s \n" +
                                        "CALL apoc.create.addLabels(id(s),$rois) YIELD node \n" +
                                        "RETURN node",
                                parameters("location", synapse.getLocationString(),
                                        "rois", synapse.getRois()));
                        tx.success();
                    }
                    try (Transaction tx = session.beginTransaction()) {
                        tx.run("MERGE (n:Neuron {bodyId:$bodyId}) ON CREATE SET n.bodyId = $bodyId \n" +
                                        "WITH n \n" +
                                        "CALL apoc.create.addLabels(id(n),$rois) YIELD node \n" +
                                        "RETURN node",
                                parameters("bodyId", bws.getBodyId(),
                                        "rois", synapse.getRois()));
                        tx.success();
                    }
                }

            }
        }
        System.out.println("ROI labels added to Synapses and Neurons.");
    }



    public void addSynapsesTo(HashMap<String,List<String>> preToPost) throws Exception {
        try (Session session = driver.session()) {
            for (String preLoc : preToPost.keySet()) {
                for (String postLoc : preToPost.get(preLoc)) {
                    try (Transaction tx = session.beginTransaction()) {
                        tx.run("MERGE (s:Synapse {location:$prelocation}) ON CREATE SET s.location = $prelocation, s:createdforsynapsesto \n" +
                                        "MERGE (t:Synapse {location:$postlocation}) ON CREATE SET t.location = $postlocation, t:createdforsynapsesto \n" +
                                        "MERGE (s)-[:SynapsesTo]->(t) \n",
                                parameters("prelocation", preLoc,
                                        "postlocation", postLoc));
                        tx.success();

                    }
                }
            }
        }
            System.out.println("SynapsesTo relations added.");
    }

    public void addNeuronParts() throws Exception {
        try (Session session = driver.session()) {
                for (BodyWithSynapses bws: bodies) {
                    for (NeuronPart np : bws.getNeuronParts()) {
                        try(Transaction tx = session.beginTransaction()) {
                        // create neuronpart node that points to neuron with partof relation
                            String neuronPartId = bws.getBodyId()+":"+np.getRoi();
                        tx.run("MERGE (n:Neuron {bodyId:$bodyId}) ON CREATE SET n.bodyId=$bodyId, n:createdforneuronpart \n"+
                                        "MERGE (p:NeuronPart {neuronPartId:$neuronPartId}) ON CREATE SET p.neuronPartId = $neuronPartId, p.pre=$pre, p.post=$post, p.size=$size \n"+
                                        "MERGE (p)-[:PartOf]->(n) \n" +
                                        "WITH p \n" +
                                        "CALL apoc.create.addLabels(id(p),[$roi]) YIELD node \n" +
                                        "RETURN node",
                                parameters("bodyId",bws.getBodyId(),
                                        "roi",np.getRoi(),
                                        "neuronPartId",neuronPartId,
                                        "pre",np.getPre(),
                                        "post",np.getPost(),
                                        "size",np.getPre()+np.getPost()));
                        tx.success();
                    }
                }
            }
        }
        System.out.println("NeuronPart nodes added with PartOf relationships.");
    }

    public void addSizeId() throws Exception {
        int sId = 0;
        try (Session session = driver.session()) {
            for (BodyWithSynapses bws : bodies) {
                try (Transaction tx = session.beginTransaction()) {
                    // bodies should be sorted in descending order by number of synapses, so can create id starting at 0
                    // id contains dataset name (e.g. fib25-0)

                    tx.run("MERGE (n:Neuron {bodyId:$bodyId}) ON CREATE SET n.bodyId=$bodyId \n" +
                                    "SET n.sId=$sId",
                            parameters("bodyId", bws.getBodyId(),
                                    "sId", sId));
                    sId++;
                    tx.success();

                }
            }
        }
    }





    public static List<Neuron> readNeuronsJson(String filepath) throws Exception{
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            neuronsArray = gson.fromJson(reader, Neuron[].class);
            neurons = Arrays.asList(neuronsArray);
            //System.out.println("Object mode: " + neurons[0]);
            System.out.println("Number of neurons: " + neurons.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return neurons;
    }

    public static List<BodyWithSynapses> readSynapsesJson(String filepath) throws Exception{
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {
            Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
            bodiesArray = gson.fromJson(reader, BodyWithSynapses[].class);
            bodies = Arrays.asList(bodiesArray);
            //System.out.println("Object mode: " + bodies[0]);
            System.out.println("Number of bodies with synapses: " + bodies.size());
            //System.out.println(bodies[0].synapseSet.get(2).getConnectionLocationStrings().get(0));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return bodies;
    }


    public static void main(String[] args) throws Exception {
        //String filepath = "/Users/neubarthn/Downloads/fib25_neo4j_inputs/fib25_Neurons.json";
        //String filepath2 = "/Users/neubarthn/Downloads/fib25_neo4j_inputs/fib25_Synapses.json";
        String filepath = "/Users/neubarthn/Downloads/mb6_neo4j_inputs/mb6_Neurons.json";
        String filepath2 = "/Users/neubarthn/Downloads/mb6_neo4j_inputs/mb6_Synapses.json";

        //read dataset name
        String patternNeurons = ".*inputs/(.*?)_Neurons.*";
        Pattern rN = Pattern.compile(patternNeurons);
        Matcher mN = rN.matcher(filepath);
        String patternSynapses = ".*inputs/(.*?)_Synapses.*";
        Pattern rS = Pattern.compile(patternSynapses);
        Matcher mS = rS.matcher(filepath2);
        mN.matches();
        mS.matches();

        try {
            if (mS.group(1).equals(mN.group(1))) {
                dataset = mS.group(1);
            };
        } catch (IllegalStateException ise) {
            System.out.println("Check input file names.");
            return;
        }
        System.out.println("Dataset is: " + dataset);




        neurons = readNeuronsJson(filepath);
        bodies = readSynapsesJson(filepath2);


        //sorting the neurons by size
        //Collections.sort(neurons,new SortNeuronBySize());
        //System.out.println(neurons.get(0));

        //create a new hashmap for storing: body>pre, pre>post; post>body
        HashMap<String, Integer> preToBody = new HashMap<>();
        HashMap<String, Integer> postToBody = new HashMap<>();
        HashMap<String,List<String>> preToPost = new HashMap<>();

        for (BodyWithSynapses bws : bodies) {
            List<String> preLocs = bws.getPreLocations();
            List<String> postLocs = bws.getPostLocations();

            if (!preLocs.isEmpty()) {
                for (String loc : preLocs) {
                    preToBody.put(loc, bws.getBodyId());

                }
            }
            if (!postLocs.isEmpty()) {
                for (String loc : postLocs) {
                    postToBody.put(loc, bws.getBodyId());
                }
            }
        }
        for (BodyWithSynapses bws : bodies) {
            bws.setNeuronParts();
            bws.setConnectsTo(postToBody);
            bws.setConnectsFrom(preToBody);
            bws.setSynapseCounts();
            preToPost.putAll(bws.getPreToPostForBody());

        }

        //can now sort bodies by synapse count
        Collections.sort(bodies,new SortBodyByNumberOfSynapses());


        //System.out.println(bodies[3].connectsTo);
        //System.out.println(bodies[3].connectsFrom);
        //List<Integer> temploc = new ArrayList<Integer>() {{
        //    add(4657);
        //    add(2648);
        //    add(1509);
        //}};
        //System.out.println(preToPost.get(temploc));
        //System.out.println(preToPost.keySet());
        //System.out.println(bodies[0].getSynapseSet().get(0));
        // start upload to database

        String configPath = new File("").getAbsolutePath();
        configPath = configPath.concat("/connconvert.properties");
        properties.load(new FileInputStream(configPath));

        String uri = "bolt://localhost:7687";
        String user = properties.getProperty("username");
        String password = properties.getProperty("password");

        try(ConnConvert connConvert = new ConnConvert(uri,user,password)) {
            // uncomment to add different features to database
            //connConvert.prepDatabase();
            //connConvert.addNeurons();
            connConvert.addConnectsTo();
            // connConvert.addSynapses();
            //connConvert.addSynapsesTo(preToPost);
            //connConvert.addRois();
            //connConvert.addNeuronParts();
            //connConvert.addSizeId();

        }

    }





}




