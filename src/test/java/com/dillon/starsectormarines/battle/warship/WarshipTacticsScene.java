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
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fleet actions in atmosphere ({@code warship-tactics.md}): small groups of hulls fighting each other with nothing
 * scripted, read for spacing, target distribution and manoeuvre.
 *
 * <p>Loops:
 * <ul>
 *   <li>{@code pack-vs-escorted}: two Enforcers hunting a Dominator screened by two Lashers.</li>
 *   <li>{@code line-of-battle}: two Ventures (broadside ships) against two Enforcers (bow ships).</li>
 *   <li>{@code mirror}: two of Dormer's Wolfs against two more.</li>
 *   <li>{@code wolfs-vs-venture}, {@code wolfs-vs-enforcer}: two of Dormer's Wolfs against one bigger hull, flying the
 *   pack relay (one holds the target's attention while the other strikes, swapping to vent); each with a
 *   {@code -control} loop played first with the relay off ({@code -Dbattle.warship.packRelay=false}).</li>
 * </ul>
 * Every hull fights under {@link ShipFireOrder#ALL} with its duel helm, automatic shield and doctrine (so a beaten
 * ship can break off); the escorts are named as such. The sides start out of reach on opposite sides of the ground.
 *
 * <p>Verdicts assert mechanics: no two hulls ever pass through one another, no friendly pair closes inside the other's
 * footprint. Duration, damage each way, losses, the least friendly gap and how the hunters shared their targets are
 * readings. A relay loop also asserts its own mechanics against its control: the pack swaps, a venting ship is not
 * the target's target for most of its vent, and the pack spends less of the fight near overload than the control.
 * Swaps per minute, time each Wolf spends as the target's target, overloads, time to kill and damage each way are
 * readings.
 */
public final class WarshipTacticsScene implements BehaviorScene {

    /** The window grows with the hull fight's durability, as the fights it watches do. */
    private static final int TICKS = Integer.getInteger("battle.scene.warshipTactics.ticks",
            Math.round(9000 * WarshipScale.HULL_DURABILITY));
    private static final int FRAME_EVERY = 30;
    private static final float TICK_SECONDS = 1f / 30f;
    /** At least this many manoeuvres finished in a loop, and on average lasting at least this long. */
    private static final int MIN_MANOEUVRES = 4;
    private static final float MIN_MANOEUVRE_SECONDS = 10f;
    /** A manoeuvring ship's widest range to its target is at least this multiple of its closest. */
    private static final float RANGE_BREATH = 1.25f;
    /** The share of escorted ticks a pack spends on different hulls. */
    private static final int MIN_SPLIT_PERCENT = 75;
    /** The most of a loop's ticks two friendly hulls may spend brushing each other. */
    private static final float FRIENDLY_TOUCH_SHARE = 0.03f;
    /** The deepest a Wolf may lie over the Onslaught, in cells: the brush any two hulls are forgiven. */
    private static final float CONTACT_TOLERANCE_CELLS = WarshipSpacing.BRUSH_CELLS;

    /** One hull of a loop. {@code escortOf} is the index of the hull it screens, or -1. */
    private record Spec(String hull, String variant, Faction side, int escortOf) {}

    /**
     * {@code relay} false plays the loop with the pack relay off; {@code control} names the loop a relay loop is read
     * against; {@code start} (-1, 0 or +1) swings the east side's start round the ground, so one matchup is read from
     * more than one opening.
     */
    private record Loop(String id, List<Spec> west, List<Spec> east, boolean relay, String control, int start) {
        Loop(String id, List<Spec> west, List<Spec> east) {
            this(id, west, east, true, null, 0);
        }
    }

    /** How far the east side's start is swung for a relay loop's other openings, as a share of the ground's height. */
    private static final float START_SWING_SHARE = 0.22f;

    private static final Spec RUSK_WOLF = new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, -1);
    /** A pack ship this full (or overloaded) is near overload. */
    private static final float NEAR_OVERLOAD_FLUX = 0.9f;
    /** The share of its venting ticks a relay's venting ships may spend as the target's target, at most. */
    private static final float VENT_TARGETED_SHARE = 0.5f;

    private static final List<Loop> LOOPS = List.of(List.of(
            new Loop("pack-vs-escorted",
                    List.of(new Spec("enforcer", "enforcer_Assault", Faction.ALLY, -1),
                            new Spec("enforcer", "enforcer_Assault", Faction.ALLY, -1)),
                    List.of(new Spec("dominator", "dominator_Assault", Faction.DEFENDER, -1),
                            new Spec("lasher", "lasher_Standard", Faction.DEFENDER, 0),
                            new Spec("lasher", "lasher_Standard", Faction.DEFENDER, 0))),
            new Loop("line-of-battle",
                    List.of(new Spec("venture", "venture_Greyhook", Faction.ALLY, -1),
                            new Spec("venture", "venture_Greyhook", Faction.ALLY, -1)),
                    List.of(new Spec("enforcer", "enforcer_Assault", Faction.DEFENDER, -1),
                            new Spec("enforcer", "enforcer_Assault", Faction.DEFENDER, -1))),
            new Loop("mirror",
                    List.of(new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, -1),
                            new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.ALLY, -1)),
                    List.of(new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.DEFENDER, -1),
                            new Spec("wolf_hegemony", "wolf_hegemony_Rusk", Faction.DEFENDER, -1)))),
            relay("venture", "venture_Greyhook", -1), relay("venture", "venture_Greyhook", 0),
            relay("venture", "venture_Greyhook", 1),
            relay("enforcer", "enforcer_Assault", -1), relay("enforcer", "enforcer_Assault", 0),
            relay("enforcer", "enforcer_Assault", 1))
            .stream().flatMap(List::stream).toList();

    /** Two of Dormer's Wolfs on one bigger hull from one opening: the control (relay off) and the relay loop. */
    private static List<Loop> relay(String hull, String variant, int start) {
        String id = "wolfs-vs-" + hull + (start < 0 ? "-north" : start > 0 ? "-south" : "");
        List<Spec> pack = List.of(RUSK_WOLF, RUSK_WOLF);
        List<Spec> target = List.of(new Spec(hull, variant, Faction.DEFENDER, -1));
        return List.of(new Loop(id + "-control", pack, target, false, null, start),
                new Loop(id, pack, target, true, id + "-control", start));
    }

    @Override public String id() { return "warship-tactics"; }

    @Override public String label() {
        return "Warship tactics: a pack against an escorted heavy cruiser, a line of broadside ships, and a mirror "
                + "match, read for spacing, targets and manoeuvre";
    }

    @Override public int frameDelayMillis() { return 100; }

    @Override
    public List<SceneReport> play(FrameSink frames) throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        String only = System.getProperty("battle.scene.warshipTactics.only");
        List<SceneReport> reports = new ArrayList<>();
        Map<String, Trace> played = new LinkedHashMap<>();
        for (Loop loop : LOOPS) {
            if (only != null && !only.isBlank() && !List.of(only.split(",")).contains(loop.id())) continue;
            // A relay loop is read against its control, so the control plays too even when only the relay was asked for.
            if (loop.control() != null && !played.containsKey(loop.control())) {
                for (Loop c : LOOPS) if (c.id().equals(loop.control())) reports.add(playOne(c, frames, played));
            }
            reports.add(playOne(loop, frames, played));
        }
        if (only == null || only.isBlank() || List.of(only.split(",")).contains(FIGHTER_LOOP)) {
            reports.add(playFighterOverCapital(frames));
        }
        return reports;
    }

    private SceneReport playOne(Loop loop, FrameSink frames, Map<String, Trace> played) throws Exception {
        List<Warship> west = fit(loop.west(), 270f);
        List<Warship> east = fit(loop.east(), 90f);
        float reach = 0f;
        float length = 0f;
        for (Warship s : concat(west, east)) {
            reach = Math.max(reach, WarshipShipFire.longestReach(s));
            length = Math.max(length, s.lengthCells);
        }
        float start = 1.3f * reach + 2f * length;
        int width = Math.round(start + 6f * length + 60f);
        int height = Math.round(Math.max(2.2f * reach + 4f * length, 200f));
        SceneBuilder builder = SceneBuilder.openGround(width, height).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(1).at(3, 3).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(1).at(width - 4, height - 4).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        float midX = width * 0.5f;
        float midY = height * 0.5f;
        place(west, loop.west(), midX - start * 0.5f, midY, length);
        place(east, loop.east(), midX + start * 0.5f, midY + loop.start() * START_SWING_SHARE * height, length);
        for (Warship s : concat(west, east)) sim.addWarship(s);
        sim.warships().tactics().packRelay(loop.relay());
        Trace trace = new Trace(west, east);
        SceneRun run = SceneRun.of(world).observe(trace);
        if (frames != null) run.frames(frames, loop.id(), FRAME_EVERY, tick -> trace.caption(loop.id(), tick));
        run.run(TICKS);
        played.put(loop.id(), trace);
        return report(loop, trace, sim, loop.control() == null ? null : played.get(loop.control()));
    }

    private static final String FIGHTER_LOOP = "fighter-over-capital";
    private static final int FIGHTER_TICKS = 2400;

    /**
     * The control for hull contact: a fighter-sized hull may pass over a ship, a frigate may not. A held Onslaught lies
     * across the lane between two of the company's hunters and a hostile Lasher on the far side: a Broadsword and a
     * Wolf, each flying its duel helm at the Lasher, which fights nobody. The Broadsword flies straight over the
     * capital; the Wolf goes round it.
     */
    private SceneReport playFighterOverCapital(FrameSink frames) throws Exception {
        int width = 420;
        int height = 200;
        SceneBuilder builder = SceneBuilder.openGround(width, height).missionCompletion(false);
        builder.squad("west-ground").faction(Faction.MARINE).size(1).at(3, 3).unarmed().stationary().done();
        builder.squad("east-ground").faction(Faction.DEFENDER).size(1).at(width - 4, height - 4).unarmed()
                .stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) sim.getTopology().setGroundKind(x, y, GroundKind.GRASS);
        }
        float midY = height * 0.5f;
        Warship capital = Warship.fit("onslaught", "onslaught_Standard", Faction.ALLY, 200f, midY, 0f);
        capital.stationTo(capital.x, capital.y);
        capital.shipFire(ShipFireOrder.OFF);
        capital.groundFire(Warship.GroundFire.HOLD);
        Warship lasher = Warship.fit("lasher", "lasher_Standard", Faction.DEFENDER, 395f, midY, 90f);
        lasher.stationTo(lasher.x, lasher.y);
        lasher.shipFire(ShipFireOrder.OFF);
        lasher.groundFire(Warship.GroundFire.HOLD);
        Warship fighter = hunter("broadsword", "broadsword_Fighter", 40f, midY - 18f);
        Warship wolf = hunter("wolf_hegemony", "wolf_hegemony_Rusk", 40f, midY + 18f);
        for (Warship s : List.of(capital, lasher, fighter, wolf)) sim.addWarship(s);
        float[] deepest = new float[2];
        int[] overTicks = new int[2];
        TickObserver trace = (s, tick) -> {
            Warship[] pair = {fighter, wolf};
            for (int i = 0; i < 2; i++) {
                if (pair[i].destroyed()) continue;
                float g = WarshipSpacing.gap(pair[i], capital);
                if (g < 0f) {
                    overTicks[i]++;
                    deepest[i] = Math.max(deepest[i], -g);
                }
            }
        };
        SceneRun.of(world).observe(trace)
                .frames(frames, FIGHTER_LOOP, FRAME_EVERY, tick -> String.format(Locale.ROOT,
                        "WARSHIP TACTICS / %s / %ds / Broadsword over the Onslaught %d ticks, Wolf %d", FIGHTER_LOOP,
                        tick / 30, overTicks[0], overTicks[1]))
                .run(FIGHTER_TICKS);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("fighters-pass-over-ships", overTicks[0] > 0 && !WarshipSpacing.inContact(fighter),
                String.format(Locale.ROOT, "the Broadsword lay over the Onslaught for %d ticks, %.1f cells deep",
                        overTicks[0], deepest[0])));
        verdicts.add(Verdict.of("frigates-go-round", deepest[1] <= CONTACT_TOLERANCE_CELLS,
                String.format(Locale.ROOT, "the Wolf overlapped the Onslaught for %d ticks, at most %.1f cells",
                        overTicks[1], deepest[1])));
        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("fighterOverTicks", overTicks[0]);
        metrics.put("fighterDeepestCells", round1(deepest[0]));
        metrics.put("wolfOverTicks", overTicks[1]);
        metrics.put("wolfDeepestCells", round1(deepest[1]));
        metrics.put("wolfEndX", round1(wolf.x));
        metrics.put("fighterEndX", round1(fighter.x));
        return new SceneReport(id(), FIGHTER_LOOP, FIGHTER_TICKS, verdicts, metrics);
    }

    private static Warship hunter(String hull, String variant, float x, float y) throws Exception {
        Warship s = Warship.fit(hull, variant, Faction.ALLY, x, y, 270f);
        s.stationTo(x, y);
        s.limitFlux();
        s.shipFire(ShipFireOrder.ALL);
        s.groundFire(Warship.GroundFire.HOLD);
        return s;
    }

    private static List<Warship> fit(List<Spec> specs, float facing) throws Exception {
        List<Warship> out = new ArrayList<>();
        for (Spec s : specs) out.add(Warship.fit(s.hull(), s.variant(), s.side(), 0f, 0f, facing));
        return out;
    }

    /** Lays a side out in a column across the ground, a hull length and a half apart, escorts on their charge. */
    private static void place(List<Warship> ships, List<Spec> specs, float x, float midY, float length) {
        float spacing = 1.5f * length;
        for (int i = 0; i < ships.size(); i++) {
            Warship s = ships.get(i);
            s.x = x;
            s.y = midY + (i - (ships.size() - 1) * 0.5f) * spacing;
            s.stationTo(s.x, s.y);
            s.limitFlux();
            s.useDoctrine();
            s.shipFire(ShipFireOrder.ALL);
            s.groundFire(Warship.GroundFire.HOLD);
        }
        for (int i = 0; i < ships.size(); i++) {
            int lead = specs.get(i).escortOf();
            if (lead >= 0) ships.get(i).escort(ships.get(lead));
        }
    }

    private static List<Warship> concat(List<Warship> a, List<Warship> b) {
        List<Warship> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    private SceneReport report(Loop loop, Trace t, BattleSimulation sim, Trace control) {
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("hulls-never-pass-through", t.worstExcess <= 0.05f,
                String.format(Locale.ROOT, "deepest overlap at a tick's end %.2f cells (%.2f past the pair's"
                        + " tolerance), %d ticks with any overlap", t.deepestOverlap, t.worstExcess, t.overlapTicks)));
        // Contact is the backstop, not the behaviour: friends may brush (within the tolerance), briefly.
        verdicts.add(Verdict.of("friends-keep-clear", t.worstFriendlyExcess <= 0.05f
                        && t.friendlyOverlapTicks <= FRIENDLY_TOUCH_SHARE * TICKS,
                String.format(Locale.ROOT, "least friendly gap %.1f cells (%s), %d ticks with two friends overlapping"
                                + " from %ds (%s)", t.minFriendlyGap, t.closestFriends, t.friendlyOverlapTicks,
                        t.firstFriendlyTouchTick / 30, t.firstFriends)));
        verdicts.add(Verdict.of("the-sides-fight", t.damageToWest > 0f && t.damageToEast > 0f,
                String.format(Locale.ROOT, "west took %.0f, east took %.0f", t.damageToWest, t.damageToEast)));

        // The manoeuvre is the unit of behaviour: held for tens of seconds, not re-chosen every tick.
        float meanSeconds = t.meanManoeuvreSeconds();
        verdicts.add(Verdict.of("manoeuvres-are-held", t.finished.size() >= MIN_MANOEUVRES
                        && meanSeconds >= MIN_MANOEUVRE_SECONDS,
                String.format(Locale.ROOT, "%d manoeuvres finished, %.1fs on average", t.finished.size(), meanSeconds)));
        // Distance breathes: every ship that flew manoeuvres and lived through the window closed and opened the
        // range on its target. A hull shot down mid-fight had its manoeuvres cut short, so it is named, not judged.
        StringBuilder flat = new StringBuilder();
        StringBuilder sunk = new StringBuilder();
        for (Warship s : t.all) {
            float[] range = t.rangeSpan.get(s);
            if (range == null || s.manoeuvre().begun() < 2) continue;
            if (s.destroyed()) {
                sunk.append(' ').append(s.hullId);
                continue;
            }
            if (range[1] < RANGE_BREATH * range[0]) {
                flat.append(String.format(Locale.ROOT, " %s %.0f-%.0f", s.hullId, range[0], range[1]));
            }
        }
        verdicts.add(Verdict.of("range-breathes", flat.length() == 0,
                (flat.length() == 0 ? "every manoeuvring ship opened its range by " + RANGE_BREATH + "x"
                        : "held one range:" + flat)
                        + (sunk.length() == 0 ? "" : "; not judged, shot down mid-fight:" + sunk)));
        if (t.splitSampleTicks > 0) {
            int split = Math.round(100f * t.splitTicks / t.splitSampleTicks);
            verdicts.add(Verdict.of("the-pack-splits-on-an-escorted-charge", split >= MIN_SPLIT_PERCENT,
                    split + "% of escorted ticks with the pack on different hulls"));
        }

        if (control != null) relayVerdicts(t, control, verdicts);

        Map<String, Number> metrics = new LinkedHashMap<>();
        if (t.lone != null) relayMetrics(t, metrics);
        metrics.put("firstHitSeconds", round1(t.firstHitTick < 0 ? -1f : t.firstHitTick * TICK_SECONDS));
        metrics.put("fightSeconds", round1(t.firstHitTick < 0 ? 0f
                : ((t.resolvedTick < 0 ? TICKS : t.resolvedTick) - t.firstHitTick) * TICK_SECONDS));
        metrics.put("resolved", t.resolvedTick < 0 ? 0 : 1);
        metrics.put("damageToWest", Math.round(t.damageToWest));
        metrics.put("damageToEast", Math.round(t.damageToEast));
        metrics.put("westLost", lost(t.west));
        metrics.put("eastLost", lost(t.east));
        metrics.put("westBrokeOff", brokeOff(t.west));
        metrics.put("eastBrokeOff", brokeOff(t.east));
        metrics.put("minFriendlyGapCells", round1(t.minFriendlyGap));
        metrics.put("minHostileGapCells", round1(t.minHostileGap));
        metrics.put("sameTargetPercentWest", t.pairTicksWest == 0 ? -1 : Math.round(100f * t.sameTargetWest / t.pairTicksWest));
        metrics.put("sameTargetPercentEast", t.pairTicksEast == 0 ? -1 : Math.round(100f * t.sameTargetEast / t.pairTicksEast));
        WarshipShipFire fire = sim.warships().shipFire();
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("manoeuvresFinished", t.finished.size());
        metrics.put("meanManoeuvreSeconds", round1(t.meanManoeuvreSeconds()));
        for (WarshipManoeuvre.Kind k : WarshipManoeuvre.Kind.values()) {
            metrics.put("seconds" + k.name(), round1(t.kindTicks.getOrDefault(k, 0) * TICK_SECONDS));
        }
        metrics.put("splitWhileEscortedPercent", t.splitSampleTicks == 0 ? -1 : Math.round(100f * t.splitTicks / t.splitSampleTicks));
        metrics.put("lineHeldPercent", t.lineTicks == 0 ? -1 : Math.round(100f * t.lineHeldTicks / t.lineTicks));
        metrics.put("hitSharePercent", fire.roundsFired() == 0 ? 0 : Math.round(100f * fire.roundsOnHull() / fire.roundsFired()));
        HullContactReadings.put(metrics, fire);
        int i = 0;
        for (Warship s : concat(t.west, t.east)) {
            String n = (s.faction == Faction.ALLY ? "west" : "east") + (i % 4) + s.hullId;
            metrics.put(n + "HullPercent", Math.round(100f * Math.max(0f, s.hullFraction())));
            metrics.put(n + "Manoeuvres", t.kinds.getOrDefault(s, EnumSet.noneOf(WarshipManoeuvre.Kind.class)).size());
            int[] e = t.exposed.get(s);
            if (e != null && e[1] > 0) metrics.put(n + "ExposedPercent", Math.round(100f * e[0] / e[1]));
            float[] range = t.rangeSpan.get(s);
            if (range != null) {
                metrics.put(n + "RangeMin", round1(range[0]));
                metrics.put(n + "RangeMax", round1(range[1]));
            }
            i++;
        }
        return new SceneReport(id(), loop.id(), TICKS, verdicts, metrics);
    }

    /** The pack relay's mechanics, read against the same loop with the relay off. */
    private static void relayVerdicts(Trace t, Trace control, List<Verdict> verdicts) {
        int swaps = t.swaps();
        verdicts.add(Verdict.of("swaps-happen", swaps >= 1,
                String.format(Locale.ROOT, "%d swaps, %d vents (control: %d swaps)", swaps, t.vents(), control.swaps())));
        float targeted = t.ventTicks == 0 ? 1f : (float) t.ventTargetedTicks / t.ventTicks;
        verdicts.add(Verdict.of("venting-ship-not-targeted", t.ventTicks > 0 && targeted <= VENT_TARGETED_SHARE,
                String.format(Locale.ROOT, "venting %.1fs in all once turned away, %.0f%% of it as the target's target;"
                                + " %d of %d vents spent mostly out of its sights (and %.1fs handing over nose-on first,"
                                + " %.0f%% of it targeted)", t.ventTicks * TICK_SECONDS, 100f * targeted,
                        t.ventsMostlyClear, t.ventEpisodes, t.handoverTicks * TICK_SECONDS,
                        t.handoverTicks == 0 ? 0f : 100f * t.handoverTargetedTicks / t.handoverTicks)));
        float near = t.nearOverloadShare();
        float controlNear = control.nearOverloadShare();
        int overloads = t.overloads(t.west);
        int controlOverloads = control.overloads(control.west);
        // Read as a share of the fight, since a control that loses its pack quickly has little time in which to
        // overload at all: what counts is how much of the fight the pack spent at its limit.
        verdicts.add(Verdict.of("pack-flux-below-overload", near < controlNear,
                String.format(Locale.ROOT, "pack near overload %.1f%% of the fight against the control's %.1f%%;"
                        + " %d overloads against %d", 100f * near, 100f * controlNear, overloads, controlOverloads)));
    }

    /** Readings for a loop against one hull: who it fought, swaps, overloads, flux near the limit. */
    private static void relayMetrics(Trace t, Map<String, Number> metrics) {
        float minutes = Math.max(1f, t.fightTicks()) * TICK_SECONDS / 60f;
        metrics.put("swaps", t.swaps());
        metrics.put("swapsPerMinute", Math.round(100f * t.swaps() / minutes) / 100f);
        metrics.put("vents", t.vents());
        metrics.put("ventSeconds", round1(t.ventTicks * TICK_SECONDS));
        metrics.put("ventTargetedPercent", t.ventTicks == 0 ? -1 : Math.round(100f * t.ventTargetedTicks / t.ventTicks));
        metrics.put("handoverSeconds", round1(t.handoverTicks * TICK_SECONDS));
        metrics.put("handoverTargetedPercent",
                t.handoverTicks == 0 ? -1 : Math.round(100f * t.handoverTargetedTicks / t.handoverTicks));
        metrics.put("packOverloads", t.overloads(t.west));
        metrics.put("targetOverloads", t.overloads(t.east));
        metrics.put("packNearOverloadPercent", round1(100f * t.nearOverloadShare()));
        metrics.put("packOverloadedShipSeconds", round1(t.overloadedShipTicks * TICK_SECONDS));
        for (int i = 0; i < t.west.size(); i++) {
            Warship w = t.west.get(i);
            metrics.put("wolf" + i + "TargetedSeconds", round1(t.targetedTicks[i] * TICK_SECONDS));
            metrics.put("wolf" + i + "TankSeconds", round1(t.tankTicks[i] * TICK_SECONDS));
            metrics.put("wolf" + i + "PeakFluxPercent", Math.round(100f * t.peakFlux[i]));
            metrics.put("wolf" + i + "Overloads", w.overloadCount());
        }
        metrics.put("targetNoTargetSeconds", round1(t.untargetedTicks * TICK_SECONDS));
        metrics.put("targetPeakFluxPercent", Math.round(100f * t.lonePeakFlux));
        metrics.put("targetShieldAbsorbed", Math.round(t.lone.shieldAbsorbedDamage()));
    }

    private static float round1(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private static int lost(List<Warship> ships) {
        int n = 0;
        for (Warship s : ships) if (s.destroyed()) n++;
        return n;
    }

    private static int brokeOff(List<Warship> ships) {
        int n = 0;
        for (Warship s : ships) if (!s.destroyed() && (s.departing() || s.departed())) n++;
        return n;
    }

    private static final class Trace implements TickObserver {
        final List<Warship> west;
        final List<Warship> east;
        final List<Warship> all;
        int firstHitTick = -1;
        int resolvedTick = -1;
        float damageToWest;
        float damageToEast;
        float deepestOverlap;
        /** The most any pair lay in each other beyond {@link WarshipSpacing#contactTolerance}; and of friends. */
        float worstExcess = Float.NEGATIVE_INFINITY;
        float worstFriendlyExcess = Float.NEGATIVE_INFINITY;
        int overlapTicks;
        int friendlyOverlapTicks;
        float minFriendlyGap = Float.MAX_VALUE;
        /** Which friendly pair came closest, and on which tick; and the first tick two friends touched. */
        String closestFriends = "";
        int firstFriendlyTouchTick = -1;
        String firstFriends = "";
        float minHostileGap = Float.MAX_VALUE;
        int sameTargetWest;
        int pairTicksWest;
        int sameTargetEast;
        int pairTicksEast;
        final Map<Warship, Float> lastHp = new IdentityHashMap<>();
        final Map<Warship, Float> lastShield = new IdentityHashMap<>();
        /** Per hull, the least and most range to its hull target once the fight has begun. */
        final Map<Warship, float[]> rangeSpan = new IdentityHashMap<>();
        /** Per front-shielded hull: ticks under fire with a shooter outside its arc, and ticks under fire. */
        final Map<Warship, int[]> exposed = new IdentityHashMap<>();
        /** Per hull, the manoeuvre it flew last tick, and the ticks each manoeuvre it finished lasted. */
        final Map<Warship, WarshipManoeuvre.Kind> lastKind = new IdentityHashMap<>();
        final Map<Warship, Integer> kindSince = new IdentityHashMap<>();
        final Map<Warship, EnumSet<WarshipManoeuvre.Kind>> kinds = new IdentityHashMap<>();
        final List<Integer> finished = new ArrayList<>();
        final Map<WarshipManoeuvre.Kind, Integer> kindTicks = new EnumMap<>(WarshipManoeuvre.Kind.class);
        /** Ticks two hunters of a side held different hulls while the other side still had a live escort. */
        int splitTicks;
        int splitSampleTicks;
        /** Ticks a line's second ship was within its interval of its slot, of the ticks it sailed second. */
        int lineHeldTicks;
        int lineTicks;
        /** For a loop against one hull: that hull, and per west hull its ticks as the hull's target and as tank. */
        final Warship lone;
        final int[] targetedTicks;
        final int[] tankTicks;
        final float[] peakFlux;
        int untargetedTicks;
        float lonePeakFlux;
        /** Ticks a west hull spent venting in a relay, and of those the ticks it was the target's target. */
        int ventTicks;
        int ventTargetedTicks;
        /** Ticks a venting west hull spent still handing the target over (nose-on, before it turned away). */
        int handoverTicks;
        int handoverTargetedTicks;
        /** Vents ended or still running, and how many spent most of their ticks out of the target's sights. */
        int ventEpisodes;
        int ventsMostlyClear;
        final int[] ventRun;
        final int[] ventRunTargeted;
        /** Fight ticks with any live west hull near overload, west hulls overloaded summed over ticks, fight ticks. */
        int nearOverloadTicks;
        int overloadedShipTicks;
        int fightSampleTicks;

        Trace(List<Warship> west, List<Warship> east) {
            this.west = west;
            this.east = east;
            this.all = concat(west, east);
            this.lone = east.size() == 1 ? east.get(0) : null;
            this.targetedTicks = new int[west.size()];
            this.tankTicks = new int[west.size()];
            this.peakFlux = new float[west.size()];
            this.ventRun = new int[west.size()];
            this.ventRunTargeted = new int[west.size()];
        }

        int swaps() {
            int n = 0;
            for (Warship w : west) n += w.manoeuvre().relaySwaps();
            return n;
        }

        int vents() {
            int n = 0;
            for (Warship w : west) n += w.manoeuvre().relayVents();
            return n;
        }

        int overloads(List<Warship> side) {
            int n = 0;
            for (Warship w : side) n += w.overloadCount();
            return n;
        }

        float nearOverloadShare() {
            return fightSampleTicks == 0 ? 0f : (float) nearOverloadTicks / fightSampleTicks;
        }

        int fightTicks() {
            return firstHitTick < 0 ? 0 : (resolvedTick < 0 ? TICKS : resolvedTick) - firstHitTick;
        }

        private void closeVent(int i) {
            if (ventRun[i] == 0) return;
            ventEpisodes++;
            if (ventRunTargeted[i] * 2 < ventRun[i]) ventsMostlyClear++;
            ventRun[i] = 0;
            ventRunTargeted[i] = 0;
        }

        /** Per tick, for a loop against one hull: whom it fights, who tanks and vents, and the pack's flux. */
        private void relay() {
            if (lone == null || !live(lone)) {
                for (int i = 0; i < west.size(); i++) closeVent(i);
                return;
            }
            Warship sighted = lone.hullTarget();
            if (sighted == null) untargetedTicks++;
            lonePeakFlux = Math.max(lonePeakFlux, lone.fluxFraction());
            boolean fight = firstHitTick >= 0 && resolvedTick < 0;
            boolean near = false;
            for (int i = 0; i < west.size(); i++) {
                Warship w = west.get(i);
                if (!live(w)) {
                    closeVent(i);
                    continue;
                }
                if (sighted == w) targetedTicks[i]++;
                if (w.manoeuvre().role() == WarshipManoeuvre.Role.TANK) tankTicks[i]++;
                peakFlux[i] = Math.max(peakFlux[i], w.fluxFraction());
                if (w.manoeuvre().relayHandingOver()) {
                    handoverTicks++;
                    if (sighted == w) handoverTargetedTicks++;
                } else if (w.manoeuvre().relayVenting()) {
                    ventTicks++;
                    ventRun[i]++;
                    if (sighted == w) {
                        ventTargetedTicks++;
                        ventRunTargeted[i]++;
                    }
                } else {
                    closeVent(i);
                }
                if (fight) {
                    if (w.overloaded()) overloadedShipTicks++;
                    near |= w.overloaded() || w.fluxFraction() >= NEAR_OVERLOAD_FLUX;
                }
            }
            if (fight) {
                fightSampleTicks++;
                if (near) nearOverloadTicks++;
            }
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "WARSHIP TACTICS / %s / %ds", loop, tick / 30));
            for (Warship w : all) {
                s.append(" / ").append(w.faction == Faction.ALLY ? "W:" : "E:").append(w.hullId).append(' ')
                        .append(w.destroyed() ? "down" : w.departed() ? "gone"
                                : Math.round(100f * Math.max(0f, w.hullFraction())) + "%");
                if (lone != null && w.faction == Faction.ALLY && live(w)) {
                    WarshipManoeuvre m = w.manoeuvre();
                    String role = m.relayVenting() ? "VENT" : m.role() == WarshipManoeuvre.Role.TANK ? "TANK"
                            : m.role() == WarshipManoeuvre.Role.STRIKER ? "STRIKE" : "";
                    s.append(String.format(Locale.ROOT, " flux %d%%", Math.round(100f * w.fluxFraction())));
                    if (!role.isEmpty()) s.append(' ').append(role);
                    if (lone.hullTarget() == w) s.append(" (targeted)");
                }
            }
            return s.toString();
        }

        private static boolean live(Warship s) {
            return !s.destroyed() && !s.departed();
        }

        @Override
        public void observe(BattleSimulation sim, int tick) {
            for (Warship s : all) {
                float hp = Math.max(0f, s.hitPoints());
                float sh = s.shieldAbsorbedDamage();
                float d = lastHp.getOrDefault(s, hp) - hp + sh - lastShield.getOrDefault(s, sh);
                lastHp.put(s, hp);
                lastShield.put(s, sh);
                if (d <= 0f) continue;
                if (firstHitTick < 0) firstHitTick = tick;
                if (s.faction == Faction.ALLY) damageToWest += d;
                else damageToEast += d;
            }
            for (Warship s : all) {
                // A front-shielded hull under fire: is any shooter on it outside its shield's arc?
                if (!live(s) || !"front".equals(s.shieldType())) continue;
                boolean fired = false;
                boolean flanked = false;
                for (Warship o : all) {
                    if (o.faction == s.faction || !live(o) || o.hullTarget() != s) continue;
                    if (Math.hypot(o.centerX() - s.centerX(), o.centerY() - s.centerY()) > WarshipShipFire.longestReach(o)) {
                        continue;
                    }
                    fired = true;
                    flanked |= !MirrorDuelScene.inArc(s, o);
                }
                if (!fired) continue;
                int[] e = exposed.computeIfAbsent(s, k -> new int[2]);
                e[1]++;
                if (flanked) e[0]++;
            }
            boolean anyOverlap = false;
            boolean friendlyOverlap = false;
            for (int i = 0; i < all.size(); i++) {
                Warship a = all.get(i);
                if (!WarshipSpacing.inContact(a)) continue;
                for (int j = i + 1; j < all.size(); j++) {
                    Warship b = all.get(j);
                    if (!WarshipSpacing.inContact(b)) continue;
                    float g = WarshipSpacing.gap(a, b);
                    if (g < 0f) {
                        anyOverlap = true;
                        deepestOverlap = Math.max(deepestOverlap, -g);
                    }
                    float excess = -g - WarshipSpacing.contactTolerance(a, b);
                    worstExcess = Math.max(worstExcess, excess);
                    if (a.faction == b.faction) worstFriendlyExcess = Math.max(worstFriendlyExcess, excess);
                    if (a.faction == b.faction) {
                        if (g < minFriendlyGap) {
                            minFriendlyGap = g;
                            closestFriends = a.hullId + "/" + b.hullId + " at " + tick / 30 + "s ("
                                    + a.manoeuvre().kind() + "/" + b.manoeuvre().kind() + ")";
                        }
                        if (g < 0f && firstFriendlyTouchTick < 0) {
                            firstFriendlyTouchTick = tick;
                            firstFriends = a.hullId + "/" + b.hullId + " (" + a.manoeuvre().kind() + "/"
                                    + b.manoeuvre().kind() + ")";
                        }
                        friendlyOverlap |= g < 0f;
                    } else {
                        minHostileGap = Math.min(minHostileGap, g);
                    }
                }
            }
            if (anyOverlap) overlapTicks++;
            if (friendlyOverlap) friendlyOverlapTicks++;
            sharing(west, true);
            sharing(east, false);
            if (firstHitTick >= 0) {
                for (Warship s : all) {
                    Warship tgt = s.hullTarget();
                    if (!live(s) || tgt == null || !live(tgt)) continue;
                    float r = (float) Math.hypot(tgt.centerX() - s.centerX(), tgt.centerY() - s.centerY());
                    float[] span = rangeSpan.computeIfAbsent(s, k -> new float[]{Float.MAX_VALUE, 0f});
                    span[0] = Math.min(span[0], r);
                    span[1] = Math.max(span[1], r);
                }
            }
            for (Warship s : all) {
                WarshipManoeuvre.Kind k = live(s) ? s.manoeuvre().kind() : null;
                WarshipManoeuvre.Kind was = lastKind.get(s);
                if (k != was) {
                    if (was != null) finished.add(tick - kindSince.getOrDefault(s, tick));
                    kindSince.put(s, tick);
                    lastKind.put(s, k);
                    if (k != null) kinds.computeIfAbsent(s, x -> EnumSet.noneOf(WarshipManoeuvre.Kind.class)).add(k);
                }
                if (k != null) kindTicks.merge(k, 1, Integer::sum);
                if (k == WarshipManoeuvre.Kind.LINE_AHEAD && s.manoeuvre().leader() != null) {
                    Warship lead = s.manoeuvre().leader();
                    float interval = 0.5f * lead.lengthCells + 0.5f * s.lengthCells
                            + WarshipTactics.LINE_GAP_SHARE * WarshipEscort.GAP_CELLS;
                    double back = Math.toRadians(lead.facingDegrees + 180f);
                    float sx = lead.centerX() - (float) Math.sin(back) * interval;
                    float sy = lead.centerY() + (float) Math.cos(back) * interval;
                    lineTicks++;
                    if (Math.hypot(sx - s.centerX(), sy - s.centerY()) <= 0.5f * interval) lineHeldTicks++;
                }
            }
            split(west, east);
            split(east, west);
            relay();
            if (resolvedTick < 0 && firstHitTick >= 0) {
                boolean westOver = false;
                boolean eastOver = false;
                for (Warship s : west) westOver |= live(s) && !s.departing();
                for (Warship s : east) eastOver |= live(s) && !s.departing();
                if (!westOver || !eastOver) resolvedTick = tick;
            }
        }

        /** While {@code theirs} has a live escort, counts whether two hunters of {@code ours} hold different hulls. */
        private void split(List<Warship> ours, List<Warship> theirs) {
            boolean escorted = false;
            for (Warship t : theirs) escorted |= live(t) && WarshipEscort.liveLead(t) != null;
            if (!escorted) return;
            List<Warship> targets = new ArrayList<>();
            for (Warship s : ours) {
                if (live(s) && !s.departing() && WarshipEscort.liveLead(s) == null && s.hullTarget() != null) {
                    targets.add(s.hullTarget());
                }
            }
            if (targets.size() < 2) return;
            splitSampleTicks++;
            if (targets.get(0) != targets.get(1)) splitTicks++;
        }

        float meanManoeuvreSeconds() {
            if (finished.isEmpty()) return 0f;
            long sum = 0;
            for (int t : finished) sum += t;
            return sum * TICK_SECONDS / finished.size();
        }

        /** Counts ticks where two live hunters of a side (not escorts) are on the same hull. */
        private void sharing(List<Warship> side, boolean isWest) {
            List<Warship> hunters = new ArrayList<>();
            for (Warship s : side) {
                if (live(s) && !s.departing() && WarshipEscort.liveLead(s) == null && s.hullTarget() != null) hunters.add(s);
            }
            if (hunters.size() < 2) return;
            boolean same = false;
            for (int i = 0; i < hunters.size(); i++) {
                for (int j = i + 1; j < hunters.size(); j++) same |= hunters.get(i).hullTarget() == hunters.get(j).hullTarget();
            }
            if (isWest) {
                pairTicksWest++;
                if (same) sameTargetWest++;
            } else {
                pairTicksEast++;
                if (same) sameTargetEast++;
            }
        }
    }
}
