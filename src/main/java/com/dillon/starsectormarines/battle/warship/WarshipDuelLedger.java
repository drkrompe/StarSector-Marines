package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The duel ledger: what became of every hull over the battle, and what the fight between hulls cost the ground. A
 * read-only record for a debrief, kept by {@link WarshipSystem} as things happen; nothing in the battle reads it, and
 * a ground objective in particular never does.
 *
 * <p>A hull's {@link Outcome} is the last thing that happened to it: still over the battle, escaped (it left by a
 * climb-out and was carried off the map by its own propulsion), broke off (the doctrine withdrew it, and it left),
 * departed (any other leaving: a visit's end, station time) or destroyed (when and where it burst, and where its
 * wreck struck). A hull destroyed while leaving was destroyed.
 *
 * <p>Ground casualties are counted by the side of the unit and by {@link Cause}: a stray round (a round fired at a
 * hull that reached none and came down on the ground) or wreckage (a falling hull's strike and skid). A unit hurt
 * counts once per landing that hurt it; a unit killed counts as killed and not as hurt.
 */
public final class WarshipDuelLedger {

    /** What became of a hull. */
    public enum Outcome { OVER_THE_BATTLE, ESCAPED, BROKE_OFF, DEPARTED, DESTROYED }

    /** What hurt the ground. */
    public enum Cause { STRAY_ROUND, WRECKAGE }

    /**
     * One hull's line. {@code seconds} and {@code (x, y)} are when and where the outcome happened (where it burst, or
     * where it was when it was gone); {@code struckX/Y} is where a destroyed hull's wreck struck, {@code NaN} until it
     * has. Battle seconds are the warship system's own clock.
     */
    public record Entry(int serial, String hullId, Faction faction, Outcome outcome, float seconds, float x, float y,
                        float struckX, float struckY) {}

    private static final class Line {
        final Warship ship;
        Outcome outcome = Outcome.OVER_THE_BATTLE;
        float seconds = Float.NaN;
        float x = Float.NaN;
        float y = Float.NaN;
        float struckX = Float.NaN;
        float struckY = Float.NaN;
        boolean leaving;
        boolean brokeOff;
        boolean climbing;

        Line(Warship ship) {
            this.ship = ship;
        }
    }

    private final Map<Warship, Line> lines = new IdentityHashMap<>();
    private final List<Line> order = new ArrayList<>();
    private final Map<Cause, Map<Faction, int[]>> ground = new EnumMap<>(Cause.class);

    WarshipDuelLedger() {
        for (Cause c : Cause.values()) ground.put(c, new EnumMap<>(Faction.class));
    }

    void register(Warship ship) {
        if (lines.containsKey(ship)) return;
        Line line = new Line(ship);
        lines.put(ship, line);
        order.add(line);
    }

    void destroyed(Warship ship, float seconds, float x, float y) {
        Line line = line(ship);
        line.outcome = Outcome.DESTROYED;
        line.seconds = seconds;
        line.x = x;
        line.y = y;
    }

    void struck(Warship ship, float x, float y) {
        Line line = line(ship);
        line.struckX = x;
        line.struckY = y;
    }

    /**
     * The hull is leaving: whether the doctrine withdrew it, and whether it is climbing out. Only the first report
     * counts, since why a hull began to leave is why it left.
     */
    void leaving(Warship ship, boolean brokeOff, boolean climbing) {
        Line line = line(ship);
        if (line.leaving) return;
        line.leaving = true;
        line.brokeOff = brokeOff;
        line.climbing = climbing;
    }

    /** The hull is gone from the battle, carried off the map ({@code physically}) or by the departure timeout. */
    void gone(Warship ship, float seconds, boolean physically) {
        Line line = line(ship);
        if (line.outcome != Outcome.OVER_THE_BATTLE) return;
        line.outcome = line.climbing && physically ? Outcome.ESCAPED
                : line.brokeOff ? Outcome.BROKE_OFF : Outcome.DEPARTED;
        line.seconds = seconds;
        line.x = ship.x;
        line.y = ship.y;
    }

    void ground(Cause cause, Faction side, boolean killed) {
        int[] tally = ground.get(cause).computeIfAbsent(side, f -> new int[2]);
        tally[killed ? 1 : 0]++;
    }

    private Line line(Warship ship) {
        Line line = lines.get(ship);
        if (line == null) {
            register(ship);
            line = lines.get(ship);
        }
        return line;
    }

    /** Every hull that has been over the battle, in the order it came. */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>(order.size());
        for (Line l : order) out.add(entry(l));
        return Collections.unmodifiableList(out);
    }

    /** This hull's line, or {@code null} when it was never over the battle. */
    public Entry entry(Warship ship) {
        Line l = lines.get(ship);
        return l == null ? null : entry(l);
    }

    private static Entry entry(Line l) {
        return new Entry(l.ship.serial(), l.ship.hullId, l.ship.faction, l.outcome, l.seconds, l.x, l.y,
                l.struckX, l.struckY);
    }

    /** Units of {@code side} hurt (and not killed) on the ground by {@code cause}, counted once per landing. */
    public int hurt(Cause cause, Faction side) {
        int[] t = ground.get(cause).get(side);
        return t == null ? 0 : t[0];
    }

    /** Units of {@code side} killed on the ground by {@code cause}. */
    public int killed(Cause cause, Faction side) {
        int[] t = ground.get(cause).get(side);
        return t == null ? 0 : t[1];
    }
}
