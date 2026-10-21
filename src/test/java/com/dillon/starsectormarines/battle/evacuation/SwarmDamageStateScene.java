package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.StructureDef;
import com.dillon.starsectormarines.battle.turret.TurretDemolitionSystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bore and its colonies drawn through their damage states (`colony-breach-nouns.md`, Bore): whole, then every
 * body hurt below half its durability (the wounded painting), then one colony killed, then the bore, which withers
 * the rest. Every death leaves the structure's remains on the ground. Scripted damage, so the verdicts are
 * mechanics: each death that has remains art records them, where the body stood, in the painting it wore.
 */
public final class SwarmDamageStateScene implements BehaviorScene {

    static final int CENTRE = 45;
    private static final int HURT_TICK = 60;
    private static final int COLONY_TICK = 150;
    private static final int BORE_TICK = 240;
    private static final int TICKS = 330;
    private static final int FRAME_EVERY = 30;

    @Override public String id() { return "swarm-damage-states"; }

    @Override public String label() {
        return "Swarm bore and spore colonies whole, wounded, and dead: the remains each death leaves";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        return List.of(playOne("maw", 9f, frames), playOne("mother", 0f, frames));
    }

    private SceneReport playOne(String loop, float biomass, FrameSink frames) throws Exception {
        SceneWorld world = SceneBuilder.openGround(90, 90).seed(20261021L).missionCompletion(false).build();
        try (BattleSimulation sim = world.sim()) {
            boolean mother = loop.equals("mother");
            SwarmBore bore = sim.swarmBores().open(sim, mother ? 0 : 1, mother, CENTRE, CENTRE);
            if (bore == null) throw new IllegalStateException("open ground refused a bore");
            if (biomass > 0f) sim.swarmBores().feed(sim, bore, biomass);
            Script script = new Script(bore);
            SceneRun.of(world).observe(script)
                    .frames(frames, loop, FRAME_EVERY, tick -> loop + " / " + phase(tick) + " / tick " + tick)
                    .run(TICKS);
            List<TurretDemolitionSystem.Remains> remains = sim.getTurretDemolitionSystem().remains();
            List<Verdict> verdicts = new ArrayList<>();
            verdicts.add(Verdict.of("hurt-bodies-are-drawn-wounded", script.hurt > 0 && script.wounded == script.hurt,
                    script.wounded + " of " + script.hurt + " hurt bodies alive and below their wounded share"));
            verdicts.add(Verdict.of("every-death-leaves-remains", remains.size() == script.deaths.size(),
                    remains.size() + " remains for " + script.deaths.size() + " dead bodies"));
            int misplaced = 0, repainted = 0;
            for (TurretDemolitionSystem.Remains r : remains) {
                Death death = nearest(script.deaths, r.x(), r.y());
                if (death == null || Math.hypot(death.x - r.x(), death.y - r.y()) > 0.01) misplaced++;
                else if (death.structure != r.structure() || death.variant != r.variant()) repainted++;
            }
            verdicts.add(Verdict.of("remains-lie-where-the-body-stood", misplaced == 0,
                    misplaced + " of " + remains.size() + " remains away from where their body stood"));
            verdicts.add(Verdict.of("remains-wear-the-body's-painting", repainted == 0,
                    repainted + " of " + remains.size() + " remains in another structure's or painting's art"));
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("deaths", script.deaths.size());
            metrics.put("remains", remains.size());
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }

    private static String phase(int tick) {
        if (tick < HURT_TICK) return "whole";
        if (tick < COLONY_TICK) return "wounded";
        if (tick < BORE_TICK) return "a colony killed";
        return "bore killed";
    }

    private static Death nearest(List<Death> deaths, float x, float y) {
        Death best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Death d : deaths) {
            double distance = Math.hypot(d.x - x, d.y - y);
            if (distance < bestDistance) {
                best = d;
                bestDistance = distance;
            }
        }
        return best;
    }

    private record Death(StructureDef structure, int variant, float x, float y) { }

    private static final class Script implements TickObserver {
        private final SwarmBore bore;
        private final Map<Long, Death> standing = new LinkedHashMap<>();
        final List<Death> deaths = new ArrayList<>();
        int hurt;
        int wounded;

        Script(SwarmBore bore) {
            this.bore = bore;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            List<Long> bodies = new ArrayList<>();
            bodies.add(bore.entityId());
            bodies.addAll(bore.colonies());
            for (long id : bodies) {
                if (sim.resolveUnit(id) == 0L) {
                    Death last = standing.remove(id);
                    if (last != null) deaths.add(last);
                    continue;
                }
                StructureDef structure = sim.turretState().structure(id);
                if (structure.art == null || structure.art.remainsPath == null) continue;
                standing.put(id, new Death(structure, structure.art.variantFor(id), sim.world().x(id), sim.world().y(id)));
            }
            if (tick == HURT_TICK) {
                for (long id : bodies) {
                    if (sim.resolveUnit(id) == 0L) continue;
                    StructureDef structure = sim.turretState().structure(id);
                    // Below half of structure and armour together: armour first, then structure.
                    float full = structure.maxStructure + structure.armorCapacity;
                    sim.applyDamage(id, full * 0.6f, 100f);
                }
            }
            if (tick == HURT_TICK + 1) {
                for (long id : bodies) {
                    hurt++;
                    if (sim.resolveUnit(id) == 0L) continue;
                    StructureDef structure = sim.turretState().structure(id);
                    float full = structure.maxStructure + structure.armorCapacity;
                    float left = (sim.world().hp(id) + sim.world().armor(id)) / full;
                    if (structure.art.woundedAt(left)) wounded++;
                }
            }
            if (tick == COLONY_TICK && !bore.colonies().isEmpty()) {
                sim.applyDamage(bore.colonies().get(0), 1_000_000f, 100f);
            }
            if (tick == BORE_TICK) sim.applyDamage(bore.entityId(), 1_000_000f, 100f);
        }
    }
}
