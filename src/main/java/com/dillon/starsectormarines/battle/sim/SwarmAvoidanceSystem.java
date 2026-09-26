package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/**
 * Adds a short-range repulsion steer to allied infantry near hostile aliens.
 * The steer is applied after authored path movement, so it bends or slows an
 * advance without replacing the squad's objective or path. Several aliens in
 * the same direction add together up to a bounded retreat speed, making a
 * swarm more intimidating than one isolated runner. The combined authored and
 * avoidance velocity is capped at the unit's movement-speed stat.
 */
public final class SwarmAvoidanceSystem {

    /** Distance at which alien proximity first begins to influence movement. */
    public static final float AVOID_RADIUS = 5f;
    /** Maximum contribution from one alien at point-blank range, in cells/sec. */
    public static final float PER_ALIEN_AVOID_SPEED = 2.5f;
    /** Total avoidance speed cap after all nearby alien contributions are summed. */
    public static final float MAX_AVOID_SPEED = 2.5f;
    /** Covers one tick of runner motion beyond the spatial index's tick-start snapshot. */
    private static final float QUERY_MARGIN = 0.25f;
    private static final float COINCIDENT_EPS = 1e-4f;

    private final UnitRosterService roster;
    private final World world;
    private final UnitSpatialIndex unitIndex;
    private final NavigationGrid grid;
    private final EntityWorld entityWorld;
    private final BattleComponents components;
    private final LongBucket nearby = new LongBucket();

    public SwarmAvoidanceSystem(UnitRosterService roster,
                                UnitSpatialIndex unitIndex,
                                NavigationGrid grid) {
        this.roster = roster;
        this.world = roster.world();
        this.unitIndex = unitIndex;
        this.grid = grid;
        this.entityWorld = roster.entityWorld();
        this.components = roster.components();
    }

    /** Applies one bounded avoidance steer to each live allied infantry unit. */
    public void tick(float dt) {
        tick(dt, 0L);
    }

    /** Manual drive owns the controlled member's response to nearby threats. */
    public void tick(float dt, long controlledUnitId) {
        if (dt <= 0f) return;
        long[] dense = roster.denseArray();
        int liveCount = roster.liveCount();
        for (int i = 0; i < liveCount; i++) {
            long marine = dense[i];
            if (marine == controlledUnitId) continue;
            if (!avoidsAliens(marine)) continue;
            applyAvoidance(marine, dt);
        }
    }

    private void applyAvoidance(long marine, float dt) {
        float mx = world.x(marine);
        float my = world.y(marine);
        unitIndex.gather(mx, my, AVOID_RADIUS + QUERY_MARGIN, nearby);
        float steerX = 0f;
        float steerY = 0f;
        Faction marineFaction = roster.identity().faction(marine);
        for (int i = 0, n = nearby.size; i < n; i++) {
            long alien = nearby.ids[i];
            if (alien == marine || !isHostileAlien(alien, marineFaction)) continue;
            float dx = mx - world.x(alien);
            float dy = my - world.y(alien);
            float dist2 = dx * dx + dy * dy;
            if (dist2 >= AVOID_RADIUS * AVOID_RADIUS) continue;
            float dist = (float) Math.sqrt(dist2);
            float dirX;
            float dirY;
            if (dist < COINCIDENT_EPS) {
                float angle = coincidentAngle(marine, alien);
                dirX = (float) Math.cos(angle);
                dirY = (float) Math.sin(angle);
            } else {
                dirX = dx / dist;
                dirY = dy / dist;
            }
            float strength = PER_ALIEN_AVOID_SPEED
                    * (1f - dist / AVOID_RADIUS);
            steerX += dirX * strength;
            steerY += dirY * strength;
        }
        float magnitude = (float) Math.sqrt(steerX * steerX + steerY * steerY);
        if (magnitude <= 0f) return;
        if (magnitude > MAX_AVOID_SPEED) {
            float scale = MAX_AVOID_SPEED / magnitude;
            steerX *= scale;
            steerY *= scale;
        }

        float currentVx = entityWorld.getFloat(marine, components.MOVEMENT,
                BattleComponents.MOVEMENT_VEL_X);
        float currentVy = entityWorld.getFloat(marine, components.MOVEMENT,
                BattleComponents.MOVEMENT_VEL_Y);
        float combinedVx = currentVx + steerX;
        float combinedVy = currentVy + steerY;
        float combinedSpeed = (float) Math.sqrt(
                combinedVx * combinedVx + combinedVy * combinedVy);
        float speedLimit = roster.movement().moveSpeed(marine);
        if (combinedSpeed > speedLimit) {
            float scale = speedLimit / combinedSpeed;
            combinedVx *= scale;
            combinedVy *= scale;
        }
        steerX = combinedVx - currentVx;
        steerY = combinedVy - currentVy;
        move(marine, steerX * dt, steerY * dt, dt);
    }

    private void move(long marine, float dx, float dy, float dt) {
        float x = world.x(marine);
        float y = world.y(marine);
        float nx = x + dx;
        float ny = y + dy;
        float appliedX;
        float appliedY;
        if (walkable(nx, ny)) {
            world.setPos(marine, nx, ny);
            appliedX = dx;
            appliedY = dy;
        } else if (walkable(nx, y)) {
            world.setPos(marine, nx, y);
            appliedX = dx;
            appliedY = 0f;
        } else if (walkable(x, ny)) {
            world.setPos(marine, x, ny);
            appliedX = 0f;
            appliedY = dy;
        } else {
            return;
        }
        foldIntoVelocity(marine, appliedX / dt, appliedY / dt);
    }

    private boolean walkable(float x, float y) {
        return grid.isWalkable((int) Math.floor(x), (int) Math.floor(y));
    }

    private boolean avoidsAliens(long id) {
        if (!roster.isAliveById(id)
                || roster.identity().faction(id) != Faction.MARINE
                || !roster.movement().has(id)) return false;
        UnitType type = roster.identity().type(id);
        return type == UnitType.MARINE || type == UnitType.MARINE_BLUE
                || type == UnitType.MARINE_RED || type == UnitType.MILITIA;
    }

    private boolean isHostileAlien(long id, Faction marineFaction) {
        if (!roster.isAliveById(id)
                || !marineFaction.hostileTo(roster.identity().faction(id))) return false;
        UnitType type = roster.identity().type(id);
        return type == UnitType.ALIEN || type == UnitType.SWARM_RUNNER;
    }

    private void foldIntoVelocity(long id, float dvx, float dvy) {
        float vx = entityWorld.getFloat(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_VEL_X);
        float vy = entityWorld.getFloat(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_VEL_Y);
        entityWorld.setFloat(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_VEL_X, vx + dvx);
        entityWorld.setFloat(id, components.MOVEMENT,
                BattleComponents.MOVEMENT_VEL_Y, vy + dvy);
    }

    private static float coincidentAngle(long marine, long alien) {
        long h = marine * 0x9E3779B97F4A7C15L
                + alien * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        float turn = (h & 0xFFFFFFFFL) / (float) 0x100000000L;
        return turn * (float) (Math.PI * 2.0);
    }
}
