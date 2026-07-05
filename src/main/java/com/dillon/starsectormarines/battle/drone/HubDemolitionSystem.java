package com.dillon.starsectormarines.battle.drone;

import com.dillon.starsectormarines.battle.unit.DeathDispatcher;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.world.MapEditor;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.sim.IdentityService;
import com.dillon.starsectormarines.battle.sim.World;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Death-event handler that converts a destroyed drone hub
 * ({@code UnitType.isDroneHub()}) into walkable rubble + cascades the kill
 * into every drone the hub launched. Subscribes to the {@link DeathDispatcher};
 * fires once per hub death when the mailbox drains (the {@code DEMOLISH}
 * phase). Pairs with {@code TurretDemolitionSystem} (same flip-to-rubble
 * pattern) but stays separate because the cascade step is hub-only and would
 * clutter the turret path.
 *
 * <p>Hubs sit on the sealed center cell of a {@code DRONE_HUB} defense post
 * (non-walkable STONE), so without the flip the cell would stay sealed after
 * the hub dies — an invisible obstacle with no sprite. No guardpost release:
 * hubs have {@code garrisonSize=0} and emit no GUARDPOST tactical node.
 *
 * <p>Migrated off the legacy {@code List<Entity>} scan (the old per-tick
 * {@code !isAlive() && !demolished} sweep) to the event seam, following
 * {@code TurretDemolitionSystem}. The {@code demolished} flag used to live as
 * a field on the hub's (now-dissolved) dedicated {@code Entity} subclass; it's
 * now {@link #demolishedHubs}, an id side-table here — the hub itself is a plain
 * {@code Entity} with no per-instance demolition state. Still a defensive
 * double-fire guard (a death publishes exactly once) and the "already
 * demolished" marker {@link #isDemolished} exposes for tests.
 *
 * <p>The drone cascade finds the hub's drones in the dense registry, and each
 * cascade-killed drone {@link DeathDispatcher#publish publishes} its own
 * {@code DeathEvent} (in addition to the direct hp=0 + release), so the same
 * death-event seam that handles a shot-down drone also starts a cascade-killed
 * drone's crash — the {@code DroneCrashSystem} attaches its {@code CrashingComponent}
 * component off that event, not a list scan. The publish is re-entrant (it
 * happens while the dispatcher is mid-{@code drain()}); the dispatcher drains in
 * waves precisely so these land in the same drain.
 *
 * <p>Sibling to other {@code *System} consumers — all dependencies
 * constructor-injected; {@link #demolishedHubs} is the one piece of per-hub
 * state this system owns (everything else is stateless).
 */
public final class HubDemolitionSystem {

    private final MapEditor mapEditor;
    private final EffectsService effects;
    private final UnitRosterService roster;
    private final DeathDispatcher deathDispatcher;
    /** Side-table of demolished hub entity ids — replaces the dissolved hub subclass's {@code demolished} field, since a plain {@code Entity} carries no per-instance demolition state. */
    private final LongOpenHashSet demolishedHubs = new LongOpenHashSet();

    public HubDemolitionSystem(MapEditor mapEditor,
                               EffectsService effects,
                               UnitRosterService roster,
                               DeathDispatcher deathDispatcher) {
        this.mapEditor = mapEditor;
        this.effects = effects;
        this.roster = roster;
        this.deathDispatcher = deathDispatcher;
    }

    /**
     * Death-event callback. Flips a newly-dead drone hub into walkable rubble
     * + a smoking wreck, then cascade-kills every {@link Drone} that called
     * the hub home so the downstream crash system picks them up. Ignores
     * non-hub deaths and already-demolished hubs (the latter can't happen via
     * the dispatcher — a death publishes once — but the guard keeps the
     * method safe if ever called twice).
     */
    public void onDeath(DeathEvent event) {
        long u = event.unitId();
        if (!roster.identity().type(u).isDroneHub()) return;
        if (!demolishedHubs.add(u)) return;
        // Death cell from the event snapshot — the hub is released by drain time.
        int cx = event.cellX();
        int cy = event.cellY();
        mapEditor.flipCellToRubble(cx, cy);
        effects.spawnSmokingWreck(cx, cy);
        cascadeKillDrones(u);
    }

    /** True iff the hub with entity id {@code hubId} has already been demolished — the double-fire guard's observable state. Exposed for tests. */
    public boolean isDemolished(long hubId) { return demolishedHubs.contains(hubId); }

    /**
     * Cascading kill: drones launched from the hub {@code hubId} lose control
     * and crash with it. Set hp=0 here; the crash system (next phase in the
     * tick chain) starts the per-drone fall sequence + impact FX off the
     * {@code DeathEvent} each kill publishes. Release from the dense registry
     * in the same beat so the next tick's UPDATE_UNITS dispatch doesn't see a
     * hp=0 drone in the dense view — {@code DamageResolver} is the registry's
     * only other release path, and this cascade bypasses it.
     *
     * <p>Finds the hub's drones in the dense registry (live-only — a dead drone
     * is already gone). Each killed drone publishes a {@code DeathEvent} so the
     * death-event seam starts its crash (the {@code DroneCrashSystem} attaches
     * its {@code CrashingComponent} component on that event), exactly as a shot-down
     * drone's resolve-published death does.
     *
     * <p>Gather-then-kill: {@code releaseFromRegistry} swap-and-pops the dense
     * table, so collecting the doomed drones first keeps the kill loop from
     * corrupting a live registry walk.
     */
    private void cascadeKillDrones(long hubId) {
        World world = roster.world();
        IdentityService identity = roster.identity();
        LongArrayList doomed = null;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long u = roster.get(i);
            if (identity.type(u).isDrone() && roster.droneState().homeHubId(u) == hubId) {
                if (doomed == null) doomed = new LongArrayList();
                doomed.add(u);
            }
        }
        if (doomed == null) return;
        for (int i = 0, n = doomed.size(); i < n; i++) {
            long d = doomed.getLong(i);
            world.setHp(d, 0f);
            // Publish before release, mirroring DamageResolver.resolve's
            // ordering — re-entrant into the in-progress drain, fanned out on
            // the next wave (the dispatcher is wave-drained for exactly this).
            // Snapshot the cell while the drone is still registered.
            // -1 pose: a cascade-killed drone crashes-and-fades (Crashing), no ground corpse.
            deathDispatcher.publish(new DeathEvent(d, world.cellX(d), world.cellY(d), -1));
            roster.releaseFromRegistry(d);
        }
    }
}
