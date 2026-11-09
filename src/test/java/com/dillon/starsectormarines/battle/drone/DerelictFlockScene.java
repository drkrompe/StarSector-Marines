package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A derelict flock of sixteen swarm drones ({@link Flock}), three loops (`derelict-drone-flocks.md`).
 *
 * <p>{@code split}: the flock probes a route straight through a roofed building; its members go round it by their own
 * sides and re-form beyond it. {@code strike-open}: it strikes a fire team of four riflemen standing in the open,
 * wraps them and strafes in passes. {@code strike-indoors}: the same team inside a roofed room with an open door; the
 * flock wraps the building and never comes in.
 *
 * <p>Each verdict is the mechanism: no member ever in a closed cell, members on both sides of the building at once,
 * the body together again past it, the strike ring round the target on several sides, rounds fired. Who wins is a
 * reading.
 */
public final class DerelictFlockScene implements BehaviorScene {

    public static final String SPLIT = "split";
    public static final String STRIKE_OPEN = "strike-open";
    public static final String STRIKE_INDOORS = "strike-indoors";

    static final int WIDTH = 96;
    static final int HEIGHT = 56;
    /** The building in the split loop: x 40..54, y 18..38, roofed. */
    static final int B_X0 = 40, B_Y0 = 18, B_X1 = 54, B_Y1 = 38;
    /** The room in the indoors loop: x 56..66, y 22..34, roofed, a door on its west wall. */
    static final int R_X0 = 56, R_Y0 = 22, R_X1 = 66, R_Y1 = 34;
    static final int FLOCK_SIZE = 16;
    static final float LAUNCH_X = 12.5f, LAUNCH_Y = 28.5f;
    static final float LEASH = 90f;

    private static final int SPLIT_TICKS = 1200;
    private static final int STRIKE_TICKS = 1200;
    private static final String RIFLE = "weapon.field-rifle";

    private final int frameEvery;

    public DerelictFlockScene() {
        this(10);
    }

    public DerelictFlockScene(int frameEvery) {
        this.frameEvery = frameEvery;
    }

    @Override public String id() { return "derelict-flock"; }

    @Override public String label() {
        return "Derelict flock: sixteen swarm drones split round a roofed building and re-form, wrap and strafe a "
                + "fire team in the open, and never enter the room a team holds";
    }

