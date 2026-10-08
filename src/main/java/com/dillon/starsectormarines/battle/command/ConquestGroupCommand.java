package com.dillon.starsectormarines.battle.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Persistent subordinate intent inside the faction commander, never a second writer. */
final class ConquestGroupCommand {
    static final int BOUND_CELLS = 24;
    static final int LATE_ARRIVAL_CELLS = 48;
    static final int WAIT_TICKS = 300;

    record Member(CommandSquadState state, ObjectiveAssignment proposal,
                  boolean available) { }
    record Decision(ObjectiveAssignment assignment, String reason) { }
    record Result(Map<Integer, Decision> decisions,
                  List<ConquestFrontSnapshot.GroupState> groups) { }
    private static final class Intent {
        ObjectiveAssignment target;
        int waitStarted = -1;
        boolean boundReleased;
    }
    private final Map<String, Intent> intents = new HashMap<>();

    /** Zone numbers are a topology publication's addresses, not persistent places. */
    void invalidateZoneTargets() {
        for (Intent intent : intents.values()) {
            if (intent.target == null || intent.target.kind() != AssignmentKind.CLEAR_ZONE) continue;
            intent.target = null;
        }
    }

    Result plan(int tick, List<Member> members,
                BiPredicate<CommandSquadState, ObjectiveAssignment> legal,
                ToDoubleFunction<CommandSquadState> forward) {
        return plan(tick, members, legal, forward, UnaryOperator.identity(),
                state -> state.anchorCellX() >= 0 && state.anchorCellY() >= 0);
    }

    Result plan(int tick, List<Member> members,
                BiPredicate<CommandSquadState, ObjectiveAssignment> legal,
                ToDoubleFunction<CommandSquadState> forward,
                UnaryOperator<ObjectiveAssignment> rebind,
                Predicate<CommandSquadState> rallyLegal) {
        Map<String, List<Member>> groups = new TreeMap<>();
        for (Member member : members) {
            CommandGroupIdentity identity = member.state().commandGroup();
            if (identity == null || member.state().aliveMembers() <= 0) continue;
            groups.computeIfAbsent(identity.groupId(), key -> new ArrayList<>()).add(member);
        }
        intents.keySet().retainAll(groups.keySet());
        Map<Integer, Decision> decisions = new TreeMap<>();
        List<ConquestFrontSnapshot.GroupState> picture = new ArrayList<>();
        for (Map.Entry<String, List<Member>> entry : groups.entrySet()) {
            List<Member> group = entry.getValue();
            group.sort(Comparator.comparingInt(member -> member.state().squadId()));
            CommandGroupIdentity identity = group.get(0).state().commandGroup();
            List<Member> assault = group.stream().filter(member -> member.available()
                    && !isHold(member.proposal())).toList();
            int holds = (int) group.stream().filter(member -> isHold(member.proposal())).count();
            Intent intent = intents.computeIfAbsent(entry.getKey(), key -> new Intent());
            if (intent.target != null) intent.target = rebind.apply(intent.target);
            String reason = identity.namedOfficer() ? "GROUP_NO_ACTIONABLE_TASK" : "ACTING_NCO_AUTONOMY";
            ObjectiveAssignment target = null;
            if (identity.namedOfficer() && !assault.isEmpty()) {
                // A quota-selected capture leads its group's support; support never adds capture slots.
                ObjectiveAssignment capture = assault.stream().map(Member::proposal)
                        .filter(assignment -> assignment != null
                                && assignment.kind() == AssignmentKind.SECURE_COMPOUND)
                        .findFirst().orElse(null);
                boolean useful = intent.target != null && assault.stream().anyMatch(member ->
                        legal.test(member.state(), intent.target));
                target = capture != null ? capture : useful ? intent.target
                        : assault.stream().map(Member::proposal).filter(assignment -> assignment != null)
                        .findFirst().orElse(null);
                reason = target == null ? "GROUP_NO_ACTIONABLE_TASK"
                        : useful && sameTarget(target, intent.target)
                        ? "GROUP_TASK_PRESERVED" : "GROUP_TASK_SELECTED";
                // Retargeting or rebuilding navigation cannot buy another wait:
                // only the body's real regrouping rearms a released bound.
                intent.target = target;
                if (target != null) {
                    ObjectiveAssignment chosen = target;
                    for (Member member : assault) {
                        if (member.proposal() != null
                                && member.proposal().kind() == AssignmentKind.SECURE_COMPOUND) continue;
                        if (!legal.test(member.state(), chosen)) continue;
                        if (chosen.kind() == AssignmentKind.ADVANCE_TRACK
                                || chosen.kind() == AssignmentKind.ATTACK_MOVE) continue;
                        decisions.put(member.state().squadId(), new Decision(
                                forSquad(chosen, member.state().squadId()), reason));
                    }
                    bound(tick, assault, intent, forward, rallyLegal, decisions);
                }
            }
            String task = holds > 0 ? assault.isEmpty() ? "HOLD" : "MIXED"
                    : target != null || group.stream().anyMatch(member -> member.proposal() != null)
                    ? "ASSAULT" : "UNASSIGNED";
            if (target == null) target = group.stream().map(Member::proposal)
                    .filter(assignment -> assignment != null).findFirst().orElse(null);
            picture.add(new ConquestFrontSnapshot.GroupState(identity,
                    group.stream().map(member -> member.state().squadId()).toList(), task,
                    target != null ? target.kind() : null,
                    target != null ? target.targetZoneId() : -1,
                    target != null ? target.targetCellX() : -1,
                    target != null ? target.targetCellY() : -1, reason,
                    group.stream().anyMatch(member -> {
                        Decision decision = decisions.get(member.state().squadId());
                        return decision != null && "GROUP_BOUNDED_RALLY".equals(decision.reason());
                    })));
        }
        return new Result(Map.copyOf(decisions), List.copyOf(picture));
    }

