package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.world.model.CellTopology;

/**
 * The swarm Brute: slow, plated, and the strain that makes a held building
 * stop being a safe room.
 *
 * <p>It hunts like a Runner (the same sensing, the same {@link
 * SwarmPressureBehavior#pressToward} strategic move) with two differences.
 * A sensed emplacement outranks infantry, because a turret is what a swarm
 * cannot otherwise get past. And when its prey stands inside a building it is
 * not in, and no opening into that building is near, it makes one: it walks to
 * the nearest light wall or window of that building and strikes it until it
 * breaks, through the ordinary structural path ({@link SwarmStrikeSystem}),
 * and then goes in through the hole. Doors are already openings; a Brute uses
 * one if one is near rather than breaking the wall beside it.
 *
 * <p>"Light" is a wall a colony builds: at most {@link #LIGHT_WALL_HP} of wall
 * left. Fortified and fort walls start far above that, so a Brute cannot dig
 * into a bunker until something else has worn it down.
 */
final class SwarmBruteBehavior {

    /** Walls with more left than this are not the Brute's to break. Colony walls stand at 80-100. */
    static final int LIGHT_WALL_HP = 120;
    /** Wall damage per blow; a 100 HP colony wall falls to four or five. */
    static final int WALL_BLOW = 25;
    /** How far from its feet a Brute looks for a feature to break, or an opening to use instead. */
    static final int BREACH_SEARCH_RADIUS = 8;
    /** An opening into the building this close means the Brute goes through it rather than making one. */
    static final float OPENING_RADIUS = 4.5f;
    /**
     * Penetration of the Brute's crushing blow. A Runner's slash has none and only scratches
     * plate; a Brute's goes through infantry armour and an emplacement's shell alike.
     */
    static final float MELEE_PENETRATION = 8f;
    /** Reach of a blow on a structure, from the Brute's centre to the struck cell or edge. */
    static final float STRUCTURE_REACH = 1.6f;

