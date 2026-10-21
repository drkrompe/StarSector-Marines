package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.component.BattleComponents;
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
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Harvest (`colony-breach-nouns.md`): Runners bring colonists down at the west
 * edge of open ground and drag them east to a bore.
 * <ul>
 *   <li>{@code harvest}: nobody interferes. Carriers take their loads down the
 *       bore field and feed them in.</li>
 *   <li>{@code intercept}: the same, with a rifle team standing off the drag
 *       line. Carriers that die drop their loads where they fall.</li>
 * </ul>
 * Verdicts are mechanics: a body a Runner kills while a bore lives is seized,
 * a load rides with its carrier, a carrier never strikes, a delivery feeds the
 * bore it reached, and a dead carrier's load is dropped rather than delivered.
 * How many bodies got through and how far the bore grew are metrics, so the
 * starved bore and the fed one are read side by side.
 */
public final class SwarmHarvestScene implements BehaviorScene {

    static final int WIDTH = 64;
    static final int HEIGHT = 36;
    private static final int TICKS = 2700;
    private static final int BORE_X = 58;
    private static final int BORE_Y = 18;
    private static final int COLONISTS = 8;
    private static final int RUNNERS = 5;

    @Override public String id() { return "swarm-harvest"; }

    @Override public String label() {
        return "Swarm harvest: Runners drag the colonists they kill to a bore; a rifle team intercepts the carriers";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("harvest", false, frames), playOne("intercept", true, frames));
    }

    private SceneReport playOne(String loop, boolean intercept, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).seed(20261021L).missionCompletion(false);
        // The far Marine every scene keeps alive, sealed in a box in the corner.
        builder.wall(0, HEIGHT - 5, 4, HEIGHT - 5).wall(4, HEIGHT - 5, 4, HEIGHT - 1)
                .unit("anchor", Faction.MARINE, UnitType.MARINE, 1, HEIGHT - 2);
        for (int i = 0; i < COLONISTS; i++) {
            builder.unit("colonist" + i, Faction.CIVILIAN, UnitType.CIVILIAN, 3 + (i % 2) * 2, 11 + i * 2,
                    spec -> spec.role(UnitRole.FLEE));
        }
        if (intercept) {
            builder.squad("riflemen").faction(Faction.MARINE).size(4).at(40, 6).stationary().done();
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, BORE_X, BORE_Y);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            for (int i = 0; i < RUNNERS; i++) {
                SwarmStrains.spawn(sim, "Runner " + i, UnitType.SWARM_RUNNER, 16, 12 + i * 3);
            }
            SwarmHarvest harvest = sim.swarmHarvest();
            long[] colonists = new long[COLONISTS];
            for (int i = 0; i < COLONISTS; i++) colonists[i] = world.unit("colonist" + i);
            Trace trace = new Trace(harvest, colonists);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 6, tick -> loop + " / seized " + harvest.seized()
                            + " / delivered " + harvest.delivered() + " / dropped " + harvest.dropped()
                            + " / bore " + bore.tier().beatName + " " + Math.round(bore.growth()) + " / tick " + tick)
                    .run(TICKS);

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("a-runner-seizes-what-it-kills", trace.killedUnseized == 0,
                    trace.colonistsKilled + " colonists died, " + trace.killedUnseized + " left where they fell"));
            verdicts.add(Verdict.of("the-load-rides-with-its-carrier",
                    trace.worstTrail <= SwarmHarvest.TRAIL_CELLS + 0.05f,
                    "furthest a load lay from its carrier: " + trace.worstTrail + " cells"));
            verdicts.add(Verdict.of("a-carrier-never-strikes", trace.carrierStrikes == 0,
                    trace.carrierStrikes + " blows struck by a carrier"));
            verdicts.add(Verdict.of("a-delivery-feeds-the-bore", bore.feeds() == harvest.delivered()
                            && Math.abs(bore.biomassFed() - harvest.delivered()) < 1e-3f,
                    harvest.delivered() + " delivered, " + bore.feeds() + " feeds, " + bore.biomassFed() + " biomass"));
            verdicts.add(Verdict.of("a-delivered-body-goes-into-the-hole", trace.deliveredStillDrawn == 0,
                    trace.deliveredStillDrawn + " delivered bodies still drawn"));
            if (intercept) {
                verdicts.add(Verdict.of("a-dead-carrier-drops-its-load",
                        trace.carriersKilled == harvest.dropped() && trace.droppedNotLoose == 0,
                        trace.carriersKilled + " carriers killed under load, " + harvest.dropped()
                                + " dropped, " + trace.droppedNotLoose + " not left loose"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("colonistsKilled", trace.colonistsKilled);
            metrics.put("seized", harvest.seized());
            metrics.put("delivered", harvest.delivered());
            metrics.put("dropped", harvest.dropped());
            metrics.put("carriersKilled", trace.carriersKilled);
            metrics.put("firstHarvestTick", harvest.firstHarvestTick());
            metrics.put("boreGrowth", Math.round(bore.growth()));
            metrics.put("boreTier", bore.tier().ordinal());
            metrics.put("boreTierTick", bore.tierTick(BoreTier.BORE));
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static final class Trace implements TickObserver {
        private final SwarmHarvest harvest;
        private final long[] colonists;
        private final boolean[] counted;
        private Map<Long, Long> lastLoads = Map.of();
        private final Map<Long, Float> dealt = new HashMap<>();
        private final Set<Long> delivered = new HashSet<>();
        int colonistsKilled;
        int killedUnseized;
        int carrierStrikes;
        int carriersKilled;
        int droppedNotLoose;
        int deliveredStillDrawn;
        float worstTrail;

        Trace(SwarmHarvest harvest, long[] colonists) {
            this.harvest = harvest;
            this.colonists = colonists;
            this.counted = new boolean[colonists.length];
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            Map<Long, Long> now = Map.copyOf(harvest.loads());
            for (Map.Entry<Long, Long> was : lastLoads.entrySet()) {
                long carrier = was.getKey(), body = was.getValue();
                if (now.containsKey(carrier)) continue;
                if (sim.resolveUnit(carrier) == 0L) {
                    carriersKilled++;
                    if (!harvest.isLoose(body) && !harvest.isCarried(body)) droppedNotLoose++;
                } else {
                    delivered.add(body);
                }
            }
            for (Map.Entry<Long, Long> load : now.entrySet()) {
                long carrier = load.getKey(), body = load.getValue();
                float dx = sim.world().x(body) - sim.world().x(carrier);
                float dy = sim.world().y(body) - sim.world().y(carrier);
                worstTrail = Math.max(worstTrail, (float) Math.sqrt(dx * dx + dy * dy));
                Float before = dealt.get(carrier);
                if (before != null && sim.telemetry().damageDealt(carrier) > before) carrierStrikes++;
            }
            dealt.clear();
            for (long carrier : now.keySet()) dealt.put(carrier, sim.telemetry().damageDealt(carrier));
            // Only Runners can kill a colonist here (Marine fire never touches civilians), and the bore
            // stands throughout, so every colonist that dies is one the harvest should be holding.
            for (int i = 0; i < colonists.length; i++) {
                if (counted[i] || sim.resolveUnit(colonists[i]) != 0L) continue;
                counted[i] = true;
                colonistsKilled++;
                long body = colonists[i];
                if (!harvest.isCarried(body) && !harvest.isLoose(body) && !delivered.contains(body)) killedUnseized++;
            }
            deliveredStillDrawn = 0;
            for (long body : delivered) {
                int pose = sim.getEntityWorld().getInt(body, sim.getBattleComponents().SPRITE,
                        BattleComponents.SPRITE_INDEX);
                if (pose >= 0) deliveredStillDrawn++;
            }
            lastLoads = now;
        }
    }
}
