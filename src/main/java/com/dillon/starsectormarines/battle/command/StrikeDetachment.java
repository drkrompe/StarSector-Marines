package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.command.RescueCommandFacts.KnownBore;
import com.dillon.starsectormarines.battle.command.RescueCommandFacts.Emplacement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;

/**
 * The rescue commander's strike detachment ({@code colony-breach-nouns.md},
 * Strike detachment): the squads it sends against a bore, chosen once and
 * kept until the bore collapses, the detachment is lost, or the
 * {@link ReadinessGate} closes and they are recalled.
 *
 * <p>State lives here between pulses the way the commander's other stable
 * roles do; every input is the frozen frame. Each commit, recall and ending is
 * kept as a {@link Decision} with the gate it was made under, which the
 * snapshot publishes for the command trace and the breach sequence turns into
 * {@code strike_committed_N} / {@code strike_recalled_N} beats.
 */
public final class StrikeDetachment {

    /** A bore within this many cells of the walk out is on the route, for Evacuate. */
    static final int ON_ROUTE_CELLS = 8;
    /** Cells round the lift that count as the lift line, for defences and losses. */
    static final int LIFT_LINE_CELLS = 12;
    /** Ticks after a recall before the gate may send anyone again: fifteen seconds. */
    static final int RECOMMIT_COOLDOWN_TICKS = 450;
    /** Squads sent at an ordinary bore; Purge sends {@link #MOTHER_SQUADS} at the mother. */
    static final int STRIKE_SQUADS = 1;
    static final int MOTHER_SQUADS = 2;

    public enum Kind { COMMITTED, RECALLED, RETARGETED, TARGET_COLLAPSED, DETACHMENT_LOST }

    /**
     * One decision about the detachment, with the gate it was read under.
     *
     * @param boreKey the bore's beat key: its number, or {@code mother}
     * @param gate    {@link ReadinessGate#describe()} plus its inputs
     */
    public record Decision(int tick, Kind kind, String boreKey, List<Integer> squads, String gate) {
        public Decision {
            squads = List.copyOf(squads);
        }
    }

    private final HivePosture posture;
    private final TreeSet<Integer> members = new TreeSet<>();
    private KnownBore target;
    private int recalledTick = Integer.MIN_VALUE / 2;
    private int lastLiftLossTick = -1;
    private int defencesSinceTick = -1;
    private final Map<Integer, Integer> lastAlive = new HashMap<>();
    private final Map<Integer, Boolean> lastOnLiftLine = new HashMap<>();
    private final List<Decision> decisions = new ArrayList<>();
    private ReadinessGate.Inputs inputs = new ReadinessGate.Inputs(0, 0, 0, 0, 0);
    private ReadinessGate gate;

    StrikeDetachment(HivePosture posture) {
        this.posture = HivePosture.orDefault(posture);
    }

    HivePosture posture() { return posture; }

    /** The squads on strike now, ascending. */
    Set<Integer> members() { return members; }

    KnownBore target() { return target; }

    List<Decision> decisions() { return decisions; }

    /**
     * Reads the lift line off the frame: built defences standing there, and
     * any squad that lost a member while it stood there (or was wiped out
     * there). Runs every pulse whatever the posture, so the window is honest
     * when a gate first asks.
     */
    void observeLift(int tick, List<CommandSquadState> squads, List<Emplacement> emplacements, int liftX, int liftY) {
        int defences = liftDefences(emplacements, liftX, liftY);
        if (defences == 0) defencesSinceTick = -1;
        else if (defencesSinceTick < 0) defencesSinceTick = tick;
        Set<Integer> seen = new HashSet<>();
        for (CommandSquadState squad : squads) {
            seen.add(squad.squadId());
            boolean near = Math.max(Math.abs(squad.anchorCellX() - liftX),
                    Math.abs(squad.anchorCellY() - liftY)) <= LIFT_LINE_CELLS;
            Integer before = lastAlive.put(squad.squadId(), squad.aliveMembers());
            if (before != null && squad.aliveMembers() < before
                    && (near || Boolean.TRUE.equals(lastOnLiftLine.get(squad.squadId())))) {
                lastLiftLossTick = tick;
            }
            lastOnLiftLine.put(squad.squadId(), near);
        }
        // A squad wiped out entirely leaves the frame; if it was on the line, that is a loss there.
        for (Integer id : new ArrayList<>(lastAlive.keySet())) {
            if (seen.contains(id)) continue;
            if (Boolean.TRUE.equals(lastOnLiftLine.get(id))) lastLiftLossTick = tick;
            lastAlive.remove(id);
            lastOnLiftLine.remove(id);
        }
    }

