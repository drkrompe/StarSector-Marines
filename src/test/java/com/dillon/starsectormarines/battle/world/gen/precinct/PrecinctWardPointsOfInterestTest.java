package com.dillon.starsectormarines.battle.world.gen.precinct;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.GrownTrunkPlan;
import com.dillon.starsectormarines.battle.world.gen.bsp.stage.PrecinctWardStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.PointOfInterest;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A garrison's stores and its seat of command are prizes, not just geometry.
 *
 * <p>The commander tier reads {@code TacticalNode}s and mission setups read
 * {@link PointOfInterest}s, and a packed garrison used to say only the first of
 * those. Measured on a 144x80 Raid map that was a raid with nothing to strike:
 * the garrison takes most of the map, the defender stands inside its claim, and
 * every point of interest on the board came off the settlement — the marines'
 * own side.
 */
class PrecinctWardPointsOfInterestTest {

    private static final int W = 560;
    private static final int H = 336;

    /**
     * The room purposes a raid is for, and what each is called in the mission
     * vocabulary.
     */
    private static final Map<RoomPurpose, PointOfInterest.Kind> PRIZES =
            new EnumMap<>(Map.of(
                    RoomPurpose.ARMORY, PointOfInterest.Kind.DEPOT,
                    RoomPurpose.VEHICLE_BAY, PointOfInterest.Kind.DEPOT,
                    RoomPurpose.STOCKROOM, PointOfInterest.Kind.DEPOT,
                    RoomPurpose.PARTS_CAGE, PointOfInterest.Kind.DEPOT,
                    RoomPurpose.KEEP_THRONE, PointOfInterest.Kind.ADMINISTRATIVE,
                    RoomPurpose.CONTROL_ROOM, PointOfInterest.Kind.COMMS));

    private static GenContext ward(List<Precinct> precincts) {
        GrownTrunkPlan.Grown grown = GrownTrunkPlan.grow(W, H, new Random(42L),
                precincts.stream()
                        .map(p -> new GrownTrunkPlan.Seed(p.seedX(), p.seedY(), p.growth()))
                        .toList());
        int[] allowance = PrecinctAllowance.derive(precincts, grown.owner(), W, H);
        int[][] claim =
                PrecinctClaim.byKind(precincts).assign(grown.owner(), W, H, allowance);

        GenContext ctx = new GenContext(new NavigationGrid(W, H), new CellTopology(W, H),
                new Random(42L), W, H, 42L);
        ctx.put(BspKeys.PRECINCTS, PrecinctPlan.authored(precincts));
        ctx.put(BspKeys.PRECINCT_CLAIM, claim);
        ctx.put(BspKeys.PRECINCT_ROAD, grown.owner());
        new PrecinctWardStage().run(ctx);
        return ctx;
    }

    private static List<Precinct> oneGarrison() {
        return List.of(
                Precinct.garrison("garrison", W / 3, H / 2,
                        GrownTrunkPlan.Profile.hamlet(), FortressProgram.garrison()),
                Precinct.settlement("town", 2 * W / 3, H / 3,
                        GrownTrunkPlan.Profile.town()));
    }

    /**
     * How many rooms of each purpose the garrison actually built.
     *
     * <p>Read off the program's expanded instance list less what the stage
     * recorded as unplaced, which is the only route to the packer's result from
     * outside the stage: the {@code FortressInterior.Result} is local to
     * {@code run}, and the per-cell room labels would have to be
     * connected-component counted to be turned back into rooms.
     */
    private static Map<RoomPurpose, Integer> built(GenContext ctx, String precinct) {
        Map<RoomPurpose, Integer> counts = new EnumMap<>(RoomPurpose.class);
        for (FortressBuilding building : FortressProgram.garrison().expanded()) {
            counts.merge(building.purpose(), 1, Integer::sum);
        }
        Map<String, List<FortressBuilding>> unplaced = ctx.get(BspKeys.UNPLACED_PROGRAM);
        List<FortressBuilding> missing = unplaced == null ? List.of()
                : unplaced.getOrDefault(precinct, List.of());
        for (FortressBuilding building : missing) {
            counts.merge(building.purpose(), -1, Integer::sum);
        }
        return counts;
    }

