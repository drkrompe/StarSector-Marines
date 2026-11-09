package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.Phase;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.Role;
import com.dillon.starsectormarines.battle.command.ExtractionDefenseSnapshot.SquadIntent;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The scavenger crew's commander on a ruins expedition ({@code ruins-expedition.md}, Occupants, Scavengers). The crew
 * has two places it answers for: the dig at the vault, first, and its stash at the camp, second. Its posted squads
 * hold both and never leave; the patrol pool is what this commander moves.
 *
 * <p>It borrows the generic Extraction defenders' pieces ({@link ExtractionDefenderCommand}): the public vault and the
 * identity-free alarm as its picture, the alarm's limit of {@link ExtractionDefenderCommand#ALARM_RESPONSE_LIMIT}
 * squads, only fresh contacts as somewhere to send a squad, the same rally ring round a centre, and the held reserve
 * manning the garrisonables pressure reaches ({@link MissionGarrisons}). What is its own is the split of that alarm
 * between the dig and the stash ({@link #answer}). The dig's responders go onto fresh contacts near the vault, as
 * Extraction's do round its source; the stash's stand round the camp.
 */
public final class RuinsCrewCommand implements AutonomousMissionCommand<
        ExtractionDefenderCommandFrame, ExtractionDefenseSnapshot>, ReactiveDefenceSource {

    /** The issuer the setup claims the crew's patrols under. */
    public static final String STRATEGY_ID = "ruins-occupant";
    /**
     * How near the dig or the camp a fresh contact must be for the crew to answer for that place: a rifle's reach, so
     * a company shooting the camp up from range is trouble at the camp. UNTUNED.
     */
    static final int THREAT_RADIUS = 26;
    /** Squads the stash takes while the dig is quiet. */
    static final int STASH_RESPONSE = 2;

    /**
     * How many of the pool go to each place this pulse. One squad always walks the dig. An alarm at the dig takes up
     * to the alarm's limit; trouble at the camp alone takes up to {@link #STASH_RESPONSE} beside the dig's walker; both
     * at once leave the dig the larger share and the camp one squad. Never more than the alarm's limit in all.
     *
     * @param dig   squads sent to the dig, nearest the vault first; the first is the dig's guard
     * @param stash squads sent to the camp, nearest the camp first
     */
    record Answer(int dig, int stash) {
        Answer {
            if (dig < 0 || stash < 0 || dig + stash > ExtractionDefenderCommand.ALARM_RESPONSE_LIMIT) {
                throw new IllegalArgumentException("the alarm moves at most its limit");
            }
        }
    }

    static Answer answer(boolean terminal, boolean digAlarm, boolean stashThreat, int pool) {
        if (terminal || pool <= 0) return new Answer(0, 0);
        int limit = ExtractionDefenderCommand.ALARM_RESPONSE_LIMIT;
        int dig = !digAlarm ? 1 : stashThreat ? limit - 1 : limit;
        dig = Math.min(dig, pool);
        int stash = !stashThreat ? 0 : digAlarm ? 1 : STASH_RESPONSE;
        stash = Math.min(stash, Math.min(pool - dig, limit - dig));
        return new Answer(dig, stash);
    }

    private final Set<Integer> initialMobileSquads = new TreeSet<>();
    private final int campCellX;
    private final int campCellY;
    private final MissionGarrisons garrisons = new MissionGarrisons();
    private volatile ExtractionDefenseSnapshot defenseSnapshot;

    /**
     * @param mobileSquadIds the crew's patrols
     * @param campCellX      the crew's camp, where its stash is; its own ground, so it knows it
     */
    public RuinsCrewCommand(Set<Integer> mobileSquadIds, int campCellX, int campCellY) {
        if (mobileSquadIds != null) initialMobileSquads.addAll(mobileSquadIds);
        this.campCellX = campCellX;
        this.campCellY = campCellY;
    }

    public ExtractionDefenseSnapshot defenseSnapshot() { return defenseSnapshot; }

    @Override public Faction faction() { return Faction.DEFENDER; }
    @Override public String strategyId() { return STRATEGY_ID; }
    @Override public void afterCommit(CommandEffects effects) { garrisons.afterCommit(effects, faction()); }
    @Override public Map<String, Integer> reactiveDefenceFigures() { return ReactiveDefenceSource.figures(garrisons); }
    @Override public void publish(CommanderSnapshot<ExtractionDefenseSnapshot> snapshot) {
        defenseSnapshot = snapshot.detail();
    }

    @Override
    public CommandPlan<ExtractionDefenseSnapshot> plan(ExtractionDefenderCommandFrame frame) {
        ExtractionObjectiveFacts payload = frame.facts().payload();
        boolean terminal = payload.complete() || payload.failed();
        int digX = payload.sourceCellX(), digY = payload.sourceCellY();
        CommandTopology topology = frame.topology();
        List<CommandSquadState> pool = frame.squads().stream()
                .filter(squad -> squad.aliveMembers() > 0)
                .filter(squad -> initialMobileSquads.contains(squad.squadId()) || strategyOwned(squad.directive()))
                .filter(squad -> squad.directive() == null || strategyOwned(squad.directive())
                        || squad.directive().authority().priority() <= CommandAuthority.MISSION_COMMAND.priority())
                .toList();
        List<CommanderContact> contacts = ExtractionDefenderCommand.contacts(frame);
        int freshestContactTick = contacts.stream().mapToInt(CommanderContact::observedTick).max().orElse(-1);
        List<CommanderContact> actionable = ExtractionDefenderCommand.actionable(contacts, frame.tick());
        List<CommanderContact> nearDig = near(actionable, digX, digY);
        List<CommanderContact> nearCamp = near(actionable, campCellX, campCellY);
        boolean digAlarm = payload.alarmActive() || !nearDig.isEmpty();
        boolean stashThreat = !nearCamp.isEmpty();
        Answer answer = answer(terminal, digAlarm, stashThreat, pool.size());

        List<CommandSquadState> digGroup = nearest(pool, Set.of(), digX, digY, answer.dig(), topology);
        Set<Integer> sent = new HashSet<>();
        for (CommandSquadState squad : digGroup) sent.add(squad.squadId());
        List<CommandSquadState> stashGroup = nearest(pool, sent, campCellX, campCellY, answer.stash(), topology);
        for (CommandSquadState squad : stashGroup) sent.add(squad.squadId());

        List<CommandProposal> proposals = new ArrayList<>();
        List<SquadIntent> intents = new ArrayList<>();
        Map<Integer, MissionGarrisons.Offer> offers = new TreeMap<>();
        Set<Long> taken = new HashSet<>();
        for (int i = 0; i < digGroup.size(); i++) {
            CommandSquadState squad = digGroup.get(i);
            CommanderContact contact = i > 0 && !nearDig.isEmpty() ? nearDig.get((i - 1) % nearDig.size()) : null;
            Role role = i == 0 ? Role.SOURCE_GUARD : Role.ALARM_RESPONDER;
            String reason = i == 0 ? digAlarm ? "DIG_ALARM_SECURITY" : "ROUTINE_DIG_SECURITY" : "DIG_ALARM_RESPONSE";
            send(squad, i, contact, digX, digY, role, reason, "DIG_CONTACT_INTERDICTION", topology, taken,
                    proposals, intents, offers);
        }
        // The stash is guarded where it lies: a crew chasing a contact off its camp has left the stash, and a contact
        // within rifle reach of the camp may be standing on the company's landing.
        for (int i = 0; i < stashGroup.size(); i++) {
            send(stashGroup.get(i), i, null, campCellX, campCellY, Role.ALARM_RESPONDER, "STASH_ALARM_RESPONSE",
                    null, topology, taken, proposals, intents, offers);
        }

        int reserveCount = 0;
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0 || sent.contains(squad.squadId())) continue;
            if (!pool.contains(squad)) {
                Role role = squad.role() == UnitRole.GARRISON ? Role.AUTHORED_POST : Role.EXTERNAL;
                intents.add(new SquadIntent(squad.squadId(), role,
                        role == Role.AUTHORED_POST ? "AUTHORED_POST_PRESERVED" : "EXTERNAL_OWNERSHIP_PRESERVED",
                        squad.assignment() != null ? squad.assignment().kind() : null,
                        squad.assignment() != null ? squad.assignment().targetCellX() : -1,
                        squad.assignment() != null ? squad.assignment().targetCellY() : -1,
                        squad.localContact()));
                continue;
            }
            if (terminal) {
                proposals.add(strategyOwned(squad.directive())
                        ? CommandProposal.release(squad.squadId(), CommandAuthority.MISSION_COMMAND,
                        "OBJECTIVE_TERMINAL", CommandStabilityBreak.OBJECTIVE_COMPLETED)
                        : CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND,
                        "OBJECTIVE_TERMINAL"));
                intents.add(new SquadIntent(squad.squadId(), Role.RELEASED, "OBJECTIVE_TERMINAL", null, -1, -1,
                        squad.localContact()));
                continue;
            }
            proposals.add(CommandProposal.claim(squad.squadId(), CommandAuthority.MISSION_COMMAND,
                    "MOBILE_RESERVE_HELD", CommandStabilityBreak.CONTEXT_INVALIDATED));
            intents.add(new SquadIntent(squad.squadId(), Role.RESERVE, "MOBILE_RESERVE_HELD", null, -1, -1,
                    squad.localContact()));
            offers.put(squad.squadId(), MissionGarrisons.Offer.reserve());
            reserveCount++;
        }

        Map<Integer, MissionGarrisons.Manning> manning = terminal ? Map.of()
                : garrisons.plan(frame, frame.garrisonables(), offers, strategyId());
        if (!manning.isEmpty()) {
            proposals = new ArrayList<>(MissionGarrisons.rewrite(proposals, manning, frame.squads()));
            for (int i = 0; i < intents.size(); i++) {
                SquadIntent intent = intents.get(i);
                MissionGarrisons.Manning m = manning.get(intent.squadId());
                if (m == null) continue;
                intents.set(i, new SquadIntent(intent.squadId(), intent.role(), m.kind().reason(),
                        m.assignmentKind(), m.cellX(), m.cellY(), intent.localContact()));
            }
        }

        String phase = terminal ? "TERMINAL"
                : digAlarm && stashThreat ? "DIG_AND_STASH"
                : digAlarm ? "DIG_ALARM"
                : stashThreat ? "STASH_ALARM" : "ROUTINE";
        Phase detailPhase = terminal ? Phase.TERMINAL
                : digAlarm || stashThreat ? Phase.ALARM_INTERDICTION : Phase.ROUTINE_SECURITY;
        ExtractionDefenseSnapshot detail = new ExtractionDefenseSnapshot(
                frame.tick(), faction(), detailPhase, payload.payloadId(), payload.payloadName(), digX, digY,
                payload.alarmActive(), payload.alarmRaisedTick(), payload.complete(), payload.failed(),
                payload.failure(), contacts.size(), freshestContactTick, pool.size(), reserveCount, intents);
        List<String> objectives = List.of("dig=" + (digAlarm ? "alarm" : "quiet"),
                "stash=" + (stashThreat ? "threatened" : "quiet"));
        return new CommandPlan<>(faction(), strategyId(), phase, frame.tick(),
                frame.influence() != null ? frame.influence().updatedTick() : -1,
                pool.size(), reserveCount, objectives, proposals, detail);
    }

    /**
     * Sends one squad to a place: onto a fresh contact there when it has one and can reach round it, otherwise onto
     * the place itself. A squad that can reach neither is stranded.
     */
    private void send(CommandSquadState squad, int slot, CommanderContact contact, int placeX, int placeY,
                      Role role, String reason, String interdictionReason, CommandTopology topology,
                      Set<Long> taken, List<CommandProposal> proposals, List<SquadIntent> intents,
                      Map<Integer, MissionGarrisons.Offer> offers) {
        int[] rally = contact != null
                ? ExtractionDefenderCommand.rally(squad, slot, contact.cellX(), contact.cellY(), topology, taken)
                : null;
        boolean interdicting = rally != null;
        if (rally == null) rally = ExtractionDefenderCommand.rally(squad, slot, placeX, placeY, topology, taken);
        if (rally == null) {
            proposals.add(CommandProposal.claim(squad.squadId(), CommandAuthority.MISSION_COMMAND,
                    "INTERDICTION_TARGET_UNREACHABLE", CommandStabilityBreak.TARGET_UNREACHABLE));
            intents.add(new SquadIntent(squad.squadId(), Role.STRANDED, "INTERDICTION_TARGET_UNREACHABLE",
                    null, -1, -1, squad.localContact()));
            return;
        }
        taken.add(ExtractionDefenderCommand.cellKey(rally[0], rally[1]));
        if (interdicting && role != Role.SOURCE_GUARD) {
            role = Role.INTERDICTION;
            reason = interdictionReason;
        }
        ObjectiveAssignment assignment = ObjectiveAssignment.defendSite(squad.squadId(), rally[0], rally[1]);
        proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, reason,
                stabilityBreak(squad, assignment)));
        intents.add(new SquadIntent(squad.squadId(), role, reason, assignment.kind(), rally[0], rally[1],
                squad.localContact()));
        if (role != Role.INTERDICTION) offers.put(squad.squadId(), MissionGarrisons.Offer.duty(rally[0], rally[1]));
    }

    /** Fresh contacts within {@link #THREAT_RADIUS} of a place, in the order given. */
    static List<CommanderContact> near(List<CommanderContact> contacts, int x, int y) {
        int r2 = THREAT_RADIUS * THREAT_RADIUS;
        return contacts.stream().filter(c -> {
            int dx = c.cellX() - x, dy = c.cellY() - y;
            return dx * dx + dy * dy <= r2;
        }).toList();
    }

    /** Up to {@code count} of the pool not yet sent, nearest the place by route first, then by squad id. */
    private static List<CommandSquadState> nearest(List<CommandSquadState> pool, Set<Integer> sent, int x, int y,
                                                   int count, CommandTopology topology) {
        if (count <= 0) return List.of();
        return pool.stream().filter(squad -> !sent.contains(squad.squadId()))
                .sorted(Comparator.comparingInt((CommandSquadState squad) ->
                                topology.routeLength(squad.anchorCellX(), squad.anchorCellY(), x, y))
                        .thenComparingInt(CommandSquadState::squadId))
                .limit(count).toList();
    }

    private static boolean strategyOwned(CommandDirective directive) {
        return directive != null && STRATEGY_ID.equals(directive.issuer());
    }

    private static CommandStabilityBreak stabilityBreak(CommandSquadState squad, ObjectiveAssignment assignment) {
        CommandDirective incumbent = squad.directive();
        return incumbent == null || Objects.equals(incumbent.assignment(), assignment)
                ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED;
    }
}
