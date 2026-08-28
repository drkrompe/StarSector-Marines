package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.SpecialAiPolicy;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a running sensor sweep does to <b>the player's picture</b>, asked of the
 * one thing that owns that picture.
 *
 * <p>Every assertion here reads {@link FogOfWarService} — the reveal bitmap the
 * fog pass paints from and the per-unit visibility byte the unit pass gates on.
 * That is deliberate and is the point of the file. The standing rule the story
 * carries is that a sensor system feeding the AI better information while
 * showing the player nothing is a hidden modifier, and a sweep is unusually easy
 * to build that way by accident. An implementation that widened a decision
 * layer's reach — a longer {@code visionRange} for targeting, a seeded contact
 * picture, a wider threat query — would leave every assertion below failing,
 * because none of them can be satisfied by anything except the player's own
 * reveal composition.
 *
 * <p>The scene is one wall column, one wearer, one hostile behind the wall, and
 * a second stationary marine as a stable reference contributor. The wearer's
 * ordinary sight already reaches most of the map, so the two cells under test
 * are chosen for being the two things a sweep is supposed to add: ground
 * further out than the suit's own eyes reach, and ground behind a wall those
 * eyes stop at.
 */
class PerceptionSweepRevealTest {

    private static final int W = 100;
    private static final int H = 25;
    private static final int ROW = 12;

    private static final int WEARER_X = 50;
    private static final int WALL_X = 54;
    /** Behind the wall, close enough that a bounded wall read still carries. */
    private static final int BEHIND_WALL_X = 58;
    /**
     * Further west than a marine's own eyes reach, so it is dark before the
     * sweep for a reason no wall is responsible for. Derived from the unit's
     * authored sight rather than picked, so retuning that stat cannot quietly
     * turn this cell into one the wearer already saw.
     */
    private static final int FAR_WEST_X =
            WEARER_X - (int) UnitType.MARINE.visionRange - 4;

    /** A read wide enough to reach {@link #FAR_WEST_X}, and through the wall at four cells. */
    private static final float TEST_RANGE = UnitType.MARINE.visionRange + 8f;
    private static final float TEST_WALL_READ = 6f;

    private static final float DURATION = 2f;
    private static final float COOLDOWN = 30f;

    /** Three sim ticks per vision tick, so this comfortably outruns the cadence. */
    private static final int VISION_SETTLE_TICKS = 9;

    // ------------------------------------------------------------------ reveal

    /**
     * The acceptance, in one scene: cells the wearer could not otherwise see are
     * open while the sweep runs, and closed again when it expires.
     */
    @Test
    void aSweepOpensGroundTheWearerCannotSeeAndCloudsItAgainOnExpiry() {
        Scene scene = scene(TEST_RANGE, TEST_WALL_READ);

        assertFalse(scene.fog.isCellRevealed(BEHIND_WALL_X, ROW),
                "before the sweep, the wall is doing its job");
        assertFalse(scene.fog.isCellRevealed(FAR_WEST_X, ROW),
                "before the sweep, ground past the wearer's own sight is dark");

        scene.startSweep();
        assertTrue(scene.fog.isCellRevealed(BEHIND_WALL_X, ROW),
                "a running sweep reads through a wall it is standing next to");
        assertTrue(scene.fog.isCellRevealed(FAR_WEST_X, ROW),
                "and reads further out than the suit's own eyes do");

        scene.runOutTheSweep();
        assertFalse(scene.fog.isCellRevealed(BEHIND_WALL_X, ROW),
                "the wall is doing its job again");
        assertFalse(scene.fog.isCellRevealed(FAR_WEST_X, ROW),
                "and the far ground is dark again");
    }

    /**
     * The reveal reaches the player's picture rather than only the simulation's.
     * A hostile is a live unit either way; whether the player is shown it is a
     * decision {@link FogOfWarService} makes and the renderer obeys, so this is
     * the assertion an AI-only implementation cannot pass.
     */
    @Test
    void aSweepPutsWhatItFindsIntoThePlayersOwnUnitPicture() {
        Scene scene = scene(TEST_RANGE, TEST_WALL_READ);

        assertEquals(FogOfWarService.VIS_HIDDEN, scene.hostileVisibility(),
                "a hostile behind a wall is not in the player's picture");

        scene.startSweep();
        assertEquals(FogOfWarService.VIS_VISIBLE, scene.hostileVisibility(),
                "the sweep found it, so the player is shown it");

        scene.runOutTheSweep();
        assertNotEquals(FogOfWarService.VIS_VISIBLE, scene.hostileVisibility(),
                "and stops being shown it when the window closes; the renderer"
                        + " decays the fade from here on real frame time");
    }

