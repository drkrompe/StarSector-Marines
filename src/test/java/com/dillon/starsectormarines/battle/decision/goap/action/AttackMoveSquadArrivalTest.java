package com.dillon.starsectormarines.battle.decision.goap.action;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.decision.goap.ActionStatus;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.squad.SquadPlan;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An attack move plan is one step shared by the whole squad, so
 * {@code GoapInfantryBehavior} completes it for every member the moment any
 * single member's {@link AttackMove#execute} reports {@link ActionStatus#SUCCESS}.
 * A lead member arriving first therefore cannot be allowed to report SUCCESS
 * while a squadmate is still well short of the objective — that freezes every
 * other member's movement call for the tick, and the plan simply rebuilds and
 * repeats the same completion next tick. See {@code AttackMove.SQUAD_ARRIVAL_RADIUS}.
 */
public class AttackMoveSquadArrivalTest {

    private static final int W = 32;
    private static final int H = 32;
    private static final int DEST_X = 20;
    private static final int DEST_Y = 15;

    private record Fixture(BattleSimulation sim, Squad squad,
                            AttackMove action, long lead, long straggler) { }

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(W, H));
    }

    /**
     * Two-member squad, both inside {@code ARRIVAL_RADIUS} of the destination
     * except the straggler, whose distance is the parameter under test. No
     * opposing faction is on the map, so nothing but the arrival check itself
     * can produce a fire intent or a completed plan.
     */
    private static Fixture fixture(float stragglerCellsFromDest) {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);

        long lead = sim.spawn(new EntitySpec("lead", Faction.MARINE,
                UnitType.MARINE, DEST_X, DEST_Y).squad(squadId));
        int stragglerX = DEST_X - Math.round(stragglerCellsFromDest);
        long straggler = sim.spawn(new EntitySpec("straggler", Faction.MARINE,
                UnitType.MARINE, stragglerX, DEST_Y).squad(squadId));

        squad.leaderId = lead;
        squad.aliveMembers = 2;
        squad.originalSize = 2;
        squad.centroidX = (DEST_X + stragglerX) / 2f;
        squad.centroidY = DEST_Y;
        squad.assignedObjective = ObjectiveAssignment.attackMove(
                squad.id, DEST_X, DEST_Y);

        AttackMove action = new AttackMove(DEST_X, DEST_Y);
        SquadPlan.Step step = new SquadPlan.Step(action);
        step.assignments.put(AbstractZoneAction.TEAM_A, List.of(lead, straggler));
        squad.currentPlan = new SquadPlan(List.of(step));

        return new Fixture(sim, squad, action, lead, straggler);
    }

    @Test
    public void arrivedMemberHoldsWhileAStragglerIsStillOutsideSquadArrival() {
        // Outside AttackMove.SQUAD_ARRIVAL_RADIUS (5f).
        Fixture f = fixture(8f);

        ActionStatus status = f.action.execute(f.lead, f.squad, f.sim);

        assertEquals(ActionStatus.RUNNING, status,
                "one member arriving must not complete the squad's shared plan "
                        + "while a squadmate is still well short of the objective");
    }

    /**
     * The straggler is not a straggler on this objective: he is a campaign
     * marine who landed after the squad stepped off and is crossing to it, and
     * a shared step that waited for him would pin the whole squad on the
     * objective until he arrived from the landing zone. Same exclusion the
     * dispatcher already makes when it hands out the step's slots.
     */
    @Test
    public void aRejoiningMemberDoesNotPinTheStep() {
        Fixture f = fixture(30f);
        f.squad.markRejoining(f.straggler);

        assertEquals(ActionStatus.SUCCESS, f.action.execute(f.lead, f.squad, f.sim),
                "a member still closing on the squad does not hold its arrival open");
        assertTrue(AttackMove.squadHasArrived(f.squad, DEST_X, DEST_Y, f.sim),
                "and the release rule agrees with the action about it");
    }

    @Test
    public void wholeSquadArrivingCompletesTheStep() {
        // Inside AttackMove.SQUAD_ARRIVAL_RADIUS (5f).
        Fixture f = fixture(3f);

        ActionStatus status = f.action.execute(f.lead, f.squad, f.sim);

        assertEquals(ActionStatus.SUCCESS, status,
                "once every assigned member has closed to within squad-arrival "
                        + "range, the member being asked may complete the step");
    }
}
