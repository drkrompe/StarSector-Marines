package com.dillon.starsectormarines.battle.derelict;

import com.dillon.starsectormarines.battle.appearance.FacingSystem;
import com.dillon.starsectormarines.battle.mech.FactionMechLoadouts;
import com.dillon.starsectormarines.battle.mech.MechSpawnPlacement;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.scene.LookReview;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The derelict security walker's look and gait (`derelict-security-network.md`, The walker's legs), in the vast
 * ruin's own halls with the roof lifted. Its route is a straight line through the site that runs both down a narrow
 * hall and across open floor, so its legs show both grips. Stills: the walker at the route's start beside a company
 * Bulwark for scale and style, and its body close. Recordings, the camera close on the legs: a walk and a run along
 * the route, the pace scripted a step each tick (the network keeps its walkers at their posts). Candidate art is shown
 * through {@code -Dsnapshot.artOverlay}. Opt-in. No outcome is judged.
 */
public final class DerelictWalkerLookSuite implements SnapshotSuite {

    static final String VAST_FIXTURE = DerelictHeaviesLookSuite.VAST_FIXTURE;
    static final int SETTLE_TICKS = 120;
    static final float STILL_CELLS = 16f;
    static final float BODY_CELLS = 7f;
    static final float FOLLOW_CELLS = 10f;
    /** The route's length, cells: the run covers it, the walk the first part. */
    static final int ROUTE_CELLS = 22;
    static final int WALK_CELLS = 13;
    static final int WALK_TICKS = 600;
    static final int RUN_TICKS = 260;
    static final int BULWARK_AHEAD_CELLS = 5;

    @Override public String id() { return "derelict-walker-look"; }

