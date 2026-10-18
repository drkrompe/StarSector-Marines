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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Equal ships against each other ({@code warship-tactics.md}, pressure between passes). One duel per hull class and
 * seed on open ground: the same hull and fit on both sides, duel helms, automatic shields, ship fire on every hull and
 * doctrine on; a warship fighting a hull fights it to the end, so a withdrawal is a failure, not a decision. The seed
 * moves each ship's start independently (its place and up to twenty degrees of heading) and every roll, nothing else.
 *
 * <p>The question is whether a fight between equals is decided, by a kill, inside the window, and
 * whether it is decided by manoeuvre rather than by a standoff: no faster than three of the loser's own ninety-degree
 * turns. Its mechanics are verdicts: the fight is decided; it takes at least three of the loser's turns; a front-shield
 * ship on a standoff keeps its target inside its shield arc; the range opens and closes; no two hulls lie in each
 * other. Who wins, the damage each way and how much of the time each ship took fire from outside its arc are readings.
 */
public final class MirrorDuelScene implements BehaviorScene {

    /**
     * Thirty minutes: the hull fight's durability ({@link WarshipScale#HULL_DURABILITY}) makes a capital mirror a fight
     * of many minutes, and the window has to hold the slowest class's kill, not cut it off.
     */
    private static final int TICKS = Integer.getInteger("battle.scene.warshipMirrors.ticks", 54000);
    private static final int FRAME_EVERY = 60;
    private static final float TICK_SECONDS = 1f / 30f;
    private static final long[] SEEDS = {11L, 23L, 37L};
    private static final boolean DIAG = Boolean.getBoolean("battle.scene.warshipMirrors.trace");
    /** A decision may come no faster than this many of the loser's own ninety-degree turns from the first hit. */
    private static final float MIN_TURNS = 3f;
    /** A front-shield ship on a standoff keeps its target inside its arc at least this share of the time. */
    private static final float STANDOFF_ARC_SHARE = 0.85f;
    /** A ship's widest range to its target is at least this multiple of its closest once the fight has begun. */
    private static final float RANGE_BREATH = 1.25f;
    /**
     * At least one side holds a crossing position for this share of the ticks the two are in reach: its guns bearing
     * from outside the other's cover ({@link WarshipTactics#offArc}), or its main battery on the other while the
     * other's is not on it (across its T). A duel in which nobody ever gets round anybody is a standoff. Both sides
     * are not required: between equals the first to get round usually keeps the advantage, and that is the fight's
     * own doing; each side's share is a reading.
     */
    private static final float OFF_ARC_SHARE = 0.02f;
    /**
     * Each ship's mean speed while the two are in reach of each other is at least this share of its top speed: ships
     * under way in the fight, not parked nose to nose once they have closed.
     */
    private static final float WAY_SHARE = 0.3f;
    /** An off-arc position counts as a fresh crossing only after this many ticks without one. */
    private static final int CROSSING_GAP_TICKS = 30;

    /** One class: its hull and fit. */
    private record Mirror(String id, String hull, String variant) {}

    private static final List<Mirror> MIRRORS = List.of(
            new Mirror("wolf", "wolf_hegemony", "wolf_hegemony_Rusk"),
            new Mirror("hound", "hound_d_pirates", "hound_d_pirates_Standard"),
            new Mirror("eagle", "eagle", "eagle_Assault"),
            new Mirror("onslaught", "onslaught", "onslaught_Standard"));

    @Override public String id() { return "warship-mirrors"; }

    @Override public String label() {
        return "Warship mirrors: equal hulls duel per class and seed; decided by manoeuvre, not a standoff";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        String only = System.getProperty("battle.scene.warshipMirrors.only");
        List<SceneReport> reports = new ArrayList<>();
        for (Mirror m : MIRRORS) {
            for (long seed : SEEDS) {
                String loop = m.id() + "-" + seed;
                if (only != null && !only.isBlank()) {
                    boolean wanted = false;
                    for (String o : only.split(",")) wanted |= loop.equals(o) || m.id().equals(o);
                    if (!wanted) continue;
                }
                reports.add(playOne(m, seed, loop, frames));
            }
        }
        return reports;
    }

