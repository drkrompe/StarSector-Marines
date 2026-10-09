package com.dillon.starsectormarines.marine;

import java.io.Serializable;

/** Saved, immutable pairing of a weapon loadout and an armour tactic sheet. */
public final class SquadProfile implements Serializable {
    private final String id;
    private final String name;
    private final String weaponDoctrineId;
    private final String armorDoctrineId;

    public SquadProfile(String id, String name, String weaponDoctrineId, String armorDoctrineId) {
        this.id = id;
        this.name = name;
        this.weaponDoctrineId = weaponDoctrineId;
        this.armorDoctrineId = armorDoctrineId;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String weaponDoctrineId() { return weaponDoctrineId; }
    public String armorDoctrineId() { return armorDoctrineId; }
}
