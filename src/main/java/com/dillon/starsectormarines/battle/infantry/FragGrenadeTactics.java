package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.PointFireAim;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.fire.FireReach;
import com.dillon.starsectormarines.battle.grenade.FragGrenadeService;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

import static com.dillon.starsectormarines.battle.sim.BattleSimulation.TICK_DT;

/**
 * Honest-contact cluster scoring, reservations, safety, and observed-hazard response.
 *
 * <p>An incendiary is judged by the fire it starts as well as its blast
 * (`fire-nouns.md`, Starting fires): one soft target standing in fuel is worth
 * it, since the fire does the spreading, and it is not thrown where the fire
 * could come to the thrower's own side ({@link FireReach}). It is aimed at
 * fuel that can still catch: ground already burnt or burning starts nothing,
 * so the throw moves onto the nearest unburnt fuel that keeps the target in the
 * blast, and with none in reach it is judged by its blast alone.
 */
public final class FragGrenadeTactics {

    private static final int MAX_DIRECT_CONTACT_AGE_TICKS = 60;
    private static final float MIN_CONTACT_CONFIDENCE = 0.5f;
    private static final int MIN_SOFT_TARGETS = 2;
    private static final float SAFETY_MARGIN = 0.4f;

    private FragGrenadeTactics() {
    }

