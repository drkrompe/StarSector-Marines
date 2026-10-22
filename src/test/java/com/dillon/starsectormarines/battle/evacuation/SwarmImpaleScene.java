package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.BodyPart;
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
import com.dillon.starsectormarines.marine.ArmorClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runner impale-and-drag (`runner-impale-drag.md`): a Runner pack comes at a
 * squad standing between it and a bore, and now and then a blow goes in and
 * the Runner hauls a living Marine off toward the hole.
 * <ul>
 *   <li>{@code taken}: the squad is unarmed, so nobody can go after the
 *       carriers. Marines on the blades reach the bore and are taken.</li>
 *   <li>{@code freed}: the same squad with rifles. A carrier holding one of
 *       its own is its priority target; a carrier it kills frees the Marine.</li>
 * </ul>
 * A power-suited Marine stands with the squad in both loops. Verdicts are
 * mechanics: impales land only on bodies harvest may take, a carrier never
 * strikes, a held body takes no harm from direct fire or blows, a taken Marine
 * is dead and fed the bore, and a freed Marine is alive, wounded in both legs
 * and acting again after the recovery. Counts are metrics.
 */
public final class SwarmImpaleScene implements BehaviorScene {

    static final int WIDTH = 56;
    static final int HEIGHT = 32;
    private static final int TICKS = 2400;
    private static final int BORE_X = 50;
    private static final int BORE_Y = 16;
    private static final int RUNNERS = 16;

    @Override public String id() { return "swarm-impale"; }

