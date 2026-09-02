package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.Role;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Bounded source security and belief-driven interdiction for Extraction. */
public final class ExtractionDefenderCommand implements AutonomousMissionCommand<
        ExtractionDefenderCommandFrame, ExtractionDefenseSnapshot> {

    static final int ALARM_RESPONSE_LIMIT = 3;
    /**
     * How old a believed contact may be and still be somewhere worth sending a
     * responder: one command pulse, the interval between the picture this plan
     * was made from and the next one.
     *
     * <p>The mobile pool is bounded source security, not a hunting party. The
     * defender influence snapshot aggregates the beliefs of every defender
     * squad on the map and holds each one until its confidence decays away,
     * which is tens of seconds after anybody last had eyes on it; treating that
     * whole set as interdictable sends the source's own reserve across the map
     * to the place a garrison last glimpsed somebody, and then back again on
     * the next pulse. A sighting older than the pulse is where the enemy was.
     * There is nothing left to interdict, and the doctrine's answer to that is
     * the source perimeter.
     */
    static final int ACTIONABLE_CONTACT_TICKS = Math.round(
            CommanderService.COMMANDER_TICK_PERIOD / BattleSimulation.TICK_DT);
    private static final int[][] RALLY_OFFSETS = {
            {0, -4}, {4, 0}, {0, 4}, {-4, 0},
            {3, -3}, {3, 3}, {-3, 3}, {-3, -3}, {0, 0}
    };

    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    private volatile ExtractionDefenseSnapshot defenseSnapshot;

    public ExtractionDefenderCommand(Set<Integer> mobileSquadIds) {
        if (mobileSquadIds != null) initialMobileSquads.addAll(mobileSquadIds);
    }

    public ExtractionDefenseSnapshot defenseSnapshot() {
        return defenseSnapshot;
    }

    @Override public Faction faction() { return Faction.DEFENDER; }
    @Override public String strategyId() { return "extraction-defender"; }

    @Override
    public CommandPlan<ExtractionDefenseSnapshot> plan(
            ExtractionDefenderCommandFrame frame) {
        ExtractionObjectiveFacts payload = frame.facts().payload();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> initialMobileSquads.contains(squad.squadId())
                        || strategyOwned(squad.directive()))
                .filter(squad -> squad.directive() == null
                        || strategyOwned(squad.directive())
                        || squad.directive().authority().priority()
                        <= CommandAuthority.MISSION_COMMAND.priority())
                .sorted(Comparator
                        .comparingInt((CommandSquadState squad) ->
                                frame.topology().routeLength(
                                        squad.anchorCellX(), squad.anchorCellY(),
                                        payload.sourceCellX(),
                                        payload.sourceCellY()))
                        .thenComparingInt(CommandSquadState::squadId))
                .toList();
        boolean terminal = payload.complete() || payload.failed();
        int assigned = terminal ? 0 : payload.alarmActive()
                ? Math.min(ALARM_RESPONSE_LIMIT, pool.size())
                : Math.min(1, pool.size());
        List<CommanderContact> contacts = contacts(frame);
        int freshestContactTick = contacts.stream()
                .mapToInt(CommanderContact::observedTick).max().orElse(-1);
        List<CommanderContact> actionable = actionable(contacts, frame.tick());
        RallyChoice[] rallies = rallies(pool, assigned, actionable, payload,
                frame);

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            int index = pool.indexOf(squad);
            if (index < 0) {
                Role role = squad.role() == UnitRole.GARRISON
                        ? Role.AUTHORED_POST : Role.EXTERNAL;
                intents.add(new SquadIntent(squad.squadId(), role,
                        role == Role.AUTHORED_POST
                                ? "AUTHORED_POST_PRESERVED"
                                : "EXTERNAL_OWNERSHIP_PRESERVED",
                        squad.assignment() != null
                                ? squad.assignment().kind() : null,
                        squad.assignment() != null
                                ? squad.assignment().targetCellX() : -1,
                        squad.assignment() != null
                                ? squad.assignment().targetCellY() : -1,
                        squad.localContact()));
                continue;
            }
            if (terminal) {
                proposals.add(strategyOwned(squad.directive())
                        ? CommandProposal.release(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        "OBJECTIVE_TERMINAL",
                        CommandStabilityBreak.OBJECTIVE_COMPLETED)
                        : CommandProposal.retain(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        "OBJECTIVE_TERMINAL"));
                intents.add(new SquadIntent(squad.squadId(), Role.RELEASED,
                        "OBJECTIVE_TERMINAL", null, -1, -1,
                        squad.localContact()));
                continue;
            }
            if (index >= assigned) {
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        "MOBILE_RESERVE_HELD",
                        CommandStabilityBreak.CONTEXT_INVALIDATED));
                intents.add(new SquadIntent(squad.squadId(), Role.RESERVE,
                        "MOBILE_RESERVE_HELD", null, -1, -1,
                        squad.localContact()));
                continue;
            }

            RallyChoice rally = rallies[index];
            if (rally == null) {
                proposals.add(CommandProposal.claim(squad.squadId(),
                        CommandAuthority.MISSION_COMMAND,
                        "INTERDICTION_TARGET_UNREACHABLE",
                        CommandStabilityBreak.TARGET_UNREACHABLE));
                intents.add(new SquadIntent(squad.squadId(), Role.STRANDED,
                        "INTERDICTION_TARGET_UNREACHABLE", null, -1, -1,
                        squad.localContact()));
                continue;
            }

            Role role = index == 0 ? Role.SOURCE_GUARD
                    : rally.interdicting() ? Role.INTERDICTION
                    : Role.ALARM_RESPONDER;
            String reason = role == Role.SOURCE_GUARD
                    ? payload.alarmActive() ? "SOURCE_ALARM_SECURITY"
                    : "ROUTINE_SOURCE_SECURITY"
                    : role == Role.INTERDICTION
                    ? "BELIEVED_CONTACT_INTERDICTION"
                    : "SOURCE_ALARM_RESPONSE";
            ObjectiveAssignment assignment = ObjectiveAssignment.defendSite(
                    squad.squadId(), rally.cellX(), rally.cellY());
            proposals.add(CommandProposal.assign(assignment,
                    CommandAuthority.MISSION_COMMAND, reason,
                    stabilityBreak(squad, assignment)));
            intents.add(new SquadIntent(squad.squadId(), role, reason,
                    assignment.kind(), rally.cellX(), rally.cellY(),
                    squad.localContact()));
        }

        Phase phase = terminal ? Phase.TERMINAL
                : payload.alarmActive() ? Phase.ALARM_INTERDICTION
                : Phase.ROUTINE_SECURITY;
        ExtractionDefenseSnapshot detail = new ExtractionDefenseSnapshot(
                frame.tick(), faction(), phase, payload.payloadId(),
                payload.payloadName(), payload.sourceCellX(),
                payload.sourceCellY(), payload.alarmActive(),
                payload.alarmRaisedTick(), payload.complete(), payload.failed(),
                payload.failure(), contacts.size(), freshestContactTick,
                pool.size(), Math.max(0, pool.size() - assigned), intents);
        return new CommandPlan<>(faction(), strategyId(), phase.name(),
                frame.tick(), frame.influence() != null
                ? frame.influence().updatedTick() : -1,
                pool.size(), Math.max(0, pool.size() - assigned),
                List.of(payload.payloadName() + "="
                        + phase.name().toLowerCase()), proposals, detail);
    }

    @Override
    public void publish(CommanderSnapshot<ExtractionDefenseSnapshot> snapshot) {
        defenseSnapshot = snapshot.detail();
    }

    private static List<CommanderContact> contacts(
            ExtractionDefenderCommandFrame frame) {
        if (frame.influence() == null) return List.of();
        return frame.influence().contacts().stream()
                .sorted(Comparator
                        .comparingInt(CommanderContact::observedTick).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                CommanderContact::confidence).reversed())
                        .thenComparingInt(CommanderContact::cellY)
                        .thenComparingInt(CommanderContact::cellX))
                .toList();
    }

    /**
     * The believed contacts a responder may still be sent to, freshest first
     * and in the order the caller gave them.
     *
     * <p>Everything the commander knows still reaches the published picture as
     * a coarse count; this narrows only what a responder may be ordered onto.
     * The whole known set is a memory, and a memory is not a target.
     *
     * @param contacts the side's known contacts, freshest first
     * @param tick     the tick this plan is being made on
     */
    static List<CommanderContact> actionable(List<CommanderContact> contacts,
                                             int tick) {
        return contacts.stream()
                .filter(contact -> tick - contact.observedTick()
                        <= ACTIONABLE_CONTACT_TICKS)
                .toList();
    }

    /**
     * Where each assigned squad rallies, and whether that cell was chosen off a
     * believed contact rather than off the source.
     *
     * <p>Every cell in one pulse is distinct. The pool is bounded source
     * security and its whole point is that the source's security is spread
     * around the source, so two squads sent to stand on one another is a
     * formation nobody would order — and the two searches that can produce it
     * run from different centres, so neither can see the collision on its own.
     * An interdiction cell derived from a contact standing near the source may
     * land exactly on the guard's, and it did. Choosing in pool order with the
     * taken cells carried forward is what makes the searches aware of each
     * other; the source guard is index 0 and therefore keeps the cell it would
     * have had before.
     *
     * @param pool       the mobile pool, in the plan's own stable order
     * @param assigned   how many of it this pulse mobilizes
     * @param actionable the beliefs a responder may still be sent to
     * @return one choice per assigned index, null where nothing is reachable
     */
    private static RallyChoice[] rallies(List<CommandSquadState> pool,
                                         int assigned,
                                         List<CommanderContact> actionable,
                                         ExtractionObjectiveFacts payload,
                                         ExtractionDefenderCommandFrame frame) {
        RallyChoice[] choices = new RallyChoice[assigned];
        Set<Long> taken = new HashSet<>();
        for (int index = 0; index < assigned; index++) {
            CommandSquadState squad = pool.get(index);
            CommanderContact contact = index > 0 && !actionable.isEmpty()
                    ? actionable.get((index - 1) % actionable.size()) : null;
            int[] rally = contact != null
                    ? rally(squad, index, contact.cellX(), contact.cellY(),
                    frame, taken) : null;
            // The published role names the ground actually taken. A contact
            // with no reachable rally position is as unactionable as no contact
            // at all, and the squad standing on the source perimeter is not
            // interdicting anything however it got there.
            boolean interdicting = rally != null;
            if (rally == null) {
                rally = rally(squad, index, payload.sourceCellX(),
                        payload.sourceCellY(), frame, taken);
            }
            if (rally == null) continue;
            taken.add(cellKey(rally[0], rally[1]));
            choices[index] = new RallyChoice(rally[0], rally[1], interdicting);
        }
        return choices;
    }

    private static int[] rally(CommandSquadState squad, int slot,
                               int centerX, int centerY,
                               ExtractionDefenderCommandFrame frame,
                               Set<Long> taken) {
        for (int i = 0; i < RALLY_OFFSETS.length; i++) {
            int[] offset = RALLY_OFFSETS[(slot + i) % RALLY_OFFSETS.length];
            int x = centerX + offset[0];
            int y = centerY + offset[1];
            if (taken.contains(cellKey(x, y))) continue;
            if (frame.topology().isWalkable(x, y)
                    && frame.topology().reachable(squad.anchorCellX(),
                    squad.anchorCellY(), x, y)) return new int[]{x, y};
        }
        return null;
    }

    private static long cellKey(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    /** One squad's rally cell for this pulse, and how it was selected. */
    private record RallyChoice(int cellX, int cellY, boolean interdicting) { }

    private static boolean strategyOwned(CommandDirective directive) {
        return directive != null
                && "extraction-defender".equals(directive.issuer());
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
