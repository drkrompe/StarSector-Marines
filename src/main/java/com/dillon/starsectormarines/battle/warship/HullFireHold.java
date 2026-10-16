package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;

import java.util.function.Predicate;

/**
 * A mission's hold on hull fire: while any hold is pending, no ship under {@link ShipFireOrder#AUTO} fires at a hull,
 * and once it is released it stays released for the rest of the battle. A hold is named, so evidence and a debrief can
 * say what the guns were waiting for, and it is released by a story beat ({@link #untilBeat}), by a condition read off
 * the battle each tick ({@link #until}), or by the mission itself ({@link #untilReleased} and {@link #release}).
 *
 * <p>A hold is not an outcome: it decides only when the ships are free to fight each other, never who wins. Ground
 * fire, air fire and an explicit {@link ShipFireOrder#ALL} are not held.
 */
public final class HullFireHold {

    private final String name;
    private final Predicate<BattleSimulation> releaseWhen;
    private boolean released;
    private int releasedTick = -1;

    private HullFireHold(String name, Predicate<BattleSimulation> releaseWhen) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("a hull-fire hold needs a name");
        this.name = name;
        this.releaseWhen = releaseWhen;
    }

    /** Held until the battle has raised the story beat {@code beatId}; the hold is named after the beat. */
    public static HullFireHold untilBeat(String beatId) {
        return new HullFireHold(beatId, sim -> raised(sim, beatId));
    }

    /** Held until {@code condition} first reads true of the battle. */
    public static HullFireHold until(String name, Predicate<BattleSimulation> condition) {
        if (condition == null) throw new IllegalArgumentException("a hull-fire hold needs a condition");
        return new HullFireHold(name, condition);
    }

    /** Held until the mission calls {@link #release} (or {@link WarshipSystem#releaseHullFire}). */
    public static HullFireHold untilReleased(String name) {
        return new HullFireHold(name, sim -> false);
    }

    private static boolean raised(BattleSimulation sim, String beatId) {
        for (StoryBeatLog.Event e : sim.getStoryBeats()) if (e.id().equals(beatId)) return true;
        return false;
    }

    public String name() {
        return name;
    }

    public boolean released() {
        return released;
    }

    /** The tick the hold was released on, or {@code -1} while it holds (or when released outside a tick). */
    public int releasedTick() {
        return releasedTick;
    }

    /** Releases the hold now, whatever its condition says. */
    public void release() {
        released = true;
    }

    /** Reads the condition once; returns whether the hold is still holding. */
    boolean holds(BattleSimulation sim) {
        if (!released && releaseWhen.test(sim)) {
            released = true;
            releasedTick = sim.getSimTickIndex();
        }
        return !released;
    }
}
