package com.dillon.starsectormarines.battle.warship;

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
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ship against ship in atmosphere, slice 1 of the frigate duel ({@code warship-behaviours.md}, ship against ship): two Wolf (H) frigates (the
 * Dormer family's, {@link Faction#ALLY}) against two Hound (P) frigates ({@link Faction#DEFENDER}) on stations within
 * reach of each other, with unarmed bystanders of both sides on the ground beneath.
 *
 * <p>The ships fire on each other with their real hull weapons under {@link ShipFireOrder#ALL}; nothing else is
 * ordered, and no outcome is set. The verdicts are mechanics (only weapons that may fire at a ship fire at one,
 * point defence never engages a round between hulls, a front shield takes hits from inside its arc as flux, an
 * unshielded hull takes them through the armour rule, a miss carries on to the ground, a lost hull falls before it
 * strikes and its crash hurts what it lands on); who lost what is a reading.
 *
 * <p>Loops: {@code duel} (two against two, stations held), {@code crossing} (one Hound flies back and forth across
 * the Wolfs' front, so its turns throw the Wolfs' lead off and rounds miss), {@code control} (the same duel with ship
 * fire off) and {@code wreck} (a Hound already battered to a tenth of its hull, over a squad of its own side).
 */
public final class FrigateDuelScene implements BehaviorScene {

    /**
     * The lines stand apart by forty cells times {@link WarshipScale#HULL_RANGE}, so a round's flight across them, and
     * with it what a crossing Hound's turns do to the Wolfs' lead, is the same at any hull range.
     */
    private static final float R = WarshipScale.HULL_RANGE;
    private static final float WOLF_X = 36f;
    private static final float HOUND_X = WOLF_X + 40f * R;
    private static final float CROSS_X = WOLF_X + 44f * R;
    static final int WIDTH = Math.round(CROSS_X + 40f);
    static final int HEIGHT = 68;
    private static final float NORTH_Y = 22f;
    private static final float SOUTH_Y = 46f;
    private static final String WOLF_HULL = "wolf_hegemony";
    private static final String WOLF_VARIANT = "wolf_hegemony_Assault";
    private static final String HOUND_HULL = "hound_d_pirates";
    private static final String HOUND_VARIANT = "hound_d_pirates_Standard";
    /** The window grows with the hull fight's durability, as the fight it watches does. */
    private static final int TICKS = Math.round(1500 * WarshipScale.HULL_DURABILITY);
    private static final int FRAME_EVERY = 6;

    @Override public String id() { return "frigate-duel"; }

    @Override public String label() {
        return "Frigate duel in atmosphere: two Wolf (H) against two Hound (P), hull weapons on hulls, stray rounds "
                + "on the ground and a lost hull falling as wreckage";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("duel", "crossing", "control", "wreck")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    /** The ships and the instruments of one loop. */
    private record Run(Trace trace, List<Warship> wolves, List<Warship> hounds, BattleSimulation sim) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean crossing = loop.equals("crossing");
        boolean wreck = loop.equals("wreck");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // Bystanders under each line: they fight nobody, so whatever hurts them came down from the duel.
        builder.squad("west-ground").faction(Faction.MARINE).size(6).at(36, 34).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(6)
                .at((int) HOUND_X, wreck ? (int) NORTH_Y : 34).unarmed().stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        // A grassy basin to read the duel over; the ground is scenery and changes nothing about the ships.
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }

        List<Warship> wolves = new ArrayList<>();
        List<Warship> hounds = new ArrayList<>();
        for (float y : new float[]{NORTH_Y, SOUTH_Y}) {
            wolves.add(ship(WOLF_HULL, WOLF_VARIANT, Faction.ALLY, WOLF_X, y, loop));
        }
        if (crossing) {
            Warship hound = ship(HOUND_HULL, HOUND_VARIANT, Faction.DEFENDER, CROSS_X, NORTH_Y - 8f, loop);
            hound.flyRoute(new float[][]{{CROSS_X, NORTH_Y - 8f}, {CROSS_X, SOUTH_Y + 8f}}, true);
            hounds.add(hound);
        } else {
            for (float y : new float[]{NORTH_Y, SOUTH_Y}) {
                hounds.add(ship(HOUND_HULL, HOUND_VARIANT, Faction.DEFENDER, HOUND_X, y, loop));
            }
        }
        if (wreck) {
            // Set up, not scripted: one Hound starts at about a tenth of its hull, so a loss is likely to be seen.
            Warship battered = hounds.get(0);
            battered.takeDamage(battered.maxHitPoints * 0.9f, 100f);
        }
        for (Warship w : wolves) sim.addWarship(w);
        for (Warship h : hounds) sim.addWarship(h);

        Trace trace = new Trace(world, wolves, hounds);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return new Run(trace, wolves, hounds, sim);
    }

    private static Warship ship(String hull, String variant, Faction side, float x, float y, String loop)
            throws Exception {
        // Facing each other across the line: the Wolfs east, the Hounds west, so neither starts with its guns on.
        Warship ship = Warship.fit(hull, variant, side, x, y, side == Faction.ALLY ? 0f : 180f);
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(loop.equals("control") ? ShipFireOrder.OFF : ShipFireOrder.ALL);
        // This scene is the stationary slugging match; the duel helm's own evidence is frigate-duel-helm.
        ship.duelHelm(false);
        return ship;
    }

    // ------------------------------------------------------------------------------------------------ verdicts

    private SceneReport report(String loop, Run run) {
        Trace t = run.trace();
        WarshipShipFire fire = run.sim().warships().shipFire();
        List<Verdict> verdicts = new ArrayList<>();
        Map<String, Integer> byWeapon = fire.roundsByWeapon();
        float houndLoss = t.hullLost(run.hounds());
        float wolfLoss = t.hullLost(run.wolves());
        if (loop.equals("control")) {
            verdicts.add(Verdict.of("no-round-at-a-hull", fire.roundsFired() == 0 && houndLoss == 0f && wolfLoss == 0f,
                    fire.roundsFired() + " rounds at hulls, hull lost " + Math.round(houndLoss + wolfLoss)));
        } else {
            boolean onlyShipGuns = true;
            for (String weapon : byWeapon.keySet()) {
                onlyShipGuns &= WarshipShipRules.mayFireAtHull(VanillaWeapons.get(weapon));
            }
            verdicts.add(Verdict.of("ships-fire-on-hulls-with-ship-guns", fire.roundsFired() > 0 && onlyShipGuns,
                    fire.roundsFired() + " rounds by weapon " + byWeapon));
            int intercepts = run.sim().warships().pointDefence().intercepts();
            // Point defence may stop a missile or its warheads (slice 3), never a direct-fire round between hulls.
            int missilesStopped = fire.missilesShotDown() + fire.warheadsShotDown();
            verdicts.add(Verdict.of("point-defence-leaves-rounds-between-hulls-alone", intercepts == missilesStopped,
                    intercepts + " bodies shot down, " + missilesStopped + " of them missiles or warheads"));
            int houndRounds = byWeapon.getOrDefault("chaingun", 0);
            int shieldHits = t.shieldHits(run.wolves());
            verdicts.add(Verdict.of("a-front-shield-takes-hits-as-flux", houndRounds == 0 || shieldHits > 0,
                    houndRounds + " chaingun rounds, " + shieldHits + " absorbed by Wolf shields, Wolf flux peak "
                            + Math.round(t.wolfFluxPeak * 100f) + "%"));
            verdicts.add(Verdict.of("an-unshielded-hull-takes-the-armour-rule",
                    fire.roundsOnHull() == 0 || houndLoss > 0f,
                    "Hounds lost " + Math.round(houndLoss) + " hull points to " + fire.roundsOnHull() + " rounds on hulls"));
        }
        if (loop.equals("crossing")) {
            verdicts.add(Verdict.of("a-miss-carries-on-to-the-ground", fire.roundsStrayed() > 0,
                    fire.roundsStrayed() + " of " + fire.roundsFired() + " rounds missed every hull and came down"));
        }
        List<WarshipWreck> wrecks = run.sim().warships().wrecks();
        boolean fell = true;
        StringBuilder falls = new StringBuilder();
        for (WarshipWreck w : wrecks) {
            boolean struckLate = w.struckAt() >= WarshipWreck.FALL_SECONDS - 1e-3f;
            float moved = (float) Math.hypot(w.struckX() - w.burstX(), w.struckY() - w.burstY());
            fell &= struckLate && moved > 0.5f && w.phase() == WarshipWreck.Phase.DOWN;
            falls.append(String.format(Locale.ROOT, "%s struck %.1fs after it burst, %.1f cells on, %s; ",
                    w.ship.hullId, w.struckAt(), moved, w.phase()));
        }
        verdicts.add(Verdict.of("a-lost-hull-falls-before-it-strikes", fell,
                wrecks.isEmpty() ? "no hull was lost" : falls.toString()));
        // A hull coming down comes back to its true landed size, and crushes what that size covers.
        boolean trueScale = true;
        StringBuilder sizes = new StringBuilder();
        for (WarshipWreck w : wrecks) {
            if (w.phase() == WarshipWreck.Phase.FALLING) continue;
            float drawn = w.ship.lengthCells * w.bodyScale();
            float landed = w.ship.lengthCells * LandedScale.berthFactor();
            trueScale &= Math.abs(drawn - landed) < 1e-3f && Math.abs(w.crushRadius() - 0.5f
                    * Math.max(w.ship.lengthCells, w.ship.widthCells) * LandedScale.berthFactor()) < 1e-3f;
            sizes.append(String.format(Locale.ROOT, "%s lies %.1f cells long (flight %.1f), crush %.1f; ",
                    w.ship.hullId, drawn, w.ship.lengthCells, w.crushRadius()));
        }
        verdicts.add(Verdict.of("a-wreck-lies-at-true-scale", trueScale,
                sizes.length() == 0 ? "no wreck on the ground" : sizes.toString()));
        if (loop.equals("wreck")) {
            boolean crashNear = t.crashNearGround;
            verdicts.add(Verdict.of("the-crash-hurts-what-it-lands-on", !crashNear || t.hurtByCrash > 0,
                    crashNear ? t.hurtByCrash + " of the squad under it hurt or killed at the crash"
                            : "no wreck struck near the squad"));
        }

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("roundsOnHull", fire.roundsOnHull());
        metrics.put("roundsStrayed", fire.roundsStrayed());
        metrics.put("wolfHullLost", Math.round(wolfLoss));
        metrics.put("houndHullLost", Math.round(houndLoss));
        metrics.put("wolfShieldHits", t.shieldHits(run.wolves()));
        metrics.put("wolfFluxPeakPercent", Math.round(t.wolfFluxPeak * 100f));
        metrics.put("wolfOverloads", t.overloads(run.wolves()));
        metrics.put("wolvesLost", t.lost(run.wolves()));
        metrics.put("houndsLost", t.lost(run.hounds()));
        metrics.put("firstHullLostTick", t.firstLossTick);
        metrics.put("westGroundHurt", t.hurt("west-ground"));
        metrics.put("eastGroundHurt", t.hurt("east-ground"));
        metrics.put("hurtByCrash", t.hurtByCrash);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    // ------------------------------------------------------------------------------------------------ trace

    private static final class Trace implements TickObserver {
        private final SceneWorld world;
        private final List<Warship> wolves;
        private final List<Warship> hounds;
        private final Map<Long, Float> startHp = new LinkedHashMap<>();
        private final Map<Long, Float> preCrashHp = new LinkedHashMap<>();
        float wolfFluxPeak;
        int firstLossTick = -1;
        int hurtByCrash;
        boolean crashNearGround;
        private boolean crashSeen;

        Trace(SceneWorld world, List<Warship> wolves, List<Warship> hounds) {
            this.world = world;
            this.wolves = wolves;
            this.hounds = hounds;
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "%s / tick %d", loop, tick));
            for (Warship w : wolves) s.append(describe("Wolf", w));
            for (Warship h : hounds) s.append(describe("Hound", h));
            return s.toString();
        }

        private static String describe(String name, Warship ship) {
            if (ship.destroyed()) return " / " + name + " down";
            return String.format(Locale.ROOT, " / %s %.0f%% flux %.0f%%%s", name, ship.hullFraction() * 100f,
                    ship.fluxFraction() * 100f, ship.shieldUp() ? " shield" : "");
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (tick == 0) {
                for (String key : List.of("west-ground", "east-ground")) {
                    for (long u : world.members(key)) startHp.put(u, sim.world().hp(u));
                }
            }
            for (Warship w : wolves) wolfFluxPeak = Math.max(wolfFluxPeak, w.fluxFraction());
            if (firstLossTick < 0) {
                for (Warship w : wolves) if (w.destroyed()) firstLossTick = tick;
                for (Warship h : hounds) if (h.destroyed()) firstLossTick = tick;
            }
            for (WarshipWreck wreck : sim.warships().wrecks()) {
                if (crashSeen) break;
                if (wreck.phase() == WarshipWreck.Phase.FALLING) {
                    // Remember the squad as it stands just before the strike, so only the crash is counted.
                    preCrashHp.clear();
                    for (long u : world.members("east-ground")) {
                        preCrashHp.put(u, sim.getRoster().isAliveById(u) ? sim.world().hp(u) : 0f);
                    }
                } else {
                    crashSeen = true;
                    for (long u : world.members("east-ground")) {
                        float before = preCrashHp.getOrDefault(u, 0f);
                        if (before <= 0f) continue;
                        float dx = sim.world().x(u) - wreck.struckX();
                        float dy = sim.world().y(u) - wreck.struckY();
                        if (Math.hypot(dx, dy) <= 6f) crashNearGround = true;
                    }
                }
            }
            if (crashSeen && hurtByCrash == 0 && !preCrashHp.isEmpty()) {
                int hurt = 0;
                for (Map.Entry<Long, Float> e : preCrashHp.entrySet()) {
                    if (e.getValue() <= 0f) continue;
                    boolean alive = sim.getRoster().isAliveById(e.getKey());
                    if (!alive || sim.world().hp(e.getKey()) < e.getValue()) hurt++;
                }
                hurtByCrash = hurt;
            }
        }

        float hullLost(List<Warship> ships) {
            float lost = 0f;
            for (Warship s : ships) lost += s.maxHitPoints - Math.max(0f, s.hitPoints());
            return lost;
        }

        int shieldHits(List<Warship> ships) {
            int hits = 0;
            for (Warship s : ships) hits += s.shieldHits();
            return hits;
        }

        int overloads(List<Warship> ships) {
            int n = 0;
            for (Warship s : ships) n += s.overloadCount();
            return n;
        }

        int lost(List<Warship> ships) {
            int n = 0;
            for (Warship s : ships) if (s.destroyed()) n++;
            return n;
        }

        int hurt(String key) {
            int n = 0;
            BattleSimulation sim = world.sim();
            for (long u : world.members(key)) {
                Float before = startHp.get(u);
                if (before == null) continue;
                if (!sim.getRoster().isAliveById(u) || sim.world().hp(u) < before) n++;
            }
            return n;
        }
    }
}
