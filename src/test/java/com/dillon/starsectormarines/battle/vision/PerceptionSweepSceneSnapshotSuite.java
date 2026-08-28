package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.marine.IntegralSystemDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.ops.battleview.PlayerViewFrameRenderer;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Animated evidence for the Janus sensor sweep, recorded through the
 * <b>player's</b> camera rather than a neutral observer's.
 *
 * <p>The claim a sweep makes is a visual one — that the player learns something
 * for a few seconds and then stops knowing it — so the artifact has to be the
 * player's own picture, fog overlay and hidden-unit gating included. Every
 * frame here is drawn by {@link PlayerViewFrameRenderer}, whose markers come
 * straight off the fog service's per-unit visibility byte. A hostile that
 * appears in one of these frames is a hostile the shipped renderer would draw.
 *
 * <p>The scene is a sealed block of rooms with a scout standing outside its west
 * wall. Nothing has a door, so ordinary sight cannot enter at all, and the two
 * things worth watching are both boundaries:
 * <ul>
 *   <li>the sweep opens the <b>near</b> room band, whose wall is inside the
 *       authored wall read, and stops at the next internal wall, which is not —
 *       so the two defenders deeper in the block stay dark the whole time;</li>
 *   <li>the whole reveal lapses when the window closes, and the picture is
 *       exactly the picture it was before.</li>
 * </ul>
 *
 * <p>The activation is fired on a fixed tick rather than through the suit's own
 * dead-ground policy, so the recording is the same recording every run — the
 * policy has its own unit coverage. Everything downstream of that tick is
 * production code: the real shadowcast, the real reference count, the real fog
 * composition, and the real renderer.
 */
public final class PerceptionSweepSceneSnapshotSuite implements SnapshotSuite {

    private static final long SEED = 20260828L;

    private static final int W = 56;
    private static final int H = 34;

    /** The sealed block: outer wall, then internal partitions splitting it into bands. */
    private static final int BLOCK_X0 = 26;
    private static final int BLOCK_X1 = 48;
    private static final int BLOCK_Y0 = 6;
    private static final int BLOCK_Y1 = 28;
    private static final int[] PARTITION_X = {32, 38, 44};

    private static final int SCOUT_X = 22;
    private static final int SCOUT_Y = 17;

    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 440;

    private static final int TICKS = 300;
    private static final int ACTIVATE_TICK = 90;
    private static final int FRAME_EVERY_TICKS = 6;
    private static final int FRAME_DELAY_MILLIS = 90;

    /** Sampled for the still triptych: settled before, mid-window, and well after. */
    private static final int STILL_BEFORE_TICK = 84;
    private static final int STILL_DURING_TICK = 132;
    private static final int STILL_AFTER_TICK = 270;

    @Override public String id() { return "perception-sweep"; }

    @Override public String label() {
        return "Sensor sweep: what the player is shown, and stops being shown";
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        PlayerViewFrameRenderer renderer =
                new PlayerViewFrameRenderer(context.modRoot(), FRAME_WIDTH, FRAME_HEIGHT);
        IntegralSystemDef sweep = shippedSweep();

        BattleSimulation sim = scene(sweep);
        long scout = firstMarine(sim);

        List<BufferedImage> frames = new ArrayList<>(TICKS / FRAME_EVERY_TICKS + 1);
        BufferedImage before = null;
        BufferedImage during = null;
        BufferedImage after = null;
        for (int tick = 0; tick <= TICKS; tick++) {
            if (tick == ACTIVATE_TICK) sim.integralSystems().activate(scout);
            if (tick % FRAME_EVERY_TICKS == 0) {
                frames.add(renderer.render(sim, caption(sim, scout, sweep, tick)));
            }
            if (tick == STILL_BEFORE_TICK) {
                before = renderer.render(sim, caption(sim, scout, sweep, tick));
            }
            if (tick == STILL_DURING_TICK) {
                during = renderer.render(sim, caption(sim, scout, sweep, tick));
            }
            if (tick == STILL_AFTER_TICK) {
                after = renderer.render(sim, caption(sim, scout, sweep, tick));
            }
            sim.advance(BattleSimulation.TICK_DT);
            // A host advances the fade on real frame time; without it a unit
            // that left sight would sit at full alpha forever and the "after"
            // frame would still show what the sweep found.
            sim.getFogOfWar().advanceFade(BattleSimulation.TICK_DT);
        }

        return List.of(
                SnapshotArtifact.animation("aperture-read.gif", frames, FRAME_DELAY_MILLIS),
                new SnapshotArtifact("aperture-read-before-during-after.png",
                        triptych(before, during, after)));
    }

    /**
     * The shipped Janus system, not a fixture. The whole point of this artifact
     * is what the authored numbers actually look like on a map, so inventing
     * numbers for it would make it evidence about nothing.
     */
    private static IntegralSystemDef shippedSweep() {
        MarineArmorCatalogDef janus = MarineArmorCatalogRegistry.require("armor.scout");
        IntegralSystemDef system = janus.integralSystem();
        if (system == null || system.perceptionSweep() == null) {
            throw new IllegalStateException(
                    "armor.scout no longer carries a sensor sweep; this suite has nothing to show");
        }
        return system;
    }

