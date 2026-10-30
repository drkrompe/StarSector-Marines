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
 * that contact was last seen, or, while its rounds are landing, on where they
 * are coming from. A long-reach shooter is usually beyond sight as well, and
 * the cell it was last seen in is wherever it stood before it backed off.
 *
 * <p>A mech holding a post (defend area, hold node) may <em>sortie</em>: when
 * an armored contact out-reaches it from ground that can shell the leash, and
 * it cannot reach the shooter from where it stands, its command stretches to
 * the ground that attacker can reach the leash from. Driving the shooter off is
 * defending the post; standing at the edge of the leash on open ground and
 * taking fire it cannot return is not. Once the shooter is beyond reach of the
 * leash the sortie ends and the mech's doctrine walks it home. Exact-cell
 * moves, rushes, attack-moves and posts not yet reached stay strict.
 */
public final class MechDuelRange {


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

    /** Incoming pressure (a round or so, decaying with a two-second half-life) that counts as being under fire. */
    static final float UNDER_FIRE_PRESSURE = 0.5f;
    /** A hit this recent (ten seconds, longer than a missile rack reloads) keeps the fire's origin fresh. */
    static final int UNDER_FIRE_TICKS = 300;
    /** Puts any last-seen contact behind every contact that explains incoming fire. */
    private static final float FIRE_RANK_OFFSET = 10_000f;

    private MechDuelRange() {}


