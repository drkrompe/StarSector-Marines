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
import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.warship.HullContactReadings;
import com.dillon.starsectormarines.battle.warship.HullFireHold;
import com.dillon.starsectormarines.battle.warship.HullRefit;
import com.dillon.starsectormarines.battle.warship.MapEdge;
import com.dillon.starsectormarines.battle.warship.MissileOpeningReadings;
import com.dillon.starsectormarines.battle.warship.MountLayReadings;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.battle.warship.WarshipDuelHelm;
import com.dillon.starsectormarines.battle.warship.WarshipDuelLedger;
import com.dillon.starsectormarines.battle.warship.WarshipEdgeHelm;
import com.dillon.starsectormarines.battle.warship.WarshipManoeuvre;
import com.dillon.starsectormarines.battle.warship.WarshipPointDefence;
import com.dillon.starsectormarines.battle.warship.WarshipPosture;
import com.dillon.starsectormarines.battle.warship.WarshipShipFire;
import com.dillon.starsectormarines.battle.warship.WarshipSpacing;
import com.dillon.starsectormarines.battle.warship.WarshipWreck;
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
import java.util.function.Function;

/**
 * The production Black Sluice duel ({@link BlackSluiceDuel}) on open ground the size of the Black Sluice map, without
 * the ground battle: the <i>Greyhook</i> (a Venture) alone in her cradle, Voss's order, and the pair Dormer lends, a
 * Hegemony Enforcer and his own Wolf, each in its refit, flying in across the west edge, heading east. The duel is the
 * production class itself, so its stations, fittings, refits, edges, route and visit are the ones the chapter plays.
 *
 * <p>Five loops, one per seed. A seed moves when the order comes and where along the west edge each of Dormer's hulls
 * flies in by a few cells, which is all the chapter's ground battle varies for the ships; everything after Voss's order
 * to run ({@link BlackSluiceDuel.BreakOff}) is the ships' own doing. Verdicts assert mechanics (both sides put rounds on
 * hulls, nobody fires on the ground, Dormer's hulls fly in on their heading and cross it outside their reach of every
 * hull of Greyhook's, the radio hears them sighted, closing and firing in that order, hears the run ordered when it is,
 * and hears the first of them, the Greyhook or the last of them go down and the Greyhook get away when they do, the
 * Greyhook fires on a hull of Dormer's in her reach, no wing launches, every hull arrives from its pad or across its
 * side's edge, the Greyhook alone leaves and only across her own edge after the order to run, no doctrine withdraws a
 * hull, the duel concludes with her gone off the map or her wreck at rest, the battle is held open exactly until it
 * does, and a hull the edge pressure holds stays out of its edge margin to within a tolerance); whether she gets away,
 * when each hull fell, how long it lasts, the Greyhook's hull over time, breaches and mounts lost, where over the map it
 * was fought and what each side did to the other are readings, as is how long each of Dormer's hulls held the relay's
 * tank. A loop ends a few seconds after the duel concludes. The fight's centroid
 * is sampled every thirty seconds. Each of Dormer's shields is traced ({@link ShieldTrace}): how long it was up, overall and under fire, where the Greyhook lay off its centre line,
 * which rounds it blocked and which reached the hull with the shield down, rising or up but outside the arc, and what
 * dropped it.
 */
public final class GreyhookDuelScene implements BehaviorScene {

    private static final int WIDTH = BlackSluice.WIDTH;
    private static final int HEIGHT = BlackSluice.HEIGHT;
    /**
     * Ten minutes after the start, the recording's bound: the duel concludes well inside it, and a loop stops
     * {@link #TAIL_TICKS} after it does; a duel still going at the end fails {@code the-duel-concludes}.
     * {@code -Dbattle.scene.greyhookDuel.ticks} plays longer or shorter.
     */
    private static final int TICKS = Integer.getInteger("battle.scene.greyhookDuel.ticks", 18000);
    /** Ticks a loop plays on after the duel concludes, so the wreck at rest or the empty sky is recorded. */
    private static final int TAIL_TICKS = 150;
    /**
     * Ticks between recorded frames outside the duel camera's dense stretches; {@code
     * -Dbattle.scene.greyhookDuel.frameEvery} raises it so a whole fifteen-minute fight fits a review GIF's size.
     */
    private static final int FRAME_EVERY = Math.max(1, Integer.getInteger("battle.scene.greyhookDuel.frameEvery", 30));
    /**
     * Seconds after the order before any frame is recorded, {@code -Dbattle.scene.greyhookDuel.framesFrom}: a review
     * excerpt of the fight's end at a dense cadence, rather than the whole fight at a sparse one. The run and its
     * verdicts are unchanged; the recorder (and its camera) starts at that tick.
     */
    private static final int FRAMES_FROM_SECONDS = Math.max(0, Integer.getInteger("battle.scene.greyhookDuel.framesFrom", 0));
    private static final long[] SEEDS = {15305L, 16205L, 16206L, 20261016L, 31337L};
    /**
     * How far, as a share of its own margin, a hull the edge pressure holds may still be found inside that margin: the
     * pressure is soft and starts at the margin, so a hull turning off an edge at speed sinks a little way into it.
     */
    static final float EDGE_TOLERANCE_SHARE = 0.25f;
    /**
     * The least share of its in-reach time a turret must spend laid on the hull it is fighting: it stays on through its
     * cooldown and every hold, so only the slew onto a new bearing and a target crossing faster than it traverses
     * take it off.
     */
    static final float LAID_SHARE = 0.75f;

    @Override public String id() { return "greyhook-duel"; }

    /**
     * The pair Dormer lends for this play: the mission's own, each hull in order replaced by
     * {@code -Dbattle.scene.greyhookDuel.variant} ({@code hull:variant}, comma-separated, one per lent hull; a tuning
     * diagnostic, the mission is unchanged), and each hull's refit by {@code -Dbattle.scene.greyhookDuel.refit} (the
     * first hull, the Enforcer) and {@code .wolfRefit} (the second), naming {@code none} (the variant as fitted, the
     * control) or five figures {@code capacity,dissipation,arc,upkeep,efficiency}.
     */
    static List<BlackSluiceDuel.RuskHull> pair() {
        List<BlackSluiceDuel.RuskHull> out = new ArrayList<>(BlackSluiceDuel.RUSK_PAIR);
        String raw = System.getProperty("battle.scene.greyhookDuel.variant");
        if (raw != null && !raw.isBlank()) {
            String[] fits = raw.split(",");
            for (int i = 0; i < fits.length && i < out.size(); i++) {
                String[] p = fits[i].split(":");
                if (p.length != 2) throw new IllegalArgumentException("variant takes hull:variant[,hull:variant]: " + raw);
                BlackSluiceDuel.RuskHull h = out.get(i);
                out.set(i, h.with(p[0].trim(), p[1].trim(), h.refit()));
            }
        }
        String[] refits = {"battle.scene.greyhookDuel.refit", "battle.scene.greyhookDuel.wolfRefit"};
        for (int i = 0; i < out.size() && i < refits.length; i++) {
            BlackSluiceDuel.RuskHull h = out.get(i);
            out.set(i, h.with(h.hullId(), h.variantId(), refit(refits[i], h.refit())));
        }
        return out;
    }

