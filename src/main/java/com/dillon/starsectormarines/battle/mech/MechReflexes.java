package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.Reflex;
import com.dillon.starsectormarines.battle.decision.ReflexContext;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.infantry.InfantryReflexes;
import com.dillon.starsectormarines.battle.mech.MechDamageDoctrine.Posture;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
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
 * whose legs are gone as a turret under any plan step. The third is the melee stand-off
 * ({@link #MELEE_STANDOFF}), which steps a walking mech back from a charging Rav.
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

    /** Cells within which a melee Rav makes a walking mech step back ({@link #MELEE_STANDOFF}). */
    static final float STANDOFF_CELLS = 6f;
    /** Cells a mech steps back, straight away from the melee Rav inside its stand-off. */
    static final int BACK_STEP_CELLS = 4;

    /**
     * A walking mech keeps a charging Rav at arm's length: with a Brute or Matriarch inside
     * {@link #STANDOFF_CELLS}, it steps {@link #BACK_STEP_CELLS} straight away from them, firing as it
     * goes, and leaves the charge to the infantry and its own guns. Its plan step otherwise walks it into
     * the charge to close on its aim (Broad Front verdant 3, 2026-10-25: three of the company's four mechs
     * fell to Brute charges by tick 9000; stepping back, all four stood at 15000). Ranked below the player's move and the crippled chassis: an ordered
     * mech goes where it was sent, and one without legs cannot step anywhere.
     */
    public static final Reflex MELEE_STANDOFF = new Reflex() {
        @Override public String name() { return "MELEE_STANDOFF"; }
        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            MechLoadoutComponent loadout = sim.world().mechLoadout(unit);
            Posture posture = MechDamageDoctrine.postureOf(loadout);
            if (!posture.walks()) return false;
            float[] away = awayFromMelee(unit, sim);
            if (away == null) return false;
            int[] step = backStep(sim.getGrid(), sim.world().cellX(unit), sim.world().cellY(unit), away[0], away[1]);
            if (step == null) return false;
            MechAssignmentBoundary.moveToward(unit, step[0], step[1], sim);
            fireFromHere(unit, posture, sim);
            return true;
        }
    };

    /** The lance's reflex chain, highest priority first. */
    public static final List<Reflex> CHAIN = List.of(PLAYER_LOCOMOTION, CRIPPLED_CHASSIS, MELEE_STANDOFF);

    private static final ThreadLocal<LongBucket> NEARBY = ThreadLocal.withInitial(LongBucket::new);

    /** Whether {@code type} fights in claw's reach: what a mech keeps at arm's length. */
    static boolean meleeRav(UnitType type) {
        return type == UnitType.SWARM_BRUTE || type == UnitType.SWARM_MATRIARCH;
    }

    /** The unit vector away from the melee Rav inside {@link #STANDOFF_CELLS}, or {@code null} when none is. */
    private static float[] awayFromMelee(long unit, BattleControl sim) {
        float ux = sim.world().x(unit), uy = sim.world().y(unit);
        LongBucket nearby = NEARBY.get();
        sim.getUnitIndex().gatherHostileCombatants(ux, uy, STANDOFF_CELLS, sim.identity().faction(unit), nearby);
        float ax = 0f, ay = 0f;
        boolean any = false;
        for (int i = 0, n = nearby.size; i < n; i++) {
            long other = nearby.ids[i];
            if (!meleeRav(sim.identity().type(other))) continue;
            float dx = ux - sim.world().x(other), dy = uy - sim.world().y(other);
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > STANDOFF_CELLS) continue;
            any = true;
            if (d > 1e-3f) {
                ax += dx / d;
                ay += dy / d;
            }
        }
        if (!any) return null;
        float length = (float) Math.sqrt(ax * ax + ay * ay);
        return length > 1e-3f ? new float[]{ax / length, ay / length} : null;
    }

    /**
     * The walkable cell nearest {@link #BACK_STEP_CELLS} from {@code (x, y)} along {@code (dirX, dirY)}, looked for
     * within two cells of that point, or {@code null} when the ground behind is closed.
     */
    static int[] backStep(NavigationGrid grid, int x, int y, float dirX, float dirY) {
        int tx = x + Math.round(dirX * BACK_STEP_CELLS), ty = y + Math.round(dirY * BACK_STEP_CELLS);
        for (int r = 0; r <= 2; r++) {
            for (int oy = -r; oy <= r; oy++) {
                for (int ox = -r; ox <= r; ox++) {
                    if (Math.max(Math.abs(ox), Math.abs(oy)) != r) continue;
                    int cx = tx + ox, cy = ty + oy;
                    if (grid.inBounds(cx, cy) && grid.isWalkable(cx, cy)) return new int[]{cx, cy};
                }
            }
        }
        return null;
    }

    private static void standFast(long unit, MechLoadoutComponent loadout, Posture posture,
                                  BattleControl sim) {
        if (loadout.routeIntent.pending() || !Paths.isEmpty(sim.world().path(unit))) {
            sim.clearPath(unit);
        }
        loadout.routeIntent.cancel();
        fireFromHere(unit, posture, sim);
    }

    /** Acquires and fires at whatever the chassis can engage from where it stands, or stands its target down. */
    private static void fireFromHere(long unit, Posture posture, BattleControl sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(unit);
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
