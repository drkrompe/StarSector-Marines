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
 * dense {@link UnitRegistry} strictly live-only — the spine of the
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
    private final List<DeathEvent> pending = new ArrayList<>();

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
     * from the death cascade; cheap (one list append, no handler invocation).
     */
    public void publish(DeathEvent event) {
        pending.add(event);
    }

    /**
     * Fans every buffered event out to every handler in publish-then-subscribe
     * order, then clears the buffer. Idempotent on an empty buffer. Called once
     * per tick at the death-reaction phase.
     */
    public void drain() {
        if (pending.isEmpty()) return;
        for (int i = 0, n = pending.size(); i < n; i++) {
            DeathEvent e = pending.get(i);
            for (int h = 0, m = handlers.size(); h < m; h++) {
                handlers.get(h).accept(e);
            }
        }
        pending.clear();
    }
}
