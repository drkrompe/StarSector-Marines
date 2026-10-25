package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPack;
import com.dillon.starsectormarines.battle.evacuation.flier.HarrierPatrol;
import com.dillon.starsectormarines.battle.evacuation.flier.SwarmFliers;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A satellite bore at maw in an old bloom with its brood spire, and a marine line with flak out of the spire's reach
 * (`colony-breach-nouns.md`, Brood spire, Patrols).
 * <ul>
 *   <li>{@code patrol}: the line stands far off. Its gap already out, the quiet spire puts up a patrol of
 *       {@link BroodSpires#PATROL}, which flies its loop over the bore, never dives, and lands back on the hooks,
 *       leaving the roost full.</li>
 *   <li>{@code spots}: the line stands just past the far side of the patrol's loop, out of the spire's wake. The patrol spots it and dives on it; the spire itself launches nothing.</li>
 * </ul>
 */
public final class HarrierPatrolScene implements BehaviorScene {

    static final int W = 160, H = 120;
    static final int BORE_X = 80, BORE_Y = 60;
    /** Where the line stands in {@code patrol}: far from the nest. */
    static final int FAR_X = 150, FAR_Y = 112;
    /** Cells past the patrol's far waypoint the line stands in {@code spots}: inside its eye. */
    static final float BEYOND = 12f;
    static final int TICKS = 4800;

    @Override public String id() { return "harrier-patrol"; }

    @Override public String label() {
        return "Harrier patrol: a quiet spire's full roost puts three up to fly a loop over the nest and land back on"
                + " its hooks; a line on the loop is spotted and dived on";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        int[] at = across();
        return List.of(run("patrol", FAR_X, FAR_Y, frames), run("spots", at[0], at[1], FrameSink.NONE));
    }

    /**
     * The cell {@link #BEYOND} cells past the waypoint of the spire's first loop furthest from the spire, out from the
     * bore: on the patrol's beat, out of the spire's own wake. The spire and its loop are seeded off the bore, so a
     * build like the loops' own finds them.
     */
    static int[] across() {
        try (BattleSimulation sim = builder(FAR_X, FAR_Y).build().sim()) {
            quietMaw(sim, BORE_X, BORE_Y);
            BroodSpires.Spire s = sim.broodSpires().spires().get(0);
            float bx = BORE_X + 0.5f, by = BORE_Y + 0.5f, hx = s.x + 0.5f, hy = s.y + 0.5f;
            float[] route = HarrierPatrol.route(bx, by, hx, hy, s.entityId * 31L, W, H);
            float best = -1f, wx = 0f, wy = 0f;
            for (int i = 0; i < HarrierPatrol.LEGS; i++) {
                float d = (float) Math.hypot(route[i * 2] - hx, route[i * 2 + 1] - hy);
                if (d > best) { best = d; wx = route[i * 2]; wy = route[i * 2 + 1]; }
            }
            float ox = wx - bx, oy = wy - by, od = (float) Math.hypot(ox, oy);
            int x = (int) Math.floor(wx + ox / od * BEYOND), y = (int) Math.floor(wy + oy / od * BEYOND);
            if (Math.hypot(x + 0.5f - hx, y + 0.5f - hy) <= BroodSpires.WAKE_RANGE) {
                throw new IllegalStateException("the loop's far side is inside the spire's wake");
            }
            return new int[]{x, y};
        }
    }

    /**
     * A bore fed to maw on matured creep, its spire grown, and no swarm director: nothing on the ground comes for the
     * line, so the only Rav that can find it are the patrol.
     */
    static SwarmBore quietMaw(BattleSimulation sim, int x, int y) {
        SwarmBore bore = sim.swarmBores().open(sim, 1, false, x, y);
        if (bore == null) throw new IllegalStateException("open ground refused a bore");
        for (int guard = 0; bore.tier() != BoreTier.MAW && guard < 64; guard++) sim.swarmBores().feed(sim, bore, 1f);
        sim.creepField();
        sim.matureCreep();
        return bore;
    }

    static SceneBuilder builder(int lineX, int lineY) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261025L).missionCompletion(false);
        builder.squad("rifles").faction(Faction.MARINE).size(6).at(lineX, lineY).primary("weapon.field-rifle")
                .stationary().done();
        return builder;
    }

    private SceneReport run(String loop, int lineX, int lineY, FrameSink frames) {
        try (BattleSimulation sim = builder(lineX, lineY).build().sim()) {
            quietMaw(sim, BORE_X, BORE_Y);
            sim.spawn(MapTurret.create("flak", Faction.MARINE, BroodSpireScene.FLAK, lineX + 3, lineY));
            BroodSpires spires = sim.broodSpires();
            BroodSpires.Spire spire = spires.spires().get(0);
            // Its gap already out, so the patrol goes up before the bore's ground swarm reaches the line's flak.
            spire.lastPatrolTick = -BroodSpires.patrolGapTicks(spire.entityId, 0);
            HarrierPack patrol = null;
            float nearest = Float.MAX_VALUE, furthest = 0f;
            int launchTick = -1, homeTick = -1, perchedHome = -1, ticks = TICKS;
            for (int tick = 1; tick <= TICKS; tick++) {
                sim.advance(BattleSimulation.TICK_DT);
                HarrierPack now = spire.patrol();
                if (now != null && patrol == null) {
                    patrol = now;
                    launchTick = tick;
                }
                if (now != null && now.unhatched() == 0 && !now.members.isEmpty() && !now.patrol().homing()) {
                    float d = (float) Math.hypot(now.centreX() - (BORE_X + 0.5f), now.centreY() - (BORE_Y + 0.5f));
                    nearest = Math.min(nearest, d);
                    furthest = Math.max(furthest, d);
                }
                if (patrol != null && now == null && homeTick < 0) {
                    homeTick = tick;
                    perchedHome = spires.perched(sim, spire);
                }
                if (tick % 15 == 0) {
                    frames.frame(loop, sim, tick, "harrier patrol / " + loop + " / tick " + tick + " / "
                            + spire.patrols() + " patrols / " + sim.swarmFliers().packs().size() + " aloft");
                }
                if ("patrol".equals(loop) && homeTick > 0 || "spots".equals(loop) && commits(sim, patrol) > 0) {
                    ticks = tick;
                    break;
                }
            }
            int landed = 0;
            for (SwarmFliers.Event e : sim.swarmFliers().events()) {
                if (e.kind() == SwarmFliers.Event.Kind.LANDED) landed++;
            }
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("a-quiet-spire-puts-a-patrol-up", patrol != null && spires.launches().isEmpty(),
                    "patrol at tick " + launchTick + ", " + spires.launches().size() + " launches"));
            switch (loop) {
                case "patrol" -> {
                    verdicts.add(Verdict.of("it-flies-its-loop-over-the-nest",
                            furthest >= HarrierPatrol.REACH_MIN - 2f && furthest <= HarrierPatrol.REACH_MAX + 8f,
                            "out " + nearest + " to " + furthest + " cells from the bore"));
                    verdicts.add(Verdict.of("it-never-dives-on-a-line-it-cannot-see", commits(sim, patrol) == 0,
                            "commits " + commits(sim, patrol)));
                    verdicts.add(Verdict.of("it-lands-back-on-its-hooks", landed == BroodSpires.PATROL
                                    && sim.swarmFliers().falls().isEmpty() && perchedHome == BroodSpires.PERCHES,
                            landed + " landed at tick " + homeTick + ", " + sim.swarmFliers().falls().size()
                                    + " fell, " + perchedHome + " perched"));
                }
                case "spots" -> verdicts.add(Verdict.of("it-spots-the-line-on-its-loop-and-dives",
                        commits(sim, patrol) > 0, "commits " + commits(sim, patrol) + " by tick " + ticks
                                + ", line at " + lineX + "," + lineY));
                default -> throw new IllegalStateException(loop);
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("launchTick", launchTick);
            metrics.put("homeTick", homeTick);
            metrics.put("furthest", furthest);
            metrics.put("landed", landed);
            return new SceneReport(id(), loop, ticks, verdicts, metrics);
        }
    }

    private static int commits(BattleSimulation sim, HarrierPack patrol) {
        if (patrol == null) return 0;
        int n = 0;
        for (SwarmFliers.Event e : sim.swarmFliers().events()) {
            if (e.kind() == SwarmFliers.Event.Kind.COMMIT && e.pack() == patrol.id) n++;
        }
        return n;
    }
}
