package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.setup.DistrictEscortSequence;
import com.dillon.starsectormarines.battle.story.DistrictEscortProgress.Phase;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.LowQuay;
import java.util.ArrayList;
import java.util.List;

/**
 * Escort duties read only disclosed stages: the company the consignment, the party its carrier,
 * the constables the apron, and the raiders the freight lane. Local squads keep combat and player orders.
 */
public final class DistrictEscortCommand implements AutonomousMissionCommand<DistrictEscortCommand.Frame, String> {
    /** Constables hold this far east of the apron, covering the lane's last stretch. */
    static final int APRON_HOLD_OFFSET = 4;

    public static final class Frame extends CommandFrame {
        final Phase phase;
        final int x, y, party, cargoX, cargoY;
        public Frame(CommandFrame base, Phase phase, int x, int y, int party, int cargoX, int cargoY) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.phase = phase; this.x = x; this.y = y; this.party = party; this.cargoX = cargoX; this.cargoY = cargoY;
        }
    }

    public static CommandFrameDisclosure<Frame> disclosure(DistrictEscortSequence sequence) {
        return (view, faction, topology, assignments) -> {
            var base = CommandFrame.freeze(view, faction, topology, assignments);
            // The raiders know where the lane runs, never where the hidden company or carrier is.
            boolean raider = faction == Faction.DEFENDER;
            int x = raider ? sequence.ambushX() : sequence.targetX();
            int y = raider ? sequence.ambushY() : sequence.targetY();
            int cargoX = raider ? x : sequence.cargoX(), cargoY = raider ? y : sequence.cargoY();
            return new Frame(base, sequence.progress().phase(), x, y, sequence.partySquadId(), cargoX, cargoY);
        };
    }

    private final Faction faction;
    public DistrictEscortCommand(Faction faction) { this.faction = faction; }
    public static String issuer(Faction faction) { return "district-escort:" + faction; }
    public Faction faction() { return faction; }
    public String strategyId() { return issuer(faction); }
    public void publish(CommanderSnapshot<String> snapshot) { }

    public CommandPlan<String> plan(Frame frame) {
        var proposals = new ArrayList<CommandProposal>(); int count = 0;
        for (var squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            var old = squad.directive();
            boolean owned = old != null && (old.authority() == CommandAuthority.MISSION_COMMAND && strategyId().equals(old.issuer())
                    || old.authority() == CommandAuthority.PLAYER_INTERVENTION && old.leaseUntilTick() >= 0 && old.leaseUntilTick() < frame.tick());
            if (!owned) { proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND, "EXTERNAL_OWNERSHIP_PRESERVED")); continue; }
            int x = frame.x, y = frame.y, radius = 3;
            boolean advance = faction == Faction.DEFENDER;
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
                if (squad.squadId() != frame.party) { x = LowQuay.LANDING_X + APRON_HOLD_OFFSET; y = LowQuay.LANDING_Y; radius = 5; }
                else if (frame.phase == Phase.ESCORT) {
                    // The party walks with its consignment and signs it over at the apron.
                    boolean nearApron = Math.hypot(frame.cargoX - LowQuay.LANDING_X, frame.cargoY - LowQuay.LANDING_Y) < 10;
                    x = nearApron ? LowQuay.LANDING_X : frame.cargoX; y = nearApron ? LowQuay.LANDING_Y : frame.cargoY;
                    advance = true; radius = 2;
                } else { x = Math.round(squad.centroidX()); y = Math.round(squad.centroidY()); }
            }
            int best = Integer.MAX_VALUE, bx = Math.round(squad.centroidX()), by = Math.round(squad.centroidY());
            for (int cy = y - 5; cy <= y + 5; cy++) for (int cx = x - 5; cx <= x + 5; cx++) {
                int d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
                if (d < best && frame.topology().isWalkable(cx, cy) && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(), cx, cy)) {
                    best = d; bx = cx; by = cy;
                }
            }
            var next = advance ? ObjectiveAssignment.attackMove(squad.squadId(), bx, by)
                    : ObjectiveAssignment.defendArea(squad.squadId(), bx, by, radius);
            if (old.assignment() != null && old.assignment().kind() == next.kind()
                    && old.assignment().targetCellX() == bx && old.assignment().targetCellY() == by
                    && old.assignment().targetRadiusCells() == next.targetRadiusCells()) next = old.assignment();
            proposals.add(CommandProposal.assign(next, CommandAuthority.MISSION_COMMAND, "DISTRICT_ESCORT_" + frame.phase,
                    next == old.assignment() ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED)); count++;
        }
        return new CommandPlan<>(faction, strategyId(), "DISTRICT_ESCORT_" + frame.phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), count, 0, List.of(frame.phase.name()), proposals, frame.phase.name());
    }
}
