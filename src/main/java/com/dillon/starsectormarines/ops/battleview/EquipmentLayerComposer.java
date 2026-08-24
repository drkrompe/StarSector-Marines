package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.EquipmentLayerDef;

/** Shared actor-local transform used by live battle and equipment preview scenes. */
public final class EquipmentLayerComposer {

    private EquipmentLayerComposer() {}

    public static Placement resolve(EquipmentLayerDef layer, boolean using, float phase,
                                    float actorX, float actorY, float shoulderPx,
                                    float facingDegrees) {
        return resolve(layer, using, false, phase, actorX, actorY, shoulderPx,
                facingDegrees);
    }

    public static Placement resolve(EquipmentLayerDef layer, boolean using, boolean firing,
                                    float phase, float actorX, float actorY,
                                    float shoulderPx, float facingDegrees) {
        float t = firing ? 1f : using ? smoothstep(clamp01(phase)) : 0f;
        EquipmentLayerDef.State from = layer.carried();
        EquipmentLayerDef.State to = layer.using();
        float offsetX = lerp(from.offsetXShoulders(), to.offsetXShoulders(), t);
        float offsetY = lerp(from.offsetYShoulders(), to.offsetYShoulders(), t);
        float localAngle = lerp(from.angleDegrees(), to.angleDegrees(), t);
        EquipmentLayerDef.Occlusion occlusion = firing && layer.firingOcclusion() != null
                ? layer.firingOcclusion()
                : t >= 0.5f ? to.occlusion() : from.occlusion();

        float[] pivot = worldPoint(actorX, actorY, offsetX, offsetY,
                shoulderPx, facingDegrees);
        float width = layer.widthShoulders() * shoulderPx;
        float height = layer.heightShoulders() * shoulderPx;
        float angle = facingDegrees + localAngle;
        float centerX = (0.5f - layer.pivotX()) * width;
        float centerY = -(0.5f - layer.pivotY()) * height;
        float[] centerOffset = rotate(centerX, centerY, angle);
        if (firing && layer.recoilShoulders() > 0f) {
            float recoil = layer.recoilShoulders()
                    * (float) Math.sin(clamp01(phase) * Math.PI) * shoulderPx;
            float[] recoilOffset = rotate(0f, -recoil, angle);
            centerOffset[0] += recoilOffset[0];
            centerOffset[1] += recoilOffset[1];
        }
        return new Placement(pivot[0] + centerOffset[0], pivot[1] + centerOffset[1],
                width, height, angle, occlusion);
    }

    public record Placement(float centerX, float centerY, float width, float height,
                            float angleDegrees, EquipmentLayerDef.Occlusion occlusion) {
    }

    private static float[] worldPoint(float actorX, float actorY,
                                      float localXShoulders, float localYShoulders,
                                      float shoulderPx, float facingDegrees) {
        float[] offset = rotate(localXShoulders * shoulderPx,
                localYShoulders * shoulderPx, facingDegrees);
        return new float[]{actorX + offset[0], actorY + offset[1]};
    }

    private static float[] rotate(float x, float y, float degrees) {
        double radians = Math.toRadians(degrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        return new float[]{x * cos - y * sin, x * sin + y * cos};
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
}
