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
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipDuelLedger;
import com.dillon.starsectormarines.battle.world.gen.BlackSluice;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The production Black Sluice duel ({@link BlackSluiceDuel}) on open ground the size of the Black Sluice map, without
 * the ground battle: the <i>Greyhook</i> (a Venture) and her two Hound (P) escorts over the pier, Voss's order, and
 * two of Rusk's Wolf (H) coming down through the cloud deck at the west edge, heading east. The duel is the production class itself, so its stations,
 * fittings, route and visit are the ones the chapter plays.
 *
 * <p>Five loops, one per seed. A seed moves when the order comes and where each Wolf breaks the cloud deck by a few
 * cells, which is all the chapter's ground battle varies for the ships; everything after is the ships' own doing.
 * Verdicts assert mechanics (both sides put rounds on hulls, nobody fires on the ground, the Wolfs come down on their
 * heading, the radio hears them sighted, closing and firing in that order, the Greyhook fights before she breaks, no
 * wing launches); who escapes, why and when she broke, how long it lasts, where over the basin it was fought and
 * what each side did to the other are readings. The fight's centroid is sampled every thirty seconds.
 */
public final class GreyhookDuelScene implements BehaviorScene {

    private static final int WIDTH = BlackSluice.WIDTH;
    private static final int HEIGHT = BlackSluice.HEIGHT;
    private static final int TICKS = 9000;
    private static final int FRAME_EVERY = 30;
    private static final long[] SEEDS = {15305L, 16205L, 16206L, 20261016L, 31337L};

    @Override public String id() { return "greyhook-duel"; }

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
        for (long seed : SEEDS) reports.add(playOne(seed, frames));
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
                    Warship wolf = wolves.get(i);
                    wolf.x += jitter[2 * i];
                    wolf.y += jitter[2 * i + 1];
                    wolf.stationTo(wolf.x, wolf.y);
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
        verdicts.add(Verdict.of("the-wolfs-come-down-on-their-heading", t.wolfEntryHeadingError <= 1f,
                String.format(Locale.ROOT, "out of the deck at most %.1f degrees off east", t.wolfEntryHeadingError)));
        int sighted = t.beatTick(BlackSluiceDuel.WOLVES_SIGHTED_BEAT);
        int closing = t.beatTick(BlackSluiceDuel.WOLVES_CLOSING_BEAT);
        int fired = t.beatTick(BlackSluiceDuel.FIRST_HULL_FIRE_BEAT);
        verdicts.add(Verdict.of("the-radio-hears-the-approach-in-order",
                sighted >= 0 && (closing < 0 || closing >= sighted) && (fired < 0 || (closing >= 0 && fired >= closing)),
                String.format(Locale.ROOT, "sighted %.1fs, closing %.1fs, first hull fire %.1fs after the order",
                        t.seconds(sighted), t.seconds(closing), t.seconds(fired))));
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
        metrics.put("greyhookGoneSeconds", t.seconds(t.greyhookGoneTick));
        metrics.put("firstHoundDownSeconds", t.seconds(t.firstHoundDownTick));
        metrics.put("lastHoundDownSeconds", t.seconds(t.lastHoundDownTick));
        metrics.put("firstWolfDownSeconds", t.seconds(t.firstWolfDownTick));
        metrics.put("greyhookRounds", shots(List.of(greyhook)));
        metrics.put("houndRounds", shots(duel.hounds()));
        metrics.put("wolfRounds", shots(duel.wolves()));
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
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
        private final BattleSimulation sim;
        int defenderAircraft;
        private final Map<Warship, Float> lastHp = new LinkedHashMap<>();
        private final Map<Warship, Float> lastShield = new LinkedHashMap<>();
        private int lastGreyhookShots;

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
            lastTick = tick;
            if (tick == orderTick + 1) {
                for (Warship wolf : duel.wolves()) {
                    float off = Math.abs(((wolf.facingDegrees - BlackSluiceDuel.WOLF_HEADING) % 360f + 540f) % 360f - 180f);
                    wolfEntryHeadingError = Math.max(wolfEntryHeadingError, off);
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
                nearestEdge = Math.min(nearestEdge, Math.min(Math.min(cx, WIDTH - cx),
                        Math.min(cy, BlackSluice.AIRSPACE_TOP - cy)));
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
