package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.AutonomousMissionCommand;
import com.dillon.starsectormarines.battle.command.CommandAuthority;
import com.dillon.starsectormarines.battle.command.CommandDirective;
import com.dillon.starsectormarines.battle.command.CommandFrame;
import com.dillon.starsectormarines.battle.command.CommandFrameDisclosure;
import com.dillon.starsectormarines.battle.command.CommandPlan;
import com.dillon.starsectormarines.battle.command.CommandProposal;
import com.dillon.starsectormarines.battle.command.CommandSquadState;
import com.dillon.starsectormarines.battle.command.CommandStabilityBreak;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;

/**
 * The company as the mobile reserve ({@code cordon-nouns.md}, Flow): the
 * minimum autonomous Marine commander the evidence needs. The company stands
 * behind the middle of the line; each squad answers the calling redoubt
 * nearest it, and returns to the reserve when no redoubt calls. Posture and
 * strikes are {@code cordon-company-command.md}.
 *
 * <p>The company hears the calls and knows where the redoubts stand; it does
 * not see the swarm beyond what its own squads see.
 */
public final class CordonCommand implements AutonomousMissionCommand<CordonCommand.Frame, String> {

    public static final String ISSUER = "cordon-company";
    /** Squads of the reserve stand this far apart along the line. */
    static final int RESERVE_SPACING = 8;
    /** How near the asked-for cell a squad's ground is looked for. */
    private static final int SNAP = 5;

    /** A redoubt as the company knows it: where it is and whether it is calling. */
    public record Call(int number, int x, int y, boolean calling) { }

    public static final class Frame extends CommandFrame {
        final List<Call> redoubts;
        Frame(CommandFrame base, List<Call> redoubts) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.redoubts = redoubts;
        }
    }

    /** What the company is told each frame: the redoubts and their calls. */
    public static CommandFrameDisclosure<Frame> disclosure(CordonSequence sequence) {
        return (view, faction, topology, assignments) -> {
            CommandFrame base = CommandFrame.freeze(view, faction, topology, assignments);
            List<Call> calls = new ArrayList<>();
            for (CordonRedoubt r : sequence.redoubts()) {
                calls.add(new Call(r.number(), r.centreX(), r.centreY(), r.state() == CordonRedoubt.State.CALLING));
            }
            return new Frame(base, List.copyOf(calls));
        };
    }

    private final int reserveX, reserveY;

    public CordonCommand(int reserveX, int reserveY) {
        this.reserveX = reserveX;
        this.reserveY = reserveY;
    }

    @Override public Faction faction() { return Faction.MARINE; }
    @Override public String strategyId() { return ISSUER; }
    @Override public void publish(CommanderSnapshot<String> snapshot) { }

    @Override
    public CommandPlan<String> plan(Frame frame) {
        List<CommandProposal> proposals = new ArrayList<>();
        List<CommandSquadState> owned = new ArrayList<>();
        for (CommandSquadState squad : frame.squads()) {
            if (squad.aliveMembers() <= 0) continue;
            if (owned(squad.directive(), frame.tick())) owned.add(squad);
            else proposals.add(CommandProposal.retain(squad.squadId(), CommandAuthority.MISSION_COMMAND,
                    "EXTERNAL_OWNERSHIP_PRESERVED"));
        }
        int calling = 0;
        for (Call call : frame.redoubts) if (call.calling()) calling++;
        String phase = calling > 0 ? "ANSWERING_CALLS" : "RESERVE";
        for (int i = 0; i < owned.size(); i++) {
            CommandSquadState squad = owned.get(i);
            Call answer = nearestCall(frame.redoubts, squad.centroidX(), squad.centroidY());
            ObjectiveAssignment next;
            String reason;
            if (answer != null) {
                int[] cell = ground(frame, squad, answer.x(), answer.y());
                next = ObjectiveAssignment.attackMove(squad.squadId(), cell[0], cell[1]);
                reason = "CORDON_RELIEVE_" + answer.number();
            } else {
                int offset = (i - (owned.size() - 1) / 2) * RESERVE_SPACING;
                int[] cell = ground(frame, squad, reserveX + offset, reserveY);
                next = ObjectiveAssignment.defendArea(squad.squadId(), cell[0], cell[1], SNAP);
                reason = "CORDON_RESERVE";
            }
            ObjectiveAssignment old = squad.directive() == null ? null : squad.directive().assignment();
            boolean same = old != null && old.kind() == next.kind()
                    && old.targetCellX() == next.targetCellX() && old.targetCellY() == next.targetCellY();
            proposals.add(CommandProposal.assign(same ? old : next, CommandAuthority.MISSION_COMMAND, reason,
                    same ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
        }
        return new CommandPlan<>(faction(), strategyId(), phase, frame.tick(),
                frame.influence() == null ? -1 : frame.influence().updatedTick(), owned.size(), 0,
                List.of(phase), proposals, phase);
    }

    private boolean owned(CommandDirective directive, int tick) {
        if (directive == null) return false;
        return directive.authority() == CommandAuthority.MISSION_COMMAND && ISSUER.equals(directive.issuer())
                || directive.authority() == CommandAuthority.PLAYER_INTERVENTION
                && directive.leaseUntilTick() >= 0 && directive.leaseUntilTick() < tick;
    }

    private static Call nearestCall(List<Call> calls, float x, float y) {
        Call best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Call call : calls) {
            if (!call.calling()) continue;
            double distance = Math.hypot(call.x() - x, call.y() - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = call;
            }
        }
        return best;
    }

    /** The reachable open cell nearest the one asked for, or the squad's own ground. */
    private static int[] ground(Frame frame, CommandSquadState squad, int x, int y) {
        int best = Integer.MAX_VALUE, bx = Math.round(squad.centroidX()), by = Math.round(squad.centroidY());
        for (int cy = y - SNAP; cy <= y + SNAP; cy++) {
            for (int cx = x - SNAP; cx <= x + SNAP; cx++) {
                int d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
                if (d < best && frame.topology().isWalkable(cx, cy)
                        && frame.topology().reachable(squad.anchorCellX(), squad.anchorCellY(), cx, cy)) {
                    best = d;
                    bx = cx;
                    by = cy;
                }
            }
        }
        return new int[]{bx, by};
    }
}
