package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBombardRig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Bombard's parts, exported by {@code art-source/rav-redesign/artillery/export_bombard_rig.py}: each side's legs,
 * the shell over the sac, the tail in slices from the root, the sling and the glob, all head-up. A dead Bombard bursts
 * into these same parts.
 */
public final class RavBombardAssets {

    public static final String ROOT = "graphics/battle/rav-bombard/";

    final LayeredSpriteCache legsL;
    final LayeredSpriteCache legsR;
    final LayeredSpriteCache body;
    final LayeredSpriteCache sling;
    final LayeredSpriteCache glob;
    final LayeredSpriteCache[] tail = new LayeredSpriteCache[RavBombardRig.TAIL_ART_SLICES];

    private RavBombardAssets(LayeredSpriteCache legsL, LayeredSpriteCache legsR, LayeredSpriteCache body,
                             LayeredSpriteCache sling, LayeredSpriteCache glob) {
        this.legsL = legsL;
        this.legsR = legsR;
        this.body = body;
        this.sling = sling;
        this.glob = glob;
    }

    /** Every sprite through loader, or null when any is missing, so the Bombard keeps its stand-in art. */
    public static RavBombardAssets load(Function<String, LayeredSpriteCache> loader) {
        RavBombardAssets assets = new RavBombardAssets(loader.apply(ROOT + "legs-l.png"),
                loader.apply(ROOT + "legs-r.png"), loader.apply(ROOT + "body.png"), loader.apply(ROOT + "sling.png"),
                loader.apply(ROOT + "glob.png"));
        if (assets.legsL == null || assets.legsR == null || assets.body == null || assets.sling == null
                || assets.glob == null) {
            return null;
        }
        for (int i = 0; i < assets.tail.length; i++) {
            assets.tail[i] = loader.apply(ROOT + "tail-" + i + ".png");
            if (assets.tail[i] == null) return null;
        }
        return assets;
    }

    /** Every image, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> all = new ArrayList<>();
        all.add(legsL);
        all.add(legsR);
        all.add(body);
        all.add(sling);
        all.add(glob);
        for (LayeredSpriteCache t : tail) all.add(t);
        return all;
    }
}
