package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A side's alarms on its own strategic places, starting with forts: what keeps a fort's pressure up while nobody can
 * see the attack on it.
 *
 * <p>A commander's pressure is belief, and belief is what somebody saw. An attack inside a fort's walls, or one that
 * killed the picket, is seen by nobody, so the pressure on the fort fades and its garrison is released exactly when it
 * is needed. But a commander knows its own places: that the picket was lost, that the bunker is being taken, that guns
 * there are firing. Those are <b>triggers</b>, own-force facts of the same class as a side's memory of its own
 * casualties, and a trigger raises an <b>alarm</b> on the place. While alarmed, the place's blocks carry a pressure
 * floor that fades with a long half-life, so the conflict is retained on ground the commander holds to matter and
 * nowhere else. Each new trigger refreshes it.
 *
 * <p>Triggers, per fort the side built: an aspect being taken or already lost; a rise in the side's own losses on the
 * fort's ground; believed hostile pressure on it at the manning level. A fort whose every aspect is held by the enemy is
 * a <b>lost cause</b>: its alarm is dropped and the floor goes with it, so squads are no longer fed into it piecemeal;
 * whether to retake it is the structure planner's question.
 *
 * <p>State is per commander and keyed by fort id. Deterministic: forts are walked in id order.
 */
final class PlaceAlarms {

