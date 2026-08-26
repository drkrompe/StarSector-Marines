package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * Battle-owned ambient work assignment and deterministic choreography.
 *
 * <p>An active assignment temporarily owns an actor's movement and layered
 * appearance. The ordinary unit dispatcher skips that actor. A route's threat
 * policy may release the assignment before the dispatch, after which the
 * actor's existing role immediately resumes normal battle behavior. Embedded
 * scenes use {@link #seek(float)} to sample exact wall-clock time without
 * advancing combat; the standalone battle calls {@link #advance(float)} and
 * {@link #applyAppearance()} from its normal tick pipeline.</p>
 */
public final class AmbientTaskService {

    private static final float FIRE_BEGIN = 0.58f;
    private static final float FIRE_END = 0.72f;

    private final UnitRosterService roster;
    private final World world;
    private final EntityWorld entities;
    private final BattleComponents components;
    private final LongConsumer clearPath;
    private final Map<Long, AmbientTaskRoute> assignments = new ConcurrentHashMap<>();
    private float elapsedSeconds;

    public AmbientTaskService(UnitRosterService roster, LongConsumer clearPath) {
        this.roster = roster;
        this.world = roster.world();
        this.entities = roster.entityWorld();
        this.components = roster.components();
        this.clearPath = clearPath;
    }

    public void assign(long actorId, AmbientTaskRoute route) {
        if (route == null) throw new IllegalArgumentException("ambient route is required");
        if (!roster.isLive(actorId)) throw new IllegalArgumentException("ambient actor must be live");
        if (!world.hasMovement(actorId)) throw new IllegalArgumentException("ambient actor must be mobile");
        if (!world.hasLayeredAppearance(actorId)) {
            throw new IllegalArgumentException("ambient actor requires layered appearance");
        }
        clearPath.accept(actorId);
        assignments.put(actorId, route);
        AmbientTaskPose pose = sample(route, elapsedSeconds);
        applyPosition(actorId, pose);
        applyAppearance(actorId, pose);
    }

    public void release(long actorId) {
        assignments.remove(actorId);
    }

    public boolean isControlling(long actorId) {
        return assignments.containsKey(actorId);
    }

    public int assignmentCount() {
        return assignments.size();
    }

    /** Advances live-battle time, releases interrupted work, and writes positions. */
    public void advance(float dt) {
        if (!Float.isFinite(dt)) throw new IllegalArgumentException("ambient dt must be finite");
        elapsedSeconds += Math.max(0f, dt);
        assignments.forEach((actorId, route) -> {
            if (!roster.isLive(actorId) || isThreatened(actorId, route)) {
                assignments.remove(actorId, route);
                return;
            }
            applyPosition(actorId, sample(route, elapsedSeconds));
        });
    }

    /** Exact-time sampling for bounded scenes which deliberately do not tick combat. */
    public void seek(float elapsedSeconds) {
        if (!Float.isFinite(elapsedSeconds)) {
            throw new IllegalArgumentException("ambient seek time must be finite");
        }
        this.elapsedSeconds = Math.max(0f, elapsedSeconds);
        assignments.forEach((actorId, route) -> {
            if (!roster.isLive(actorId)) {
                assignments.remove(actorId, route);
                return;
            }
            AmbientTaskPose pose = sample(route, this.elapsedSeconds);
            applyPosition(actorId, pose);
            applyAppearance(actorId, pose);
        });
    }

    /** Reasserts task poses after the ordinary battle FacingSystem has run. */
    public void applyAppearance() {
        assignments.forEach((actorId, route) -> {
            if (roster.isLive(actorId)) applyAppearance(actorId, sample(route, elapsedSeconds));
        });
    }

    public static AmbientTaskPose sample(AmbientTaskRoute route, float elapsedSeconds) {
        float loopSeconds = loopSeconds(route);
        float cursor = positiveModulo(elapsedSeconds + route.phaseOffsetSeconds(), loopSeconds);
        float walked = 0f;
        for (int index = 0; index < route.stops().size(); index++) {
            AmbientTaskRoute.Stop stop = route.stops().get(index);
            if (cursor < stop.dwellSeconds()) {
                float actionPhase = cursor / stop.dwellSeconds();
                float facing = facing(stop.worldX(), stop.worldY(), stop.focusX(), stop.focusY());
                float headLook = headLook(stop.activity(), elapsedSeconds, route.phaseOffsetSeconds());
                return new AmbientTaskPose(stop.worldX(), stop.worldY(), facing,
                        0f, actionPhase, stop.focusX(), stop.focusY(),
                        headLook, false, stop.activity());
            }
            cursor -= stop.dwellSeconds();
            AmbientTaskRoute.Stop next = route.stops().get((index + 1) % route.stops().size());
            float dx = next.worldX() - stop.worldX();
            float dy = next.worldY() - stop.worldY();
            float distance = length(dx, dy);
            float travelSeconds = distance / route.walkSpeedCellsPerSecond();
            if (cursor < travelSeconds) {
                float progress = travelSeconds > 0f ? cursor / travelSeconds : 1f;
                float gait = positiveModulo(walked + distance * progress, 1f);
                return new AmbientTaskPose(
                        lerp(stop.worldX(), next.worldX(), progress),
                        lerp(stop.worldY(), next.worldY(), progress),
                        facing(stop.worldX(), stop.worldY(), next.worldX(), next.worldY()),
                        gait, progress, next.worldX(), next.worldY(),
                        0f, true, AmbientActivity.WALKING);
            }
            cursor -= travelSeconds;
            walked += distance;
        }
        throw new IllegalStateException("ambient route has no sampleable segment");
    }

    private void applyPosition(long actorId, AmbientTaskPose pose) {
        world.setPos(actorId, pose.worldX(), pose.worldY());
    }

    private void applyAppearance(long actorId, AmbientTaskPose pose) {
        int authoredPose = LayeredAppearance.POSE_IDLE;
        float weaponPhase = 0f;
        int flags = pose.moving() ? LayeredAppearance.FLAG_MOVING : 0;
        if (pose.activity() == AmbientActivity.FIRING_PRIMARY) {
            float cycle = positiveModulo(pose.actionPhase() * 4f, 1f);
            if (cycle >= FIRE_BEGIN && cycle < FIRE_END) {
                authoredPose = LayeredAppearance.POSE_FIRING;
                weaponPhase = (cycle - FIRE_BEGIN) / (FIRE_END - FIRE_BEGIN);
                if (cycle < FIRE_BEGIN + 0.055f) flags |= LayeredAppearance.FLAG_MUZZLE_FLASH;
            } else {
                authoredPose = LayeredAppearance.POSE_AIMED;
                weaponPhase = cycle < FIRE_BEGIN ? cycle / FIRE_BEGIN : 1f;
            }
        }
        entities.setFloat(actorId, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_FACING_DEGREES, pose.facingDegrees());
        entities.setFloat(actorId, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_LOCOMOTION_PHASE, pose.locomotionPhase());
        entities.setFloat(actorId, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_WEAPON_PHASE, weaponPhase);
        entities.setFloat(actorId, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_HEAD_LOOK_DEGREES, pose.headLookDegrees());
        entities.setInt(actorId, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_WEAPON_POSE, authoredPose);
        entities.setInt(actorId, components.LAYERED_ANIMATION,
                BattleComponents.LAYERED_FLAGS, flags);
    }

    private boolean isThreatened(long actorId, AmbientTaskRoute route) {
        if (route.threatPolicy() == AmbientThreatPolicy.NONE || route.threatRadiusCells() <= 0f) {
            return false;
        }
        if (world.hasAiState(actorId) && world.fallbackTimer(actorId) > 0f) return true;
        float x = world.x(actorId);
        float y = world.y(actorId);
        Faction faction = roster.identity().faction(actorId);
        float radiusSq = route.threatRadiusCells() * route.threatRadiusCells();
        for (int i = 0; i < roster.liveCount(); i++) {
            long candidate = roster.get(i);
            if (candidate == actorId || !roster.identity().type(candidate).combatant) continue;
            if (route.threatPolicy() == AmbientThreatPolicy.HOSTILE_COMBATANT
                    && roster.identity().faction(candidate) == faction) continue;
            float dx = world.x(candidate) - x;
            float dy = world.y(candidate) - y;
            if (dx * dx + dy * dy <= radiusSq) return true;
        }
        return false;
    }

    private static float loopSeconds(AmbientTaskRoute route) {
        float total = 0f;
        for (int index = 0; index < route.stops().size(); index++) {
            AmbientTaskRoute.Stop stop = route.stops().get(index);
            AmbientTaskRoute.Stop next = route.stops().get((index + 1) % route.stops().size());
            total += stop.dwellSeconds()
                    + length(next.worldX() - stop.worldX(), next.worldY() - stop.worldY())
                    / route.walkSpeedCellsPerSecond();
        }
        return total;
    }

    private static float headLook(AmbientActivity activity, float elapsed, float offset) {
        if (activity != AmbientActivity.SOCIALIZING
                && activity != AmbientActivity.INSPECTING
                && activity != AmbientActivity.WORKING) return 0f;
        return (float) Math.sin((elapsed + offset) * 1.35f) * 11f;
    }

    private static float facing(float fromX, float fromY, float toX, float toY) {
        return LayeredAppearance.facingDegrees(
                Math.round((toX - fromX) * 100f),
                Math.round((toY - fromY) * 100f));
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float positiveModulo(float value, float divisor) {
        float result = value % divisor;
        return result < 0f ? result + divisor : result;
    }
}
