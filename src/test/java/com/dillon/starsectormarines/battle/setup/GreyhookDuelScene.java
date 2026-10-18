package com.dillon.starsectormarines.battle.setup;

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
import com.dillon.starsectormarines.battle.warship.HullFireHold;
import com.dillon.starsectormarines.battle.warship.MapEdge;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipDuelLedger;
import com.dillon.starsectormarines.battle.warship.WarshipEdgeHelm;
import com.dillon.starsectormarines.battle.warship.WarshipShipRules;
import com.dillon.starsectormarines.battle.warship.WarshipSpacing;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The production Black Sluice duel ({@link BlackSluiceDuel}) on open ground the size of the Black Sluice map, without
 * the ground battle: the <i>Greyhook</i> (a Venture) and her two Hound (P) escorts on their pads, Voss's order, and
 * two of Rusk's Wolf (H) flying in across the west edge, heading east. The duel is the production class itself, so its
 * stations, fittings, edges, route and visit are the ones the chapter plays.
 *
 * <p>Five loops, one per seed. A seed moves when the order comes and where along the west edge each Wolf flies in by a
 * few cells, which is all the chapter's ground battle varies for the ships; everything after is the ships' own doing.
 * Verdicts assert mechanics (both sides put rounds on hulls, nobody fires on the ground, the Wolfs fly in on their
 * heading, the radio hears them sighted, closing and firing in that order, the Greyhook fights before she breaks, no
 * wing launches, every hull arrives from its pad or across its side's edge and leaves across its side's edge, no hull
 * crosses an edge that is not its side's, and a hull the edge pressure holds stays out of its edge margin to within a
 * tolerance); who escapes, why and when she broke, how long it lasts, where over the map it was fought and what each
 * side did to the other are readings. The fight's centroid is sampled every thirty seconds.
 */
public final class GreyhookDuelScene implements BehaviorScene {

    private static final int WIDTH = BlackSluice.WIDTH;
    private static final int HEIGHT = BlackSluice.HEIGHT;
    /**
     * Ten minutes: long enough for a slow Venture that breaks late to fight her way across the basin and off the east
     * edge, and for a Wolf whose visit ends over the east of the map to fly home across the west.
     */
    private static final int TICKS = 18000;
    private static final int FRAME_EVERY = 30;
    private static final long[] SEEDS = {15305L, 16205L, 16206L, 20261016L, 31337L};
    /**
     * How far, as a share of its own margin, a hull the edge pressure holds may still be found inside that margin: the
     * pressure is soft and starts at the margin, so a hull turning off an edge at speed sinks a little way into it.
     */
    static final float EDGE_TOLERANCE_SHARE = 0.25f;

    @Override public String id() { return "greyhook-duel"; }

