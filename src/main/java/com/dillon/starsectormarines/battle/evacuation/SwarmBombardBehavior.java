package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.creep.CreepEffects;
import com.dillon.starsectormarines.battle.creep.CreepField;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.SwarmStage;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

/**
 * The swarm Bombard (`bombard.md`): the Rav long arm, a lumbering catapult that fires only planted on creep.
 *
 * <p>Every tick, by its {@link SwarmBombards.Phase}:
 * <ol>
 *   <li><b>Off creep</b> (walking, or the creep under it has died): it uproots if it was planted, then lumbers to
 *       the nearest living creep within {@link #CREEP_SEARCH_CELLS}. It never fires off creep.</li>
 *   <li><b>On creep</b>, walking: it stops and plants ({@link SwarmBombards#PLANT_SECONDS}).</li>
 *   <li><b>Planted</b>, reloaded: it picks its mark ({@link #markInRange}) and cocks its tail back
 *       ({@link SwarmBombards#WINDUP_SECONDS}).</li>
 *   <li><b>Release</b>: the glob is flung at the mark's cell as it stands then, plus a scatter that widens with
 *       range ({@link #scatterRadius}). It flies {@link #flightSeconds} and bursts into a spore haze
 *       ({@link SwarmBombards#tick}). A mark that has died or gone out of reach, or whose haze would now cover a melee, is let
 *       go and the lob is not thrown.</li>
 * </ol>
 * It has no melee and never chases: distance and the surge in front are all it has.
 */
final class SwarmBombardBehavior {

    static final String GLOB_WEAPON_ID = "weapon.swarm-bombard";
    /** Inside this many cells it cannot lob. */
    static final float MIN_RANGE = 20f;
    /** Scatter radius at minimum range and at full range, cells. */
    static final float NEAR_SCATTER = 2f;
    static final float FAR_SCATTER = 6f;
    /** Glob flight at minimum and at full range, seconds. */
    static final float NEAR_FLIGHT_SECONDS = 3f;
    static final float FAR_FLIGHT_SECONDS = 4f;
    /** A swarm body this close to someone a haze would catch is in melee, and the haze is not thrown there. */
    static final float MELEE_GUARD = 2.5f;
    /** A swarm fighter this close with someone a haze would catch as its target is in that fight. */
    static final float ENGAGED_RADIUS = 8f;
    /** A mark standing still and firing counts this much more. */
    static final float STANDING_FIRE_BONUS = 1.5f;
    /** Rav bodies closing within this many cells of a mark make it count more. */
    static final float CLOSING_RADIUS = 10f;
    /** How far it looks for creep to walk back to. */
    static final int CREEP_SEARCH_CELLS = 24;

    private static final ThreadLocal<LongBucket> CANDIDATES = ThreadLocal.withInitial(LongBucket::new);
    private static final ThreadLocal<LongBucket> NEIGHBOURS = ThreadLocal.withInitial(LongBucket::new);
    private static final ThreadLocal<LongBucket> SWARM = ThreadLocal.withInitial(LongBucket::new);
    private static volatile WeaponDef glob;

    private SwarmBombardBehavior() {}

    static WeaponDef glob() {
        WeaponDef def = glob;
        if (def == null) {
            def = WeaponRegistry.require(GLOB_WEAPON_ID);
            glob = def;
        }
        return def;
    }