    /** Whether alarms are kept ({@code battle.command.placeAlarms}, default on). Off is the belief-only control. */
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("battle.command.placeAlarms", "true"));
    /** The floor a fresh alarm puts under its place's pressure: enough to man it. */
    static final float ALARM_PRESSURE = GarrisonDirector.START_PRESSURE;
    /** Ticks for the floor to halve: 60 s. Below the release level after a little over three. UNTUNED. */
    static final float HALF_LIFE_TICKS = 1800f;
    /** Rise in the side's own loss weight on a fort's ground, between two updates, that is a trigger. UNTUNED. */
    static final float LOSS_TRIGGER = 0.5f;

    /** One fort's alarm. */
    private static final class Alarm {
        final long[] blocks;
        int triggeredTick = Integer.MIN_VALUE;
        float lossesBefore = -1f;
        boolean lost;
        String cause = "";

        Alarm(long[] blocks) {
            this.blocks = blocks;
        }
    }

    /** A side's believed reading of a block, the alarm's other input. */
    interface Reading {
        float hostileAt(int blockX, int blockY);

        float lossesAt(int blockX, int blockY);
    }

    private final Map<Integer, Alarm> alarms = new TreeMap<>();
    private int tick;

    /**
     * Brings the alarms up to {@code now} from the side's own structure facts and its reading of the field. Forts the
     * side did not build are ignored: a captured enemy fort is a different question.
     */
    void update(int now, Faction side, List<DefenceStructureFact> structures, Reading reading, int blockSize,
                int blocksWide, int blocksHigh) {
        tick = now;
        if (!ENABLED) return;
        Map<Integer, List<DefenceStructureFact>> forts = new TreeMap<>();
        for (DefenceStructureFact fact : structures) {
            if (!fact.ofFort() || fact.originalHolder() != side) continue;
            forts.computeIfAbsent(fact.fortId(), id -> new ArrayList<>()).add(fact);
        }
        for (Map.Entry<Integer, List<DefenceStructureFact>> entry : forts.entrySet()) {
            List<DefenceStructureFact> aspects = entry.getValue();
            Alarm alarm = alarms.computeIfAbsent(entry.getKey(),
                    id -> new Alarm(blocksOf(aspects, blockSize, blocksWide, blocksHigh)));
            boolean anyHeld = false;
            String cause = null;
            for (DefenceStructureFact aspect : aspects) {
                if (aspect.holder() == side) anyHeld = true;
                if (cause == null && aspect.holder() != side) cause = aspect.kind() + " lost";
                if (cause == null && aspect.threatened()) cause = aspect.kind() + " being taken";
            }
            alarm.lost = !anyHeld;
            if (alarm.lost) {
                alarm.cause = "lost cause";
                continue;
            }
            float losses = 0f;
            float hostile = 0f;
            for (long block : alarm.blocks) {
                int bx = (int) (block >>> 32);
                int by = (int) block;
                losses += reading.lossesAt(bx, by);
                hostile = Math.max(hostile, reading.hostileAt(bx, by));
            }
            if (cause == null && alarm.lossesBefore >= 0f && losses - alarm.lossesBefore >= LOSS_TRIGGER) {
                cause = "own losses";
            }
            alarm.lossesBefore = losses;
            if (cause == null && hostile >= GarrisonDirector.START_PRESSURE) cause = "pressed";
            if (cause != null) {
                alarm.triggeredTick = now;
                alarm.cause = cause;
            }
        }
    }

    /** The pressure floor an alarm puts on this block now; {@code 0} where no live alarm covers it. */
    float floorAt(int blockX, int blockY) {
        if (!ENABLED) return 0f;
        float floor = 0f;
        long key = key(blockX, blockY);
        for (Alarm alarm : alarms.values()) {
            float level = level(alarm);
            if (level <= floor) continue;
            for (long block : alarm.blocks) {
                if (block == key) {
                    floor = level;
                    break;
                }
            }
        }
        return floor;
    }

    /** Every block under a live alarm, with its floor, for a caller seeding pressure areas: {@code [bx, by]} pairs. */
    List<int[]> alarmedBlocks() {
        List<int[]> out = new ArrayList<>();
        if (!ENABLED) return out;
        for (Alarm alarm : alarms.values()) {
            if (level(alarm) < GarrisonDirector.RELEASE_PRESSURE) continue;
            for (long block : alarm.blocks) out.add(new int[]{(int) (block >>> 32), (int) block});
        }
        return out;
    }

    /** One line per fort with an alarm that has ever triggered, for the trace. */
    String describe() {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<Integer, Alarm> entry : alarms.entrySet()) {
            Alarm alarm = entry.getValue();
            if (alarm.triggeredTick == Integer.MIN_VALUE && !alarm.lost) continue;
            if (out.length() > 0) out.append(", ");
            out.append("fort ").append(entry.getKey()).append(' ')
                    .append(alarm.lost ? "lost" : String.format(Locale.ROOT, "%.2f", level(alarm)))
                    .append(" (").append(alarm.cause).append(')');
        }
        return out.toString();
    }

    private float level(Alarm alarm) {
        if (alarm.lost || alarm.triggeredTick == Integer.MIN_VALUE) return 0f;
        int elapsed = Math.max(0, tick - alarm.triggeredTick);
        return ALARM_PRESSURE * (float) Math.pow(0.5, elapsed / HALF_LIFE_TICKS);
    }

    /** The blocks a fort covers: its belt and its aspects' anchors. */
    private static long[] blocksOf(List<DefenceStructureFact> aspects, int blockSize, int blocksWide, int blocksHigh) {
        TreeSet<Long> blocks = new TreeSet<>();
        for (DefenceStructureFact aspect : aspects) {
            add(blocks, aspect.cellX(), aspect.cellY(), blockSize, blocksWide, blocksHigh);
            int[] belt = aspect.beltCells();
            for (int i = 0; i + 1 < belt.length; i += 2) add(blocks, belt[i], belt[i + 1], blockSize, blocksWide, blocksHigh);
        }
        long[] out = new long[blocks.size()];
        int i = 0;
        for (long block : blocks) out[i++] = block;
        return out;
    }

    private static void add(Set<Long> blocks, int x, int y, int blockSize, int blocksWide, int blocksHigh) {
        int bx = Math.min(blocksWide - 1, Math.max(0, x / blockSize));
        int by = Math.min(blocksHigh - 1, Math.max(0, y / blockSize));
        blocks.add(key(bx, by));
    }

    private static long key(int bx, int by) {
        return ((long) bx << 32) | (by & 0xffffffffL);
    }
}