    @Override public int frameDelayMillis() { return 70; }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        List<SceneReport> reports = new ArrayList<>();
        reports.add(split(frames));
        reports.add(strike(STRIKE_OPEN, false, frames));
        reports.add(strike(STRIKE_INDOORS, true, frames));
        return reports;
    }

    /** One sealed box a side, far off, so the battle ticks with both sides on the map. */
    private static SceneBuilder holdouts(SceneBuilder b) {
        return b.wall(WIDTH - 9, HEIGHT - 9, WIDTH - 2, HEIGHT - 9).wall(WIDTH - 9, HEIGHT - 2, WIDTH - 2, HEIGHT - 2)
                .wall(WIDTH - 9, HEIGHT - 9, WIDTH - 9, HEIGHT - 2).wall(WIDTH - 2, HEIGHT - 9, WIDTH - 2, HEIGHT - 2)
                .unit("marine-holdout", Faction.MARINE, UnitType.MARINE, WIDTH - 5, HEIGHT - 5)
                .wall(WIDTH - 9, 1, WIDTH - 2, 1).wall(WIDTH - 9, 8, WIDTH - 2, 8)
                .wall(WIDTH - 9, 1, WIDTH - 9, 8).wall(WIDTH - 2, 1, WIDTH - 2, 8)
                .unit("defender-holdout", Faction.DEFENDER, UnitType.MARINE, WIDTH - 5, 4);
    }

    /** Walls round {@code x0..x1, y0..y1} and a roof over all of it, as the generator marks a building. */
    static void roof(BattleSimulation sim, int x0, int y0, int x1, int y1, int buildingId) {
        CellTopology topology = sim.getTopology();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) topology.setBuildingId(x, y, buildingId);
        }
    }

    private SceneReport split(FrameSink frames) {
        SceneBuilder b = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false)
                .wall(B_X0, B_Y0, B_X1, B_Y0).wall(B_X0, B_Y1, B_X1, B_Y1)
                .wall(B_X0, B_Y0, B_X0, B_Y1).wall(B_X1, B_Y0, B_X1, B_Y1);
        holdouts(b);
        SceneWorld world = b.build();
        BattleSimulation sim = world.sim();
        roof(sim, B_X0, B_Y0, B_X1, B_Y1, 1);
        Flock flock = sim.flocks().launch(sim, Faction.DEFENDER, LAUNCH_X, LAUNCH_Y, FLOCK_SIZE, FlockWeights.WASP,
                LEASH);
        flock.probe(30.5f, 28.5f, 80.5f, 28.5f);
        Trace trace = new Trace(flock, 0L);
        SceneRun.of(world).observe(trace)
                .frames(frames, SPLIT, frameEvery, tick -> SPLIT + " / tick " + tick + " / " + trace.caption())
                .run(SPLIT_TICKS);
        sim.close();

        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("never-indoors", trace.closedTicks == 0,
                trace.closedTicks + " member-ticks in a wall or under the roof"));
        v.add(Verdict.of("splits", trace.splitTicks > 0,
                trace.splitTicks + " ticks with three or more members either side of the building at once"));
        v.add(Verdict.of("re-forms", trace.endSpread <= 5f && trace.endCentreX > B_X1 + 6,
                "past the building (centre x " + round(trace.endCentreX) + ") every member within "
                        + round(trace.endSpread) + " cells of the centre"));
        return new SceneReport(id(), SPLIT, SPLIT_TICKS, v, trace.readings());
    }

    private SceneReport strike(String loop, boolean indoors, FrameSink frames) {
        SceneBuilder b = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // In the open, on the flock's line; indoors, in the room's far corner, out of the open door's line.
        int teamX = indoors ? 62 : 60, teamY = indoors ? 32 : 27;
        if (indoors) {
            b.wall(R_X0, R_Y0, R_X1, R_Y0).wall(R_X0, R_Y1, R_X1, R_Y1)
                    .wall(R_X1, R_Y0, R_X1, R_Y1).wall(R_X0, R_Y0, R_X0, 26).wall(R_X0, 30, R_X0, R_Y1);
        }
        b.squad("team").faction(Faction.MARINE).size(4).inFile(teamX, teamY, 1, 0).primary(RIFLE).stationary().done();
        holdouts(b);
        SceneWorld world = b.build();
        BattleSimulation sim = world.sim();
        if (indoors) roof(sim, R_X0, R_Y0, R_X1, R_Y1, 2);
        long[] team = world.members("team");
        Flock flock = sim.flocks().launch(sim, Faction.DEFENDER, LAUNCH_X, LAUNCH_Y, FLOCK_SIZE, FlockWeights.WASP,
                LEASH);
        flock.strike(team[0]);
        Trace trace = new Trace(flock, team[0]);
        trace.team = team;
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, frameEvery, tick -> loop + " / tick " + tick + " / " + trace.caption())
                .run(STRIKE_TICKS);
        sim.close();

        List<Verdict> v = new ArrayList<>();
        v.add(Verdict.of("never-indoors", trace.closedTicks == 0,
                trace.closedTicks + " member-ticks in a wall or under the roof"));
        if (indoors) {
            v.add(Verdict.of("wraps-the-building", trace.bestQuadrants >= 3,
                    "at best " + trace.bestQuadrants + " sides of the room held by members round it"));
        } else {
            v.add(Verdict.of("wraps", trace.bestQuadrants == 4,
                    "at best " + trace.bestQuadrants + " of 4 quadrants round the target held by members on the ring"));
            v.add(Verdict.of("strafes", trace.rounds > 0, trace.rounds + " rounds fired by members"));
        }
        return new SceneReport(id(), loop, STRIKE_TICKS, v, trace.readings());
    }

    private static float round(float f) {
        return Math.round(f * 100f) / 100f;
    }

    private static final class Trace implements TickObserver {
        final Flock flock;
        final long target;
        final Set<Long> members = new HashSet<>();
        long[] team = new long[0];
        int closedTicks, splitTicks, bestQuadrants, rounds, firstRoundTick = -1;
        float endSpread, endCentreX, centreX, centreY;
        int alive, teamAlive;
        float targetX, targetY;
        private String caption = "";

        Trace(Flock flock, long target) {
            this.flock = flock;
            this.target = target;
            members.addAll(flock.memberIds());
        }

        String caption() { return caption; }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            FlockField.Open open = FlockSystem.open(sim.getGrid(), sim.getTopology());
            List<Long> live = new ArrayList<>();
            for (long m : members) if (sim.resolveUnit(m) != 0L) live.add(m);
            alive = live.size();
            teamAlive = 0;
            for (long t : team) if (sim.resolveUnit(t) != 0L) teamAlive++;
            if (live.isEmpty()) return;
            float sx = 0f, sy = 0f;
            int north = 0, south = 0;
            if (target != 0L && sim.world().isAlive(target)) {
                targetX = sim.world().x(target);
                targetY = sim.world().y(target);
            }
            boolean[] quadrant = new boolean[4];
            for (long m : live) {
                float x = sim.world().x(m), y = sim.world().y(m);
                if (!open.open((int) Math.floor(x), (int) Math.floor(y))) closedTicks++;
                sx += x;
                sy += y;
                if (x >= B_X0 - 2 && x <= B_X1 + 2) {
                    if (y > B_Y1) north++;
                    if (y < B_Y0) south++;
                }
                float dx = x - targetX, dy = y - targetY;
                float r = (float) Math.hypot(dx, dy);
                if (target != 0L && r <= FlockFlight.ORBIT_CELLS + 6f && r >= 2f) {
                    quadrant[(int) (((Math.toDegrees(Math.atan2(dy, dx)) + 360.0) % 360.0) / 90.0)] = true;
                }
            }
            int q = 0;
            for (boolean b : quadrant) if (b) q++;
            bestQuadrants = Math.max(bestQuadrants, q);
            if (north >= 3 && south >= 3) splitTicks++;
            centreX = sx / live.size();
            centreY = sy / live.size();
            float spread = 0f;
            for (long m : live) {
                spread = Math.max(spread, (float) Math.hypot(sim.world().x(m) - centreX, sim.world().y(m) - centreY));
            }
            endSpread = spread;
            endCentreX = centreX;
            for (ShotEvent shot : sim.getShotsThisFrame()) {
                if (members.contains(shot.shooterId)) {
                    rounds++;
                    if (firstRoundTick < 0) firstRoundTick = tick;
                }
            }
            caption = flock.task() + ", " + alive + " of " + members.size() + " flying, spread " + round(spread)
                    + (target != 0L ? ", " + q + " sides, " + rounds + " rounds" : "");
        }

        Map<String, Number> readings() {
            Map<String, Number> m = new LinkedHashMap<>();
            m.put("membersLeft", alive);
            m.put("membersLost", members.size() - alive);
            m.put("closedTicks", closedTicks);
            m.put("splitTicks", splitTicks);
            m.put("bestQuadrants", bestQuadrants);
            m.put("rounds", rounds);
            m.put("firstRoundTick", firstRoundTick);
            m.put("endSpread", round(endSpread));
            m.put("teamLeft", teamAlive);
            return m;
        }
    }
}
