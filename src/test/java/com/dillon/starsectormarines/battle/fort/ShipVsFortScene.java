package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.battery.FortressBattery;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.fort.FortAirDefenceScene.Stamped;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.battle.scene.BehaviorScene;
import com.dillon.starsectormarines.battle.scene.FrameSink;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneReport;
import com.dillon.starsectormarines.battle.scene.SceneRun;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.scene.TickObserver;
import com.dillon.starsectormarines.battle.scene.Verdict;
import com.dillon.starsectormarines.battle.shield.ShieldDomeService.DomeView;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What a fort does to the company's heavy support, measured rather than judged: an Onslaught standing in a fort's
 * reach under its own director, and a called Mudskipper gunship circling over the garrison, each against a battery
 * fort and a shield fort, with and without the optional anti-air aspect, and against a bare garrison for a baseline.
 *
 * <p>Nothing here is a balance target. The verdicts are mechanics (the ship fires, the guns fire on it, the dome takes
 * rounds, the yard shoots at aircraft and not at hulls) and the readings are the evidence: hull, flux and shield
 * collapses of the ship; battery rounds; dome hits and overloads; nests and aspects lost; the gunship's hull and the
 * moment it fell. The company fights from its own side of the map and the forts are never weakened.
 *
 * <p>Loops: {@code no-fort}, {@code battery}, {@code battery-aa}, {@code shield-fort}, {@code shield-fort-aa},
 * {@code gunship-battery}, {@code gunship-battery-aa}.
 */
public final class ShipVsFortScene implements BehaviorScene {

    private static final int TICKS = 3000;
    private static final int CALL_TICK = 90;
    private static final int GARRISON = 8;
    /** How far west of the fort the capital is held: inside the battery's reach, outside the nests'. */
    private static final float SHIP_STANDOFF = 38f;
    private static final int SPOTTER_X = 74;
    private static final int SUPPLIES = 10;

    @Override public String id() { return "ship-vs-fort"; }

    @Override public String label() {
        return "Ships against forts: an Onslaught in a battery fort's reach and against a shield fort's dome, and a called "
                + "gunship over the garrison, with and without the fort's anti-air aspect";
    }

