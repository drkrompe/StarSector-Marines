package com.dillon.starsectormarines.battle.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Death-event mailbox / distributor. The single fan-out point for unit death:
 * the damage pipeline {@link #publish(DeathEvent) publishes} a {@link DeathEvent}
 * on the {@code wasAlive → !isAlive} transition; pluggable handlers
 * {@link #subscribe(Consumer) subscribe} and each decides how to represent the
 * death (turret/hub demolition, drone-crash FX, render, future medic/revive).
 *
 * <p>Decoupling the death reaction from the death <em>site</em>
 * ({@code DamageResolver}) is the point: new post-death behavior attaches as a
 * handler, never as another edit to the resolve god-method. It also keeps the
 * dense {@link UnitRosterService} strictly live-only — the spine of the
 * {@code retire-legacy-units-list} migration.
 *
 * <h2>Buffered, not synchronous</h2>
 *
 * Publish does not invoke handlers inline. Events accumulate in {@link #pending}
 * and are fanned out by {@link #drain()}, which the sim calls once per tick at a
 * fixed phase (where the demolition passes used to scan the roster). This is
 * deliberate:
 *
 * <ul>
 *   <li>{@code resolve} fires at several points in a tick (inline direct fire,
 *       the {@code APPLY_DAMAGE} queue drain, AoE detonations, off-tick external
 *       strafing). Buffering decouples <em>when a death is recorded</em> from
 *       <em>when its reaction runs</em>, so handlers always run at one known,
 *       serial phase regardless of which path killed the unit — exactly the
 *       end-of-tick timing the batch demolition systems had.</li>
 *   <li>By drain time every unit that died this tick is fully dead, so a handler
 *       that queries sibling state (e.g. "are all turrets on this post down?")
 *       sees the same settled picture the per-tick scan did.</li>
 * </ul>
 *
 * <p><b>Serial-only.</b> Both {@code publish} and {@code drain} run on the sim
 * thread — {@code resolve} is only ever called serially (inline when
 * {@code !insideParallel}, else from the serial {@code flushPendingDamage}
 * drain), so the {@code ArrayList}s need no synchronization. This mirrors the
 * existing {@code deathsThisFrame} sink.
 */
public final class DeathDispatcher {

    private final List<Consumer<DeathEvent>> handlers = new ArrayList<>();

    /**
     * Two swap buffers, not one. {@link #drain()} processes the events it found
     * at entry out of one buffer while re-entrant {@link #publish(DeathEvent)}
     * calls (a handler killing more units — e.g. a hub demolition cascading its
     * drones) land in the other. Swapped each wave so a death published during
     * fan-out is itself fanned out, in the same drain, on the next wave. Without
     * the second buffer a re-entrant publish would either be dropped by the
     * end-of-drain clear or trip a concurrent-modification on the loop.
     */
    private List<DeathEvent> pending = new ArrayList<>();
    private List<DeathEvent> draining = new ArrayList<>();

    /**
     * Registers a handler. Handlers fire in subscription order on every
     * {@link #drain()}. Subscribe once at sim setup — there is no unsubscribe;
     * a handler that wants to ignore an event filters inside its own callback
     * (e.g. {@code instanceof MapTurret}).
     */
    public void subscribe(Consumer<DeathEvent> handler) {
        handlers.add(handler);
    }

    /**
     * Records a death for fan-out at the next {@link #drain()}. Called serially
     * — from the death cascade, or re-entrantly from a handler killing further
     * units mid-drain. Cheap (one list append, no handler invocation).
     */
    public void publish(DeathEvent event) {
        pending.add(event);
    }

    /**
     * Fans every buffered event out to every handler in publish-then-subscribe
     * order. Drains in waves until quiescent: events published <em>by</em> a
     * handler during fan-out are themselves fanned out before {@code drain}
     * returns (same tick). Idempotent on an empty buffer; called once per tick
     * at the death-reaction phase. Allocation-free — the two buffers are reused
     * and swapped, never reallocated.
     */
    public void drain() {
        while (!pending.isEmpty()) {
            // Swap: process this wave out of `draining`; re-entrant publishes
            // accumulate in the now-empty `pending` for the next wave.
            List<DeathEvent> wave = pending;
            pending = draining;
            draining = wave;
            for (int i = 0, n = wave.size(); i < n; i++) {
                DeathEvent e = wave.get(i);
                for (int h = 0, m = handlers.size(); h < m; h++) {
                    handlers.get(h).accept(e);
                }
            }
            wave.clear();
        }
    }
}
