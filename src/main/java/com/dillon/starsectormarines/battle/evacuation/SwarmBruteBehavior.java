package com.dillon.starsectormarines.battle.evacuation;

import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.GroundBody;
import com.dillon.starsectormarines.battle.vehicle.HullImpact;
import com.dillon.starsectormarines.battle.vehicle.HullReach;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
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
 *
 * <p>A barricade is broken the same way. When its prey stands in the open but
 * behind a fence, a crate stack, any other destructible cover prop or a light
 * free-standing wall that closes the ground between them (a redoubt's works, a
 * colony compound's curtain, a fenced yard), the
 * Brute strikes the nearest light piece that stands between and goes through,
 * rather than walking round to the gate the Runners crowd into.
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

    /**
     * What a strain can break and from where: walls with at most {@code wallHp} left, struck from within
     * {@code reach} of its centre, unless an opening lies within {@code openingRadius} of its feet.
     */
    record Breaker(int wallHp, float reach, float openingRadius) {}

    static final Breaker BRUTE = new Breaker(LIGHT_WALL_HP, STRUCTURE_REACH, OPENING_RADIUS);

    private static final ThreadLocal<LongBucket> CANDIDATES =
            ThreadLocal.withInitial(LongBucket::new);

    private SwarmBruteBehavior() {}

    static void update(long brute, BattleSimulation sim) {
        SwarmPressureBehavior.tickCooldown(brute, sim);
        // Stunned, or in a charge: the charge system moves it, and a stunned Brute does nothing at all.
        if (sim.stagger().isStaggered(brute) || sim.bruteCharges().busy(brute)) {
            sim.clearPath(brute);
            return;
        }
        long previousTarget = sim.combat().targetId(brute);
        long target = sensedEmplacement(brute, sim);
        if (target == 0L) target = SwarmPressureBehavior.selectTarget(brute, sim);
        sim.combat().setTargetId(brute, target);
        if (target == 0L) {
            SwarmPressureBehavior.updateRoaming(brute, previousTarget, sim);
            return;
        }
        if (target != previousTarget && !sim.swarmPacks().followsLeader(brute, target, sim)) {
            sim.swarmCues().post(SwarmCues.Kind.ALERT, brute, sim);
        }

        float dx = sim.world().x(target) - sim.world().x(brute);
        float dy = sim.world().y(target) - sim.world().y(brute);
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float gap = gap(brute, target, sim);
        boolean hull = sim.convoy().isVehicle(target) && sim.convoy().body(target) != null;
        // A hull is struck once the Brute touches its side; anything else under its head.
        if (gap <= (hull ? sim.getRoster().radius(brute) + HullReach.TOUCH : BruteChargeRules.BLOW_REACH)) {
            sim.clearPath(brute);
            if (sim.combat().cooldownTimer(brute) <= 0f) {
                if (BruteChargeSystem.kind(target, sim) == BruteChargeRules.Kind.INFANTRY) {
                    // On infantry the blow is a trample: rear, and come down on them with the whole body.
                    sim.bruteCharges().trample(brute, target);
                } else {
                    sim.applyDamage(target, brute, sim.combat().attackDamage(brute), MELEE_PENETRATION,
                            sim.identity().type(brute).moraleImpact);
                    // The weight behind it lands too: a hull or a mech is pushed by it.
                    sim.bruteCharges().blow(brute, target);
                }
                sim.swarmCues().post(SwarmCues.Kind.STRIKE, brute, sim);
                sim.combat().setCooldownTimer(brute, SwarmPressureBehavior.strikeCooldown(brute, sim));
            }
            return;
        }

        // Within its blow's reach of a hull's side it closes the rest straight in.
        if (hull && gap <= BruteChargeRules.BLOW_REACH && SwarmMovement.closeOnHull(brute, sim.convoy().body(target),
                sim.convoy().vehicleType(target), sim)) {
            return;
        }
        if (tryCharge(brute, target, sim.world().x(target), sim.world().y(target), distance, sim)) return;

        BreachPoint breach = breachPointToward(brute, target, sim);
        if (breach == null) breach = barricadeToward(brute, target, sim);
        if (breach != null) {
            // The light wall it means to break, charged when it stands in the band with open ground between.
            float wallX = breach.cellX + .5f + (breach.edge == null ? 0f : breach.edge.dx * .5f);
            float wallY = breach.cellY + .5f + (breach.edge == null ? 0f : breach.edge.dy * .5f);
            float toWall = (float) Math.hypot(wallX - sim.world().x(brute), wallY - sim.world().y(brute));
            if (!breach.inReach && tryCharge(brute, 0L, wallX, wallY, toWall, sim)) return;
            if (breach.inReach) {
                sim.clearPath(brute);
                if (sim.combat().cooldownTimer(brute) <= 0f) {
                    sim.swarmStrikes().postBlow(brute, breach.cellX, breach.cellY,
                            breach.edge, WALL_BLOW);
                    sim.combat().setCooldownTimer(brute, SwarmPressureBehavior.strikeCooldown(brute, sim));
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

    /**
     * Posts a charge at {@code target} (0 for a wall) aimed at {@code (aimX, aimY)} when the rules allow
     * one: in the band, an open line, the cooldown spent. True when it did, and the Brute holds for it.
     */
    private static boolean tryCharge(long brute, long target, float aimX, float aimY, float distance,
                                     BattleSimulation sim) {
        BruteChargeSystem charges = sim.bruteCharges();
        int now = sim.getSimTickIndex();
        if (!charges.ready(brute, now)) return false;
        boolean open = lineOpen(sim, sim.world().cellX(brute), sim.world().cellY(brute),
                (int) Math.floor(aimX), (int) Math.floor(aimY));
        if (!BruteChargeRules.shouldCharge(distance, open, now, 0)) return false;
        charges.request(brute, target, aimX, aimY);
        sim.clearPath(brute);
        return true;
    }

    /**
     * Whether the straight line between two cells, ends excluded, is ground a charge can cross: walkable,
     * or a light barricade prop it will bowl through.
     */
    static boolean lineOpen(BattleSimulation sim, int x0, int y0, int x1, int y1) {
        NavigationGrid grid = sim.getGrid();
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy, x = x0, y = y0;
        while (x != x1 || y != y1) {
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
            if (x == x1 && y == y1) break;
            if (!grid.inBounds(x, y)) return false;
            if (grid.isWalkable(x, y)) continue;
            int hp = grid.getWallHp(x, y);
            if (!sim.isBarricadeAt(x, y) || hp <= 0 || hp > LIGHT_WALL_HP) return false;
        }
        return true;
    }

    /**
     * How far the Brute's centre stands from {@code target}'s surface: a hull's footprint, or the circle of
     * any other body. Its blow lands within {@link BruteChargeRules#BLOW_REACH} of it.
     */
    static float gap(long brute, long target, BattleSimulation sim) {
        float bx = sim.world().x(brute), by = sim.world().y(brute);
        if (sim.convoy().isVehicle(target)) {
            GroundBody hull = sim.convoy().body(target);
            VehicleType type = sim.convoy().vehicleType(target);
            if (hull != null && type != null) {
                return HullImpact.surfaceDistance(hull.x, hull.y, hull.facingDegrees,
                        type.visualLengthCells / 2f, type.visualWidthCells / 2f, bx, by);
            }
        }
        float d = (float) Math.hypot(sim.world().x(target) - bx, sim.world().y(target) - by);
        return Math.max(0f, d - sim.getRoster().radius(target));
    }

    /** The walkable cell nearest the Brute from which it can reach {@code target}, or null. */
    static int[] standBeside(long brute, long target, BattleSimulation sim) {
        return standBeside(brute, target, BruteChargeRules.BLOW_REACH, sim);
    }

    /** {@link #standBeside(long, long, BattleSimulation)} for a blow that lands within {@code blowReach}. */
    static int[] standBeside(long brute, long target, float blowReach, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        float tx = sim.world().x(target);
        float ty = sim.world().y(target);
        float px = sim.world().x(brute);
        float py = sim.world().y(brute);
        float reach = blowReach + sim.getRoster().radius(target);
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
        return breachPointToward(brute, target, BRUTE, sim);
    }

    static BreachPoint breachPointToward(long brute, long target, Breaker breaker, BattleSimulation sim) {
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
        float openingLimit = breaker.openingRadius() * breaker.openingRadius();
        float reach = breaker.reach() * breaker.reach();
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
                        candidate = new BreachPoint(x, y, d, x, y, mx * mx + my * my <= reach);
                    } else if (breakableWall(nx, ny, building, breaker.wallHp(), grid, topology)) {
                        float mx = nx + 0.5f - px;
                        float my = ny + 0.5f - py;
                        candidate = new BreachPoint(nx, ny, null, x, y, mx * mx + my * my <= reach);
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
     * The barricade the Brute should break to reach prey in the open behind
     * it: null when the prey is far, when nothing closes the straight way to
     * it, or when no light barricade piece within the search radius stands
     * between them. A piece qualifies when the Brute can stand on its near
     * side and its far side is open ground nearer the prey than the Brute is.
     */
    static BreachPoint barricadeToward(long brute, long target, BattleSimulation sim) {
        return barricadeToward(brute, target, BRUTE, sim);
    }

    static BreachPoint barricadeToward(long brute, long target, Breaker breaker, BattleSimulation sim) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        int bruteX = sim.world().cellX(brute), bruteY = sim.world().cellY(brute);
        int preyX = sim.world().cellX(target), preyY = sim.world().cellY(target);
        int reach = BREACH_SEARCH_RADIUS * 2;
        int toPrey = sq(preyX - bruteX) + sq(preyY - bruteY);
        if (toPrey > reach * reach || !blockedBetween(grid, bruteX, bruteY, preyX, preyY)) return null;
        float px = sim.world().x(brute), py = sim.world().y(brute);
        BreachPoint best = null;
        int bestScore = Integer.MAX_VALUE;
        for (int y = bruteY - BREACH_SEARCH_RADIUS; y <= bruteY + BREACH_SEARCH_RADIUS; y++) {
            for (int x = bruteX - BREACH_SEARCH_RADIUS; x <= bruteX + BREACH_SEARCH_RADIUS; x++) {
                if (!grid.inBounds(x, y) || grid.isWalkable(x, y)) continue;
                int hp = grid.getWallHp(x, y);
                if (hp <= 0 || hp > breaker.wallHp()) continue;
                // A barricade prop, or a free-standing wall such as a compound's curtain: no building's own wall.
                boolean freeWall = (topology.isWall(x, y) || topology.isWindow(x, y)) && freeStanding(x, y, grid, topology);
                if (!freeWall && !sim.isBarricadeAt(x, y)) continue;
                for (Direction d : Direction.CARDINALS) {
                    int nearX = x - d.dx, nearY = y - d.dy, farX = x + d.dx, farY = y + d.dy;
                    if (!grid.inBounds(nearX, nearY) || !grid.inBounds(farX, farY)
                            || !grid.isWalkable(nearX, nearY) || !grid.isWalkable(farX, farY)) continue;
                    int far = sq(preyX - farX) + sq(preyY - farY);
                    if (far >= toPrey || far >= sq(preyX - nearX) + sq(preyY - nearY)) continue;
                    int score = sq(nearX - bruteX) + sq(nearY - bruteY) + far;
                    if (score >= bestScore) continue;
                    float mx = x + 0.5f - px, my = y + 0.5f - py;
                    best = new BreachPoint(x, y, null, nearX, nearY,
                            mx * mx + my * my <= breaker.reach() * breaker.reach());
                    bestScore = score;
                }
            }
        }
        return best;
    }

    /** Whether a wall cell belongs to no building: neither it nor any cell beside it is a building's. */
    private static boolean freeStanding(int x, int y, NavigationGrid grid, CellTopology topology) {
        for (int ny = y - 1; ny <= y + 1; ny++) {
            for (int nx = x - 1; nx <= x + 1; nx++) {
                if (grid.inBounds(nx, ny) && topology.getBuildingId(nx, ny) != 0) return false;
            }
        }
        return true;
    }

    /** Whether a cell that cannot be walked lies on the straight line between two cells. */
    private static boolean blockedBetween(NavigationGrid grid, int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy, x = x0, y = y0;
        while (x != x1 || y != y1) {
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
            if ((x != x1 || y != y1) && grid.inBounds(x, y) && !grid.isWalkable(x, y)) return true;
        }
        return false;
    }

    private static int sq(int v) {
        return v * v;
    }

    /**
     * A wall or window cell of {@code building} light enough to break: impassable, at most
     * {@link #LIGHT_WALL_HP} left, and either part of the building or touching its inside.
     */
    static boolean breakableWall(int x, int y, int building, NavigationGrid grid,
                                 CellTopology topology) {
        return breakableWall(x, y, building, LIGHT_WALL_HP, grid, topology);
    }

    /** {@link #breakableWall(int, int, int, NavigationGrid, CellTopology)} up to {@code wallHp} left. */
    static boolean breakableWall(int x, int y, int building, int wallHp, NavigationGrid grid,
                                 CellTopology topology) {
        if (grid.isWalkable(x, y)) return false;
        if (!topology.isWall(x, y) && !topology.isWindow(x, y)) return false;
        int hp = grid.getWallHp(x, y);
        if (hp <= 0 || hp > wallHp) return false;
        if (topology.getBuildingId(x, y) == building) return true;
        for (Direction d : Direction.CARDINALS) {
            int nx = x + d.dx;
            int ny = y + d.dy;
            if (grid.inBounds(nx, ny) && topology.getBuildingId(nx, ny) == building) return true;
        }
        return false;
    }
}
