package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.decision.TacticalMap;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.Buildings;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.road.RoadGraph;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.ops.MissionType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mission says what it needs its map to contain, and a map that falls short
 * says so by name.
 *
 * <p>Generation declines politely all the way down, and a quarter of conquest
 * battles shipped with no garrison airfield because of it — every pass correct,
 * the mission not the mission any more. Nothing in the chain could catch that,
 * because no pass knows what was promised.
 */
class MissionMapRequirementsTest {

    /** A conquest-shaped map, minus whichever features are asked to be absent. */
    private static MapResult mapWithout(MapFeature... absent) {
        EnumSet<MapFeature> gone = EnumSet.noneOf(MapFeature.class);
        for (MapFeature feature : absent) gone.add(feature);

        List<TacticalNode> nodes = new java.util.ArrayList<>();
        if (!gone.contains(MapFeature.DEFENDER_GARRISON)) {
            nodes.add(new TacticalNode(TacticalNode.Kind.BARRACKS, 5, 5,
                    4, 4, 6, 6, Faction.DEFENDER, 50, 4));
        }
        if (!gone.contains(MapFeature.CENTRAL_KEEP)) {
            nodes.add(new TacticalNode(TacticalNode.Kind.COMMAND_POST, 9, 9,
                    8, 8, 10, 10, Faction.DEFENDER, 90, 6));
        }
        List<LandingPad> pads = gone.contains(MapFeature.GARRISON_AIRFIELD)
                ? List.of()
                : List.of(LandingPad.garrison(12, 12, LandingPad.Approach.NORTH));
        int spawnX = gone.contains(MapFeature.MARINE_LANDING_ZONE) ? -1 : 2;

        return new MapResult(new NavigationGrid(20, 20), new CellTopology(20, 20),
                spawnX, spawnX, 18, 18, List.of(), List.of(),
                new TacticalMap(nodes), Buildings.EMPTY, List.of(), RoadGraph.EMPTY, pads);
    }

    @Test
    void aWholeConquestMapMeetsItsMissionsRequirements() {
        assertTrue(MissionMapRequirements.satisfied(MissionType.CONQUEST, mapWithout()),
                "a map with every conquest feature was reported invalid");
    }

    @Test
    void aMapWithNoGarrisonAirfieldIsNotAValidConquestMap() {
        EnumSet<MapFeature> missing = MissionMapRequirements.missingFrom(
                MissionType.CONQUEST, mapWithout(MapFeature.GARRISON_AIRFIELD));

        assertEquals(EnumSet.of(MapFeature.GARRISON_AIRFIELD), missing,
                "the missing airfield was not the thing reported missing");
    }

    /** Every requirement is checked, not just the first one that fails. */
    @Test
    void everyMissingFeatureIsNamed() {
        EnumSet<MapFeature> missing = MissionMapRequirements.missingFrom(
                MissionType.CONQUEST,
                mapWithout(MapFeature.GARRISON_AIRFIELD, MapFeature.CENTRAL_KEEP));

        assertEquals(EnumSet.of(MapFeature.GARRISON_AIRFIELD, MapFeature.CENTRAL_KEEP),
                missing);
        assertTrue(MissionMapRequirements.describeFailure(
                        MissionType.CONQUEST, 7L, 8, missing).contains("garrison airfield"),
                "the failure message does not say what was missing");
    }

    /**
     * A mission that has not said what it needs is unaffected.
     *
     * <p>Requirements are a claim about a mission, and inventing one for a
     * mission nobody has thought about turns a working battle into a crash.
     */
    @Test
    void aMissionWithNoStatedRequirementsAcceptsAnyMap() {
        assertTrue(MissionMapRequirements.requiredFor(MissionType.ASSAULT).isEmpty());
        assertTrue(MissionMapRequirements.satisfied(MissionType.ASSAULT,
                mapWithout(MapFeature.values())));
        assertFalse(MissionMapRequirements.satisfied(MissionType.CONQUEST,
                mapWithout(MapFeature.values())));
    }
}
