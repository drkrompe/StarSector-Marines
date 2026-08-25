package com.dillon.starsectormarines.battle.command;

/** Authority that owns one squad-level strategic assignment. */
public enum CommandAuthority {
    MISSION_COMMAND(100),
    PLAYER_INTERVENTION(200),
    REINFORCEMENT(300),
    PAYLOAD(400),
    GARRISON(500),
    SCRIPTED(600);

    private final int priority;

    CommandAuthority(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
