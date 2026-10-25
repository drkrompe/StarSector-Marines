package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A satellite bore at maw in an old bloom, its brood spire, and a marine line in reach (`colony-breach-nouns.md`,
 * Brood spire).
 * <ul>
 *   <li>{@code launch}: the line has a flak position. Matured creep grows one spire off the bore; the bore's surge
 *       launches a pack off it; a second surge while that pack is still in the air launches nothing.</li>
 *   <li>{@code no-aa}: the same line without flak. The spire grows; no surge launches a pack (the flier law).</li>
 *   <li>{@code withers}: the fixture destroys the bore. Its spire withers with it.</li>
 *   <li>{@code fresh}: the creep is not matured, as in a fresh Colony Breach. No spire grows.</li>
 * </ul>
 */
public final class BroodSpireScene implements BehaviorScene {

    static final int W = 120, H = 80;
    static final int BORE_X = 30, BORE_Y = 40;
    static final int LINE_X = 44, LINE_Y = 40;
    static final int TICKS = 900;
    static final int SECOND_SURGE = 300, KILL_TICK = 60;
    static final String FLAK = "structure.turret-anti-air-flak";

    @Override public String id() { return "brood-spire"; }

    @Override public String label() {
        return "Brood spire: grows only on old creep, launches a pack on its bore's surge under anti-air, never two"
                + " at once, and withers with its bore";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) {
        return List.of(run("launch", true, true, frames), run("no-aa", false, true, FrameSink.NONE),
                run("withers", true, true, FrameSink.NONE), run("fresh", true, false, FrameSink.NONE));
    }

    /** Opens the bore fed to maw on {@code sim} and, when {@code matured}, matures its creep (growing the spire). */
    static SwarmBore openMaw(BattleSimulation sim, int x, int y, boolean matured) {
        SwarmBore bore = sim.swarmBores().open(sim, 1, false, x, y);
        if (bore == null) throw new IllegalStateException("open ground refused a bore");
        for (int guard = 0; bore.tier() != BoreTier.MAW && guard < 64; guard++) sim.swarmBores().feed(sim, bore, 1f);
        sim.creepField();
        if (matured) sim.matureCreep();
        sim.configureSwarmFromBores(20261025L);
        return bore;
    }

    private SceneReport run(String loop, boolean antiAir, boolean matured, FrameSink frames) {
        SceneBuilder builder = SceneBuilder.openGround(W, H).seed(20261025L).missionCompletion(false);
        builder.squad("rifles").faction(Faction.MARINE).size(6).at(LINE_X, LINE_Y).primary("weapon.field-rifle")
                .stationary().done();
        SceneWorld scene = builder.build();
        try (BattleSimulation sim = scene.sim()) {
            SwarmBore bore = openMaw(sim, BORE_X, BORE_Y, matured);
            if (antiAir) sim.spawn(MapTurret.create("flak", Faction.MARINE, FLAK, LINE_X + 3, LINE_Y));
            BroodSpires spires = sim.broodSpires();
            int launchesAtSecond = -1;
            for (int tick = 1; tick <= TICKS; tick++) {
                if (tick == 1 || tick == SECOND_SURGE) sim.swarmBores().surgeNow(bore, sim.getSimTickIndex());
                if (tick == SECOND_SURGE) launchesAtSecond = spires.launches().size();
                if ("withers".equals(loop) && tick == KILL_TICK) sim.applyDamage(bore.entityId(), 1e7f, 1e7f);
                sim.advance(BattleSimulation.TICK_DT);
                if (tick % 15 == 0) {
                    frames.frame(loop, sim, tick, "brood spire / " + loop + " / tick " + tick + " / "
                            + spires.launches().size() + " packs / " + sim.swarmFliers().packs().size() + " aloft");
                }
            }
            List<Verdict> verdicts = new ArrayList<>();
            int surges = 0;
            for (SwarmBores.Event e : sim.swarmBores().events()) if (e.kind() == SwarmBores.EventKind.SURGE) surges++;
            BroodSpires.Spire spire = spires.spires().isEmpty() ? null : spires.spires().get(0);
            float standoff = spire == null ? -1f
                    : (float) Math.hypot(spire.x - bore.x(), spire.y - bore.y());
            boolean standing = spire != null && sim.resolveUnit(spire.entityId) != 0L
                    && sim.world().isAlive(spire.entityId);
            switch (loop) {
                case "launch" -> {
                    verdicts.add(Verdict.of("a-spire-grows-off-the-maw", spires.spires().size() == 1
                                    && standoff >= BroodSpires.STANDOFF_MIN - 1f
                                    && standoff <= BroodSpires.STANDOFF_MAX + 1f,
                            spires.spires().size() + " spires, " + standoff + " cells from the bore"));
                    verdicts.add(Verdict.of("its-bore's-surge-launches-a-pack", launchesAtSecond == 1,
                            launchesAtSecond + " packs before the second surge, " + surges + " surges"));
                    verdicts.add(Verdict.of("no-second-pack-while-it-flies-or-inside-the-gap", spires.launches().size() == 1,
                            spires.launches().size() + " packs after the second surge, while the first flew"));
                }
                case "no-aa" -> {
                    verdicts.add(Verdict.of("the-spire-still-grows", spires.spires().size() == 1,
                            spires.spires().size() + " spires"));
                    verdicts.add(Verdict.of("no-pack-without-anti-air", spires.launches().isEmpty(),
                            spires.launches().size() + " packs over " + surges + " surges"));
                }
                case "withers" -> verdicts.add(Verdict.of("it-withers-with-its-bore", bore.collapsed() && !standing,
                        "bore collapsed " + bore.collapsed() + ", spire standing " + standing));
                case "fresh" -> verdicts.add(Verdict.of("no-spire-on-fresh-creep", spires.spires().isEmpty(),
                        spires.spires().size() + " spires"));
                default -> throw new IllegalStateException(loop);
            }
            Map<String, Number> metrics = new LinkedHashMap<>();
            metrics.put("spires", spires.spires().size());
            metrics.put("packs", spires.launches().size());
            metrics.put("surges", surges);
            metrics.put("standoff", standoff);
            return new SceneReport(id(), loop, TICKS, verdicts, metrics);
        }
    }
}
