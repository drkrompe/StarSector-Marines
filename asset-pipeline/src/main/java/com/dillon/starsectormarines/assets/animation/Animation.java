package com.dillon.starsectormarines.assets.animation;

import lombok.Getter;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Represents an animation clip.
 */
public class Animation {
    @Getter
    private final String name;
    @Getter
    private final double duration;
    @Getter
    private final double ticksPerSecond;
    @Getter
    private final List<NodeAnimation> nodeAnimations;

    public Animation(String name, double duration, double ticksPerSecond, List<NodeAnimation> nodeAnimations) {
        this.name = name;
        this.duration = duration;
        this.ticksPerSecond = ticksPerSecond;
        this.nodeAnimations = nodeAnimations;
    }

    /**
     * Represents the animation tracks for a specific node (bone).
     */
    public static class NodeAnimation {
        @Getter
        private final String nodeName;
        @Getter
        private final List<KeyFrame<Vector3f>> positionKeys;
        @Getter
        private final List<KeyFrame<Quaternionf>> rotationKeys;
        @Getter
        private final List<KeyFrame<Vector3f>> scalingKeys;

        public NodeAnimation(String nodeName, List<KeyFrame<Vector3f>> positionKeys, List<KeyFrame<Quaternionf>> rotationKeys, List<KeyFrame<Vector3f>> scalingKeys) {
            this.nodeName = nodeName;
            this.positionKeys = positionKeys;
            this.rotationKeys = rotationKeys;
            this.scalingKeys = scalingKeys;
        }
    }

    /**
     * Represents a single keyframe value at a specific time.
     */
    public record KeyFrame<T>(double time, T value) {}
}
