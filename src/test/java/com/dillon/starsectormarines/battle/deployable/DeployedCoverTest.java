package com.dillon.starsectormarines.battle.deployable;

import com.dillon.starsectormarines.battle.combat.BallisticResolver;
import com.dillon.starsectormarines.battle.combat.DamageResolver;
import com.dillon.starsectormarines.battle.combat.FireStance;
import com.dillon.starsectormarines.battle.infantry.DeployableTactics;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.DeployableCoverSpec;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural coverage for the second deployable, driving a real
 * {@link BattleSimulation}. Every expected magnitude is derived from the
 * installed catalogs and the shipped cover tables rather than restated, so a
 * balance edit retunes the screen without touching this file.
 *
 * <p>The claims worth defending are narrow and specific: the screen protects
 * one boundary and not the others; it changes cover and <em>nothing</em> else
 * about the map; and the map it leaves behind when it goes is byte-identical to
 * the one it found.
 */
class DeployedCoverTest {

    private static final float EPS = 1e-3f;
    private static final int CARRIER_X = 10;
    private static final int CARRIER_Y = 10;
    /** Well past the proximity ramp, so cover catches at its full authored chance. */
    private static final int VOLLEY_RANGE_CELLS = 18;
    private static final int VOLLEY_TICKS = 2_000;

    private static SpecialEquipmentDef revetment() {
        return SpecialEquipmentRegistry.require(
                SpecialEquipmentRegistry.FIELD_REVETMENT_ID);
    }

    private static DeployableCoverSpec spec() {
        return revetment().deployableCoverSpec();
    }

    private static SharedEdgeBarrier.Kind profile() {
        return SharedEdgeBarrier.Kind.valueOf(spec().barrierKind());
    }