    private SceneReport playOne(Mirror mirror, long seed, String loop, FrameSink frames) throws Exception {
        Random rng = new Random(seed);
        // Each side's start is its own draw, so the two ships do not come in as each other's mirror image.
        Warship west = Warship.fit(mirror.hull(), mirror.variant(), Faction.ALLY, 0f, 0f,
                270f + (rng.nextFloat() - 0.5f) * 40f);
        Warship east = Warship.fit(mirror.hull(), mirror.variant(), Faction.DEFENDER, 0f, 0f,
                90f + (rng.nextFloat() - 0.5f) * 40f);
        float reach = WarshipShipFire.longestReach(west);
        float length = Math.max(west.lengthCells, west.widthCells);
        float start = 1.15f * reach + length;
        int width = Math.round(start + 6f * length + 40f);
        int height = Math.round(Math.max(1.8f * reach + 4f * length, 180f));
        SceneBuilder builder = SceneBuilder.openGround(width, height).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(1).at(3, 3).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(1).at(width - 4, height - 4).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        float midX = width * 0.5f;
        float midY = height * 0.5f;
        place(west, midX - start * 0.5f + (rng.nextFloat() - 0.5f) * 0.3f * reach,
                midY + (rng.nextFloat() - 0.5f) * 0.6f * reach);
        place(east, midX + start * 0.5f + (rng.nextFloat() - 0.5f) * 0.3f * reach,
                midY + (rng.nextFloat() - 0.5f) * 0.6f * reach);
        sim.addWarship(west);
        sim.addWarship(east);
        if (DIAG) {
            StringBuilder p = new StringBuilder();
            float d = 1000f;
            for (int a = 0; a <= 180; a += 15) {
                double r = Math.toRadians(a);
                p.append(String.format(Locale.ROOT, " %d:%.0f", a, WarshipShipRules.firepowerAt(west, 0f,
                        west.x - (float) Math.sin(r) * d, west.y + (float) Math.cos(r) * d)));
            }
            System.out.printf(Locale.ROOT, "DIAG %s speed %.2f c/s turn %.2f d/s len %.1f battery %.1f longest %.1f arc %.0f %s fp%s%n",
                    loop, west.maxSpeed, west.maxTurnDegPerSec, west.lengthCells, WarshipDuelHelm.batteryReach(west),
                    WarshipShipFire.longestReach(west), west.shieldArcDegrees(), west.shieldType(), p);
        }
        Trace trace = new Trace(west, east);
        SceneRun.of(world).observe(trace)
                .frames(frames, loop, FRAME_EVERY, tick -> trace.caption(loop, tick))
                .run(TICKS);
        return report(loop, trace, sim.warships().shipFire());
    }

    private static void place(Warship ship, float x, float y) {
        ship.x = x;
        ship.y = y;
        ship.stationTo(x, y);
        ship.limitFlux();
        ship.useDoctrine();
        ship.shipFire(ShipFireOrder.ALL);
        ship.groundFire(Warship.GroundFire.HOLD);
    }

