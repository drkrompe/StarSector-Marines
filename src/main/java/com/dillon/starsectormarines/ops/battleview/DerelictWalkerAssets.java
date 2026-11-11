package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.Arrays;
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
    /** The fallen hull's MG-18 stripper's own parts ({@code art-source/fallen-hull}). */
    public static final String STRIPPER_ROOT = "graphics/battle/derelict-stripper/";
    /** The fallen hull's MG-30 hauler's own parts ({@code art-source/fallen-hull}). */
    public static final String HAULER_ROOT = "graphics/battle/derelict-hauler/";
    /** Tri-Tachyon's light crawler's parts (`mech-lines.md`, high-tech light). */
    public static final String CRAWLER_ROOT = "graphics/battle/tt-crawler/";
    /** The parts' authored density: a part's pixels over this are its size in cells. */
    public static final float PX_PER_CELL = 128f;

    final LayeredSpriteCache torso;
    final LayeredSpriteCache thigh;
    final LayeredSpriteCache shin;
    final LayeredSpriteCache joint;
    /**
     * A salvage walker's work parts ({@code art-source/fallen-hull/work_parts.py}), null on any other walker: the
     * stripper's cutting arm's boom and saw, the hauler's bed load at each quarter. Each may be null.
     */
    LayeredSpriteCache arm;
    LayeredSpriteCache saw;
    /** A heap of the salvage the stripper cuts, drawn on the ground shrinking as it is cut. */
    LayeredSpriteCache pile;
    LayeredSpriteCache[] loads;

    /** The hauler's bed load stages ({@code load-1..4.png}), a quarter each. */
    static final int LOAD_STAGES = 4;

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
        DerelictWalkerAssets art = new DerelictWalkerAssets(torso, thigh, shin, joint);
        if (root.equals(STRIPPER_ROOT)) {
            art.arm = loader.apply(root + "arm.png");
            art.saw = loader.apply(root + "saw.png");
            art.pile = loader.apply(root + "pile.png");
        } else if (root.equals(HAULER_ROOT)) {
            // Its grab arms are the stripper's boom tube, jointed: crane arms, not a second pair of legs.
            art.arm = loader.apply(STRIPPER_ROOT + "arm.png");
            LayeredSpriteCache[] loads = new LayeredSpriteCache[LOAD_STAGES];
            boolean all = true;
            for (int k = 0; k < LOAD_STAGES; k++) {
                loads[k] = loader.apply(root + "load-" + (k + 1) + ".png");
                all &= loads[k] != null;
            }
            art.loads = all ? loads : null;
        }
        return art;
    }

    /** Every image, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> out = new ArrayList<>(List.of(torso, thigh, shin, joint));
        if (arm != null) out.add(arm);
        if (saw != null) out.add(saw);
        if (pile != null) out.add(pile);
        if (loads != null) out.addAll(Arrays.asList(loads));
        return out;
    }
}
