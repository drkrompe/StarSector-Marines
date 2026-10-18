package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Band;
import com.dillon.starsectormarines.battle.mech.MechDuelBand.Weapon;
import com.dillon.starsectormarines.battle.mech.components.MechLoadoutComponent;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.BodyCarrier;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.ArrayList;
import java.util.List;

/**
 * The duel-range rule: a mech whose current target is armor — another mech or
 * a ground vehicle — fights it from the band {@link MechDuelBand} chooses
 * rather than from wherever its doctrine's infantry geometry would take it.
 *
 * <p>It runs inside {@link ExecuteMechDoctrine}, below every reflex, so a
 * player's move order still owns the chassis and a crippled one is still a
 * turret. It never leaves command ground: every cell it walks to must pass
 * {@link MechAssignmentBoundary#permitsCell}, and a mech standing outside its
 * command (an exact cell not yet reached) is handed back to its doctrine. A
 * Frontline Support mech with a legal anchor keeps pacing that anchor; the
 * rule is for the duel, not for the escort.
 *
 * <p>Inside its band, with a clear shot, the mech holds and fires, and steps
 * once into cover facing the target if a covered in-band cell is a few cells
 * off (hull-down: the section rows put low cover over the legs). Outside it,
 * it looks a short way for an in-band firing cell, or failing that a cell
 * nearer the band, and while the target can reach it never takes a walk that
 * turns its back on the target. Too close with nowhere legal to open
 * distance, it holds and fights; too far with nowhere legal to go, its
 * doctrine advances it as before. An armored contact shelling it from beyond
 * its own reach is a duel too: the band decides whether it closes on where
 * that contact was last seen.
 *
 * <p>{@code -Dbattle.mech.duelRange=false} is the control.
 */
public final class MechDuelRange {

    public static final String PROPERTY = "battle.mech.duelRange";

    /** Volatile and settable so a scene's control loop can flip it between loops. */
    private static volatile boolean enabled =
            Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /** Cells either side of the band a holding mech tolerates before it moves. */
    static final float HOLD_SLACK = 1f;
    /** How far from where it stands a mech looks for a cell, Chebyshev. */
    static final int SEARCH_RADIUS = 12;
    /** How far a mech already in band will step for cover. */
    static final int COVER_HOP_RADIUS = 4;
    /** Walk cost one level of cover is worth. */
    static final float COVER_WEIGHT = 2.5f;
    /** Widest angle between the walk and the target a threatened mech accepts: past this its back is turned. */
    static final float MAX_WALK_OFF_TARGET_DEGREES = 120f;

    private MechDuelRange() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabledForEvidence(boolean value) {
        enabled = value;
    }

