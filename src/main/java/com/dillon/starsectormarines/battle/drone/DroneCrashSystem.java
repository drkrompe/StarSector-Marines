package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.component.ComponentStore;
import com.dillon.starsectormarines.battle.component.Crashing;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The drone crash, modelled as composition rather than a per-tick type scan.
 * Two halves over a {@link Crashing} {@link ComponentStore}:
 *
 * <ol>
 *   <li><b>Attach</b> ({@link #onDeath}) — a death-event handler. When a
 *       {@link Drone} dies (shot down, or hub-cascade-killed), it attaches a
 *       {@code Crashing} component seeded with the drone's body + crash tuning
 *       and puffs the initial smoke plume. Attaching the component <em>is</em>
 *       starting the crash.</li>
 *   <li><b>Process</b> ({@link #tick}) — iterates the entities that
 *       <em>have</em> a {@code Crashing} component (not every unit), spinning
 *       each body's facing and counting its timer down; on impact it drops a
 *       {@link com.dillon.starsectormarines.battle.combat.fx.SmokingWreck} at
 *       the body's floor cell and detaches the component (crash done).</li>
 * </ol>
 *
 * <p>The FX is the side effect of an entity carrying the component — no
 * {@code List<Unit>} scan, no {@code instanceof}/{@code !isAlive()} gating in
 * the hot path. The renderer reads the same store to draw a falling entity (the
 * presence of a {@code Crashing} component) with its tumble + fade. The store
 * is keyed by entity id, so a crashing drone keeps its component after release
 * from the live {@code UnitRegistry}.
 *
 * <p>{@link #onDeath} is the only entity-aware part (it knows a {@code Drone}
 * crashes, and its tuning); {@link #tick} is entity-agnostic and processes any
 * future air unit that gets a {@code Crashing} component the same way.
 */
public final class DroneCrashSystem {

    private final NavigationService navigation;
    private final EffectsService effects;
    private final ComponentStore<Crashing> crashing;

    public DroneCrashSystem(NavigationService navigation,
                            EffectsService effects,
                            ComponentStore<Crashing> crashing) {
        this.navigation = navigation;
        this.effects = effects;
        this.crashing = crashing;
    }

    /**
     * Death-event callback: a dead {@link Drone} starts crashing. Attaches a
     * {@code Crashing} component (seeded with the drone's body + crash tuning)
     * and puffs the opening smoke plume from the body position. Ignores
     * non-drone deaths and a drone that somehow already carries the component.
     */
    public void onDeath(DeathEvent event) {
        if (!(event.unit() instanceof Drone d)) return;
        if (crashing.has(d.entityId)) return;
        crashing.add(d.entityId,
                new Crashing(d.body, Drone.CRASH_DURATION_SEC, Drone.CRASH_SPIN_DEG_PER_SEC));
        effects.spawnSmokePlume(d.body.x, d.body.y);
    }

    /**
     * Advances every crashing entity's fall by {@code dt} sim-seconds: spin the
     * body facing, count the timer down, and on impact drop a smoking wreck at
     * the body's floor cell + detach the component. Iterates only entities that
     * have the component.
     */
    public void tick(float dt) {
        if (crashing.isEmpty()) return;
        NavigationGrid grid = navigation.getGrid();
        List<Long> settled = null;
        for (Map.Entry<Long, Crashing> e : crashing.entries()) {
            Crashing c = e.getValue();
            c.timer -= dt;
            c.body.facingDegrees += c.spinDegPerSec * dt;
            if (c.timer <= 0f) {
                int wx = Math.max(0, Math.min(grid.getWidth() - 1, (int) Math.floor(c.body.x)));
                int wy = Math.max(0, Math.min(grid.getHeight() - 1, (int) Math.floor(c.body.y)));
                effects.spawnSmokingWreck(wx, wy);
                if (settled == null) settled = new ArrayList<>();
                settled.add(e.getKey());
            }
        }
        if (settled != null) {
            for (int i = 0, n = settled.size(); i < n; i++) {
                crashing.remove(settled.get(i));
            }
        }
    }
}
