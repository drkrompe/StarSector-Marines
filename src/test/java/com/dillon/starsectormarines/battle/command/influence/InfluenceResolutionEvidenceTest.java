package com.dillon.starsectormarines.battle.command.influence;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceResolutionEvidenceTest {
    @Test
    void snapshotPayloadSeparatesKnownPrimitiveBytesFromUnknownReferenceWidth() throws JSONException {
        JSONObject payload = InfluenceResolutionEvidence.snapshotPayload(560, 336);
        long cells = 560L * 336;
        assertEquals(cells, payload.getLong("cells"));
        assertEquals(9, payload.getInt("requiredPrimitiveBytesPerCell"));
        assertEquals(46, payload.getInt("avoidedPrimitiveBytesPerCell"));
        assertEquals(cells * 9, payload.getLong("compactPerCellPrimitivePayloadBytes"));
        assertEquals(cells * 55, payload.getLong("fullPerCellPrimitivePayloadBytes"));
        assertEquals(cells * 46, payload.getLong("avoidedPrimitivePayloadBytes"));
        assertEquals(cells * 2, payload.getLong("avoidedReferenceSlots"));
    }

    @Test
    void equalConstantFieldsCompareByWorldCellsDespiteDifferentStorage() throws JSONException {
        NavigationGrid grid = openGrid(31, 15);
        float[] control = new float[8];
        float[] subject = new float[2];
        Arrays.fill(control, 2f);
        Arrays.fill(subject, 2f);
        JSONObject result = InfluenceResolutionEvidence.compareFields(grid, control, subject);
        assertEquals(465, result.getInt("walkableCells"));
        assertEquals(0, result.getDouble("meanAbsoluteError"));
        assertEquals(0, result.getDouble("rootMeanSquareError"));
        assertEquals(0, result.getInt("supportChangedCells"));
        assertEquals(1, result.getDouble("hotRegionJaccard"));
    }

    @Test
    void coarsePlateauExpansionReportsCellWeightedErrorAndChangedSupport() throws JSONException {
        NavigationGrid grid = openGrid(32, 16);
        float[] control = new float[8];
        control[0] = 1f;
        JSONObject result = InfluenceResolutionEvidence.compareFields(grid, control, new float[]{1f, 0f});
        assertEquals(512, result.getInt("walkableCells"));
        assertEquals(64, result.getInt("controlPositiveCells"));
        assertEquals(256, result.getInt("subjectPositiveCells"));
        assertEquals(192, result.getInt("supportChangedCells"));
        assertEquals(0.375, result.getDouble("meanAbsoluteError"));
        assertEquals(Math.sqrt(0.375), result.getDouble("rootMeanSquareError"));
        assertEquals(3.0, result.getDouble("absoluteErrorOverControlMass"));
        JSONObject region = result.getJSONArray("largestMeanDifferenceRegions").getJSONObject(0);
        assertEquals(0, region.getInt("worldX"));
        assertEquals(0.25, region.getDouble("controlMean"));
        assertEquals(1.0, region.getDouble("subjectMean"));
    }

    @Test
    void emptyWalkableSupportDoesNotInventHotspotAgreementOrRelativeError() throws JSONException {
        JSONObject result = InfluenceResolutionEvidence.compareFields(new NavigationGrid(16, 16),
                new float[4], new float[1]);
        assertEquals(0, result.getInt("walkableCells"));
        assertEquals(0, result.getDouble("meanAbsoluteError"));
        assertTrue(result.isNull("hotRegionJaccard"));
        assertTrue(result.isNull("absoluteErrorOverControlMass"));
    }

    @Test
    void wallProbeSeparatesGraphReachabilityFromCoarseValueAliasing() throws JSONException {
        JSONArray probes = InfluenceResolutionEvidence.wallProbes();
        JSONObject closedEight = probes.getJSONObject(0);
        JSONObject closedSixteen = probes.getJSONObject(1);
        assertFalse(closedEight.getBoolean("graphReachableAcrossWall"));
        assertFalse(closedSixteen.getBoolean("graphReachableAcrossWall"));
        assertEquals(0, closedEight.getDouble("acrossWallValue"));
        assertEquals(1, closedSixteen.getDouble("acrossWallValue"));
        assertEquals(0, closedEight.getDouble("farAcrossWallValue"));
        assertEquals(0, closedSixteen.getDouble("farAcrossWallValue"));
        for (int i = 2; i < 4; i++) {
            assertTrue(probes.getJSONObject(i).getBoolean("graphReachableAcrossWall"));
            assertTrue(probes.getJSONObject(i).getDouble("farAcrossWallValue") > 0);
        }
    }

    private static NavigationGrid openGrid(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        return grid;
    }
}
