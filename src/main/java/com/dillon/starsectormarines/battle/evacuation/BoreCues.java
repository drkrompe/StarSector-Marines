package com.dillon.starsectormarines.battle.evacuation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the bores ask the presentation to sound: an eruption, a tier reached, a
 * collapse, a surge leaving the mouth (`breach-audio.md`, hook points).
 *
 * <p>The simulation only records here and never reads back, so a headless run
 * stays silent and nothing presented reaches the simulation. Requests last one
 * advance ({@link #beginFrame()}). The ambience loop is not a cue: a player
 * reads the living bores and their tiers every frame. Spore-colony bursts are not
 * cues either: a burst is a shot, and the vent weapon names its own fire sound.
 */
public final class BoreCues {

    /** What happened. */
    public enum Kind { ERUPTED, GREW, COLLAPSED, SURGE }

    /** One event at a bore's cell, on a simulation tick. */
    public record Cue(Kind kind, float x, float y, BoreTier tier, int tick) { }

    private final List<Cue> frame = new ArrayList<>();
    private final List<Cue> view = Collections.unmodifiableList(frame);

    /** Sound requests since the last {@link #beginFrame()}. */
    public List<Cue> cuesThisFrame() { return view; }

    /** Forgets last advance's requests. */
    public void beginFrame() { frame.clear(); }

    void add(Kind kind, SwarmBore bore, int tick) {
        frame.add(new Cue(kind, bore.x() + 0.5f, bore.y() + 0.5f, bore.tier(), tick));
    }
}