    /**
     * The seeds to play: all five, or the comma-separated list in {@code -Dbattle.scene.greyhookDuel.seeds} (one seed is
     * how a single review GIF is rendered without the other four).
     */
    static long[] seeds() {
        String only = System.getProperty("battle.scene.greyhookDuel.seeds");
        if (only == null || only.isBlank()) return SEEDS;
        String[] parts = only.split(",");
        long[] out = new long[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Long.parseLong(parts[i].trim());
        return out;
    }

    @Override public String label() {
        return "The Black Sluice duel: the Greyhook (Venture) and two Hounds climbing out from the pier against two "
                + "of Rusk's Wolfs, five seeds";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        List<SceneReport> reports = new ArrayList<>();
        for (long seed : seeds()) reports.add(playOne(seed, frames));
        return reports;
    }

    private SceneReport playOne(long seed, FrameSink frames) throws Exception {
        String loop = "seed-" + seed;
        Random rng = new Random(seed);
        int orderTick = 60 + rng.nextInt(240);
        float[] jitter = {rng.nextFloat() * 12f - 6f, rng.nextFloat() * 12f - 6f,
                rng.nextFloat() * 12f - 6f, rng.nextFloat() * 12f - 6f};
        SceneBuilder builder = SceneBuilder.openGround(WIDTH, HEIGHT).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(4).at(4, 4).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(4).at(WIDTH - 6, 4).unarmed().stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(seed);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        BlackSluiceDuel duel = new BlackSluiceDuel();
        duel.stage(sim);
        Trace trace = new Trace(duel, sim, orderTick);
        TickObserver script = (s, tick) -> {
            if (tick == orderTick) {
                for (HullFireHold hold : s.warships().hullFireHolds()) hold.release();
                duel.withdrawalOrdered(s);
                List<Warship> wolves = duel.wolves();
                for (int i = 0; i < wolves.size(); i++) {
                    // Along the edge only: a Wolf still flies in across it, from a few cells up or down the line.
                    Warship wolf = wolves.get(i);
                    wolf.y += jitter[2 * i + 1];
                    wolf.stationTo(wolf.stationX(), wolf.stationY() + jitter[2 * i + 1]);
                }
            }
            duel.advance(s);
        };
        SceneRun.of(world).observe(script).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return report(loop, trace, duel);
    }

    private SceneReport report(String loop, Trace t, BlackSluiceDuel duel) {
        Warship greyhook = duel.greyhook();
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("both-sides-put-rounds-on-hulls",
                t.damageToGreyhookSide > 0f && t.damageToWolves > 0f,
                String.format(Locale.ROOT, "Greyhook's side took %.0f (hull and shield), the Wolfs %.0f",
                        t.damageToGreyhookSide, t.damageToWolves)));
        int groundShots = 0;
        for (Warship hull : duel.hulls()) groundShots += hull.groundShots();
        verdicts.add(Verdict.of("nobody-fires-on-the-ground", groundShots == 0, groundShots + " ground shots"));
        verdicts.add(Verdict.of("the-greyhook-fights-before-she-breaks",
                t.wolvesInReachBeforeBreak == 0 || t.greyhookShotsBeforeBreak > 0,
                t.wolvesInReachBeforeBreak == 0 ? "no Wolf came into her reach before she broke"
                        : String.format(Locale.ROOT, "%d rounds before she broke, a Wolf in reach for %d ticks",
                        t.greyhookShotsBeforeBreak, t.wolvesInReachBeforeBreak)));
        verdicts.add(Verdict.of("the-wolfs-fly-in-on-their-heading", t.wolfEntryHeadingError <= 1f,
                String.format(Locale.ROOT, "in across the west edge at most %.1f degrees off east", t.wolfEntryHeadingError)));
        verdicts.add(Verdict.of("hulls-arrive-only-from-a-pad-or-across-their-edge", t.badArrivals.isEmpty(),
                t.badArrivals.isEmpty() ? t.hullsSeen + " hulls: Greyhook's on their pads, the Wolfs off the west edge"
                        : String.join("; ", t.badArrivals)));
        verdicts.add(Verdict.of("hulls-leave-only-across-their-edge", t.badExits.isEmpty(),
                t.badExits.isEmpty() ? t.exits + " hulls left, each across its own edge" : String.join("; ", t.badExits)));
        verdicts.add(Verdict.of("no-hull-crosses-an-edge-it-does-not-own", t.foreignCrossings.isEmpty(),
                t.foreignCrossings.isEmpty() ? "none" : String.join("; ", t.foreignCrossings)));
        verdicts.add(Verdict.of("held-hulls-stay-out-of-their-edge-margin",
                t.worstMarginShare >= -EDGE_TOLERANCE_SHARE,
                String.format(Locale.ROOT, "least clear air %.1f cells against a margin of %.1f (%s), %d ticks inside it",
                        t.leastClearance, t.leastClearanceMargin, t.leastClearanceWho, t.ticksInsideMargin)));
        int sighted = t.beatTick(BlackSluiceDuel.WOLVES_SIGHTED_BEAT);
        int closing = t.beatTick(BlackSluiceDuel.WOLVES_CLOSING_BEAT);
        int fired = t.beatTick(BlackSluiceDuel.FIRST_HULL_FIRE_BEAT);
        verdicts.add(Verdict.of("the-radio-hears-the-approach-in-order",
                sighted >= 0 && (closing < 0 || closing >= sighted) && (fired < 0 || (closing >= 0 && fired >= closing)),
                String.format(Locale.ROOT, "sighted %.1fs, closing %.1fs, first hull fire %.1fs after the order",
                        t.seconds(sighted), t.seconds(closing), t.seconds(fired))));
        verdicts.add(Verdict.of("friendly-hulls-do-not-overlap", t.worstFriendlyExcess <= 0.05f,
                String.format(Locale.ROOT, "least friendly gap %.1f cells (%s), %d ticks touching", t.minFriendlyGap,
                        t.closestFriends, t.friendlyOverlapTicks)));
        verdicts.add(Verdict.of("no-wing-launches", t.defenderAircraft == 0,
                t.defenderAircraft + " Greyhook aircraft over the battle"));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("orderTick", t.orderTick);
        metrics.put("durationSeconds", t.resolvedTick < 0 ? -1f : (t.resolvedTick - t.orderTick) / 30f);
        metrics.put("firstHullHitSeconds", t.firstHitTick < 0 ? -1f : (t.firstHitTick - t.orderTick) / 30f);
        metrics.put("greyhookOutcome", outcomeCode(duel, greyhook));
        metrics.put("greyhookHullPercent", Math.round(greyhook.hullFraction() * 100f));
        metrics.put("houndsLost", lost(duel.hounds()));
        metrics.put("wolvesLost", lost(duel.wolves()));
        metrics.put("damageToGreyhookSide", Math.round(t.damageToGreyhookSide));
        metrics.put("damageToWolves", Math.round(t.damageToWolves));
        metrics.put("hullDamageToGreyhook", Math.round(t.hullLost(greyhook)));
        metrics.put("hullDamageToHounds", Math.round(t.hullLost(duel.hounds())));
        metrics.put("hullDamageToWolves", Math.round(t.hullLost(duel.wolves())));
        metrics.put("shieldDamageToWolves", Math.round(t.shieldTaken(duel.wolves())));
        metrics.put("shieldDamageToGreyhook", Math.round(greyhook.shieldAbsorbedDamage()));
        metrics.put("greyhookShotsBeforeBreak", t.greyhookShotsBeforeBreak);
        metrics.put("sightedSeconds", t.seconds(sighted));
        metrics.put("closingSeconds", t.seconds(closing));
        metrics.put("firstHullFireSeconds", t.seconds(fired));
        metrics.put("sightedToFirstFireSeconds", sighted < 0 || fired < 0 ? -1f : (fired - sighted) / 30f);
        metrics.put("breakSeconds", t.seconds(duel.breakTick()));
        // 0 still fighting, 1 made her height, 2 crippled, 3 nothing left to stop her.
        metrics.put("breakReason", duel.breakOff().ordinal());
        metrics.put("climbPercent", Math.round(duel.climb() * 100f));
        metrics.put("foughtBeforeBreakSeconds", t.firstHitTick < 0 ? 0f
                : ((duel.breakTick() < 0 ? t.lastTick : duel.breakTick()) - t.firstHitTick) / 30f);
        for (int i = 0; i < t.centroids.size(); i++) {
            float[] c = t.centroids.get(i);
            metrics.put("centroid" + (30 * (i + 1)) + "sX", Math.round(c[0]));
            metrics.put("centroid" + (30 * (i + 1)) + "sY", Math.round(c[1]));
        }
        metrics.put("nearestEdgeOfFightCells", Math.round(t.nearestEdge));
        metrics.put("fightMinX", Math.round(t.fightMinX));
        metrics.put("fightMaxX", Math.round(t.fightMaxX));
        metrics.put("fightMinY", Math.round(t.fightMinY));
        metrics.put("fightMaxY", Math.round(t.fightMaxY));
        metrics.put("fightOverGroundBattlePercent", t.fightTicks == 0 ? 0 : Math.round(100f * t.fightNorthTicks / t.fightTicks));
        metrics.put("leastHullEdgeClearanceCells", Math.round(t.leastClearance * 10f) / 10f);
        metrics.put("leastHullEdgeClearanceMarginCells", Math.round(t.leastClearanceMargin * 10f) / 10f);
        metrics.put("ticksInsideEdgeMargin", t.ticksInsideMargin);
        for (int i = 0; i < duel.wolves().size(); i++) {
            Warship wolf = duel.wolves().get(i);
            metrics.put("wolf" + (i + 1) + "OnMapSeconds", t.seconds(t.onMapTick.getOrDefault(wolf, -1)));
            metrics.put("wolf" + (i + 1) + "ClearOfEdgeSeconds", t.seconds(t.clearTick.getOrDefault(wolf, -1)));
            metrics.put("wolf" + (i + 1) + "ExitSeconds", t.seconds(t.exitTick.getOrDefault(wolf, -1)));
            metrics.put("wolf" + (i + 1) + "Outcome", outcomeCode(duel, wolf));
            metrics.put("wolf" + (i + 1) + "HullPercent", Math.round(Math.max(0f, wolf.hullFraction()) * 100f));
        }
        metrics.put("greyhookExitSeconds", t.seconds(t.exitTick.getOrDefault(greyhook, -1)));
        for (Map.Entry<String, List<float[]>> track : t.tracks.entrySet()) {
            for (int i = 0; i < track.getValue().size(); i++) {
                float[] g = track.getValue().get(i);
                if (g[0] < -1e3f) continue;
                metrics.put(track.getKey() + "At" + (30 * (i + 1)) + "sX", Math.round(g[0]));
                metrics.put(track.getKey() + "At" + (30 * (i + 1)) + "sY", Math.round(g[1]));
            }
        }
        for (int i = 0; i < duel.hounds().size(); i++) {
            metrics.put("hound" + (i + 1) + "ExitSeconds", t.seconds(t.exitTick.getOrDefault(duel.hounds().get(i), -1)));
        }
        metrics.put("greyhookGoneSeconds", t.seconds(t.greyhookGoneTick));
        metrics.put("firstHoundDownSeconds", t.seconds(t.firstHoundDownTick));
        metrics.put("lastHoundDownSeconds", t.seconds(t.lastHoundDownTick));
        metrics.put("firstWolfDownSeconds", t.seconds(t.firstWolfDownTick));
        metrics.put("greyhookRounds", shots(List.of(greyhook)));
        metrics.put("houndRounds", shots(duel.hounds()));
        metrics.put("wolfRounds", shots(duel.wolves()));
        var fire = t.sim.warships().shipFire();
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("hitSharePercent", fire.roundsFired() == 0 ? 0 : Math.round(100f * fire.roundsOnHull() / fire.roundsFired()));
        metrics.put("minFriendlyGapCells", Math.round(t.minFriendlyGap * 10f) / 10f);
        metrics.put("friendlyOverlapTicks", t.friendlyOverlapTicks);
        metrics.put("houndMountsLost", mountsLost(duel.hounds()));
        metrics.put("houndChaingunBearsPercent", t.houndInReach == 0 ? -1 : Math.round(100f * t.houndBears / t.houndInReach));
        List<Warship> wolves = duel.wolves();
        List<Warship> targets = duel.hulls().subList(0, Math.min(3, duel.hulls().size()));
        for (int i = 0; i < wolves.size(); i++) {
            int[] on = t.targetTicks.getOrDefault(wolves.get(i), new int[4]);
            int total = on[0] + on[1] + on[2] + on[3];
            String[] names = {"Greyhook", "HoundA", "HoundB", "None"};
            for (int k = 0; k < 4; k++) {
                metrics.put("wolf" + (i + 1) + "On" + names[k] + "Percent", total == 0 ? 0 : Math.round(100f * on[k] / total));
            }
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static int mountsLost(List<Warship> ships) {
        int n = 0;
        for (Warship s : ships) n += s.mountsKnockedOut();
        return n;
    }

    /** 0 over the battle, 1 escaped, 2 destroyed, 3 broke off or departed. */
    private static int outcomeCode(BlackSluiceDuel duel, Warship ship) {
        WarshipDuelLedger.Entry e = duel.entry(ship);
        if (e == null) return 0;
        return switch (e.outcome()) {
            case OVER_THE_BATTLE -> 0;
            case ESCAPED -> 1;
            case DESTROYED -> 2;
            case BROKE_OFF, DEPARTED -> 3;
        };
    }

    private static int lost(List<Warship> ships) {
        int n = 0;
        for (Warship s : ships) if (s.destroyed()) n++;
        return n;
    }

    /** Rounds fired by {@code ships}' guns and launchers (point defence left out). */
    private static int shots(List<Warship> ships) {
        int n = 0;
        for (Warship s : ships) for (Warship.Mount m : s.mounts()) if (!m.pointDefence()) n += m.shotsFired;
        return n;
    }

    private static final class Trace implements TickObserver {
        final BlackSluiceDuel duel;
        final int orderTick;
        final Map<Warship, Float> startHp = new LinkedHashMap<>();
        int resolvedTick = -1;
        int firstHitTick = -1;
        int greyhookGoneTick = -1;
        int firstHoundDownTick = -1;
        int lastHoundDownTick = -1;
        int firstWolfDownTick = -1;
        private int houndsDown;
        float damageToGreyhookSide;
        float damageToWolves;
        int greyhookShotsBeforeBreak;
        int wolvesInReachBeforeBreak;
        float wolfEntryHeadingError;
        int lastTick;
        /** The centroid of the live duel hulls every thirty seconds after the order, while any fight is over the basin. */
        final List<float[]> centroids = new ArrayList<>();
        /** The nearest the fight's centroid came to a map edge while both sides were over the basin. */
        float nearestEdge = Float.MAX_VALUE;
        final BattleSimulation sim;
        int defenderAircraft;
        private final Map<Warship, Float> lastHp = new LinkedHashMap<>();
        private final Map<Warship, Float> lastShield = new LinkedHashMap<>();
        private int lastGreyhookShots;
        /** The least gap, in cells, between two friendly hulls' footprints over the battle; negative is overlap. */
        float minFriendlyGap = Float.MAX_VALUE;
        /** Which friendly pair came closest, and when after the order. */
        String closestFriends = "";
        /** The most two friends lay in each other beyond the pair's contact tolerance. */
        float worstFriendlyExcess = Float.NEGATIVE_INFINITY;
        int friendlyOverlapTicks;
        /** Per Wolf, ticks with its hull target the Greyhook, the first Hound, the second Hound, or none. */
        final Map<Warship, int[]> targetTicks = new LinkedHashMap<>();
        int houndInReach;
        int houndBears;
        /** Arrivals and exits that did not cross the hull's own edge (or, for Greyhook's ships, start on a pad). */
        final List<String> badArrivals = new ArrayList<>();
        final List<String> badExits = new ArrayList<>();
        final List<String> foreignCrossings = new ArrayList<>();
        int hullsSeen;
        int exits;
        final Map<Warship, Integer> onMapTick = new LinkedHashMap<>();
        final Map<Warship, Integer> clearTick = new LinkedHashMap<>();
        final Map<Warship, Integer> exitTick = new LinkedHashMap<>();
        private final Set<Warship> seen = new HashSet<>();
        private final Set<Warship> settled = new HashSet<>();
        private final Set<Warship> crossedForeign = new HashSet<>();
        /** The least clear air to an edge of a hull the edge pressure held, after it first cleared its margin. */
        float leastClearance = Float.MAX_VALUE;
        float leastClearanceMargin;
        String leastClearanceWho = "nobody";
        /** That clear air less the margin, as a share of the margin; negative is inside it. */
        float worstMarginShare = Float.MAX_VALUE;
        int ticksInsideMargin;
        float fightMinX = Float.MAX_VALUE, fightMaxX = -Float.MAX_VALUE, fightMinY = Float.MAX_VALUE, fightMaxY = -Float.MAX_VALUE;
        int fightTicks;
        int fightNorthTicks;
        /** Where each hull was every thirty seconds after the order, while it was over the battle, by name. */
        final Map<String, List<float[]>> tracks = new LinkedHashMap<>();

        Trace(BlackSluiceDuel duel, BattleSimulation sim, int orderTick) {
            this.duel = duel;
            this.orderTick = orderTick;
            this.sim = sim;
        }

        /** The tick a story beat was published, or -1. */
        int beatTick(String id) {
            for (var e : sim.getStoryBeats()) if (e.id().equals(id)) return e.tick();
            return -1;
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "GREYHOOK DUEL / %s / %ds after the order",
                    loop, Math.max(0, tick - orderTick) / 30));
            for (Warship hull : duel.hulls()) {
                String name = switch (hull.hullId) { case "venture" -> "Greyhook"; case "hound" -> "Hound"; default -> "Wolf"; };
                s.append(" / ").append(name).append(' ').append(hull.destroyed() ? "down" : hull.departed() ? "gone"
                        : hull.departing() ? "leaving " + Math.round(hull.hullFraction() * 100) + "%"
                        : Math.round(hull.hullFraction() * 100) + "%");
            }
            return s.toString();
        }

        /** Seconds after the order of {@code tick}, or -1 for a tick that never came. */
        float seconds(int tick) {
            return tick < 0 ? -1f : (tick - orderTick) / 30f;
        }

        float hullLost(Warship ship) {
            Float hp = startHp.get(ship);
            return hp == null ? 0f : hp - Math.max(0f, ship.hitPoints());
        }

        float hullLost(List<Warship> ships) {
            float sum = 0f;
            for (Warship s : ships) sum += hullLost(s);
            return sum;
        }

        float shieldTaken(List<Warship> ships) {
            float sum = 0f;
            for (Warship s : ships) sum += s.shieldAbsorbedDamage();
            return sum;
        }

        private static MapEdge ownEdge(Warship ship) {
            return ship.faction == Faction.ALLY ? BlackSluiceDuel.WOLF_EDGE : BlackSluiceDuel.GREYHOOK_EDGE;
        }

        private static String name(Warship ship) {
            return ship.hullId + "/" + ship.faction;
        }

        /** Arrivals, exits, edge crossings and clear air to the edges, for every hull of the duel, every tick. */
        private void edges(BattleSimulation sim, int tick, List<Warship> all) {
            WarshipEdgeHelm helm = new WarshipEdgeHelm(WIDTH, HEIGHT);
            for (Warship s : all) {
                float x = s.centerX(), y = s.centerY();
                MapEdge own = ownEdge(s);
                if (seen.add(s)) {
                    hullsSeen++;
                    boolean fromPad = s.faction == Faction.DEFENDER && s.berthed();
                    boolean fromEdge = s.enteringAcrossEdge() && s.entryEdge() == own && own.beyond(x, y, WIDTH, HEIGHT);
                    if (!fromPad && !fromEdge) {
                        badArrivals.add(String.format(Locale.ROOT, "%s first seen at %.0f,%.0f", name(s), x, y));
                    }
                }
                if (s.destroyed()) continue;
                if (!onMapTick.containsKey(s) && x >= 0f && x <= WIDTH && y >= 0f && y <= HEIGHT) onMapTick.put(s, tick);
                if (s.entryEdge() != null && !s.enteringAcrossEdge() && !clearTick.containsKey(s)) clearTick.put(s, tick);
                if (s.departed()) {
                    if (exitTick.containsKey(s)) continue;
                    exitTick.put(s, tick);
                    exits++;
                    if (!s.physicallyDeparted() || s.throughDeck() || s.exitEdge() != own || !own.beyond(x, y, WIDTH, HEIGHT)) {
                        badExits.add(String.format(Locale.ROOT, "%s gone at %.0f,%.0f by %s", name(s), x, y,
                                s.throughDeck() ? "the deck" : s.exitEdge() == null ? "no edge" : s.exitEdge().toString()));
                    }
                    continue;
                }
                for (MapEdge e : MapEdge.values()) {
                    if (e != own && e.beyond(x, y, WIDTH, HEIGHT) && crossedForeign.add(s)) {
                        foreignCrossings.add(String.format(Locale.ROOT, "%s over the %s edge at %.0fs", name(s), e,
                                seconds(tick)));
                    }
                }
                if (!duel.ordered() || !WarshipEdgeHelm.applies(s)) continue;
                float clear = helm.nearestClearance(s);
                float margin = helm.margin(s);
                if (clear >= margin) settled.add(s);
                if (!settled.contains(s)) continue;
                if (clear < margin) ticksInsideMargin++;
                float share = (clear - margin) / margin;
                if (share < worstMarginShare) {
                    worstMarginShare = share;
                    leastClearance = clear;
                    leastClearanceMargin = margin;
                    leastClearanceWho = String.format(Locale.ROOT, "%s at %.0fs", name(s), seconds(tick));
                }
            }
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            List<Warship> all = duel.hulls();
            for (Warship s : all) startHp.putIfAbsent(s, s.hitPoints());
            Warship greyhook = duel.greyhook();
            for (Warship s : all) {
                float hp = Math.max(0f, s.hitPoints());
                float sh = s.shieldAbsorbedDamage();
                float dh = lastHp.getOrDefault(s, hp) - hp;
                float ds = sh - lastShield.getOrDefault(s, sh);
                lastHp.put(s, hp);
                lastShield.put(s, sh);
                if (dh + ds <= 0f) continue;
                if (firstHitTick < 0) firstHitTick = tick;
                if (s.faction == Faction.ALLY) damageToWolves += dh + ds;
                else damageToGreyhookSide += dh + ds;
            }
            int shots = 0;
            for (Warship.Mount m : greyhook.mounts()) shots += m.shotsFired;
            if (duel.ordered() && !greyhook.departing() && !greyhook.destroyed()) {
                greyhookShotsBeforeBreak += shots - lastGreyhookShots;
                for (Warship wolf : duel.wolves()) {
                    if (!wolf.destroyed() && !wolf.departed()
                            && greyhook.distanceTo(wolf.centerX(), wolf.centerY()) <= 40f) {
                        wolvesInReachBeforeBreak++;
                        break;
                    }
                }
            }
            lastGreyhookShots = shots;
            if (duel.ordered()) {
                List<Warship> live = new ArrayList<>();
                for (Warship s : all) if (WarshipSpacing.inContact(s)) live.add(s);
                boolean overlap = false;
                for (int i = 0; i < live.size(); i++) {
                    for (int j = i + 1; j < live.size(); j++) {
                        Warship a = live.get(i), b = live.get(j);
                        if (a.faction != b.faction) continue;
                        float g = WarshipSpacing.gap(a, b);
                        worstFriendlyExcess = Math.max(worstFriendlyExcess, -g - WarshipSpacing.contactTolerance(a, b));
                        if (g < minFriendlyGap) {
                            minFriendlyGap = g;
                            closestFriends = String.format(Locale.ROOT, "%s/%s at %.0fs", a.hullId, b.hullId,
                                    (tick - orderTick) / 30f);
                        }
                        overlap |= g < 0f;
                    }
                }
                if (overlap) friendlyOverlapTicks++;
                List<Warship> hounds = duel.hounds();
                for (Warship wolf : duel.wolves()) {
                    if (wolf.destroyed() || wolf.departed() || wolf.descending()) continue;
                    Warship tgt = wolf.hullTarget();
                    int k = tgt == null ? 3 : tgt == greyhook ? 0 : tgt == hounds.get(0) ? 1 : 2;
                    targetTicks.computeIfAbsent(wolf, w -> new int[4])[k]++;
                }
                for (Warship hound : hounds) {
                    Warship tgt = hound.hullTarget();
                    if (hound.destroyed() || tgt == null || tgt.destroyed()) continue;
                    if (WarshipShipRules.strongestGunOn(hound, tgt) < 0) continue;
                    houndInReach++;
                    if (WarshipShipRules.strongestGunOn(hound, tgt) > 0) houndBears++;
                }
            }
            lastTick = tick;
            if (tick == orderTick + 1) {
                for (Warship wolf : duel.wolves()) {
                    float off = Math.abs(((wolf.facingDegrees - BlackSluiceDuel.WOLF_HEADING) % 360f + 540f) % 360f - 180f);
                    wolfEntryHeadingError = Math.max(wolfEntryHeadingError, off);
                }
            }
            edges(sim, tick, all);
            if (duel.ordered() && tick > orderTick && (tick - orderTick) % 900 == 0) {
                int slot = (tick - orderTick) / 900;
                String[] names = {"greyhook", "hound1", "hound2", "wolf1", "wolf2"};
                for (int i = 0; i < all.size() && i < names.length; i++) {
                    Warship s = all.get(i);
                    if (s.destroyed() || s.departed()) continue;
                    List<float[]> track = tracks.computeIfAbsent(names[i], k -> new ArrayList<>());
                    while (track.size() < slot - 1) track.add(new float[]{-1e4f, -1e4f});
                    track.add(new float[]{s.centerX(), s.centerY()});
                }
            }
            boolean ours = false, theirs = false;
            float cx = 0f, cy = 0f;
            int n = 0;
            for (Warship s : all) {
                if (s.destroyed() || s.departed() || s.departing()) continue;
                if (s.faction == Faction.ALLY) theirs = true;
                else ours = true;
                cx += s.centerX();
                cy += s.centerY();
                n++;
            }
            if (n > 0 && ours && theirs && tick > orderTick) {
                cx /= n;
                cy /= n;
                nearestEdge = Math.min(nearestEdge, Math.min(Math.min(cx, WIDTH - cx), Math.min(cy, HEIGHT - cy)));
                fightMinX = Math.min(fightMinX, cx);
                fightMaxX = Math.max(fightMaxX, cx);
                fightMinY = Math.min(fightMinY, cy);
                fightMaxY = Math.max(fightMaxY, cy);
                fightTicks++;
                if (cy >= BlackSluice.BAND_BOTTOM) fightNorthTicks++;
                if ((tick - orderTick) % 900 == 0) {
                    while (centroids.size() < (tick - orderTick) / 900 - 1) centroids.add(new float[]{-1f, -1f});
                    centroids.add(new float[]{cx, cy});
                }
            }
            if (greyhookGoneTick < 0 && (greyhook.departed() || greyhook.destroyed())) greyhookGoneTick = tick;
            int down = 0;
            for (Warship h : duel.hounds()) if (h.destroyed()) down++;
            if (down > houndsDown) {
                if (firstHoundDownTick < 0) firstHoundDownTick = tick;
                lastHoundDownTick = tick;
                houndsDown = down;
            }
            if (firstWolfDownTick < 0) for (Warship w : duel.wolves()) if (w.destroyed()) firstWolfDownTick = tick;
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.DEFENDER) defenderAircraft++;
            if (resolvedTick < 0 && duel.ordered() && tick > orderTick) {
                boolean over = true;
                for (Warship s : all) over &= s.destroyed() || s.departed();
                if (over) resolvedTick = tick;
            }
        }
    }
}
