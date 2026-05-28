package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.drone.GoapDroneBehavior;
import com.dillon.starsectormarines.battle.infantry.GoapInfantryBehavior;
import com.dillon.starsectormarines.battle.mech.GoapMechBehavior;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * Squad-level replan pass — dispatches each {@link Squad} to the GOAP
 * behavior matching its kind (drone / mech / infantry) so plans reflect
 * THIS tick's fresh {@code aliveMembers} + centroid + alert level before
 * any unit executes. Runs after the squad triad (alert / morale /
 * fallback) and before {@link com.dillon.starsectormarines.battle.decision.UnitUpdateSystem}.
 *
 * <p>Serial today; the planner + WorldStateBuilder + actions are designed
 * for parallel execution across squads (see {@code roadmap/ai/README.md}
 * parallelism section) and we'll fork-join here once we feel the cost.
 * When that happens, the for-loop becomes the next entity for-loop seam —
 * sibling shape to {@link com.dillon.starsectormarines.battle.decision.UnitUpdateSystem},
 * just keyed on {@link Squad} instead of {@code Unit}.
 *
 * <p>Sibling System to {@link SquadAlertSystem} / {@link SquadMoraleSystem}
 * / {@link SquadFallbackSystem}.
 */
public final class SquadReplanSystem {

    private final UnitRosterService rosterService;

    public SquadReplanSystem(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * Run one replan pass across all squads. {@code sim} is threaded
     * through to the GOAP behaviors unchanged — same sim-as-context
     * coupling that the rest of the AI layer carries until the
     * {@code *SimContext} deprecation path completes.
     */
    public void tick(BattleSimulation sim) {
        for (Squad squad : rosterService.getSquads()) {
            if (squad.isDroneSquad()) {
                GoapDroneBehavior.replanIfNeeded(squad, sim);
            } else if (squad.isMechSquad()) {
                GoapMechBehavior.replanIfNeeded(squad, sim);
            } else {
                GoapInfantryBehavior.replanIfNeeded(squad, sim);
            }
        }
    }
}
