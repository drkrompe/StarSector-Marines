package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.setup.LimitedWarrantSequence;
import com.dillon.starsectormarines.battle.story.LimitedWarrantProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LowQuay;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recovery duties consume disclosed work stages; local squads retain combat and player orders.
 *
 * <p>The constables are a party with places of their own (`garrisonables.md`, parties; {@link PartyPlaces}): on their
 * standing duty at the loading exit they man the place their post stands in when their own picture says it is
 * pressed, and the mission's place directive ({@link #directives}) locks them into the gatehouse beside it once the
 * company has the records and the rearguard commits, until the calibration load starts home: they take it and hold its
 * windows over the gate and the road to the crossing, and nothing reactive gives it back.
 */
public final class LimitedWarrantCommand implements AutonomousMissionCommand<LimitedWarrantCommand.Frame, String>,
        ReactiveDefenceSource {
    public static final class Frame extends CommandFrame {
        final Phase phase;
        final int x, y, technician;
        final boolean companyCover;
        /** The company's nearest known enemy near the lane crossing, or null. */
        final CommanderContact crossingThreat;
        /** The map's garrisonables, for the constables' places; {@code null} for the other sides or with them switched off. */
        final GarrisonableRegistry places;
        public Frame(CommandFrame base, Phase phase, int x, int y, int technician, boolean companyCover, CommanderContact crossingThreat) {
            this(base, phase, x, y, technician, companyCover, crossingThreat, null);
        }
        Frame(CommandFrame base, Phase phase, int x, int y, int technician, boolean companyCover, CommanderContact crossingThreat,
              GarrisonableRegistry places) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.phase = phase; this.x = x; this.y = y; this.technician = technician;
            this.companyCover = companyCover; this.crossingThreat = crossingThreat;
            this.places = places;
        }
    }
    public static CommandFrameDisclosure<Frame> disclosure(LimitedWarrantSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base = CommandFrame.freeze(view, faction, topology, assignments);
            // The rearguard knows the lane crossing every extraction must pass, never the hidden
            // company or package position.
            int x = faction == Faction.DEFENDER ? LowQuay.CROSS_X : sequence.targetX();
            int y = faction == Faction.DEFENDER ? LowQuay.CROSS_Y : sequence.targetY();
            boolean covered = false;
            CommanderContact crossing = null;
            if (faction != Faction.DEFENDER) {
                var company = faction == Faction.MARINE ? base : CommandFrame.freeze(view, Faction.MARINE, topology, assignments);
                // The company's own picture of the lane crossing every route in the compound passes.
                if (company.influence() != null) crossing = crossingThreat(company.influence().contacts());
                if (faction == Faction.ALLY) {
                    boolean alarm = company.influence() != null
                            && WorkingPassageCommand.constructionThreat(company.influence().contacts(), x, y) != null;
                    covered = !alarm && company.squads().stream().anyMatch(s -> s.aliveMembers() > 0
                            && Math.hypot(s.centroidX() - x, s.centroidY() - y) < 20);
                }
            }
            return new Frame(base, sequence.progress().phase(), x, y, sequence.technicianSquadId(), covered, crossing,
                    faction == Faction.ALLY && PartyPlaces.enabled() ? view.garrisonables() : null);
        };
    }
    /** Radius around the crossing covering the lane, both gallery vestibules and their first stretch. */
    static final int CROSSING_THREAT_CELLS = 16;
    static CommanderContact crossingThreat(List<CommanderContact> contacts) {
        CommanderContact nearest = null; double best = CROSSING_THREAT_CELLS * CROSSING_THREAT_CELLS;
        for (var contact : contacts) {
            double dx = contact.cellX() - LowQuay.CROSS_X, dy = contact.cellY() - LowQuay.CROSS_Y, distance = dx*dx + dy*dy;
            if (distance <= best && (nearest == null || distance < best || contact.unitId() < nearest.unitId())) { nearest = contact; best = distance; }
        }
        return nearest;
    }
    /**
     * The mission's place directives (`garrisonables.md`, place directives and locked positions): once the company
     * has the records and the rearguard commits, the constables take and hold the gatehouse beside their post, by its
     * own manning order, until the load starts home.
     */
    static List<PlaceDirective> directives() {
        return List.of(PlaceDirective.lock("gatehouse", Faction.ALLY,
                        PlaceTarget.region(LowQuay.GATEHOUSE_X, LowQuay.GATEHOUSE_Y, 3))
                .when(PlaceTrigger.stageAtLeast("phase", Phase.CALIBRATION.ordinal()))
                .until(PlaceTrigger.stageAtLeast("phase", Phase.EGRESS.ordinal()))
                .reason("LIMITED_WARRANT_HOLD_GATEHOUSE").build());
    }

    private final Faction faction;
    private final PartyPlaces places;
    public LimitedWarrantCommand(Faction faction) {
        this.faction = faction;
        this.places = new PartyPlaces(faction,
                PlaceDirectives.forParty(PartyPlaces.enabled() ? directives() : List.of(), faction));
    }
    public static String issuer(Faction faction) { return "limited-warrant:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }
    @Override public void afterCommit(CommandEffects effects) { places.afterCommit(effects); }
    @Override public Map<String, Integer> reactiveDefenceFigures() { return places.figures(); }
    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>(); int count = 0;
        if (faction == Faction.ALLY) places.begin(PlaceSignals.of(frame).stage("phase", frame.phase.ordinal()));
        // With two or more squads ashore, the highest-numbered one holds the lane crossing while the
        // supplier's technician walks in; the rest cover the calibration shop.
        int routeSquad = -1, alive = 0;
        for (var squad : frame.squads()) if (squad.aliveMembers() > 0) { alive++; routeSquad = Math.max(routeSquad, squad.squadId()); }
        if (alive < 2) routeSquad = -1;
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) { proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED")); continue; }
            int x = frame.x, y = frame.y, radius = 3;
            boolean advance = faction == Faction.DEFENDER;
            boolean standing = false;
            PartyPlaces.Locked locked = null;
            String reason = "LIMITED_WARRANT_" + frame.phase;
            if (faction == Faction.MARINE && frame.phase == Phase.CALIBRATION && squad.squadId() == routeSquad) {
                // Hold the crossing; clear a known enemy near it rather than waiting it out.
                if (frame.crossingThreat != null) { x = frame.crossingThreat.cellX(); y = frame.crossingThreat.cellY(); advance = true; }
                else { x = LowQuay.CROSS_X; y = LowQuay.CROSS_Y; radius = 4; }
            } else if (faction == Faction.MARINE) {
                if (frame.phase != Phase.EGRESS) x += squad.squadId() % 2 == 0 ? -2 : 1;
                if (frame.influence() != null) {
                    var threat = WorkingPassageCommand.constructionThreat(frame.influence().contacts(), frame.x, frame.y);
                    if (threat != null) {
                        var point = WorkingPassageCommand.screenPoint(frame.x, frame.y, threat, squad.squadId() % 2 == 0 ? 1 : -1, old.assignment());
                        x = point[0]; y = point[1];
                    }
                }
            } else if (faction == Faction.ALLY) {
                if (squad.squadId() != frame.technician) {
                    x = 37; y = 59; radius = 5;
                    places.own(squad);
                    PartyPlaces.Locked lock = places.lock(squad, 0, frame.places);
                    if (lock != null) locked = lock;
                    else standing = true;
                }
                else if (frame.phase == Phase.EGRESS) { x = LowQuay.LANDING_X; y = LowQuay.LANDING_Y; advance = true; }
                else if (frame.phase == Phase.RECORDS || !frame.companyCover || frame.crossingThreat != null) {
                    x = Math.round(squad.centroidX()); y = Math.round(squad.centroidY());
                } else { advance = true; radius = 2; }
            }
            int best = Integer.MAX_VALUE, bx = Math.round(squad.centroidX()), by = Math.round(squad.centroidY());
            for (int cy = y - 5; cy <= y + 5; cy++) for (int cx = x - 5; cx <= x + 5; cx++) {
                int d = (cx-x)*(cx-x)+(cy-y)*(cy-y);
                if (d < best && frame.topology().isWalkable(cx, cy) && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(), cx, cy)) {
                    best = d; bx = cx; by = cy;
                }
            }
            var next = locked != null ? locked.order() : advance ? ObjectiveAssignment.attackMove(squad.squadId(), bx, by)
                    : ObjectiveAssignment.defendArea(squad.squadId(), bx, by, radius);
            if (locked != null) reason = locked.directive().reason();
            if (locked != null ? next.equals(old.assignment()) : old.assignment() != null && old.assignment().kind() == next.kind()
                    && old.assignment().targetCellX() == bx && old.assignment().targetCellY() == by
                    && old.assignment().targetRadiusCells() == next.targetRadiusCells()) next = old.assignment();
            if (standing) places.offerDuty(squad, bx, by);
            proposals.add(CommandProposal.assign(next, CommandAuthority.MISSION_COMMAND, reason,
                    next == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED)); count++;
        }
        if (faction == Faction.ALLY) proposals = new ArrayList<>(places.apply(frame, frame.places, proposals, strategyId()));
        return new CommandPlan<>(faction, strategyId(), "LIMITED_WARRANT_" + frame.phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), count, 0, List.of(frame.phase.name()), proposals, frame.phase.name());
    }
}