    static void update(long body, BattleSimulation sim) {
        SwarmPressureBehavior.tickCooldown(body, sim);
        SwarmBombards.Stance stance = sim.swarmBombards().stance(body);
        float x = sim.world().x(body);
        float y = sim.world().y(body);
        stance.lastX = x;
        stance.lastY = y;
        stance.scale = SwarmStage.of(sim.identity().swarmStage(body)).scale(UnitType.SWARM_BOMBARD);
        CreepField creep = sim.creepFieldIfPresent();
        boolean onCreep = CreepEffects.onCreep(creep, x, y);
        float dt = BattleSimulation.TICK_DT;

        switch (stance.phase) {
            case WALKING -> {
                if (onCreep) {
                    sim.clearPath(body);
                    stance.creepX = -1;
                    stance.phase = SwarmBombards.Phase.PLANTING;
                    stance.timer = SwarmBombards.PLANT_SECONDS;
                } else {
                    walkToCreep(body, stance, creep, sim);
                }
            }
            case PLANTING, UPROOTING -> {
                sim.clearPath(body);
                stance.timer -= dt;
                if (stance.timer > 0f) return;
                stance.phase = stance.phase == SwarmBombards.Phase.PLANTING
                        ? SwarmBombards.Phase.PLANTED : SwarmBombards.Phase.WALKING;
            }
            case PLANTED -> {
                sim.clearPath(body);
                if (!onCreep) {
                    uproot(body, stance, sim);
                    return;
                }
                if (sim.combat().cooldownTimer(body) > 0f) return;
                long mark = markInRange(body, sim);
                if (mark == 0L) return;
                sim.combat().setTargetId(body, mark);
                stance.aim = mark;
                stance.phase = SwarmBombards.Phase.WINDING_UP;
                stance.timer = SwarmBombards.WINDUP_SECONDS;
            }
            case WINDING_UP -> {
                sim.clearPath(body);
                if (!onCreep) {
                    uproot(body, stance, sim);
                    return;
                }
                stance.timer -= dt;
                if (stance.timer > 0f) return;
                long mark = stance.aim;
                stance.aim = 0L;
                stance.phase = SwarmBombards.Phase.PLANTED;
                if (stillMarked(body, mark, sim)) fling(body, mark, stance.scale, sim);
                else sim.combat().setTargetId(body, 0L);
            }
        }
    }

    private static void uproot(long body, SwarmBombards.Stance stance, BattleSimulation sim) {
        stance.aim = 0L;
        sim.combat().setTargetId(body, 0L);
        stance.phase = SwarmBombards.Phase.UPROOTING;
        stance.timer = SwarmBombards.PLANT_SECONDS;
    }

    /** Walks to the nearest living creep it can find, choosing anew when the chosen cell has died; else stands. */
    private static void walkToCreep(long body, SwarmBombards.Stance stance, CreepField creep, BattleSimulation sim) {
        if (creep == null) {
            sim.clearPath(body);
            return;
        }
        boolean retarget = stance.creepX < 0 || !creep.isCreeped(stance.creepX, stance.creepY);
        if (retarget) {
            int[] cell = nearestCreep(sim.getGrid(), creep, sim.world().cellX(body), sim.world().cellY(body));
            if (cell == null) {
                stance.creepX = -1;
                sim.clearPath(body);
                return;
            }
            stance.creepX = cell[0];
            stance.creepY = cell[1];
        }
        SwarmMovement.pathDirect(body, stance.creepX, stance.creepY, retarget, sim);
    }