    // ----------------------------------------------------------- no concealment

    /**
     * A sweep only ever adds. Concealment belongs to the recon role behind its
     * own contract, and the cheapest accidental route to it is a reveal path
     * that recomputes rather than contributes — so this pins that every cell
     * open before the sweep is still open during it, and that the wearer's own
     * visibility never moves.
     */
    @Test
    void aSweepNeverTakesAnythingOutOfThePicture() {
        Scene scene = scene(TEST_RANGE, TEST_WALL_READ);
        boolean[] before = scene.revealedSnapshot();
        byte wearerBefore = scene.visibilityOf(scene.wearer);

        scene.startSweep();
        boolean[] during = scene.revealedSnapshot();
        for (int i = 0; i < before.length; i++) {
            if (before[i]) {
                assertTrue(during[i], "a sweep must not close a cell that was already open");
            }
        }
        assertEquals(wearerBefore, scene.visibilityOf(scene.wearer),
                "a sweep is not a concealment suite: the wearer is exactly as visible as before");
    }

    // ------------------------------------------------------------- no residue

    /**
     * The reference count returns to exactly what it was, three times over. The
     * boolean view alone cannot see a leak — a decrement that undershot is
     * masked by any other source holding the cell, and one that overshot is
     * clamped at zero — so this reads the count itself.
     */
    @Test
    void aSweepReleasesExactlyWhatItTookEveryTime() {
        Scene scene = scene(TEST_RANGE, TEST_WALL_READ);
        boolean[] baseline = scene.revealedSnapshot();
        int[] countsBefore = scene.countsSnapshot();

        for (int cycle = 0; cycle < 3; cycle++) {
            scene.startSweep();
            assertTrue(scene.fog.isCellRevealed(BEHIND_WALL_X, ROW),
                    "cycle " + cycle + " should still open the room");
            scene.runOutTheSweep();
            scene.readyTheSweepAgain();
            assertArrayEquals(baseline, scene.revealedSnapshot(),
                    "cycle " + cycle + " left the reveal changed");
            assertArrayEquals(countsBefore, scene.countsSnapshot(),
                    "cycle " + cycle + " left a reference behind");
        }
    }

    /**
     * A wearer killed mid-sweep takes the reveal with them. The system component
     * is live-only, so there is no running sweep left to republish and the
     * temporary source simply is not in the next rebuild — which is the whole
     * reason the set is replaced rather than expired individually.
     */
    @Test
    void aWearerKilledMidSweepLeavesNoRevealBehind() {
        Scene scene = scene(TEST_RANGE, TEST_WALL_READ);
        scene.startSweep();
        assertTrue(scene.fog.isCellRevealed(BEHIND_WALL_X, ROW));

        scene.killWearer();
        scene.settleVision();
        assertEquals(0, scene.fog.revealCountAt(BEHIND_WALL_X, ROW),
                "the sweep's reveal must not outlive the suit running it");
        assertEquals(0, scene.fog.revealCountAt(FAR_WEST_X, ROW),
                "nor any other cell only that sweep reached");
    }

    // -------------------------------------------------------- authored numbers

    /**
     * The authored numbers govern, and they govern separately. A narrower wall
     * read leaves the same room shut with the same range; a shorter range
     * leaves the same far ground dark with the same wall read. Neither scene
     * changes in any other respect.
     */
    @Test
    void theAuthoredReadAndWallToleranceEachDecideTheirOwnHalf() {
        Scene narrowWallRead = scene(TEST_RANGE, 2f);
        narrowWallRead.startSweep();
        assertFalse(narrowWallRead.fog.isCellRevealed(BEHIND_WALL_X, ROW),
                "a wall read that does not reach the wall does not read through it");
        assertTrue(narrowWallRead.fog.isCellRevealed(FAR_WEST_X, ROW),
                "while the same authored range still reaches the far ground");

        Scene shortRange = scene(UnitType.MARINE.visionRange - 4f, TEST_WALL_READ);
        shortRange.startSweep();
        assertTrue(shortRange.fog.isCellRevealed(BEHIND_WALL_X, ROW),
                "a short read still opens the room it is standing against");
        assertFalse(shortRange.fog.isCellRevealed(FAR_WEST_X, ROW),
                "but cannot reach ground outside its authored range");
    }

