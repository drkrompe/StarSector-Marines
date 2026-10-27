package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment.FireMission;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.setup.TermsOfProtectionSequence;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Phase;
import com.dillon.starsectormarines.battle.story.TermsOfProtectionProgress.Route;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LatchkeyYard;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Duty assignment for Terms of Protection. Each side reads only what it is entitled to: the
 * company its own beliefs and the public work stages, the employer parties the party's own
 * movement and whether company infantry is covering the hall, and Greyhook its own authored
 * ingress and objectives. Local squads keep combat; player orders keep precedence.
 *
 * <p>The route changes the company's problem and nothing else. Under House Custody the estate
 * guards hold the Cinder Steps gate, so a company section is free for the mortar once rounds
 * are actually seen falling; under the Yard Compact a section has to cover that gate itself and
 * the mortar waits until the assaults are spent.
 */
public final class TermsOfProtectionCommand implements AutonomousMissionCommand<TermsOfProtectionCommand.Frame, String> {
    /** Inside the wall on the Cinder Steps gate: the approach a company section covers under the compact. */
    static final int GATE_COVER_X = 120, GATE_COVER_Y = 43;
    /** On the lane beyond the gate, where a section searching for a reported mortar first looks across the terraces. */
    static final int MORTAR_SEARCH_X = 138, MORTAR_SEARCH_Y = 50;
    static final int DISPATCH_X = 58, DISPATCH_Y = 68;
    static final int HALL_X = 93, HALL_Y = 72;