    /**
     * One pulse's decision.
     *
     * @param candidates the mobile squads free of escort duty, members included
     * @param mobileForce every squad under mission command, escorts and detachment included
     * @param escortNeed  {@link ReadinessGate#escortNeed}
     * @param onRoute     whether a cell sits on the walk out (consulted for Evacuate only)
     */
    void update(int tick, List<KnownBore> bores, List<Emplacement> emplacements, int liftX, int liftY,
                List<CommandSquadState> candidates, int mobileForce, int escortNeed,
                BiPredicate<Integer, Integer> onRoute) {
        int defences = liftDefences(emplacements, liftX, liftY);
        int quiet = defencesSinceTick < 0 ? 0 : tick - Math.max(lastLiftLossTick, defencesSinceTick);
        Map<Integer, CommandSquadState> byId = new HashMap<>();
        for (CommandSquadState squad : candidates) byId.put(squad.squadId(), squad);

        if (target != null) {
            members.removeIf(id -> !byId.containsKey(id));
            KnownBore now = find(bores, target.key());
            if (now == null || !now.living()) {
                end(tick, Kind.TARGET_COLLAPSED, defences, quiet, mobileForce, escortNeed);
                return;
            }
            if (members.isEmpty()) {
                end(tick, Kind.DETACHMENT_LOST, defences, quiet, mobileForce, escortNeed);
                return;
            }
            inputs = new ReadinessGate.Inputs(defences, quiet, mobileForce - members.size(), escortNeed,
                    members.size());
            gate = ReadinessGate.evaluate(posture, target.mother(), inputs);
            boolean stillOnRoute = posture != HivePosture.EVACUATE || onRoute.test(target.x(), target.y());
            if (!gate.open() || !stillOnRoute) {
                recalledTick = tick;
                end(tick, Kind.RECALLED, defences, quiet, mobileForce, escortNeed);
                return;
            }
            // Purge's objective is the mother: once she opens, a detachment on a
            // first-wave bore is turned onto her rather than left where it was.
            KnownBore mother = posture == HivePosture.PURGE && !target.mother() ? livingMother(bores) : null;
            if (mother == null) return;
            end(tick, Kind.RETARGETED, defences, quiet, mobileForce, escortNeed);
        }

        List<CommandSquadState> capable = candidates.stream()
                .filter(StrikeDetachment::capable)
                .toList();
        KnownBore choice = chooseTarget(bores, liftX, liftY, onRoute);
        int size = choice != null && choice.mother() && posture == HivePosture.PURGE ? MOTHER_SQUADS : STRIKE_SQUADS;
        inputs = new ReadinessGate.Inputs(defences, quiet,
                mobileForce - Math.min(size, Math.max(1, capable.size())), escortNeed, capable.size());
        gate = ReadinessGate.evaluate(posture, choice != null && choice.mother(), inputs);
        if (choice == null || !gate.open() || tick < recalledTick + RECOMMIT_COOLDOWN_TICKS) return;
        List<CommandSquadState> chosen = new ArrayList<>(capable);
        chosen.sort(Comparator.comparingInt((CommandSquadState s) -> s.machine() ? 0 : s.demolition() ? 1 : 2)
                .thenComparingInt(s -> Math.abs(s.anchorCellX() - choice.x()) + Math.abs(s.anchorCellY() - choice.y()))
                .thenComparingInt(CommandSquadState::squadId));
        for (int i = 0; i < Math.min(size, chosen.size()); i++) members.add(chosen.get(i).squadId());
        target = choice;
        decisions.add(new Decision(tick, Kind.COMMITTED, choice.key(), List.copyOf(members),
                gateLine(gate, inputs)));
    }

    /**
     * The bore this posture would strike: Purge the mother once it is known,
     * otherwise the living bore nearest the lift (the one that presses on the
     * walk out), and for Evacuate only one standing on that walk. Straight
     * distance, then number, so a tie is the earlier bore.
     */
    KnownBore chooseTarget(List<KnownBore> bores, int liftX, int liftY, BiPredicate<Integer, Integer> onRoute) {
        if (posture == HivePosture.PURGE) {
            KnownBore mother = livingMother(bores);
            if (mother != null) return mother;
        }
        KnownBore best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (KnownBore bore : bores) {
            if (!bore.living()) continue;
            if (posture == HivePosture.EVACUATE && !onRoute.test(bore.x(), bore.y())) continue;
            int distance = Math.abs(bore.x() - liftX) + Math.abs(bore.y() - liftY);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = bore;
            }
        }
        return best;
    }

    /** What the published picture says about the gate this pulse. */
    ReadinessGate gate() { return gate; }

    ReadinessGate.Inputs inputs() { return inputs; }

    /** A squad that can hurt a bore: it carries a satchel, it is a machine, or it fires on structures. */
    static boolean capable(CommandSquadState squad) {
        if (squad.aliveMembers() <= 0 || squad.moraleBroken()) return false;
        if (squad.demolition() || squad.machine()) return true;
        CapabilityProfile capabilities = squad.capabilities();
        return capabilities != null && (capabilities.has(AssaultCapability.DIRECT_STRUCTURE_FIRE)
                || capabilities.has(AssaultCapability.STANDOFF_STRUCTURE_FIRE));
    }

    static int liftDefences(List<Emplacement> emplacements, int liftX, int liftY) {
        int count = 0;
        for (Emplacement e : emplacements) {
            if (Math.max(Math.abs(e.x() - liftX), Math.abs(e.y() - liftY)) <= LIFT_LINE_CELLS) count++;
        }
        return count;
    }

    private void end(int tick, Kind kind, int defences, int quiet, int mobileForce, int escortNeed) {
        if (gate == null || kind != Kind.RECALLED) {
            inputs = new ReadinessGate.Inputs(defences, quiet, mobileForce, escortNeed, 0);
            gate = ReadinessGate.evaluate(posture, target.mother(), inputs);
        }
        decisions.add(new Decision(tick, kind, target.key(), List.copyOf(members), gateLine(gate, inputs)));
        members.clear();
        target = null;
    }

    private static KnownBore livingMother(List<KnownBore> bores) {
        for (KnownBore bore : bores) if (bore.mother() && bore.living()) return bore;
        return null;
    }

    private static KnownBore find(List<KnownBore> bores, String key) {
        for (KnownBore bore : bores) if (bore.key().equals(key)) return bore;
        return null;
    }

    static String gateLine(ReadinessGate gate, ReadinessGate.Inputs in) {
        return gate.describe() + " defences=" + in.liftDefences() + " quiet=" + in.liftQuietTicks()
                + " left=" + in.leftBehind() + " need=" + in.escortNeed() + " capable=" + in.capableSquads();
    }
}
