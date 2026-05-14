package com.dillon.starsectormarines.assets.animation;

import lombok.Getter;

import java.util.List;

/**
 * Represents the skeletal hierarchy of a model.
 */
public class Skeleton {
    @Getter
    private final List<Bone> bones;
    @Getter
    private final Bone rootBone;

    public Skeleton(List<Bone> bones, Bone rootBone) {
        this.bones = bones;
        this.rootBone = rootBone;
    }
}
