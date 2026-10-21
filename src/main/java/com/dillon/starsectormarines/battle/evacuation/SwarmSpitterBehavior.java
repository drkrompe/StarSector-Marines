package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.air.AirAppearance;
import com.dillon.starsectormarines.battle.air.AirLocomotion;
import com.dillon.starsectormarines.battle.combat.PendingDetonation;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

/**
 * The swarm Spitter: fragile, and the strain that punishes standing still.
 *
 * <p>Priorities, every tick:
 * <ol>
 *   <li><b>Charged:</b> a sensed ground enemy inside {@link #CHARGED_RADIUS}
 *       sends it backing away from that enemy; it does not lob while it runs.</li>
 *   <li><b>Low flier:</b> a hostile airborne craft below {@link #LOW_ALTITUDE}
 *       within lob range takes acid, applied to the hull in the serial pass
 *       ({@link SwarmStrikeSystem}). High craft are out of its reach.</li>
 *   <li><b>Densest infantry:</b> of the enemies it senses within range, it
 *       lobs at the one with the most others inside the splash. The glob is a
 *       real arcing projectile ({@code weapon.swarm-acid}) that splashes
 *       through the ordinary detonation path, sparing the swarm.</li>
 *   <li>Otherwise it moves toward its quarry through {@link
 *       SwarmPressureBehavior#pressToward} like every strain, and stops as
 *       soon as something is in range: Runners close past it, so it ends up
 *       behind them.</li>
 * </ol>
 * It senses exactly as a Runner does; it never lobs at what it cannot see.
 */
final class SwarmSpitterBehavior {

    static final String ACID_WEAPON_ID = "weapon.swarm-acid";
    /** A ground enemy this close is a charge, and the Spitter backs off. */
    static final float CHARGED_RADIUS = 3.5f;
    /** How far a retreat aims to open the gap. */
    static final int RETREAT_CELLS = 6;
    /** Craft at or below this altitude (0 on the ground, 1 at cruise) are in reach. */
    static final float LOW_ALTITUDE = 0.6f;
    /** Acid eats a hull faster than it hurts a plated body: the air hit's multiplier and penetration. */
    static final float AIR_DAMAGE_MULTIPLIER = 2f;
    static final float AIR_PENETRATION = 10f;