    /** Commits a carrier to the best useful, friendly-safe believed soft cluster. */
    public static boolean tryCommitThrow(long carrier, SpecialEquipmentDef grenade,
                                         BattleControl sim) {
        Squad squad = sim.squadOf(carrier);
        if (squad == null || blockedByHigherPriorityWork(carrier, squad)) return false;
        cleanupReservations(sim);
        FireField fire = grenade.weaponDef().incendiaryHeat() > 0 && FireReach.enabled() ? sim.fireField() : null;
        int lit = FireReach.litCells(grenade.aoeRadius(), grenade.hitSpread());

        Candidate best = null;
        for (BelievedContact anchor : squad.believedContacts()) {
            if (!usableSoftContact(anchor, squad.faction, sim)) continue;
            int[] fuelCell = fire != null
                    ? fuelCellNear(fire, anchor.lastSeenCellX(), anchor.lastSeenCellY(), grenade.aoeRadius())
                    : null;
            boolean inFuel = fuelCell != null;
            int cellX = inFuel ? fuelCell[0] : anchor.lastSeenCellX();
            int cellY = inFuel ? fuelCell[1] : anchor.lastSeenCellY();
            float targetX = cellX + 0.5f;
            float targetY = cellY + 0.5f;
            float dx = targetX - sim.world().x(carrier);
            float dy = targetY - sim.world().y(carrier);
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq > grenade.range() * grenade.range()) continue;
            if (!sim.getGrid().hasLineOfSight(sim.world().cellX(carrier),
                    sim.world().cellY(carrier), anchor.lastSeenCellX(),
                    anchor.lastSeenCellY())) continue;

            int softTargets = countSoftContacts(squad, targetX, targetY,
                    grenade.aoeRadius(), sim);
            if (!worthThrowing(inFuel, softTargets)) continue;
            if (!friendlyFootprintClear(carrier, targetX, targetY, grenade, sim)) continue;
            if (fire != null && FireReach.reachesSide(fire, cellX, cellY, lit,
                    sim.identity().faction(carrier), sim)) continue;
            if (overlapsCommittedGrenade(targetX, targetY, grenade.aoeRadius(),
                    grenade.weaponId(),
                    sim.identity().faction(carrier), sim)) continue;
            Candidate candidate = new Candidate(targetX, targetY, softTargets, distanceSq);
            if (best == null || candidate.softTargets > best.softTargets
                    || candidate.softTargets == best.softTargets
                    && candidate.distanceSq < best.distanceSq) best = candidate;
        }
        if (best == null) return false;
        if (!sim.fragGrenades().tryReserve(carrier, squad.id,
                sim.identity().faction(carrier), best.targetX, best.targetY,
                grenade.aoeRadius())) return false;
        sim.world().setSecondaryActionTimer(carrier, grenade.aimDuration());
        sim.world().setSecondaryFired(carrier, false);
        sim.world().setSecondaryAimTargetId(carrier, 0L);
        return true;
    }

    /**
     * The cell nearest {@code (x, y)}, within {@code reach} cells centre to centre, whose fuel can still catch, or
     * null when there is none: an incendiary is aimed there rather than at ground already burnt or burning.
     */
    static int[] fuelCellNear(FireField fire, int x, int y, float reach) {
        if (fire.canBurn(x, y)) return new int[]{x, y};
        int span = (int) Math.floor(reach);
        float reachSq = reach * reach;
        int[] best = null;
        int bestSq = Integer.MAX_VALUE;
        for (int dy = -span; dy <= span; dy++) {
            for (int dx = -span; dx <= span; dx++) {
                int sq = dx * dx + dy * dy;
                if (sq > reachSq || sq >= bestSq || !fire.canBurn(x + dx, y + dy)) continue;
                best = new int[]{x + dx, y + dy};
                bestSq = sq;
            }
        }
        return best;
    }

    /**
     * Whether a throw at a point is worth its charge: one soft target standing in fuel that can still catch, since
     * the fire does the rest; otherwise the blast must do the work alone, and it wants two. A carrier that finds no
     * such point keeps its charge and looks again on its next turn.
     */
    static boolean worthThrowing(boolean inFuel, int softTargets) {
        return softTargets >= (inFuel ? 1 : MIN_SOFT_TARGETS);
    }

    /** Equipment availability for a player's throw: a hand grenade, in hand or on the belt, with one left and the hand free. */
    public static boolean canThrow(long carrier, BattleView sim) {
        World world = sim.world();
        return world.isAlive(carrier) && CarriedSpecials.ready(world, carrier, SpecialEquipmentDef::isThrownGrenade);
    }

    /**
     * A player's throw: freezes the cursor point, pulled in to the grenade's range and kept on the map, and
     * starts the same committed channel an autonomous throw uses. It releases once at the channel's midpoint
     * through {@code throwFragmentationGrenade}, which spends the grenade; nothing is spent here.
     */
    public static boolean beginManualThrow(long carrier, PointFireAim target, BattleControl sim) {
        if (target == null || !Float.isFinite(target.x()) || !Float.isFinite(target.y())
                || !canThrow(carrier, sim)) return false;
        World world = sim.world();
        SpecialEquipmentDef grenade = CarriedSpecials.item(world, carrier, SpecialEquipmentDef::isThrownGrenade);
        float[] point = clampThrow(world.renderX(carrier), world.renderY(carrier), target.x(), target.y(),
                grenade.range(), sim.getGrid().getWidth(), sim.getGrid().getHeight());
        if (point == null) return false;
        Squad squad = sim.squadOf(carrier);
        if (!CarriedSpecials.draw(world, carrier, SpecialEquipmentDef::isThrownGrenade)) return false;
        sim.fragGrenades().reserveManual(carrier, squad != null ? squad.id : -1,
                sim.identity().faction(carrier), point[0], point[1], grenade.aoeRadius());
        world.setSecondaryActionTimer(carrier, grenade.aimDuration());
        world.setSecondaryFired(carrier, false);
        world.setSecondaryAimTargetId(carrier, 0L);
        sim.combat().clearPrimaryFire(carrier);
        return true;
    }

    /** The point a throw from {@code (fromX, fromY)} at the cursor lands on: no further than {@code range}, inside the map. */
    static float[] clampThrow(float fromX, float fromY, float x, float y, float range, int width, int height) {
        float dx = x - fromX;
        float dy = y - fromY;
        float distance = (float) Math.hypot(dx, dy);
        if (!Float.isFinite(distance)) return null;
        if (distance > range && distance > 0f) {
            x = fromX + dx / distance * range;
            y = fromY + dy / distance * range;
        }
        return new float[]{Math.max(.5f, Math.min(width - .5f, x)), Math.max(.5f, Math.min(height - .5f, y))};
    }

    /** Evades a known friendly grenade or a hostile grenade honestly observed by the squad. */
    public static boolean evadeKnownGrenade(long unit, BattleControl sim) {
        if (!sim.world().hasMovement(unit)) return false;
        Projectile hazard = nearestKnownHazard(unit, sim);
        if (hazard == null) {
            retireEscape(unit, sim);
            return false;
        }
        TickInnerProfile profile = TickInnerProfile.currentIfBound();
        NavigationGrid grid = sim.getGrid();
        float dangerRadius = hazard.onArrival.aoeRadius + SAFETY_MARGIN;
        LocalGrenadeEscape.Retained retained = sim.movement().grenadeEscape(unit);
        int fromX = sim.world().cellX(unit);
        int fromY = sim.world().cellY(unit);
        if (retained != null && retained.usable(hazard, grid, sim.world().path(unit),
                sim.world().pathIdx(unit), fromX, fromY)) {
            // Finish the short safety-margin escape rather than handing back
            // on the first fractional step outside the danger radius.
            if (profile != null) profile.recordCount(TickInnerProfile.Bucket.GRENADE_ESCAPE_REUSE, 1);
            sim.advanceMovement(unit);
            return true;
        }
        if (distanceSq(sim.world().x(unit), sim.world().y(unit), hazard.toX, hazard.toY)
                > dangerRadius * dangerRadius) {
            // This route leads into danger soon; stopping is sufficient. Preserve
            // its owner's route, and ask again next tick rather than latch a hold.
            retireEscape(unit, sim);
            if (profile != null) profile.recordCount(TickInnerProfile.Bucket.GRENADE_PATH_HOLD, 1);
            return true;
        }
        retireEscape(unit, sim);
        long started = profile != null ? System.nanoTime() : 0L;
        LocalGrenadeEscape.Result result = LocalGrenadeEscape.find(grid, fromX, fromY,
                hazard.toX, hazard.toY, dangerRadius + 0.75f,
                GridPathfinder.USE_CARDINAL_NAVIGATION);
        if (profile != null) {
            profile.record(TickInnerProfile.Bucket.GRENADE_ESCAPE_SEARCH, System.nanoTime() - started);
            profile.recordCount(TickInnerProfile.Bucket.GRENADE_ESCAPE_EXPANDED, result.expanded());
        }
        if (result.path().length == 0) {
            // Budget exhaustion is not proof of disconnection. Hold this tick;
            // no partial unsafe path or map-wide fallback is authorized.
            if (profile != null) profile.recordCount(TickInnerProfile.Bucket.GRENADE_ESCAPE_NO_ROUTE, 1);
            return true;
        }
        sim.setPath(unit, result.path());
        sim.movement().setGrenadeEscape(unit, new LocalGrenadeEscape.Retained(hazard, grid,
                grid.topologyRevision(), GridPathfinder.USE_CARDINAL_NAVIGATION, result.path()));
        sim.advanceMovement(unit);
        return true;
    }

    private static void retireEscape(long unit, BattleControl sim) {
        LocalGrenadeEscape.Retained retained = sim.movement().grenadeEscape(unit);
        if (retained == null) return;
        if (sim.world().path(unit) == retained.path()) sim.clearPath(unit);
        sim.movement().setGrenadeEscape(unit, null);
    }

    public static void cleanupReservations(BattleView sim) {
        sim.fragGrenades().removeStale(reservation -> {
            long carrier = sim.resolveUnit(reservation.carrierId());
            return carrier != 0L
                    && sim.world().hasSecondaryWeapon(carrier)
                    && sim.world().specialEquipment(carrier).activation()
                    == SpecialActivation.ARC_EXPLOSIVE
                    && sim.world().secondaryActionTimer(carrier) > 0f;
        });
    }

    private static boolean blockedByHigherPriorityWork(long carrier, Squad squad) {
        if (squad.currentGoal != null) {
            Goal.Priority priority = squad.currentGoal.priority();
            if (priority == Goal.Priority.MISSION || priority == Goal.Priority.SURVIVAL) {
                return true;
            }
        }
        if (!squad.boundingActive) return false;
        for (long movingMember : squad.boundingMemberIds) {
            if (movingMember == carrier) return true;
        }
        return false;
    }

    private static boolean usableSoftContact(BelievedContact contact, Faction faction, BattleView sim) {
        if (contact.source() != BeliefSource.DIRECT
                || contact.confidence() < MIN_CONTACT_CONFIDENCE
                || sim.getSimTickIndex() - contact.lastSeenTick() > MAX_DIRECT_CONTACT_AGE_TICKS
                || !sim.identity().has(contact.unitId())
                || !faction.hostileTo(sim.identity().faction(contact.unitId()))) {
            return false;
        }
        UnitType type = sim.identity().type(contact.unitId());
        return type != null && type.combatant && !TacticalScoring.isHardened(type);
    }

    private static int countSoftContacts(Squad squad, float targetX, float targetY,
                                         float radius, BattleView sim) {
        int count = 0;
        float radiusSq = radius * radius;
        for (BelievedContact contact : squad.believedContacts()) {
            if (!usableSoftContact(contact, squad.faction, sim)) continue;
            float dx = contact.lastSeenCellX() + 0.5f - targetX;
            float dy = contact.lastSeenCellY() + 0.5f - targetY;
            if (dx * dx + dy * dy <= radiusSq) count++;
        }
        return count;
    }

    private static boolean friendlyFootprintClear(long carrier, float targetX, float targetY,
                                                  SpecialEquipmentDef grenade,
                                                  BattleView sim) {
        Faction faction = sim.identity().faction(carrier);
        float dangerRadius = grenade.aoeRadius() + grenade.hitSpread() + SAFETY_MARGIN;
        float dangerSq = dangerRadius * dangerRadius;
        for (int i = 0; i < sim.liveUnitCount(); i++) {
            long friendly = sim.liveUnitAt(i);
            if (!faction.friendlyTo(sim.identity().faction(friendly))) continue;
            if (distanceSq(sim.world().x(friendly), sim.world().y(friendly),
                    targetX, targetY) <= dangerSq) return false;
            if (!sim.world().hasMovement(friendly)) continue;
            int[] path = sim.world().path(friendly);
            for (int cell = Math.max(0, sim.world().pathIdx(friendly)),
                 count = Paths.cellCount(path); cell < count; cell++) {
                if (distanceSq(Paths.cellX(path, cell) + 0.5f,
                        Paths.cellY(path, cell) + 0.5f, targetX, targetY) <= dangerSq) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean overlapsCommittedGrenade(float targetX, float targetY,
                                                     float radius, String weaponId,
                                                     Faction faction,
                                                     BattleView sim) {
        for (Projectile projectile : sim.committedProjectiles()) {
            if (projectile.shooterFaction != faction
                    || !weaponId.equals(projectile.sourceWeaponId)) continue;
            float reach = radius + (projectile.onArrival != null
                    ? projectile.onArrival.aoeRadius : radius);
            if (distanceSq(projectile.toX, projectile.toY, targetX, targetY)
                    <= reach * reach) return true;
        }
        return false;
    }

    private static Projectile nearestKnownHazard(long unit, BattleView sim) {
        Faction faction = sim.identity().faction(unit);
        Squad squad = sim.squadOf(unit);
        Projectile nearest = null;
        float nearestSq = Float.MAX_VALUE;
        boolean nearestCurrent = false;
        LocalGrenadeEscape.Retained retained = sim.movement().grenadeEscape(unit);
        for (Projectile projectile : sim.snapshotActiveProjectiles()) {
            if (!isArcExplosiveWeapon(projectile.sourceWeaponId)
                    || projectile.onArrival == null) continue;
            if (projectile.intercepted || projectile.isExpired()) continue;
            if (projectile.shooterFaction != faction
                    && !squadObservesProjectile(squad, unit, projectile, sim)) continue;
            float reach = projectile.onArrival.aoeRadius + SAFETY_MARGIN;
            boolean finishingEscape = retained != null && retained.hazard() == projectile
                    && retained.usable(projectile, sim.getGrid(), sim.world().path(unit),
                    sim.world().pathIdx(unit), sim.world().cellX(unit), sim.world().cellY(unit));
            if (distanceSq(sim.world().x(unit), sim.world().y(unit), projectile.toX,
                    projectile.toY) > reach * reach
                    && !finishingEscape
                    && !LocalGrenadeEscape.pathThreatened(sim.world().x(unit),
                    sim.world().y(unit), sim.world().path(unit), sim.world().pathIdx(unit),
                    Math.max(0f, sim.world().moveSpeed(unit))
                            // Movement precedes projectile expiry: conservatively
                            // include the final full movement tick before impact.
                            * (projectile.remainingTime + TICK_DT),
                    projectile.toX, projectile.toY, reach)) continue;
            float distanceSq = distanceSq(sim.world().x(unit), sim.world().y(unit),
                    projectile.toX, projectile.toY);
            boolean current = distanceSq <= reach * reach;
            if (nearest == null || current && !nearestCurrent
                    || current == nearestCurrent && distanceSq < nearestSq) {
                nearest = projectile;
                nearestSq = distanceSq;
                nearestCurrent = current;
            }
        }
        return nearest;
    }

    private static boolean squadObservesProjectile(Squad squad, long unit,
                                                    Projectile projectile,
                                                    BattleView sim) {
        int px = Math.max(0, Math.min(sim.getGrid().getWidth() - 1,
                (int) Math.floor(projectile.currentX())));
        int py = Math.max(0, Math.min(sim.getGrid().getHeight() - 1,
                (int) Math.floor(groundY(projectile))));
        if (squad == null) return observerSees(unit, px, py, sim);
        for (int i = 0; i < sim.squadMemberCount(squad.id); i++) {
            long member = sim.squadMemberAt(squad.id, i);
            if (observerSees(member, px, py, sim)) return true;
        }
        return false;
    }

    private static boolean observerSees(long observer, int px, int py, BattleView sim) {
        if (sim.resolveUnit(observer) == 0L) return false;
        float dx = px + 0.5f - sim.world().x(observer);
        float dy = py + 0.5f - sim.world().y(observer);
        float vision = sim.vision().visionRange(observer);
        return dx * dx + dy * dy <= vision * vision
                && TacticalScoring.canSeePair(sim.getGrid(),
                sim.world().cellX(observer), sim.world().cellY(observer), px, py,
                sim.vision().airLosRadius(observer), 0f);
    }

    private static float distanceSq(float ax, float ay, float bx, float by) {
        float dx = bx - ax;
        float dy = by - ay;
        return dx * dx + dy * dy;
    }

    private static float groundY(Projectile projectile) {
        float progress = projectile.progress();
        if (projectile.hasBoostRamp) progress = Projectile.applyBoostCurve(progress);
        return projectile.fromY + (projectile.toY - projectile.fromY) * progress;
    }

    private static boolean isArcExplosiveWeapon(String weaponId) {
        SpecialEquipmentRegistry registry = SpecialEquipmentRegistry.installed();
        if (registry == null || weaponId == null) return false;
        for (SpecialEquipmentDef equipment : registry.all()) {
            if (equipment.activation() == SpecialActivation.ARC_EXPLOSIVE
                    && weaponId.equals(equipment.weaponId())) return true;
        }
        return false;
    }

    private record Candidate(float targetX, float targetY, int softTargets,
                             float distanceSq) {
    }
}
