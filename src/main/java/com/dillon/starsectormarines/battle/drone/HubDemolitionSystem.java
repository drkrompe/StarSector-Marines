package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.world.MapService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.List;

/**
 * Stateless tick consumer that converts destroyed {@link DroneHubUnit}s into
 * walkable rubble + cascades the kill into every drone the hub launched.
 * Pairs with {@code TurretDemolitionSystem} (same flip-to-rubble pattern) but
 * stays separate because the cascade step is hub-only and would clutter the
 * turret path.
 *
 * <p>Hubs sit on the sealed center cell of a {@code DRONE_HUB} defense post
 * (non-walkable STONE), so without the flip the cell would stay sealed after
 * the hub dies — an invisible obstacle with no sprite. No guardpost release:
 * hubs have {@code garrisonSize=0} and emit no GUARDPOST tactical node.
 *
 * <p>Drones killed by the cascade enter the normal death flow on the next
 * tick — {@link DroneCrashSystem} catches them with {@code !isAlive()} and
 * starts the fall sequence.
 *
 * <p>Sibling to other {@code *System} tick consumers — single {@link #tick}
 * entry point, all dependencies constructor-injected.
 */
public final class HubDemolitionSystem {

    private final MapService mapService;
    private final EffectsService effects;
    private final UnitRosterService roster;

    public HubDemolitionSystem(MapService mapService,
                               EffectsService effects,
                               UnitRosterService roster) {
        this.mapService = mapService;
        this.effects = effects;
        this.roster = roster;
    }

    /**
     * Walks {@code units}, flipping newly-dead, not-yet-demolished
     * {@link DroneHubUnit}s into walkable rubble + a smoking wreck, and
     * setting hp=0 on every {@link Drone} that called the hub home so the
     * downstream crash system picks them up. Safe to call every tick — work
     * is gated on {@link DroneHubUnit#demolished}.
     */
    public void tick(List<Unit> units) {
        for (int i = 0, n = units.size(); i < n; i++) {
            Unit u = units.get(i);
            if (!(u instanceof DroneHubUnit h)) continue;
            if (h.isAlive() || h.demolished) continue;
            mapService.flipCellToRubble(h.getCellX(), h.getCellY());
            h.demolished = true;
            effects.spawnSmokingWreck(h.getCellX(), h.getCellY());
            // Cascading kill: drones launched from this hub lose control and
            // crash with it. Set hp=0 here; the crash system (next call in
            // the tick chain) starts the per-drone fall sequence + impact FX
            // by iterating the legacy units list. Release from the dense
            // registry in the same beat so the next tick's UPDATE_UNITS
            // dispatch doesn't see a hp=0 drone in the dense view —
            // DamageResolver is the registry's only other release path, and
            // this cascade bypasses it.
            for (int j = 0, m = units.size(); j < m; j++) {
                Unit other = units.get(j);
                if (!(other instanceof Drone d)) continue;
                if (!d.isAlive() || d.homeHub != h) continue;
                d.setHp(0f);
                roster.releaseFromRegistry(d.entityId);
            }
        }
    }
}
