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
        return resolve(layer, using, firing, phase, actorX, actorY, shoulderPx,
                facingDegrees, 1f, 1f);
    }

    /** Offsets and sprite dimensions inherit the selected attachment's local scale and bearing. */
    public static Placement resolve(EquipmentLayerDef layer, boolean using, boolean firing,
                                    float phase, float parentX, float parentY,
                                    float shoulderPx, float parentDegrees,
                                    float scaleX, float scaleY) {
        float t = firing ? 1f : using ? smoothstep(clamp01(phase)) : 0f;
        EquipmentLayerDef.State from = layer.carried();
        EquipmentLayerDef.State to = layer.using();
        float offsetX = lerp(from.offsetXShoulders(), to.offsetXShoulders(), t);
        float offsetY = lerp(from.offsetYShoulders(), to.offsetYShoulders(), t);
        float localAngle = lerp(from.angleDegrees(), to.angleDegrees(), t);
        EquipmentLayerDef.Occlusion occlusion = firing && layer.firingOcclusion() != null
                ? layer.firingOcclusion()
                : t >= 0.5f ? to.occlusion() : from.occlusion();

        float[] pivot = worldPoint(parentX, parentY, offsetX * scaleX, offsetY * scaleY,
                shoulderPx, parentDegrees);
        float width = layer.widthShoulders() * shoulderPx * scaleX;
        float height = layer.heightShoulders() * shoulderPx * scaleY;
        float angle = parentDegrees + localAngle;
        float centerX = (0.5f - layer.pivotX()) * width;
        float centerY = -(0.5f - layer.pivotY()) * height;
        float[] centerOffset = rotate(centerX, centerY, angle);
        if (firing && layer.recoilShoulders() > 0f) {
            float recoil = layer.recoilShoulders()
                    * (float) Math.sin(clamp01(phase) * Math.PI) * shoulderPx * scaleY;
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

    /** Braces the pod at the committed ballistic muzzle, then settles onto its carried joint. */
    public static Placement launch(Placement carried, float muzzleX, float muzzleY,
                                   float bearingDegrees, float phase, float shoulderPx,
                                   float sourceMuzzleX, float sourceMuzzleY) {
        float p = clamp01(phase);
        // Hold registration through the flash; return smoothly during the final half.
        float settle = smoothstep(clamp01((p - 0.5f) * 2f));
        float kick = 0.025f * shoulderPx * (float) Math.sin(p * Math.PI);
        float[] back = rotate((0.5f - sourceMuzzleX) * carried.width(),
                -carried.height() * (0.5f - sourceMuzzleY) - kick,
                bearingDegrees);
        float angleDelta = (carried.angleDegrees() - bearingDegrees + 540f) % 360f - 180f;
        return new Placement(lerp(muzzleX + back[0], carried.centerX(), settle),
                lerp(muzzleY + back[1], carried.centerY(), settle),
                carried.width(), carried.height(), bearingDegrees + angleDelta * settle,
                carried.occlusion());
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