    @Override public String label() {
        return "Runner impale: Marines dragged off alive to a bore; a rifle squad frees them by killing the carriers";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("taken", false, frames), playOne("freed", true, frames));
    }

    private SceneReport playOne(String loop, boolean armed, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).seed(20261021L).missionCompletion(false);
        SceneBuilder.SquadSpec squad = builder.squad("squad").faction(Faction.MARINE).size(6).at(34, BORE_Y).stationary()
                // Sturdy enough to take many blows: the impale is one blow in twenty, and the scene wants several.
                .health(300f);
        // Unarmed means no kit and no base weapon either: nobody here can go after a carrier.
        if (!armed) squad.unarmed().each(spec -> spec.attackDamage(0f));
        squad.done();
        builder.unit("suit", Faction.MARINE, UnitType.MARINE, 33, BORE_Y + 2,
                spec -> spec.armorClass(ArmorClass.POWER_SUIT).attackDamage(0f));
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            SwarmBore bore = sim.swarmBores().open(sim, 1, false, BORE_X, BORE_Y);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            for (int i = 0; i < RUNNERS; i++) {
                SwarmStrains.spawn(sim, "Runner " + i, UnitType.SWARM_RUNNER, 20 + (i % 4) * 2, 6 + (i / 4) * 4);
            }
            SwarmHarvest harvest = sim.swarmHarvest();
            Trace trace = new Trace(harvest, world.unit("suit"));
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 6, tick -> loop + " / impaled " + harvest.impaled()
                            + " / freed " + harvest.freed() + " / taken " + harvest.taken()
                            + " / bore " + bore.tier().beatName + " " + Math.round(bore.growth()) + " / tick " + tick)
                    .run(TICKS);

            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("impales-only-on-bodies-harvest-may-take", trace.ineligibleImpales == 0,
                    harvest.impaled() + " impales, " + trace.ineligibleImpales + " on a body harvest may not take"));
            verdicts.add(Verdict.of("never-a-power-suit", !trace.suitHeld, trace.suitHeld ? "the suit was held" : "the suit never was"));
            verdicts.add(Verdict.of("a-carrier-never-strikes", trace.carrierStrikes == 0,
                    trace.carrierStrikes + " blows struck by a carrier"));
            verdicts.add(Verdict.of("a-held-body-takes-no-harm", trace.heldHarm == 0f,
                    "health and armour lost while held: " + trace.heldHarm));
            verdicts.add(Verdict.of("a-taken-marine-is-dead-and-fed-the-bore",
                    trace.takenAlive == 0 && bore.feeds() == harvest.delivered()
                            && harvest.takenMarines(sim).size() >= trace.takenMarines,
                    trace.takenMarines + " Marines taken, " + trace.takenAlive + " alive after, "
                            + bore.feeds() + " feeds for " + harvest.delivered() + " deliveries"));
            if (armed) {
                verdicts.add(Verdict.of("a-dead-carrier-frees-a-living-body",
                        trace.freedDead == 0 && trace.freedUnwounded == 0,
                        harvest.freed() + " freed, " + trace.freedDead + " dead, "
                                + trace.freedUnwounded + " without both legs wounded"));
                verdicts.add(Verdict.of("a-freed-marine-acts-again", trace.stillHeldAfterRecovery == 0,
                        trace.stillHeldAfterRecovery + " freed bodies still holding still after the recovery"));
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("impaled", harvest.impaled());
            metrics.put("freed", harvest.freed());
            metrics.put("taken", harvest.taken());
            metrics.put("seized", harvest.seized());
            metrics.put("delivered", harvest.delivered());
            metrics.put("marinesAlive", alive(world.members("squad"), sim));
            metrics.put("firstImpaleTick", trace.firstImpaleTick);
            metrics.put("boreGrowth", Math.round(bore.growth()));
            metrics.put("boreTier", bore.tier().ordinal());
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static int alive(long[] ids, BattleSimulation sim) {
        int count = 0;
        for (long id : ids) if (sim.world().isAlive(id)) count++;
        return count;
    }

    private static final class Trace implements TickObserver {
        private static final int RECOVERY_TICKS = Math.round(SwarmHarvest.RECOVERY_SECONDS / BattleSimulation.TICK_DT) + 2;
        private final SwarmHarvest harvest;
        private final long suit;
        private int cursor;
        private final Map<Long, Float> dealt = new HashMap<>();
        private final Map<Long, Float> heldHealth = new HashMap<>();
        private final Map<Long, Integer> freedAt = new HashMap<>();
        int ineligibleImpales;
        boolean suitHeld;
        int carrierStrikes;
        float heldHarm;
        int takenMarines;
        int takenAlive;
        int freedDead;
        int freedUnwounded;
        int stillHeldAfterRecovery;
        int firstImpaleTick = -1;

        Trace(SwarmHarvest harvest, long suit) {
            this.harvest = harvest;
            this.suit = suit;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            List<SwarmHarvest.Event> events = harvest.events();
            for (; cursor < events.size(); cursor++) {
                SwarmHarvest.Event event = events.get(cursor);
                long body = event.body();
                switch (event.kind()) {
                    case IMPALED -> {
                        if (firstImpaleTick < 0) firstImpaleTick = tick;
                        if (!SwarmHarvest.isInfantry(sim.identity().type(body))
                                || sim.identity().armorClass(body) == ArmorClass.POWER_SUIT) ineligibleImpales++;
                    }
                    case FREED -> {
                        if (!sim.world().isAlive(body)) freedDead++;
                        if (sim.wounds().wounds(body, BodyPart.LEGS) < 2) freedUnwounded++;
                        freedAt.put(body, tick);
                    }
                    case TAKEN -> {
                        if (event.faction() == Faction.MARINE) takenMarines++;
                        if (sim.world().isAlive(body)) takenAlive++;
                    }
                }
            }
            if (harvest.isCarried(suit)) suitHeld = true;
            Map<Long, Long> now = Map.copyOf(harvest.loads());
            for (Map.Entry<Long, Long> load : now.entrySet()) {
                long carrier = load.getKey(), body = load.getValue();
                Float before = dealt.get(carrier);
                if (before != null && sim.telemetry().damageDealt(carrier) > before) carrierStrikes++;
                if (sim.world().isAlive(body)) {
                    float health = sim.world().hp(body) + (sim.world().hasArmor(body) ? sim.world().armor(body) : 0f);
                    Float was = heldHealth.get(body);
                    if (was != null && health < was) heldHarm += was - health;
                    heldHealth.put(body, health);
                }
            }
            heldHealth.keySet().retainAll(now.values());
            dealt.clear();
            for (long carrier : now.keySet()) dealt.put(carrier, sim.telemetry().damageDealt(carrier));
            for (Map.Entry<Long, Integer> freed : new ArrayList<>(freedAt.entrySet())) {
                if (tick - freed.getValue() < RECOVERY_TICKS) continue;
                long body = freed.getKey();
                if (sim.world().isAlive(body) && harvest.holdsStill(body) && !harvest.isCarried(body)) {
                    stillHeldAfterRecovery++;
                }
                freedAt.remove(body);
            }
        }
    }
}
