package com.dillon.starsectormarines.battle.combat;

import com.dillon.starsectormarines.battle.modifier.UnitModifier;
import com.dillon.starsectormarines.battle.modifier.UnitStat;

import java.util.EnumSet;
import java.util.Set;

/**
 * A limb wound ({@link WoundService}): a leg wound slows the man, an arm wound costs him accuracy.
 * One source per limb, so the player reads which wound is costing what. Applies while the wound is
 * carried, so a medic's dressing ends it with nothing to restore.
 */
public final class WoundModifier implements UnitModifier {

    private final WoundService wounds;
    private final BodyPart limb;
    private final UnitStat stat;
    private final Set<UnitStat> stats;
    private final String name;

    private WoundModifier(WoundService wounds, BodyPart limb, UnitStat stat, String name) {
        this.wounds = wounds;
        this.limb = limb;
        this.stat = stat;
        this.stats = EnumSet.of(stat);
        this.name = name;
    }

    /** Leg wounds, on movement speed. */
    public static WoundModifier legs(WoundService wounds) {
        return new WoundModifier(wounds, BodyPart.LEGS, UnitStat.MOVE_SPEED, "unitModifierLegWound");
    }

    /** Arm wounds, on the accuracy of the man's own fire. */
    public static WoundModifier arms(WoundService wounds) {
        return new WoundModifier(wounds, BodyPart.ARMS, UnitStat.ACCURACY, "unitModifierArmWound");
    }

    @Override public String name(long unit) { return name; }

    @Override public Set<UnitStat> stats() { return stats; }

    @Override
    public boolean appliesTo(long unit) {
        return wounds.wounds(unit, limb) > 0;
    }

    @Override
    public float multiplier(long unit, UnitStat stat) {
        return stat == this.stat ? WoundService.multiplierFor(wounds.wounds(unit, limb)) : 1f;
    }
}