    @Override public String label() { return "The derelict security walker in the ruin's halls: look, walk and run"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(VAST_FIXTURE),
                    StandardCharsets.UTF_8));
            List<SnapshotArtifact> out = new ArrayList<>();
            Staged still = stage(fixture);
            if (still == null) return out;
            int[] r = still.route;
            System.out.println("    [look/derelict-walker] route " + r[0] + "," + r[1] + " along " + r[2] + ","
                    + r[3]);
            float bodyX = still.sim.world().renderX(still.walker);
            float bodyY = still.sim.world().renderY(still.walker);
            out.add(new SnapshotArtifact("derelict-walker-body.png", BattleReviewFrameRenderer.look(
                    context.modRoot(), 1920, 1080, bodyX, bodyY, BODY_CELLS).withoutRoofs()
                    .render(still.sim, "derelict-walker-body")));
            bulwark(still.sim, r[0] + r[2] * BULWARK_AHEAD_CELLS, r[1] + r[3] * BULWARK_AHEAD_CELLS);
            out.add(new SnapshotArtifact("derelict-walker-hall.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, r[0] + 0.5f + r[2] * BULWARK_AHEAD_CELLS / 2f,
                    r[1] + 0.5f + r[3] * BULWARK_AHEAD_CELLS / 2f, STILL_CELLS).withoutRoofs()
                    .render(still.sim, "derelict-walker-hall")));
            out.addAll(recording(context, fixture, "derelict-walker-walk", WALK_CELLS, WALK_TICKS));
            out.addAll(recording(context, fixture, "derelict-walker-run", ROUTE_CELLS, RUN_TICKS));
            return out;
        }
    }

    private static List<SnapshotArtifact> recording(SnapshotContext context, JSONObject fixture, String name,
                                                    int cells, int ticks) throws Exception {
        long[] walker = {0L};
        return LookReview.record(context.modRoot(), new LookReview.Shot(name, LookGround.verdant(), 40, 30,
                placement -> walking(fixture, walker, placement, cells, ticks), placement -> List.of(follow(walker)),
                ticks, 1, 1, new int[] {ticks / 2}));
    }

    /** The settled site with its vault frame stood at the start of the route; null when it has no walker or route. */
    static Staged stage(JSONObject fixture) throws Exception {
        BattleSimulation sim = HeadlessBattles.fixture(fixture);
        while (sim.getSimTickIndex() < SETTLE_TICKS) {
            int before = sim.getSimTickIndex();
            sim.advance(BattleSimulation.TICK_DT);
            if (sim.getSimTickIndex() == before) break;
        }
        DerelictSecurityDirector director = sim.derelictSecurity();
        if (director == null || director.walkers().isEmpty()) return null;
        DerelictSecurityDirector.Walker w = director.walkers().get(0);
        if (sim.squadMemberCount(w.squadId()) == 0) return null;
        long walker = sim.squadMemberAt(w.squadId(), 0);
        int[] route = route(sim.getGrid(), sim.world().cellX(walker), sim.world().cellY(walker));
        if (route == null) return null;
        sim.world().setPos(walker, route[0] + 0.5f, route[1] + 0.5f);
        FacingSystem.faceStanding(sim.getEntityWorld(), sim.getBattleComponents(), walker, heading(route));
        return new Staged(sim, walker, route);
    }

    record Staged(BattleSimulation sim, long walker, int[] route) {
    }

    /** The sprite heading down the route. */
    static float heading(int[] route) {
        return (float) Math.toDegrees(Math.atan2(-route[2], route[3]));
    }

    /** The staged site with the walker moved {@code cells} down its route over {@code ticks}, a step each tick. */
    static BattleSimulation walking(JSONObject fixture, long[] walker, LookReview.Placement placement, int cells,
                                    int ticks) {
        Staged staged;
        try {
            staged = stage(fixture);
        } catch (Exception e) {
            throw new IllegalStateException("derelict fixture", e);
        }
        if (staged == null) throw new IllegalStateException("no walker or route in the vast site");
        walker[0] = staged.walker;
        int[] r = staged.route;
        float heading = heading(r);
        float step = cells / (float) ticks;
        int[] tick = {0};
        placement.beforeTick(sim -> {
            int t = Math.min(ticks, tick[0]++);
            sim.world().setPos(staged.walker, r[0] + 0.5f + r[2] * step * t, r[1] + 0.5f + r[3] * step * t);
            FacingSystem.faceStanding(sim.getEntityWorld(), sim.getBattleComponents(), staged.walker, heading);
        });
        return staged.sim;
    }

    /** A camera close on the walker. */
    static LookReview.View follow(long[] walker) {
        return new LookReview.View("follow", sim -> new float[] {sim.world().renderX(walker[0]),
                sim.world().renderY(walker[0]), FOLLOW_CELLS}).withoutRoofs();
    }

    /**
     * A straight route of {@link #ROUTE_CELLS} open cells (each with a cell clear either side) near (cx, cy) that
     * runs both down a narrow hall (walls within two cells on both sides) and across open floor (none within three),
     * as {x, y, stepX, stepY}; the one with the most of the rarer kind, nearest first. Null when the site has none.
     */
    static int[] route(NavigationGrid grid, int cx, int cy) {
        int[] best = null;
        long bestScore = Long.MIN_VALUE;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                for (int[] s : steps) {
                    int narrow = 0;
                    int wide = 0;
                    boolean clear = true;
                    for (int k = 0; k < ROUTE_CELLS && clear; k++) {
                        int px = x + s[0] * k;
                        int py = y + s[1] * k;
                        int left = open(grid, px, py, -s[1], s[0], 4);
                        int right = open(grid, px, py, s[1], -s[0], 4);
                        if (!walkable(grid, px, py) || left < 1 || right < 1) clear = false;
                        else if (left <= 1 && right <= 1) narrow++;
                        else if (left >= 3 && right >= 3) wide++;
                    }
                    if (!clear || narrow < 4 || wide < 4) continue;
                    long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy);
                    long score = Math.min(narrow, wide) * 100_000L - d;
                    if (score > bestScore) {
                        bestScore = score;
                        best = new int[] {x, y, s[0], s[1]};
                    }
                }
            }
        }
        return best;
    }

    /** Open cells beside (x, y) in the direction (dx, dy), up to {@code max}. */
    private static int open(NavigationGrid grid, int x, int y, int dx, int dy, int max) {
        int n = 0;
        while (n < max && walkable(grid, x + dx * (n + 1), y + dy * (n + 1))) n++;
        return n;
    }

    private static boolean walkable(NavigationGrid grid, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y);
    }

    /** A company Bulwark set down on standable ground near (x, y), for scale and style; 0 when none stands. */
    static long bulwark(BattleSimulation sim, int x, int y) {
        MechSpawnPlacement.Point at = null;
        for (int r = 0; at == null && r <= 6; r++)
            for (int dy = -r; at == null && dy <= r; dy++) for (int dx = -r; at == null && dx <= r; dx++)
                if (Math.max(Math.abs(dx), Math.abs(dy)) == r)
                    at = MechSpawnPlacement.nearCell(sim.getGrid(), MechVariant.BULWARK.radius, x + dx, y + dy,
                            (px, py) -> true, (px, py) -> true);
        if (at == null) return 0L;
        int squad = sim.mintSquad(Faction.MARINE, UnitType.HEAVY_MECH);
        long mech = sim.spawn(new EntitySpec("Scale Bulwark", Faction.MARINE, UnitType.HEAVY_MECH, at.cellX(),
                at.cellY()).atPosition(at.x(), at.y()).mechVariant(MechVariant.BULWARK).squad(squad));
        sim.world().attachMechLoadout(mech, FactionMechLoadouts.create(MechVariant.BULWARK,
                MechVariant.BULWARK.defaultRole, "independent"));
        Squad s = sim.getSquad(squad);
        s.leaderId = mech;
        s.originalSize = 1;
        return mech;
    }
}
