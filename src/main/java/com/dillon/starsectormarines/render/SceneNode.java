package com.dillon.starsectormarines.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Hierarchical scene node. Local transform is translation + euler rotation (radians, XYZ
 * applied as Rz*Ry*Rx) + scale. World transform composes by walking parents.
 * An optional {@link Drawable} renders at this node's world transform; children render
 * recursively beneath it.
 */
public class SceneNode {

    public final float[] translation = new float[3];
    public final float[] rotation    = new float[3]; // euler XYZ radians
    public final float[] scale       = {1f, 1f, 1f};

    public Drawable drawable;

    private final List<SceneNode> children = new ArrayList<>();

    public SceneNode addChild(SceneNode child) {
        children.add(child);
        return child;
    }

    public List<SceneNode> getChildren() {
        return children;
    }

    /** Build T * Rz * Ry * Rx * S. */
    public float[] getLocalMatrix() {
        float[] t  = Mat4.translation(translation[0], translation[1], translation[2]);
        float[] rx = Mat4.rotationX(rotation[0]);
        float[] ry = Mat4.rotationY(rotation[1]);
        float[] rz = Mat4.rotationZ(rotation[2]);
        float[] s  = Mat4.scaling(scale[0], scale[1], scale[2]);
        return Mat4.mul(t, Mat4.mul(rz, Mat4.mul(ry, Mat4.mul(rx, s))));
    }
}