    /** The nearest walkable living creep cell within {@link #CREEP_SEARCH_CELLS}, ring by ring; {@code null} for none. */
    static int[] nearestCreep(NavigationGrid grid, CreepField creep, int cx, int cy) {
        for (int r = 1; r <= CREEP_SEARCH_CELLS; r++) {
            int[] best = null;
            int bestD = Integer.MAX_VALUE;
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dy)) != r) continue;
                    int x = cx + dx;
                    int y = cy + dy;
                    if (!grid.inBounds(x, y) || !grid.isWalkable(x, y) || !creep.isCreeped(x, y)) continue;
                    int d = dx * dx + dy * dy;
                    if (d < bestD) {
                        bestD = d;
                        best = new int[]{x, y};
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * Of the ground enemies it senses between {@link #MIN_RANGE} and its reach, the one whose haze would catch the
     * most: bodies inside a haze's radius of it, counting more for a mark {@linkplain #STANDING_FIRE_BONUS standing
     * still and firing} and for each Rav body closing on it. Never a craft, and never a mark whose haze could cover
     * a melee: a swarm body already on anyone within a haze's radius plus the scatter of it. Ties go to the nearer, then the lower id. Shelter guards are left alone while the
     * shelter is sealed, as they are by every strain.
     */
    static long markInRange(long body, BattleSimulation sim) {
        float x = sim.world().x(body);
        float y = sim.world().y(body);
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        float range = sim.combat().attackRange(body);
        float haze = SporeHazes.RADIUS;
        boolean shelterProtected = sim.isCivilianShelterProtected();
        LongBucket nearby = CANDIDATES.get();
        LongBucket around = NEIGHBOURS.get();
        sim.getUnitIndex().gatherHostileCombatants(x, y, range, Faction.DEFENDER, nearby);
        long best = 0L;
        float bestScore = -1f;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = nearby.size; i < n; i++) {
            long candidate = nearby.ids[i];
            UnitType type = sim.identity().type(candidate);
            if (type.isFlier() || type == UnitType.BASED_AIRCRAFT || type == UnitType.DRONE) continue;
            if (!SwarmPressureBehavior.eligiblePreyKnownFaction(candidate, shelterProtected, sim)) continue;
            float cx = sim.world().x(candidate);
            float cy = sim.world().y(candidate);
            float d = (float) Math.hypot(cx - x, cy - y);
            if (!inLobRange(d, range)) continue;
            if (!SwarmPressureBehavior.canSense(cellX, cellY, cx, cy, range, sim)) continue;
            if (meleeUnderHaze(cx, cy, haze + scatterRadius(d, range), sim)) continue;
            sim.getUnitIndex().gatherHostileCombatants(cx, cy, haze, Faction.DEFENDER, around);
            float score = around.size;
            if (standingAndFiring(candidate, sim)) score *= STANDING_FIRE_BONUS;
            score += closingRav(cx, cy, sim);
            if (score > bestScore || (score == bestScore
                    && (d < bestDistance || (d == bestDistance && candidate < best)))) {
                best = candidate;
                bestScore = score;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Whether a mark at {@code distance} cells may be lobbed on by a Bombard reaching {@code range}. */
    static boolean inLobRange(float distance, float range) {
        return distance >= MIN_RANGE && distance <= range;
    }

    /** Scatter radius at {@code distance}: {@link #NEAR_SCATTER} at minimum range widening to {@link #FAR_SCATTER} at full. */
    static float scatterRadius(float distance, float range) {
        return NEAR_SCATTER + (FAR_SCATTER - NEAR_SCATTER) * reachShare(distance, range);
    }

    /** Glob flight at {@code distance}: {@link #NEAR_FLIGHT_SECONDS} at minimum range to {@link #FAR_FLIGHT_SECONDS} at full. */
    static float flightSeconds(float distance, float range) {
        return NEAR_FLIGHT_SECONDS + (FAR_FLIGHT_SECONDS - NEAR_FLIGHT_SECONDS) * reachShare(distance, range);
    }

    private static float reachShare(float distance, float range) {
        if (range <= MIN_RANGE) return 1f;
        return Math.max(0f, Math.min(1f, (distance - MIN_RANGE) / (range - MIN_RANGE)));
    }

    /** Whether a mark still holds at release: alive, still in reach and sensed, and not now in melee. */
    private static boolean stillMarked(long body, long mark, BattleSimulation sim) {
        if (mark == 0L || sim.resolveUnit(mark) == 0L || !sim.world().isAlive(mark)) return false;
        float x = sim.world().x(body);
        float y = sim.world().y(body);
        float mx = sim.world().x(mark);
        float my = sim.world().y(mark);
        float range = sim.combat().attackRange(body);
        float d = (float) Math.hypot(mx - x, my - y);
        return inLobRange(d, range)
                && SwarmPressureBehavior.canSense((int) Math.floor(x), (int) Math.floor(y), mx, my, range, sim)
                && !meleeUnderHaze(mx, my, SporeHazes.RADIUS + scatterRadius(d, range), sim);
    }

    /**
     * Whether anyone within {@code reach} of the point is in melee with the swarm: the reach is a haze's radius plus
     * the lob's scatter, everywhere a glob aimed there can catch.
     */
    private static boolean meleeUnderHaze(float x, float y, float reach, BattleSimulation sim) {
        LongBucket around = NEIGHBOURS.get();
        sim.getUnitIndex().gatherHostileCombatants(x, y, reach, Faction.DEFENDER, around);
        return meleeUnder(around, sim);
    }

    /** Whether any of {@code caught} is in melee with the swarm ({@link #inMelee}). */
    private static boolean meleeUnder(LongBucket caught, BattleSimulation sim) {
        for (int i = 0, n = caught.size; i < n; i++) {
            if (inMelee(caught.ids[i], sim)) return true;
        }
        return false;
    }

    /**
     * Whether a swarm fighter is on {@code body}: within {@link #MELEE_GUARD} of it, or within {@link #ENGAGED_RADIUS}
     * with it as its target, so a Brute pulling back between charges still counts as in the fight.
     */
    private static boolean inMelee(long body, BattleSimulation sim) {
        float x = sim.world().x(body);
        float y = sim.world().y(body);
        LongBucket around = SWARM.get();
        sim.getUnitIndex().gatherFaction(x, y, ENGAGED_RADIUS, Faction.DEFENDER, around);
        for (int i = 0, n = around.size; i < n; i++) {
            long id = around.ids[i];
            UnitType type = sim.identity().type(id);
            if (!type.isSwarm() || type == UnitType.SWARM_WORKER || type == UnitType.SWARM_BOMBARD) continue;
            if (!sim.world().isAlive(id)) continue;
            float dx = sim.world().x(id) - x;
            float dy = sim.world().y(id) - y;
            float d2 = dx * dx + dy * dy;
            if (d2 <= MELEE_GUARD * MELEE_GUARD) return true;
            if (d2 <= ENGAGED_RADIUS * ENGAGED_RADIUS && sim.combat().targetId(id) == body) return true;
        }
        return false;
    }

    /** Rav fighters within {@link #CLOSING_RADIUS} of the point, not counting Bombards and skitterers. */
    private static int closingRav(float x, float y, BattleSimulation sim) {
        LongBucket around = SWARM.get();
        sim.getUnitIndex().gatherFaction(x, y, CLOSING_RADIUS, Faction.DEFENDER, around);
        int count = 0;
        for (int i = 0, n = around.size; i < n; i++) {
            UnitType type = sim.identity().type(around.ids[i]);
            if (type.isSwarm() && type != UnitType.SWARM_WORKER && type != UnitType.SWARM_BOMBARD) count++;
        }
        return count;
    }

    /** A mark with a target and no path left to walk: a squad holding a line and shooting. */
    private static boolean standingAndFiring(long mark, BattleSimulation sim) {
        if (sim.combat().targetId(mark) == 0L) return false;
        if (sim.identity().type(mark).isStatic()) return true;
        return sim.movement().pathIdx(mark) >= Paths.cellCount(sim.movement().path(mark));
    }

    private static void fling(long body, long mark, float scale, BattleSimulation sim) {
        WeaponDef weapon = glob();
        float fromX = sim.world().x(body);
        float fromY = sim.world().y(body);
        float tx = sim.world().cellX(mark) + 0.5f;
        float ty = sim.world().cellY(mark) + 0.5f;
        float range = sim.combat().attackRange(body);
        float distance = (float) Math.hypot(tx - fromX, ty - fromY);
        // Drawn from a hash of the body and tick: the parallel update may not share the battle's random stream.
        long sample = mix(body * 0x9E3779B97F4A7C15L ^ (long) sim.getSimTickIndex() * 0xD1B54A32D192ED03L);
        float angle = (float) ((sample & 0xFFFF) / 65536.0 * Math.PI * 2);
        float radius = (float) Math.sqrt(((sample >>> 16) & 0xFFFF) / 65536.0) * scatterRadius(distance, range);
        NavigationGrid grid = sim.getGrid();
        float ex = Math.max(0f, Math.min(grid.getWidth() - 0.01f, tx + (float) Math.cos(angle) * radius));
        float ey = Math.max(0f, Math.min(grid.getHeight() - 0.01f, ty + (float) Math.sin(angle) * radius));
        float flight = flightSeconds(distance, range);
        Faction faction = sim.identity().faction(body);
        sim.queueProjectile(new Projectile(fromX, fromY, ex, ey, weapon.boostRamp, weapon.arcHeight, faction,
                true, flight, null, weapon.id, weapon.pointDefenseTarget));
        sim.postShot(new ShotEvent(body, fromX, fromY, ex, ey, true, faction, flight, null, null, null, weapon,
                sim.identity().type(body).moraleImpact));
        int arrival = sim.getSimTickIndex() + Math.max(1, Math.round(flight / BattleSimulation.TICK_DT));
        sim.swarmBombards().postGlob(body, sim.world().cellX(body), sim.world().cellY(body), ex, ey, scale, arrival);
        sim.combat().setCooldownTimer(body, SwarmPressureBehavior.strikeCooldown(body, sim));
        sim.combat().setTargetId(body, 0L);
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
