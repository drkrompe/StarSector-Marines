package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.setup.WaterUnderSealSequence;
import com.dillon.starsectormarines.battle.setup.WaterUnderSealRoadExit;
import com.dillon.starsectormarines.battle.command.influence.CommanderContact;
import com.dillon.starsectormarines.battle.story.WaterUnderSealProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.NorthWeir;
import java.util.ArrayList;
import java.util.List;

/** Paired weir command uses public work stages and own-force geometry only. */
public final class WaterUnderSealCommand implements AutonomousMissionCommand<WaterUnderSealCommand.Frame, String> {
    public static final class Frame extends CommandFrame {
        final Phase phase;
        final int x, y, technicians;
        final List<Integer> militia;
        final boolean companyCover;
        final List<CommanderContact> contacts;
        Frame(CommandFrame base, Phase phase, int x, int y, int technicians, List<Integer> militia, boolean companyCover) {
            this(base,phase,x,y,technicians,militia,companyCover,base.influence() == null ? List.of() : base.influence().contacts());
        }
        Frame(CommandFrame base, Phase phase, int x, int y, int technicians, List<Integer> militia, boolean companyCover, List<CommanderContact> contacts) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.phase = phase; this.x = x; this.y = y; this.technicians = technicians;
            this.militia = List.copyOf(militia); this.companyCover = companyCover; this.contacts = List.copyOf(contacts);
        }
    }
    public static CommandFrameDisclosure<Frame> disclosure(WaterUnderSealSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base = CommandFrame.freeze(view, faction, topology, assignments);
            int x = sequence.targetX(), y = sequence.targetY();
            boolean covered = true;
            if (faction == Faction.ALLY) {
                var company = CommandFrame.freeze(view, Faction.MARINE, topology, assignments);
                // The repair watch receives a bounded friendly radio alarm, never hostile identities or cells.
                boolean alarm = company.influence() != null
                        && WorkingPassageCommand.constructionThreat(company.influence().contacts(), x, y) != null;
                covered = !alarm && company.squads().stream().anyMatch(squad -> squad.aliveMembers() > 0
                        && Math.hypot(squad.centroidX() - x, squad.centroidY() - y) < 22);
            }
            return new Frame(base, sequence.progress().phase(), x, y, sequence.technicianSquadId(), sequence.militiaSquadIds(), covered);
        };
    }
    private final Faction faction;
    public WaterUnderSealCommand(Faction faction) { this.faction = faction; }
    public static String issuer(Faction faction) { return "water-under-seal:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) {}
    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>(); int count = 0;
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) { proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED")); continue; }
            int x = frame.x, y = frame.y, radius = 4;
            boolean advance = faction == Faction.DEFENDER;
            boolean withdrawing = faction == Faction.DEFENDER && WaterUnderSealRoadExit.withdraws(squad.aliveMembers());
            if (faction == Faction.MARINE) {
                if (frame.phase == Phase.NORTH_ROOM || frame.phase == Phase.SOUTH_ROOM) {
                    // Both sections enter actual switch interiors; exterior arrival cannot reconnect a room.
                    x += squad.squadId() % 2 == 0 ? -3 : 1; radius = 3;
                }
                if (frame.phase != Phase.COMPLETE && frame.phase != Phase.FAILED) {
                    int flank = squad.squadId() % 2 == 0 ? 1 : -1;
                    if (frame.phase == Phase.CONSTRUCTION) { x += 7; y += flank * 5; }
                    else if (frame.phase == Phase.ATTACK) { x -= 9; y += flank * 5; }

                    var threat = WorkingPassageCommand.constructionThreat(frame.contacts, frame.x, frame.y);
                    if (threat != null && frame.phase != Phase.ATTACK) {
                        // A switch crew needs its approaches clear as well as an occupied room.
                        var point = WorkingPassageCommand.screenPoint(frame.x, frame.y, threat, flank, old.assignment());
                        x = point[0]; y = point[1];
                    }
                }
            } else if (faction == Faction.ALLY) {
                if (squad.squadId() == frame.technicians) {
                    if (frame.phase == Phase.ESCORT) { x = NorthWeir.ISOLATION_X; y = NorthWeir.ISOLATION_Y; }
                    radius = 2;
                    advance = frame.companyCover;
                    if (frame.phase.ordinal() >= Phase.CONSTRUCTION.ordinal()) {
                        // Their switch work is finished; the repair watch remains inside the secured gallery.
                        x = NorthWeir.SOUTH_X; y = NorthWeir.SOUTH_Y; advance = true;
                    } else if (!frame.companyCover) { x = Math.round(squad.centroidX()); y = Math.round(squad.centroidY()); }
                } else {
                    int team = frame.militia.indexOf(squad.squadId());
                    if (frame.phase.ordinal() >= Phase.CONSTRUCTION.ordinal()) { x = frame.phase == Phase.ATTACK ? 93 : team == 0 ? 100 : 110; y = team == 0 ? 80 : 95; }
                    else if (frame.phase.ordinal() >= Phase.SOUTH_ROOM.ordinal() && team == 0) { x = NorthWeir.NORTH_X; y = NorthWeir.NORTH_Y; }
                    else { x = team == 0 ? 33 : 39; y = team == 0 ? 52 : 68; }
                }
            } else { x = withdrawing ? WaterUnderSealRoadExit.X : NorthWeir.BUILD_X + 3; y = withdrawing ? WaterUnderSealRoadExit.Y : NorthWeir.BUILD_Y; }
            int bestDistance = Integer.MAX_VALUE, bestX = Math.round(squad.centroidX()), bestY = Math.round(squad.centroidY());
            for (int cy = y - 5; cy <= y + 5; cy++) for (int cx = x - 5; cx <= x + 5; cx++) {
                int d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
                if (d < bestDistance && frame.topology().isWalkable(cx, cy)
                        && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(), cx, cy)) {
                    bestDistance = d; bestX = cx; bestY = cy;
                }
            }
            var next = withdrawing ? ObjectiveAssignment.withdraw(squad.squadId(), bestX, bestY)
                    : advance ? ObjectiveAssignment.attackMove(squad.squadId(), bestX, bestY)
                    : ObjectiveAssignment.defendArea(squad.squadId(), bestX, bestY, radius);
            if (old.assignment() != null && old.assignment().kind() == next.kind()
                    && old.assignment().targetCellX() == bestX && old.assignment().targetCellY() == bestY
                    && old.assignment().targetRadiusCells() == next.targetRadiusCells()) next = old.assignment();
            proposals.add(CommandProposal.assign(next, CommandAuthority.MISSION_COMMAND, "WATER_UNDER_SEAL_" + frame.phase,
                    next == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED)); count++;
        }
        return new CommandPlan<>(faction, strategyId(), "WATER_UNDER_SEAL_" + frame.phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), count, 0, List.of(frame.phase.name()), proposals, frame.phase.name());
    }
}
