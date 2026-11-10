package com.dillon.starsectormarines.ops.battleview;

import java.util.List;
import java.util.function.Function;

/**
 * The derelict security walker's parts, upright: its torso, a thigh (hip joint at the bottom, knee at the top) and a
 * shin (knee joint at the bottom, foot at the top) and the knee joint drawn where they meet, authored at {@link #PX_PER_CELL}. Cut by
 * {@code art-source/derelict-walker/export.py}; the shipped set is the placeholder from {@code placeholder.py} until
 * the owner picks the walker's look. Tri-Tachyon's light crawler ships the same four parts, cut by the same script from
 * its own master, under {@link #CRAWLER_ROOT}.
 */
public final class DerelictWalkerAssets {

    public static final String ROOT = "graphics/battle/derelict-walker/";
    /** Tri-Tachyon's light crawler's parts (`mech-lines.md`, high-tech light). */
    public static final String CRAWLER_ROOT = "graphics/battle/tt-crawler/";
    /** The parts' authored density: a part's pixels over this are its size in cells. */
    public static final float PX_PER_CELL = 128f;

    final LayeredSpriteCache torso;
    final LayeredSpriteCache thigh;
    final LayeredSpriteCache shin;
    final LayeredSpriteCache joint;

    private DerelictWalkerAssets(LayeredSpriteCache torso, LayeredSpriteCache thigh, LayeredSpriteCache shin,
                                 LayeredSpriteCache joint) {
        this.torso = torso;
        this.thigh = thigh;
        this.shin = shin;
        this.joint = joint;
    }

    /** Every sprite through loader, or null when any is missing. */
    public static DerelictWalkerAssets load(Function<String, LayeredSpriteCache> loader) {
        return load(ROOT, loader);
    }

    /** The four parts under {@code root} through loader, or null when any is missing. */
    public static DerelictWalkerAssets load(String root, Function<String, LayeredSpriteCache> loader) {
        LayeredSpriteCache torso = loader.apply(root + "torso.png");
        LayeredSpriteCache thigh = loader.apply(root + "thigh.png");
        LayeredSpriteCache shin = loader.apply(root + "shin.png");
        LayeredSpriteCache joint = loader.apply(root + "joint.png");
        if (torso == null || thigh == null || shin == null || joint == null) return null;
        return new DerelictWalkerAssets(torso, thigh, shin, joint);
    }

    /** Every image, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        return List.of(torso, thigh, shin, joint);
    }
}
