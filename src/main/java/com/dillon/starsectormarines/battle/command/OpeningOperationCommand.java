package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Phase;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Reason;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.Role;
import com.dillon.starsectormarines.battle.command.OpeningOperationCommandPicture.SquadIntent;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.ops.OpeningOperationKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Belief-honest small-force command around one authored scenario place. */
public final class OpeningOperationCommand implements AutonomousMissionCommand<
        OpeningOperationCommandFrame, OpeningOperationCommandPicture> {

    public static final String ISSUER_PREFIX = "opening-operation:";
    private static final int[][] RALLY_OFFSETS = {
            {0, 0}, {0, -3}, {3, 0}, {0, 3}, {-3, 0},
            {2, -2}, {2, 2}, {-2, 2}, {-2, -2}
    };

    private final Faction faction;
    private volatile OpeningOperationCommandPicture operationPicture;

    public OpeningOperationCommand(Faction faction) {
        if (faction != Faction.MARINE && faction != Faction.DEFENDER) {
            throw new IllegalArgumentException(
                    "opening operation requires MARINE or DEFENDER command");
        }
        this.faction = faction;
    }

    @Override public Faction faction() { return faction; }
    @Override public String strategyId() { return issuer(faction); }
    public OpeningOperationCommandPicture operationPicture() {
        return operationPicture;
    }

    public static String issuer(Faction faction) {
        return ISSUER_PREFIX + faction;
    }

    @Override
    public CommandPlan<OpeningOperationCommandPicture> plan(
            OpeningOperationCommandFrame frame) {
        Phase phase = phase(frame.facts().kind());
        Role commandedRole = commandedRole(frame.facts().kind());
        Reason commandedReason = commandedReason(frame.facts().kind());
        boolean advancing = commandedRole == Role.ASSAULT_ELEMENT
                || commandedRole == Role.SECURE_ELEMENT;
        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        int commandPoolSize = 0;

        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (!ownedByStrategy(squad, frame.tick())) {
                CommandDirective incumbent = squad.directive();
                Role role = squad.role() == UnitRole.GARRISON
                        || incumbent != null
                        && incumbent.authority() == CommandAuthority.GARRISON
                        ? Role.AUTHORED_POST : Role.EXTERNAL;
                Reason reason = role == Role.AUTHORED_POST
                        ? Reason.AUTHORED_POST_PRESERVED
                        : Reason.EXTERNAL_OWNERSHIP_PRESERVED;
                ObjectiveAssignment assignment = incumbent != null
                        ? incumbent.assignment() : squad.assignment();
                proposals.add(CommandProposal.retain(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND, reason.name()));
                intents.add(new SquadIntent(squad.squadId(), role, reason,
                        assignment != null ? assignment.kind() : null,
                        assignment != null ? assignment.targetCellX() : -1,
                        assignment != null ? assignment.targetCellY() : -1));
                continue;
            }
            commandPoolSize++;
            int[] rally = reachableRally(squad, frame);
            if (rally == null) {
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        Reason.OBJECTIVE_UNREACHABLE.name(),
                        CommandStabilityBreak.TARGET_UNREACHABLE));
                intents.add(new SquadIntent(squad.squadId(), Role.UNASSIGNED,
                        Reason.OBJECTIVE_UNREACHABLE, null, -1, -1));
                continue;
            }
            ObjectiveAssignment assignment = advancing
                    ? ObjectiveAssignment.sweepSector(squad.squadId(),
                    rally[0], rally[1])
                    : ObjectiveAssignment.defendArea(squad.squadId(),
                    rally[0], rally[1]);
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, commandedReason.name(),
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), commandedRole,
                    commandedReason, assignment.kind(), rally[0], rally[1]));
        }

        OpeningOperationCommandFacts facts = frame.facts();
        int influenceTick = frame.influence() != null
                ? frame.influence().updatedTick() : -1;
        OpeningOperationCommandPicture detail =
                new OpeningOperationCommandPicture(frame.tick(), influenceTick,
                        faction, facts.kind(), phase, facts.placeId(),
                        facts.placeName(), facts.cellX(), facts.cellY(),
                        frame.placeZoneId(), intents);
        return new CommandPlan<>(faction, strategyId(), phase.name(),
                frame.tick(), influenceTick, commandPoolSize, 0,
                List.of(facts.placeName() + "="
                        + commandedReason.name().toLowerCase()), proposals,
                detail);
    }

    @Override
    public void publish(CommanderSnapshot<OpeningOperationCommandPicture> snapshot) {
        operationPicture = snapshot.detail();
    }

    private boolean ownedByStrategy(CommandSquadState squad, int tick) {
        CommandDirective incumbent = squad.directive();
        if (incumbent == null) return false;
        if (strategyId().equals(incumbent.issuer())
                && incumbent.authority() == CommandAuthority.MISSION_COMMAND) {
            return true;
        }
        return incumbent.authority() == CommandAuthority.PLAYER_INTERVENTION
                && incumbent.leaseUntilTick() >= 0
                && incumbent.leaseUntilTick() < tick;
    }

    private Phase phase(OpeningOperationKind kind) {
        if (kind == OpeningOperationKind.RELIEF) {
            return faction == Faction.MARINE
                    ? Phase.PRESERVE_RELIEF_ANCHOR
                    : Phase.ASSAULT_RELIEF_ANCHOR;
        }
        return faction == Faction.MARINE
                ? Phase.SECURE_BANDIT_DEPOT : Phase.DEFEND_BANDIT_DEPOT;
    }

    private Role commandedRole(OpeningOperationKind kind) {
        if (kind == OpeningOperationKind.RELIEF) {
            return faction == Faction.MARINE
                    ? Role.PRESERVE_ELEMENT : Role.ASSAULT_ELEMENT;
        }
        return faction == Faction.MARINE
                ? Role.SECURE_ELEMENT : Role.DEPOT_GUARD;
    }

    private Reason commandedReason(OpeningOperationKind kind) {
        if (kind == OpeningOperationKind.RELIEF) {
            return faction == Faction.MARINE
                    ? Reason.RELIEF_ANCHOR_PRESERVE
                    : Reason.RELIEF_ANCHOR_ASSAULT;
        }
        return faction == Faction.MARINE
                ? Reason.BANDIT_DEPOT_SECURE : Reason.BANDIT_DEPOT_DEFEND;
    }

    private static int[] reachableRally(CommandSquadState squad,
                                        OpeningOperationCommandFrame frame) {
        int start = Math.floorMod(squad.squadId(), RALLY_OFFSETS.length);
        for (int i = 0; i < RALLY_OFFSETS.length; i++) {
            int[] offset = RALLY_OFFSETS[(start + i) % RALLY_OFFSETS.length];
            int x = frame.facts().cellX() + offset[0];
            int y = frame.facts().cellY() + offset[1];
            if (frame.topology().isWalkable(x, y)
                    && frame.topology().reachable(squad.anchorCellX(),
                    squad.anchorCellY(), x, y)) return new int[]{x, y};
        }
        return null;
    }

    private static CommandStabilityBreak stabilityBreak(
            CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null || Objects.equals(
                incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE
                : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }
}
