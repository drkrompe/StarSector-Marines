package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.goap.SquadRouteGoalProvider;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SquadRouteRequest;
import com.dillon.starsectormarines.battle.sim.BattleView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Captures route inputs after serial replanning, before any member moves. */
public final class SquadRoutePreparationSystem {
    private SquadRoutePreparationSystem() {}

    public static List<SquadRouteRequest> collect(BattleView sim) {
        List<Squad> squads = new ArrayList<>(sim.getSquads());
        squads.sort(Comparator.comparingInt(squad -> squad.id));
        List<SquadRouteRequest> requests = new ArrayList<>();
        NavigationGrid grid = sim.getGrid();
        for (Squad squad : squads) {
            SquadPlan plan = squad.currentPlan;
            SquadPlan.Step step = plan == null ? null : plan.currentStep();
            if (step == null || !(step.action instanceof SquadRouteGoalProvider provider)) continue;
            SquadRouteGoalProvider.Goal goal = provider.squadRouteGoal(squad, sim);
            if (goal == null || !grid.inBounds(goal.x(), goal.y())) continue;
            List<Long> members = step.allAssignedMembers();
            members.sort(Long::compare);
            int[] starts = new int[members.size()];
            int count = 0;
            long previous = 0L;
            for (long member : members) {
                if (member == previous) continue;
                previous = member;
                if (sim.resolveUnit(member) == 0L || sim.isRiding(member)) continue;
                int x = sim.world().cellX(member);
                int y = sim.world().cellY(member);
                if (grid.inBounds(x, y)) starts[count++] = grid.index(x, y);
            }
            if (count != 0) {
                requests.add(new SquadRouteRequest(squad.id, squad.routingEpoch, step,
                        goal.x(), goal.y(), Arrays.copyOf(starts, count),
                        sim.getRouteCostField(squad.faction), step.action.name()));
            }
        }
        return List.copyOf(requests);
    }
}
