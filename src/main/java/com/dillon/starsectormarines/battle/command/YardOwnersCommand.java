package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.setup.YardOwnersSequence;
import com.dillon.starsectormarines.battle.story.YardOwnersProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Inspection duties consume disclosed work stages; local squads retain combat and player orders.
 *
 * <p>The constables are a party with places of their own (`garrisonables.md`, parties; {@link PartyPlaces}): each on a
 * standing duty mans the place its post stands in when its own picture says it is pressed, and the mission's place
 * directives ({@link #directives}) send the constable section still waiting for the operator entrance to hold the
 * yard outside the tooling hall's freight doors, where the prize crew's Cinder Steps group comes in, from the company's
 * first hold of dispatch until the entrance is taken.
 */
public final class YardOwnersCommand implements AutonomousMissionCommand<YardOwnersCommand.Frame, String>,
        ReactiveDefenceSource {
    public static final class Frame extends CommandFrame {
        final Phase phase;
        final int x, y, technician;
        final List<Integer> constables;
        final boolean dispatchVisited, entranceVisited;
        final boolean companyCover;
        final List<Integer> reserves;
        /** The map's garrisonables, for the constables' places; {@code null} for the other sides or with them switched off. */
        final GarrisonableRegistry places;
        public Frame(CommandFrame base, Phase phase, int x, int y, int technician, boolean companyCover, List<Integer> constables, boolean dispatchVisited, boolean entranceVisited, List<Integer> reserves) {
            this(base, phase, x, y, technician, companyCover, constables, dispatchVisited, entranceVisited, reserves, null);
        }
        Frame(CommandFrame base, Phase phase, int x, int y, int technician, boolean companyCover, List<Integer> constables, boolean dispatchVisited, boolean entranceVisited, List<Integer> reserves,
              GarrisonableRegistry places) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.phase = phase; this.x = x; this.y = y; this.technician = technician; this.companyCover = companyCover; this.constables = List.copyOf(constables);
            this.dispatchVisited = dispatchVisited; this.entranceVisited = entranceVisited; this.reserves = List.copyOf(reserves);
            this.places = places;
        }
    }
    public static CommandFrameDisclosure<Frame> disclosure(YardOwnersSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base = CommandFrame.freeze(view, faction, topology, assignments);
            var phase = sequence.progress().phase();
            var site = LatchkeyYard.SITES.get(phase.ordinal() < 3 ? phase.ordinal() : 1);
            var hall = LatchkeyYard.SITES.get(1);
            int x = faction == Faction.DEFENDER ? hall.x() : site.x();
            int y = faction == Faction.DEFENDER ? hall.y() : site.y();
            boolean covered = false;
            if (faction == Faction.ALLY) {
                var company = CommandFrame.freeze(view, Faction.MARINE, topology, assignments);
                boolean alarm = company.influence() != null
                        && WorkingPassageCommand.constructionThreat(company.influence().contacts(), x, y) != null;
                covered = !alarm && company.squads().stream().anyMatch(s -> s.aliveMembers() > 0
                        && Math.hypot(s.centroidX() - x, s.centroidY() - y) < 20);
            }
            return new Frame(base, sequence.progress().phase(), x, y, sequence.technicianSquadId(), covered, sequence.constableSquadIds(), sequence.progress().visited(0), sequence.progress().visited(2), sequence.reserveSquadIds(),
                    faction == Faction.ALLY && PartyPlaces.enabled() ? view.garrisonables() : null);
        };
    }
    /** The flag a pulse raises once the company has held dispatch: the moment the prize crew commits. */
    static final String DISPATCH_HELD = "dispatch-held";
    /** The flag a pulse raises once the company has held the operator entrance. */
    static final String ENTRANCE_HELD = "entrance-held";
    /** Outside the tooling hall's freight doors, in the yard the Cinder Steps group crosses to reach them. */
    static final int FREIGHT_DOORS_POST_X = LatchkeyYard.HALL_FREIGHT_DOORS.x(), FREIGHT_DOORS_POST_Y = LatchkeyYard.HALL_FREIGHT_DOORS.y() + 4;

    /**
     * The mission's place directives (`garrisonables.md`, place directives): the constable section waiting for the
     * operator entrance takes and holds the yard outside the hall's freight doors from the company's first hold of
     * dispatch, when the prize crew commits, until the company has the entrance and the section goes to keep it.
     */
    static List<PlaceDirective> directives() {
        return List.of(PlaceDirective.lock("freight-doors", Faction.ALLY,
                        PlaceTarget.region(FREIGHT_DOORS_POST_X, FREIGHT_DOORS_POST_Y, 6))
                .post(FREIGHT_DOORS_POST_X, FREIGHT_DOORS_POST_Y).radius(4)
                .when(PlaceTrigger.flag(DISPATCH_HELD)).until(PlaceTrigger.flag(ENTRANCE_HELD))
                .reason("YARD_OWNERS_HOLD_FREIGHT_DOORS").build());
    }

    private final Faction faction;
    private final PartyPlaces places;
    public YardOwnersCommand(Faction faction) {
        this.faction = faction;
        this.places = new PartyPlaces(faction,
                PlaceDirectives.forParty(PartyPlaces.enabled() ? directives() : List.of(), faction));
    }
    public static String issuer(Faction faction) { return "yard-owners:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }
    @Override public void afterCommit(CommandEffects effects) { places.afterCommit(effects); }
    @Override public Map<String, Integer> reactiveDefenceFigures() { return places.figures(); }
    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>(); int count = 0;
        if (faction == Faction.ALLY) places.begin(PlaceSignals.of(frame).flag(DISPATCH_HELD, frame.dispatchVisited)
                .flag(ENTRANCE_HELD, frame.entranceVisited).stage("phase", frame.phase.ordinal()));
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) { proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED")); continue; }
            int x = frame.x, y = frame.y, radius = 3;
            boolean advance = faction == Faction.DEFENDER;
            boolean standing = false;
            String reason = "YARD_OWNERS_" + frame.phase;
            if (faction == Faction.MARINE) {
                x += squad.squadId() % 2 == 0 ? -2 : 1;
                if (frame.influence() != null) {
                    var threat = WorkingPassageCommand.constructionThreat(frame.influence().contacts(), frame.x, frame.y);
                    if (threat != null) {
                        var point = WorkingPassageCommand.screenPoint(frame.x, frame.y, threat, squad.squadId() % 2 == 0 ? 1 : -1, old.assignment());
                        x = point[0]; y = point[1];
                    }
                }
            } else if (faction == Faction.ALLY) {
                if (squad.squadId() != frame.technician) {
                    int duty = frame.constables.indexOf(squad.squadId());
                    int siteIndex = duty == 0 ? 0 : 2;
                    if (duty == 0 ? frame.dispatchVisited : frame.entranceVisited) {
                        var site = LatchkeyYard.SITES.get(siteIndex); x = site.x(); y = site.y();
                    } else { x = duty == 0 ? 32 : 38; y = 59; }
                    places.own(squad);
                    // The section still waiting for the entrance is the freight doors' lock detachment.
                    PartyPlaces.Locked lock = duty == 1 ? places.lock(squad, 0, frame.places) : null;
                    if (lock != null) {
                        x = lock.order().targetCellX(); y = lock.order().targetCellY();
                        radius = lock.order().targetRadiusCells(); reason = lock.directive().reason();
                    } else standing = true;
                } else if (frame.phase.ordinal() < Phase.ENTRANCE.ordinal() || !frame.companyCover && frame.phase != Phase.INSPECTION) {
                    x = Math.round(squad.centroidX()); y = Math.round(squad.centroidY());
                } else { x = LatchkeyYard.SITES.get(1).x(); y = LatchkeyYard.SITES.get(1).y(); advance = true; radius = 2; }
            } else {
                // Each committed group goes for the place its ingress serves: the Cinder Steps group for the
                // hall's freight doors, the south-road group for the operator entrance and the gallery behind it.
                int group = frame.reserves.indexOf(squad.squadId());
                var site = LatchkeyYard.SITES.get(group >= 0 ? 1 + group % 2 : squad.squadId() % 2 == 0 ? 1 : 2);
                x = site.x(); y = site.y();
            }
            int best = Integer.MAX_VALUE, bx = Math.round(squad.centroidX()), by = Math.round(squad.centroidY());
            for (int cy = y - 5; cy <= y + 5; cy++) for (int cx = x - 5; cx <= x + 5; cx++) {
                int d = (cx-x)*(cx-x)+(cy-y)*(cy-y);
                if (d < best && frame.topology().isWalkable(cx, cy) && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(), cx, cy)) {
                    best = d; bx = cx; by = cy;
                }
            }
            var next = advance ? ObjectiveAssignment.attackMove(squad.squadId(), bx, by)
                    : ObjectiveAssignment.defendArea(squad.squadId(), bx, by, radius);
            if (old.assignment() != null && old.assignment().kind() == next.kind()
                    && old.assignment().targetCellX() == bx && old.assignment().targetCellY() == by
                    && old.assignment().targetRadiusCells() == next.targetRadiusCells()) next = old.assignment();
            if (standing) places.offerDuty(squad, bx, by);
            proposals.add(CommandProposal.assign(next, CommandAuthority.MISSION_COMMAND, reason,
                    next == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED)); count++;
        }
        if (faction == Faction.ALLY) proposals = new ArrayList<>(places.apply(frame, frame.places, proposals, strategyId()));
        return new CommandPlan<>(faction, strategyId(), "YARD_OWNERS_" + frame.phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), count, 0, List.of(frame.phase.name()), proposals, frame.phase.name());
    }
}
