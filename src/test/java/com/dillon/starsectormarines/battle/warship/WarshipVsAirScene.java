package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.AirLoss;
import com.dillon.starsectormarines.battle.air.ShuttleMission;
import com.dillon.starsectormarines.battle.air.ShuttleState;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
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
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.WarshipAirRules.WeaponClass;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ship against air: what an Onslaught's guns do to aircraft, by the weapon and not
 * by the ship, in five loops.
 *
 * <ul>
 *   <li>{@code control-transport}: a hostile transport shuttle crosses the ship's
 *       reach and the ship is under no air order, as a ship always was. The boat
 *       lands and its people walk off.</li>
 *   <li>{@code lone-transport}: the same crossing under an explicit order to
 *       fire on aircraft. Every non-point-defence weapon in reach may fire on a
 *       transport; the point-defence weapons may not. The boat is shot down and
 *       its passengers are lost.</li>
 *   <li>{@code fighter-wing}: a wing of fighters runs in over the ship to strike
 *       marines under its guns. The large assault guns stay silent (measured by
 *       rounds per weapon id); flak, anti-fighter and medium guns engage and kill
 *       some.</li>
 *   <li>{@code doctrine-air-superiority}: the same wing, the ship's doctrine in
 *       charge. It takes the air-superiority posture, engages with the weapons
 *       that may, keeps its flux well under a ship pinned to bombard, and leaves a
 *       ground block outside its opportunity range alone.</li>
 *   <li>{@code doctrine-intercept-boat}: a defending ship under the intercept
 *       posture shoots the inbound marine boat in flight; with air fire switched
 *       off, the control, it can only deny the landing zone and the boat lands.</li>
 * </ul>
 */
public final class WarshipVsAirScene implements BehaviorScene {

    private static final int WIDTH = 200;
    private static final int HEIGHT = 64;
    private static final int TICKS = 1350;
    private static final float SHIP_X = 120f;
    private static final float SHIP_Y = 32f;
    private static final float LZ_X = 70.5f;
    private static final float LZ_Y = 32.5f;
    private static final float BOAT_DELAY_SECONDS = 3f;

    @Override public String id() { return "warship-vs-air"; }

    @Override public String label() {
        return "Warship versus air: the weapon decides what may fire at what; transports are shot by anything that "
                + "shoots ships, fighters by flak and medium guns only, and a doctrine does it while conserving flux";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        Run control = playOne("control-transport", frames);
        reports.add(report("control-transport", control, control, null));
        Run lone = playOne("lone-transport", frames);
        reports.add(report("lone-transport", lone, control, null));
        Run wingControl = playOne("control-wing", frames);
        reports.add(report("control-wing", wingControl, control, null));
        Run wing = playOne("fighter-wing", frames);
        reports.add(report("fighter-wing", wing, wingControl, null));
        Run bombard = playOne("bombard-control", frames);
        Run doctrine = playOne("doctrine-air-superiority", frames);
        reports.add(report("doctrine-air-superiority", doctrine, wingControl, bombard));
        Run denial = playOne("denial-control", frames);
        Run intercept = playOne("doctrine-intercept-boat", frames);
        reports.add(report("doctrine-intercept-boat", intercept, denial, null));
        return reports;
    }