    /**
     * Executes the duel for this tick when the rule applies, and returns true
     * when it did; false hands the tick to the member's doctrine unchanged.
     */
    static boolean engage(long member, Squad squad, BattleControl sim) {
        MechLoadoutComponent loadout = sim.world().mechLoadout(member);
        if (loadout == null) return false;
        MechRole role = loadout.effectiveRole();
        if (role == MechRole.ARMORED_SUPPORT
                && BackstopAssignedSquad.pickBackedSquad(member, squad, sim) != null) {
            return false;
        }
        int cellX = sim.world().cellX(member);
        int cellY = sim.world().cellY(member);
        boolean inCommand = MechAssignmentBoundary.permitsCell(member, squad, cellX, cellY, sim);
        int[] post = MechAssignmentBoundary.heldPost(member, squad, sim);
        if (!inCommand && post == null) return false;

        List<Weapon> own = ownWeapons(loadout);
        long target = MechTargeting.refreshTarget(member, sim);
        boolean engaged = target != 0L;
        float tx;
        float ty;
        if (engaged) {
            tx = sim.world().x(target);
            ty = sim.world().y(target);
        } else {
            Threat threat = outReachingThreat(member, squad, own, sim);
            if (threat == null) return false;
            target = threat.id();
            tx = threat.x();
            ty = threat.y();
        }
        own = ownWeapons(loadout, armorRatingOf(target, sim));
        List<Weapon> threat = threatOf(target, sim, armorRatingOf(member, sim));
        if (threat == null) return false;
        Band band = MechDuelBand.choose(own, threat, role);
        if (band == null) return false;
        float sortie = post == null ? 0f
                : sortieRadius(post, tx, ty, inCommand,
                        TacticalScoring.cellDistance(sim.world().x(member), sim.world().y(member), tx, ty),
                        MechDuelBand.reach(own), MechDuelBand.reach(threat));
        if (!inCommand && sortie <= 0f) return false;
        Command command = new Command(squad, post, sortie);

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
                int[] hop = findBandCell(member, command, target, tx, ty, band, 0f, COVER_HOP_RADIUS, true, sim);
                if (hop != null && (hop[0] != cellX || hop[1] != cellY)) {
                    route.moveToward(member, hop[0], hop[1], sim);
                }
            }
            return true;
        }

        if (route.resume(member, sim)) return true;
        boolean progressing = walking && destinationError < error - 0.5f;
        if (!progressing && sim.movement().mayRepath(member)) {
            int[] dest = findBandCell(member, command, target, tx, ty, band, error, SEARCH_RADIUS, false, sim);
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

    /**
     * How far from its post a holding mech may go to answer an attacker at
     * {@code (tx, ty)}, or zero when it may not leave its leash: the attacker
     * must out-reach it and be able to shell the leash, and a mech still
     * inside its leash leaves only when it cannot reach the attacker from
     * where it stands. Asking whether some cell of the leash could answer
     * instead strands it at the leash's edge: a shooter that out-reaches it
     * simply steps back as it arrives. Once out, it stays out while the
     * attacker can still shell the leash. The answer is the ground the
     * attacker can shell the leash from.
     */
    static float sortieRadius(int[] post, float tx, float ty, boolean inLeash, float distance,
                              float ownReach, float threatReach) {
        float leash = MechAssignmentBoundary.EXACT_CELL_LEASH;
        if (threatReach < ownReach + HOLD_SLACK) return 0f;
        float fromPost = TacticalScoring.cellDistance(post[0] + 0.5f, post[1] + 0.5f, tx, ty);
        if (fromPost > leash + threatReach) return 0f;
        if (inLeash && distance <= ownReach) return 0f;
        return leash + threatReach;
    }

    /** Where this mech may stand this tick: its command, or a sortie around its post. */
    record Command(Squad squad, int[] post, float sortie) {
        boolean permits(long member, int x, int y, BattleView sim) {
            if (sortie > 0f && TacticalScoring.cellDistance(x + 0.5f, y + 0.5f,
                    post[0] + 0.5f, post[1] + 0.5f) <= sortie) return true;
            return MechAssignmentBoundary.permitsCell(member, squad, x, y, sim);
        }
    }

    /** Cells outside the band, zero inside it. */
    static float bandError(Band band, float distance) {
        return Math.max(0f, band.min() - distance) + Math.max(0f, distance - band.max());
    }

    /** A believed armored contact and where this mech should take it to be. */
    record Threat(long id, float x, float y) { }

    /**
     * The armored contact that out-reaches this mech, or null: one that can
     * reach it from where it is believed to stand while this mech cannot reach
     * it back. While this mech is under fire it is believed to stand where the
     * rounds come from, taken as the believed contact that could have fired
     * them from there; otherwise the nearest such contact where it was last
     * seen.
     */
    static Threat outReachingThreat(long member, Squad squad, List<Weapon> own, BattleView sim) {
        if (squad == null) return null;
        float reach = MechDuelBand.reach(own);
        float mx = sim.world().x(member);
        float my = sim.world().y(member);
        int tick = sim.getSimTickIndex();
        boolean underFire = sim.combat().incomingPressure(member, tick) >= UNDER_FIRE_PRESSURE
                && sim.combat().ticksSinceDamaged(member, tick) <= UNDER_FIRE_TICKS;
        float fromX = sim.combat().incomingFromX(member) + 0.5f;
        float fromY = sim.combat().incomingFromY(member) + 0.5f;
        float fromDistance = TacticalScoring.cellDistance(mx, my, fromX, fromY);
        Threat best = null;
        float bestScore = Float.MAX_VALUE;
        for (BelievedContact contact : squad.believedContacts()) {
            long id = contact.unitId();
            if (id == 0L || !sim.canEngage(member, id)
                    || !sim.identity().faction(member).hostileTo(sim.identity().faction(id))) continue;
            List<Weapon> threat = threatOf(id, sim);
            if (threat == null) continue;
            float threatReach = MechDuelBand.reach(threat);
            float seenX = contact.lastSeenCellX() + 0.5f;
            float seenY = contact.lastSeenCellY() + 0.5f;
            if (underFire && fromDistance > reach && threatReach >= fromDistance - HOLD_SLACK) {
                // The shooter is whichever such contact was last seen nearest the fire.
                float score = TacticalScoring.cellDistance(seenX, seenY, fromX, fromY);
                if (score < bestScore) {
                    best = new Threat(id, fromX, fromY);
                    bestScore = score;
                }
                continue;
            }
            if (underFire && best != null) continue;
            float d = TacticalScoring.cellDistance(mx, my, seenX, seenY);
            // Ranked behind every contact that explains the fire.
            if (d <= reach || threatReach < d - HOLD_SLACK || d + FIRE_RANK_OFFSET >= bestScore) continue;
            best = new Threat(id, seenX, seenY);
            bestScore = d + FIRE_RANK_OFFSET;
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
    static int[] findBandCell(long member, Command command, long target, float tx, float ty, Band band,
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
                        || !command.permits(member, x, y, sim)
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

    /**
     * Plate a duel is read against before the target is known: a Bulwark's. A missile rack's
     * pressure depends on it ({@link Weapon#of(WeaponDef, float)}).
     */
    static final float REFERENCE_ARMOR_RATING = 18f;

    /** Installed weapons still in the fight, read against {@link #REFERENCE_ARMOR_RATING}. */
    public static List<Weapon> ownWeapons(MechLoadoutComponent loadout) {
        return ownWeapons(loadout, REFERENCE_ARMOR_RATING);
    }

    /**
     * Installed weapons still in the fight ({@link MechWeaponMount#inFight}), read against plate of
     * {@code targetRating}. A rack between salvos counts: a five-round rack is empty for all but a moment of its
     * recharge, and reading it out would walk a Long-Range Support mech into a brawler's reach after every volley.
     */
    public static List<Weapon> ownWeapons(MechLoadoutComponent loadout, float targetRating) {
        List<Weapon> out = new ArrayList<>(3);
        for (MechWeaponMount mount : loadout.mounts()) {
            if (mount != null && mount.inFight()) {
                out.add(Weapon.of(mount.weaponDef(), targetRating));
            }
        }
        return out;
    }

    /** An actor's armor rating, or zero for one without armor. */
    static float armorRatingOf(long id, BattleView sim) {
        return sim.world().hasArmor(id) ? sim.world().armorRating(id) : 0f;
    }

    /**
     * The target's weapons, if it is armor: a mech's live mounts, or a ground
     * vehicle's turret (none for an unarmed hull). Null for anything else,
     * which the rule leaves to doctrine.
     */
    static List<Weapon> threatOf(long target, BattleView sim) {
        return threatOf(target, sim, REFERENCE_ARMOR_RATING);
    }

    /** As above, a mech target's racks read against the asking mech's plate of {@code ownRating}. */
    static List<Weapon> threatOf(long target, BattleView sim, float ownRating) {
        if (sim.world().hasMechLoadout(target)) {
            return ownWeapons(sim.world().mechLoadout(target), ownRating);
        }
        BodyCarrier carrier = sim.bodies().carrierOf(target);
        if (carrier instanceof ConvoyService convoy) {
            VehicleType type = convoy.vehicleType(target);
            if (type == null) return null;
            List<Weapon> out = new ArrayList<>(1);
            if (convoy.isArmed(target)) out.add(Weapon.of(convoy.turretStructure(target).mount.weapon));
            return out;
        }
        return null;
    }

    private static float threatReach(long target, BattleView sim) {
        List<Weapon> threat = threatOf(target, sim);
        return threat == null ? 0f : MechDuelBand.reach(threat);
    }
}