    /**
     * Executes the duel for this tick when the rule applies, and returns true
     * when it did; false hands the tick to the member's doctrine unchanged.
     */
    static boolean engage(long member, Squad squad, BattleControl sim) {
        if (!enabled) return false;
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        if (loadout == null) return false;
        MechRole role = loadout.effectiveRole();
        if (role == MechRole.ARMORED_SUPPORT
                && BackstopAssignedSquad.pickBackedSquad(member, squad, sim) != null) {
            return false;
        }
        int cellX = sim.world().cellX(member);
        int cellY = sim.world().cellY(member);
        if (!MechAssignmentBoundary.permitsCell(member, squad, cellX, cellY, sim)) return false;

        List<Weapon> own = ownWeapons(loadout);
        long target = MechTargeting.refreshTarget(member, sim);
        boolean engaged = target != 0L;
        float tx;
        float ty;
        if (engaged) {
            tx = sim.world().x(target);
            ty = sim.world().y(target);
        } else {
            BelievedContact contact = outReachingContact(member, squad, own, sim);
            if (contact == null) return false;
            target = contact.unitId();
            tx = contact.lastSeenCellX() + 0.5f;
            ty = contact.lastSeenCellY() + 0.5f;
        }
        List<Weapon> threat = threatOf(target, sim);
        if (threat == null) return false;
        Band band = MechDuelBand.choose(own, threat, role);
        if (band == null) return false;

        float distance = TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member), tx, ty);
        boolean visible = false;
        if (engaged) {
            sim.world().setTargetId(member, target);
            visible = sim.getTacticalScoring().hasClearShot(member, target);
            if (distance <= sim.world().attackRange(member)) {
                MechCombatantBehavior.tryFireMechWeapons(member, loadout, target, distance, sim, visible);
            }
        }

        MechRouteIntent route = MechRouteIntent.forMember(member, MechDuelRange.class, target, sim);
        boolean inBand = visible && band.contains(distance, HOLD_SLACK);
        int[] path = sim.world().path(member);
        boolean walking = !Paths.isEmpty(path) && sim.world().pathIdx(member) < Paths.cellCount(path);
        float error = bandError(band, distance);
        float destinationError = walking
                ? bandError(band, TacticalScoring.cellDistance(
                        Paths.destX(path) + 0.5f, Paths.destY(path) + 0.5f, tx, ty))
                : Float.MAX_VALUE;

        if (inBand) {
            if (walking && destinationError == 0f) {
                sim.advanceMovement(member);
                return true;
            }
            if (walking || route.pending()) {
                // In band but bound somewhere that is not: stop where it stands.
                sim.clearPath(member);
                route.cancel();
                return true;
            }
            if (coverAt(cellX, cellY, tx, ty, sim) <= 0 && sim.movement().mayRepath(member)) {
                int[] hop = findBandCell(member, squad, target, tx, ty, band, 0f, COVER_HOP_RADIUS, true, sim);
                if (hop != null && (hop[0] != cellX || hop[1] != cellY)) {
                    route.moveToward(member, hop[0], hop[1], sim);
                }
            }
            return true;
        }

        if (route.resume(member, sim)) return true;
        boolean progressing = walking && destinationError < error - 0.5f;
        if (!progressing && sim.movement().mayRepath(member)) {
            int[] dest = findBandCell(member, squad, target, tx, ty, band, error, SEARCH_RADIUS, false, sim);
            if (dest != null) {
                route.moveToward(member, dest[0], dest[1], sim);
                return true;
            }
            if (engaged && distance < band.min()) {
                // Too close, and nowhere legal to open distance: hold and fight.
                if (walking) sim.clearPath(member);
                route.cancel();
                return true;
            }
            return false;
        }
        if (walking) sim.advanceMovement(member);
        return true;
    }

    /** Cells outside the band, zero inside it. */
    static float bandError(Band band, float distance) {
        return Math.max(0f, band.min() - distance) + Math.max(0f, distance - band.max());
    }

    /**
     * The nearest believed armored contact that can reach this mech from
     * where it was last seen while this mech cannot reach it back, or null.
     */
    static BelievedContact outReachingContact(long member, Squad squad, List<Weapon> own, BattleView sim) {
        if (squad == null) return null;
        float reach = MechDuelBand.reach(own);
        float mx = sim.world().x(member);
        float my = sim.world().y(member);
        BelievedContact best = null;
        float bestDistance = Float.MAX_VALUE;
        for (BelievedContact contact : squad.believedContacts()) {
            long id = contact.unitId();
            if (id == 0L || !sim.canEngage(member, id)
                    || !sim.identity().faction(member).hostileTo(sim.identity().faction(id))) continue;
            float d = TacticalScoring.cellDistance(mx, my,
                    contact.lastSeenCellX() + 0.5f, contact.lastSeenCellY() + 0.5f);
            if (d <= reach || d >= bestDistance) continue;
            List<Weapon> threat = threatOf(id, sim);
            if (threat == null || MechDuelBand.reach(threat) < d - HOLD_SLACK) continue;
            best = contact;
            bestDistance = d;
        }
        return best;
    }

    /**
     * The best cell within {@code radius} of the mech. An in-band cell needs
     * line of fire to the target; short of one, a cell that brings the mech at
     * least a cell nearer the band than {@code currentError} serves as a
     * waypoint. Every cell is inside command and reachable, and while the
     * target can reach the mech, none is a walk that turns its back. Cover
     * facing the target buys walk; {@code requireCover} keeps only covered
     * in-band cells.
     */
    static int[] findBandCell(long member, Squad squad, long target, float tx, float ty, Band band,
                              float currentError, int radius, boolean requireCover, BattleView sim) {
        NavigationGrid grid = sim.getGrid();
        int memberX = sim.world().cellX(member);
        int memberY = sim.world().cellY(member);
        MechReachability reachable = new MechReachability(grid, memberX, memberY);
        if (!reachable.contains(memberX, memberY)) return null;
        float mx = sim.world().x(member);
        float my = sim.world().y(member);
        float toTargetX = tx - mx;
        float toTargetY = ty - my;
        float toTarget = (float) Math.hypot(toTargetX, toTargetY);
        boolean threatened = toTarget <= threatReach(target, sim);
        float minCos = (float) Math.cos(Math.toRadians(MAX_WALK_OFF_TARGET_DEGREES));
        int[] best = null;
        float bestScore = Float.MAX_VALUE;
        for (int y = memberY - radius; y <= memberY + radius; y++) {
            for (int x = memberX - radius; x <= memberX + radius; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                float cx = x + 0.5f;
                float cy = y + 0.5f;
                float d = TacticalScoring.cellDistance(cx, cy, tx, ty);
                float error = bandError(band, d);
                if (error > 0f && (requireCover || error > currentError - 1f)) continue;
                int cover = error == 0f ? coverAt(x, y, tx, ty, sim) : 0;
                if (requireCover && cover <= 0) continue;
                float walkX = cx - mx;
                float walkY = cy - my;
                float walk = (float) Math.hypot(walkX, walkY);
                if (threatened && walk > 1f && toTarget > 0f
                        && (walkX * toTargetX + walkY * toTargetY) / (walk * toTarget) < minCos) continue;
                float score = error * 3f + walk + Math.abs(d - band.middle()) * 0.3f - cover * COVER_WEIGHT;
                if (score >= bestScore) continue;
                if (!reachable.contains(x, y)
                        || error == 0f && !grid.hasLineOfFire(cx, cy, tx, ty)
                        || !MechAssignmentBoundary.permitsCell(member, squad, x, y, sim)
                        || !MechRouteIntent.candidate(member, x, y, sim)) continue;
                bestScore = score;
                best = new int[]{x, y};
            }
        }
        return best;
    }

    private static int coverAt(int x, int y, float tx, float ty, BattleView sim) {
        int dx = (int) Math.floor(tx) - x;
        int dy = (int) Math.floor(ty) - y;
        return sim.getGrid().getCoverAt(x, y, dx, dy) + sim.getDoodadCoverAt(x, y, dx, dy);
    }

    /** Installed weapons that can still fire. */
    public static List<Weapon> ownWeapons(MechLoadoutComponent loadout) {
        List<Weapon> out = new ArrayList<>(3);
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && (mount.hasAmmo() || mount.burstRemaining > 0)) {
                out.add(Weapon.of(mount.weaponDef()));
            }
        }
        return out;
    }

    /**
     * The target's weapons, if it is armor: a mech's live mounts, or a ground
     * vehicle's turret (none for an unarmed hull). Null for anything else,
     * which the rule leaves to doctrine.
     */
    static List<Weapon> threatOf(long target, BattleView sim) {
        if (sim.world().hasMechLoadout(target)) {
            return ownWeapons(sim.world().mechLoadout(target));
        }
        BodyCarrier carrier = sim.bodies().carrierOf(target);
        if (carrier instanceof ConvoyService convoy) {
            VehicleType type = convoy.vehicleType(target);
            if (type == null) return null;
            List<Weapon> out = new ArrayList<>(1);
            if (type.hasTurretWeapon()) out.add(Weapon.of(type.turretStructure().mount.weapon));
            return out;
        }
        return null;
    }

    private static float threatReach(long target, BattleView sim) {
        List<Weapon> threat = threatOf(target, sim);
        return threat == null ? 0f : MechDuelBand.reach(threat);
    }
}
