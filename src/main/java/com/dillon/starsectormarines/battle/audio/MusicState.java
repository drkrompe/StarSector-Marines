package com.dillon.starsectormarines.battle.audio;

/**
 * What the battle music is saying about the battle. A mission may declare one; otherwise
 * {@link BattleMusicDirector}'s heat rule chooses it.
 */
public enum MusicState {
    /** Before contact, or a stealth phase: a low bed, or silence under the map's ambient loops. */
    QUIET(0),
    /** Contact is near, or the fighting has lulled but not ended. */
    ALERT(1),
    /** The fighting: the combat pool. */
    COMBAT(2),
    /** The battle is won or lost. Ranked with quiet, so entering it lowers the music. */
    AFTERMATH(0);

    private final int intensity;

    MusicState(int intensity) { this.intensity = intensity; }

    /** True when moving from {@code from} to this state lifts the music, which may cut a fresh track. */
    public boolean raises(MusicState from) { return intensity > from.intensity; }
}
