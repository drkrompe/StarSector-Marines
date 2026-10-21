package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.evacuation.BruteChargeSystem.Event;
import com.dillon.starsectormarines.battle.evacuation.BruteChargeSystem.Phase;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechSection;
import com.dillon.starsectormarines.battle.mech.MechSections;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
import com.dillon.starsectormarines.battle.vehicle.VehicleMission;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Brute's charge ({@code brute-charge.md}) against one kind of target per loop. The Brute has a deep
 * health pool and the marines are squadless and hold their fire: the scene is about what the charge
 * does, not who wins. Verdicts assert the mechanics of rule 4's table; counts are metrics.
 * <ul>
 *   <li>{@code infantry-line}: three marines along the line, the first in the charge band. The Brute
 *       decides to charge on its own; the first two are thrown and held and the charge goes on, the
 *       third stops it.</li>
 *   <li>{@code far-target}: one marine well beyond the band. The Brute walks until the band, then
 *       charges.</li>
 *   <li>{@code light-wall}: marines in a colony building with the door on the far side. The Brute
 *       charges the wall it means to break: one double-weight blow.</li>
 *   <li>{@code heavy-wall}: the same building at fortified strength, a charge at the wall scripted. The
 *       Brute is stunned and the wall untouched.</li>
 *   <li>{@code technical}, {@code apc}, {@code tank}: a hull in the band, a charge at it scripted where
 *       the Brute does not start one itself.</li>
 *   <li>{@code hound}, {@code bulwark}: a light and a heavy mech, the same way.</li>
 * </ul>
 */
public final class BruteChargeScene implements BehaviorScene {

    private static final int TICKS = 900;
    private static final int SCRIPT_TICK = 20;
    private static final float DEEP = 50_000f;

    @Override public String id() { return "brute-charge"; }

