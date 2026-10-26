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
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.cordon.CordonCompanyRules.Duty;
import com.dillon.starsectormarines.battle.cordon.CordonCompanyRules.Kind;
import com.dillon.starsectormarines.battle.cordon.CordonCompanyRules.Squad;
import com.dillon.starsectormarines.battle.evacuation.SwarmBore;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The company commander ({@code cordon-allied-command.md}, The company commander's rules): stands in for the player
 * in evidence and decides when to lean on the allies and when to act itself. It hears the allies' radio
 * ({@link CordonRadio}) and the convoys' and beats' calls, and sees the swarm only through its own squads. The rules
 * are {@link CordonCompanyRules}; this freezes the picture and issues what they decide. Posture and strikes are
 * {@code cordon-company-command.md}.
 */
public final class CordonCommand implements AutonomousMissionCommand<CordonCommand.Frame, String> {

    public static final String ISSUER = "cordon-company";
    /** Squads of the reserve stand this far apart along the line. */
    static final int RESERVE_SPACING = CordonCompanyRules.POST_SPACING;
    /**
     * Ticks a squad whose lift was called off walks before it may ask again (20 s): a call that comes and goes
     * would otherwise have a carrier granted and called off every review.
     */
    static final int LIFT_COOLDOWN_TICKS = 600;
    /** How near the asked-for cell a squad's ground is looked for. */
    private static final int SNAP = 5;

    public static final class Frame extends CommandFrame {
        final List<CordonRadio> radio;
        /** On the Road, where a halted convoy's road is blocked; empty otherwise. */
        final List<int[]> roadBlocks;
        /** On the Road, where each convoy on the road no allied armour is with wants its escort; empty otherwise. */
        final List<int[]> escorts;
        /** Where the battle's optional objectives want squads, {x, y, squads}; empty without any. */
        final List<int[]> beats;
        /** The base's apron while the swarm is on it, else {@code null}. */
        final int[] baseOverrun;
        final boolean tide;
        /** The base's carriers free to lift a squad; empty in a city or without a base. */
        final List<CordonLogistics.Carrier> carriers;
        /** Where the carrier lifting each squad stands, by squad. */
        final Map<Integer, float[]> lifting;
        /** The bores, living and collapsed: an eruption is announced, so every bore is known from the moment it opens. */
        final List<KnownBore> bores;

        Frame(CommandFrame base, List<CordonRadio> radio, List<int[]> roadBlocks, List<int[]> escorts,
              List<int[]> beats, int[] baseOverrun, boolean tide, List<CordonLogistics.Carrier> carriers,
              Map<Integer, float[]> lifting, List<KnownBore> bores) {
            super(base.tick(), base.perspective(), base.squads(), base.influence(), base.topology(), base.assignments());
            this.radio = radio;
            this.roadBlocks = roadBlocks;
            this.escorts = escorts;
            this.beats = beats;
            this.baseOverrun = baseOverrun;
            this.tide = tide;
            this.carriers = carriers;
            this.lifting = lifting;
            this.bores = bores;
        }
    }

    /** What the company is told each frame: the radio, the road's and the beats' calls, the base, the tide. */
    public static CommandFrameDisclosure<Frame> disclosure(CordonSequence sequence) {
        return (view, faction, topology, assignments) -> {
            CommandFrame base = CommandFrame.freeze(view, faction, topology, assignments);
            BattleSimulation sim = view instanceof BattleSimulation s ? s : null;
            List<CordonRadio> radio = new ArrayList<>();
            CordonLineCommand line = sequence.line();
            for (CordonRedoubt r : sequence.redoubts()) {
                int standing = sim == null ? r.garrison() : CordonSequence.standing(sim, r);
                // Uncovered and help coming are the allied line command's to say.
                radio.add(CordonRadio.report(r.number(), r.centreX(), r.centreY(), r.manned(), r.swarmInside(),
                        standing, r.garrison(), r.quietTicks(base.tick()), line != null && line.uncovered(r),
                        line != null && line.helpComing(r)));
            }
            List<int[]> blocks = sequence.convoys() == null ? List.of() : sequence.convoys().roadBlocks();
            List<int[]> escorts = sequence.convoys() == null || sim == null ? List.of() : sequence.convoys().unguarded(sim);
            List<int[]> beats = sequence.beats() == null || sim == null ? List.of() : sequence.beats().calls(sim);
            CordonLogistics logistics = sequence.logistics();
            int[] overrun = logistics == null || sim == null || logistics.dispatchCell(sim) != null ? null
                    : new int[]{logistics.musterX(), logistics.musterY()};
            List<CordonLogistics.Carrier> carriers = logistics == null || sim == null ? List.of() : logistics.liftable(sim);
            Map<Integer, float[]> lifting = logistics == null || sim == null ? Map.of() : logistics.lifting(sim);
            List<KnownBore> bores = new ArrayList<>();
            if (sim != null) {
                for (SwarmBore bore : sim.getSwarmBores()) {
                    bores.add(new KnownBore(bore.beatKey(), bore.mother(), bore.x(), bore.y(), !bore.collapsed()));
                }
            }
            return new Frame(base, List.copyOf(radio), blocks, escorts, beats, overrun, sequence.inTide(), carriers,
                    lifting, bores);
        };
    }

    private final int reserveX, reserveY;
    private final CordonPlan.Density density;
    /** The duty each squad was last given, by key, so it keeps it while it stands. */
    private final Map<Integer, String> onDuty = new HashMap<>();
    /** The base the company asks for lifts, or {@code null} where there is none. */
    private final CordonLogistics depot;
    /** The tick each squad's lift was last called off, by squad. */
    private final Map<Integer, Integer> calledOff = new HashMap<>();
    /** Whether each squad walking to its duty is getting there. */
    private final CordonCompanyRules.Progress progress = new CordonCompanyRules.Progress();
    /** The duty each squad that stalled on it is kept off, and until which tick, by squad. */
    private final Map<Integer, Barred> barred = new HashMap<>();
    /** The posture's raids at the bores, or {@code null} for a commander that never raids. */
    private final CordonStrikes strikes;

    public CordonCommand(int reserveX, int reserveY, CordonPlan.Density density, CordonLogistics depot,
                         CordonStrikes strikes) {
        this.reserveX = reserveX;
        this.reserveY = reserveY;
        this.density = density;
        this.depot = depot;
        this.strikes = strikes;
    }

    /** A duty a squad stalled on, and the tick it may take it again. */
    private record Barred(String key, int until) { }

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
        // The base feeds the line where its carriers and shed do; a city's walking columns do not need it saved.
        int[] base = density == CordonPlan.Density.URBAN ? null : frame.baseOverrun;
        List<Duty> duties = CordonCompanyRules.duties(frame.radio, frame.roadBlocks, frame.escorts, frame.beats, base);
        List<Squad> squads = new ArrayList<>();
        Set<Integer> striking = strikes == null ? Set.of() : strikes.members();
        // A squad on strike is never drawn for a duty: the gate already counted what it leaves behind.
        for (CommandSquadState s : owned) {
            if (striking.contains(s.squadId())) continue;
            int id = s.squadId();
            String key = onDuty.get(id);
            Duty on = null;
            for (Duty d : duties) if (d.key().equals(key)) on = d;
            if (on == null) progress.forget(id);
            else if (progress.stalled(id, key, Math.hypot(on.x() - s.centroidX(), on.y() - s.centroidY()),
                    frame.lifting.containsKey(id), frame.tick())) {
                // Rule 11: it is not getting there; the duty goes to the next nearest, and it stays off it a while.
                barred.put(id, new Barred(key, frame.tick() + CordonCompanyRules.BARRED_TICKS));
            }
            Barred bar = barred.get(id);
            if (bar != null && bar.until() <= frame.tick()) {
                barred.remove(id);
                bar = null;
            }
            squads.add(new Squad(id, s.centroidX(), s.centroidY(), key, s.machine(), bar == null ? null : bar.key()));
        }
        Map<Integer, Duty> assigned = CordonCompanyRules.assign(duties, squads,
                CordonCompanyRules.reserveFloor(owned.size(), frame.tide));
        onDuty.clear();
        for (Map.Entry<Integer, Duty> e : assigned.entrySet()) onDuty.put(e.getKey(), e.getValue().key());
        if (strikes != null) {
            List<CommandSquadState> free = new ArrayList<>();
            for (CommandSquadState s : owned) {
                if (!assigned.containsKey(s.squadId()) && !striking.contains(s.squadId())) free.add(s);
            }
            int need = 0;
            for (Duty d : duties) need += d.squads();
            boolean quiet = frame.roadBlocks.isEmpty();
            for (CordonRadio r : frame.radio) quiet &= !(r.manned() && r.unsettled());
            strikes.update(frame.tick(), frame.bores, frame.radio, free, owned, need, quiet, frame.tide);
        }
        Set<Integer> onStrike = strikes == null ? Set.of() : strikes.members();
        KnownBore struck = strikes == null ? null : strikes.target();

        List<int[]> line = new ArrayList<>();
        for (CordonRadio r : frame.radio) line.add(new int[]{r.x(), r.y()});
        List<CommandSquadState> waiting = new ArrayList<>();
        for (CommandSquadState s : owned) {
            if (!assigned.containsKey(s.squadId()) && !onStrike.contains(s.squadId())) waiting.add(s);
        }
        List<int[]> posts = CordonCompanyRules.reservePosts(density, line, reserveX, reserveY, waiting.size(), frame.tide);

        String phase = struck != null && phase(assigned).equals("RESERVE") ? "STRIKING" : phase(assigned);
        List<CordonLogistics.Carrier> free = new ArrayList<>(frame.carriers);
        for (CommandSquadState squad : owned) {
            Duty duty = assigned.get(squad.squadId());
            ObjectiveAssignment next;
            String reason;
            if (struck != null && onStrike.contains(squad.squadId())) {
                // Ordered at the bore as the player would: an attack move naming its cell as the thing to open.
                ObjectiveAssignment strike = ObjectiveAssignment.attackMove(squad.squadId(), struck.x(), struck.y());
                ObjectiveAssignment held = squad.directive() == null ? null : squad.directive().assignment();
                boolean kept = held != null && held.kind() == strike.kind() && held.targetCellX() == strike.targetCellX()
                        && held.targetCellY() == strike.targetCellY();
                proposals.add(CommandProposal.assign(kept ? held : strike, CommandAuthority.MISSION_COMMAND,
                        "CORDON_STRIKE_" + struck.key(),
                        kept ? CommandStabilityBreak.NONE : CommandStabilityBreak.CONTEXT_INVALIDATED));
                continue;
            }
            float[] ride = frame.lifting.get(squad.squadId());
            if (ride != null && depot != null && (duty == null
                    || Math.hypot(duty.x() - ride[2], duty.y() - ride[3]) > CordonCompanyRules.LIFT_WORTH / 2)) {
                // The duty it rides for has ended, or moved half a lift's worth (a convoy it escorts moves all the
                // while, so a smaller move is still worth the ride): set down where it is rather than carried on.
                depot.callOff(squad.squadId());
                calledOff.put(squad.squadId(), frame.tick());
                ride = null;
            }
            if (duty != null && ride != null) {
                // Lifted: walk to the carrier and board; the base sets the squad down at its duty.
                next = ObjectiveAssignment.attackMove(squad.squadId(), (int) ride[0], (int) ride[1]);
                reason = "CORDON_LIFT_" + duty.key();
            } else if (duty != null) {
                CordonLogistics.Carrier carrier = nearest(free, squad.centroidX(), squad.centroidY(),
                        squad.aliveMembers());
                Integer off = calledOff.get(squad.squadId());
                boolean cooling = off != null && frame.tick() - off < LIFT_COOLDOWN_TICKS;
                if (depot != null && carrier != null && !cooling && CordonCompanyRules.liftWorth(density, squad.centroidX(),
                        squad.centroidY(), duty.x(), duty.y(), carrier.x(), carrier.y())) {
                    depot.ask(carrier.hull(), squad.squadId(), duty.x(), duty.y());
                    free.remove(carrier);
                }
                int[] cell = ground(frame, squad, duty.x(), duty.y());
                next = ObjectiveAssignment.attackMove(squad.squadId(), cell[0], cell[1]);
                reason = "CORDON_" + duty.kind() + "_" + duty.key();
            } else {
                int[] post = posts.get(waiting.indexOf(squad));
                int[] cell = ground(frame, squad, post[0], post[1]);
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

    /** The free carrier nearest a squad that seats it whole, or {@code null}: a squad rides together or walks. */
    static CordonLogistics.Carrier nearest(List<CordonLogistics.Carrier> carriers, float x, float y, int members) {
        CordonLogistics.Carrier best = null;
        for (CordonLogistics.Carrier c : carriers)
            if (c.seats() >= members && (best == null || Math.hypot(c.x() - x, c.y() - y) < Math.hypot(best.x() - x, best.y() - y))) best = c;
        return best;
    }

    /** The most urgent duty anyone is on names the phase. */
    static String phase(Map<Integer, Duty> assigned) {
        Kind top = null;
        for (Duty d : assigned.values()) if (top == null || d.kind().ordinal() < top.ordinal()) top = d.kind();
        if (top == null) return "RESERVE";
        return switch (top) {
            case ROAD_BLOCK -> "CLEARING_ROAD";
            case CONVOY -> "ESCORTING";
            case BREACHED, BLED -> "ANSWERING_CALLS";
            case UNCOVERED -> "COVERING";
            case BEAT -> "OBJECTIVES";
            case BASE -> "BASE";
        };
    }

    private boolean owned(CommandDirective directive, int tick) {
        if (directive == null) return false;
        return directive.authority() == CommandAuthority.MISSION_COMMAND && ISSUER.equals(directive.issuer())
                || directive.authority() == CommandAuthority.PLAYER_INTERVENTION
                && directive.leaseUntilTick() >= 0 && directive.leaseUntilTick() < tick;
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
