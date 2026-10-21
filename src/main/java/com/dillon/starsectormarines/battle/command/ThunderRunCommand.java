package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.command.compound.CompoundService.CompoundState;
import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Separate assault, stable militia holds, and a finite counterattack pool. A pool squad holding home or holding its
 * ground mans the places near it, within the story share ({@link StoryGarrisons}); one in the push goes as authored.
 *
 * <p>The militia's holds are locked positions (`garrisonables.md`, locked positions; {@link #militiaLocks}): the convoy
 * staging from the start, and each road site from the first pulse the company holds it, never let go. Detachments fill
 * them oldest first and the surplus reinforces the newest gain, each on its own post ({@link PartyPlaces}).
 */
public final class ThunderRunCommand implements AutonomousMissionCommand<ThunderRunCommandFrame, ThunderRunCommand.Picture>,
        ReactiveDefenceSource {
    public record Duty(int squadId, int site, String reason) {}
    public record Picture(List<Duty> duties) { public Picture { duties = List.copyOf(duties); } }
    private final Faction faction;
    private final RelayNineTactics tactics = new RelayNineTactics();
    private final Map<Integer, Integer> detachments = new LinkedHashMap<>();
    private volatile Picture picture;
    private final StoryGarrisons garrisons = new StoryGarrisons();
    /** The militia's locked positions; empty for the other sides. */
    private final PartyPlaces places;
    /** The defender's finite pool on the shared counter-attack (`conquest-command.md`, Defender command); {@code null} for the other sides. */
    private final SiteCounterattack counter;
    public ThunderRunCommand(Faction faction) {
        if (faction != Faction.MARINE && faction != Faction.ALLY && faction != Faction.DEFENDER)
            throw new IllegalArgumentException("Thunder Run requires a fighting faction");
        this.faction = faction;
        this.counter = faction == Faction.DEFENDER ? new SiteCounterattack(5) : null;
        this.places = new PartyPlaces(faction, PlaceDirectives.forParty(militiaLocks(), faction));
    }

    /** The flag a pulse raises once the company has held road site {@code site}: public history, never lowered. */
    static String gained(int site) { return "gained:" + site; }

    /**
     * The militia's locked positions: the convoy staging, locked from the start and let go once the company has any
     * road site, and one lock per road site, locked once the company has held it and never let go. Keyed by
     * site; the posts are the militia's own ({@link ThunderRunCompound#militiaPosts}).
     */
    static List<PlaceDirective> militiaLocks() {
        List<PlaceDirective> out = new ArrayList<>();
        PlaceTrigger anyGain = PlaceTrigger.anyOf(PlaceTrigger.flag(gained(1)), PlaceTrigger.flag(gained(2)),
                PlaceTrigger.flag(gained(3)));
        for (int site = 0; site < ThunderRunCompound.SITE_NAMES.size(); site++) {
            var posts = ThunderRunCompound.militiaPosts(site);
            var lock = PlaceDirective.lock("militia-" + site, Faction.ALLY,
                            PlaceTarget.region(posts.get(0).x(), posts.get(0).y(), 6))
                    .key(site).radius(5).reason(site == 0 ? "FREIGHT_COVER_LANDING" : "FREIGHT_HOLD_GAIN")
                    .when(site == 0 ? PlaceTrigger.always() : PlaceTrigger.flag(gained(site)))
                    .until(site == 0 ? anyGain : PlaceTrigger.never());
            for (var post : posts) lock.post(post.x(), post.y());
            out.add(lock.build());
        }
        return out;
    }
    public static String issuer(Faction faction) { return "thunder-run:" + faction; }
    @Override public Faction faction() { return faction; }
    @Override public String strategyId() { return issuer(faction); }
    public Picture picture() { return picture; }
    @Override public void publish(CommanderSnapshot<Picture> snapshot) { picture = snapshot.detail(); }
    @Override public void afterCommit(CommandEffects effects) {
        garrisons.afterCommit(effects, faction);
        places.afterCommit(effects);
    }
    @Override public Map<String, Integer> reactiveDefenceFigures() {
        return faction == Faction.ALLY ? places.figures() : garrisons.figures();
    }
    @Override public CommandPlan<Picture> plan(ThunderRunCommandFrame frame) {
        var sites = frame.facts().compounds();
        var states = sites.stream().map(ConquestCommandFacts.Compound::state).toList();
        tactics.observe(states);
        if (!places.directives().isEmpty()) {
            PlaceSignals signals = PlaceSignals.of(frame);
            for (int i = 1; i < states.size(); i++) signals.flag(gained(i), tactics.gained(i));
            places.begin(signals);
        }
        List<CommandProposal> proposals = new ArrayList<>();
        List<Duty> duties = new ArrayList<>();
        Map<Integer, SiteCounterattack.Duty> retake = counter != null && PlaceCounterattack.isEnabled()
                ? counter.planRoadSites(frame.tick(), sites, owned(frame), frame.squads(), frame.influence(), frame.topology(), "FREIGHT")
                : null;
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            if (old == null || old.authority() != CommandAuthority.MISSION_COMMAND || !strategyId().equals(old.issuer())) {
                proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED"));
                continue;
            }
            int detachment = detachments.computeIfAbsent(squad.squadId(), id -> detachments.size());
            PartyPlaces.Locked lock = faction == Faction.ALLY
                    ? places.lock(squad, detachment, frame.facts().garrisonables()) : null;
            int site = faction == Faction.MARINE ? tactics.assaultTarget(states)
                    : faction == Faction.DEFENDER ? tactics.counterTarget(states)
                    : lock != null ? lock.directive().key() : tactics.militiaTarget(detachment);
            boolean hold = faction == Faction.MARINE && frame.holdSite() > 0
                    && states.get(frame.holdSite()) == CompoundState.MARINE_HELD
                    && site >= frame.holdSite();
            if (hold) site = frame.holdSite();
            boolean corridorOpen = faction == Faction.MARINE
                    && states.stream().allMatch(state -> state == CompoundState.MARINE_HELD);
            boolean companyDefense = hold || corridorOpen;
            var place = sites.get(site);
            SiteCounterattack.Duty duty = retake == null ? null : retake.get(squad.squadId());
            if (faction == Faction.DEFENDER) garrisons.own(squad);
            if (duty != null) {
                ObjectiveAssignment assignment = same(old.assignment(), duty.assignment()) ? old.assignment() : duty.assignment();
                if (duty.reason().endsWith(SiteCounterattack.HOLD_HOME) || duty.reason().endsWith(SiteCounterattack.HOLD_GROUND))
                    garrisons.offerDuty(squad, duty.assignment().targetCellX(), duty.assignment().targetCellY());
                proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, duty.reason(),
                        assignment == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
                duties.add(new Duty(squad.squadId(), site, duty.reason()));
                continue;
            }
            String reason = hold ? "FREIGHT_DEFEND_DISPATCH_WINDOW" : corridorOpen ? "FREIGHT_SCREEN_FINAL_CROSSING"
                    : faction == Faction.MARINE ? "FREIGHT_SECURE_SITE"
                    : faction == Faction.DEFENDER ? "FREIGHT_RETAKE_GAIN"
                    : site == 0 ? "FREIGHT_COVER_LANDING" : "FREIGHT_HOLD_GAIN";
            if (lock != null) reason = lock.directive().reason();
            var post = faction == Faction.ALLY ? ThunderRunCompound.militiaPost(site, detachment)
                    : ThunderRunCompound.companyPost(site, detachment);
            ObjectiveAssignment assignment = lock != null ? lock.order()
                    : faction == Faction.MARINE && !companyDefense
                    ? ObjectiveAssignment.secureCompound(squad.squadId(), place.captureZoneId(), place.node())
                    : ObjectiveAssignment.defendArea(squad.squadId(),
                            faction == Faction.DEFENDER ? place.captureCellX() : post.x(),
                            faction == Faction.DEFENDER ? place.captureCellY() : post.y(), 5);
            if (same(old.assignment(), assignment)) assignment = old.assignment();
            proposals.add(CommandProposal.assign(assignment, CommandAuthority.MISSION_COMMAND, reason,
                    assignment == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
            duties.add(new Duty(squad.squadId(), site, reason));
        }
        if (faction == Faction.DEFENDER) proposals = garrisons.apply(frame, frame.facts().garrisonables(), proposals, strategyId());
        return new CommandPlan<>(faction, strategyId(), faction == Faction.ALLY ? "HOLD_GAINS"
                : faction == Faction.MARINE ? "OPEN_FREIGHT_CORRIDOR" : "FINITE_COUNTERATTACK", frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), duties.size(), 0,
                summaries(duties), proposals, new Picture(duties));
    }
    private List<String> summaries(List<Duty> duties) {
        List<String> out = new ArrayList<>(duties.stream().map(d -> ThunderRunCompound.SITE_NAMES.get(d.site())).distinct().toList());
        if (counter != null && PlaceCounterattack.isEnabled()) out.addAll(counter.notes());
        return out;
    }
    /** The squads this commander moves: alive and under its own mission directive. */
    private List<CommandSquadState> owned(ThunderRunCommandFrame frame) {
        List<CommandSquadState> out = new ArrayList<>();
        for (var squad : frame.squads()) {
            var old = squad.directive();
            if (squad.aliveMembers() > 0 && old != null && old.authority() == CommandAuthority.MISSION_COMMAND
                    && strategyId().equals(old.issuer())) out.add(squad);
        }
        return out;
    }
    private static boolean same(ObjectiveAssignment a, ObjectiveAssignment b) {
        return a != null && a.kind() == b.kind() && a.targetZoneId() == b.targetZoneId()
                && a.targetCellX() == b.targetCellX() && a.targetCellY() == b.targetCellY()
                && a.targetRadiusCells() == b.targetRadiusCells()
                && CommandFrameCopies.sameNodeIdentity(a.targetNode(), b.targetNode());
    }
}
