package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.unit.DeathDispatcher;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.world.MapService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.List;

/**
 * Death-event handler that converts a destroyed {@link DroneHubUnit} into
 * walkable rubble + cascades the kill into every drone the hub launched.
 * Subscribes to the {@link DeathDispatcher}; fires once per hub death when the
 * mailbox drains (the {@code DEMOLISH} phase). Pairs with
 * {@code TurretDemolitionSystem} (same flip-to-rubble pattern) but stays
 * separate because the cascade step is hub-only and would clutter the turret
 * path.
 *
 * <p>Hubs sit on the sealed center cell of a {@code DRONE_HUB} defense post
 * (non-walkable STONE), so without the flip the cell would stay sealed after
 * the hub dies — an invisible obstacle with no sprite. No guardpost release:
 * hubs have {@code garrisonSize=0} and emit no GUARDPOST tactical node.
 *
 * <p>Migrated off the legacy {@code List<Unit>} scan (the old per-tick
 * {@code !isAlive() && !demolished} sweep) to the event seam, following
 * {@code TurretDemolitionSystem}. The {@link DroneHubUnit#demolished} flag
 * stays as a defensive double-fire guard (a death publishes exactly once) and
 * as the "already rubble" marker the renderer reads.
 *
 * <p>The drone cascade still scans the legacy roster list to find the hub's
 * drones, but each cascade-killed drone now {@link DeathDispatcher#publish
 * publishes} its own {@code DeathEvent} (in addition to the direct hp=0 +
 * release), so the same death-event seam that handles a shot-down drone also
 * starts a cascade-killed drone's crash — the {@code DroneCrashSystem} attaches
 * its {@code Crashing} component off that event, not a list scan. The publish
 * is re-entrant (it happens while the dispatcher is mid-{@code drain()}); the
 * dispatcher drains in waves precisely so these land in the same drain.
 *
 * <p>Sibling to other {@code *System} consumers — all dependencies
 * constructor-injected, no per-event state.
 */
public final class HubDemolitionSystem {

    private final MapService mapService;
    private final EffectsService effects;
    private final UnitRosterService roster;
    private final DeathDispatcher deathDispatcher;

    public HubDemolitionSystem(MapService mapService,
                               EffectsService effects,
                               UnitRosterService roster,
                               DeathDispatcher deathDispatcher) {
        this.mapService = mapService;
        this.effects = effects;
        this.roster = roster;
        this.deathDispatcher = deathDispatcher;
    }

    /**
     * Death-event callback. Flips a newly-dead {@link DroneHubUnit} into
     * walkable rubble + a smoking wreck, then cascade-kills every {@link Drone}
     * that called the hub home so the downstream crash system picks them up.
     * Ignores non-hub deaths and already-demolished hubs (the latter can't
     * happen via the dispatcher — a death publishes once — but the guard keeps
     * the method safe if ever called twice).
     */
    public void onDeath(DeathEvent event) {
        if (!(event.unit() instanceof DroneHubUnit h)) return;
        if (h.demolished) return;
        mapService.flipCellToRubble(h.getCellX(), h.getCellY());
        h.demolished = true;
        effects.spawnSmokingWreck(h.getCellX(), h.getCellY());
        cascadeKillDrones(h);
    }

    /**
     * Cascading kill: drones launched from {@code h} lose control and crash
     * with it. Set hp=0 here; the crash system (next phase in the tick chain)
     * starts the per-drone fall sequence + impact FX by iterating the legacy
     * units list. Release from the dense registry in the same beat so the next
     * tick's UPDATE_UNITS dispatch doesn't see a hp=0 drone in the dense view —
     * {@code DamageResolver} is the registry's only other release path, and
     * this cascade bypasses it.
     *
     * <p>Reads the legacy roster list to find the hub's drones — one of the
     * {@code retire-legacy-units-list} corpse-adjacent reads; migrates with the
     * rest of that bucket. Each killed drone publishes a {@code DeathEvent} so
     * the death-event seam starts its crash (the {@code DroneCrashSystem}
     * attaches its {@code Crashing} component on that event), exactly as a
     * shot-down drone's resolve-published death does.
     */
    private void cascadeKillDrones(DroneHubUnit h) {
        List<Unit> units = roster.getUnits();
        for (int j = 0, m = units.size(); j < m; j++) {
            Unit other = units.get(j);
            if (!(other instanceof Drone d)) continue;
            if (!d.isAlive() || d.homeHub != h) continue;
            d.setHp(0f);
            // Publish before release, mirroring DamageResolver.resolve's
            // ordering — re-entrant into the in-progress drain, fanned out on
            // the next wave (the dispatcher is wave-drained for exactly this).
            deathDispatcher.publish(new DeathEvent(d));
            roster.releaseFromRegistry(d.entityId);
        }
    }
}
