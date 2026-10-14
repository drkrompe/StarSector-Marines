package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.airsupport.AirPoolAllowance;
import com.dillon.starsectormarines.battle.airsupport.AirPoolDeclaration;
import com.dillon.starsectormarines.battle.airsupport.AirPoolStock;
import com.dillon.starsectormarines.battle.unit.Faction;

/**
 * The DEBUG board's statement of which side holds a regenerating pool of bombers and fighters, and who directs it,
 * overriding the mission type's own policy (today none). A mission that states none ({@code null}) keeps that policy.
 * Pools are tasked by comms operators ({@code special.comms-radio}); a pool with no operator on its side is inert.
 *
 * <p>UNTUNED: the stock figures are first-pass debug values, not a mission-design statement.
 */
public enum DebugAirPool {
    /** No pool for anybody. */
    NONE,
    /** The marines hold a pool the player directs through the Air Strike power. */
    MARINES_PLAYER,
    /** The marines hold a pool their own AI directs. */
    MARINES_AI,
    /** The defenders hold a pool their own AI directs. */
    DEFENDERS_AI,
    /** Both sides hold a pool, each AI-directed. */
    BOTH_AI;

    /** Bombers at the start / most held / seconds to replace one. UNTUNED. */
    static final AirPoolStock BOMBERS = new AirPoolStock(2, 3, 60f);
    /** Fighters at the start / most held / seconds to replace one. UNTUNED. */
    static final AirPoolStock FIGHTERS = new AirPoolStock(1, 2, 45f);

    /** The declaration this statement makes. */
    public AirPoolDeclaration declaration() {
        AirPoolDeclaration declaration = AirPoolDeclaration.NONE;
        switch (this) {
            case MARINES_PLAYER -> declaration = declaration.with(Faction.MARINE, pool(false));
            case MARINES_AI -> declaration = declaration.with(Faction.MARINE, pool(true));
            case DEFENDERS_AI -> declaration = declaration.with(Faction.DEFENDER, pool(true));
            case BOTH_AI -> declaration = declaration.with(Faction.MARINE, pool(true))
                    .with(Faction.DEFENDER, pool(true));
            case NONE -> { }
        }
        return declaration;
    }

    private static AirPoolAllowance pool(boolean aiDirected) {
        return AirPoolAllowance.of(BOMBERS, FIGHTERS, aiDirected);
    }
}
