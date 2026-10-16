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
import com.dillon.starsectormarines.battle.warship.WarshipPlating.Section;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Two capitals trading broadsides at a held station, for the warship armour grid ({@code warship-hull-damage.md}):
 * a Conquest of the company's allies against a defending Conquest, guns on each other, stations held and shields
 * held down, so the plate is the only thing between a round and the hull.
 *
 * <p>Verdicts are mechanics: rounds land on the side of a hull that faces the ship firing them; that side's plate
 * wears while the far side's does not; nothing reaches the core until a breach has opened; the control (the same
 * fight with no grid) has flat armour that never wears; and a front shield, asked of the side a round came from,
 * takes a round from ahead and leaves one from astern to the hull. Hull lost, breaches and when the first one
 * opened are readings.
 *
 * <p>Loops: {@code broadside}, {@code control} (no armour grid), {@code shield-astern} and {@code shield-ahead} (a
 * target with its front shield held up, fired on from behind and from in front).
 */
public final class HullBroadsideScene implements BehaviorScene {

    /** The ground and the spacing grow with the hulls' presence; laid out at a presence of four. */
    private static final float K = WarshipScale.HULL_PRESENCE / 4f;
    static final int WIDTH = Math.round(160 * K);
    static final int HEIGHT = Math.round(90 * K);
    static final float MID_X = WIDTH / 2f;
    static final float MID_Y = HEIGHT / 2f;
    /** Centre to centre across the line, in cells: inside the heavy guns' reach, clear of the other hull. */
    private static final float SEPARATION = 34f * K;
    /**
     * Across the line for the broadside loops: two Conquests beam to beam, clear of each other at their drawn size and
     * inside the Mark IXs' hull reach.
     */
    private static final float BROADSIDE_SEPARATION = 45f * K;
    /**
     * The broadside pair is a Conquest, whose battery is on its beams, so the heading rule keeps it broadside on of its
     * own accord; an Onslaught's heavy guns bear forward, and it turns its nose onto the target. The shield loops keep
     * the Onslaught for its front shield.
     */
    private static final String BROADSIDE_HULL = "conquest";
    private static final String BROADSIDE_VARIANT = "conquest_Standard";
    private static final String HULL = "onslaught";
    private static final String VARIANT = "onslaught_Standard";
    private static final int BROADSIDE_TICKS = 3600;
    private static final int SHIELD_TICKS = 900;
    // Two 1920x1080 views of a two-minute exchange: a frame every ten ticks ran the snapshot heap out.
    private static final int FRAME_EVERY = 20;
    /** Hull facing that points the nose east, toward +x (along is {@code (-sin, cos)}). */
    private static final float EAST = 270f;
    private static final float WEST = 90f;

    @Override public String id() { return "hull-broadside"; }

    @Override public String label() {
        return "Two Conquests trading broadsides: hits land on the side facing the shooter, strip its plate and "
                + "breach inward to the core; a front shield is asked of the side a round came from";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (String loop : List.of("broadside", "control", "shield-astern", "shield-ahead")) {
            reports.add(report(loop, playOne(loop, frames)));
        }
        return reports;
    }

    private record Run(Trace trace, Warship west, Warship east, BattleSimulation sim, int ticks) {}

    private Run playOne(String loop, FrameSink frames) throws Exception {
        boolean shieldLoop = loop.startsWith("shield");
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        // A lone bystander per side, far off, so the battle has both sides on the ground and keeps ticking.
        builder.squad("west-ground").faction(Faction.MARINE).size(1).at(6, 6).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(1).at(WIDTH - 6, HEIGHT - 6).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }

        Warship west;
        Warship east;
        if (shieldLoop) {
            // The target sits east; its nose points away from the shooter (astern) or at it (ahead).
            west = ship(HULL, VARIANT, Faction.ALLY, MID_X - SEPARATION * 0.5f, MID_Y, EAST, ShipFireOrder.ALL);
            east = ship(HULL, VARIANT, Faction.DEFENDER, MID_X + SEPARATION * 0.5f, MID_Y,
                    loop.equals("shield-astern") ? EAST : WEST, ShipFireOrder.OFF);
            east.raiseShield(true);
            // The shooter takes what it can bring to bear without swinging round; the target holds its heading.
        } else {
            west = ship(BROADSIDE_HULL, BROADSIDE_VARIANT, Faction.ALLY, MID_X - BROADSIDE_SEPARATION * 0.5f, MID_Y, 0f,
                    ShipFireOrder.ALL);
            east = ship(BROADSIDE_HULL, BROADSIDE_VARIANT, Faction.DEFENDER, MID_X + BROADSIDE_SEPARATION * 0.5f, MID_Y,
                    0f, ShipFireOrder.ALL);
            west.raiseShield(false);
            east.raiseShield(false);
        }
        if (loop.equals("control")) {
            west.plating(null);
            east.plating(null);
        }
        sim.addWarship(west);
        sim.addWarship(east);