    private SceneReport report(String loop, Trace t, WarshipShipFire fire) {
        List<Verdict> verdicts = new ArrayList<>();
        Warship loser = t.loser();
        float decided = t.decisionTick < 0 || t.firstHitTick < 0 ? -1f : (t.decisionTick - t.firstHitTick) * TICK_SECONDS;
        float floor = loser == null ? 0f : MIN_TURNS * HullDurabilityScene.turnSeconds(loser, 90f);
        verdicts.add(Verdict.of("the-fight-is-decided", t.decisionTick >= 0 && !t.withdrawn,
                t.decisionTick < 0 ? "no kill in " + TICKS / 30 + "s"
                        : String.format(Locale.ROOT, "%s %s %.1fs after the first hit", loser == t.west ? "west" : "east",
                        t.withdrawn ? "withdrew (" + loser.postureReason() + ")" : "was destroyed", decided)));
        if (t.decisionTick >= 0) {
            verdicts.add(Verdict.of("decided-by-manoeuvre-not-a-blow", decided >= floor,
                    String.format(Locale.ROOT, "%.1fs against %.0f turns of the loser's (%.1fs)", decided, MIN_TURNS, floor)));
        }
        StringBuilder arc = new StringBuilder();
        boolean arcHeld = true;
        for (Warship s : List.of(t.west, t.east)) {
            int[] st = t.standoffArc.get(s);
            if (st == null || st[1] == 0) continue;
            float share = (float) st[0] / st[1];
            arc.append(String.format(Locale.ROOT, " %s %.0f%% of %d ticks", s == t.west ? "west" : "east", 100f * share, st[1]));
            arcHeld &= share >= STANDOFF_ARC_SHARE;
        }
        if (arc.length() > 0) {
            verdicts.add(Verdict.of("a-standoff-keeps-the-shield-on-the-target", arcHeld, "target in arc:" + arc));
        }
        StringBuilder flat = new StringBuilder();
        for (Warship s : List.of(t.west, t.east)) {
            float[] r = t.rangeSpan.get(s);
            if (r != null && r[1] < RANGE_BREATH * r[0]) {
                flat.append(String.format(Locale.ROOT, " %s %.0f-%.0f", s == t.west ? "west" : "east", r[0], r[1]));
            }
        }
        StringBuilder orbit = new StringBuilder();
        boolean orbitHeld = true;
        for (Warship s : List.of(t.west, t.east)) {
            int[] st = t.orbitArc.get(s);
            if (st == null || st[1] == 0) continue;
            float share = (float) st[0] / st[1];
            orbit.append(String.format(Locale.ROOT, " %s %.0f%% of %d ticks", s == t.west ? "west" : "east", 100f * share, st[1]));
            orbitHeld &= share >= STANDOFF_ARC_SHARE;
        }
        if (orbit.length() > 0) {
            verdicts.add(Verdict.of("an-orbit-keeps-the-shield-on-the-target", orbitHeld, "target in arc:" + orbit));
        }
        StringBuilder off = new StringBuilder();
        boolean someoneGotRound = false;
        for (Warship s : List.of(t.west, t.east)) {
            int[] o = t.offArc.get(s);
            float share = o[1] == 0 ? 0f : (float) t.position.getOrDefault(s, new int[1])[0] / o[1];
            someoneGotRound |= share >= OFF_ARC_SHARE;
            off.append(String.format(Locale.ROOT, " %s %.1f%% of %d in-reach ticks, %d crossings;",
                    s == t.west ? "west" : "east", 100f * share, o[1], t.crossings.getOrDefault(s, 0)));
        }
        verdicts.add(Verdict.of("a-ship-gets-round", someoneGotRound,
                "off the other's cover with guns bearing, or across its T:" + off));
        StringBuilder way = new StringBuilder();
        boolean underWay = true;
        for (Warship s : List.of(t.west, t.east)) {
            float share = t.wayShare(s);
            underWay &= share >= WAY_SHARE;
            way.append(String.format(Locale.ROOT, " %s %.0f cells, %.0f%% of top speed;", s == t.west ? "west" : "east",
                    t.flown.getOrDefault(s, 0f), 100f * share));
        }
        verdicts.add(Verdict.of("ships-keep-way-on", underWay, "in reach:" + way));
        verdicts.add(Verdict.of("range-breathes", flat.length() == 0,
                flat.length() == 0 ? "both opened the range by " + RANGE_BREATH + "x" : "held one range:" + flat));
        verdicts.add(Verdict.of("hulls-never-pass-through", t.worstExcess <= 0.05f,
                String.format(Locale.ROOT, "least gap %.1f cells, %.2f past the pair's tolerance", t.minGap, t.worstExcess)));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("firstHitSeconds", round1(t.firstHitTick < 0 ? -1f : t.firstHitTick * TICK_SECONDS));
        metrics.put("decisionSeconds", round1(decided));
        metrics.put("decision", t.decisionTick < 0 ? 0 : t.withdrawn ? 2 : 1);
        metrics.put("loser", loser == null ? 0 : loser == t.west ? 1 : 2);
        metrics.put("minTurnsFloorSeconds", round1(floor));
        metrics.put("minGapCells", round1(t.minGap));
        for (Warship s : List.of(t.west, t.east)) {
            String n = s == t.west ? "west" : "east";
            metrics.put(n + "DamageTaken", Math.round(t.damage.getOrDefault(s, 0f)));
            metrics.put(n + "HullPercent", Math.round(100f * Math.max(0f, s.hullFraction())));
            metrics.put(n + "Overloads", s.overloadCount());
            metrics.put(n + "MountsOut", s.mountsKnockedOut());
            metrics.put(n + "MountSilencings", t.silencings.getOrDefault(s, 0));
            metrics.put(n + "MountsLost", s.mountsLost());
            WarshipPlating p = s.plating();
            metrics.put(n + "Breaches", p == null ? 0 : p.breachCount());
            metrics.put(n + "FirstBreachSeconds", p == null || Float.isNaN(p.firstBreachAt()) || t.firstHitTick < 0 ? -1f
                    : round1(p.firstBreachAt() - t.firstHitTick * TICK_SECONDS));
            metrics.put(n + "HullCells", p == null ? 0 : p.hullCellCount());
            metrics.put(n + "MaxFluxPercent", Math.round(100f * t.maxFlux.getOrDefault(s, 0f)));
            int[] f = t.underFire.get(s);
            metrics.put(n + "UnderFireTicks", f[1]);
            metrics.put(n + "ExposedPercent", f[1] == 0 ? -1 : Math.round(100f * f[0] / f[1]));
            int[] st = t.standoffArc.get(s);
            metrics.put(n + "StandoffInArcPercent", st == null || st[1] == 0 ? -1 : Math.round(100f * st[0] / st[1]));
            metrics.put(n + "Manoeuvres", s.manoeuvre().begun());
            int[] o = t.offArc.get(s);
            metrics.put(n + "OffArcPercent", o[1] == 0 ? -1 : round1(100f * o[0] / o[1]));
            metrics.put(n + "TPercent", o[1] == 0 ? -1 : round1(100f * t.tShare.getOrDefault(s, new int[1])[0] / o[1]));
            metrics.put(n + "PositionPercent",
                    o[1] == 0 ? -1 : round1(100f * t.position.getOrDefault(s, new int[1])[0] / o[1]));
            metrics.put(n + "Crossings", t.crossings.getOrDefault(s, 0));
            metrics.put(n + "FlownCells", Math.round(t.flown.getOrDefault(s, 0f)));
            metrics.put(n + "WayPercent", Math.round(100f * t.wayShare(s)));
            float[] r = t.rangeSpan.get(s);
            if (r != null) {
                metrics.put(n + "RangeMin", round1(r[0]));
                metrics.put(n + "RangeMax", round1(r[1]));
            }
        }
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("hitSharePercent", fire.roundsFired() == 0 ? 0 : Math.round(100f * fire.roundsOnHull() / fire.roundsFired()));
        metrics.put("shieldType", t.west.shieldType().equals("front") ? 1 : t.west.shieldType().equals("omni") ? 2 : 0);
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    /** Whether {@code shooter} lies inside {@code ship}'s front shield arc (always, for an omni shield). */
    static boolean inArc(Warship ship, Warship shooter) {
        if ("omni".equals(ship.shieldType())) return true;
        float bearing = WarshipFacing.bearing(ship.centerX(), ship.centerY(), shooter.centerX(), shooter.centerY());
        return Math.abs(WarshipFacing.wrap(bearing - ship.facingDegrees)) <= 0.5f * ship.shieldArcDegrees();
    }

    private static final class Trace implements TickObserver {
        final Warship west;
        final Warship east;
        int firstHitTick = -1;
        int decisionTick = -1;
        boolean withdrawn;
        Warship decidedAgainst;
        final Map<Warship, Float> lastHp = new IdentityHashMap<>();
        final Map<Warship, Float> lastShield = new IdentityHashMap<>();
        final Map<Warship, Float> damage = new IdentityHashMap<>();
        /** Per ship: ticks under fire with the shooter outside its arc, and ticks under fire. */
        final Map<Warship, int[]> underFire = new IdentityHashMap<>();
        /** Per front-shield ship: standoff ticks with its target in its arc, and standoff ticks. */
        final Map<Warship, int[]> standoffArc = new IdentityHashMap<>();
        /** Per front-shield ship: orbit ticks with its target in its arc, and orbit ticks. */
        final Map<Warship, int[]> orbitArc = new IdentityHashMap<>();
        /**
         * Per ship: in-reach ticks in an off-arc firing position (its guns bearing on the other from outside the other's
         * cover, {@link WarshipTactics#offArc}), and in-reach ticks.
         */
        final Map<Warship, int[]> offArc = new IdentityHashMap<>();
        final Map<Warship, Integer> crossings = new IdentityHashMap<>();
        /** Per ship: in-reach ticks with its main battery on the other while the other's is not on it (its T crossed). */
        final Map<Warship, int[]> tShare = new IdentityHashMap<>();
        /** Per ship: in-reach ticks in either position, off the other's cover or across its T. */
        final Map<Warship, int[]> position = new IdentityHashMap<>();
        final Map<Warship, Integer> lastOffArcTick = new IdentityHashMap<>();
        final Map<Warship, Float> flown = new IdentityHashMap<>();
        final Map<Warship, float[]> lastPos = new IdentityHashMap<>();
        final Map<Warship, float[]> rangeSpan = new IdentityHashMap<>();
        final Map<Warship, Float> maxFlux = new IdentityHashMap<>();
        /** Per ship: mounts silenced by a breach while it still flew. */
        final Map<Warship, Integer> silencings = new IdentityHashMap<>();
        final Map<Warship, Integer> lastOut = new IdentityHashMap<>();
        float minGap = Float.MAX_VALUE;
        float worstExcess = Float.NEGATIVE_INFINITY;

        Trace(Warship west, Warship east) {
            this.west = west;
            this.east = east;
            underFire.put(west, new int[2]);
            underFire.put(east, new int[2]);
            offArc.put(west, new int[2]);
            offArc.put(east, new int[2]);
        }

        /** Mean speed while in reach of the other, as a share of top speed. */
        float wayShare(Warship s) {
            int ticks = offArc.get(s)[1];
            return ticks == 0 ? 0f : flown.getOrDefault(s, 0f) / (s.maxSpeed * ticks * TICK_SECONDS);
        }

        Warship loser() {
            return decidedAgainst;
        }

        String caption(String loop, int tick) {
            return String.format(Locale.ROOT, "WARSHIP MIRRORS / %s / %ds / west %s %d%% %s / east %s %d%% %s", loop,
                    tick / 30, west.hullId, Math.round(100f * Math.max(0f, west.hullFraction())), kind(west),
                    east.hullId, Math.round(100f * Math.max(0f, east.hullFraction())), kind(east));
        }

        private static String kind(Warship s) {
            if (s.destroyed()) return "down";
            if (s.departing() || s.departed()) return "withdrawing";
            WarshipManoeuvre.Kind k = s.manoeuvre().kind();
            return k == null ? "-" : k.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        }

        private static boolean out(Warship s) {
            return s.destroyed() || s.departing() || s.departed();
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (Warship s : List.of(west, east)) {
                float hp = Math.max(0f, s.hitPoints());
                float sh = s.shieldAbsorbedDamage();
                float d = lastHp.getOrDefault(s, hp) - hp + sh - lastShield.getOrDefault(s, sh);
                lastHp.put(s, hp);
                lastShield.put(s, sh);
                if (d > 0f) {
                    if (firstHitTick < 0) firstHitTick = tick;
                    damage.merge(s, d, Float::sum);
                }
            }
            if (decisionTick < 0) {
                // A kill on the same tick outranks the survivor leaving an empty sky.
                List<Warship> order = east.destroyed() && !west.destroyed() ? List.of(east, west) : List.of(west, east);
                for (Warship s : order) {
                    if (out(s)) {
                        decisionTick = tick;
                        decidedAgainst = s;
                        withdrawn = !s.destroyed();
                        break;
                    }
                }
            }
            for (Warship s : List.of(west, east)) {
                if (s.destroyed()) continue;
                int o = s.mountsKnockedOut();
                int was = lastOut.getOrDefault(s, 0);
                if (o > was) silencings.merge(s, o - was, Integer::sum);
                lastOut.put(s, o);
            }
            if (out(west) || out(east)) return;
            if (DIAG && tick % 30 == 0) {
                StringBuilder b = new StringBuilder();
                for (Warship s : List.of(west, east)) {
                    Warship o = s == west ? east : west;
                    float aspect = WarshipFacing.wrap(WarshipFacing.bearing(s.centerX(), s.centerY(), o.centerX(), o.centerY())
                            - s.facingDegrees);
                    b.append(String.format(Locale.ROOT, " | %s %-10s asp %4.0f spd %.2f hp %3.0f fx %2.0f%s",
                            s == west ? "W" : "E", kind(s), aspect, Math.hypot(s.velocityX, s.velocityY),
                            100f * s.hullFraction(), 100f * s.fluxFraction(), s.shieldUp() ? "S" : " "));
                }
                System.out.printf(Locale.ROOT, "T %4d rng %5.1f%s%n", tick / 30,
                        Math.hypot(west.centerX() - east.centerX(), west.centerY() - east.centerY()), b);
            }
            for (Warship s : List.of(west, east)) {
                maxFlux.merge(s, s.fluxFraction(), Math::max);
                Warship o = s == west ? east : west;
                boolean firing = o.hullTarget() == s
                        && Math.hypot(o.centerX() - s.centerX(), o.centerY() - s.centerY()) <= WarshipShipFire.longestReach(o);
                if (firing && !"none".equals(s.shieldType())) {
                    int[] f = underFire.get(s);
                    f[1]++;
                    if (!inArc(s, o)) f[0]++;
                }
                if ("front".equals(s.shieldType()) && s.manoeuvre().kind() == WarshipManoeuvre.Kind.ORBIT) {
                    int[] st = orbitArc.computeIfAbsent(s, k -> new int[2]);
                    st[1]++;
                    if (inArc(s, o)) st[0]++;
                }
                if (firstHitTick >= 0) {
                    float[] last = lastPos.get(s);
                    float gap = (float) Math.hypot(o.centerX() - s.centerX(), o.centerY() - s.centerY());
                    // The hull's own point, not its sprite centre, which swings with every turn.
                    if (last != null && gap <= WarshipShipFire.longestReach(s)) {
                        flown.merge(s, (float) Math.hypot(s.x - last[0], s.y - last[1]), Float::sum);
                    }
                    lastPos.put(s, new float[]{s.x, s.y});
                    if (gap <= WarshipShipFire.longestReach(s)) {
                        int[] oa = offArc.get(s);
                        oa[1]++;
                        boolean off = WarshipTactics.bearsOn(s, o) && WarshipTactics.offArc(o, s);
                        boolean crossingT = WarshipTactics.mainBears(s, o) && !WarshipTactics.mainBears(o, s);
                        if (off) oa[0]++;
                        if (crossingT) tShare.computeIfAbsent(s, k -> new int[1])[0]++;
                        if (off || crossingT) {
                            position.computeIfAbsent(s, k -> new int[1])[0]++;
                            Integer last2 = lastOffArcTick.get(s);
                            if (last2 == null || tick - last2 > CROSSING_GAP_TICKS) crossings.merge(s, 1, Integer::sum);
                            lastOffArcTick.put(s, tick);
                        }
                    }
                }
                if ("front".equals(s.shieldType()) && s.manoeuvre().kind() == WarshipManoeuvre.Kind.STANDOFF) {
                    int[] st = standoffArc.computeIfAbsent(s, k -> new int[2]);
                    st[1]++;
                    if (inArc(s, o)) st[0]++;
                }
                if (firstHitTick >= 0) {
                    float range = (float) Math.hypot(o.centerX() - s.centerX(), o.centerY() - s.centerY());
                    float[] r = rangeSpan.computeIfAbsent(s, k -> new float[]{Float.MAX_VALUE, 0f});
                    r[0] = Math.min(r[0], range);
                    r[1] = Math.max(r[1], range);
                }
            }
            if (WarshipSpacing.inContact(west) && WarshipSpacing.inContact(east)) {
                float g = WarshipSpacing.gap(west, east);
                minGap = Math.min(minGap, g);
                worstExcess = Math.max(worstExcess, -g - WarshipSpacing.contactTolerance(west, east));
            }
        }
    }
}
