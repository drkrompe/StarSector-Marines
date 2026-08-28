package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.decision.TacticalScoring;
import com.dillon.starsectormarines.battle.decision.goap.Goal;
import com.dillon.starsectormarines.battle.grenade.FragGrenadeService;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.squad.BeliefSource;
import com.dillon.starsectormarines.battle.squad.BelievedContact;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.marine.SpecialActivation;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

/** Honest-contact cluster scoring, reservations, safety, and observed-hazard response. */
public final class FragGrenadeTactics {

    private static final int MAX_DIRECT_CONTACT_AGE_TICKS = 60;
    private static final float MIN_CONTACT_CONFIDENCE = 0.5f;
    private static final int MIN_SOFT_TARGETS = 2;
    private static final float SAFETY_MARGIN = 0.4f;
    private static final int ESCAPE_DIRECTIONS = 16;

    private FragGrenadeTactics() {
    }

    /** Commits a carrier to the best useful, friendly-safe believed soft cluster. */
    public static boolean tryCommitThrow(long carrier, SpecialEquipmentDef grenade,
                                         BattleControl sim) {
        Squad squad = sim.squadOf(carrier);
        if (squad == null || blockedByHigherPriorityWork(carrier, squad)) return false;
        cleanupReservations(sim);

        Candidate best = null;
        for (BelievedContact anchor : squad.believedContacts()) {
            if (!usableSoftContact(anchor, sim)) continue;
            float targetX = anchor.lastSeenCellX() + 0.5f;
            float targetY = anchor.lastSeenCellY() + 0.5f;
            float dx = targetX - sim.world().x(carrier);
            float dy = targetY - sim.world().y(carrier);
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq > grenade.range() * grenade.range()) continue;
            if (!sim.getGrid().hasLineOfSight(sim.world().cellX(carrier),
                    sim.world().cellY(carrier), anchor.lastSeenCellX(),
                    anchor.lastSeenCellY())) continue;

            int softTargets = countSoftContacts(squad, targetX, targetY,
                    grenade.aoeRadius(), sim);
            if (softTargets < MIN_SOFT_TARGETS) continue;
            if (!friendlyFootprintClear(carrier, targetX, targetY, grenade, sim)) continue;
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

    /** Evades a known friendly grenade or a hostile grenade honestly observed by the squad. */
    public static boolean evadeKnownGrenade(long unit, BattleControl sim) {
        Projectile hazard = nearestKnownHazard(unit, sim);
        if (hazard == null || hazard.onArrival == null) return false;
        NavigationGrid grid = sim.getGrid();
        float safeRadius = hazard.onArrival.aoeRadius + SAFETY_MARGIN + 0.75f;
        int fromX = sim.world().cellX(unit);
        int fromY = sim.world().cellY(unit);
        int[] bestPath = null;
        float bestDistanceSq = -1f;
        for (int i = 0; i < ESCAPE_DIRECTIONS; i++) {
            float angle = (float) (Math.PI * 2.0 * i / ESCAPE_DIRECTIONS);
            int x = (int) Math.floor(hazard.toX + Math.cos(angle) * safeRadius);
            int y = (int) Math.floor(hazard.toY + Math.sin(angle) * safeRadius);
            if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
            int[] path = GridPathfinder.findPath(grid, fromX, fromY, x, y,
                    sim.getOccupancyMap());
            if (path.length == 0) continue;
            float dx = x + 0.5f - hazard.toX;
            float dy = y + 0.5f - hazard.toY;
            float distanceSq = dx * dx + dy * dy;
            if (distanceSq > bestDistanceSq
                    || distanceSq == bestDistanceSq
                    && (bestPath == null || path.length < bestPath.length)) {
                bestPath = path;
                bestDistanceSq = distanceSq;
            }
        }
        if (bestPath == null) sim.clearPath(unit);
        else {
            sim.setPath(unit, bestPath);
            sim.advanceMovement(unit);
        }
        return true;
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

    private static boolean usableSoftContact(BelievedContact contact, BattleView sim) {
        if (contact.source() != BeliefSource.DIRECT
                || contact.confidence() < MIN_CONTACT_CONFIDENCE
                || sim.getSimTickIndex() - contact.lastSeenTick() > MAX_DIRECT_CONTACT_AGE_TICKS
                || !sim.identity().has(contact.unitId())) {
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
            if (!usableSoftContact(contact, sim)) continue;
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
            if (sim.identity().faction(friendly) != faction) continue;
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
        for (Projectile projectile : sim.snapshotActiveProjectiles()) {
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
        for (Projectile projectile : sim.snapshotActiveProjectiles()) {
            if (!isArcExplosiveWeapon(projectile.sourceWeaponId)
                    || projectile.onArrival == null) continue;
            if (projectile.shooterFaction != faction
                    && !squadObservesProjectile(squad, unit, projectile, sim)) continue;
            float reach = projectile.onArrival.aoeRadius + SAFETY_MARGIN;
            if (!unitOrPathThreatened(unit, projectile.toX, projectile.toY,
                    reach * reach, sim)) continue;
            float distanceSq = distanceSq(sim.world().x(unit), sim.world().y(unit),
                    projectile.toX, projectile.toY);
            if (distanceSq < nearestSq) {
                nearest = projectile;
                nearestSq = distanceSq;
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

    private static boolean unitOrPathThreatened(long unit, float x, float y,
                                                float dangerSq, BattleView sim) {
        if (distanceSq(sim.world().x(unit), sim.world().y(unit), x, y) <= dangerSq) {
            return true;
        }
        if (!sim.world().hasMovement(unit)) return false;
        int[] path = sim.world().path(unit);
        for (int cell = Math.max(0, sim.world().pathIdx(unit)),
             count = Paths.cellCount(path); cell < count; cell++) {
            if (distanceSq(Paths.cellX(path, cell) + 0.5f,
                    Paths.cellY(path, cell) + 0.5f, x, y) <= dangerSq) return true;
        }
        return false;
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