    @Override public int frameDelayMillis() { return 250; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledWeaponSlots.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        try {
            Run bare = playOne("no-fort", frames);
            reports.add(report("no-fort", bare, bare));
            for (String loop : List.of("battery", "battery-aa", "shield-fort", "shield-fort-aa", "gunship-battery",
                    "gunship-battery-aa")) {
                reports.add(report(loop, playOne(loop, frames), bare));
            }
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
        return reports;
    }

    private static final class Supplies implements CommandPowerResources {
        int supplies = SUPPLIES;

        @Override public int availableSupplies() { return supplies; }

        @Override public boolean spendSupplies(int amount) {
            if (amount < 0 || supplies < amount) return false;
            supplies -= amount;
            return true;
        }
    }

    private record Run(Trace trace, Warship ship, Fort fort, BattleSimulation sim) {}

    private static boolean gunship(String loop) {
        return loop.startsWith("gunship");
    }

    private static boolean aa(String loop) {
        return loop.endsWith("-aa");
    }

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean noFort = loop.equals("no-fort");
        FortKind kind = loop.startsWith("shield") ? FortKind.SHIELD_FORT : FortKind.BATTERY_FORT;
        SceneBuilder builder;
        int[] garrisonCell;
        int[] spotterCell = null;
        if (noFort) {
            builder = SceneBuilder.openGround(FortAirDefenceScene.WIDTH, FortAirDefenceScene.HEIGHT).missionCompletion(false);
            garrisonCell = new int[]{FortAirDefenceScene.CX, FortAirDefenceScene.CY};
        } else {
            Stamped stamped = FortAirDefenceScene.stamp(kind, aa(loop));
            builder = SceneBuilder.over(stamped.grid(), stamped.topology()).posts(stamped.posts()).missionCompletion(false);
            var yard = stamped.placed().plan().airDefence();
            garrisonCell = FortAirDefenceScene.openNear(stamped.grid(),
                    stamped.placed().placement().cell(yard.centreX() + 5, yard.centreY() + 6));
            spotterCell = FortAirDefenceScene.openNear(stamped.grid(), new int[]{garrisonCell[0] - 2, garrisonCell[1]});
        }
        final int gx = garrisonCell[0];
        final int gy = garrisonCell[1];
        // The company's eyes: unarmed, so they sight for the ship and the radio without fighting anyone.
        builder.unit("spotter", Faction.MARINE, UnitType.MARINE, SPOTTER_X, FortAirDefenceScene.CY);
        // A called gunship needs the target in the company's sight, so those loops put an unkillable pair beside the garrison
        // (and make the garrison unkillable too, so nothing about who holds the ground changes mid-recording).
        float garrisonHealth = gunship(loop) ? 100_000f : 100f;
        if (gunship(loop)) {
            final int sx0 = spotterCell[0];
            final int sy0 = spotterCell[1];
            builder.squad("scouts").faction(Faction.MARINE).size(2).at(sx0, sy0).primary("weapon.smg").health(100_000f)
                    .stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, sx0, sy0)).done();
        }
        builder.squad("garrison").faction(Faction.DEFENDER).size(GARRISON).at(gx, gy).primary("weapon.smg")
                .health(garrisonHealth).stationary().assigned(id -> ObjectiveAssignment.defendTrack(id, gx, gy)).done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261017L);
        Fort fort = noFort ? null : sim.forts().forts().get(0);

        Warship ship = null;
        if (!gunship(loop)) {
            float sx = FortAirDefenceScene.CX - SHIP_STANDOFF;
            float sy = FortAirDefenceScene.CY;
            ship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, sx, sy, 90f);
            ship.stationTo(sx, sy);
            ship.direct(10f, true, 2);
            sim.addWarship(ship);
        }
        Supplies supplies = new Supplies();
        if (gunship(loop)) {
            sim.setCommandPowers(List.of(new CloseAirSupport()));
            sim.setCommandPowerResources(supplies);
        }
        Trace trace = new Trace(world, fort, ship, gx, gy);
        TickObserver script = (s, tick) -> {
            if (gunship(loop) && tick == CALL_TICK) {
                trace.targetSeen = s.playerSeesCell(gx, gy);
                s.getCommandPowerService().requestActivation(CloseAirSupport.ID, gx, gy);
            }
        };
        final Warship shown = ship;
        SceneRun.of(world).observe(script, trace)
                .frames(frames, loop, 60, tick -> trace.caption(loop, tick, shown))
                .run(TICKS);
        return new Run(trace, ship, fort, sim);
    }

    // ------------------------------------------------------------------------------------------------ verdicts

    private SceneReport report(String loop, Run run, Run bare) {
        Trace t = run.trace();
        Fort fort = run.fort();
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "no-fort" -> verdicts.add(Verdict.of("the-ship-shells-the-garrison", t.garrisonDead > 0,
                    t.garrisonDead + " of " + GARRISON + " defenders dead, hull low "
                            + Math.round(t.shipHullLow * 100f) + "%"));
            case "battery", "battery-aa" -> {
                verdicts.add(Verdict.of("the-battery-fires-on-the-ship", t.batteryShots > 0,
                        t.batteryShots + " ship-grade rounds, first at tick " + t.firstBatteryShot));
                verdicts.add(Verdict.of("the-ship-answers", t.shipFired,
                        "ship rounds seen " + t.shipFired + ", garrison dead " + t.garrisonDead));
                if (aa(loop)) {
                    verdicts.add(Verdict.of("the-yard-leaves-hulls-alone", t.yardRounds == 0,
                            t.yardRounds + " anti-air rounds fired with only a ship in reach"));
                }
            }
            case "shield-fort", "shield-fort-aa" -> {
                verdicts.add(Verdict.of("the-dome-takes-ship-fire", t.domeHits > 0,
                        t.domeHits + " rounds absorbed, " + t.domeOverloads + " overloads"));
                if (aa(loop)) {
                    verdicts.add(Verdict.of("the-yard-leaves-hulls-alone", t.yardRounds == 0,
                            t.yardRounds + " anti-air rounds fired with only a ship in reach"));
                }
            }
            case "gunship-battery", "gunship-battery-aa" -> {
                verdicts.add(Verdict.of("the-gunship-is-called", t.gunshipSeen,
                        "target seen " + t.targetSeen + ", craft first seen at tick " + t.gunshipFirstTick));
                if (aa(loop)) {
                    verdicts.add(Verdict.of("the-yard-fires-on-it", t.yardRounds > 0 || !t.gunshipSeen,
                            t.yardRounds + " yard rounds, " + t.guidedRounds + " guided"));
                } else {
                    verdicts.add(Verdict.of("no-yard-no-yard-rounds", t.yardRounds == 0 && fort.airMountIds().isEmpty(),
                            t.yardRounds + " yard rounds"));
                }
            }
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("garrisonDead", t.garrisonDead);
        metrics.put("shipHullLowPercent", Math.round(t.shipHullLow * 100f));
        metrics.put("shipFluxPeakPercent", Math.round(t.shipFluxPeak * 100f));
        metrics.put("shipOverloads", t.shipOverloads);
        metrics.put("shipShieldCollapses", t.shipCollapses);
        metrics.put("shipDeathTick", t.shipDeathTick);
        metrics.put("batteryShots", t.batteryShots);
        metrics.put("domeHits", t.domeHits);
        metrics.put("domeOverloads", t.domeOverloads);
        metrics.put("nestsLost", t.nestsLost);
        metrics.put("aspectsWrecked", t.aspectsWrecked);
        metrics.put("yardRounds", t.yardRounds);
        metrics.put("gunshipHullLowPercent", Math.round(t.gunshipHullLow * 100f));
        metrics.put("gunshipDownTick", t.gunshipDownTick);
        metrics.put("gunshipLeftTick", t.gunshipLeftTick);
        metrics.put("gunshipRounds", t.gunshipRounds);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    // ------------------------------------------------------------------------------------------------ trace

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final Fort fort;
        private final Warship ship;
        private final int gx;
        private final int gy;
        boolean targetSeen;
        boolean gunshipSeen;
        boolean shipFired;
        int gunshipFirstTick = -1;
        int gunshipDownTick = -1;
        int gunshipLeftTick = -1;
        int firstBatteryShot = -1;
        int batteryShots;
        int garrisonDead;
        int domeHits;
        int domeOverloads;
        int nestsLost;
        int aspectsWrecked;
        int yardRounds;
        int guidedRounds;
        int gunshipRounds;
        int shipOverloads;
        int shipCollapses;
        int shipDeathTick = -1;
        float shipHullLow = 1f;
        float shipFluxPeak;
        float gunshipHullLow = 1f;
        private long gunshipId;
        private ShuttleMission gunshipMission;
        private int lastGunshipAmmo = -1;
        private long guided;

        Trace(SceneWorld world, Fort fort, Warship ship, int gx, int gy) {
            this.world = world;
            this.fort = fort;
            this.ship = ship;
            this.gx = gx;
            this.gy = gy;
        }

        String caption(String loop, int tick, Warship shown) {
            String s = shown == null ? String.format(Locale.ROOT, "gunship hull low %.0f%%", gunshipHullLow * 100f)
                    : String.format(Locale.ROOT, "ship hull %.0f%% / flux %.0f%%", shown.hullFraction() * 100f,
                            shown.fluxFraction() * 100f);
            return String.format(Locale.ROOT, "%s / tick %d / %s / garrison dead %d / battery rounds %d / yard rounds %d",
                    loop, tick, s, garrisonDead, batteryShots, yardRounds);
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            int dead = 0;
            for (long u : world.members("garrison")) if (!sim.getRoster().isAliveById(u)) dead++;
            garrisonDead = dead;

            if (ship != null) {
                shipHullLow = Math.min(shipHullLow, ship.hullFraction());
                shipFluxPeak = Math.max(shipFluxPeak, ship.fluxFraction());
                shipOverloads = ship.overloadCount();
                shipCollapses = ship.shieldCollapses();
                if (ship.destroyed() && shipDeathTick < 0) shipDeathTick = tick;
                
            }
            if (fort != null) {
                FortressBattery battery = fort.battery();
                if (battery != null) {
                    int n = 0;
                    for (FortressBattery.Gun g : battery.guns()) n += g.shotsFired;
                    if (n > batteryShots && firstBatteryShot < 0) firstBatteryShot = tick;
                    batteryShots = n;
                }
                int lostNests = 0;
                for (long nest : fort.nestIds()) if (!sim.world().isAlive(nest)) lostNests++;
                nestsLost = lostNests;
                int wrecked = 0;
                for (Fort.Place p : fort.places()) if (p.wrecked()) wrecked++;
                aspectsWrecked = wrecked;
                int rounds = 0;
                for (long id : fort.airMountIds()) {
                    rounds += sim.telemetry().roundsFired(id);
                    if (guided == 0L && sim.world().isAlive(id)
                            && sim.turretState().structure(id).mount.weapon.guidance != null) guided = id;
                }
                yardRounds = rounds;
                if (guided != 0L) guidedRounds = sim.telemetry().roundsFired(guided);
            }
            for (DomeView d : sim.shieldDomes().domes()) {
                domeHits = Math.max(domeHits, d.hits());
                domeOverloads = Math.max(domeOverloads, d.overloads());
            }

            shipFired = shipFired || dead > 0 || nestsLost > 0 || aspectsWrecked > 0 || domeHits > 0;
            if (gunshipMission == null) {
                for (long craft : sim.getAirEntityIds()) {
                    ShuttleMission m = sim.world().mission(craft);
                    if (m == null || !m.gunship) continue;
                    gunshipId = craft;
                    gunshipMission = m;
                    gunshipSeen = true;
                    gunshipFirstTick = tick;
                    lastGunshipAmmo = -1;
                }
            }
            if (gunshipMission != null && gunshipDownTick < 0) {
                if (gunshipMission.destroyed || !sim.world().isAlive(gunshipId)) {
                    gunshipDownTick = tick;
                    gunshipHullLow = 0f;
                } else if (sim.world().airframe(gunshipId) != null) {
                    gunshipHullLow = Math.min(gunshipHullLow,
                            sim.world().hp(gunshipId) / sim.world().airframe(gunshipId).maxHp());
                    var turrets = sim.world().airTurrets(gunshipId);
                    if (turrets != null && turrets.mounts.length > 0) {
                        int ammo = turrets.mounts[0].ammo;
                        if (lastGunshipAmmo >= 0 && ammo < lastGunshipAmmo) gunshipRounds += lastGunshipAmmo - ammo;
                        lastGunshipAmmo = ammo;
                    }
                }
                if (gunshipMission.state == ShuttleState.GONE && gunshipLeftTick < 0) gunshipLeftTick = tick;
            }
        }
    }
}