    private static BattleSimulation openArena(int width, int height) {
        NavigationGrid grid = new NavigationGrid(width, height);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(width, height));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /**
     * The marine who carries and places the screen. Given the inert structure
     * role so the measurement is about the placed screen rather than about
     * whatever the carrier's AI decided to do this second.
     */
    private static long marine(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("marine-" + sim.liveUnitCount(),
                Faction.MARINE, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE)
                .specialEquipment(revetment(), revetment().startingAmmo()));
    }

    private static long shooter(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("shooter-" + sim.liveUnitCount(),
                Faction.DEFENDER, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE));
    }

    /** A body that stays where it was put and soaks measurable damage. */
    private static long standingTarget(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("target-" + sim.liveUnitCount(),
                Faction.MARINE, UnitType.RANGE_TARGET, x, y)
                .role(UnitRole.STRUCTURE)
                .health(1_000_000f));
    }

    /** Places a screen directly, bypassing the carrier's opportunity gate. */
    private static void placeScreen(BattleSimulation sim, long carrier,
                                    int cellX, int cellY, Direction facing) {
        sim.deployedCover().queuePlacement(carrier, sim.identity().faction(carrier),
                cellX, cellY, facing, spec());
        sim.advance(BattleSimulation.TICK_DT);
    }

    private static void advanceSeconds(BattleSimulation sim, float seconds) {
        int ticks = Math.max(1, Math.round(seconds / BattleSimulation.TICK_DT));
        for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
    }

    /** Damage the target actually lost from one attributed hit. */
    private static float damageFrom(BattleSimulation sim, long target, long attacker,
                                    float requested) {
        float before = sim.world().hp(target);
        sim.applyDamage(target, attacker, requested, /*penetration*/ 1_000f, 1f);
        sim.advance(BattleSimulation.TICK_DT);
        return before - sim.world().hp(target);
    }

    @Test
    void theCarriedScreenNamesAProfileThatLeavesMovementAlone() {
        assertNotNull(spec(), "the field revetment must author a cover-deployable spec");
        assertFalse(profile().blocksMovement(),
                "a carried screen may only place a profile that leaves the"
                        + " navigation transition open — anything that closes one"
                        + " could partition the walkable graph");
        assertTrue(profile().coverLevel() > 0, "a cover screen must supply cover");
    }

    @Test
    void aScreenCoversTheBoundaryItStandsOnAndNoOther() {
        BattleSimulation sim = openArena(24, 24);
        long carrier = marine(sim, CARRIER_X, CARRIER_Y);
        NavigationGrid grid = sim.getGrid();
        assertEquals(0, grid.getCoverAt(CARRIER_X, CARRIER_Y),
                "the arena is deliberately bare ground");

        placeScreen(sim, carrier, CARRIER_X, CARRIER_Y, Direction.E);

        int level = profile().coverLevel();
        assertEquals(level, grid.getCoverAtFacing(CARRIER_X, CARRIER_Y,
                        NavigationGrid.FACING_E),
                "the carrier is covered from the side the screen is on");
        for (int facing : new int[]{NavigationGrid.FACING_N, NavigationGrid.FACING_S,
                NavigationGrid.FACING_W}) {
            assertEquals(0, grid.getCoverAtFacing(CARRIER_X, CARRIER_Y, facing),
                    "a screen on one boundary must leave the other three open;"
                            + " cover from every direction is a durability bonus"
                            + " wearing a costume");
        }
        // A boundary belongs to both cells it joins. This is deliberate and
        // faction-blind: whoever crouches on the far side is behind it too.
        assertEquals(level, grid.getCoverAtFacing(CARRIER_X + 1, CARRIER_Y,
                        NavigationGrid.FACING_W),
                "the far side of the boundary is covered back the other way");
    }

    @Test
    void aHitFromTheCoveredFacingResolvesLessDamageThanTheSameHitFromAnUncoveredOne() {
        BattleSimulation sim = openArena(24, 24);
        long carrier = marine(sim, CARRIER_X, CARRIER_Y);
        long victim = standingTarget(sim, CARRIER_X, CARRIER_Y);
        long fromEast = shooter(sim, CARRIER_X + 6, CARRIER_Y);
        long fromSouth = shooter(sim, CARRIER_X, CARRIER_Y + 6);
        float requested = 100f;

        float bareEast = damageFrom(sim, victim, fromEast, requested);
        float bareSouth = damageFrom(sim, victim, fromSouth, requested);
        assertEquals(bareEast, bareSouth, EPS,
                "bare ground protects equally badly from every direction");

        placeScreen(sim, carrier, CARRIER_X, CARRIER_Y, Direction.E);

        float coveredEast = damageFrom(sim, victim, fromEast, requested);
        float stillBareSouth = damageFrom(sim, victim, fromSouth, requested);

        float reduction = DamageResolver.COVER_DAMAGE_REDUCTION[profile().coverLevel()];
        assertEquals(bareEast * (1f - reduction), coveredEast, EPS,
                "a hit crossing the screen resolves the authored cover reduction");
        assertTrue(coveredEast < bareEast,
                "the screen must measurably help against fire it faces");
        assertEquals(bareSouth, stillBareSouth, EPS,
                "and must do nothing at all against fire from behind it");
    }

    /**
     * The same measurement end to end, through real rifle fire rather than
     * through the damage seam alone. It is the one that would catch a screen
     * that publishes its cover somewhere the ballistic resolver never reads.
     */
    @Test
    void realFireIntoTheCoveredFacingLandsMeasurablyLessThanTheSameFireFromTheFlank() {
        float openGround = fireVolleyAndMeasure(false, /*fromFlank*/ false);
        float behindScreen = fireVolleyAndMeasure(true, /*fromFlank*/ false);
        float behindScreenButFlanked = fireVolleyAndMeasure(true, /*fromFlank*/ true);

        // Cover intercepts the round and then reduces what a surviving round
        // resolves, so the retained fraction is the product of the two — the
        // upper bound a single authored level can produce. Nothing here pins a
        // number; both figures come from the shipped tables.
        float level = profile().coverLevel();
        float retained = (1f - BallisticResolver.BLOCK_CHANCE_BY_LEVEL[(int) level])
                * (1f - DamageResolver.COVER_DAMAGE_REDUCTION[(int) level]);
        assertTrue(behindScreen < openGround * (1f + (1f - retained) / 2f),
                "fire into the screen must land measurably less than fire on bare"
                        + " ground: " + behindScreen + " vs " + openGround);
        assertTrue(behindScreen < behindScreenButFlanked,
                "the same screen must do nothing for fire that never crosses it: "
                        + behindScreen + " covered vs " + behindScreenButFlanked
                        + " flanked");
    }

    /**
     * Walks a fixed volley into one stationary body and returns the damage it
     * took. Practice targets rather than marines: a marine dies partway through
     * and rolls a fall-back that walks it off the boundary under measurement.
     */
    private static float fireVolleyAndMeasure(boolean screen, boolean fromFlank) {
        BattleSimulation sim = openArena(40, 40);
        long victim = standingTarget(sim, CARRIER_X, CARRIER_Y);
        long attacker = shooter(sim,
                fromFlank ? CARRIER_X : CARRIER_X + VOLLEY_RANGE_CELLS,
                fromFlank ? CARRIER_Y + VOLLEY_RANGE_CELLS : CARRIER_Y);
        if (screen) {
            sim.deployedCover().queuePlacement(victim, Faction.MARINE,
                    CARRIER_X, CARRIER_Y, Direction.E, spec());
            sim.advance(BattleSimulation.TICK_DT);
        }
        for (int tick = 0; tick < VOLLEY_TICKS; tick++) {
            if (tick % 4 == 0) {
                sim.world().setCooldownTimer(attacker, 0f);
                sim.fireShot(attacker, victim, FireStance.STANCED);
            }
            sim.advance(BattleSimulation.TICK_DT);
        }
        return sim.telemetry().damageTaken(victim);
    }

    @Test
    void placementLeavesMovementExactlyAsItFoundIt() {
        BattleSimulation sim = openArena(24, 24);
        long carrier = marine(sim, CARRIER_X, CARRIER_Y);
        NavigationGrid grid = sim.getGrid();

        placeScreen(sim, carrier, CARRIER_X, CARRIER_Y, Direction.E);

        assertTrue(grid.isWalkable(CARRIER_X, CARRIER_Y),
                "a marine may not be sealed onto the cell they built from");
        assertTrue(grid.isWalkable(CARRIER_X + 1, CARRIER_Y),
                "nor may the ground on the far side be taken away");
        assertTrue(grid.isSharedEdgePassable(CARRIER_X, CARRIER_Y, Direction.E),
                "the screen is cover, not a wall: its transition stays open, so"
                        + " no path across it is invalidated and the walkable"
                        + " graph cannot be partitioned by a placement");
        assertTrue(grid.canTraverseCellStep(CARRIER_X, CARRIER_Y, CARRIER_X + 1, CARRIER_Y),
                "a unit can still step straight over it");
        assertTrue(grid.canTraverseCellStep(CARRIER_X + 1, CARRIER_Y, CARRIER_X, CARRIER_Y),
                "in both directions");
    }

    @Test
    void theScreenExpiresAndLeavesTheBoundaryAsItWas() {
        BattleSimulation sim = openArena(24, 24);
        long carrier = marine(sim, CARRIER_X, CARRIER_Y);
        NavigationGrid grid = sim.getGrid();

        placeScreen(sim, carrier, CARRIER_X, CARRIER_Y, Direction.E);
        assertEquals(1, sim.deployedCover().activeScreens().size());
        assertNotNull(grid.getEdgeBarrier(CARRIER_X, CARRIER_Y, Direction.E));

        advanceSeconds(sim, spec().lifetimeSeconds() + 1f);

        assertTrue(sim.deployedCover().activeScreens().isEmpty(),
                "a deployable is bounded: it comes off the field on its own");
        assertNull(grid.getEdgeBarrier(CARRIER_X, CARRIER_Y, Direction.E),
                "and takes its identity with it");
        assertEquals(0, grid.getCoverAt(CARRIER_X, CARRIER_Y),
                "and its cover, leaving the cell exactly as bare as it was");
        assertTrue(grid.isSharedEdgePassable(CARRIER_X, CARRIER_Y, Direction.E));
        assertTrue(grid.isWalkable(CARRIER_X, CARRIER_Y));
    }

    @Test
    void breakingTheScreenTakesItsCoverWithIt() {
        BattleSimulation sim = openArena(24, 24);
        long carrier = marine(sim, CARRIER_X, CARRIER_Y);
        NavigationGrid grid = sim.getGrid();
        placeScreen(sim, carrier, CARRIER_X, CARRIER_Y, Direction.E);

        SharedEdgeBarrier screen = grid.getEdgeBarrier(CARRIER_X, CARRIER_Y, Direction.E);
        assertFalse(sim.damageEdgeBarrier(CARRIER_X, CARRIER_Y, Direction.E,
                        screen.maxStructure() - 1),
                "it has a real structure pool rather than dying to a scratch");
        assertTrue(sim.damageEdgeBarrier(CARRIER_X, CARRIER_Y, Direction.E, 1));

        assertEquals(0, grid.getCoverAt(CARRIER_X, CARRIER_Y),
                "a broken screen protects nobody");
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(sim.deployedCover().activeScreens().isEmpty(),
                "and the service drops a screen something else destroyed");
        assertTrue(grid.isSharedEdgePassable(CARRIER_X, CARRIER_Y, Direction.E));
    }

    @Test
    void aMarineCarryingNoScreenGetsNothing() {
        BattleSimulation sim = openArena(24, 24);
        sim.spawn(new EntitySpec("empty-handed", Faction.MARINE, UnitType.MARINE,
                CARRIER_X, CARRIER_Y).role(UnitRole.STRUCTURE));
        advanceSeconds(sim, 3f);

        assertTrue(sim.deployedCover().activeScreens().isEmpty());
        assertEquals(0, sim.getGrid().getCoverAt(CARRIER_X, CARRIER_Y),
                "carrying nothing changes nothing about the ground you stand on");
    }

    @Test
    void theCarrierBuildsFacingTheThreatItIsEngagingAndSpendsOne() {
        BattleSimulation sim = openArena(24, 24);
        long carrier = marine(sim, CARRIER_X, CARRIER_Y);
        long threat = shooter(sim, CARRIER_X, CARRIER_Y - 6);
        int carried = sim.world().secondaryAmmo(carrier);
        sim.combat().setFireIntent(carrier, threat, FireStance.STANCED, false);

        assertTrue(DeployableTactics.tryCommitCoverPlacement(carrier, revetment(), sim),
                "a settled marine under fire on bare ground builds");
        // The carrier is deliberately inert so its plan cannot wander off
        // mid-measurement, which also means the GOAP dispatcher never reaches
        // it. Drive the channel the way that dispatcher would, so the executor
        // under test is the production one.
        for (int guard = 0; guard < 1_000
                && sim.world().secondaryActionTimer(carrier) > 0f; guard++) {
            DeployableTactics.tickPlacement(carrier, revetment(), sim);
        }
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(carried - 1, sim.world().secondaryAmmo(carrier),
                "a placement spends hardware the marine physically carried");
        assertEquals(1, sim.deployedCover().activeScreens().size());
        assertEquals(profile().coverLevel(),
                sim.getGrid().getCoverAtFacing(CARRIER_X, CARRIER_Y, NavigationGrid.FACING_N),
                "the screen faces the threat the marine was already shooting at");
    }

    @Test
    void theCarrierDeclinesGroundThatIsAlreadyCovered() {
        BattleSimulation sim = openArena(24, 24);
        long carrier = marine(sim, CARRIER_X, CARRIER_Y);
        long threat = shooter(sim, CARRIER_X, CARRIER_Y - 6);
        sim.combat().setFireIntent(carrier, threat, FireStance.STANCED, false);
        placeScreen(sim, carrier, CARRIER_X, CARRIER_Y, Direction.S);

        assertFalse(DeployableTactics.tryCommitCoverPlacement(carrier, revetment(), sim),
                "cover you already have is not worth a second piece of hardware");
    }

    @Test
    void runtimeConstructionRefusesAProfileThatWouldCloseATransition() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        MapEditor editor = new MapEditor(new NavigationService(grid, new CellTopology(8, 8)));

        assertThrows(IllegalArgumentException.class,
                () -> editor.placeDeployedBarrier(2, 2, Direction.E,
                        SharedEdgeBarrier.Kind.WINDOW),
                "runtime construction that closes a transition could strand a"
                        + " unit or cut the map in two; the one construction"
                        + " seam refuses it outright");
        assertNotNull(editor.placeDeployedBarrier(2, 2, Direction.E,
                        SharedEdgeBarrier.Kind.REVETMENT),
                "a profile that leaves movement alone is admitted");
    }

    @Test
    void aRuntimePlacementOnAnIllegalBoundaryDeclinesRatherThanThrowing() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        grid.setWalkable(4, 2, false);
        MapEditor editor = new MapEditor(new NavigationService(grid, new CellTopology(8, 8)));

        assertNull(editor.placeDeployedBarrier(3, 2, Direction.E,
                        SharedEdgeBarrier.Kind.REVETMENT),
                "a boundary with a wall on one side takes no screen");
        assertNull(editor.placeDeployedBarrier(7, 2, Direction.E,
                        SharedEdgeBarrier.Kind.REVETMENT),
                "nor does the map edge");
        assertNotNull(editor.placeDeployedBarrier(1, 2, Direction.E,
                SharedEdgeBarrier.Kind.REVETMENT));
        assertNull(editor.placeDeployedBarrier(1, 2, Direction.E,
                        SharedEdgeBarrier.Kind.REVETMENT),
                "and a boundary somebody already built on takes no second one");
    }

    @Test
    void theCatalogRefusesACoverItemNamingAMovementBlockingProfile() throws JSONException {
        JSONObject json = new JSONObject(coverItemJson("window"));
        JSONException failure = assertThrows(JSONException.class,
                () -> SpecialEquipmentDef.parse(json));
        assertTrue(failure.getMessage().contains("closes its navigation transition"),
                "the law is enforced at load, not discovered mid-battle: " + failure);
        // The same entry with a legal profile parses, so the rejection is about
        // the profile rather than about the shape of the entry.
        assertNotNull(SpecialEquipmentDef.parse(new JSONObject(coverItemJson("revetment"))));
    }

    private static String coverItemJson(String barrierKind) {
        return "{\"id\":\"special.test-screen\","
                + "\"catalog\":{\"displayName\":\"Test Screen\",\"subtitle\":\"s\","
                + "\"description\":\"d\"},"
                + "\"activation\":{\"type\":\"utility-deployable\",\"barrierKind\":\""
                + barrierKind + "\",\"deployDuration\":1.0,\"lifetimeSeconds\":10.0},"
                + "\"resource\":{\"mode\":\"ammunition\",\"startingAmmo\":1},"
                + "\"ai\":{\"policy\":\"directional-cover-screen\"},"
                + "\"presentation\":{\"armoryIcon\":\"graphics/ui/armory/special-field-revetment.png\","
                + "\"usePose\":\"plant\","
                + "\"carrierLayer\":{\"sprite\":\"graphics/ui/armory/special-field-revetment.png\","
                + "\"widthShoulders\":0.3,\"heightShoulders\":0.3,\"pivot\":[0.5,0.5],"
                + "\"carried\":{\"offsetShoulders\":[0.0,0.0],\"angleDegrees\":0.0,"
                + "\"occlusion\":\"under-body\"},"
                + "\"using\":{\"offsetShoulders\":[0.0,0.0],\"angleDegrees\":0.0,"
                + "\"occlusion\":\"over-body\"}}}}";
    }
}