    // ------------------------------------------------------------------ scene

    /** One wearer, one wall, one hostile behind it, and a reference contributor. */
    private static final class Scene {
        final BattleSimulation sim;
        final FogOfWarService fog;
        final long wearer;
        final long hostile;
        final float duration;

        Scene(BattleSimulation sim, long wearer, long hostile, float duration) {
            this.sim = sim;
            this.fog = sim.getFogOfWar();
            this.wearer = wearer;
            this.hostile = hostile;
            this.duration = duration;
        }

        /** Spends the system directly, then lets the vision cadence catch up. */
        void startSweep() {
            assertTrue(sim.integralSystems().activate(wearer), "the suit should be ready");
            settleVision();
        }

        void runOutTheSweep() {
            advance((int) Math.ceil(duration / BattleSimulation.TICK_DT) + VISION_SETTLE_TICKS);
        }

        /** Drains the rest of the cooldown so the next cycle can spend it again. */
        void readyTheSweepAgain() {
            while (sim.integralSystems().cooldownRemaining(wearer) > 0f) {
                advance(30);
            }
        }

        void settleVision() {
            advance(VISION_SETTLE_TICKS);
        }

        void advance(int ticks) {
            for (int i = 0; i < ticks; i++) sim.advance(BattleSimulation.TICK_DT);
        }

        void killWearer() {
            sim.applyDamage(wearer, Float.MAX_VALUE, 1_000f);
        }

        byte hostileVisibility() {
            return visibilityOf(hostile);
        }

        byte visibilityOf(long unit) {
            UnitRosterService roster = sim.getRoster();
            for (int i = 0, n = roster.liveCount(); i < n; i++) {
                if (roster.get(i) == unit) return fog.getUnitVisibility(i);
            }
            throw new AssertionError("unit is no longer live");
        }

        boolean[] revealedSnapshot() {
            return Arrays.copyOf(fog.cellRevealedArray(), W * H);
        }

        int[] countsSnapshot() {
            int[] counts = new int[W * H];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) counts[y * W + x] = fog.revealCountAt(x, y);
            }
            return counts;
        }
    }

    private static Scene scene(float revealRangeCells, float wallReadRadiusCells) {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                // A fresh grid is solid; a column left unset is the wall.
                if (x != WALL_X) grid.setWalkableFloor(x, y);
            }
        }
        BattleSimulation sim = new BattleSimulation(grid,
                new CellTopology(W, H), 20260828L);
        sim.setMissionCompletionEnabled(false);

        long wearer = sim.spawn(new EntitySpec("wearer", Faction.MARINE, UnitType.MARINE,
                WEARER_X, ROW)
                .moveSpeed(0f)
                .integralSystem(sweepDef(revealRangeCells, wallReadRadiusCells)));
        // A second contributor that never moves and never sweeps, so the
        // reference-count assertions have something stable underneath them.
        sim.spawn(new EntitySpec("reference", Faction.MARINE, UnitType.MARINE,
                WEARER_X - 2, ROW).moveSpeed(0f));
        long hostile = sim.spawn(new EntitySpec("hostile", Faction.DEFENDER, UnitType.MILITIA,
                BEHIND_WALL_X, ROW).moveSpeed(0f));

        Scene scene = new Scene(sim, wearer, hostile, DURATION);
        scene.settleVision();
        return scene;
    }

    private static IntegralSystemDef sweepDef(float revealRangeCells, float wallReadRadiusCells) {
        try {
            return IntegralSystemDef.parse(new JSONObject()
                    .put("id", "system.test-sweep")
                    .put("grade", "milspec")
                    .put("displayName", "Test sweep")
                    .put("description", "One wide active return.")
                    .put("effect", "perception-sweep")
                    .put("resource", "cooldown")
                    .put("policy", SpecialAiPolicy.APPROACHING_DEAD_GROUND.key)
                    .put("lookaheadCells", 7.0)
                    .put("durationSeconds", DURATION)
                    .put("cooldownSeconds", COOLDOWN)
                    .put("revealRangeCells", revealRangeCells)
                    .put("wallReadRadiusCells", wallReadRadiusCells), "armor.test");
        } catch (JSONException failure) {
            throw new AssertionError("test fixture should parse", failure);
        }
    }
}