    private static void bound(int tick, List<Member> members, Intent intent,
                              ToDoubleFunction<CommandSquadState> forward,
                              Predicate<CommandSquadState> rallyLegal,
                              Map<Integer, Decision> decisions) {
        List<Member> quiet = members.stream().filter(member -> !member.state().localContact()
                && !member.state().underFireRecently() && !member.state().moraleBroken()
                && member.state().executionSuspension() == null
                && member.proposal() != null
                && member.proposal().kind() != AssignmentKind.SECURE_COMPOUND).toList();
        if (quiet.size() < 2) { intent.waitStarted = -1; return; }
        double lead = quiet.stream().mapToDouble(member -> forward.applyAsDouble(member.state()))
                .max().orElse(0);
        List<Member> body = quiet.stream().filter(member ->
                lead - forward.applyAsDouble(member.state()) <= LATE_ARRIVAL_CELLS).toList();
        if (body.size() < 2) { intent.waitStarted = -1; return; }
        double[] positions = body.stream().mapToDouble(member -> forward.applyAsDouble(member.state()))
                .sorted().toArray();
        double supported = positions[(positions.length - 1) / 2];
        if (lead - supported <= BOUND_CELLS) {
            intent.waitStarted = -1;
            intent.boundReleased = false; // A new bound requires actual regrouping first.
            return;
        }
        if (intent.boundReleased) return;
        if (intent.waitStarted < 0) intent.waitStarted = tick;
        if (tick - intent.waitStarted >= WAIT_TICKS) {
            intent.boundReleased = true;
            return;
        }
        for (Member member : body) {
            CommandSquadState state = member.state();
            if (forward.applyAsDouble(state) - supported <= BOUND_CELLS) continue;
            if (!rallyLegal.test(state)) continue;
            decisions.put(state.squadId(), new Decision(ObjectiveAssignment.advanceTrack(
                    state.squadId(), state.anchorCellX(), state.anchorCellY()), "GROUP_BOUNDED_RALLY"));
        }
    }

    static boolean isHold(ObjectiveAssignment assignment) {
        return assignment != null && (assignment.kind() == AssignmentKind.HOLD_NODE
                || assignment.kind() == AssignmentKind.DEFEND_AREA
                || assignment.kind() == AssignmentKind.DEFEND_SITE
                || assignment.kind() == AssignmentKind.DEFEND_TRACK);
    }

    static ObjectiveAssignment forSquad(ObjectiveAssignment target, int squadId) {
        if (target.kind() == AssignmentKind.SECURE_COMPOUND)
            return ObjectiveAssignment.clearZone(squadId, target.targetZoneId());
        return new ObjectiveAssignment(squadId, target.kind(), target.targetZoneId(),
                target.targetNode(), target.objectiveId(), target.targetCellX(),
                target.targetCellY(), target.targetRadiusCells());
    }

    private static boolean sameTarget(ObjectiveAssignment a, ObjectiveAssignment b) {
        return a == b || a != null && b != null && a.kind() == b.kind()
                && a.targetZoneId() == b.targetZoneId()
                && CommandFrameCopies.sameNodeIdentity(a.targetNode(), b.targetNode())
                && a.targetCellX() == b.targetCellX() && a.targetCellY() == b.targetCellY();
    }

}
