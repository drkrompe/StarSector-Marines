package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Matriarch's rig sprites, exported by {@code art-source/rav-redesign/export_matriarch_rig.py}: the
 * approved hero cut into a body, a head and its roaring gape, two scythes, four feet and the shanks, all
 * head-up. Feet are indexed front left, front right, hind left, hind right; scythes left, right; shanks
 * {@code [front/hind][left/right]}.
 */
public final class RavMatriarchAssets {

    public static final String ROOT = "graphics/battle/rav-matriarch/";
    private static final String[] FEET = {"fl", "fr", "hl", "hr"};

    final LayeredSpriteCache body;
    final LayeredSpriteCache head;
    final LayeredSpriteCache gape;
    final LayeredSpriteCache[] scythe = new LayeredSpriteCache[2];
    final LayeredSpriteCache[] foot = new LayeredSpriteCache[4];
    final LayeredSpriteCache[][] shank = new LayeredSpriteCache[2][2];

    private RavMatriarchAssets(LayeredSpriteCache body, LayeredSpriteCache head, LayeredSpriteCache gape) {
        this.body = body;
        this.head = head;
        this.gape = gape;
    }

    /** Every sprite through loader, or null when any is missing. */
    public static RavMatriarchAssets load(Function<String, LayeredSpriteCache> loader) {
        RavMatriarchAssets assets = new RavMatriarchAssets(loader.apply(ROOT + "body.png"),
                loader.apply(ROOT + "head.png"), loader.apply(ROOT + "gape.png"));
        if (assets.body == null || assets.head == null || assets.gape == null) return null;
        assets.scythe[0] = loader.apply(ROOT + "scythe-l.png");
        assets.scythe[1] = loader.apply(ROOT + "scythe-r.png");
        if (assets.scythe[0] == null || assets.scythe[1] == null) return null;
        for (int i = 0; i < 4; i++) {
            assets.foot[i] = loader.apply(ROOT + "foot-" + FEET[i] + ".png");
            if (assets.foot[i] == null) return null;
        }
        for (int part = 0; part < 2; part++) {
            for (int side = 0; side < 2; side++) {
                assets.shank[part][side] = loader.apply(ROOT + "shank-" + (part == 0 ? "f" : "h") + "-"
                        + (side == 0 ? "l" : "r") + ".png");
                if (assets.shank[part][side] == null) return null;
            }
        }
        return assets;
    }

    /** Every image, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> all = new ArrayList<>();
        all.add(body);
        all.add(head);
        all.add(gape);
        for (LayeredSpriteCache s : scythe) all.add(s);
        for (LayeredSpriteCache f : foot) all.add(f);
        for (LayeredSpriteCache[] pair : shank) {
            all.add(pair[0]);
            all.add(pair[1]);
        }
        return all;
    }
}
