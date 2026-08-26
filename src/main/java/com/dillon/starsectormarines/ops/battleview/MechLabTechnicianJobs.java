package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

/** Deterministic workshop-job choreography for the Mech Lab's real engineer entities. */
final class MechLabTechnicianJobs {

    static final float WALK_SPEED_CELLS_PER_SECOND = 0.72f;

    private MechLabTechnicianJobs() { }

    static TechnicianPose sample(int technicianIndex, float elapsedSeconds) {
        MechLabSceneLayout.TechnicianJob job = MechLabSceneLayout.TECHNICIAN_JOBS.get(
                Math.floorMod(technicianIndex, MechLabSceneLayout.TECHNICIAN_JOBS.size()));
        float loopSeconds = loopSeconds(job);
        float cursor = positiveModulo(elapsedSeconds + job.phaseOffsetSeconds(), loopSeconds);
        float walked = 0f;
        for (int index = 0; index < job.stops().size(); index++) {
            MechLabSceneLayout.TechnicianStop stop = job.stops().get(index);
            if (cursor < stop.dwellSeconds()) {
                float facing = facing(stop.worldX(), stop.worldY(),
                        stop.focusX(), stop.focusY());
                float headLook = stop.activity() == TechnicianActivity.SORTING
                        ? (float) Math.sin((elapsedSeconds + technicianIndex) * 1.7f) * 12f
                        : 0f;
                return new TechnicianPose(stop.worldX(), stop.worldY(), facing,
                        0f, false, stop.activity(), stop.focusX(), stop.focusY(), headLook);
            }
            cursor -= stop.dwellSeconds();
            MechLabSceneLayout.TechnicianStop next = job.stops().get(
                    (index + 1) % job.stops().size());
            float dx = next.worldX() - stop.worldX();
            float dy = next.worldY() - stop.worldY();
            float distance = length(dx, dy);
            float travelSeconds = distance / WALK_SPEED_CELLS_PER_SECOND;
            if (cursor < travelSeconds) {
                float progress = travelSeconds > 0f ? cursor / travelSeconds : 1f;
                float gait = positiveModulo(walked + distance * progress, 1f);
                return new TechnicianPose(
                        lerp(stop.worldX(), next.worldX(), progress),
                        lerp(stop.worldY(), next.worldY(), progress),
                        facing(stop.worldX(), stop.worldY(), next.worldX(), next.worldY()),
                        gait, true, TechnicianActivity.WALKING,
                        next.worldX(), next.worldY(), 0f);
            }
            cursor -= travelSeconds;
            walked += distance;
        }
        throw new IllegalStateException("technician job has no sampleable segment");
    }

    static void apply(BattleSimulation simulation, float elapsedSeconds) {
        EntityWorld entities = simulation.getEntityWorld();
        BattleComponents components = simulation.getBattleComponents();
        int technicianIndex = 0;
        for (int index = 0, count = simulation.getRoster().liveCount(); index < count; index++) {
            long id = simulation.getRoster().get(index);
            if (simulation.identity().type(id) != UnitType.ENGINEER) continue;
            TechnicianPose pose = sample(technicianIndex++, elapsedSeconds);
            simulation.world().setPos(id, pose.worldX(), pose.worldY());
            entities.setFloat(id, components.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_FACING_DEGREES, pose.facingDegrees());
            entities.setFloat(id, components.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_LOCOMOTION_PHASE, pose.locomotionPhase());
            entities.setFloat(id, components.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_WEAPON_PHASE, 0f);
            entities.setFloat(id, components.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_HEAD_LOOK_DEGREES, pose.headLookDegrees());
            entities.setInt(id, components.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_WEAPON_POSE, LayeredAppearance.POSE_IDLE);
            entities.setInt(id, components.LAYERED_ANIMATION,
                    BattleComponents.LAYERED_FLAGS,
                    pose.moving() ? LayeredAppearance.FLAG_MOVING : 0);
        }
    }

    private static float loopSeconds(MechLabSceneLayout.TechnicianJob job) {
        float total = 0f;
        for (int index = 0; index < job.stops().size(); index++) {
            MechLabSceneLayout.TechnicianStop stop = job.stops().get(index);
            MechLabSceneLayout.TechnicianStop next = job.stops().get(
                    (index + 1) % job.stops().size());
            total += stop.dwellSeconds()
                    + length(next.worldX() - stop.worldX(), next.worldY() - stop.worldY())
                    / WALK_SPEED_CELLS_PER_SECOND;
        }
        return total;
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

    enum TechnicianActivity {
        WALKING,
        WELDING,
        SORTING,
        INSPECTING
    }

    record TechnicianPose(float worldX, float worldY, float facingDegrees,
                          float locomotionPhase, boolean moving,
                          TechnicianActivity activity,
                          float focusX, float focusY,
                          float headLookDegrees) { }
}
