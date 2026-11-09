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
 * The derelict security walker's look and legs (`derelict-security-network.md`, The walker's legs), in the vast
 * ruin's own halls: the vault frame stood at one end of the longest straight hall three cells wide, roof lifted. A
 * still with a company Bulwark set down beside it for scale and style, then a recording of the walker walking the
 * hall, the camera following it. Candidate art is shown through {@code -Dsnapshot.artOverlay}; the leg switches
 * ({@code -Dbattle.derelictWalker.legs}, {@code -Dbattle.derelictWalker.wallReach}) choose the rig. Opt-in. No outcome
 * is judged.
 */
public final class DerelictWalkerLookSuite implements SnapshotSuite {

    static final String VAST_FIXTURE = DerelictHeaviesLookSuite.VAST_FIXTURE;
    static final int SETTLE_TICKS = 120;
    static final float STILL_CELLS = 16f;
    static final float FOLLOW_CELLS = 14f;
    static final int RECORD_TICKS = 540;
    /** How far along the hall the walker is sent, cells. */
    static final int WALK_CELLS = 12;
    static final int BULWARK_ASIDE_CELLS = 5;

    @Override public String id() { return "derelict-walker-look"; }

    @Override public String label() { return "The derelict security walker in a vault hall, beside a Bulwark"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            JSONObject fixture = new JSONObject(Files.readString(context.projectRoot().resolve(VAST_FIXTURE),
                    StandardCharsets.UTF_8));
            List<SnapshotArtifact> out = new ArrayList<>();
            Staged still = stage(fixture);
            if (still == null) return out;
            long bulwark = bulwark(still.sim, still.hall[0] + still.hall[2] * BULWARK_ASIDE_CELLS,
                    still.hall[1] + still.hall[3] * BULWARK_ASIDE_CELLS);
            float cx = still.hall[0] + 0.5f + still.hall[2] * BULWARK_ASIDE_CELLS / 2f;
            float cy = still.hall[1] + 0.5f + still.hall[3] * BULWARK_ASIDE_CELLS / 2f;
            System.out.println("    [look/derelict-walker] hall " + still.hall[0] + "," + still.hall[1] + " along "
                    + still.hall[2] + "," + still.hall[3] + ", bulwark " + (bulwark != 0L));
            out.add(new SnapshotArtifact("derelict-walker-hall.png", BattleReviewFrameRenderer.look(context.modRoot(),
                    1920, 1080, cx, cy, STILL_CELLS).withoutRoofs().render(still.sim, "derelict-walker-hall")));
            long[] walker = {0L};
            out.addAll(LookReview.record(context.modRoot(), new LookReview.Shot("derelict-walker-walk",
                    LookGround.verdant(), 40, 30, placement -> walking(fixture, walker, placement),
                    placement -> List.of(follow(walker)), RECORD_TICKS, 1, 1, new int[] {RECORD_TICKS / 3})));
            return out;
        }
    }

    /** The settled site with its vault frame stood at the start of a hall; null when the site has no walker or hall. */
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
        int[] hall = longestHall(sim.getGrid(), sim.world().cellX(walker), sim.world().cellY(walker));
        if (hall == null) return null;
        sim.world().setPos(walker, hall[0] + 0.5f, hall[1] + 0.5f);
        return new Staged(sim, walker, w.squadId(), hall);
    }

    record Staged(BattleSimulation sim, long walker, int squad, int[] hall) {
    }

    /**
     * The staged site with the walker walked along its hall at a steady pace, scripted a step each tick and pointed
     * down the hall: a look of the legs at a walk, not of the network's orders, which keep it at its post.
     */
    static BattleSimulation walking(JSONObject fixture, long[] walker, LookReview.Placement placement) {
        Staged staged;
        try {
            staged = stage(fixture);
        } catch (Exception e) {
            throw new IllegalStateException("derelict fixture", e);
        }
        if (staged == null) throw new IllegalStateException("no walker or hall in the vast site");
        walker[0] = staged.walker;
        float heading = (float) Math.toDegrees(Math.atan2(-staged.hall[2], staged.hall[3]));
        float step = WALK_CELLS / (float) RECORD_TICKS;
        int[] ticks = {0};
        placement.beforeTick(sim -> {
            int t = Math.min(RECORD_TICKS, ticks[0]++);
            sim.world().setPos(staged.walker, staged.hall[0] + 0.5f + staged.hall[2] * step * t,
                    staged.hall[1] + 0.5f + staged.hall[3] * step * t);
            FacingSystem.faceStanding(sim.getEntityWorld(), sim.getBattleComponents(), staged.walker, heading);
        });
        return staged.sim;
    }

    /** A camera on the walker. */
    static LookReview.View follow(long[] walker) {
        return new LookReview.View("follow", sim -> new float[] {sim.world().renderX(walker[0]),
                sim.world().renderY(walker[0]), FOLLOW_CELLS}).withoutRoofs();
    }

    /**
     * The start of the longest straight hall three cells wide (open across 3, walled either side) near (cx, cy), as
     * {x, y, stepX, stepY}: the walker walks from it in the step direction. Null when the site has none of at least
     * {@link #WALK_CELLS} cells.
     */
    static int[] longestHall(NavigationGrid grid, int cx, int cy) {
        int[] best = null;
        long bestScore = Long.MIN_VALUE;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                for (int[] s : steps) {
                    int run = 0;
                    while (run <= WALK_CELLS + 2 && hallCell(grid, x + s[0] * run, y + s[1] * run, s[0] != 0)) run++;
                    if (run < WALK_CELLS + 2) continue;
                    long d = (long) (x - cx) * (x - cx) + (long) (y - cy) * (y - cy);
                    long score = -d;
                    if (score > bestScore) {
                        bestScore = score;
                        best = new int[] {x, y, s[0], s[1]};
                    }
                }
            }
        }
        return best;
    }

    private static boolean hallCell(NavigationGrid grid, int x, int y, boolean alongX) {
        for (int across = -2; across <= 2; across++) {
            int gx = alongX ? x : x + across;
            int gy = alongX ? y + across : y;
            boolean open = grid.inBounds(gx, gy) && grid.isWalkable(gx, gy);
            if (open != (Math.abs(across) <= 1)) return false;
        }
        return true;
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
