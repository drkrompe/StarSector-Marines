package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.DeathEvent;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRegistry;
import com.dillon.starsectormarines.battle.infantry.PatrolRoute;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.world.MapService;
import com.dillon.starsectormarines.battle.decision.TacticalContextService;
import com.dillon.starsectormarines.battle.unit.DeathDispatcher;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.List;

/**
 * Death-event handler that converts a destroyed {@link MapTurret} into walkable
 * rubble. Subscribes to the {@link DeathDispatcher}; fires once per turret
 * death when the mailbox drains (the {@code DEMOLISH} phase). Pairs
 * with {@code HubDemolitionSystem} (same flip-to-rubble pattern for drone hubs)
 * but stays separate because the post-demolish step — releasing the squad that
 * was guarding the post once every turret on it is dead — is turret-only and
 * would clutter the hub path.
 *
 * <p>Migrated off the legacy {@code List<Unit>} scan (the old per-tick
 * {@code !isAlive() && !demolished} sweep) to the event seam — the first
 * handler proving the {@code retire-legacy-units-list} spine. The
 * {@link MapTurret#demolished} flag stays as a defensive double-fire guard
 * (a death publishes exactly once, so it's belt-and-suspenders) and as the
 * "already rubble" marker the renderer reads.
 *
 * <p>Sibling to other {@code *System} consumers — all dependencies
 * constructor-injected, no per-event state.
 */
public final class TurretDemolitionSystem {

    private final MapService mapService;
    private final EffectsService effects;
    private final TacticalContextService tactical;
    private final UnitRosterService roster;

    public TurretDemolitionSystem(MapService mapService,
                                  EffectsService effects,
                                  TacticalContextService tactical,
                                  UnitRosterService roster) {
        this.mapService = mapService;
        this.effects = effects;
        this.tactical = tactical;
        this.roster = roster;
    }

    /**
     * Death-event callback. Flips a newly-dead {@link MapTurret} into walkable
     * rubble + a smoking wreck and releases the owning defense post's squad if
     * every turret on the post is now down. Ignores non-turret deaths and
     * already-demolished turrets (the latter can't happen via the dispatcher —
     * a death publishes once — but the guard keeps the method safe if ever
     * called twice).
     */
    public void onDeath(DeathEvent event) {
        if (!(event.unit() instanceof MapTurret t)) return;
        if (t.demolished) return;
        // Death cell from the event snapshot — the turret is released by the
        // time this drains, so its Group-C cell accessors are fail-loud.
        int cx = event.cellX();
        int cy = event.cellY();
        mapService.flipCellToRubble(cx, cy);
        t.demolished = true;
        // Mount cell keeps smoking for a while so the player can see the
        // wreck is dead-and-cooling rather than just "gone".
        effects.spawnSmokingWreck(cx, cy);
        releaseGuardpostIfAllTurretsDead(cx, cy);
    }

    /**
     * If {@code deadTurret} was part of a {@link DefensePost} and every turret
     * on that post is now dead, find the squad linked to the post and revert
     * its patrol radius to the wide default — so the garrison stops orbiting
     * the wreckage and resumes normal search-and-destroy via the existing
     * SUSPICIOUS/ENGAGED transitions. No-op for stand-alone turrets (legacy
     * scatter, port defenses outside conquest).
     *
     * <p>Linear scan through posts + units is fine here: turret deaths cap at
     * ~10-15 per battle, posts at ~5-8, units at the few hundred peak — total
     * work bounded and infrequent.
     *
     * <p>Reads the dense registry for the "is any turret at this spec cell still
     * alive?" query. The query only cares about <em>live</em> turrets — a spec
     * with no live turret at its cell counts as down, which covers the
     * demolished, never-spawned, and just-killed cases alike — so the live-only
     * registry is exactly the right view (a dead turret is simply absent). No
     * corpse read needed.
     *
     * <p>{@code deadCellX/Y} are the dead turret's death cell, snapshotted off
     * the {@link DeathEvent} — the turret is already released here, so its own
     * cell accessors would fail loud.
     */
    private void releaseGuardpostIfAllTurretsDead(int deadCellX, int deadCellY) {
        UnitRegistry registry = roster.getRegistry();
        List<DefensePost> defensePosts = tactical.getDefensePosts();
        if (defensePosts.isEmpty()) return;
        DefensePost owner = null;
        for (DefensePost post : defensePosts) {
            for (DefensePost.TurretSpec spec : post.turrets) {
                if (spec.cellX == deadCellX && spec.cellY == deadCellY) {
                    owner = post;
                    break;
                }
            }
            if (owner != null) break;
        }
        if (owner == null) return;
        // Check whether every turret on the owning post is now dead. A spec
        // with no live MapTurret at its cell counts as dead — covers both the
        // already-demolished and the never-spawned edge cases.
        for (DefensePost.TurretSpec spec : owner.turrets) {
            boolean aliveAtSpec = false;
            for (int i = 0, n = registry.liveCount(); i < n; i++) {
                Unit u = registry.get(i);
                if (!(u instanceof MapTurret)) continue;
                if (u.getCellX() != spec.cellX || u.getCellY() != spec.cellY) continue;
                aliveAtSpec = true;
                break;
            }
            if (aliveAtSpec) return;
        }
        for (Squad squad : roster.getSquads()) {
            if (squad.defensePost != owner) continue;
            squad.defensePost = null;
            squad.patrolRadius = PatrolRoute.DEFAULT_DISTRICT_RADIUS;
        }
    }
}
