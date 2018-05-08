package org.janelia.flyem.connconvert.model;

import java.util.List;

import org.janelia.flyem.connconvert.json.JsonUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests the {@link BodyWithSynapses} class.
 */
public class BodyWithSynapsesTest {

    @Test
    public void testJsonProcessing() {

        final List<BodyWithSynapses> parsedBodyList = BodyWithSynapses.fromJson(BODY_LIST_JSON);

        Assert.assertEquals("invalid number of bodies parsed",
                            4, parsedBodyList.size());

        final String serializedJson = JsonUtils.GSON.toJson(parsedBodyList);

        Assert.assertTrue("serialized result is empty",
                          serializedJson.length() > 0);


        Assert.assertEquals("serialized result does not match original",
                BODY_LIST_JSON.replaceAll("[\\n\\t\\r\\s+]+"," "), serializedJson.replaceAll("[\\n\\t\\r\\s+]+"," "));

        // TODO: improve this test
    }

    // TODO: add more tests!

    private static final String BODY_LIST_JSON =
            "[\n" +
            "  {\n" +
            "    \"BodyId\": 8426959,\n" +
            "    \"SynapseSet\": [\n" +
            "      {\n" +
            "        \"Type\": \"pre\", \"Location\": [ 4287, 2277, 1542 ], \"Confidence\": 1.0, \"rois\": [ \"seven_column_roi\" ],\n" +
            "        \"ConnectsTo\": [\n" +
            "          [ 4298, 2294, 1542 ], [ 4301, 2276, 1535 ], [ 4292, 2261, 1542 ]\n" +
            "        ]\n" +
            "      },\n" +
            "      { \"Type\": \"post\", \"Location\": [ 4222, 2402, 1688 ], \"ConnectsFrom\": [ [ 4236, 2394, 1700 ] ], \"Confidence\": 1.0, \"rois\": [ \"seven_column_roi\" ] }\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"BodyId\": 26311,\n" +
            "    \"SynapseSet\": [\n" +
            "      { \"Type\": \"post\", \"Location\": [ 4301, 2276, 1535 ], \"ConnectsFrom\": [ [ 4287, 2277, 1542 ] ], \"Confidence\": 1.0, \"rois\": [ \"seven_column_roi\" ] }\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"BodyId\": 2589725,\n" +
            "    \"SynapseSet\": [\n" +
            "      { \"Type\": \"post\", \"Location\": [ 4298, 2294, 1542 ], \"ConnectsFrom\": [ [ 4287, 2277, 1542 ] ], \"Confidence\": 1.0, \"rois\": [ \"seven_column_roi\" ] }\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"BodyId\": 831744,\n" +
            "    \"SynapseSet\": [\n" +
            "      { \"Type\": \"post\", \"Location\": [ 4292, 2261, 1542 ], \"ConnectsFrom\": [ [ 4287, 2277, 1542 ] ], \"Confidence\": 1.0, \"rois\": [ \"seven_column_roi\" ] }\n" +
            "    ]\n" +
            "  }\n" +
            "]";

}