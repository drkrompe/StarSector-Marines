package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.mech.MechDropAllowance;
import com.dillon.starsectormarines.battle.mech.MechDropDeclaration;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * The DEBUG board's statement of how many mech lances the attacking commander may call down, overriding the mission
 * type's own policy. A mission that states none ({@code null}) keeps that policy. Each value is a
 * {@link MechDropDeclaration} for the marines; the lance composition is the policy's, not the board's.
 */
public enum DebugMechDrop {
    NONE(0, MechDropAllowance.NO_DEBUG_CALL),
    ONE_LANCE(1, MechDropAllowance.NO_DEBUG_CALL),
    TWO_LANCES(2, MechDropAllowance.NO_DEBUG_CALL),
    /**
     * One lance, called at the drop zone {@link #AT_ONCE_TICK} ticks into the battle whatever the commander has
     * decided: the way to watch the delivery without a commander that asks for it.
     */
    ONE_LANCE_AT_ONCE(1, DebugMechDrop.AT_ONCE_TICK);

    /** Ten seconds in: after the first lift is down, before anything has been decided. */
    public static final int AT_ONCE_TICK = 300;

    private final MechDropDeclaration declaration;

    DebugMechDrop(int lances, int debugCallTick) {
        this.declaration = MechDropDeclaration.NONE.with(Faction.MARINE,
                new MechDropAllowance(lances, MissionDefencePolicy.CONQUEST_MECH_LANCE, debugCallTick));
    }

    /** The declaration this statement makes. */
    public MechDropDeclaration declaration() {
        return declaration;
    }
}
