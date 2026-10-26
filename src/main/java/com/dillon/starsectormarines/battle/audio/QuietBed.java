package com.dillon.starsectormarines.battle.audio;

/**
 * The looped synth beds the quiet state can play (and aftermath by fallback). A mission names its
 * bed; one that names none gets {@link #DEFAULT}. All four are generated loops in the combat pool's
 * family, about 130 BPM around E minor, so the alarm's crossfade lands naturally (owner, 2026-10-26:
 * "These are all useful to keep for future missions").
 */
public enum QuietBed {
    /** A purposeful groove with a hinted theme: a mission the company walks into with a plan. */
    BRIEFING_DRIVE("marines_battle_bed_briefing_drive"),
    /** Cold, mechanical and the most neutral: any battle before contact. */
    SEQUENCER_PULSE("marines_battle_bed_sequencer_pulse"),
    /** Gritty outpost colour with a lonely guitar: frontier colonies, militia, dusty ground. */
    FRONTIER_TWANG("marines_battle_bed_frontier_twang"),
    /** Dark, sparse and stealthy: night raids, infiltration, waiting in the dark. */
    NIGHT_OPS("marines_battle_bed_night_ops");

    /** The bed for a battle whose mission names none. */
    public static final QuietBed DEFAULT = SEQUENCER_PULSE;

    private final String soundId;

    QuietBed(String soundId) { this.soundId = soundId; }

    /** The music sound set in {@code sounds.json}. */
    public String soundId() { return soundId; }
}
