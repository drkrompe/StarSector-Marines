package com.dillon.starsectormarines.battle.turret;

import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.ai.goap.actions.PatrolRoute;
import com.dillon.starsectormarines.battle.combat.fx.EffectsService;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.decision.TacticalContextService;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.List;

/**
 * Stateless tick consumer that converts destroyed {@link MapTurret}s into
 * walkable rubble at the end of {@code BattleSimulation.tick}. Pairs with
 * {@code HubDemolitionSystem} (same flip-to-rubble pattern for drone hubs)
 * but stays separate because the post-demolish step — releasing the squad
 * that was guarding the post once every turret on it is dead — is
 * turret-only and would clutter the hub path.
 *
 * <p>Idempotency is via {@link MapTurret#demolished}: successive ticks
 * skip wrecks the system has already processed.
 *
 * <p>Sibling to other {@code *System} tick consumers — single {@link #tick}
 * entry point, all dependencies constructor-injected.
 */
public final class TurretDemolitionSystem {

    private final NavigationService navigation;
    private final EffectsService effects;
    private final TacticalContextService tactical;
    private final UnitRosterService roster;

    public TurretDemolitionSystem(NavigationService navigation,
                                  EffectsService effects,
                                  TacticalContextService tactical,
                                  UnitRosterService roster) {
        this.navigation = navigation;
        this.effects = effects;
        this.tactical = tactical;
        this.roster = roster;
    }

    /**
     * Walks {@code units}, flipping any newly-dead, not-yet-demolished
     * {@link MapTurret} into walkable rubble + a smoking wreck, and
     * releasing the owning defense post's squad if every turret on the post
     * is now down. Safe to call every tick — work is gated on
     * {@link MapTurret#demolished}.
     */
    public void tick(List<Unit> units) {
        for (int i = 0, n = units.size(); i < n; i++) {
            Unit u = units.get(i);
            if (!(u instanceof MapTurret t)) continue;
            if (t.isAlive() || t.demolished) continue;
            navigation.flipCellToRubble(t.getCellX(), t.getCellY());
            t.demolished = true;
            // Mount cell keeps smoking for a while so the player can see the
            // wreck is dead-and-cooling rather than just "gone".
            effects.spawnSmokingWreck(t.getCellX(), t.getCellY());
            releaseGuardpostIfAllTurretsDead(t, units);
        }
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
     */
    private void releaseGuardpostIfAllTurretsDead(MapTurret deadTurret, List<Unit> units) {
        List<DefensePost> defensePosts = tactical.getDefensePosts();
        if (defensePosts.isEmpty()) return;
        DefensePost owner = null;
        for (DefensePost post : defensePosts) {
            for (DefensePost.TurretSpec spec : post.turrets) {
                if (spec.cellX == deadTurret.getCellX() && spec.cellY == deadTurret.getCellY()) {
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
            for (int i = 0, n = units.size(); i < n; i++) {
                Unit u = units.get(i);
                if (!(u instanceof MapTurret)) continue;
                if (u.getCellX() != spec.cellX || u.getCellY() != spec.cellY) continue;
                if (u.isAlive()) { aliveAtSpec = true; break; }
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
