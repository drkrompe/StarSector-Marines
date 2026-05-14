package com.dillon.starsectormarines.render;

/**
 * Look-at style camera. Independent of the scene-node tree for now — we'll re-attach
 * it as a SceneNode once we need camera-follows-node behavior.
 */
public class Camera {

    public final float[] eye    = {0f, 0f, 4f};
    public final float[] target = {0f, 0f, 0f};
    public final float[] up     = {0f, 1f, 0f};

    public float fovYDeg = 60f;
    public float aspect  = 1f;
    public float near    = 0.1f;
    public float far     = 50f;

    public float[] getProjection() {
        return Mat4.perspective(fovYDeg, aspect, near, far);
    }

    public float[] getView() {
        return Mat4.lookAt(
                eye[0],    eye[1],    eye[2],
                target[0], target[1], target[2],
                up[0],     up[1],     up[2]);
    }

    public float[] getViewProjection() {
        return Mat4.mul(getProjection(), getView());
    }
}
