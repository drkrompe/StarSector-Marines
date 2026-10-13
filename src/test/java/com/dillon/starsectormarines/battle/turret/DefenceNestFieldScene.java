package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.AirFireOrder;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A defence-nest field and what it is a threat to: aircraft and people, and not a capital. Three loops over one
 * ground.
 *
 * <ul>
 *   <li>{@code control}: no nests. A hostile boat flies the length of the field to a landing zone beyond it and sets
 *       down, and a section of marines stands in the open where the nests would reach. Nobody is hurt.</li>
 *   <li>{@code field}: the same world with eight nests (dual flak alternating with heavy machine gun, as the stage
 *       lays them) either side of the boat's track. The boat is shot at on the way in, and the marines standing
 *       in reach are shot.</li>
 *   <li>{@code capital}: no boat, no marines in reach. An Onslaught holds its station over the same field with its
 *       shield down and its guns told not to fire. The nests are shooting at it and it loses next to nothing: ordinary
 *       ground-to-ship fire stays weak, which is why a nest is not a barrier to a ship.</li>
 * </ul>
 */
public final class DefenceNestFieldScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 64;
    private static final int TICKS = 1500;
    private static final float LZ_X = 20.5f;
    private static final float LZ_Y = 32.5f;
    private static final float ENTRY_X = 199.5f;
    private static final int SECTION = 6;
    private static final int[] NEST_XS = {70, 95, 120, 145};
    private static final int[] NEST_YS = {24, 40};
    private static final float SHIP_X = 110f;
    private static final float SHIP_Y = 32f;

    @Override public String id() { return "defence-nest-field"; }

    @Override public String label() {
        return "Defence nests: a field of small hardened emplacements shoots at a boat crossing it and at people in "
                + "reach, and does next to nothing to a capital holding station over it";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        Run control = playOne("control", frames);
        Run field = playOne("field", frames);
        Run capital = playOne("capital", frames);
        List<SceneReport> reports = new ArrayList<>();
        reports.add(report("control", control, control));
        reports.add(report("field", field, control));
        reports.add(report("capital", capital, control));
        return reports;
    }

    private record Run(Trace trace, BattleSimulation sim, Warship ship) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean capital = loop.equals("capital");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 195, 6);
        // The company's own, a long way off, so the player's fog reveals the field and both sides stay on the map.
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, 60);
        if (!capital) {
            for (int i = 0; i < SECTION; i++) {
                builder.unit("section-" + i, Faction.MARINE, UnitType.MARINE, 104 + (i % 3), 31 + (i / 3));
            }
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261014L);

        if (!loop.equals("control")) {
            List<DefensePost> nests = new ArrayList<>();
            int n = 0;
            for (int x : NEST_XS) {
                for (int y : NEST_YS) {
                    String gun = n++ % 2 == 0
                            ? TurretCatalogRegistry.DUAL_FLAK_STRUCTURE_ID : TurretCatalogRegistry.HEAVY_MG_STRUCTURE_ID;
                    nests.add(new DefensePost(DefensePostKind.LIGHT, x, y,
                            List.of(new DefensePost.TurretSpec(gun, x, y))));
                }
            }
            BattleSetup.spawnDefensePostTurrets(sim, nests);
        }

        long boat = -1L;
        Warship ship = null;
        if (capital) {
            ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, SHIP_X, SHIP_Y, 90f);
            ship.stationTo(SHIP_X, SHIP_Y);
            ship.airFire(AirFireOrder.OFF);
            ship.shieldAuto(false);
            ship.raiseShield(false);
            sim.addWarship(ship);
        } else {
            boat = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE, LZ_X, LZ_Y, ENTRY_X, LZ_Y, ENTRY_X, LZ_Y,
                    2f, 6);
        }
        Trace trace = new Trace(boat, ship);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, 12, tick -> String.format(Locale.ROOT, "%s / tick %d / boat %s / section alive %d%s",
                        loop, tick, trace.boatState, trace.sectionAlive,
                        trace.ship == null ? "" : String.format(Locale.ROOT, " / hull %.1f%%", 100f * trace.ship.hullFraction())))
                .run(TICKS);
        return new Run(trace, sim, ship);
    }

    private SceneReport report(String loop, Run run, Run control) {
        Trace t = run.trace();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "control" -> {
                verdicts.add(Verdict.of("the-boat-sets-down", t.landedTick >= 0 && t.lossTick < 0,
                        "touched down at tick " + t.landedTick + ", lost " + t.lossTick));
                verdicts.add(Verdict.of("nobody-is-hurt", t.sectionAlive == SECTION,
                        t.sectionAlive + " of " + SECTION + " alive"));
            }
            case "field" -> {
                verdicts.add(Verdict.of("people-in-reach-are-shot", t.sectionAlive < SECTION,
                        t.sectionAlive + " of " + SECTION + " alive against " + control.trace().sectionAlive + " without nests"));
                verdicts.add(Verdict.of("the-boat-is-denied-its-landing",
                        t.lossTick >= 0 || t.landedTick < 0 || t.landedTick > control.trace().landedTick,
                        "lost at tick " + t.lossTick + ", touched down at tick " + t.landedTick
                                + " against " + control.trace().landedTick + " without nests"));
            }
            default -> {
                float hull = run.ship().hullFraction();
                verdicts.add(Verdict.of("the-capital-shrugs-it-off", hull >= 0.97f,
                        String.format(Locale.ROOT, "hull %.2f%% after %d ticks over a live field", 100f * hull, TICKS)));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("sectionAlive", t.sectionAlive);
        metrics.put("landedTick", t.landedTick);
        metrics.put("lossTick", t.lossTick);
        if (run.ship() != null) metrics.put("hullPercent", Math.round(1000f * run.ship().hullFraction()) / 10f);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static final class Trace implements TickObserver {
        private final long boat;
        final Warship ship;
        int landedTick = -1;
        int lossTick = -1;
        int sectionAlive = SECTION;
        String boatState = "-";
        private int losses;

        Trace(long boat, Warship ship) {
            this.boat = boat;
            this.ship = ship;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (boat >= 0) {
                ShuttleMission mission = sim.world().mission(boat);
                if (mission != null) {
                    boatState = mission.state.toString();
                    if (landedTick < 0 && mission.state == ShuttleState.LANDED) landedTick = tick;
                }
                List<AirLoss> lost = sim.getAirLosses();
                if (lost.size() > losses) {
                    losses = lost.size();
                    if (lossTick < 0) lossTick = tick;
                }
            }
            if (ship == null) {
                int alive = 0;
                for (int i = 0, n = sim.getRoster().liveCount(); i < n; i++) {
                    long u = sim.getRoster().denseArray()[i];
                    if (sim.getRoster().identity().faction(u) != Faction.MARINE) continue;
                    float x = sim.world().x(u);
                    if (x > 90f && x < 130f) alive++;
                }
                sectionAlive = alive;
            }
        }
    }
}
