package com.dillon.starsectormarines.battle.command.reinforcement;

import com.dillon.starsectormarines.battle.command.BattleResources;
import com.dillon.starsectormarines.battle.command.ResourceType;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.BiomeKind;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Staged bulge counterattack — the defender Commander-tier set piece from
 * {@code roadmap/conquest/stories/biome-counterattack.md}. Where {@link
 * FrontLineReinforcementTrigger} spreads squads thin to hold the contested
 * front, this masses a burst of reinforcement tickets at one point to push
 * back <em>into</em> a slice the marines have already conceded — the
 * deliberate exception to the frontline eligibility filter.
 *
 * <p>A <b>System</b> (processor): it owns only the phase/timer state machine
 * — the cadence {@link #accumulator}, the current {@link #phase}, its
 * countdown, and the muster snapshot ({@link #bulgeSlice}, the manned
 * targets it's pushing on, and the dispatch rotation cursor). It reads
 * {@link RecaptureTargetService} for frontline state, posts prepaid
 * requests to {@link ReinforcementService}, and debits/refunds the earmark
 * on {@link BattleResources} directly — there is no {@code *Service}
 * counterpart because there's no state here worth splitting out; the whole
 * point is a transient state machine. Named {@code *System} under the
 * Service(data-owner)/System(processor) convention — see
 * {@code roadmap/ecs-migration/stories/entity-field-migration.md}.
 *
 * <h2>Phases</h2>
 * <pre>
 * IDLE --(muster)--&gt; TELEGRAPH --(timer)--&gt; ASSAULT --(one tick)--&gt; RESOLVE --(success/timeout)--&gt; COOLDOWN --(timer)--&gt; IDLE
 *                        |                        |
 *                        +--(slice re-contested)--+--&gt; COOLDOWN (short; refund, not counted against the cap)
 * </pre>
 * <ul>
 *   <li><b>IDLE.</b> Polls muster conditions each cadence tick: budget
 *       surplus, a stable/overflow front, a reclaimable conceded slice, and
 *       at least one registered means able to deliver to it. On muster,
 *       earmarks the ticket burst all-or-nothing and snapshots the target
 *       slice's manned nodes.</li>
 *   <li><b>TELEGRAPH.</b> The comms-hook window ({@link #TELEGRAPH_SEC}) —
 *       the battle HUD reads the exposed phase, timer, target, and outcome
 *       state to narrate the buildup and mark the threatened district.
 *       Re-validates each tick that the slice is still conceded;
 *       a natural re-contest aborts with a full refund.</li>
 *   <li><b>ASSAULT.</b> One cadence tick: re-checks the slice is still
 *       conceded (a re-contest landing in the one-tick gap since the last
 *       TELEGRAPH check aborts the same as a telegraph-phase re-contest),
 *       then posts up to {@link #BURST_TICKETS} prepaid requests,
 *       round-robining the objective over the snapshot targets and skipping
 *       any that were re-manned since the snapshot was taken. Marks each
 *       dispatched target so the frontline trigger doesn't double-book it
 *       once the slice flips contested.</li>
 *   <li><b>RESOLVE.</b> Waits up to {@link #RESOLVE_WINDOW_SEC} for {@link
 *       RecaptureTargetService#isContested} to read true on the bulge slice
 *       for {@link #SUCCESS_HOLD_TICKS} consecutive ticks — the debounced
 *       frontline filter re-including it, <em>sustained</em>, is the success
 *       signal (no wave-squad bookkeeping). A wave that gets wiped shortly
 *       after arriving drops the slice back to conceded before the hold
 *       completes, correctly reading as failure rather than an instantly-
 *       latched success. Timeout is failure; the earmark stays burned
 *       either way.</li>
 *   <li><b>COOLDOWN.</b> Spaces bulges out. A resolve (success or timeout)
 *       gets the full {@link #COOLDOWN_SEC}; a TELEGRAPH/ASSAULT abort
 *       (natural re-contest) gets the shorter {@link #ABORT_COOLDOWN_SEC} —
 *       enough to stop muster/abort churn at a flickering slice boundary
 *       without charging the full post-resolve spacing for a bet that was
 *       never placed.</li>
 * </ul>
 */
public final class CounterattackSystem {

    private static final Logger LOG = Global.getLogger(CounterattackSystem.class);

    private static final float TICK_PERIOD = ReinforcementService.REINFORCEMENT_TICK_PERIOD;

    /**
     * Nearest-to-defender-rear slice order the muster scan walks looking for
     * a reclaimable (conceded, but once-manned) objective. Mirrors {@link
     * FrontLineReinforcementTrigger}'s dispatch order minus the legacy
     * OUTSKIRTS catch-all — a bulge never targets it.
     */
    private static final BiomeKind[] SLICE_ORDER = {
            BiomeKind.FORTRESS_DISTRICT, BiomeKind.CITY, BiomeKind.PORT, BiomeKind.BEACH
    };

    /**
     * Squads in the massed wave. The lump earmark at muster is {@code
     * BURST_TICKETS * resources.reinforcementCost()} — a single all-or-
     * nothing debit, distinct from the steady per-dispatch cost each squad
     * would otherwise pay. Tune in playtest per the design doc's slice 5.
     */
    public static final int BURST_TICKETS = 4;

    /**
     * Tickets of headroom the muster budget check demands <em>above</em> the
     * burst itself — muster requires {@code balance >= (BURST_TICKETS +
     * SURPLUS_FLOOR_TICKETS) * cost}. Keeps the bulge from ever emptying the
     * steady-reinforcement reserve the front-line trigger depends on; the
     * defender only gambles surplus, never the last of its means to hold the
     * line.
     */
    public static final int SURPLUS_FLOOR_TICKETS = 2;

    /**
     * Sim-seconds the telegraph phase holds before the wave launches — the
     * player's reaction window to dig in, fall back, or pre-empt. The
     * comms warning fires at phase entry. Tune for "long enough to react,
     * short enough to feel threatening" per the design doc's open question.
     */
    public static final float TELEGRAPH_SEC = 20f;

    /**
     * Sim-seconds the resolve phase waits for the bulge slice to flip back
     * to contested before declaring the wave failed. Generous relative to
     * {@link #TELEGRAPH_SEC} — the wave has to travel, fight, and hold long
     * enough for the frontline debounce to notice.
     */
    public static final float RESOLVE_WINDOW_SEC = 120f;

    /**
     * Consecutive RESOLVE ticks the bulge slice must read contested,
     * back-to-back, before the wave counts as having genuinely
     * re-established presence — not merely transited through the slice on
     * its way to the objective. {@link RecaptureTargetSystem}'s own
     * presence debounce ({@link RecaptureTargetSystem#PRESENCE_DEBOUNCE_TICKS})
     * only guards against a single stray unit flickering the read; it
     * flips {@code isContested} true the moment the wave's own squads are
     * physically inside the slice, which can be well before they've beaten
     * back whatever marine presence is holding it. Requiring a longer,
     * unbroken hold gives combat time to actually resolve — a wave wiped
     * shortly after arriving drops the slice back to conceded before the
     * streak completes, so it reads as a failure instead of an instantly
     * latched success. Comfortably below {@link #RESOLVE_WINDOW_SEC} so a
     * wave that really does hold still has time to be declared successful.
     */
    public static final int SUCCESS_HOLD_TICKS = 10;

    /**
     * Sim-seconds of cooldown after a bulge resolves (either way) before the
     * next one may muster. Spaces bulges out so each reads as a set piece,
     * not a background drain on the reinforcement economy.
     */
    public static final float COOLDOWN_SEC = 240f;

    /**
     * Sim-seconds of cooldown after a TELEGRAPH or ASSAULT-launch abort (the
     * slice re-contests naturally before the wave lands) before the next
     * muster may fire. Doesn't count against {@link #MAX_BULGES_PER_BATTLE}
     * — it only spaces muster attempts apart so a slice flickering at a
     * biome-band boundary can't churn muster&rarr;telegraph&rarr;abort every
     * cadence tick, which would defeat {@link #COOLDOWN_SEC}'s "each bulge
     * is an event" intent. Deliberately shorter than the full post-resolve
     * cooldown: an abort spends nothing but the telegraph's reaction
     * window, so it doesn't need the full economy-reset spacing a burned
     * wave does.
     */
    public static final float ABORT_COOLDOWN_SEC = 30f;

    /**
     * Hard cap on bulges per battle. A failed telegraph abort (the slice
     * re-contests naturally before the wave launches) does <em>not</em> count
     * against this — only a wave that actually launched and resolved does.
     */
    public static final int MAX_BULGES_PER_BATTLE = 2;

    /** Phase of the bulge state machine. Public so tests and the HUD/comms presenter can read it directly. */
    public enum Phase { IDLE, TELEGRAPH, ASSAULT, RESOLVE, COOLDOWN }

    /** Player-facing disposition of the most recent bulge. Retained through cooldown so the HUD cannot miss the resolving tick. */
    public enum Resolution { NONE, SUCCESS, FAILURE, ABORTED }

    private final RecaptureTargetService targets;
    private final ReinforcementService reinforcement;
    private final BattleResources resources;
    private final TraversalAxis axis;

    private float accumulator = 0f;
    private Phase phase = Phase.IDLE;

    /** Countdown for the current phase (TELEGRAPH/RESOLVE/COOLDOWN); unused in IDLE/ASSAULT. */
    private float phaseTimer = 0f;

    /** The slice the current (or most recently mustered) bulge targets. Null in IDLE with no bulge staged. */
    private BiomeKind bulgeSlice;

    /** Snapshot of the bulge slice's manned targets, taken at muster — the ASSAULT round-robin dispatches over this, not a live re-query. */
    private List<RecaptureTarget> bulgeTargets;

    /** Round-robin cursor into {@link #bulgeTargets} for the ASSAULT dispatch. */
    private int rotationIndex = 0;

    /** Bulges that have launched and resolved (success or failure) so far this battle — gates {@link #MAX_BULGES_PER_BATTLE}. */
    private int bulgesFired = 0;

    /** Consecutive RESOLVE ticks the bulge slice has read contested, unbroken. Reset to 0 on entering RESOLVE and whenever a tick reads not-contested. See {@link #SUCCESS_HOLD_TICKS}. */
    private int holdStreak = 0;

    /** Outcome of the current/most recent bulge. Reset when a new muster starts and after cooldown returns to idle. */
    private Resolution resolution = Resolution.NONE;

    /** Representative world cell for the threatened-slice signpost: centroid of the muster's target nodes. */
    private float bulgeCenterX = Float.NaN;
    private float bulgeCenterY = Float.NaN;

    public CounterattackSystem(RecaptureTargetService targets, ReinforcementService reinforcement,
                                BattleResources resources, TraversalAxis axis) {
        this.targets = targets;
        this.reinforcement = reinforcement;
        this.resources = resources;
        this.axis = axis;
    }

    /** Current phase. Exposed for tests and the battle comms presenter. */
    public Phase getPhase() { return phase; }

    /** The slice the current or most recent bulge targets, or {@code null} when no bulge has ever mustered / the last one fully reset. */
    public BiomeKind getBulgeSlice() { return bulgeSlice; }

    /** Countdown for TELEGRAPH/RESOLVE/COOLDOWN, in simulation seconds. */
    public float getPhaseTimeRemaining() { return Math.max(0f, phaseTimer); }

    /** Resolution retained through cooldown; {@link Resolution#NONE} while no bulge has resolved. */
    public Resolution getResolution() { return resolution; }

    /** Representative X cell for the current threatened slice, or NaN while no bulge snapshot exists. */
    public float getBulgeCenterX() { return bulgeCenterX; }

    /** Representative Y cell for the current threatened slice, or NaN while no bulge snapshot exists. */
    public float getBulgeCenterY() { return bulgeCenterY; }

    /** Slow-tick: accumulate {@code dt}, then on cadence advance whichever phase is active. */
    public void tick(float dt, BattleView sim) {
        accumulator += dt;
        if (accumulator < TICK_PERIOD) return;
        accumulator -= TICK_PERIOD;

        switch (phase) {
            case IDLE:
                tryMuster(sim);
                break;
            case TELEGRAPH:
                tickTelegraph();
                break;
            case ASSAULT:
                tickAssault(sim);
                break;
            case RESOLVE:
                tickResolve();
                break;
            case COOLDOWN:
                tickCooldown();
                break;
        }
    }

    /**
     * IDLE -&gt; TELEGRAPH. Musters only when the front is stable/overflow
     * (no eligible frontline targets, but at least one slice still
     * contested — otherwise there's nothing to stage a wave from), a
     * reclaimable conceded slice exists with at least one means able to
     * deliver to it, the cap isn't hit, and the burst earmark clears both
     * the surplus-floor check and the all-or-nothing lump debit.
     */
    private void tryMuster(BattleView sim) {
        if (bulgesFired >= MAX_BULGES_PER_BATTLE) return;
        if (!targets.eligibleTargets().isEmpty()) return;

        boolean anyContested = false;
        for (BiomeKind b : BiomeKind.values()) {
            if (targets.isContested(b)) {
                anyContested = true;
                break;
            }
        }
        if (!anyContested) return;

        BiomeKind slice = null;
        List<RecaptureTarget> manned = null;
        for (BiomeKind candidate : SLICE_ORDER) {
            if (targets.isContested(candidate)) continue;
            List<RecaptureTarget> mannedInSlice = mannedTargets(candidate);
            if (!mannedInSlice.isEmpty()) {
                slice = candidate;
                manned = mannedInSlice;
                break;
            }
        }
        if (slice == null) return;
        if (!anyMeansCanDeliver(sim, manned)) return;

        float cost = resources.reinforcementCost();
        float floor = (BURST_TICKETS + SURPLUS_FLOOR_TICKETS) * cost;
        if (resources.getBalance(Faction.DEFENDER, ResourceType.REINFORCEMENT) < floor) return;
        float lump = BURST_TICKETS * cost;
        if (!resources.tryConsume(Faction.DEFENDER, ResourceType.REINFORCEMENT, lump)) return;

        bulgeSlice = slice;
        bulgeTargets = manned;
        updateBulgeCenter(manned);
        rotationIndex = 0;
        resolution = Resolution.NONE;
        phase = Phase.TELEGRAPH;
        phaseTimer = TELEGRAPH_SEC;
        LOG.info("counterattack: mustering bulge against " + slice + " — "
                + BURST_TICKETS + " tickets earmarked, telegraph in " + TELEGRAPH_SEC + "s");
    }

    /**
     * Probes every registered {@link ReinforcementMeans} against a
     * representative request for {@code candidates} (the first target's
     * rally/objective — means feasibility is about supply-compound
     * survival and map connectivity, not the specific node) so a muster
     * doesn't earmark a burst that's deterministically undeliverable — every
     * supply compound a bulge could route through already marine-held.
     * A single request no means can fulfill is still an accepted bet-the-
     * reserve-and-lose outcome ({@link ReinforcementSystem#dispatch}); this
     * only screens out the case where the whole wave is knowable-dead on
     * arrival at muster time.
     */
    private boolean anyMeansCanDeliver(BattleView sim, List<RecaptureTarget> candidates) {
        RecaptureTarget probeTarget = candidates.get(0);
        int[] rally = FrontLineReinforcementTrigger.rallyRearShift(
                probeTarget.node.anchorX, probeTarget.node.anchorY, axis, sim.getGrid());
        ReinforcementRequest probe = new ReinforcementRequest(
                Faction.DEFENDER,
                ReinforcementRequest.Reason.COUNTERATTACK,
                ReinforcementRequest.Strength.SMALL,
                rally[0], rally[1],
                probeTarget.objectiveX(), probeTarget.objectiveY(),
                true);
        for (ReinforcementMeans m : reinforcement.means()) {
            if (m.canFulfill(sim, probe)) return true;
        }
        return false;
    }

    /** Every target in {@code slice} that has ever been manned — the "had a garrison, then lost it" pool the bulge reclaims, regardless of its current open/dispatched state. */
    private List<RecaptureTarget> mannedTargets(BiomeKind slice) {
        List<RecaptureTarget> out = new ArrayList<>();
        for (RecaptureTarget t : targets.targetsInSlice(slice)) {
            if (t.manned) out.add(t);
        }
        return out;
    }

    /**
     * TELEGRAPH -&gt; ASSAULT on timer expiry, or -&gt; COOLDOWN (short,
     * refunded, not counted against the cap) if the defenders re-establish
     * the slice naturally before the wave launches.
     */
    private void tickTelegraph() {
        if (targets.isContested(bulgeSlice)) {
            abortAndRefund("defenders re-established the slice naturally");
            return;
        }
        phaseTimer -= TICK_PERIOD;
        if (phaseTimer <= 0f) {
            phase = Phase.ASSAULT;
        }
    }

    /**
     * ASSAULT -&gt; RESOLVE (single tick), or -&gt; COOLDOWN (short, refunded,
     * not counted against the cap) if the slice re-contests naturally in the
     * one-tick gap between the last TELEGRAPH check and this tick.
     *
     * <p>Otherwise posts up to {@link #BURST_TICKETS} prepaid requests, one
     * per snapshot target in round-robin order (wraps if the slice has
     * fewer manned targets than the burst size), skipping any target that's
     * been re-manned ({@link RecaptureTarget#isOpen()} false) since the
     * muster snapshot was taken — e.g. by an in-flight frontline
     * reinforcement that was already en route when the slice conceded. Each
     * posted target is marked {@link RecaptureTargetService#markDispatched
     * dispatched} so the frontline trigger doesn't double-book it once the
     * slice flips contested and the targets re-enter its eligible set.
     */
    private void tickAssault(BattleView sim) {
        if (targets.isContested(bulgeSlice)) {
            abortAndRefund("defenders re-established the slice naturally before the wave launched");
            return;
        }
        int posted = 0;
        int attempts = 0;
        int attemptCap = bulgeTargets.size() * BURST_TICKETS;
        while (posted < BURST_TICKETS && attempts < attemptCap) {
            RecaptureTarget target = bulgeTargets.get(rotationIndex % bulgeTargets.size());
            rotationIndex++;
            attempts++;
            if (!target.isOpen()) continue;
            int[] rally = FrontLineReinforcementTrigger.rallyRearShift(
                    target.node.anchorX, target.node.anchorY, axis, sim.getGrid());
            reinforcement.post(new ReinforcementRequest(
                    Faction.DEFENDER,
                    ReinforcementRequest.Reason.COUNTERATTACK,
                    ReinforcementRequest.Strength.SMALL,
                    rally[0], rally[1],
                    target.objectiveX(), target.objectiveY(),
                    true));
            targets.markDispatched(target);
            posted++;
        }
        if (posted < BURST_TICKETS) {
            // Tickets that never became a request aren't part of the bet —
            // only posted (delivery-gambled) requests burn their share of the
            // earmark. Refund the remainder.
            float unspent = (BURST_TICKETS - posted) * resources.reinforcementCost();
            resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT, unspent);
        }
        LOG.info("counterattack: bulge wave of " + posted + " squads dispatched against " + bulgeSlice
                + (posted < BURST_TICKETS ? " (" + (BURST_TICKETS - posted)
                        + " unposted tickets refunded — snapshot targets already re-manned)" : ""));
        holdStreak = 0;
        phase = Phase.RESOLVE;
        phaseTimer = RESOLVE_WINDOW_SEC;
    }

    /**
     * RESOLVE -&gt; COOLDOWN on success (the bulge slice reads contested for
     * {@link #SUCCESS_HOLD_TICKS} consecutive ticks — no wave-squad
     * bookkeeping, the sustained frontline filter re-inclusion <em>is</em>
     * the signal) or on {@link #RESOLVE_WINDOW_SEC} timeout (failure — the
     * earmark stays burned). Either way counts against
     * {@link #MAX_BULGES_PER_BATTLE} and gets the full {@link #COOLDOWN_SEC}.
     */
    private void tickResolve() {
        if (targets.isContested(bulgeSlice)) {
            holdStreak++;
        } else {
            holdStreak = 0;
        }
        boolean success = holdStreak >= SUCCESS_HOLD_TICKS;
        phaseTimer -= TICK_PERIOD;
        if (success || phaseTimer <= 0f) {
            bulgesFired++;
            if (success) {
                resolution = Resolution.SUCCESS;
                LOG.info("counterattack: bulge against " + bulgeSlice
                        + " succeeded — defenders re-established presence, front shifts back");
            } else {
                resolution = Resolution.FAILURE;
                LOG.info("counterattack: bulge against " + bulgeSlice
                        + " failed — wave wiped, earmark burned, front holds");
            }
            holdStreak = 0;
            phase = Phase.COOLDOWN;
            phaseTimer = COOLDOWN_SEC;
        }
    }

    /** COOLDOWN -&gt; IDLE on timer expiry. */
    private void tickCooldown() {
        phaseTimer -= TICK_PERIOD;
        if (phaseTimer <= 0f) {
            resetToIdle();
        }
    }

    /**
     * TELEGRAPH/ASSAULT -&gt; COOLDOWN. Refunds the earmark in full and
     * spaces the next muster attempt by {@link #ABORT_COOLDOWN_SEC} — short
     * of the full {@link #COOLDOWN_SEC}, since this bulge never actually
     * launched, but long enough that a slice flickering at a biome-band
     * boundary can't muster/abort every cadence tick. Does not touch
     * {@link #bulgesFired} — an abort never counts against
     * {@link #MAX_BULGES_PER_BATTLE}.
     */
    private void abortAndRefund(String reason) {
        float lump = BURST_TICKETS * resources.reinforcementCost();
        resources.produce(Faction.DEFENDER, ResourceType.REINFORCEMENT, lump);
        LOG.info("counterattack: bulge against " + bulgeSlice + " aborted — " + reason + ", earmark refunded");
        holdStreak = 0;
        resolution = Resolution.ABORTED;
        phase = Phase.COOLDOWN;
        phaseTimer = ABORT_COOLDOWN_SEC;
    }

    /** Clears the muster snapshot and returns to IDLE. Does not touch {@link #bulgesFired}. */
    private void resetToIdle() {
        phase = Phase.IDLE;
        phaseTimer = 0f;
        bulgeSlice = null;
        bulgeTargets = null;
        rotationIndex = 0;
        holdStreak = 0;
        resolution = Resolution.NONE;
        bulgeCenterX = Float.NaN;
        bulgeCenterY = Float.NaN;
    }

    private void updateBulgeCenter(List<RecaptureTarget> candidates) {
        float sumX = 0f;
        float sumY = 0f;
        for (RecaptureTarget candidate : candidates) {
            sumX += candidate.objectiveX() + 0.5f;
            sumY += candidate.objectiveY() + 0.5f;
        }
        bulgeCenterX = sumX / candidates.size();
        bulgeCenterY = sumY / candidates.size();
    }
}
