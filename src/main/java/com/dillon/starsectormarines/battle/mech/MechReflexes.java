package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.Reflex;
import com.dillon.starsectormarines.battle.decision.ReflexContext;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.InfantryReflexes;
import com.dillon.starsectormarines.battle.mech.MechDamageDoctrine.Posture;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.squad.Squad;

import java.util.List;

/**
 * The lance's reflex chain — what may pre-empt the doctrine step for one
 * chassis on one tick.
 *
 * <p>It began with one entry, and its existence is the point. The mech dispatcher had
 * exactly the same seam as the marine one — an interrupt between "this unit has
 * a squad" and "execute the step" — expressed as a lone {@code if} that read
 * like an implementation detail rather than as the same tier. Declared as a
 * chain, the two arms now agree about what sits in that gap, and the next
 * chassis-level interrupt has a rank to be given rather than a branch to be
 * added above the loop.
 *
 * <p>The second entry is the crippled chassis ({@link #CRIPPLED_CHASSIS}), which holds a mech
 * whose legs are gone as a turret under any plan step.
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

    /**
     * A crippled chassis is a turret, whatever step its lance is executing. It holds where it
     * stands, drops any path or pending route it owned, and — while it still has a weapon —
     * acquires and fires at whatever it can engage from there; the torso and the planted hips
     * turn onto that target as they always do. It never authors movement, so no plan step,
     * doctrine or order can walk a chassis whose legs are gone. Ranked below the player's move,
     * which releases itself for a crippled chassis rather than driving legs that cannot carry it.
     */
    public static final Reflex CRIPPLED_CHASSIS = new Reflex() {
        @Override public String name() { return "CRIPPLED_CHASSIS"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            MechLoadoutComponent loadout = sim.world().mechLoadout(unit);
            Posture posture = MechDamageDoctrine.postureOf(loadout);
            if (posture.walks()) return false;
            standFast(unit, loadout, posture, sim);
            return true;
        }
    };

    /** The lance's reflex chain, highest priority first. */
    public static final List<Reflex> CHAIN = List.of(PLAYER_LOCOMOTION, CRIPPLED_CHASSIS);

    private static void standFast(long unit, MechLoadoutComponent loadout, Posture posture,
                                  BattleControl sim) {
        if (loadout.routeIntent.pending() || !Paths.isEmpty(sim.world().path(unit))) {
            sim.clearPath(unit);
        }
        loadout.routeIntent.cancel();
        if (!posture.attacks()) {
            sim.world().setTargetId(unit, 0L);
            return;
        }
        long target = MechTargeting.refreshTarget(unit, sim);
        sim.world().setTargetId(unit, target);
        if (target == 0L) return;
        float distance = TacticalScoring.cellDistance(sim.world().x(unit), sim.world().y(unit),
                sim.world().x(target), sim.world().y(target));
        if (distance > sim.world().attackRange(unit)) return;
        boolean visible = sim.getTacticalScoring().hasClearShot(unit, target);
        MechCombatantBehavior.tryFireMechWeapons(unit, loadout, target, distance, sim, visible);
    }
}
