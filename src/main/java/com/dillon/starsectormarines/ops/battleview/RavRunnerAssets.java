package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Rav Bloom Runner's bone sprites, exported by {@code art-source/rav-redesign/export_runner_rig.py}.
 * Each runs from its root at the bottom to its end at the top with the bone's midpoint at the
 * sprite's centre. Limb bones come in left and right, the right mirrored: {@code [bone][side]}.
 */
public final class RavRunnerAssets {

    public static final String ROOT = "graphics/battle/rav-runner/";

    final LayeredSpriteCache trunk;
    final LayeredSpriteCache fore;
    final LayeredSpriteCache[][] front = new LayeredSpriteCache[2][2];
    final LayeredSpriteCache[][] hind = new LayeredSpriteCache[3][2];
    final LayeredSpriteCache[][] arm = new LayeredSpriteCache[3][2];

    private RavRunnerAssets(LayeredSpriteCache trunk, LayeredSpriteCache fore) {
        this.trunk = trunk;
        this.fore = fore;
    }

    /** Every sprite through loader, or null when any is missing, so the Runner keeps its old art. */
    public static RavRunnerAssets load(Function<String, LayeredSpriteCache> loader) {
        RavRunnerAssets assets = new RavRunnerAssets(loader.apply(ROOT + "trunk.png"),
                loader.apply(ROOT + "fore.png"));
        if (assets.trunk == null || assets.fore == null) return null;
        if (!fill(assets.front, "front", loader) || !fill(assets.hind, "hind", loader)
                || !fill(assets.arm, "arm", loader)) return null;
        return assets;
    }

    /** Every bone image, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> all = new ArrayList<>();
        all.add(trunk);
        all.add(fore);
        for (LayeredSpriteCache[][] bones : new LayeredSpriteCache[][][]{front, hind, arm}) {
            for (LayeredSpriteCache[] pair : bones) {
                all.add(pair[0]);
                all.add(pair[1]);
            }
        }
        return all;
    }

    private static boolean fill(LayeredSpriteCache[][] bones, String name,
                                Function<String, LayeredSpriteCache> loader) {
        for (int i = 0; i < bones.length; i++) {
            bones[i][0] = loader.apply(ROOT + name + "-" + i + "-l.png");
            bones[i][1] = loader.apply(ROOT + name + "-" + i + "-r.png");
            if (bones[i][0] == null || bones[i][1] == null) return false;
        }
        return true;
    }
}
