package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.BattleSetup;
import com.dillon.starsectormarines.battle.Squad;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.tactical.TacticalNode;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

import java.util.List;
import java.util.function.Consumer;

/**
 * Stateless tick consumer that evaluates per-squad fall-back chains and
 * shepherds in-progress retreats. Runs once per sim-tick after the alert /
 * morale passes and before the per-unit dispatch.
 *
 * <p>Two passes per squad:
 * <ol>
 *   <li><b>Arrival</b> — if the squad is already retreating
 *       ({@link Squad#fallbackInProgress}), check whether every alive member
 *       has arrived at their assigned home cell and clear the flag if so.</li>
 *   <li><b>Trigger</b> — for squads that haven't triggered yet, fire the
 *       fall-back when alive-fraction drops at or below
 *       {@link #FALLBACK_TRIGGER_RATIO}. Picks the first FALLBACK_TO link
 *       on the squad's assigned tactical node, redistributes home cells to
 *       the new anchor, sets the in-progress flag.</li>
 * </ol>
 *
 * <p>Sibling to other {@code *System} tick consumers — single {@link #tick}
 * entry point, all dependencies constructor-injected.
 */
public final class SquadFallbackSystem {

    /** Squad alive-fraction at or below which the fall-back chain fires (50%). */
    private static final float FALLBACK_TRIGGER_RATIO = 0.5f;

    /** Squared cell distance from a unit to its home cell counted as "arrived." */
    private static final float HOME_ARRIVAL_RADIUS_SQ = 2.0f * 2.0f;

    private final NavigationService navigation;
    private final UnitRosterService roster;
    private final Consumer<Unit> pathClearer;

    public SquadFallbackSystem(NavigationService navigation,
                               UnitRosterService roster,
                               Consumer<Unit> pathClearer) {
        this.navigation = navigation;
        this.roster = roster;
        this.pathClearer = pathClearer;
    }

    public void tick(List<Unit> units) {
        for (Squad squad : roster.getSquads()) {
            if (squad.assignedNode == null) continue;
            if (squad.aliveMembers == 0) continue;

            // Arrival pass for in-progress retreats.
            if (squad.fallbackInProgress) {
                if (allMembersHome(squad, units)) squad.fallbackInProgress = false;
                continue;
            }

            // Trigger pass — only fire once per squad.
            if (squad.fallbackTriggered) continue;
            if (squad.originalSize <= 0) continue;
            if ((float) squad.aliveMembers / squad.originalSize > FALLBACK_TRIGGER_RATIO) continue;
            List<TacticalNode> targets = squad.assignedNode.linkedTo(TacticalNode.LinkKind.FALLBACK_TO);
            if (targets.isEmpty()) continue;

            TacticalNode newNode = targets.get(0);
            assignFallbackHomes(squad, newNode, units);
            squad.assignedNode = newNode;
            squad.fallbackTriggered = true;
            squad.fallbackInProgress = true;
        }
    }

    /** True when every alive squad member is within {@link #HOME_ARRIVAL_RADIUS_SQ} of their home cell — caller treats that as "the retreat is finished." */
    private boolean allMembersHome(Squad squad, List<Unit> units) {
        for (int i = 0, n = units.size(); i < n; i++) {
            Unit u = units.get(i);
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (u.homeCellX < 0) continue;
            float dx = u.homeCellX - u.cellX;
            float dy = u.homeCellY - u.cellY;
            if (dx * dx + dy * dy > HOME_ARRIVAL_RADIUS_SQ) return false;
        }
        return true;
    }

    /**
     * Distributes new home cells around {@code newNode}'s anchor to every
     * surviving member of {@code squad}. Reuses
     * {@link BattleSetup#pickCellsNear} so the cover-sorted ordering is the
     * same one the original spawn used — the highest-rank survivors (iterated
     * in unit list order, which preserves spawn priority) take the best new
     * cover stacks.
     */
    private void assignFallbackHomes(Squad squad, TacticalNode newNode, List<Unit> units) {
        List<int[]> cells = BattleSetup.pickCellsNear(navigation.getGrid(),
                newNode.anchorX, newNode.anchorY, 5, squad.aliveMembers);
        int idx = 0;
        for (int i = 0, n = units.size(); i < n; i++) {
            Unit u = units.get(i);
            if (!u.isAlive() || u.squadId != squad.id) continue;
            if (idx >= cells.size()) {
                // Out of cells — keep the survivor's current home so they
                // don't end up homeless. They'll just hold where they are.
                continue;
            }
            int[] cell = cells.get(idx++);
            u.homeCellX = cell[0];
            u.homeCellY = cell[1];
            // Wipe stale path — next garrison tick re-paths to the new home.
            pathClearer.accept(u);
        }
    }
}
