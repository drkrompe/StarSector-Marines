package com.dillon.starsectormarines.battle.ambient;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.MovementService;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.task.TaskPoint;
import com.dillon.starsectormarines.battle.task.TaskPointService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialUsePose;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Battle-owned ambient work assignment and deterministic choreography.
 *
 * <p>An active assignment temporarily owns an actor's movement and layered
 * appearance. The ordinary unit dispatcher skips that actor. A route's threat
 * policy may release the assignment before the dispatch, after which the
 * actor's existing role immediately resumes normal battle behavior. Embedded
 * scenes may use {@link #seek(float)} for a pure pose or advance their bounded
 * simulation when an authored task has physical actions. Live advancement owns
 * destinations, never positions: task-point claims select an exclusive place,
 * then ordinary navigation, movement, and separation move the actor there.
 * The standalone battle calls {@link #advance(float)} and
 * {@link #applyAppearance()} from its normal tick pipeline.</p>
 */
public final class AmbientTaskService {

    private static final float FIRE_BEGIN = 0.58f;
    private static final float FIRE_END = 0.72f;

    private final UnitRosterService roster;
    private final World world;
    private final EntityWorld entities;
    private final BattleComponents components;
    private final NavigationService navigation;
    private final MovementService movement;
    private final TaskPointService taskPoints;
    private final Map<Long, AmbientTaskRoute> assignments = new ConcurrentHashMap<>();
    private final Map<Long, Long> liveFireTargets = new ConcurrentHashMap<>();
    private final Map<Long, Boolean> primaryFireWindows = new ConcurrentHashMap<>();
    private final Map<Long, AmbientTaskPose> livePoses = new ConcurrentHashMap<>();
    private AmbientLiveFireSink liveFireSink = AmbientLiveFireSink.NONE;
    private float elapsedSeconds;

    public AmbientTaskService(
            UnitRosterService roster,
            NavigationService navigation,
            TaskPointService taskPoints) {
        this.roster = roster;
        this.world = roster.world();
        this.entities = roster.entityWorld();
        this.components = roster.components();
        this.navigation = navigation;
        this.movement = roster.movement();
        this.taskPoints = taskPoints;
    }

    public void assign(long actorId, AmbientTaskRoute route) {
        assignInternal(actorId, route, 0L);
    }

    /**
     * Assigns an ambient route whose primary-fire beats are real simulation
     * actions against a simulation-owned target. The route remains generic;
     * the battle host chooses the target and the owning simulation supplies
     * the firing implementation.
     */
    public void assignLiveFire(long actorId, AmbientTaskRoute route, long targetId) {
        if (!roster.isLive(targetId)) {
            throw new IllegalArgumentException("ambient live-fire target must be live");
        }
        assignInternal(actorId, route, targetId);
    }

    /** Installs the battle-owned bridge to its ordinary primary firing service. */
    public void setLiveFireSink(AmbientLiveFireSink sink) {
        liveFireSink = sink != null ? sink : AmbientLiveFireSink.NONE;
    }

    private void assignInternal(long actorId, AmbientTaskRoute route, long targetId) {
        if (route == null) throw new IllegalArgumentException("ambient route is required");
        if (!roster.isLive(actorId)) throw new IllegalArgumentException("ambient actor must be live");
        if (!world.hasMovement(actorId)) throw new IllegalArgumentException("ambient actor must be mobile");
        if (!world.hasLayeredAppearance(actorId)) {
            throw new IllegalArgumentException("ambient actor requires layered appearance");
        }
        navigation.clearPath(actorId);
        taskPoints.release(actorId);
        assignments.put(actorId, route);
        if (targetId != 0L) liveFireTargets.put(actorId, targetId);
        else liveFireTargets.remove(actorId);
        AmbientTaskPose pose = stationaryPoseAtCurrentPosition(actorId, sample(route, elapsedSeconds));
        primaryFireWindows.put(actorId, isPrimaryFireWindow(actorId, pose));
        livePoses.put(actorId, pose);
        applyAppearance(actorId, pose);
    }

    public void release(long actorId) {
        assignments.remove(actorId);
        liveFireTargets.remove(actorId);
        primaryFireWindows.remove(actorId);
        livePoses.remove(actorId);
        taskPoints.release(actorId);
        if (roster.isLive(actorId) && world.hasMovement(actorId)) navigation.clearPath(actorId);
    }

    public boolean isControlling(long actorId) {
        return assignments.containsKey(actorId);
    }

    public int assignmentCount() {
        return assignments.size();
    }

    /** Advances live-battle time through ordinary navigation and movement. */
    public void advance(float dt) {
        if (!Float.isFinite(dt)) throw new IllegalArgumentException("ambient dt must be finite");
        elapsedSeconds += Math.max(0f, dt);
        for (int index = 0; index < roster.liveCount(); index++) {
            long actorId = roster.get(index);
            AmbientTaskRoute route = assignments.get(actorId);
            if (route == null) continue;
            if (!roster.isLive(actorId) || isThreatened(actorId, route)) {
                release(actorId);
                continue;
            }
            RouteSample authored = sampleState(route, elapsedSeconds);
            AmbientTaskPose pose = advanceTowardDestination(actorId, route, authored, dt);
            livePoses.put(actorId, pose);
            boolean firing = isPrimaryFireWindow(actorId, pose);
            boolean wasFiring = primaryFireWindows.put(actorId, firing) == Boolean.TRUE;
            Long targetId = liveFireTargets.get(actorId);
            if (firing && !wasFiring && targetId != null && roster.isLive(targetId)) {
                liveFireSink.firePrimary(actorId, targetId);
            }
        }
    }

    /**
     * Exact-time pose sampling for bounded scenes which deliberately do not
     * tick combat. This is a presentation-only teleport; any scene that needs
     * physical interaction must use {@link #advance(float)}.
     */
    public void seek(float elapsedSeconds) {
        if (!Float.isFinite(elapsedSeconds)) {
            throw new IllegalArgumentException("ambient seek time must be finite");
        }
        this.elapsedSeconds = Math.max(0f, elapsedSeconds);
        for (int index = 0; index < roster.liveCount(); index++) {
            long actorId = roster.get(index);
            AmbientTaskRoute route = assignments.get(actorId);
            if (route == null) continue;
            if (!roster.isLive(actorId)) {
                release(actorId);
                continue;
            }
            AmbientTaskPose pose = sample(route, this.elapsedSeconds);
            primaryFireWindows.put(actorId, isPrimaryFireWindow(actorId, pose));
            applyPosition(actorId, pose);
            livePoses.put(actorId, pose);
            applyAppearance(actorId, pose);
        }
    }

    /**
     * What an assigned actor is doing right now, or null if they have none.
     *
     * <p>For a host that draws something extra over the top of the ordinary
     * unit — welding sparks at the point a technician is working. That overlay
     * has to follow the actor who is actually working rather than a script laid
     * over the scene, or it lights up where nobody is standing.
     */
    public AmbientTaskPose pose(long actorId) {
        return livePoses.get(actorId);
    }

    /** Actors currently assigned a route, in roster order. */
    public List<Long> assigned() {
        List<Long> working = new ArrayList<>();
        for (int index = 0; index < roster.liveCount(); index++) {
            long actorId = roster.get(index);
            if (assignments.containsKey(actorId)) working.add(actorId);
        }
        return working;
    }

    /** Reasserts task poses after the ordinary battle FacingSystem has run. */
    public void applyAppearance() {
        for (int index = 0; index < roster.liveCount(); index++) {
            long actorId = roster.get(index);
            if (!assignments.containsKey(actorId) || !roster.isLive(actorId)) continue;
            AmbientTaskPose pose = livePoses.get(actorId);
            if (pose != null) applyAppearance(actorId, pose);
        }
    }

    public static AmbientTaskPose sample(AmbientTaskRoute route, float elapsedSeconds) {
        return sampleState(route, elapsedSeconds).pose();
    }

    /**
     * The stop somebody joining this route at this moment should be put down at.
     *
     * <p>Not where {@link #sample} says they are. A watch is deliberately spread
     * across its loop, so at any given instant most of it is between jobs, and
     * the sampler draws that as the straight line from one stop to the next.
     * That is right for a presentation teleport and wrong for putting somebody
     * into the world: on a generated map the line crosses bulkheads, and a
     * seeded sweep of ship decks found one shift in seventy standing inside one.
     *
     * <p>Somebody mid-transit is placed at the job they are heading for rather
     * than the one they left, so the very first tick finds them arrived instead
     * of halfway along a leg they never walked.
     */
    public static AmbientTaskRoute.Stop standingPlace(AmbientTaskRoute route,
                                                      float elapsedSeconds) {
        return route.stops().get(sampleState(route, elapsedSeconds).destinationIndex());
    }

    /**
     * Put every assigned actor down at a job and pose them there.
     *
     * <p>What a host calls once it has spawned a watch, in place of seeking to
     * time zero. {@link #seek} is a presentation teleport that bypasses
     * collision by design, which is exactly what setup must not do — the actors
     * it places stay where it puts them and are then physically simulated.
     */
    public void settle() {
        for (int index = 0; index < roster.liveCount(); index++) {
            long actorId = roster.get(index);
            AmbientTaskRoute route = assignments.get(actorId);
            if (route == null) continue;
            if (!roster.isLive(actorId)) {
                release(actorId);
                continue;
            }
            AmbientTaskPose pose = working(standingPlace(route, elapsedSeconds), 0f, 0f);
            primaryFireWindows.put(actorId, isPrimaryFireWindow(actorId, pose));
            applyPosition(actorId, pose);
            livePoses.put(actorId, pose);
            applyAppearance(actorId, pose);
        }
    }

    /** Somebody at a stop, doing what the stop is for and facing what it faces. */
    private static AmbientTaskPose working(AmbientTaskRoute.Stop stop,
                                           float actionPhase, float headLook) {
        return new AmbientTaskPose(stop.worldX(), stop.worldY(),
                facing(stop.worldX(), stop.worldY(), stop.focusX(), stop.focusY()),
                0f, actionPhase, stop.focusX(), stop.focusY(),
                headLook, false, stop.activity());
    }

    private static RouteSample sampleState(AmbientTaskRoute route, float elapsedSeconds) {
        float loopSeconds = loopSeconds(route);
        float cursor = positiveModulo(elapsedSeconds + route.phaseOffsetSeconds(), loopSeconds);
        float walked = 0f;
        for (int index = 0; index < route.stops().size(); index++) {
            AmbientTaskRoute.Stop stop = route.stops().get(index);
            if (cursor < stop.dwellSeconds()) {
                float actionPhase = cursor / stop.dwellSeconds();
                float headLook = headLook(stop.activity(), elapsedSeconds, route.phaseOffsetSeconds());
                return new RouteSample(working(stop, actionPhase, headLook), index, true);
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
                return new RouteSample(new AmbientTaskPose(
                        lerp(stop.worldX(), next.worldX(), progress),
                        lerp(stop.worldY(), next.worldY(), progress),
                        facing(stop.worldX(), stop.worldY(), next.worldX(), next.worldY()),
                        gait, progress, next.worldX(), next.worldY(),
                        0f, true, AmbientActivity.WALKING),
                        (index + 1) % route.stops().size(), false);
            }
            cursor -= travelSeconds;
            walked += distance;
        }
        throw new IllegalStateException("ambient route has no sampleable segment");
    }

    private AmbientTaskPose advanceTowardDestination(
            long actorId, AmbientTaskRoute route, RouteSample authored, float dt) {
        AmbientTaskRoute.Stop stop = route.stops().get(authored.destinationIndex());
        TaskPoint point = null;
        if (stop.pointGroup() != null) {
            point = taskPoints.claimNearest(actorId, stop.pointGroup(), world.x(actorId), world.y(actorId));
            if (point == null) {
                if (!Paths.isEmpty(world.path(actorId))) navigation.clearPath(actorId);
                return stationaryPoseAtCurrentPosition(actorId, authored.pose());
            }
        } else {
            taskPoints.release(actorId);
        }

        float destinationX = point != null ? point.worldX() : stop.worldX();
        float destinationY = point != null ? point.worldY() : stop.worldY();
        float focusX = point != null ? point.focusX() : stop.focusX();
        float focusY = point != null ? point.focusY() : stop.focusY();
        int destinationCellX = (int) Math.floor(destinationX);
        int destinationCellY = (int) Math.floor(destinationY);
        if (!navigation.getGrid().inBounds(destinationCellX, destinationCellY)
                || !navigation.getGrid().isWalkable(destinationCellX, destinationCellY)) {
            if (!Paths.isEmpty(world.path(actorId))) navigation.clearPath(actorId);
            return stationaryPoseAtCurrentPosition(actorId, authored.pose());
        }

        boolean arrived = movement.atCell(actorId, destinationCellX, destinationCellY)
                && movement.settled(actorId);
        int[] path = world.path(actorId);
        boolean wrongDestination = Paths.destX(path) != destinationCellX
                || Paths.destY(path) != destinationCellY;
        if (!arrived && (wrongDestination || movement.mayRepath(actorId))) {
            int[] replacement = navigation.findPath(
                    world.cellX(actorId), world.cellY(actorId),
                    destinationCellX, destinationCellY);
            if (!Paths.isEmpty(replacement)) navigation.setPath(actorId, replacement);
            else if (!Paths.isEmpty(path)) navigation.clearPath(actorId);
        }
        movement.advanceAlongPath(world, actorId, dt);
        arrived = movement.atCell(actorId, destinationCellX, destinationCellY)
                && movement.settled(actorId);

        float velocityX = movement.velX(actorId);
        float velocityY = movement.velY(actorId);
        boolean movingNow = velocityX * velocityX + velocityY * velocityY > 1e-5f;
        float facing = movingNow
                ? facing(0f, 0f, velocityX, velocityY)
                : facing(world.x(actorId), world.y(actorId), focusX, focusY);
        boolean performing = arrived && authored.dwelling();
        AmbientTaskPose sampled = authored.pose();
        return new AmbientTaskPose(
                world.x(actorId), world.y(actorId), facing,
                sampled.locomotionPhase(), performing ? sampled.actionPhase() : 0f,
                focusX, focusY, performing ? sampled.headLookDegrees() : 0f,
                movingNow, performing ? stop.activity()
                        : movingNow ? AmbientActivity.WALKING : AmbientActivity.IDLE);
    }

    private AmbientTaskPose stationaryPoseAtCurrentPosition(long actorId, AmbientTaskPose authored) {
        return new AmbientTaskPose(
                world.x(actorId), world.y(actorId), authored.facingDegrees(),
                0f, 0f, authored.focusX(), authored.focusY(),
                0f, false, AmbientActivity.IDLE);
    }

    private void applyPosition(long actorId, AmbientTaskPose pose) {
        world.setPos(actorId, pose.worldX(), pose.worldY());
    }

    private void applyAppearance(long actorId, AmbientTaskPose pose) {
        int authoredPose = LayeredAppearance.POSE_IDLE;
        float weaponPhase = 0f;
        int flags = pose.moving() ? LayeredAppearance.FLAG_MOVING : 0;
        boolean practiceEquipment = pose.activity() == AmbientActivity.PRACTICING_EQUIPMENT;
        SpecialEquipmentDef carriedSpecial = practiceEquipment && world.hasSecondaryWeapon(actorId)
                ? world.specialEquipment(actorId) : null;
        boolean usingSpecial = carriedSpecial != null && pose.actionPhase() >= 0.5f;
        if (usingSpecial) {
            float cycle = positiveModulo((pose.actionPhase() - 0.5f) * 2f, 1f);
            SpecialUsePose usePose = carriedSpecial.presentation().usePose();
            boolean direct = usePose == SpecialUsePose.SHOULDER_LAUNCHER
                    || usePose == SpecialUsePose.BRACED_RIFLE;
            boolean fired = direct && cycle >= FIRE_BEGIN && cycle < FIRE_END;
            authoredPose = switch (usePose) {
                case THROW -> LayeredAppearance.POSE_SMOKE_THROW;
                case PLANT -> LayeredAppearance.POSE_SATCHEL_PLANT;
                case BRACED_RIFLE -> fired ? LayeredAppearance.POSE_AMR_FIRE
                        : LayeredAppearance.POSE_AMR_AIM;
                case SHOULDER_LAUNCHER -> fired ? LayeredAppearance.POSE_ROCKET_FIRE
                        : LayeredAppearance.POSE_ROCKET_AIM;
            };
            weaponPhase = fired
                    ? (cycle - FIRE_BEGIN) / (FIRE_END - FIRE_BEGIN)
                    : direct ? cycle < FIRE_BEGIN ? cycle / FIRE_BEGIN : 1f : cycle;
            if (fired && usePose == SpecialUsePose.SHOULDER_LAUNCHER) {
                flags |= LayeredAppearance.FLAG_WEAPON_OVER_SHOULDER;
            }
            if (fired && cycle < FIRE_BEGIN + 0.055f) {
                flags |= LayeredAppearance.FLAG_MUZZLE_FLASH;
            }
        } else if (pose.activity() == AmbientActivity.FIRING_PRIMARY || practiceEquipment) {
            float cycle = primaryCycle(carriedSpecial != null, pose);
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

    private boolean isPrimaryFireWindow(long actorId, AmbientTaskPose pose) {
        if (pose.activity() != AmbientActivity.FIRING_PRIMARY
                && pose.activity() != AmbientActivity.PRACTICING_EQUIPMENT) {
            return false;
        }
        boolean carriesSpecial = world.hasSecondaryWeapon(actorId);
        if (pose.activity() == AmbientActivity.PRACTICING_EQUIPMENT
                && carriesSpecial && pose.actionPhase() >= 0.5f) {
            return false;
        }
        float cycle = primaryCycle(carriesSpecial, pose);
        return cycle >= FIRE_BEGIN && cycle < FIRE_END;
    }

    private static float primaryCycle(boolean carriesSpecial, AmbientTaskPose pose) {
        float repeats = carriesSpecial
                && pose.activity() == AmbientActivity.PRACTICING_EQUIPMENT ? 8f : 4f;
        return positiveModulo(pose.actionPhase() * repeats, 1f);
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

    private record RouteSample(AmbientTaskPose pose, int destinationIndex, boolean dwelling) { }
}