    private record Run(Trace trace, Warship ship, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        boolean wing = loop.equals("fighter-wing") || loop.equals("doctrine-air-superiority")
                || loop.equals("bombard-control") || loop.equals("control-wing");
        boolean intercept = loop.equals("doctrine-intercept-boat") || loop.equals("denial-control");
        builder.unit("far-marine", Faction.MARINE, UnitType.MARINE, 5, 60);
        builder.unit("far-defender", Faction.DEFENDER, UnitType.MARINE, 195, 6);
        if (wing) {
            // The marines the wing is sent for stand under the ship's guns; the defenders' own people hold the east edge,
            // which tells the dispatcher which side the wing comes in from. A hostile block stands west, beyond the
            // opportunity range of a ship that is fighting for the air but inside its longest reach.
            for (int i = 0; i < 12; i++) {
                builder.unit("marine-" + i, Faction.MARINE, UnitType.MARINE, 128 + (i % 4) * 2, 29 + (i / 4) * 3);
            }
            // Four hostile clusters spread down the west, 30 to 40 cells from the ship: inside the reach of most of its
            // guns, outside a ship at air superiority's shots-of-opportunity range, and apart enough to hold it busy.
            for (int c = 0; c < 4; c++) {
                for (int i = 0; i < 8; i++) {
                    builder.unit("block-" + c + "-" + i, Faction.DEFENDER, UnitType.MARINE,
                            82 + (i % 4) * 2, 8 + c * 16 + (i / 4) * 2);
                }
            }
            for (int i = 0; i < 6; i++) {
                builder.unit("edge-" + i, Faction.DEFENDER, UnitType.MARINE, 196 + (i % 3), 22 + i * 3);
            }
        }
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261012L);
        long boat = -1L;
        Faction shipSide = intercept ? Faction.DEFENDER : Faction.MARINE;
        if (loop.endsWith("transport")) {
            boat = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER, LZ_X, LZ_Y, 199.5f, LZ_Y, 199.5f, LZ_Y,
                    BOAT_DELAY_SECONDS, 6);
        } else if (intercept) {
            boat = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.MARINE, LZ_X, LZ_Y, 199.5f, LZ_Y, 199.5f, LZ_Y,
                    BOAT_DELAY_SECONDS, 6);
        }
        if (wing) {
            sim.setFlybyRoster(new FlybyRoster(List.of(
                    new FighterWing(FighterProfile.BROADSWORD, Faction.DEFENDER, 4, 3f, 2.5f),
                    new FighterWing(FighterProfile.THUNDER, Faction.DEFENDER, 4, 4f, 2.5f))));
        }
        Warship ship = Warship.fit("onslaught", "onslaught_Standard", shipSide, SHIP_X, SHIP_Y, 90f);
        ship.stationTo(SHIP_X, SHIP_Y);
        switch (loop) {
            case "control-transport", "denial-control" -> ship.airFire(AirFireOrder.OFF);
            case "control-wing" -> ship.airFire(AirFireOrder.OFF);
            case "lone-transport", "fighter-wing" -> ship.airFire(AirFireOrder.ALL);
            case "bombard-control" -> {
                ship.airFire(AirFireOrder.ALL);
                ship.direct(8f, true, 3);
            }
            default -> ship.direct(8f, true, 3);
        }
        if (loop.equals("doctrine-air-superiority")) {
            ship.useDoctrine();
            // The fleet is busy with a hostile air arm: declared before the first wing is seen, because a ship that
            // waits to count two armed craft has by then already opened fire on the ground.
            ship.declareAirThreat(1f, 25f);
        } else if (intercept) {
            // A capital swings slowly: the defender already lies on the heading that brings its guns onto the zone.
            float heading = WarshipFacing.bestHeading(ship, LZ_X, LZ_Y);
            ship.facingDegrees = heading;
            ship.desiredHeading = heading;
            ship.useDoctrine();
            ship.stance(MissionStance.DEFEND);
        }
        sim.addWarship(ship);
        // The two controls that only exist to be compared against are measured, not drawn.
        boolean recorded = !loop.equals("bombard-control") && !loop.equals("denial-control");
        Trace trace = new Trace(loop.endsWith("transport") ? Faction.DEFENDER : intercept ? Faction.MARINE : null, boat, ship);
        SceneRun.of(world).observe(trace)
                .frames(recorded ? frames : FrameSink.NONE, loop, 9, tick -> String.format(Locale.ROOT, "%s / tick %d / %s / air rounds %d hits %d / losses %d",
                        loop, tick, trace.postureNow, sim.warships().airFire().roundsFired(),
                        sim.warships().airFire().roundsHit(), sim.getAirLosses().size()))
                .run(TICKS);
        return new Run(trace, ship, sim);
    }

    private SceneReport report(String loop, Run run, Run control, Run bombard) {
        Trace t = run.trace();
        BattleSimulation sim = run.sim();
        WarshipAirFire air = sim.warships().airFire();
        Map<WeaponClass, Integer> byClass = roundsByClass(run.ship(), air);
        int pdRounds = byClass.getOrDefault(WeaponClass.POINT_DEFENCE, 0);
        int flakRounds = byClass.getOrDefault(WeaponClass.FLAK, 0);
        int assaultRounds = byClass.getOrDefault(WeaponClass.ASSAULT, 0);
        List<Verdict> verdicts = new ArrayList<>();
        switch (loop) {
            case "control-transport" -> {
                verdicts.add(Verdict.of("the-boat-lands", t.landedTick >= 0,
                        "touched down at tick " + t.landedTick + ", boat " + t.boatEnd));
                verdicts.add(Verdict.of("no-aircraft-fire", air.roundsFired() == 0 && sim.getAirLosses().isEmpty(),
                        air.roundsFired() + " rounds at aircraft, " + sim.getAirLosses().size() + " losses"));
                verdicts.add(Verdict.of("its-people-walk-off", t.peakPeopleNearLz > 0,
                        t.peakPeopleNearLz + " people at most near the landing zone"));
            }
            case "lone-transport" -> {
                verdicts.add(Verdict.of("the-boat-is-shot-down", t.lossTick >= 0 && t.landedTick < 0,
                        "lost at tick " + t.lossTick + " in state " + t.lastStateBeforeLoss + ", touchdown " + t.landedTick));
                verdicts.add(Verdict.of("passengers-are-lost", t.passengersLost == 6 && t.peakPeopleNearLz == 0,
                        t.passengersLost + " aboard when she went down, " + t.peakPeopleNearLz + " ever near the zone"));
                verdicts.add(Verdict.of("point-defence-stays-silent", pdRounds == 0,
                        pdRounds + " point-defence rounds at a transport (flak " + flakRounds + ", assault " + assaultRounds + ")"));
                verdicts.add(Verdict.of("the-big-guns-may-fire-on-it", assaultRounds + flakRounds > 0 && air.roundsHit() > 0,
                        "rounds by weapon " + air.roundsByWeapon() + ", hits " + air.hitsByWeapon()));
            }
            case "control-wing" -> {
                verdicts.add(Verdict.of("the-wing-flew", t.fightersSeen >= 3, t.fightersSeen + " fighters seen in the air"));
                verdicts.add(Verdict.of("no-aircraft-fire", air.roundsFired() == 0 && t.fighterLosses == 0,
                        air.roundsFired() + " rounds at aircraft, " + t.fighterLosses + " fighters lost"));
            }
            case "fighter-wing" -> {
                verdicts.add(Verdict.of("the-wing-flew", t.fightersSeen >= 3, t.fightersSeen + " fighters seen in the air"));
                verdicts.add(Verdict.of("assault-guns-stay-silent", assaultRounds == 0,
                        assaultRounds + " rounds from assault-class weapons at fighters; by weapon " + air.roundsByWeapon()));
                verdicts.add(Verdict.of("flak-and-medium-guns-engage", pdRounds > 0 && flakRounds > 0,
                        "point defence " + pdRounds + ", flak-class " + flakRounds));
                verdicts.add(Verdict.of("some-fighters-are-killed", t.fighterLosses >= 1,
                        t.fighterLosses + " of " + t.fightersSeen + " fighters lost"));
                verdicts.add(Verdict.of("the-marines-are-better-off", t.marinesAlive >= control.trace().marinesAlive,
                        t.marinesAlive + " marines alive against " + control.trace().marinesAlive + " with no air fire"));
            }
            case "doctrine-air-superiority" -> {
                verdicts.add(Verdict.of("takes-the-air-fight", t.sawAirSuperiority,
                        "first air-superiority posture at tick " + t.firstAirPostureTick + ", reason: " + t.airReason));
                verdicts.add(Verdict.of("assault-guns-stay-silent", assaultRounds == 0,
                        assaultRounds + " rounds from assault-class weapons at fighters"));
                verdicts.add(Verdict.of("engages-and-kills", air.roundsFired() > 0 && t.fighterLosses >= 1,
                        air.roundsFired() + " rounds, " + air.roundsHit() + " hits, " + t.fighterLosses + " fighters lost"));
                // Over the ticks the doctrine ship held the air-superiority posture, against the same ticks of the same
                // ship at bombard: what it spent and what it fired at the ground.
                int window = t.windowTicks();
                float peak = t.peakFluxIn(t.airPosture);
                float bombardPeak = bombard.trace().peakFluxIn(t.airPosture);
                int ground = t.groundIn(t.airPosture);
                int bombardGround = bombard.trace().groundIn(t.airPosture);
                verdicts.add(Verdict.of("conserves-flux", window > 0 && peak < bombardPeak && ground < bombardGround,
                        String.format(Locale.ROOT, "over %d ticks at air superiority: peak flux %.2f and %d ground rounds, against %.2f and %d for the same ship at bombard",
                                window, peak, ground, bombardPeak, bombardGround)));
                int last = t.lastTickIn(t.airPosture);
                verdicts.add(Verdict.of("leaves-the-ground-block-alone",
                        t.blockAt(last) >= 30 && t.blockAt(last) > bombard.trace().blockAt(last),
                        t.blockAt(last) + " of 32 hostile ground units alive when the air fight ended at tick " + last
                                + ", against " + bombard.trace().blockAt(last) + " for the same ship at bombard"));
            }
            default -> {
                verdicts.add(Verdict.of("is-intercepting", t.sawIntercept, "posture " + t.postureNow + ": " + run.ship().postureReason()));
                verdicts.add(Verdict.of("the-boat-is-shot-down-in-flight", t.lossTick >= 0 && t.landedTick < 0,
                        "lost at tick " + t.lossTick + " in state " + t.lastStateBeforeLoss + ", touchdown " + t.landedTick));
                verdicts.add(Verdict.of("passengers-are-lost", t.passengersLost == 6 && t.peakPeopleNearLz == 0,
                        t.passengersLost + " aboard when she went down, " + t.peakPeopleNearLz + " ever near the zone"));
                verdicts.add(Verdict.of("the-control-boat-lands", control.trace().landedTick >= 0 && control.trace().peakPeopleNearLz > 0,
                        "with air fire off the boat touched down at tick " + control.trace().landedTick + " and "
                                + control.trace().peakPeopleNearLz + " people stood near the zone"));
            }
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("airRounds", air.roundsFired());
        metrics.put("airHits", air.roundsHit());
        metrics.put("airEngagements", air.engagements());
        metrics.put("airDamage", Math.round(air.damageLanded()));
        metrics.put("pointDefenceRounds", pdRounds);
        metrics.put("flakClassRounds", flakRounds);
        metrics.put("assaultClassRounds", assaultRounds);
        metrics.put("airLosses", sim.getAirLosses().size());
        metrics.put("fighterLosses", t.fighterLosses);
        metrics.put("fightersSeen", t.fightersSeen);
        metrics.put("lossTick", t.lossTick);
        metrics.put("touchdownTick", t.landedTick);
        metrics.put("passengersLost", t.passengersLost);
        metrics.put("peakPeopleNearLz", t.peakPeopleNearLz);
        metrics.put("peakFlux", Math.round(t.peakFlux * 100f) / 100f);
        metrics.put("blockAlive", t.blockAlive);
        metrics.put("marinesAlive", t.marinesAlive);
        metrics.put("groundRounds", groundRounds(run.ship(), air));
        metrics.put("firstAirPostureTick", t.firstAirPostureTick);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** Rounds the ship's guns (point defence excluded) fired at ground: everything they fired less what went at aircraft. */
    private static int groundRounds(Warship ship, WarshipAirFire air) {
        int ground = 0;
        for (Warship.Mount m : ship.mounts()) {
            if (m.pointDefence()) continue;
            ground += m.shotsFired;
        }
        int atAir = 0;
        Set<String> seen = new HashSet<>();
        for (Warship.Mount m : ship.mounts()) {
            if (m.pointDefence() || !seen.add(m.weapon.id())) continue;
            atAir += air.roundsByWeapon().getOrDefault(m.weapon.id(), 0);
        }
        return Math.max(0, ground - atAir);
    }

    private static Map<WeaponClass, Integer> roundsByClass(Warship ship, WarshipAirFire air) {
        Map<WeaponClass, Integer> out = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Warship.Mount m : ship.mounts()) {
            if (!seen.add(m.weapon.id())) continue;
            int rounds = air.roundsByWeapon().getOrDefault(m.weapon.id(), 0);
            out.merge(WarshipAirRules.classify(m), rounds, Integer::sum);
        }
        return out;
    }

    private static final class Trace implements TickObserver {
        private final long boat;
        private final Faction passengers;
        private final Warship ship;
        private final Set<Long> fighters = new HashSet<>();
        int fightersSeen;
        int fighterLosses;
        int landedTick = -1;
        int lossTick = -1;
        int passengersLost;
        int peopleNearLz;
        int peakPeopleNearLz;
        final float[] flux = new float[TICKS + 2];
        final int[] ground = new int[TICKS + 2];
        final boolean[] airPosture = new boolean[TICKS + 2];
        final int[] blockSeries = new int[TICKS + 2];
        int blockAlive;
        int marinesAlive;
        int firstAirPostureTick = -1;
        float peakFlux;
        boolean sawAirSuperiority;
        boolean sawIntercept;
        String postureNow = "bombard";
        String airReason = "";
        String boatEnd = "?";
        String lastStateBeforeLoss = "?";
        private int losses;

        private Trace(Faction passengers, long boat, Warship ship) {
            this.passengers = passengers;
            this.boat = boat;
            this.ship = ship;
        }

        int lastTickIn(boolean[] mask) {
            int last = 0;
            for (int i = 0; i < mask.length; i++) if (mask[i]) last = i;
            return last;
        }

        int blockAt(int tick) {
            return blockSeries[Math.max(0, Math.min(tick, TICKS + 1))];
        }

        int windowTicks() {
            int n = 0;
            for (boolean b : airPosture) if (b) n++;
            return n;
        }

        float peakFluxIn(boolean[] mask) {
            float peak = 0f;
            for (int i = 0; i < mask.length; i++) if (mask[i]) peak = Math.max(peak, flux[i]);
            return peak;
        }

        /** Ground rounds fired over the ticks the mask names (a round counted on the tick it was fired). */
        int groundIn(boolean[] mask) {
            int total = 0;
            for (int i = 1; i < mask.length; i++) if (mask[i]) total += ground[i] - ground[i - 1];
            return total;
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (boat >= 0) {
                ShuttleMission mission = sim.world().mission(boat);
                if (mission != null) {
                    boatEnd = mission.state.toString();
                    lastStateBeforeLoss = boatEnd;
                    if (landedTick < 0 && mission.state == ShuttleState.LANDED) landedTick = tick;
                }
            }
            for (long id : sim.getAirEntityIds()) {
                if (sim.world().airframe(id) instanceof FighterProfile && fighters.add(id)) fightersSeen++;
            }
            List<AirLoss> lost = sim.getAirLosses();
            if (lost.size() > losses) {
                for (int i = losses; i < lost.size(); i++) {
                    AirLoss loss = lost.get(i);
                    if (loss.frame() instanceof FighterProfile) {
                        fighterLosses++;
                    } else if (lossTick < 0) {
                        lossTick = tick;
                        passengersLost = loss.passengersAboard();
                    }
                }
                losses = lost.size();
            }
            peakFlux = Math.max(peakFlux, ship.fluxFraction());
            postureNow = ship.posture().label();
            if (ship.posture() == WarshipPosture.AIR_SUPERIORITY && !sawAirSuperiority) {
                sawAirSuperiority = true;
                firstAirPostureTick = tick;
                airReason = ship.postureReason();
            }
            if (ship.posture() == WarshipPosture.INTERCEPT) sawIntercept = true;
            UnitRosterService roster = sim.getRoster();
            int near = 0;
            int block = 0;
            int marines = 0;
            for (int i = 0, n = roster.liveCount(); i < n; i++) {
                long u = roster.denseArray()[i];
                Faction f = roster.identity().faction(u);
                float ux = sim.world().x(u);
                float uy = sim.world().y(u);
                boolean ours = passengers != null && f == passengers;
                if (ours && Math.hypot(ux - LZ_X, uy - LZ_Y) < 30f && ux > 40f) near++;
                if (f == Faction.DEFENDER && ux > 75f && ux < 100f) block++;
                if (f == Faction.MARINE && ux > 100f && ux < 190f) marines++;
            }
            peopleNearLz = near;
            peakPeopleNearLz = Math.max(peakPeopleNearLz, near);
            int at = Math.min(tick, TICKS + 1);
            flux[at] = ship.fluxFraction();
            ground[at] = groundRounds(ship, sim.warships().airFire());
            airPosture[at] = ship.posture() == WarshipPosture.AIR_SUPERIORITY;
            blockAlive = block;
            blockSeries[at] = block;
            marinesAlive = marines;
        }
    }
}
