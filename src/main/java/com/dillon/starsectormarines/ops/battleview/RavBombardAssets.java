package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBombardRig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Bombard's parts, exported by {@code art-source/rav-redesign/artillery/export_bombard_rig.py}: each leg,
 * the shell over the sac, the tail in slices from the root, the sling and the glob, all head-up; and the shell cut in two
 * down its seam, which a dead Bombard cracks open into.
 */
public final class RavBombardAssets {

    public static final String ROOT = "graphics/battle/rav-bombard/";

    /** Each leg, left then right, front to back, as {@link RavBombardRig#LEG_COL} lists them. */
    final LayeredSpriteCache[] legs = new LayeredSpriteCache[RavBombardRig.LEGS];
    final LayeredSpriteCache body;
    final LayeredSpriteCache sling;
    final LayeredSpriteCache glob;
    final LayeredSpriteCache shellL;
    final LayeredSpriteCache shellR;
    final LayeredSpriteCache[] tail = new LayeredSpriteCache[RavBombardRig.TAIL_ART_SLICES];

    private RavBombardAssets(LayeredSpriteCache body, LayeredSpriteCache sling, LayeredSpriteCache glob,
                             LayeredSpriteCache shellL, LayeredSpriteCache shellR) {
        this.body = body;
        this.sling = sling;
        this.glob = glob;
        this.shellL = shellL;
        this.shellR = shellR;
    }

    /** Every sprite through loader, or null when any is missing, so the Bombard keeps its stand-in art. */
    public static RavBombardAssets load(Function<String, LayeredSpriteCache> loader) {
        RavBombardAssets assets = new RavBombardAssets(loader.apply(ROOT + "body.png"),
                loader.apply(ROOT + "sling.png"), loader.apply(ROOT + "glob.png"), loader.apply(ROOT + "shell-l.png"),
                loader.apply(ROOT + "shell-r.png"));
        if (assets.body == null || assets.sling == null || assets.glob == null || assets.shellL == null
                || assets.shellR == null) {
            return null;
        }
        for (int i = 0; i < assets.legs.length; i++) {
            String side = i < RavBombardRig.LEGS_PER_SIDE ? "l" : "r";
            assets.legs[i] = loader.apply(ROOT + "leg-" + side + (i % RavBombardRig.LEGS_PER_SIDE) + ".png");
            if (assets.legs[i] == null) return null;
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
        for (LayeredSpriteCache leg : legs) all.add(leg);
        all.add(body);
        all.add(sling);
        all.add(glob);
        all.add(shellL);
        all.add(shellR);
        for (LayeredSpriteCache t : tail) all.add(t);
        return all;
    }
}
