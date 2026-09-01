package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.Reflex;
import com.dillon.starsectormarines.battle.decision.ReflexContext;
import com.dillon.starsectormarines.battle.infantry.InfantryReflexes;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;

/**
 * The lance's reflex chain — what may pre-empt the doctrine step for one
 * chassis on one tick.
 *
 * <p>It has one entry, and its existence is the point. The mech dispatcher had
 * exactly the same seam as the marine one — an interrupt between "this unit has
 * a squad" and "execute the step" — expressed as a lone {@code if} that read
 * like an implementation detail rather than as the same tier. Declared as a
 * chain, the two arms now agree about what sits in that gap, and the next
 * chassis-level interrupt has a rank to be given rather than a branch to be
 * added above the loop.
 *
 * @see InfantryReflexes the marine's eight-entry chain
 */
public final class MechReflexes {

    private MechReflexes() {}

    /**
     * A player move order temporarily owns this exact chassis's locomotion, not
     * the squad plan. It keeps the ordinary targeting and weapon pass alive,
     * then releases before doctrine runs on arrival — so handback has no
     * plan-less interval.
     */
    public static final Reflex PLAYER_LOCOMOTION = new Reflex() {
        @Override public String name() { return "PLAYER_LOCOMOTION"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            return sim.getMechMoveOrderSystem().executeIfActive(unit, squad, sim);
        }
    };

    /** The lance's reflex chain, highest priority first. */
    public static final List<Reflex> CHAIN = List.of(PLAYER_LOCOMOTION);
}