    @Override public String label() {
        return "Brute charge: tell, straight run, and what it does to infantry, walls, hulls and mechs";
    }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("infantry-line", "far-target", "light-wall", "heavy-wall",
                "technical", "apc", "tank", "hound", "bulwark")) {
            reports.add(playOne(loop, frames));
        }
        return reports;
    }

    private SceneReport playOne(String loop, FrameSink frames) throws Exception {
        boolean building = loop.endsWith("-wall");
        SwarmStrainGround ground = building
                ? new SwarmStrainGround(40, 28, 22, 10, 29, 17, loop.equals("heavy-wall") ? 240 : 100).door(30, 14)
                : null;
        NavigationGrid grid = building ? ground.grid : new NavigationGrid(40, 20);
        CellTopology topology = building ? ground.topology : new CellTopology(40, 20);
        if (!building) {
            for (int y = 0; y < 20; y++) for (int x = 0; x < 40; x++) grid.setWalkableFloor(x, y);
        }
        int lane = building ? 13 : 10;
        SceneBuilder builder = SceneBuilder.over(grid, topology).seed(20261021L).missionCompletion(false);
        switch (loop) {
            case "infantry-line" -> builder
                    .unit("m1", Faction.MARINE, UnitType.MARINE, 11, lane, spec -> spec.health(DEEP))
                    .unit("m2", Faction.MARINE, UnitType.MARINE, 13, lane, spec -> spec.health(DEEP))
                    .unit("m3", Faction.MARINE, UnitType.MARINE, 15, lane, spec -> spec.health(DEEP));
            case "far-target" -> builder
                    .unit("m1", Faction.MARINE, UnitType.MARINE, 25, lane, spec -> spec.health(DEEP));
            case "light-wall", "heavy-wall" -> builder
                    .unit("m1", Faction.MARINE, UnitType.MARINE, 24, 12, spec -> spec.health(DEEP))
                    .unit("m2", Faction.MARINE, UnitType.MARINE, 24, 15, spec -> spec.health(DEEP));
            case "hound", "bulwark" -> builder.squad("mech").faction(Faction.MARINE).size(1).at(11, lane)
                    .mech(loop.equals("hound") ? MechVariant.HOUND : MechVariant.BULWARK, MechRole.ASSAULT)
                    .stationary().health(DEEP).done();
            default -> { }
        }
        SceneWorld world = builder.build();
        try (BattleSimulation sim = world.sim()) {
            long target = switch (loop) {
                case "technical", "apc", "tank" -> {
                    VehicleType type = loop.equals("technical") ? VehicleType.MILITIA_TECHNICAL
                            : loop.equals("apc") ? VehicleType.HEAVY_APC : VehicleType.BREAKTHROUGH_TANK;
                    long v = sim.convoy().spawn(type, Faction.MARINE, VehicleMission.deployed(11, lane));
                    sim.convoy().body(v).teleport(11.5f, lane + .5f, 0f);
                    yield v;
                }
                case "hound", "bulwark" -> world.members("mech")[0];
                case "light-wall", "heavy-wall" -> 0L;
                default -> world.unit("m1");
            };
            int startX = building ? 12 : loop.equals("far-target") ? 3 : 5;
            long brute = sim.spawn(SwarmStrains.spec("Brute", UnitType.SWARM_BRUTE, startX, lane).health(DEEP));
            List<Long> bodies = new ArrayList<>();
            if (target != 0L) bodies.add(target);
            List<String> keys = switch (loop) {
                case "infantry-line" -> List.of("m1", "m2", "m3");
                case "far-target" -> List.of("m1");
                case "light-wall", "heavy-wall" -> List.of("m1", "m2");
                default -> List.of();
            };
            for (String key : keys) {
                if (world.unit(key) != target) bodies.add(world.unit(key));
            }
            Trace trace = new Trace(loop, brute, target, bodies.stream().mapToLong(Long::longValue).toArray(),
                    ground, sim);
            trace.remember(sim);
            SceneRun.of(world).observe(trace)
                    .frames(frames, loop, 6, tick -> loop + " / brute charge / tick " + tick)
                    .run(TICKS);
            return report(loop, trace, sim);
        }
    }

    private SceneReport report(String loop, Trace trace, BattleSimulation sim) {
        List<Event> events = sim.bruteCharges().events();
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("tell-precedes-run", trace.tellBeforeEveryRun,
                trace.tells + " tells, " + trace.runs + " runs, shortest tell " + trace.shortestTell + " ticks"));
        verdicts.add(Verdict.of("holds-its-line", trace.maxTurnPerTick
                        <= BruteChargeRules.TURN_RATE * BattleSimulation.TICK_DT + 1e-3f,
                "largest turn in a tick " + trace.maxTurnPerTick + " degrees"));
        verdicts.add(Verdict.of("cooldown-between-charges", trace.shortestGap < 0
                        || trace.shortestGap >= BruteChargeRules.ticks(BruteChargeRules.COOLDOWN),
                trace.shortestGap < 0 ? "one charge" : "shortest gap between tells " + trace.shortestGap + " ticks"));
        verdicts.add(Verdict.of("spares-its-own-side", !trace.hitOwnSide, "own side struck " + trace.hitOwnSide));
        switch (loop) {
            case "infantry-line" -> {
                verdicts.add(Verdict.of("charges", trace.tells > 0, trace.tells + " tells"));
                verdicts.add(Verdict.of("thrown-and-knocked-down", trace.infantryHits > 0
                                && trace.infantryHeld == trace.infantryHits && trace.infantryThrown == trace.infantryHits,
                        trace.infantryHits + " hits, " + trace.infantryThrown + " thrown, " + trace.infantryHeld + " held"));
                verdicts.add(Verdict.of("goes-through-two", trace.infantryHits > 1 && trace.chargesStoppedEarly == 0,
                        trace.infantryHits + " bodies hit; " + trace.chargesStoppedEarly + " charges ended on a body before the limit of "
                                + BruteChargeRules.MAX_BODIES));
            }
            case "far-target" -> verdicts.add(Verdict.of("walks-until-in-band", trace.tells > 0
                            && trace.firstTellDistance <= BruteChargeRules.MAX_BAND + .5f,
                    "first tell at " + trace.firstTellDistance + " cells"));
            case "light-wall" -> verdicts.add(Verdict.of("rams-double", trace.rams == 0 || trace.ramsDouble == trace.rams,
                    trace.rams == 0 ? "no charge reached the wall" : trace.ramsDouble + " of " + trace.rams
                            + " rams took " + BruteChargeRules.WALL_BLOW));
            case "heavy-wall" -> {
                verdicts.add(Verdict.of("stunned-by-the-wall", trace.bruteStunned, "stunned " + trace.bruteStunned));
                verdicts.add(Verdict.of("wall-untouched", trace.ringUnchanged(), "ring hp unchanged " + trace.ringUnchanged()));
            }
            case "technical" -> verdicts.add(Verdict.of("shoved-and-stalled", trace.targetHits > 0
                            && trace.targetHeld && trace.shove >= BruteChargeRules.SHOVE * .5f,
                    trace.targetHits + " hits, shoved " + trace.shove + ", stalled " + trace.targetHeld));
            case "apc" -> verdicts.add(Verdict.of("stalled-not-shoved", trace.targetHits > 0
                            && trace.targetHeld && trace.shove < .05f,
                    trace.targetHits + " hits, shoved " + trace.shove + ", stalled " + trace.targetHeld));
            case "tank" -> verdicts.add(Verdict.of("stuns-the-brute", trace.targetHits > 0 && trace.bruteStunned,
                    trace.targetHits + " hits, stunned " + trace.bruteStunned));
            case "hound" -> verdicts.add(Verdict.of("legs-hit-and-staggered", trace.targetHits > 0
                            && trace.legsHit && trace.targetHeld,
                    trace.targetHits + " hits, legs " + trace.legsHit + ", staggered " + trace.targetHeld));
            case "bulwark" -> verdicts.add(Verdict.of("legs-hit-keeps-its-feet", trace.targetHits > 0
                            && (trace.legsHit || trace.plateTurnedIt) && !trace.targetHeld,
                    trace.targetHits + " hits, legs " + trace.legsHit + (trace.plateTurnedIt ? " (plate turned it)" : "")
                            + ", staggered " + trace.targetHeld));
            default -> throw new IllegalStateException(loop);
        }
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("tells", trace.tells);
        metrics.put("runs", trace.runs);
        metrics.put("events", events.size());
        metrics.put("infantryHits", trace.infantryHits);
        metrics.put("targetHits", trace.targetHits);
        metrics.put("rams", trace.rams);
        metrics.put("shove", trace.shove);
        metrics.put("firstTellDistance", Float.isNaN(trace.firstTellDistance) ? -1f : trace.firstTellDistance);
        metrics.put("bruteDamageDealt", trace.damageDealt(sim));
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    /** Watches the charge record tick by tick; scripts the charge where the loop needs one. */
    private static final class Trace implements TickObserver {
        private final String loop;
        private final long brute;
        private final long target;
        private final SwarmStrainGround ground;
        private final int[] ringHp;
        private final float targetStartHp;
        private final float targetStartPool;
        private int seen;
        private int tells, runs, lastTellTick = -1, shortestTell = Integer.MAX_VALUE, shortestGap = -1;
        private boolean tellBeforeEveryRun = true, pendingTell;
        private float lastHeading = Float.NaN;
        private float maxTurnPerTick;
        private boolean hitOwnSide;
        private int infantryHits, infantryThrown, infantryHeld;
        private int bodiesThisCharge, chargesStoppedEarly;
        private int targetHits, rams, ramsDouble;
        private boolean targetHeld, bruteStunned, legsHit, plateTurnedIt;
        private float shove;
        private float firstTellDistance = Float.NaN;
        private final Map<Long, float[]> lastPos = new LinkedHashMap<>();
        private final long[] watched;

        Trace(String loop, long brute, long target, long[] watched, SwarmStrainGround ground, BattleSimulation sim) {
            this.loop = loop;
            this.watched = watched;
            this.brute = brute;
            this.target = target;
            this.ground = ground;
            if (ground != null) {
                int w = ground.grid.getWidth();
                ringHp = new int[w * ground.grid.getHeight()];
                for (int y = 0; y < ground.grid.getHeight(); y++) {
                    for (int x = 0; x < w; x++) ringHp[y * w + x] = ground.grid.getWallHp(x, y);
                }
            } else {
                ringHp = null;
            }
            targetStartHp = target == 0L ? 0f : sim.world().hp(target);
            targetStartPool = target == 0L || !sim.identity().type(target).isMech() ? 0f
                    : sim.world().hp(target) + sim.world().armor(target);
        }

        boolean ringUnchanged() {
            int w = ground.grid.getWidth();
            for (int y = 0; y < ground.grid.getHeight(); y++) {
                for (int x = 0; x < w; x++) {
                    if (ground.onRing(x, y) && ground.grid.getWallHp(x, y) != ringHp[y * w + x]) return false;
                }
            }
            return true;
        }

        float damageDealt(BattleSimulation sim) {
            return target == 0L || !sim.world().isAlive(target) ? 0f : targetStartHp - sim.world().hp(target);
        }

        @Override public void observe(BattleSimulation sim, int tick) {
            if (!sim.world().isAlive(brute)) return;
            BruteChargeSystem charges = sim.bruteCharges();
            if (tick == SCRIPT_TICK && charges.phase(brute) == Phase.IDLE) script(sim);

            List<Event> events = charges.events();
            for (; seen < events.size(); seen++) {
                Event e = events.get(seen);
                if (e.brute() != brute) continue;
                switch (e.what()) {
                    case "tell" -> {
                        if (lastTellTick >= 0) {
                            int gap = e.tick() - lastTellTick;
                            shortestGap = shortestGap < 0 ? gap : Math.min(shortestGap, gap);
                        }
                        lastTellTick = e.tick();
                        tells++;
                        pendingTell = true;
                        bodiesThisCharge = 0;
                        if (Float.isNaN(firstTellDistance) && target != 0L && sim.world().isAlive(target)) {
                            firstTellDistance = (float) Math.hypot(sim.world().x(target) - sim.world().x(brute),
                                    sim.world().y(target) - sim.world().y(brute));
                        }
                    }
                    case "run" -> {
                        runs++;
                        if (!pendingTell) tellBeforeEveryRun = false;
                        shortestTell = Math.min(shortestTell, e.tick() - lastTellTick);
                        if (e.tick() - lastTellTick < BruteChargeRules.ticks(BruteChargeRules.TELL) - 1) {
                            tellBeforeEveryRun = false;
                        }
                        pendingTell = false;
                    }
                    case "stunned" -> bruteStunned = true;
                    case "ram" -> {
                        rams++;
                        int w = ground.grid.getWidth();
                        int before = ringHp[e.cellY() * w + e.cellX()];
                        int now = ground.grid.getWallHp(e.cellX(), e.cellY());
                        if (before - now == BruteChargeRules.WALL_BLOW || (now == 0 && before <= BruteChargeRules.WALL_BLOW)) {
                            ramsDouble++;
                        }
                        ringHp[e.cellY() * w + e.cellX()] = now;
                    }
                    default -> {
                        if (e.target() == 0L) break;
                        if (sim.identity().faction(e.target()) == sim.identity().faction(brute)) hitOwnSide = true;
                        boolean held = sim.stagger().isStaggered(e.target());
                        if (e.what().equals("infantry")) {
                            infantryHits++;
                            if (held) infantryHeld++;
                            float[] was = lastPos.get(e.target());
                            if (was != null && Math.hypot(sim.world().x(e.target()) - was[0],
                                    sim.world().y(e.target()) - was[1]) >= .5f) {
                                infantryThrown++;
                            }
                            bodiesThisCharge++;
                            if (bodiesThisCharge <= BruteChargeRules.MAX_BODIES
                                    && charges.phase(brute) != Phase.RUN) {
                                chargesStoppedEarly++;
                            }
                        }
                        if (e.target() == target) {
                            targetHits++;
                            if (held) targetHeld = true;
                            float[] was = lastPos.get(target);
                            if (was != null && sim.convoy().isVehicle(target)) {
                                shove = Math.max(shove, (float) Math.hypot(sim.world().x(target) - was[0],
                                        sim.world().y(target) - was[1]));
                            }
                            if (sim.identity().type(target).isMech()) {
                                MechSections sections = sim.world().mechLoadout(target).sections();
                                // A hit the plate turns whole leaves armour and structure where they were.
                                if (sim.world().hp(target) + sim.world().armor(target) >= targetStartPool) plateTurnedIt = true;
                                legsHit |= sections != null && (sections.armor(MechSection.LEGS)
                                        < sections.maxArmor(MechSection.LEGS)
                                        || sections.structure(MechSection.LEGS) < sections.maxStructure(MechSection.LEGS));
                            }
                        }
                    }
                }
            }

            if (charges.phase(brute) == Phase.RUN) {
                float heading = charges.headingDegrees(brute);
                if (!Float.isNaN(lastHeading)) {
                    float turn = Math.abs(((heading - lastHeading) % 360f + 540f) % 360f - 180f);
                    maxTurnPerTick = Math.max(maxTurnPerTick, turn);
                }
                lastHeading = heading;
            } else {
                lastHeading = Float.NaN;
            }
            remember(sim);
        }

        /** Scripted charge for loops whose target the Brute's own hunting may not choose. */
        private void script(BattleSimulation sim) {
            switch (loop) {
                case "heavy-wall" -> sim.bruteCharges().request(brute, 0L, ground.minX - .5f, sim.world().y(brute));
                case "technical", "apc", "tank", "hound", "bulwark" ->
                        sim.bruteCharges().request(brute, target, sim.world().x(target), sim.world().y(target));
                default -> { }
            }
        }

        private void remember(BattleSimulation sim) {
            for (long id : watched) {
                if (sim.getRoster().isAliveById(id)) lastPos.put(id, new float[]{sim.world().x(id), sim.world().y(id)});
            }
        }
    }
}