    private static final ThreadLocal<LongBucket> CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);

    private SwarmBruteBehavior() {}

    static void update(long brute, BattleSimulation sim) {
        SwarmPressureBehavior.tickCooldown(brute, sim);
        long previousTarget = sim.combat().targetId(brute);
        long target = sensedEmplacement(brute, sim);
        if (target == 0L) target = SwarmPressureBehavior.selectTarget(brute, sim);
        sim.combat().setTargetId(brute, target);
        if (target == 0L) {
            SwarmPressureBehavior.updateRoaming(brute, previousTarget, sim);
            return;
        }

        float dx = sim.world().x(target) - sim.world().x(brute);
        float dy = sim.world().y(target) - sim.world().y(brute);
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float reach = sim.combat().attackRange(brute) + sim.getRoster().radius(target);
        if (distance <= reach) {
            sim.clearPath(brute);
            if (sim.combat().cooldownTimer(brute) <= 0f) {
                sim.applyDamage(target, brute, sim.combat().attackDamage(brute), MELEE_PENETRATION,
                        sim.identity().type(brute).moraleImpact);
                sim.combat().setCooldownTimer(brute, sim.combat().attackCooldown(brute));
            }
            return;
        }

        BreachPoint breach = breachPointToward(brute, target, sim);
        if (breach != null) {
            if (breach.inReach) {
                sim.clearPath(brute);
                if (sim.combat().cooldownTimer(brute) <= 0f) {
                    sim.swarmStrikes().postBlow(brute, breach.cellX, breach.cellY,
                            breach.edge, WALL_BLOW);
                    sim.combat().setCooldownTimer(brute, sim.combat().attackCooldown(brute));
                }
                return;
            }
            SwarmPressureBehavior.pressToward(brute, breach.approachX, breach.approachY,
                    target != previousTarget, sim);
            return;
        }
        if (sim.identity().type(target).isStatic()) {
            // An emplacement seals its own footprint, so there is no path to its cell:
            // close on the open ground beside it instead.
            int[] stand = standBeside(brute, target, sim);
            if (stand != null) {
                SwarmPressureBehavior.pressToward(brute, stand[0], stand[1],
                        target != previousTarget, sim);
                return;
            }
        }
        SwarmPressureBehavior.pressToward(brute, target, target != previousTarget, sim);
    }

    /** The walkable cell nearest the Brute from which it can reach {@code target}, or null. */
    static int[] standBeside(long brute, long target, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        float tx = sim.world().x(target);
        float ty = sim.world().y(target);
        float px = sim.world().x(brute);
        float py = sim.world().y(brute);
        float reach = sim.combat().attackRange(brute) + sim.getRoster().radius(target);
        int span = (int) Math.ceil(reach);
        int[] best = null;
        float bestDistance = Float.MAX_VALUE;
        for (int y = (int) Math.floor(ty) - span; y <= (int) Math.floor(ty) + span; y++) {
            for (int x = (int) Math.floor(tx) - span; x <= (int) Math.floor(tx) + span; x++) {
                if (!grid.inBounds(x, y) || !grid.isWalkable(x, y)) continue;
                float rx = x + 0.5f - tx;
                float ry = y + 0.5f - ty;
                if (rx * rx + ry * ry > reach * reach) continue;
                float bx = x + 0.5f - px;
                float by = y + 0.5f - py;
                float d = bx * bx + by * by;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best;
    }

    /** The nearest emplacement the Brute senses, or 0. */
    static long sensedEmplacement(long brute, BattleSimulation sim) {
        float x = sim.world().x(brute);
        float y = sim.world().y(brute);
        int cellX = (int) Math.floor(x);
        int cellY = (int) Math.floor(y);
        float sense = sim.vision().visionRange(brute);
        LongBucket nearby = CANDIDATES.get();
        sim.getUnitIndex().gatherHostileCombatants(x, y, sense, Faction.DEFENDER, nearby);
        long best = 0L;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = nearby.size; i < n; i++) {
            long candidate = nearby.ids[i];
            if (!sim.identity().type(candidate).isTurret()) continue;
            float cx = sim.world().x(candidate);
            float cy = sim.world().y(candidate);
            if (!SwarmPressureBehavior.canSense(cellX, cellY, cx, cy, sense, sim)) continue;
            float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (d < bestDistance || (d == bestDistance && candidate < best)) {
                best = candidate;
                bestDistance = d;
            }
        }
        return best;
    }

    /** Where the Brute should strike to get at {@code target}, or null when it should simply press on. */
    static final class BreachPoint {
        final int cellX;
        final int cellY;
        /** Set when the struck thing is a window on the edge leaving {@code cellX, cellY} this way. */
        final Direction edge;
        final int approachX;
        final int approachY;
        final boolean inReach;

        BreachPoint(int cellX, int cellY, Direction edge, int approachX, int approachY,
                    boolean inReach) {
            this.cellX = cellX;
            this.cellY = cellY;
            this.edge = edge;
            this.approachX = approachX;
            this.approachY = approachY;
            this.inReach = inReach;
        }
    }

    /**
     * The breach the Brute makes into the building its target stands in: null when the
     * target is in the open, the Brute is already inside, the building is beyond the search
     * radius, or an opening into it is near enough to use.
     */
    static BreachPoint breachPointToward(long brute, long target, BattleSimulation sim) {
        CellTopology topology = sim.getTopology();
        NavigationGrid grid = sim.getGrid();
        int building = topology.getBuildingId(sim.world().cellX(target), sim.world().cellY(target));
        if (building == 0) return null;
        int bruteX = sim.world().cellX(brute);
        int bruteY = sim.world().cellY(brute);
        if (topology.getBuildingId(bruteX, bruteY) == building) return null;
        float px = sim.world().x(brute);
        float py = sim.world().y(brute);

        BreachPoint best = null;
        float bestScore = Float.MAX_VALUE;
        float openingLimit = OPENING_RADIUS * OPENING_RADIUS;
        for (int y = bruteY - BREACH_SEARCH_RADIUS; y <= bruteY + BREACH_SEARCH_RADIUS; y++) {
            for (int x = bruteX - BREACH_SEARCH_RADIUS; x <= bruteX + BREACH_SEARCH_RADIUS; x++) {
                if (!grid.inBounds(x, y)) continue;
                // An outside standing cell next to the building: an opening if the step in is
                // clear, otherwise the window edge or wall cell beside it is a candidate breach.
                if (!grid.isWalkable(x, y) || topology.getBuildingId(x, y) == building) continue;
                for (Direction d : Direction.CARDINALS) {
                    int nx = x + d.dx;
                    int ny = y + d.dy;
                    if (!grid.inBounds(nx, ny)) continue;
                    float ax = x + 0.5f - px;
                    float ay = y + 0.5f - py;
                    float approach = ax * ax + ay * ay;
                    SharedEdgeBarrier barrier = grid.getEdgeBarrier(x, y, d);
                    boolean inside = topology.getBuildingId(nx, ny) == building;
                    if (inside && grid.isWalkable(nx, ny) && barrier == null) {
                        if (approach <= openingLimit) return null;
                        continue;
                    }
                    BreachPoint candidate = null;
                    if (inside && barrier != null
                            && barrier.kind() == SharedEdgeBarrier.Kind.WINDOW) {
                        float mx = x + 0.5f + d.dx * 0.5f - px;
                        float my = y + 0.5f + d.dy * 0.5f - py;
                        candidate = new BreachPoint(x, y, d, x, y,
                                mx * mx + my * my <= STRUCTURE_REACH * STRUCTURE_REACH);
                    } else if (breakableWall(nx, ny, building, grid, topology)) {
                        float mx = nx + 0.5f - px;
                        float my = ny + 0.5f - py;
                        candidate = new BreachPoint(nx, ny, null, x, y,
                                mx * mx + my * my <= STRUCTURE_REACH * STRUCTURE_REACH);
                    }
                    if (candidate != null && approach < bestScore) {
                        best = candidate;
                        bestScore = approach;
                    }
                }
            }
        }
        return best;
    }

    /**
     * A wall or window cell of {@code building} light enough to break: impassable, at most
     * {@link #LIGHT_WALL_HP} left, and either part of the building or touching its inside.
     */
    static boolean breakableWall(int x, int y, int building, NavigationGrid grid,
                                 CellTopology topology) {
        if (grid.isWalkable(x, y)) return false;
        if (!topology.isWall(x, y) && !topology.isWindow(x, y)) return false;
        int hp = grid.getWallHp(x, y);
        if (hp <= 0 || hp > LIGHT_WALL_HP) return false;
        if (topology.getBuildingId(x, y) == building) return true;
        for (Direction d : Direction.CARDINALS) {
            int nx = x + d.dx;
            int ny = y + d.dy;
            if (grid.inBounds(nx, ny) && topology.getBuildingId(nx, ny) == building) return true;
        }
        return false;
    }
}
