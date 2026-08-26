package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.decision.goap.world.ZoneQueries;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable, perspective-specific input to one autonomous command plan. */
public class CommandFrame {

    private final int tick;
    private final Faction perspective;
    private final List<CommandSquadState> squads;
    private final CommanderInfluenceSnapshot influence;
    private final CommandTopology topology;
    private final CommandAssignmentSnapshot assignments;

    protected CommandFrame(int tick, Faction perspective,
                           List<CommandSquadState> squads,
                           CommanderInfluenceSnapshot influence,
                           CommandTopology topology,
                           CommandAssignmentSnapshot assignments) {
        this.tick = tick;
        this.perspective = Objects.requireNonNull(perspective, "perspective");
        this.squads = List.copyOf(squads);
        this.influence = influence;
        this.topology = Objects.requireNonNull(topology, "topology");
        this.assignments = Objects.requireNonNull(assignments, "assignments");
    }

    public static CommandFrame freeze(BattleView sim, Faction perspective,
                                      CommandTopology topology,
                                      CommandAssignmentSnapshot assignments) {
        CommandAssignmentSnapshot ownAssignments =
                assignments.forPerspective(perspective);
        List<CommandSquadState> rows = new ArrayList<>();
        for (Squad squad : sim.getSquads()) {
            if (squad.faction != perspective) continue;
            int memberCount = sim.squadMemberCount(squad.id);
            long anchor = sim.resolveUnit(squad.leaderId);
            if (anchor == 0L && memberCount > 0) anchor = sim.squadMemberAt(squad.id, 0);
            int anchorX = anchor != 0L ? sim.world().cellX(anchor) : -1;
            int anchorY = anchor != 0L ? sim.world().cellY(anchor) : -1;
            UnitRole role = memberCount > 0
                    ? sim.role().role(sim.squadMemberAt(squad.id, 0)) : null;
            rows.add(new CommandSquadState(squad.id, squad.faction,
                    squad.aliveMembers, squad.centroidX, squad.centroidY,
                    anchorX, anchorY, ZoneQueries.squadCurrentZone(squad, sim),
                    role, squad.hasBelievedContacts(),
                    squad.assignmentExecutionSuspension(),
                    CommandFrameCopies.assignment(squad.assignedObjective),
                    ownAssignments.directiveFor(squad.id)));
        }
        rows.sort(Comparator.comparingInt(CommandSquadState::squadId));
        return new CommandFrame(sim.getSimTickIndex(), perspective, rows,
                sim.getCommanderInfluence(perspective), topology,
                ownAssignments);
    }

    public int tick() { return tick; }
    public Faction perspective() { return perspective; }
    public List<CommandSquadState> squads() { return squads; }
    public CommanderInfluenceSnapshot influence() { return influence; }
    public CommandTopology topology() { return topology; }
    public CommandAssignmentSnapshot assignments() { return assignments; }

    public CommandSquadState squad(int squadId) {
        for (CommandSquadState squad : squads) {
            if (squad.squadId() == squadId) return squad;
        }
        return null;
    }
}
