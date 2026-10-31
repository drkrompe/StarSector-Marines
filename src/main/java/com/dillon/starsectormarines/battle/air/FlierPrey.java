package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;

/**
 * A flier ({@code UnitType.isFlier}, a Harrier) as a cover fighter's prey (`air-nouns.md`, Fighters and fliers).
 *
 * <p>A flier is a unit, not an aircraft: it has no air body and no sortie, and the ground's guns reach it by the weapon
 * rule ({@code EngagementService.weaponTracksFliers}). A fighter's nose guns are such a weapon, so a cover fighter may
 * chase one as it chases a hostile aircraft, through the same intercept and the same guns; what differs is only where
 * the flier is read from (its unit position, its motion taken from tick to tick) and how a round lands (the ordinary
 * damage route, attributed to the fighter).
 */
public final class FlierPrey {

    /** How high a flier is drawn for a tracer's end, as an air entity's altitude share. */
    public static final float VISUAL_ALTITUDE_T = 0.35f;

    private FlierPrey() {}

    /** Whether {@code id} is a live flier hostile to {@code mine}. */
    public static boolean hostile(UnitRosterService roster, long id, Faction mine) {
        if (id == 0L || mine == null || !roster.isAliveById(id) || !roster.isLive(id)) return false;
        return roster.identity().type(id).isFlier() && mine.hostileTo(roster.identity().faction(id));
    }
}
