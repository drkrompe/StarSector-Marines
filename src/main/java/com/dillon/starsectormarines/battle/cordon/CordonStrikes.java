package com.dillon.starsectormarines.battle.cordon;

import com.dillon.starsectormarines.battle.command.AssaultCapability;
import com.dillon.starsectormarines.battle.command.CapabilityProfile;
import com.dillon.starsectormarines.battle.command.CommandSquadState;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The company's raids at the bores ({@code cordon-nouns.md}, Line posture; the Line's flow, Raids): the readiness gate
 * read every command pulse, and the strike detachment it sends. Grown from Colony Breach's
 * ({@code StrikeDetachment}, {@code ReadinessGate}) with the Cordon's inputs:
 *
 * <ol>
 *   <li><b>the line has held</b>: no redoubt breached and no road blocked for {@link #HELD_WINDOW_TICKS};</li>
 *   <li><b>the strength left behind covers the calls</b>: the squads that stay are at least the squads every duty
 *       wants (every calling redoubt, every convoy and group on the move);</li>
 *   <li><b>a detachment exists</b>: a free squad that can hurt a bore.</li>
 * </ol>
 *
 * <p>Opportunistic needs all three and raids only between tides, with one squad; Push makes the bores objectives,
 * waives the first, raids in a tide too and sends two. Hold reads the gate and never sends anyone. Membership is
 * stable: the same squads stay on the strike until their bore collapses, they are lost, or the gate closes and they
 * are recalled, after which nobody is sent for {@link #RECOMMIT_COOLDOWN_TICKS}. Each commit and recall is a beat,
 * {@code strike_committed_N} / {@code strike_recalled_N}, N the bore's key.
 */
public final class CordonStrikes {

    /** Ticks the line must have held before a raid (twenty seconds at thirty ticks a second). */
    static final int HELD_WINDOW_TICKS = 600;
    /** Ticks after a recall before the gate may send anyone again: fifteen seconds. */
    static final int RECOMMIT_COOLDOWN_TICKS = 450;
    /** Squads an Opportunistic raid sends; Push sends {@link #PUSH_SQUADS}. */
    static final int STRIKE_SQUADS = 1;
    static final int PUSH_SQUADS = 2;

    /**
     * The gate's verdict and the three conditions it was read from.
     *
     * @param held       condition 1 (waived under Push)
     * @param strength   condition 2
     * @param detachment condition 3
     */
    record Gate(boolean held, boolean strength, boolean detachment, boolean open) {
        String describe() {
            return (held ? "H" : "h") + (strength ? "S" : "s") + (detachment ? "D" : "d") + (open ? " open" : " closed");
        }
    }

    /**
     * The gate for {@code posture}: Hold never opens; Opportunistic needs the line held, the strength left and a
     * detachment, and no tide; Push needs the strength and a detachment.
     */
    static Gate gate(CordonPosture posture, int heldTicks, int leftBehind, int need, int capable, boolean tide) {
        boolean held = heldTicks >= HELD_WINDOW_TICKS;
        boolean strength = leftBehind >= need;
        boolean detachment = capable > 0;
        boolean open = switch (posture) {
            case HOLD -> false;
            case OPPORTUNISTIC -> held && strength && detachment && !tide;
            case PUSH -> strength && detachment;
        };
        return new Gate(held, strength, detachment, open);
    }

    /**
     * The bore pressing hardest: of the living bores, the one whose nearest manned redoubt is worst off (breached, then
     * bled, then uncovered, then quiet), the bore nearer the squads that would go ({@code fromX, fromY}) breaking a tie,
     * then the lower key: with the line quiet every bore presses alike, and the raid is the one they can reach.
     * {@code null} with no living bore or no manned redoubt.
     */
    static KnownBore pressingHardest(List<KnownBore> bores, List<CordonRadio> radio, float fromX, float fromY) {
        KnownBore best = null;
        int bestScore = -1;
        double bestDistance = 0;
        for (KnownBore bore : bores) {
            if (!bore.living()) continue;
            CordonRadio near = null;
            double nearest = Double.MAX_VALUE;
            for (CordonRadio r : radio) {
                if (!r.manned()) continue;
                double d = Math.hypot(r.x() - bore.x(), r.y() - bore.y());
                if (d < nearest) {
                    nearest = d;
                    near = r;
                }
            }
            if (near == null) continue;
            int score = near.unsettled() ? 3 : near.bled() ? 2 : near.uncovered() ? 1 : 0;
            double away = Math.hypot(bore.x() - fromX, bore.y() - fromY);
            if (best == null || score > bestScore || score == bestScore && away < bestDistance
                    || score == bestScore && away == bestDistance && bore.key().compareTo(best.key()) < 0) {
                best = bore;
                bestScore = score;
                bestDistance = away;
            }
        }
        return best;
    }

    /** A squad that can hurt a bore: it carries a satchel, it is a machine, or it fires on structures. */
    static boolean capable(CommandSquadState squad) {
        if (squad.aliveMembers() <= 0 || squad.moraleBroken()) return false;
        if (squad.demolition() || squad.machine()) return true;
        CapabilityProfile capabilities = squad.capabilities();
        return capabilities != null && (capabilities.has(AssaultCapability.DIRECT_STRUCTURE_FIRE)
                || capabilities.has(AssaultCapability.STANDOFF_STRUCTURE_FIRE));
    }

    private final CordonPosture posture;
    private final TreeSet<Integer> members = new TreeSet<>();
    private KnownBore target;
    private int heldSince = 0;
    private int recalledTick = Integer.MIN_VALUE / 2;
    private Gate last;
    private int committed, recalled, collapsed, lost;
    private final List<String> beats = new ArrayList<>();

    public CordonStrikes(CordonPosture posture) {
        this.posture = CordonPosture.orDefault(posture);
    }

    public CordonPosture posture() { return posture; }

    /** The squads on strike now, ascending. */
    TreeSet<Integer> members() { return members; }

    /** The bore struck now, or {@code null}. */
    KnownBore target() { return target; }

    /**
     * One pulse.
     *
     * @param free     the company's squads on no duty and not on the strike
     * @param owned    every squad under the company's command
     * @param need     squads the duties want, summed
     * @param quiet    whether no redoubt is breached and no road blocked now
     */
    void update(int tick, List<KnownBore> bores, List<CordonRadio> radio, List<CommandSquadState> free,
                List<CommandSquadState> owned, int need, boolean quiet, boolean tide) {
        if (!quiet) heldSince = tick;
        int held = tick - heldSince;
        if (target != null) {
            members.removeIf(id -> owned.stream().noneMatch(s -> s.squadId() == id && s.aliveMembers() > 0));
            KnownBore now = find(bores, target.key());
            if (now == null || !now.living()) {
                collapsed++;
                end();
                return;
            }
            if (members.isEmpty()) {
                lost++;
                end();
                return;
            }
            last = gate(posture, held, owned.size() - members.size(), need, members.size(), tide);
            if (!last.open()) {
                recalled++;
                beats.add("strike_recalled_" + target.key());
                recalledTick = tick;
                end();
            }
            return;
        }
        List<CommandSquadState> capable = new ArrayList<>();
        for (CommandSquadState s : free) if (capable(s)) capable.add(s);
        int size = posture == CordonPosture.PUSH ? PUSH_SQUADS : STRIKE_SQUADS;
        int sending = Math.min(size, Math.max(1, capable.size()));
        last = gate(posture, held, owned.size() - sending, need, capable.size(), tide);
        float fromX = 0, fromY = 0;
        for (CommandSquadState s : capable) {
            fromX += s.centroidX() / capable.size();
            fromY += s.centroidY() / capable.size();
        }
        KnownBore choice = pressingHardest(bores, radio, fromX, fromY);
        if (choice == null || !last.open() || tick < recalledTick + RECOMMIT_COOLDOWN_TICKS) return;
        capable.sort(Comparator.comparingInt((CommandSquadState s) -> s.machine() ? 0 : s.demolition() ? 1 : 2)
                .thenComparingDouble(s -> Math.hypot(s.centroidX() - choice.x(), s.centroidY() - choice.y()))
                .thenComparingInt(CommandSquadState::squadId));
        for (int i = 0; i < Math.min(size, capable.size()); i++) members.add(capable.get(i).squadId());
        target = choice;
        committed++;
        beats.add("strike_committed_" + choice.key());
    }

    private void end() {
        members.clear();
        target = null;
    }

    private static KnownBore find(List<KnownBore> bores, String key) {
        for (KnownBore bore : bores) if (bore.key().equals(key)) return bore;
        return null;
    }

    /** The beats since the last call, for the sequence to emit. */
    public List<String> drainBeats() {
        List<String> out = new ArrayList<>(beats);
        beats.clear();
        return out;
    }

    /** One line for evidence: the posture, the gate as last read, and how the raids went. */
    public String readings() {
        return String.format(Locale.ROOT, "%s; gate %s; %d committed, %d recalled, %d bores collapsed, %d lost",
                posture.name().toLowerCase(Locale.ROOT), last == null ? "unread" : last.describe(), committed,
                recalled, collapsed, lost);
    }
}
