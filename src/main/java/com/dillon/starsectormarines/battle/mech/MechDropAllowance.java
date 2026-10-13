package com.dillon.starsectormarines.battle.mech;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one side's commander may call down on its own authority: a count of mech
 * lances and the composition each lance is drawn in.
 *
 * <p>The mission declares it the way it declares its {@code GroundDefences}; nothing
 * about the map, the market or the force committed decides it. It is a payment, not
 * a power: the player's Mech Support spends command points and charges, a
 * commander-called drop spends one lance of this allowance, and the two never draw
 * on each other.
 *
 * @param lances        how many lances may be called, never negative
 * @param lance         the composition of every lance, one to {@link #MAX_LANCE_SIZE} variants; ignored (and
 *                      emptied) when {@code lances} is zero
 * @param debugCallTick a DEBUG/evidence statement: when {@code >= 0} the side's first lance is called at the drop
 *                      zone on that sim tick whatever its commander has decided, so the delivery can be watched
 *                      without a commander that asks for it. {@link #NO_DEBUG_CALL} (negative) is production
 */
public record MechDropAllowance(int lances, List<MechVariant> lance, int debugCallTick) {

    /** Largest lance one sortie unloads; equals {@code MechSupport.LANCE_SIZE}. */
    public static final int MAX_LANCE_SIZE = 4;

    /** The production value of {@link #debugCallTick}: nobody calls on this side's behalf. */
    public static final int NO_DEBUG_CALL = -1;

    public static final MechDropAllowance NONE = new MechDropAllowance(0, List.of());

    public MechDropAllowance {
        if (lances < 0) lances = 0;
        List<MechVariant> kept = new ArrayList<>();
        if (lance != null) {
            for (MechVariant variant : lance) {
                if (variant != null && kept.size() < MAX_LANCE_SIZE) kept.add(variant);
            }
        }
        if (lances == 0 || kept.isEmpty()) {
            lances = 0;
            kept.clear();
        }
        if (debugCallTick < 0) debugCallTick = NO_DEBUG_CALL;
        lance = Collections.unmodifiableList(kept);
    }

    /** An allowance nobody calls on a side's behalf: the production shape. */
    public MechDropAllowance(int lances, List<MechVariant> lance) {
        this(lances, lance, NO_DEBUG_CALL);
    }

    /** This allowance with its first lance called on {@code tick}. */
    public MechDropAllowance calledAtTick(int tick) {
        return new MechDropAllowance(lances, lance, tick);
    }

    /** Whether this allows anything at all. */
    public boolean isNone() {
        return lances == 0;
    }
}
