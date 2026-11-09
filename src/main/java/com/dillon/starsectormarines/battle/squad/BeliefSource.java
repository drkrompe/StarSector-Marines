package com.dillon.starsectormarines.battle.squad;

/** Most recent evidence source behind a remembered hostile contact. */
public enum BeliefSource {
    DIRECT,
    AUDIO,
    /**
     * Relayed by a spotter the squad does not hold under its own eyes: a derelict flock's sighting handed to a siege
     * frame (`derelict-security-network.md`, slice 4). Indirect fire may be laid on it without a line of sight.
     */
    SPOTTED
}