    private static final ThreadLocal<LongBucket> CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);
    private static final ThreadLocal<LongBucket> NEIGHBOURS =
            ThreadLocal.withInitial(LongBucket::new);
    private static volatile WeaponDef acid;

    private SwarmSpitterBehavior() {}

    static WeaponDef acid() {
        WeaponDef def = acid;
        if (def == null) {
            def = WeaponRegistry.require(ACID_WEAPON_ID);
            acid = def;
        }
        return def;
    }

    static void update(long spitter, BattleSimulation sim) {
        SwarmPressureBehavior.tickCooldown(spitter, sim);
        long previousTarget = sim.combat().targetId(spitter);

        long threat = nearestCharger(spitter, sim);
        if (threat != 0L) {
            sim.combat().setTargetId(spitter, threat);
            retreatFrom(spitter, threat, sim);
            return;
        }

        boolean ready = sim.combat().cooldownTimer(spitter) <= 0f;
        if (ready) {
            long craft = lowFlierInRange(spitter, sim);
            if (craft != 0L) {
                sim.clearPath(spitter);
                lobAtCraft(spitter, craft, sim);
                return;
            }
        }

        long aim = densestPreyInRange(spitter, sim);
        if (aim != 0L) {
            sim.combat().setTargetId(spitter, aim);
            sim.clearPath(spitter);
            if (ready) lobAt(spitter, aim, sim);
            return;
        }

        long target = SwarmPressureBehavior.selectTarget(spitter, sim);
        sim.combat().setTargetId(spitter, target);
        if (target == 0L) {
            SwarmPressureBehavior.updateRoaming(spitter, previousTarget, sim);
            return;
        }
        SwarmPressureBehavior.pressToward(spitter, target, target != previousTarget, sim);
    }

    /** The nearest sensed mobile ground enemy inside {@link #CHARGED_RADIUS}, or 0. */
    static long nearestCharger(long spitter, BattleSimulation sim) {
        float x = sim.world().x(spitter);
        float y = sim.world().y(spitter);
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        LongBucket nearby = CANDIDATES.get();
        sim.getUnitIndex().gatherHostileCombatants(x, y, CHARGED_RADIUS, Faction.DEFENDER, nearby);
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = nearby.size; i < n; i++) {
            long candidate = nearby.ids[i];
            if (sim.identity().type(candidate).isStatic()) continue;
            float cx = sim.world().x(candidate);
            float cy = sim.world().y(candidate);
            float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (d > CHARGED_RADIUS * CHARGED_RADIUS) continue;
            if (!SwarmPressureBehavior.canSense(cellX, cellY, cx, cy, CHARGED_RADIUS, sim)) continue;
            if (d < bestDistance || (d == bestDistance && candidate < best)) {
                best = candidate;
                bestDistance = d;
            }
        }
        return best;
    }

    /**
     * Of the enemies the Spitter senses within lob range, the one with the most others
     * inside the splash; ties go to the nearer, then the lower id. Shelter guards are left
     * alone while the shelter is sealed, as they are by every strain.
     */
    static long densestPreyInRange(long spitter, BattleSimulation sim) {
        float x = sim.world().x(spitter);
        float y = sim.world().y(spitter);
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        float range = sim.combat().attackRange(spitter);
        float splash = acid().aoeRadius;
        boolean shelterProtected = sim.isCivilianShelterProtected();
        LongBucket nearby = CANDIDATES.get();
        LongBucket around = NEIGHBOURS.get();
        sim.getUnitIndex().gatherHostileCombatants(x, y, range, Faction.DEFENDER, nearby);
        long best = 0L;
        int bestCount = -1;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = nearby.size; i < n; i++) {
            long candidate = nearby.ids[i];
            if (sim.identity().type(candidate).isStatic()) continue;
            if (!SwarmPressureBehavior.eligiblePreyKnownFaction(candidate, shelterProtected, sim)) {
                continue;
            }
            float cx = sim.world().x(candidate);
            float cy = sim.world().y(candidate);
            float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (d > range * range) continue;
            if (!SwarmPressureBehavior.canSense(cellX, cellY, cx, cy, range, sim)) continue;
            sim.getUnitIndex().gatherHostileCombatants(cx, cy, splash, Faction.DEFENDER, around);
            int count = around.size;
            if (count > bestCount || (count == bestCount
                    && (d < bestDistance || (d == bestDistance && candidate < best)))) {
                best = candidate;
                bestCount = count;
                bestDistance = d;
            }
        }
        return best;
    }

    /** The nearest hostile craft flying low within lob range, or 0. */
    static long lowFlierInRange(long spitter, BattleSimulation sim) {
        float x = sim.world().x(spitter);
        float y = sim.world().y(spitter);
        float range = sim.combat().attackRange(spitter);
        long best = 0L;
        float bestDistance = range * range;
        for (long craft : sim.getAirEntityIds()) {
            if (!sim.world().isAlive(craft)) continue;
            var mission = sim.world().mission(craft);
            if (mission == null || mission.destroyed || !mission.isOnMap()
                    || !AirLocomotion.of(mission.state).airborne()
                    || !Faction.DEFENDER.hostileTo(sim.world().airFaction(craft))
                    || sim.world().altitudeT(craft) > LOW_ALTITUDE) {
                continue;
            }
            float dx = sim.world().x(craft) - x;
            float dy = sim.world().y(craft) - y;
            float d = dx * dx + dy * dy;
            if (d < bestDistance || (d == bestDistance && craft < best)) {
                best = craft;
                bestDistance = d;
            }
        }
        return best;
    }

    private static void lobAt(long spitter, long target, BattleSimulation sim) {
        WeaponDef weapon = acid();
        float fromX = sim.world().x(spitter);
        float fromY = sim.world().y(spitter);
        float tx = sim.world().x(target);
        float ty = sim.world().y(target);
        float distance = (float) Math.hypot(tx - fromX, ty - fromY);
        // Scatter grows with distance and shrinks with accuracy, drawn from a hash of the
        // body and tick: the parallel update may not share the battle's random stream.
        long sample = mix(spitter * 0x9E3779B97F4A7C15L
                ^ (long) sim.getSimTickIndex() * 0xD1B54A32D192ED03L);
        float error = weapon.hitSpread * (1f - sim.combat().accuracy(spitter))
                * Math.min(1f, distance / Math.max(1f, weapon.range)) * 2f;
        float angle = (float) ((sample & 0xFFFF) / 65536.0 * Math.PI * 2);
        float radius = (float) Math.sqrt(((sample >>> 16) & 0xFFFF) / 65536.0) * error;
        float ex = tx + (float) Math.cos(angle) * radius;
        float ey = ty + (float) Math.sin(angle) * radius;
        float flight = Math.max(0.3f, Math.min(weapon.flightSec,
                distance / Math.max(1f, weapon.roundVelocity)));
        Faction faction = sim.identity().faction(spitter);
        float morale = sim.identity().type(spitter).moraleImpact;
        PendingDetonation splash = new PendingDetonation(spitter, ex, ey, flight,
                weapon.aoeRadius, sim.combat().attackDamage(spitter), weapon.penetration,
                /*wallDamage*/ 0, faction, /*aerialDelivery: a roof shields*/ true,
                /*wallDamageRadius*/ 0f, /*spawnDustOnWallBreak*/ false,
                /*friendlyFireImmune: acid spares the swarm*/ true,
                /*authoredAftermath*/ true);
        sim.queueProjectile(new Projectile(fromX, fromY, ex, ey, weapon.boostRamp,
                weapon.arcHeight, faction, true, flight, splash, weapon.id,
                weapon.pointDefenseTarget));
        sim.postShot(new ShotEvent(spitter, fromX, fromY, ex, ey, true, faction, flight,
                null, null, null, weapon, morale));
        sim.combat().setCooldownTimer(spitter, sim.combat().attackCooldown(spitter));
    }

    private static void lobAtCraft(long spitter, long craft, BattleSimulation sim) {
        WeaponDef weapon = acid();
        float fromX = sim.world().x(spitter);
        float fromY = sim.world().y(spitter);
        float tx = sim.world().x(craft);
        float ty = sim.world().y(craft);
        float distance = (float) Math.hypot(tx - fromX, ty - fromY);
        float flight = Math.max(0.3f, Math.min(weapon.flightSec,
                distance / Math.max(1f, weapon.roundVelocity)));
        long sample = mix(spitter * 0xBF58476D1CE4E5B9L
                ^ (long) sim.getSimTickIndex() * 0x94D049BB133111EBL);
        boolean hit = (sample & 0xFFFF) / 65536f < sim.combat().accuracy(spitter);
        Faction faction = sim.identity().faction(spitter);
        float toZ = AirAppearance.visualAltitudeOffsetCells(sim.world().altitudeT(craft));
        sim.postShot(new ShotEvent(fromX, fromY, 0f, tx, ty, toZ, hit, faction, flight,
                null, null, null, weapon, sim.identity().type(spitter).moraleImpact,
                false, null, spitter));
        if (hit) {
            int arrival = sim.getSimTickIndex()
                    + Math.max(1, Math.round(flight / BattleSimulation.TICK_DT));
            sim.swarmStrikes().postAirRound(spitter, craft,
                    sim.combat().attackDamage(spitter) * AIR_DAMAGE_MULTIPLIER,
                    AIR_PENETRATION, arrival);
        }
        sim.combat().setTargetId(spitter, 0L);
        sim.combat().setCooldownTimer(spitter, sim.combat().attackCooldown(spitter));
    }

    /**
     * Backs away from {@code threat}: a short path to a walkable cell about {@link
     * #RETREAT_CELLS} further from it. A local manoeuvre, not strategic movement, so it
     * paths itself rather than through the shared swarm move.
     */
    private static void retreatFrom(long spitter, long threat, BattleSimulation sim) {
        int[] path = sim.movement().path(spitter);
        boolean underway = sim.movement().pathIdx(spitter) < Paths.cellCount(path);
        if (underway && !sim.movement().mayRepath(spitter)) {
            sim.advanceMovement(spitter);
            return;
        }
        float x = sim.world().x(spitter);
        float y = sim.world().y(spitter);
        float awayX = x - sim.world().x(threat);
        float awayY = y - sim.world().y(threat);
        float length = (float) Math.hypot(awayX, awayY);
        if (length < 1e-3f) {
            awayX = 1f;
            awayY = 0f;
            length = 1f;
        }
        awayX /= length;
        awayY /= length;
        int fromX = sim.world().cellX(spitter);
        int fromY = sim.world().cellY(spitter);
        var grid = sim.getGrid();
        // Straight back first, then fanning out 30 degrees at a time to either side.
        for (int attempt = 0; attempt < 7; attempt++) {
            double turn = Math.toRadians(30 * ((attempt + 1) / 2) * (attempt % 2 == 0 ? 1 : -1));
            float dx = (float) (awayX * Math.cos(turn) - awayY * Math.sin(turn));
            float dy = (float) (awayX * Math.sin(turn) + awayY * Math.cos(turn));
            int destX = (int) Math.floor(x + dx * RETREAT_CELLS);
            int destY = (int) Math.floor(y + dy * RETREAT_CELLS);
            if (!grid.inBounds(destX, destY) || !grid.isWalkable(destX, destY)
                    || sim.isInsideRescueOpeningProtectedZone(destX, destY)) {
                continue;
            }
            int[] retreat = GridPathfinder.findPath(grid, fromX, fromY, destX, destY,
                    sim.getOccupancyMap());
            if (Paths.isEmpty(retreat)) continue;
            sim.setPath(spitter, retreat);
            sim.advanceMovement(spitter);
            return;
        }
        if (underway) sim.advanceMovement(spitter);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