        int ticks = shieldLoop ? SHIELD_TICKS : BROADSIDE_TICKS;
        Trace trace = new Trace(west, east);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(ticks);
        return new Run(trace, west, east, sim, ticks);
    }

    private static Warship ship(String hull, String variant, Faction side, float x, float y, float facing,
                                ShipFireOrder order) throws Exception {
        Warship ship = Warship.fit(hull, variant, side, x, y, facing);
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.shipFire(order);
        // A held station: the duel helm's own evidence is frigate-duel-helm, and a slugging match is the question here.
        ship.duelHelm(false);
        return ship;
    }

    // ------------------------------------------------------------------------------------------------ verdicts

    private SceneReport report(String loop, Run run) {
        List<Verdict> verdicts = new ArrayList<>();
        Warship west = run.west();
        Warship east = run.east();
        Map<String, Number> metrics = new LinkedHashMap<>();
        WarshipShipFire fire = run.sim().warships().shipFire();
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("roundsOnHull", fire.roundsOnHull());
        metrics.put("westHullLost", Math.round(west.maxHitPoints - Math.max(0f, west.hitPoints())));
        metrics.put("eastHullLost", Math.round(east.maxHitPoints - Math.max(0f, east.hitPoints())));
        metrics.put("westDestroyed", west.destroyed() ? 1 : 0);
        metrics.put("eastDestroyed", east.destroyed() ? 1 : 0);
        metrics.put("westDownTick", run.trace().westDownTick);
        metrics.put("eastDownTick", run.trace().eastDownTick);
        switch (loop) {
            case "broadside" -> {
                for (Warship ship : List.of(west, east)) {
                    String name = ship == west ? "west" : "east";
                    WarshipPlating p = ship.plating();
                    // Read at the moment the hull had lost a third of itself: by the end a hull holed through takes
                    // rounds on its far side too, which is the point of a breach.
                    Trace.Snapshot at = run.trace().snapshot(ship);
                    Section struck = at.hits(Section.PORT) >= at.hits(Section.STARBOARD) ? Section.PORT : Section.STARBOARD;
                    Section far = struck == Section.PORT ? Section.STARBOARD : Section.PORT;
                    int on = at.hits(struck);
                    int off = at.hits(far);
                    verdicts.add(Verdict.of(name + "-is-hit-on-the-side-facing-the-shooter",
                            on > 0 && struck == at.facing() && on >= 3 * off,
                            String.format(Locale.ROOT, "at %.0f%% hull (%.1fs) the %s side faces the shooter; hits port "
                                            + "%d, starboard %d, bow %d, stern %d, core %d", at.hull() * 100f,
                                    at.seconds(), at.facing(), at.hits(Section.PORT), at.hits(Section.STARBOARD),
                                    at.hits(Section.BOW), at.hits(Section.STERN), at.hits(Section.CORE))));
                    verdicts.add(Verdict.of(name + "-struck-plate-wears-and-the-far-side-holds",
                            at.plate(struck) < 0.9f && at.plate(far) > at.plate(struck) + 0.2f,
                            String.format(Locale.ROOT, "at %.0f%% hull plate left %s %.2f, %s %.2f", at.hull() * 100f,
                                    struck, at.plate(struck), far, at.plate(far))));
                    float breach = p.firstBreachAt();
                    float core = p.firstCoreEntryAt();
                    verdicts.add(Verdict.of(name + "-reaches-the-core-only-through-a-breach",
                            Float.isNaN(core) || (!Float.isNaN(breach) && core >= breach),
                            String.format(Locale.ROOT, "first breach %.1fs, first core entry %.1fs", breach, core)));
                    metrics.put(name + "Breaches", p.breachCount());
                    metrics.put(name + "MountsKnockedOut", ship.mountsKnockedOut());
                    metrics.put(name + "FirstBreachSeconds", Float.isNaN(breach) ? -1 : Math.round(breach * 10f) / 10f);
                    metrics.put(name + "FirstCoreEntrySeconds", Float.isNaN(core) ? -1 : Math.round(core * 10f) / 10f);
                    for (Section s : Section.values()) {
                        metrics.put(name + "Plate" + s, Math.round(p.plateLeft(s) * 100f));
                        metrics.put(name + "Breached" + s, Math.round(p.breachedShare(s) * 100f));
                    }
                }
            }
            case "control" -> verdicts.add(Verdict.of("the-control-has-flat-armour",
                    west.plating() == null && east.plating() == null && fire.roundsOnHull() > 0,
                    fire.roundsOnHull() + " rounds on hulls with no armour grid"));
            case "shield-astern" -> {
                WarshipPlating p = east.plating();
                verdicts.add(Verdict.of("a-front-shield-leaves-a-round-from-astern-to-the-hull",
                        east.shieldHits() == 0 && p.hitsOn(Section.STERN) > 0,
                        east.shieldHits() + " absorbed, " + p.hitsOn(Section.STERN) + " on the stern, shield "
                                + run.trace().shieldUpTicks + " ticks up"));
                metrics.put("eastShieldHits", east.shieldHits());
                metrics.put("eastSternHits", p.hitsOn(Section.STERN));
            }
            case "shield-ahead" -> {
                verdicts.add(Verdict.of("a-front-shield-takes-a-round-from-ahead", east.shieldHits() > 0,
                        east.shieldHits() + " absorbed, " + east.plating().hitsOn(Section.BOW) + " on the bow"));
                metrics.put("eastShieldHits", east.shieldHits());
                metrics.put("eastBowHits", east.plating().hitsOn(Section.BOW));
            }
            default -> throw new IllegalStateException(loop);
        }
        if (loop.equals("broadside")) {
            int lost = west.mountsKnockedOut() + east.mountsKnockedOut();
            verdicts.add(Verdict.of("a-mount-lost-with-its-plating-fires-no-more",
                    run.trace().shotsAfterKnockOut == 0,
                    lost == 0 ? "no mount was lost" : lost + " mounts lost with their plating, "
                            + run.trace().shotsAfterKnockOut + " rounds fired by them afterwards"));
        }
        return new SceneReport(id(), loop, run.ticks(), verdicts, metrics);
    }

    // ------------------------------------------------------------------------------------------------ trace

    private static final class Trace implements TickObserver {
        /** Hull share at which the side readings are taken. */
        static final float SNAPSHOT_HULL = 0.67f;
        private final Warship west;
        private final Warship east;
        private final Map<Warship, Snapshot> snapshots = new LinkedHashMap<>();
        int shieldUpTicks;
        int westDownTick = -1;
        /** Rounds each lost mount had fired when it was lost, and rounds lost mounts fired afterwards. */
        private final Map<Warship.Mount, Integer> shotsAtKnockOut = new LinkedHashMap<>();
        int shotsAfterKnockOut;
        int eastDownTick = -1;

        /** One hull's grid as it stood when it first fell to {@link #SNAPSHOT_HULL}. */
        record Snapshot(float hull, float seconds, Section facing, int[] hitsBySection, float[] plateBySection) {
            int hits(Section s) { return hitsBySection[s.ordinal()]; }
            float plate(Section s) { return plateBySection[s.ordinal()]; }
        }

        Snapshot snapshot(Warship ship) {
            Snapshot s = snapshots.get(ship);
            return s != null ? s : take(ship, -1);
        }

        private Snapshot take(Warship ship, int tick) {
            WarshipPlating p = ship.plating();
            int[] hits = new int[Section.values().length];
            float[] plate = new float[Section.values().length];
            for (Section s : Section.values()) {
                hits[s.ordinal()] = p.hitsOn(s);
                plate[s.ordinal()] = p.plateLeft(s);
            }
            Warship other = ship == west ? east : west;
            Section facing = ship.hullU(other.centerX(), other.centerY()) < 0.5f ? Section.PORT : Section.STARBOARD;
            return new Snapshot(ship.hullFraction(), tick / 30f, facing, hits, plate);
        }

        Trace(Warship west, Warship east) {
            this.west = west;
            this.east = east;
        }

        String caption(String loop, int tick) {
            return String.format(Locale.ROOT, "%s / tick %d / west %s / east %s", loop, tick, describe(west),
                    describe(east));
        }

        private static String describe(Warship ship) {
            if (ship.destroyed()) return "down";
            WarshipPlating p = ship.plating();
            if (p == null) return String.format(Locale.ROOT, "hull %.0f%% (flat armour)", ship.hullFraction() * 100f);
            return String.format(Locale.ROOT, "hull %.0f%% plate P%.0f S%.0f B%.0f St%.0f breaches %d",
                    ship.hullFraction() * 100f, p.plateLeft(Section.PORT) * 100f,
                    p.plateLeft(Section.STARBOARD) * 100f, p.plateLeft(Section.BOW) * 100f,
                    p.plateLeft(Section.STERN) * 100f, p.breachCount());
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            if (east.shieldUp()) shieldUpTicks++;
            if (westDownTick < 0 && west.destroyed()) westDownTick = tick;
            if (eastDownTick < 0 && east.destroyed()) eastDownTick = tick;
            for (Warship ship : List.of(west, east)) {
                for (Warship.Mount m : ship.mounts()) {
                    if (!m.knockedOut()) continue;
                    Integer at = shotsAtKnockOut.putIfAbsent(m, m.shotsFired);
                    if (at != null && m.shotsFired > at) {
                        shotsAfterKnockOut += m.shotsFired - at;
                        shotsAtKnockOut.put(m, m.shotsFired);
                    }
                }
            }
            for (Warship ship : List.of(west, east)) {
                if (ship.plating() != null && !snapshots.containsKey(ship) && ship.hullFraction() <= SNAPSHOT_HULL) {
                    snapshots.put(ship, take(ship, tick));
                }
            }
        }
    }
}
