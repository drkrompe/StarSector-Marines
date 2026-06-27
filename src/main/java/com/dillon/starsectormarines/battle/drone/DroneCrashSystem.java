package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.air.AirBody;
import com.dillon.starsectormarines.battle.air.components.CrashingComponent;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * The drone crash, modelled as composition rather than a per-tick type scan.
 * Two halves over the world's {@code CRASHING} component (an OBJECT column holding
 * the {@link CrashingComponent} payload):
 *
 * <ol>
 *   <li><b>Attach</b> ({@link #onDeath}) — a death-event handler. When a
 *       {@link Drone} dies (shot down, or hub-cascade-killed), it attaches a
 *       {@code CrashingComponent} component seeded with the drone's body + crash tuning
 *       and puffs the initial smoke plume. Attaching the component <em>is</em>
 *       starting the crash.</li>
 *   <li><b>Process</b> ({@link #tick}) — iterates the entities that
 *       <em>have</em> a {@code CrashingComponent} component (not every unit), spinning
 *       each body's facing and counting its timer down; on impact it drops a
 *       {@link com.dillon.starsectormarines.battle.combat.fx.SmokingWreck} at
 *       the body's floor cell and detaches the component (crash done).</li>
 * </ol>
 *
 * <p>The FX is the side effect of an entity carrying the component — no
 * {@code List<Entity>} scan, no {@code instanceof}/{@code !isAlive()} gating in
 * the hot path. The renderer reads the same {@code CRASHING} component to draw a
 * falling entity with its tumble + fade. {@code CRASHING} rides the corpse
 * archetype (it is kept off the corpse-transmute's remove mask), so a crashing
 * drone keeps it after death — the entity-id-keyed-store "survives release"
 * semantic, now expressed as archetype membership.
 *
 * <p>{@link #onDeath} is the only entity-aware part (it knows a {@code Drone}
 * crashes, and its tuning); {@link #tick} is entity-agnostic and processes any
 * future air unit that gets a {@code CrashingComponent} component the same way.
 */
public final class DroneCrashSystem {

    private final NavigationService navigation;
    private final EffectsService effects;
    private final EntityWorld world;
    private final BattleComponents components;

    public DroneCrashSystem(NavigationService navigation,
                            EffectsService effects,
                            EntityWorld world,
                            BattleComponents components) {
        this.navigation = navigation;
        this.effects = effects;
        this.world = world;
        this.components = components;
    }

    /**
     * Death-event callback: a dead {@link Drone} starts crashing. Attaches a
     * {@code CrashingComponent} component (seeded with the drone's body + crash tuning)
     * and puffs the opening smoke plume from the body position. Ignores
     * non-drone deaths and a drone that somehow already carries the component.
     */
    public void onDeath(DeathEvent event) {
        if (!(event.unit() instanceof Drone d)) return;
        if (world.has(d.entityId, components.CRASHING)) return;
        // The drone's world entity outlives its registry release (it transmuted to
        // the corpse archetype on this same death drain), so attach CRASHING to it
        // — a one-component row-move onto the corpse. CRASHING is off the
        // corpseRemove mask, so it rides the corpse while the drone falls.
        //
        // KINEMATICS is off the corpseRemove mask too, so the dead drone's AirBody
        // is still readable here. Hand it to the CrashingComponent (which owns the
        // body for the fall), then detach KINEMATICS — a corpse doesn't fly, and
        // the body's lifecycle has moved to the crash component (the
        // MECH_LOADOUT "survive the transmute, detach once read" precedent).
        AirBody body = (AirBody) world.getObject(d.entityId, components.KINEMATICS, BattleComponents.KINEMATICS_BODY);
        world.addComponent(d.entityId, components.CRASHING);
        world.setObject(d.entityId, components.CRASHING, BattleComponents.CRASHING_STATE,
                new CrashingComponent(body, Drone.CRASH_DURATION_SEC, Drone.CRASH_SPIN_DEG_PER_SEC));
        effects.spawnSmokePlume(body.x, body.y);
        world.removeComponent(d.entityId, components.KINEMATICS);
    }

    /**
     * Advances every crashing entity's fall by {@code dt} sim-seconds: spin the
     * body facing, count the timer down, and on impact drop a smoking wreck at
     * the body's floor cell + detach the component. Iterates only entities that
     * have the component.
     */
    public void tick(float dt) {
        NavigationGrid grid = null;
        List<Long> settled = null;
        // Walk every table carrying CRASHING; advance each crash payload in place.
        // Detaching the component on impact is a structural change, so gather the
        // settled ids during the walk and remove them after (gather-then-apply).
        for (ArchetypeTable t : world.matched(components.crashing)) {
            Object[] states = t.objects(components.CRASHING, BattleComponents.CRASHING_STATE).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                CrashingComponent c = (CrashingComponent) states[r];
                c.timer -= dt;
                c.body.facingDegrees += c.spinDegPerSec * dt;
                if (c.timer <= 0f) {
                    if (grid == null) grid = navigation.getGrid();
                    int wx = Math.max(0, Math.min(grid.getWidth() - 1, (int) Math.floor(c.body.x)));
                    int wy = Math.max(0, Math.min(grid.getHeight() - 1, (int) Math.floor(c.body.y)));
                    effects.spawnSmokingWreck(wx, wy);
                    if (settled == null) settled = new ArrayList<>();
                    settled.add(t.entityAt(r));
                }
            }
        }
        if (settled != null) {
            for (int i = 0, n = settled.size(); i < n; i++) {
                world.removeComponent(settled.get(i), components.CRASHING);
            }
        }
    }
}
