package com.dillon.starsectormarines.assets.animation;

import lombok.Getter;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class Bone {
    @Getter
    private final int index;
    @Getter
    private final String name;
    @Getter
    private final Matrix4f offsetMatrix; // Inverse Bind Matrix
    @Getter
    private final Matrix4f localRestTransform; // Node's local transform from scene graph (parent-relative rest pose)
    @Getter
    private final List<Bone> children = new ArrayList<>();
    @Getter
    private Bone parent;

    public Bone(int index, String name, Matrix4f offsetMatrix, Matrix4f localRestTransform) {
        this.index = index;
        this.name = name;
        this.offsetMatrix = offsetMatrix;
        this.localRestTransform = localRestTransform;
    }

    public void addChild(Bone child) {
        children.add(child);
        child.parent = this;
    }
}