    /**
     * Tick, the state of the window, and how many hostiles the player can
     * presently see. The count is read off the fog service rather than off the
     * roster, so it is the number of red markers in the frame rather than the
     * number of defenders alive.
     */
    private static String caption(BattleSimulation sim, long scout,
                                  IntegralSystemDef sweep, int tick) {
        float remaining = sim.integralSystems().activeRemaining(scout);
        String state = remaining > 0f
                ? String.format(Locale.ROOT, "sweep running, %.1fs left", remaining)
                : (tick < ACTIVATE_TICK ? "ordinary sight only" : "sweep expired");
        return String.format(Locale.ROOT,
                "t%-4d  %s  |  %.0f-cell read, %.0f through walls  |  hostiles in your picture: %d",
                tick, state, sweep.perceptionSweep().revealRangeCells(),
                sweep.perceptionSweep().wallReadRadiusCells(), visibleHostiles(sim));
    }

    private static int visibleHostiles(BattleSimulation sim) {
        FogOfWarService fog = sim.getFogOfWar();
        int seen = 0;
        for (int index = 0; index < sim.getRoster().liveCount(); index++) {
            if (fog.getUnitVisibility(index) != FogOfWarService.VIS_VISIBLE) continue;
            if (sim.identity().faction(sim.getRoster().get(index)) == Faction.DEFENDER) seen++;
        }
        return seen;
    }

    /** The three states side by side, for a reader who wants one file rather than a loop. */
    private static BufferedImage triptych(BufferedImage before, BufferedImage during,
                                          BufferedImage after) {
        int gap = 8;
        BufferedImage sheet = new BufferedImage(
                before.getWidth() * 3 + gap * 2, before.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = sheet.createGraphics();
        graphics.setColor(new Color(16, 18, 22));
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        graphics.drawImage(before, 0, 0, null);
        graphics.drawImage(during, before.getWidth() + gap, 0, null);
        graphics.drawImage(after, (before.getWidth() + gap) * 2, 0, null);
        graphics.dispose();
        return sheet;
    }

    private static long firstMarine(BattleSimulation sim) {
        for (int index = 0; index < sim.getRoster().liveCount(); index++) {
            long unit = sim.getRoster().get(index);
            if (sim.identity().faction(unit) == Faction.MARINE) return unit;
        }
        throw new IllegalStateException("the scene spawns a scout");
    }

    /**
     * A sealed block of rooms and one scout outside it. Nothing has a door,
     * because the artifact is about a wall read and a door would give ordinary
     * sight a way in that muddles which of the two opened the room.
     */
    private static BattleSimulation scene(IntegralSystemDef sweep) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, inBlock(x, y)
                        ? CellTopology.GroundKind.INDOOR
                        : CellTopology.GroundKind.STREET);
            }
        }
        for (int x = BLOCK_X0; x <= BLOCK_X1; x++) {
            wall(grid, topology, x, BLOCK_Y0);
            wall(grid, topology, x, BLOCK_Y1);
        }
        for (int y = BLOCK_Y0; y <= BLOCK_Y1; y++) {
            wall(grid, topology, BLOCK_X0, y);
            wall(grid, topology, BLOCK_X1, y);
            for (int partition : PARTITION_X) wall(grid, topology, partition, y);
        }

        BattleSimulation sim = new BattleSimulation(grid, topology, SEED);
        sim.setMissionCompletionEnabled(false);

        sim.spawn(new EntitySpec("janus-scout", Faction.MARINE, UnitType.MARINE,
                SCOUT_X, SCOUT_Y)
                .role(UnitRole.STRUCTURE)
                .moveSpeed(0f)
                .integralSystem(sweep));

        // Two in the near band, whose wall is inside the authored read, and two
        // deeper in, whose wall is not. The second pair is the control: a sweep
        // that lit them would be an x-ray rather than a bounded read.
        for (int[] cell : new int[][] {{29, 13}, {29, 21}, {35, 17}, {41, 17}}) {
            sim.spawn(new EntitySpec("defender-" + cell[0] + "-" + cell[1],
                    Faction.DEFENDER, UnitType.MILITIA, cell[0], cell[1])
                    .role(UnitRole.STRUCTURE)
                    .moveSpeed(0f));
        }
        return sim;
    }

    private static boolean inBlock(int x, int y) {
        return x >= BLOCK_X0 && x <= BLOCK_X1 && y >= BLOCK_Y0 && y <= BLOCK_Y1;
    }

    private static void wall(NavigationGrid grid, CellTopology topology, int x, int y) {
        grid.setWalkable(x, y, false);
        topology.setWall(x, y, true);
        topology.setGroundKind(x, y, CellTopology.GroundKind.INDOOR);
    }
}
