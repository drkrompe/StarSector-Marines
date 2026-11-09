package com.dillon.starsectormarines.ops.battleview;

import java.util.List;
import java.util.function.Function;

/**
 * The derelict security walker's parts, head-up: its torso plate, a leg bone and a foot. Placeholders from
 * {@code art-source/derelict-walker/placeholder.py} until the owner picks its look.
 */
public final class DerelictWalkerAssets {

    public static final String ROOT = "graphics/battle/derelict-walker/";

    final LayeredSpriteCache torso;
    final LayeredSpriteCache leg;
    final LayeredSpriteCache foot;

    private DerelictWalkerAssets(LayeredSpriteCache torso, LayeredSpriteCache leg, LayeredSpriteCache foot) {
        this.torso = torso;
        this.leg = leg;
        this.foot = foot;
    }

    /** Every sprite through loader, or null when any is missing. */
    public static DerelictWalkerAssets load(Function<String, LayeredSpriteCache> loader) {
        LayeredSpriteCache torso = loader.apply(ROOT + "torso.png");
        LayeredSpriteCache leg = loader.apply(ROOT + "leg.png");
        LayeredSpriteCache foot = loader.apply(ROOT + "foot.png");
        if (torso == null || leg == null || foot == null) return null;
        return new DerelictWalkerAssets(torso, leg, foot);
    }

    /** Every image, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        return List.of(torso, leg, foot);
    }
}