    public static final class Frame extends CommandFrame {
        final Phase phase;
        final Route route;
        final int escortX, escortY;
        final TermsOfProtectionSequence.Parties parties;
        final List<Integer> pell;
        final int mortar, lane;
        final boolean mortarReported, mortarWithdrawing, hallCovered, assaultsSpent, fieldClear;
        final List<CommanderContact> contacts;
        Frame(CommandFrame base, TermsOfProtectionSequence s, boolean hallCovered, boolean assaultsSpent) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            phase = s.progress().phase(); route = s.route(); escortX = s.escortRallyX(); escortY = s.escortRallyY();
            parties = s.parties(); pell = s.pellSquadIds(); mortar = s.mortarSquadId(); lane = s.laneSquadId();
            mortarReported = s.mortarReported(); mortarWithdrawing = s.mortarWithdrawing();
            this.hallCovered = hallCovered; this.assaultsSpent = assaultsSpent;
            fieldClear = s.fieldClear();
            contacts = base.influence() == null ? List.of() : base.influence().contacts();
        }
    }

    public static CommandFrameDisclosure<Frame> disclosure(TermsOfProtectionSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base = CommandFrame.freeze(view, faction, topology, assignments);
            boolean covered = false, spent = false;
            if (faction == Faction.ALLY) {
                // The party is told only whether company infantry stands in the hall, never where Greyhook is.
                var company = CommandFrame.freeze(view, Faction.MARINE, topology, assignments);
                covered = company.squads().stream().anyMatch(s -> s.aliveMembers() > 0
                        && Math.hypot(s.centroidX() - HALL_X, s.centroidY() - HALL_Y) < 14);
            }
            if (faction == Faction.MARINE) {
                // Spent means the company has seen both assault waves commit and has no belief left about them.
                spent = sequence.pellSighted() && sequence.laneSighted()
                        && (base.influence() == null || base.influence().contacts().stream()
                        .noneMatch(c -> !c.structure() && Math.hypot(c.cellX() - LatchkeyYard.MORTAR_POST.x(), c.cellY() - LatchkeyYard.MORTAR_POST.y()) > 12));
            }
            return new Frame(base, sequence, covered, spent);
        };
    }

    private final Faction faction;
    public TermsOfProtectionCommand(Faction faction) { this.faction = faction; }
    public static String issuer(Faction faction) { return "terms-of-protection:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }

    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>(); int count = 0;
        var company = new ArrayList<Integer>();
        if (faction == Faction.MARINE) for (var squad : frame.squads()) if (squad.aliveMembers() > 0) company.add(squad.squadId());
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) { proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED")); continue; }
            Duty duty = switch (faction) {
                case MARINE -> companyDuty(frame, company.indexOf(squad.squadId()), Math.round(squad.centroidX()), Math.round(squad.centroidY()));
                case ALLY -> allyDuty(frame, squad.squadId(), Math.round(squad.centroidX()), Math.round(squad.centroidY()));
                default -> greyhookDuty(frame, squad.squadId(), squad.centroidX(), squad.centroidY());
            };
            int best = Integer.MAX_VALUE, bx = Math.round(squad.centroidX()), by = Math.round(squad.centroidY());
            for (int cy = duty.y - 5; cy <= duty.y + 5; cy++) for (int cx = duty.x - 5; cx <= duty.x + 5; cx++) {
                int d = (cx - duty.x) * (cx - duty.x) + (cy - duty.y) * (cy - duty.y);
                if (d < best && frame.topology().isWalkable(cx, cy) && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(), cx, cy)) {
                    best = d; bx = cx; by = cy;
                }
            }
            ObjectiveAssignment next = switch (duty.kind) {
                case ADVANCE -> ObjectiveAssignment.attackMove(squad.squadId(), bx, by);
                case WITHDRAW -> ObjectiveAssignment.withdraw(squad.squadId(), bx, by);
                case HOLD -> ObjectiveAssignment.defendArea(squad.squadId(), bx, by, duty.radius);
            };
            if (duty.mission != null) next = next.withFireMission(duty.mission);
            if (old.assignment() != null && old.assignment().kind() == next.kind()
                    && old.assignment().targetCellX() == bx && old.assignment().targetCellY() == by
                    && old.assignment().targetRadiusCells() == next.targetRadiusCells()
                    && Objects.equals(old.assignment().fireMission(), next.fireMission())) next = old.assignment();
            proposals.add(CommandProposal.assign(next, CommandAuthority.MISSION_COMMAND, "TERMS_OF_PROTECTION_" + frame.phase,
                    next == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED)); count++;
        }
        return new CommandPlan<>(faction, strategyId(), "TERMS_OF_PROTECTION_" + frame.phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), count, 0, List.of(frame.phase.name()), proposals, frame.phase.name());
    }

    private enum Kind { HOLD, ADVANCE, WITHDRAW }
    private record Duty(Kind kind, int x, int y, int radius, FireMission mission) {
        static Duty hold(int x, int y, int radius) { return new Duty(Kind.HOLD, x, y, radius, null); }
        static Duty advance(int x, int y) { return new Duty(Kind.ADVANCE, x, y, 3, null); }
    }

    /** The company's duty by its rank among living squads; a squad ordered by the player keeps that order. */
    static Duty companyDuty(Frame f, int rank, int at, int atY) {
        switch (f.phase) {
            case ESCORT: return Duty.hold(f.escortX + (rank % 2 == 0 ? -2 : 2), f.escortY + (rank - 1) * 3, 4);
            case SIGNING: return rank == 1 ? Duty.hold(HALL_X, HALL_Y, 5) : Duty.hold(DISPATCH_X + rank * 3 - 3, DISPATCH_Y - 6, 5);
            default: break;
        }
        CommanderContact mortar = mortarContact(f.contacts);
        boolean counterBattery = f.mortarReported || mortar != null;
        if (rank == 0) return Duty.hold(HALL_X, HALL_Y, 6);
        if (f.fieldClear) return Duty.hold(DISPATCH_X + rank * 2 - 2, DISPATCH_Y - 6, 5);
        // After both assault waves are spent, every section beyond the hall goes after whatever is left.
        if (f.assaultsSpent || f.phase == Phase.DEFENCE) {
            CommanderContact nearest = nearest(f.contacts, at, atY);
            if (nearest != null) return Duty.advance(nearest.cellX(), nearest.cellY());
            if (counterBattery) return Duty.advance(MORTAR_SEARCH_X, MORTAR_SEARCH_Y);
            return rank == 1 ? Duty.hold(DISPATCH_X, DISPATCH_Y - 6, 5) : Duty.hold(GATE_COVER_X, GATE_COVER_Y, 5);
        }
        if (f.route == Route.HOUSE_CUSTODY) {
            // The guards hold the lane on Dormer's terms: the third section is free.
            if (rank == 1) return Duty.hold(DISPATCH_X, DISPATCH_Y - 6, 5);
            if (rank == 2 && counterBattery)
                return mortar != null ? Duty.advance(mortar.cellX(), mortar.cellY()) : Duty.advance(MORTAR_SEARCH_X, MORTAR_SEARCH_Y);
            return rank == 2 ? Duty.hold(HALL_X, 89, 5) : Duty.hold(DISPATCH_X, DISPATCH_Y - 6, 5);
        }
        // The compact: nobody else holds the gate, so one section covers it and the mortar waits.
        if (rank == 1) return Duty.hold(GATE_COVER_X, GATE_COVER_Y, 5);
        return Duty.hold(DISPATCH_X, DISPATCH_Y - 6, 5);
    }

    static Duty allyDuty(Frame f, int squad, int at, int atY) {
        var p = f.parties;
        boolean escorting = f.phase == Phase.ESCORT;
        if (squad == p.constables()) return Duty.hold(88, 13, 3);
        if (escorting) return Duty.hold(f.escortX - 4, f.escortY, 3);
        if (squad == p.representatives()) return Duty.hold(58, 70, 2);
        if (squad == p.guards()) return Duty.hold(LatchkeyYard.GUARD_POST.x(), LatchkeyYard.GUARD_POST.y(), 3);
        if (squad == p.fitters()) return Duty.hold(93, 80, 4);
        if (squad == p.technicians()) {
            if (f.phase == Phase.SIGNING) return Duty.hold(61, 68, 2);
            // The work waits for company infantry in the hall; once started the crew stays at the bench.
            if (f.hallCovered || f.phase == Phase.DEFENCE || insideCalibration(at, atY))
                return new Duty(Kind.ADVANCE, LatchkeyYard.COMMISSIONING.x(), LatchkeyYard.COMMISSIONING.y(), 1, null);
            return Duty.hold(61, 68, 2);
        }
        return Duty.hold(at, atY, 3);
    }

    static Duty greyhookDuty(Frame f, int squad, float x, float y) {
        int section = f.pell.indexOf(squad);
        // Reed wants his levy records at dispatch and the association's work in the hall stopped.
        if (section == 0) return Duty.advance(DISPATCH_X, 70);
        if (section == 1) return Duty.advance(HALL_X, HALL_Y);
        if (squad == f.mortar) {
            if (f.mortarWithdrawing) return new Duty(Kind.WITHDRAW, LatchkeyYard.WIDTH - 3, 43, 3, null);
            var post = LatchkeyYard.MORTAR_POST;
            return new Duty(Kind.HOLD, post.x(), post.y(), 1, new FireMission(HALL_X, HALL_Y, TermsOfProtectionSequence.MORTAR_MISSION_RADIUS));
        }
        if (squad == f.lane) return Duty.advance(HALL_X, 89);
        return Duty.advance(HALL_X, HALL_Y);
    }

    private static boolean insideCalibration(int x, int y) { return LatchkeyYard.insideCalibrationRoom(x, y); }

    /** A believed contact at the terraces' pit: the company has actually located the tube or its crew. */
    static CommanderContact mortarContact(List<CommanderContact> contacts) {
        CommanderContact found = null;
        for (var c : contacts) {
            if (c.structure()) continue;
            if (Math.hypot(c.cellX() - LatchkeyYard.MORTAR_POST.x(), c.cellY() - LatchkeyYard.MORTAR_POST.y()) <= 10
                    && (found == null || c.unitId() < found.unitId())) found = c;
        }
        return found;
    }

    private static CommanderContact nearest(List<CommanderContact> contacts, int x, int y) {
        CommanderContact best = null; double distance = Double.MAX_VALUE;
        for (var c : contacts) {
            double d = Math.hypot(c.cellX() - x, c.cellY() - y);
            if (d < distance || d == distance && best != null && c.unitId() < best.unitId()) { best = c; distance = d; }
        }
        return best;
    }
}
