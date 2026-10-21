package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.satchel.SatchelChargeService;
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
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A squad of sappers sent onto a live bore (`colony-breach-nouns.md`, Bore:
 * bores are fought, not scripted). The order is the ordinary attack move with
 * the bore's cell named as the thing to open, which is how a commander points
 * a squad at a structure; the squad walks there and its kit does the rest.
 * <ul>
 *   <li>{@code satchels}: four marines, three of them carrying satchel
 *       charges, walk onto the bore. The ordinary satchel path plants charges
 *       on it, the charges damage it through ordinary durability, and the bore
 *       system notices the collapse: the umbrella's reach is forgotten, the
 *       bore goal is withdrawn and the bore takes no more biomass.</li>
 *   <li>{@code rifles}: the same squad with no satchels. No charge is ever
 *       planted; how much of the bore small arms wear away is a reading.</li>
 * </ul>
 * No director runs, so the bore surges nothing: the scene is about what a
 * squad does to a bore, not about the swarm that would come out of it.
 */
public final class SwarmBoreCollapseScene implements BehaviorScene {

    private static final int TICKS = 1800;
    private static final int BORE_X = 34;
    private static final int BORE_Y = 16;

    @Override public String id() { return "swarm-bore-collapse"; }

    @Override public String label() {
        return "Swarm bore: a satchel squad collapses it through ordinary demolition, and the collapse is permanent";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("satchels", true, frames), playOne("rifles", false, FrameSink.NONE));
    }

    private SceneReport playOne(String loop, boolean satchels, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(50, 32).seed(20261021L).missionCompletion(false);
        SceneBuilder.SquadSpec squad = builder.squad("sappers").faction(Faction.MARINE).size(4)
                .at(12, BORE_Y).primary("weapon.smg")
                .assigned(id -> ObjectiveAssignment.attackMove(id, BORE_X, BORE_Y).withBreach(BORE_X, BORE_Y));
        if (satchels) squad.special(SpecialEquipmentRegistry.SATCHEL_CHARGE_ID, 3);
        squad.done();
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, BORE_X, BORE_Y);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            float full = sim.world().hp(bore.entityId()) + sim.world().armor(bore.entityId());
            int[] declared = sim.getGrid().swarmFlowFields().fixedSources(SwarmGoal.BORE.ordinal());
            Trace trace = new Trace(bore, full);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 30, tick -> loop + " / bore " + trace.integrityPercent(sim) + "% / tick " + tick)
                    .run(TICKS);

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("the-bore-is-a-flow-field-goal", declared != null && declared.length == 1,
                    "bore goal declared on " + (declared == null ? 0 : declared.length) + " cells at opening"));
            if (satchels) {
                verdicts.add(Verdict.of("charges-are-set-on-the-bore", trace.chargesPlanted > 0,
                        trace.chargesPlanted + " charges set on the bore"));
                verdicts.add(Verdict.of("a-charge-damages-the-bore", trace.chargesPlanted == 0
                                || trace.largestDrop >= SATCHEL_DROP_FLOOR,
                        "largest single-tick loss " + trace.largestDrop + " of " + full));
                boolean dead = bore.collapsed();
                int[] after = sim.getGrid().swarmFlowFields().fixedSources(SwarmGoal.BORE.ordinal());
                verdicts.add(Verdict.of("a-collapse-is-noticed", !dead || after == null
                                && collapsedEvent(sim, bore),
                        dead ? "collapsed at tick " + bore.collapsedTick() + ", bore goal "
                                + (after == null ? "withdrawn" : "still declared")
                                : "the bore stood (" + trace.integrityPercent(sim) + "% left)"));
                verdicts.add(Verdict.of("collapse-is-permanent", !dead
                                || !sim.swarmBores().feed(sim, bore, 5f) && bore.tier() == BoreTier.FISSURE
                                && sim.swarmBores().livingCount() == 0,
                        dead ? "a feed after the collapse was refused" : "the bore stood"));
            } else {
                verdicts.add(Verdict.of("no-charge-without-satchels", trace.chargesPlanted == 0,
                        trace.chargesPlanted + " charges set"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("chargesPlanted", trace.chargesPlanted);
            metrics.put("firstChargeTick", trace.firstChargeTick);
            metrics.put("collapsedTick", bore.collapsedTick());
            metrics.put("integrityLeftPercent", trace.integrityPercent(sim));
            metrics.put("largestDrop", trace.largestDrop);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    /** A satchel's blast takes far more off a bore in one tick than small arms ever do. */
    private static final float SATCHEL_DROP_FLOOR = 300f;

    private static boolean collapsedEvent(BattleSimulation sim, SwarmBore bore) {
        for (SwarmBores.Event event : sim.swarmBores().events()) {
            if (event.bore() == bore && event.kind() == SwarmBores.EventKind.COLLAPSED) return true;
        }
        return false;
    }

    private static final class Trace implements TickObserver {
        private final SwarmBore bore;
        private final float full;
        private final List<Long> charges = new ArrayList<>();
        private float last;
        int chargesPlanted;
        int firstChargeTick = -1;
        float largestDrop;

        Trace(SwarmBore bore, float full) {
            this.bore = bore;
            this.full = full;
            this.last = full;
        }

        int integrityPercent(BattleSimulation sim) {
            long id = bore.entityId();
            if (bore.collapsed() || !sim.world().isAlive(id)) return 0;
            return Math.round(100f * (sim.world().hp(id) + sim.world().armor(id)) / full);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            long id = bore.entityId();
            for (SatchelChargeService.ChargeView charge : sim.satchelCharges().activeCharges()) {
                if (charge.targetId() != id || charges.contains(charge.id())) continue;
                charges.add(charge.id());
                chargesPlanted++;
                if (firstChargeTick < 0) firstChargeTick = tick;
            }
            float now = sim.world().isAlive(id) ? sim.world().hp(id) + sim.world().armor(id) : 0f;
            largestDrop = Math.max(largestDrop, last - now);
            last = now;
        }
    }
}