    /** The Greyhook's refit for this play, read the same way from {@code -Dbattle.scene.greyhookDuel.greyhookRefit}. */
    static HullRefit greyhookRefit() {
        return refit("battle.scene.greyhookDuel.greyhookRefit", BlackSluiceDuel.GREYHOOK_REFIT);
    }

    private static HullRefit refit(String property, HullRefit production) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) return production;
        if (raw.trim().equalsIgnoreCase("none")) return HullRefit.NONE;
        String[] p = raw.split(",");
        if (p.length != 5) throw new IllegalArgumentException("refit takes capacity,dissipation,arc,upkeep,efficiency: " + raw);
        float[] f = new float[5];
        for (int i = 0; i < 5; i++) f[i] = Float.parseFloat(p[i].trim());
        return new HullRefit("diagnostic", f[0], f[1], f[2], f[3], f[4]);
    }

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
        return "The Black Sluice duel: the Greyhook (Venture) lifting alone from her cradle to fight Dormer's "
                + "Enforcer and Wolf, then running for the east edge, five seeds";
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
        String base = "seed-" + seed;
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
        HullRefit greyhookRefit = greyhookRefit();
        List<BlackSluiceDuel.RuskHull> pair = pair();
        String greyhookFit = System.getProperty("battle.scene.greyhookDuel.greyhookVariant", BlackSluiceDuel.GREYHOOK_VARIANT);
        boolean production = greyhookRefit == BlackSluiceDuel.GREYHOOK_REFIT && pair.equals(BlackSluiceDuel.RUSK_PAIR)
                && greyhookFit.equals(BlackSluiceDuel.GREYHOOK_VARIANT);
        BlackSluiceDuel duel = new BlackSluiceDuel(greyhookFit, pair, greyhookRefit);
        duel.stage(sim);
        String loop = production ? base : base + "-diagnostic";
        Trace trace = new Trace(duel, sim, orderTick);
        TickObserver script = (s, tick) -> {
            if (tick == orderTick) {
                for (HullFireHold hold : s.warships().hullFireHolds()) hold.release();
                duel.withdrawalOrdered(s);
                List<Warship> rusk = duel.rusk();
                for (int i = 0; i < rusk.size(); i++) {
                    // Along the edge only: each still flies in across it, from a few cells up or down the line.
                    Warship ship = rusk.get(i);
                    ship.y += jitter[2 * i + 1];
                    ship.stationTo(ship.stationX(), ship.stationY() + jitter[2 * i + 1]);
                }
            }
            duel.advance(s);
        };
        SceneRun run = SceneRun.of(world).observe(script).observe(trace)
                .frames(from(frames, orderTick + 30 * FRAMES_FROM_SECONDS), loop, FRAME_EVERY,
                        tick -> trace.caption(loop, tick));
        while (run.tick() < TICKS && (trace.concludedTick < 0 || run.tick() <= trace.concludedTick + TAIL_TICKS)) {
            run.step();
        }
        return report(loop, trace, duel);
    }

    /** {@code sink} with nothing recorded, and its recorder not advanced, before {@code firstTick}. */
    private static FrameSink from(FrameSink sink, int firstTick) {
        if (firstTick <= 0) return sink;
        return new FrameSink() {
            @Override
            public void frame(String loopId, BattleSimulation sim, int tick, String caption) {
                sink.frame(loopId, sim, tick, caption);
            }

            @Override
            public void advanced(String loopId, BattleSimulation sim, int tick) {
                if (tick >= firstTick) sink.advanced(loopId, sim, tick);
            }

            @Override
            public boolean records(String loopId, BattleSimulation sim, int tick, int sceneEveryTicks) {
                return tick >= firstTick && sink.records(loopId, sim, tick, sceneEveryTicks);
            }
        };
    }

    private SceneReport report(String loop, Trace t, BlackSluiceDuel duel) {
        Warship greyhook = duel.greyhook();
        List<Warship> ours = List.of(greyhook);
        List<Verdict> verdicts = new ArrayList<>();
        verdicts.add(Verdict.of("both-sides-put-rounds-on-hulls",
                t.damageToGreyhookSide > 0f && t.damageToRusk > 0f,
                String.format(Locale.ROOT, "Greyhook's side took %.0f (hull and shield), Dormer's %.0f",
                        t.damageToGreyhookSide, t.damageToRusk)));
        int groundShots = 0;
        for (Warship hull : duel.hulls()) groundShots += hull.groundShots();
        verdicts.add(Verdict.of("nobody-fires-on-the-ground", groundShots == 0, groundShots + " ground shots"));
        verdicts.add(Verdict.of("the-greyhook-fires-on-a-rusk-hull-in-her-reach",
                t.ruskInReach == 0 || t.greyhookShots > 0,
                t.ruskInReach == 0 ? "none of Dormer's hulls came into her reach"
                        : String.format(Locale.ROOT, "%d rounds, one of Dormer's hulls inside her reach for %d ticks",
                        t.greyhookShots, t.ruskInReach)));
        verdicts.add(Verdict.of("rusk-flies-in-on-the-heading", t.ruskEntryHeadingError <= 1f,
                String.format(Locale.ROOT, "in across the west edge at most %.1f degrees off east", t.ruskEntryHeadingError)));
        verdicts.add(Verdict.of("rusk-crosses-the-edge-outside-reach", t.entryGap > 0f,
                String.format(Locale.ROOT, "as the first of Dormer's hulls crossed the edge the nearest of Greyhook's hulls was "
                        + "%.0f cells beyond its %.0f-cell reach", t.entryGap, t.entryReach)));
        verdicts.add(Verdict.of("hulls-arrive-only-from-a-pad-or-across-their-edge", t.badArrivals.isEmpty(),
                t.badArrivals.isEmpty() ? t.hullsSeen + " hulls: Greyhook's on their pads, Dormer's off the west edge"
                        : String.join("; ", t.badArrivals)));
        verdicts.add(Verdict.of("only-the-greyhook-leaves-and-only-across-her-edge", t.leavers.isEmpty(),
                t.leavers.isEmpty() ? (greyhook.departing() || greyhook.departed()
                        ? String.format(Locale.ROOT, "the Greyhook alone, across the %s edge, from %.0fs (the order to run)",
                        greyhook.exitEdge(), t.seconds(duel.breakTick())) : "none left, none started to")
                        : String.join("; ", t.leavers)));
        verdicts.add(Verdict.of("no-hull-crosses-an-edge-once-over-the-map", t.foreignCrossings.isEmpty(),
                t.foreignCrossings.isEmpty() ? "none" : String.join("; ", t.foreignCrossings)));
        verdicts.add(Verdict.of("nobody-withdraws-by-doctrine", t.withdrawals.isEmpty(),
                t.withdrawals.isEmpty() ? "no hull took the withdraw posture" : String.join("; ", t.withdrawals)));
        boolean greyhookSideDown = allDown(ours), ruskDown = allDown(duel.rusk());
        WarshipDuelLedger.Entry fate = duel.entry(greyhook);
        boolean escaped = fate != null && fate.outcome() == WarshipDuelLedger.Outcome.ESCAPED;
        verdicts.add(Verdict.of("the-duel-concludes",
                t.concludedTick >= 0 && (escaped || greyhookSideDown) && t.leavers.isEmpty(),
                escaped ? String.format(Locale.ROOT, "the Greyhook off the map across the %s edge at %.0fs",
                        greyhook.exitEdge(), t.seconds(t.concludedTick))
                        : greyhookSideDown ? String.format(Locale.ROOT, "the Greyhook destroyed at %.0fs, her wreck at rest at %.0fs",
                        t.seconds(t.decidedTick), t.seconds(t.concludedTick))
                        : String.format(Locale.ROOT, "the Greyhook still over the battle %.0fs after the order (%s)",
                        t.seconds(t.lastTick), fate == null ? "no ledger line" : fate.outcome())));
        verdicts.add(Verdict.of("a-destroyed-greyhook-crashes-to-rest",
                !greyhookSideDown || t.wreckRestTick >= 0 && t.wreckStruck,
                !greyhookSideDown ? "she was not destroyed"
                        : String.format(Locale.ROOT, "burst %.1fs, struck the ground %s, at rest %.1fs after the order",
                        t.seconds(t.decidedTick), t.wreckStruck ? "yes" : "no", t.seconds(t.wreckRestTick))));
        verdicts.add(Verdict.of("the-battle-is-held-open-until-she-concludes", t.holdErrors.isEmpty(),
                t.holdErrors.isEmpty() ? String.format(Locale.ROOT,
                        "held from the order to %.1fs, released on the tick she concluded", t.seconds(t.concludedTick))
                        : String.join("; ", t.holdErrors)));
        verdicts.add(Verdict.of("the-run-is-ordered-once-by-voss",
                duel.breakOff() != BlackSluiceDuel.BreakOff.NONE || greyhookSideDown,
                String.format(Locale.ROOT, "%s at %.1fs (first hull fire %.1fs)", duel.breakOff(),
                        t.seconds(duel.breakTick()), t.seconds(duel.firstFireTick()))));
        int sighted = t.beatTick(BlackSluiceDuel.RUSK_SIGHTED_BEAT);
        int closing = t.beatTick(BlackSluiceDuel.RUSK_CLOSING_BEAT);
        int fired = t.beatTick(BlackSluiceDuel.FIRST_HULL_FIRE_BEAT);
        verdicts.add(Verdict.of("the-radio-hears-the-approach-in-order",
                sighted >= 0 && (closing < 0 || closing >= sighted) && (fired < 0 || (closing >= 0 && fired >= closing)),
                String.format(Locale.ROOT, "sighted %.1fs, closing %.1fs, first hull fire %.1fs after the order",
                        t.seconds(sighted), t.seconds(closing), t.seconds(fired))));
        int breaksBeat = t.beatTick(BlackSluiceDuel.GREYHOOK_BREAKS_BEAT);
        int escapedBeat = t.beatTick(BlackSluiceDuel.GREYHOOK_ESCAPED_BEAT);
        int goneTick = escaped ? t.decidedTick : -1;
        verdicts.add(Verdict.of("the-radio-hears-the-run",
                (duel.breakTick() < 0 ? breaksBeat < 0 : breaksBeat >= duel.breakTick() && breaksBeat - duel.breakTick() <= 1)
                        && (goneTick < 0 ? escapedBeat < 0 : escapedBeat >= goneTick && escapedBeat - goneTick <= 1),
                String.format(Locale.ROOT, "run ordered %.1fs, beat %.1fs; off the map %.1fs, beat %.1fs",
                        t.seconds(duel.breakTick()), t.seconds(breaksBeat), t.seconds(goneTick), t.seconds(escapedBeat))));
        int greyhookBeat = t.beatTick(BlackSluiceDuel.GREYHOOK_DOWN_BEAT);
        int ruskBeat = t.beatTick(BlackSluiceDuel.RUSK_DOWN_BEAT);
        int firstBeat = t.beatTick(BlackSluiceDuel.RUSK_FIRST_DOWN_BEAT);
        int greyhookDown = t.downTick.getOrDefault(greyhook, -1);
        int lastRuskDown = -1, firstRuskDown = -1;
        for (Warship w : duel.rusk()) {
            int down = t.downTick.getOrDefault(w, -1);
            lastRuskDown = Math.max(lastRuskDown, down);
            if (down >= 0 && (firstRuskDown < 0 || down < firstRuskDown)) firstRuskDown = down;
        }
        // The first of Dormer's going down is heard only while the Greyhook and another of his still fly.
        boolean firstHeard = firstRuskDown >= 0 && (!ruskDown || firstRuskDown < lastRuskDown)
                && (greyhookDown < 0 || greyhookDown > firstRuskDown);
        verdicts.add(Verdict.of("the-radio-hears-who-goes-down",
                (greyhookDown < 0 ? greyhookBeat < 0 : greyhookBeat >= greyhookDown && greyhookBeat - greyhookDown <= 1)
                        && (ruskDown ? ruskBeat >= lastRuskDown && ruskBeat - lastRuskDown <= 1 : ruskBeat < 0)
                        && (firstHeard ? firstBeat >= firstRuskDown && firstBeat - firstRuskDown <= 1 : firstBeat < 0),
                String.format(Locale.ROOT, "Greyhook down %.1fs, beat %.1fs; first of Dormer's down %.1fs, beat %.1fs; "
                                + "last down %.1fs, beat %.1fs",
                        t.seconds(greyhookDown), t.seconds(greyhookBeat), t.seconds(firstRuskDown), t.seconds(firstBeat),
                        ruskDown ? t.seconds(lastRuskDown) : -1f, t.seconds(ruskBeat))));
        verdicts.add(Verdict.of("held-hulls-stay-out-of-their-edge-margin",
                t.worstMarginShare >= -EDGE_TOLERANCE_SHARE,
                String.format(Locale.ROOT, "least clear air %.1f cells against a margin of %.1f (%s), %d ticks inside it",
                        t.leastClearance, t.leastClearanceMargin, t.leastClearanceWho, t.ticksInsideMargin)));
        verdicts.add(Verdict.of("friendly-hulls-do-not-overlap", t.worstFriendlyExcess <= 0.05f,
                String.format(Locale.ROOT, "least friendly gap %.1f cells (%s), %d ticks touching", t.minFriendlyGap,
                        t.closestFriends, t.friendlyOverlapTicks)));
        verdicts.add(Verdict.of("no-wing-launches", t.defenderAircraft == 0,
                t.defenderAircraft + " Greyhook aircraft over the battle"));
        WarshipShipFire shipFire = t.sim.warships().shipFire();
        List<Warship> hulls = duel.hulls();
        Function<Warship, String> named = s -> {
            int i = hulls.indexOf(s);
            return i >= 0 && i < t.names.length ? t.names[i] : s.hullId;
        };
        if (shipFire.missileWindows() && shipFire.missileRestraint()) verdicts.add(MissileOpeningReadings.verdict(shipFire, named));
        verdicts.add(t.lay.verdict("turrets-stay-laid-on-a-hull-in-reach", LAID_SHARE));

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("orderTick", t.orderTick);
        MissileOpeningReadings.metrics(shipFire, named, metrics);
        WarshipPointDefence pd = t.sim.warships().pointDefence();
        metrics.put("missilesFired", shipFire.missilesFired());
        metrics.put("missilesShotDown", shipFire.missilesShotDown());
        metrics.put("missilesOnHull", shipFire.missilesOnHull());
        metrics.put("pdEngagements", pd.hullMissileEngagements());
        metrics.put("pdIntercepts", pd.hullMissileIntercepts());
        // 0 still over the battle at the end, 1 escaped, 2 shot down.
        metrics.put("greyhookEnding", escaped ? 1 : greyhookSideDown ? 2 : 0);
        // 0 still fighting, 1 ordered on the clock, 2 crippled, 3 unopposed.
        metrics.put("breakOffReason", duel.breakOff().ordinal());
        metrics.put("decidedSeconds", t.seconds(t.decidedTick));
        metrics.put("concludedSeconds", t.seconds(t.concludedTick));
        metrics.put("breakOffSeconds", t.seconds(duel.breakTick()));
        metrics.put("firstHullHitSeconds", t.firstHitTick < 0 ? -1f : (t.firstHitTick - t.orderTick) / 30f);
        metrics.put("breakOffSecondsFromFirstHit", t.firstHitTick < 0 || duel.breakTick() < 0 ? -1f
                : (duel.breakTick() - t.firstHitTick) / 30f);
        metrics.put("fightSecondsFromFirstHit", t.firstHitTick < 0 ? -1f
                : ((t.decidedTick < 0 ? t.lastTick : t.decidedTick) - t.firstHitTick) / 30f);
        metrics.put("concludedSecondsFromFirstHit", t.firstHitTick < 0 || t.concludedTick < 0 ? -1f
                : (t.concludedTick - t.firstHitTick) / 30f);
        metrics.put("greyhookHullAtBreakOffPercent", t.hullAtBreak);
        metrics.put("greyhookRunCells", Math.round(t.runCells));
        for (int i = 0; i < t.run.size(); i++) {
            int[] r = t.run.get(i);
            String at = "RunAt" + (10 * i) + "s";
            metrics.put("greyhookHull" + at, r[0]);
            for (int k = 1; k < r.length; k++) metrics.put(t.names[k] + "Range" + at, r[k]);
        }
        String[] names = t.names;
        List<Warship> all = duel.hulls();
        for (int i = 0; i < all.size() && i < names.length; i++) {
            Warship s = all.get(i);
            metrics.put(names[i] + "DownSeconds", t.seconds(t.downTick.getOrDefault(s, -1)));
            metrics.put(names[i] + "HullPercent", Math.round(Math.max(0f, s.hullFraction()) * 100f));
            metrics.put(names[i] + "Breaches", s.plating() == null ? -1 : s.plating().breachCount());
            metrics.put(names[i] + "MountsKnockedOut", s.mountsKnockedOut());
            metrics.put(names[i] + "MountsLost", s.mountsLost());
        }
        for (int i = 0; i < t.greyhookHull.size(); i++) {
            metrics.put("greyhookHullAt" + (30 * (i + 1)) + "s", t.greyhookHull.get(i));
        }
        for (int i = 0; i < t.windows.size(); i++) {
            int[] w = t.windows.get(i);
            String at = "At" + (30 * (i + 1)) + "s";
            metrics.put("greyhookRounds" + at, w[0]);
            metrics.put("greyhookFlux" + at, w[1]);
            metrics.put("greyhookTargetRangePercent" + at, w[2]);
            for (int k = 3; k < w.length; k++) metrics.put(t.names[k - 2] + "Hull" + at, w[k]);
        }
        metrics.put("ruskLost", lost(duel.rusk()));
        metrics.put("damageToGreyhookSide", Math.round(t.damageToGreyhookSide));
        metrics.put("damageToRusk", Math.round(t.damageToRusk));
        metrics.put("hullDamageToGreyhook", Math.round(t.hullLost(greyhook)));
        metrics.put("hullDamageToRusk", Math.round(t.hullLost(duel.rusk())));
        metrics.put("shieldDamageToRusk", Math.round(t.shieldTaken(duel.rusk())));
        metrics.put("shieldDamageToGreyhook", Math.round(greyhook.shieldAbsorbedDamage()));
        metrics.put("entryGapBeyondReachCells", Math.round(t.entryGap));
        metrics.put("sightedSeconds", t.seconds(sighted));
        metrics.put("closingSeconds", t.seconds(closing));
        metrics.put("firstHullFireSeconds", t.seconds(fired));
        metrics.put("firstRuskDownSeconds", t.seconds(firstRuskDown));
        metrics.put("sightedToFirstFireSeconds", sighted < 0 || fired < 0 ? -1f : (fired - sighted) / 30f);
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
        for (int i = 0; i < duel.rusk().size(); i++) {
            Warship ship = duel.rusk().get(i);
            String who = t.names[i + 1];
            metrics.put(who + "OnMapSeconds", t.seconds(t.onMapTick.getOrDefault(ship, -1)));
            metrics.put(who + "ClearOfEdgeSeconds", t.seconds(t.clearTick.getOrDefault(ship, -1)));
            metrics.put(who + "Outcome", outcomeCode(duel, ship));
        }
        metrics.put("greyhookOutcome", outcomeCode(duel, greyhook));
        for (Map.Entry<String, List<float[]>> track : t.tracks.entrySet()) {
            for (int i = 0; i < track.getValue().size(); i++) {
                float[] g = track.getValue().get(i);
                if (g[0] < -1e3f) continue;
                metrics.put(track.getKey() + "At" + (30 * (i + 1)) + "sX", Math.round(g[0]));
                metrics.put(track.getKey() + "At" + (30 * (i + 1)) + "sY", Math.round(g[1]));
            }
        }
        metrics.put("greyhookRounds", shots(List.of(greyhook)));
        metrics.put("ruskRounds", shots(duel.rusk()));
        int aimed = 0;
        for (int n : t.greyhookOn) aimed += n;
        for (int i = 0; i + 1 < t.greyhookOn.length; i++) {
            metrics.put("greyhookOn" + duel.pair().get(i).name() + "Percent",
                    aimed == 0 ? 0 : Math.round(100f * t.greyhookOn[i] / aimed));
        }
        // Rounds by weapon, each hull: which gun is doing the work (and which is never in reach).
        for (int h = 0; h < duel.hulls().size(); h++) {
            Warship hull = duel.hulls().get(h);
            String side = t.names[h];
            for (Warship.Mount m : hull.mounts()) {
                if (m.pointDefence()) continue;
                metrics.merge(side + "Rounds_" + m.weapon.id(), m.shotsFired, (a, b) -> a.intValue() + b.intValue());
            }
        }
        var fire = t.sim.warships().shipFire();
        metrics.put("roundsAtHulls", fire.roundsFired());
        metrics.put("hitSharePercent", fire.roundsFired() == 0 ? 0 : Math.round(100f * fire.roundsOnHull() / fire.roundsFired()));
        HullContactReadings.put(metrics, fire);
        t.lay.put(metrics);
        metrics.put("minFriendlyGapCells", Math.round(t.minFriendlyGap * 10f) / 10f);
        metrics.put("friendlyOverlapTicks", t.friendlyOverlapTicks);
        int swaps = 0, vents = 0;
        for (Warship e : duel.rusk()) {
            swaps += e.manoeuvre().relaySwaps();
            vents += e.manoeuvre().relayVents();
        }
        metrics.put("relaySwaps", swaps);
        metrics.put("relayVents", vents);
        metrics.put("greyhookOverloads", greyhook.overloadCount());
        metrics.put("greyhookShieldCollapses", greyhook.shieldCollapses());
        metrics.put("greyhookPeakFluxPercent", Math.round(100f * t.peakFlux.getOrDefault(greyhook, 0f)));
        List<Warship> rusk = duel.rusk();
        for (int i = 0; i < rusk.size(); i++) {
            Warship ship = rusk.get(i);
            String who = t.names[i + 1];
            metrics.put(who + "Overloads", ship.overloadCount());
            metrics.put(who + "RelaySwaps", ship.manoeuvre().relaySwaps());
            metrics.put(who + "RelayVents", ship.manoeuvre().relayVents());
            int[] roles = t.roleTicks.getOrDefault(ship, new int[3]);
            int fighting = roles[0] + roles[1] + roles[2];
            metrics.put(who + "TankPercent", fighting == 0 ? 0 : Math.round(100f * roles[0] / fighting));
            metrics.put(who + "StrikerPercent", fighting == 0 ? 0 : Math.round(100f * roles[1] / fighting));
            metrics.put(who + "ShieldCollapses", ship.shieldCollapses());
            metrics.put(who + "ShieldAbsorbed", Math.round(ship.shieldAbsorbedDamage()));
            metrics.put(who + "PeakFluxPercent", Math.round(100f * t.peakFlux.getOrDefault(ship, 0f)));
            ShieldTrace st = t.shieldTraces.get(ship);
            if (st != null) st.report(who, metrics);
            int[] on = t.targetTicks.getOrDefault(ship, new int[2]);
            int total = on[0] + on[1];
            String[] targets = {"Greyhook", "None"};
            for (int k = 0; k < 2; k++) {
                metrics.put(who + "On" + targets[k] + "Percent", total == 0 ? 0 : Math.round(100f * on[k] / total));
            }
        }
        return new SceneReport(id(), loop, TICKS, verdicts, metrics);
    }

    private static boolean allDown(List<Warship> ships) {
        if (ships.isEmpty()) return false;
        for (Warship s : ships) if (!s.destroyed()) return false;
        return true;
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
        /** The tick the Greyhook was destroyed or gone off the map, or -1. */
        int decidedTick = -1;
        /** The tick her part concluded (gone, or her wreck at rest), or -1. */
        int concludedTick = -1;
        /** The tick her wreck came to rest, or -1; whether it struck the ground first. */
        int wreckRestTick = -1;
        boolean wreckStruck;
        /** Ticks the battle's hold disagreed with her state: held after she concluded, or released before. */
        final List<String> holdErrors = new ArrayList<>();
        /** Her hull, percent, on the tick the run was ordered (-1 before), and how far she flew from there. */
        int hullAtBreak = -1;
        float runCells;
        private float runX = Float.NaN, runY;
        /**
         * Every ten seconds from the order to run: her hull (percent), and the range to each of Dormer's hulls over the
         * battle (cells, -1 for one that is down).
         */
        final List<int[]> run = new ArrayList<>();
        int firstHitTick = -1;
        /** The tick each hull was destroyed. */
        final Map<Warship, Integer> downTick = new LinkedHashMap<>();
        /** The Greyhook's hull, as a percentage, every thirty seconds after the order. */
        final List<Integer> greyhookHull = new ArrayList<>();
        /**
         * Every thirty seconds after the order: the Greyhook's rounds in those thirty seconds, her flux (percent), the
         * range to her hull target over her reach (percent, -1 with none), and each of Dormer's hulls' remaining hull.
         */
        final List<int[]> windows = new ArrayList<>();
        private int windowShots;
        float damageToGreyhookSide;
        float damageToRusk;
        int greyhookShots;
        int ruskInReach;
        float ruskEntryHeadingError;
        /** The duel's hulls in {@link BlackSluiceDuel#hulls()} order, as the metrics name them: greyhook, then Dormer's. */
        final String[] names;
        /** Ticks the Greyhook's hull target was each hull of Dormer's, in {@link BlackSluiceDuel#rusk()} order, or none (last). */
        int[] greyhookOn = new int[0];
        /** Per hull of Dormer's, ticks fighting as the relay's tank, as its striker, and otherwise. */
        final Map<Warship, int[]> roleTicks = new LinkedHashMap<>();
        /**
         * On the tick the first of Dormer's hulls crossed the edge: how far the nearest of Greyhook's hulls lay beyond that
         * hull's reach (negative is inside it), and the reach.
         */
        float entryGap = Float.NaN;
        float entryReach;
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
        /** Per hull of Dormer's, ticks with its hull target the Greyhook, or none. */
        final Map<Warship, int[]> targetTicks = new LinkedHashMap<>();
        /** The fullest each hull's flux pool was, as a share of capacity. */
        final Map<Warship, Float> peakFlux = new LinkedHashMap<>();
        /** Arrivals that did not cross the hull's own edge (or, for Greyhook's ships, start on a pad). */
        final List<String> badArrivals = new ArrayList<>();
        /** Hulls seen leaving or gone: nothing of this duel leaves the map. */
        final List<String> leavers = new ArrayList<>();
        /** Hulls seen in the withdraw posture. */
        final List<String> withdrawals = new ArrayList<>();
        final List<String> foreignCrossings = new ArrayList<>();
        int hullsSeen;
        final Map<Warship, Integer> onMapTick = new LinkedHashMap<>();
        final Map<Warship, Integer> clearTick = new LinkedHashMap<>();
        private final Set<Warship> left = new HashSet<>();
        private final Set<Warship> withdrew = new HashSet<>();
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
        /** Per hull of Dormer's, how its shield met the Greyhook's fire. */
        final Map<Warship, ShieldTrace> shieldTraces = new LinkedHashMap<>();
        /** Where each hull was every thirty seconds after the order, while it was over the battle, by name. */
        final Map<String, List<float[]>> tracks = new LinkedHashMap<>();
        /** How well every hull's turrets stay laid on the hull they fight, from the order on. */
        final MountLayReadings lay = new MountLayReadings();

        Trace(BlackSluiceDuel duel, BattleSimulation sim, int orderTick) {
            this.duel = duel;
            this.orderTick = orderTick;
            this.sim = sim;
            List<BlackSluiceDuel.RuskHull> pair = duel.pair();
            names = new String[pair.size() + 1];
            names[0] = "greyhook";
            for (int i = 0; i < pair.size(); i++) {
                String n = pair.get(i).name().toLowerCase(Locale.ROOT);
                boolean twin = false;
                for (int j = 0; j < pair.size(); j++) twin |= j != i && pair.get(j).name().equals(pair.get(i).name());
                names[i + 1] = twin ? n + (i + 1) : n;
            }
        }

        /** The tick a story beat was published, or -1. */
        int beatTick(String id) {
            for (var e : sim.getStoryBeats()) if (e.id().equals(id)) return e.tick();
            return -1;
        }

        String caption(String loop, int tick) {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "GREYHOOK DUEL / %s / %ds after the order",
                    loop, Math.max(0, tick - orderTick) / 30));
            List<Warship> hulls = duel.hulls();
            for (int i = 0; i < hulls.size(); i++) {
                Warship hull = hulls.get(i);
                String name = i == 0 ? "Greyhook" : duel.pair().get(i - 1).name();
                s.append(" / ").append(name).append(' ').append(hull.destroyed() ? "down"
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

        /** The edge a hull's side arrives across: Dormer's west; Greyhook's side has none, it lifts from its pads. */
        private static MapEdge ownEdge(Warship ship) {
            return ship.faction == Faction.ALLY ? BlackSluiceDuel.RUSK_EDGE : null;
        }

        private static String name(Warship ship) {
            return ship.hullId + "/" + ship.faction;
        }

        /** Arrivals, leavers, edge crossings and clear air to the edges, for every hull of the duel, every tick. */
        private void edges(BattleSimulation sim, int tick, List<Warship> all) {
            WarshipEdgeHelm helm = new WarshipEdgeHelm(WIDTH, HEIGHT);
            for (Warship s : all) {
                float x = s.centerX(), y = s.centerY();
                MapEdge own = ownEdge(s);
                if (seen.add(s)) {
                    hullsSeen++;
                    boolean fromPad = s.faction == Faction.DEFENDER && s.berthed();
                    boolean fromEdge = own != null && s.enteringAcrossEdge() && s.entryEdge() == own
                            && own.beyond(x, y, WIDTH, HEIGHT);
                    if (!fromPad && !fromEdge) {
                        badArrivals.add(String.format(Locale.ROOT, "%s first seen at %.0f,%.0f", name(s), x, y));
                    }
                }
                if (s.destroyed()) continue;
                if (!onMapTick.containsKey(s) && x >= 0f && x <= WIDTH && y >= 0f && y <= HEIGHT) onMapTick.put(s, tick);
                if (s.entryEdge() != null && !s.enteringAcrossEdge() && !clearTick.containsKey(s)) clearTick.put(s, tick);
                // Only the Greyhook leaves, only once Voss has ordered the run, and only across her own edge.
                boolean run = s == duel.greyhook() && duel.breakTick() >= 0 && s.escapingAcrossEdge()
                        && s.exitEdge() == BlackSluiceDuel.GREYHOOK_EDGE;
                if ((s.departing() || s.departed()) && !run && left.add(s)) {
                    leavers.add(String.format(Locale.ROOT, "%s leaving at %.0f,%.0f, %.0fs after the order", name(s), x, y,
                            seconds(tick)));
                }
                if (s.posture() == WarshipPosture.WITHDRAW && withdrew.add(s)) {
                    withdrawals.add(String.format(Locale.ROOT, "%s withdrew at %.0fs: %s", name(s), seconds(tick),
                            s.postureReason()));
                }
                if (s.departed()) continue;
                // Over the map once, a hull never goes past an edge again: arriving across it is the only crossing.
                for (MapEdge e : MapEdge.values()) {
                    if (s.enteringAcrossEdge()) break;
                    if (run && e == BlackSluiceDuel.GREYHOOK_EDGE) continue;
                    if (e.beyond(x, y, WIDTH, HEIGHT) && crossedForeign.add(s)) {
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
            if (duel.ordered()) {
                for (int i = 0; i < all.size() && i < names.length; i++) lay.observe(names[i], all.get(i));
                for (Warship ship : duel.rusk()) {
                    shieldTraces.computeIfAbsent(ship, w -> new ShieldTrace()).observe(ship, greyhook);
                }
            }
            for (Warship s : all) {
                float hp = Math.max(0f, s.hitPoints());
                float sh = s.shieldAbsorbedDamage();
                float dh = lastHp.getOrDefault(s, hp) - hp;
                float ds = sh - lastShield.getOrDefault(s, sh);
                lastHp.put(s, hp);
                lastShield.put(s, sh);
                if (dh + ds <= 0f) continue;
                if (firstHitTick < 0) firstHitTick = tick;
                if (s.faction == Faction.ALLY) damageToRusk += dh + ds;
                else damageToGreyhookSide += dh + ds;
            }
            int shots = 0;
            for (Warship.Mount m : greyhook.mounts()) shots += m.shotsFired;
            if (duel.ordered() && !greyhook.destroyed()) {
                greyhookShots += shots - lastGreyhookShots;
                for (Warship ship : duel.rusk()) {
                    if (!ship.destroyed() && !ship.departed()
                            && greyhook.distanceTo(ship.centerX(), ship.centerY()) <= WarshipDuelHelm.reach(greyhook)) {
                        ruskInReach++;
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
                List<Warship> ruskNow = duel.rusk();
                if (greyhookOn.length != ruskNow.size() + 1) greyhookOn = new int[ruskNow.size() + 1];
                if (!greyhook.destroyed() && !ruskNow.isEmpty()) {
                    Warship aim = greyhook.hullTarget();
                    int at = aim == null ? -1 : ruskNow.indexOf(aim);
                    greyhookOn[at < 0 ? ruskNow.size() : at]++;
                }
                for (Warship ship : duel.rusk()) {
                    if (ship.destroyed() || ship.departed() || ship.descending()) continue;
                    targetTicks.computeIfAbsent(ship, w -> new int[2])[ship.hullTarget() == greyhook ? 0 : 1]++;
                    if (ship.hullTarget() == greyhook && !greyhook.destroyed()) {
                        WarshipManoeuvre.Role role = ship.manoeuvre().role();
                        roleTicks.computeIfAbsent(ship, w -> new int[3])[role == WarshipManoeuvre.Role.TANK ? 0
                                : role == WarshipManoeuvre.Role.STRIKER ? 1 : 2]++;
                    }
                }
                for (Warship s : all) {
                    if (!s.destroyed()) peakFlux.merge(s, s.fluxFraction(), Math::max);
                }
            }
            lastTick = tick;
            if (tick == orderTick + 1) {
                for (Warship ship : duel.rusk()) {
                    float off = Math.abs(((ship.facingDegrees - BlackSluiceDuel.RUSK_HEADING) % 360f + 540f) % 360f - 180f);
                    ruskEntryHeadingError = Math.max(ruskEntryHeadingError, off);
                }
            }
            edges(sim, tick, all);
            if (Float.isNaN(entryGap)) {
                for (Warship ship : duel.rusk()) {
                    float x = ship.centerX();
                    if (ship.destroyed() || x < 0f || x > WIDTH) continue;
                    float near = Float.MAX_VALUE;
                    for (Warship s : all) {
                        if (s.faction != Faction.ALLY && !s.destroyed()) near = Math.min(near, s.distanceTo(ship.centerX(), ship.centerY()));
                    }
                    entryReach = WarshipDuelHelm.reach(ship);
                    entryGap = near - entryReach;
                    break;
                }
            }
            if (duel.ordered() && tick > orderTick && (tick - orderTick) % 900 == 0) {
                greyhookHull.add(Math.round(Math.max(0f, greyhook.hullFraction()) * 100f));
                List<Warship> lent = duel.rusk();
                int[] w = new int[3 + lent.size()];
                w[0] = shots - windowShots;
                windowShots = shots;
                w[1] = Math.round(100f * greyhook.fluxFraction());
                Warship aim = greyhook.hullTarget();
                w[2] = aim == null || greyhook.destroyed() ? -1 : Math.round(100f * greyhook.distanceTo(aim.centerX(),
                        aim.centerY()) / Math.max(1f, WarshipDuelHelm.reach(greyhook)));
                for (int i = 0; i < lent.size(); i++) w[3 + i] = Math.round(100f * Math.max(0f, lent.get(i).hullFraction()));
                windows.add(w);
                int slot = (tick - orderTick) / 900;
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
            for (Warship s : all) if (s.destroyed()) downTick.putIfAbsent(s, tick);
            if (duel.ordered()) {
                boolean done = sim.warships().concluded(greyhook);
                if (done && concludedTick < 0) concludedTick = tick;
                boolean held = sim.warships().awaitingConclusion();
                if (held == done && holdErrors.size() < 5) {
                    holdErrors.add(String.format(Locale.ROOT, "%s at %.1fs", held ? "held after she concluded"
                            : "released before she concluded", seconds(tick)));
                }
                for (var wreck : sim.warships().wrecks()) {
                    if (wreck.ship != greyhook) continue;
                    wreckStruck |= !Float.isNaN(wreck.struckX());
                    if (wreckRestTick < 0 && wreck.phase() == WarshipWreck.Phase.DOWN) {
                        wreckRestTick = tick;
                    }
                }
                if (duel.breakTick() >= 0 && hullAtBreak < 0) hullAtBreak = Math.round(100f * Math.max(0f, greyhook.hullFraction()));
                if (duel.breakTick() >= 0 && (tick - duel.breakTick()) % 300 == 0 && !greyhook.destroyed()
                        && !greyhook.departed()) {
                    List<Warship> lent = duel.rusk();
                    int[] r = new int[1 + lent.size()];
                    r[0] = Math.round(100f * Math.max(0f, greyhook.hullFraction()));
                    for (int i = 0; i < lent.size(); i++) {
                        Warship ship = lent.get(i);
                        r[1 + i] = ship.destroyed() ? -1 : Math.round(greyhook.distanceTo(ship.centerX(), ship.centerY()));
                    }
                    run.add(r);
                }
                if (duel.breakTick() >= 0 && !greyhook.destroyed() && !greyhook.departed()) {
                    if (!Float.isNaN(runX)) runCells += (float) Math.hypot(greyhook.centerX() - runX, greyhook.centerY() - runY);
                    runX = greyhook.centerX();
                    runY = greyhook.centerY();
                }
            }
            for (long air : sim.getAirEntityIds()) if (sim.world().airFaction(air) == Faction.DEFENDER) defenderAircraft++;
            if (decidedTick < 0 && duel.decidedTick() >= 0) decidedTick = duel.decidedTick();
        }
    }

    /**
     * How one shielded hull's shield met its attacker's fire, tick by tick: how long it was up, overall and under fire,
     * how the damage it took split between the shield, the hull with the shield down (or still rising) and the hull
     * with the shield up but the attacker outside the arc, the attacker's bearing off the shield's centre line when a
     * round reached the hull, and what dropped the shield each time it went down. A tick's damage is classed by the
     * shield's state that tick; the attacker's bearing is taken from its hull centre.
     */
    static final class ShieldTrace {
        /** Ticks counted as under fire after the last damage taken. */
        static final int UNDER_FIRE_TICKS = 30;
        int ticks, upTicks, absorbingTicks, underFireTicks, underFireUpTicks, attackerInArcTicks, inArcUpTicks;
        int inArcUnderFireTicks, inArcUnderFireUpTicks;
        float blocked, hullShieldDown, hullShieldRising, hullOutsideArc;
        int blockedTicks, hullDownTicks, hullRisingTicks, hullOutsideTicks;
        /** Hull-damage ticks by the attacker's bearing off the shield's centre line, 15 degrees a bin. */
        final int[] hullBearing = new int[12];
        /** Shield-down-under-fire ticks by why: overload, flux lockout, attacker outside the arc, none of those. */
        int downOverload, downLockout, downOutsideArc, downNoTarget, downOther;
        /** Drops of a raised shield by why. */
        int dropOverload, dropLockout, dropQuiet;
        float worstOffArcUnderFire;
        float sumOffUnderFire;
        int offSamples;
        private float lastHp = Float.NaN, lastAbsorbed = Float.NaN;
        private int lastShieldHits = -1, lastHullHits = -1;
        /** Rounds the shield took, and rounds that reached the hull with the shield down, rising, or up with the attacker outside the arc. */
        int roundsBlocked, roundsShieldDown, roundsShieldRising, roundsOutsideArc;
        private boolean wasUp;
        private int lastDamageTick = -100000, tick;
        float capacity, dissipation, arc;
        /** Crossings of the shield's drop level by what pushed the pool over it that tick. */
        int crossBlaster, crossGun, crossHit, crossOther;
        private float lastFlux, lastAbsorbedFlux;
        private int lastShots, lastBlasterShots;

        void observe(Warship ship, Warship attacker) {
            tick++;
            if (ship.destroyed() || ship.departed() || ship.descending() || attacker.destroyed()) return;
            capacity = Math.max(capacity, ship.fluxCapacity());
            dissipation = Math.max(dissipation, ship.fluxDissipation());
            arc = ship.shieldArcDegrees();
            ticks++;
            int shots = 0;
            for (Warship.Mount m : ship.mounts()) {
                if (m.pointDefence()) continue;
                shots += m.shotsFired;
            }
            int blasterNow = 0;
            for (Warship.Mount m : ship.mounts()) if (m.weapon.id().equals("amblaster")) blasterNow += m.shotsFired;
            float absorbedFlux = ship.shieldAbsorbedFlux();
            float fluxNow = ship.fluxFraction();
            if (lastFlux < 0.9f && fluxNow >= 0.9f) {
                if (blasterNow > lastBlasterShots) crossBlaster++;
                else if (shots > lastShots) crossGun++;
                else if (absorbedFlux > lastAbsorbedFlux) crossHit++;
                else crossOther++;
            }
            lastFlux = fluxNow;
            lastShots = shots;
            lastBlasterShots = blasterNow;
            lastAbsorbedFlux = absorbedFlux;
            float hp = Math.max(0f, ship.hitPoints());
            float ab = ship.shieldAbsorbedDamage();
            float dh = Float.isNaN(lastHp) ? 0f : lastHp - hp;
            float ds = Float.isNaN(lastAbsorbed) ? 0f : ab - lastAbsorbed;
            lastHp = hp;
            lastAbsorbed = ab;
            int sh = ship.shieldHits(), hh = ship.hullHits();
            int dsh = lastShieldHits < 0 ? 0 : sh - lastShieldHits;
            int dhh = lastHullHits < 0 ? 0 : hh - lastHullHits;
            lastShieldHits = sh;
            lastHullHits = hh;
            if (dh > 0f || ds > 0f || dsh > 0 || dhh > 0) lastDamageTick = tick;
            boolean underFire = tick - lastDamageTick <= UNDER_FIRE_TICKS;
            boolean up = ship.shieldUp();
            boolean absorbing = ship.shieldAbsorbing();
            float flux = ship.fluxFraction();
            boolean lockedOut = ship.shieldLockedOut();
            float bearing = AirBody.facingToward(attacker.centerX() - ship.shieldCentreX(),
                    attacker.centerY() - ship.shieldCentreY());
            float off = Math.abs(((bearing - ship.shieldFacingDegrees()) % 360f + 540f) % 360f - 180f);
            boolean inArc = off <= ship.shieldArcDegrees() * 0.5f;
            if (up) upTicks++;
            if (absorbing) absorbingTicks++;
            if (inArc) {
                attackerInArcTicks++;
                if (up) inArcUpTicks++;
            }
            if (underFire) {
                underFireTicks++;
                if (up) underFireUpTicks++;
                if (inArc) {
                    inArcUnderFireTicks++;
                    if (up) inArcUnderFireUpTicks++;
                }
                worstOffArcUnderFire = Math.max(worstOffArcUnderFire, off);
                sumOffUnderFire += off;
                offSamples++;
                if (!up) {
                    if (ship.overloaded()) downOverload++;
                    else if (lockedOut) downLockout++;
                    else if (!inArc) downOutsideArc++;
                    else if (ship.hullTarget() == null) downNoTarget++;
                    else downOther++;
                }
            }
            roundsBlocked += dsh;
            if (dhh > 0) {
                if (!up) roundsShieldDown += dhh;
                else if (!absorbing) roundsShieldRising += dhh;
                else roundsOutsideArc += dhh;
            }
            if (ds > 0f) {
                blocked += ds;
                blockedTicks++;
            }
            if (dh > 0f) {
                hullBearing[Math.min(11, (int) (off / 15f))]++;
                if (!up) {
                    hullShieldDown += dh;
                    hullDownTicks++;
                } else if (!absorbing) {
                    hullShieldRising += dh;
                    hullRisingTicks++;
                } else {
                    hullOutsideArc += dh;
                    hullOutsideTicks++;
                }
            }
            if (wasUp && !up) {
                if (ship.overloaded()) dropOverload++;
                else if (lockedOut) dropLockout++;
                else dropQuiet++;
            }
            wasUp = up;
        }

        private static int pct(int a, int b) {
            return b == 0 ? -1 : Math.round(100f * a / b);
        }

        void report(String who, Map<String, Number> m) {
            float hull = hullShieldDown + hullShieldRising + hullOutsideArc;
            float all = blocked + hull;
            m.put(who + "ShieldUpPercent", pct(upTicks, ticks));
            m.put(who + "ShieldUpUnderFirePercent", pct(underFireUpTicks, underFireTicks));
            m.put(who + "AttackerInArcPercent", pct(attackerInArcTicks, ticks));
            m.put(who + "AttackerInArcUnderFirePercent", pct(inArcUnderFireTicks, underFireTicks));
            m.put(who + "ShieldUpWithAttackerInArcUnderFirePercent", pct(inArcUnderFireUpTicks, inArcUnderFireTicks));
            int rounds = roundsBlocked + roundsShieldDown + roundsShieldRising + roundsOutsideArc;
            m.put(who + "RoundBlockSharePercent", pct(roundsBlocked, rounds));
            m.put(who + "RoundsBlocked", roundsBlocked);
            m.put(who + "RoundsHullShieldDown", roundsShieldDown);
            m.put(who + "RoundsHullShieldRising", roundsShieldRising);
            m.put(who + "RoundsHullShieldUpOutsideArc", roundsOutsideArc);
            m.put(who + "BlockSharePercent", all <= 0f ? -1 : Math.round(100f * blocked / all));
            m.put(who + "DamageBlocked", Math.round(blocked));
            m.put(who + "DamageHullShieldDown", Math.round(hullShieldDown));
            m.put(who + "DamageHullShieldRising", Math.round(hullShieldRising));
            m.put(who + "DamageHullShieldUpOutsideArc", Math.round(hullOutsideArc));
            m.put(who + "TicksBlocked", blockedTicks);
            m.put(who + "TicksHullShieldDown", hullDownTicks);
            m.put(who + "TicksHullShieldRising", hullRisingTicks);
            m.put(who + "TicksHullOutsideArc", hullOutsideTicks);
            for (int i = 0; i < hullBearing.length; i++) {
                if (hullBearing[i] > 0) m.put(who + "HullHitTicksOffArc" + (15 * i) + "to" + (15 * i + 15), hullBearing[i]);
            }
            m.put(who + "DownUnderFireOverloadTicks", downOverload);
            m.put(who + "DownUnderFireLockoutTicks", downLockout);
            m.put(who + "DownUnderFireAttackerOutsideArcTicks", downOutsideArc);
            m.put(who + "DownUnderFireNoHullTargetTicks", downNoTarget);
            m.put(who + "DownUnderFireOtherTicks", downOther);
            m.put(who + "DropsOverload", dropOverload);
            m.put(who + "DropsLockout", dropLockout);
            m.put(who + "DropsQuiet", dropQuiet);
            m.put(who + "MeanAttackerOffCentreUnderFireDeg", offSamples == 0 ? -1 : Math.round(sumOffUnderFire / offSamples));
            m.put(who + "WorstAttackerOffCentreUnderFireDeg", Math.round(worstOffArcUnderFire));
            m.put(who + "DropLevelCrossedByBlaster", crossBlaster);
            m.put(who + "DropLevelCrossedByOtherGun", crossGun);
            m.put(who + "DropLevelCrossedByAbsorbedHit", crossHit);
            m.put(who + "DropLevelCrossedByUpkeep", crossOther);
            m.put(who + "ShieldArcDeg", Math.round(arc));
            m.put(who + "FluxCapacity", Math.round(capacity));
            m.put(who + "FluxDissipation", Math.round(dissipation));
        }
    }
}