    private static long count(GenContext ctx, PointOfInterest.Kind kind) {
        return ctx.pois.stream().filter(poi -> poi.kind == kind).count();
    }

    /** Every store and every seat of command the garrison built is a prize. */
    @Test
    void aGarrisonEmitsOnePointOfInterestPerPrizeItBuilt() {
        GenContext ctx = ward(oneGarrison());
        Map<RoomPurpose, Integer> built = built(ctx, "garrison");
        Map<PointOfInterest.Kind, Integer> expected =
                new EnumMap<>(PointOfInterest.Kind.class);
        PRIZES.forEach((purpose, kind) ->
                expected.merge(kind, built.getOrDefault(purpose, 0), Integer::sum));

        assertFalse(ctx.pois.isEmpty(),
                "a packed garrison produced no points of interest at all, so every "
                        + "mission that looks for a prize sees walls and roofs");
        for (PointOfInterest.Kind kind : expected.keySet()) {
            assertEquals(expected.get(kind).longValue(), count(ctx, kind),
                    "the garrison built " + expected.get(kind) + " " + kind
                            + " room(s) and put " + count(ctx, kind) + " on the map");
        }
        assertEquals(expected.values().stream().mapToInt(Integer::intValue).sum(),
                ctx.pois.size(),
                "the ward emitted a point of interest for something that is not a prize");
    }

    /** A barrack block is somewhere people sleep, which is not worth flying for. */
    @Test
    void aBarracksIsNotAPrize() {
        GenContext ctx = ward(oneGarrison());
        assertTrue(built(ctx, "garrison").getOrDefault(RoomPurpose.BARRACKS, 0) > 0,
                "the garrison built no barracks, so this test is measuring nothing");
        for (PointOfInterest poi : ctx.pois) {
            assertEquals(PRIZES.get(ctx.topology.getRoomPurpose(
                            poi.interiorAnchorX, poi.interiorAnchorY)), poi.kind,
                    "a " + poi.kind + " stands in a "
                            + ctx.topology.getRoomPurpose(poi.interiorAnchorX,
                            poi.interiorAnchorY) + ", which is not one of the six");
        }
    }

    /** Both anchors are somewhere a unit sent there can actually arrive. */
    @Test
    void everyAnchorIsSomewhereAUnitCanStand() {
        GenContext ctx = ward(oneGarrison());
        for (PointOfInterest poi : ctx.pois) {
            assertTrue(poi.interiorAnchorX >= poi.left && poi.interiorAnchorX <= poi.right
                            && poi.interiorAnchorY >= poi.top && poi.interiorAnchorY <= poi.bottom,
                    "a " + poi.kind + "'s interior anchor at " + poi.interiorAnchorX + ","
                            + poi.interiorAnchorY + " is outside its own bounds");
            assertTrue(ctx.grid.isWalkable(poi.interiorAnchorX, poi.interiorAnchorY),
                    "a " + poi.kind + "'s interior anchor stands on something nobody "
                            + "can occupy");
            assertFalse(ctx.grid.isDoorway(poi.interiorAnchorX, poi.interiorAnchorY),
                    "a " + poi.kind + "'s interior anchor blocks its own doorway");
            assertTrue(ctx.grid.isWalkable(poi.anchorCellX, poi.anchorCellY),
                    "a " + poi.kind + "'s exterior anchor at " + poi.anchorCellX + ","
                            + poi.anchorCellY + " is nowhere anybody can stand");
        }
    }

    /** A zoned precinct's prizes come from its own fill, not from this stage. */
    @Test
    void aZonedPrecinctEmitsNothingHere() {
        GenContext ctx = ward(List.of(
                Precinct.settlement("town", W / 3, H / 2, GrownTrunkPlan.Profile.town()),
                Precinct.settlement("hamlet", 2 * W / 3, H / 3,
                        GrownTrunkPlan.Profile.hamlet())));
        assertTrue(ctx.pois.isEmpty(),
                "the ward emitted " + ctx.pois.size() + " point(s) of interest for "
                        + "precincts it never packed; a zoned precinct's parcels are "
                        + "filled later and emit their own");
    }
}
