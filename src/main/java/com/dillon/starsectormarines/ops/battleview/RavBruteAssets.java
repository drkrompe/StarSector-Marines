package com.dillon.starsectormarines.ops.battleview;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Rav Bloom Brute's rig sprites, exported by {@code art-source/rav-redesign/export_brute_rig.py}:
 * the approved hero cut into a body, a head, four hooves and two shanks, all head-up, and the body again in
 * the front and rear halves a dead Brute bursts into. Hooves are indexed front left, front right, hind left,
 * hind right; shanks {@code [front/hind][left/right]}.
 */
public final class RavBruteAssets {

    public static final String ROOT = "graphics/battle/rav-brute/";
    private static final String[] HOOVES = {"fl", "fr", "hl", "hr"};

    final LayeredSpriteCache body;
    final LayeredSpriteCache head;
    LayeredSpriteCache bodyFront, bodyRear;
    final LayeredSpriteCache[] hoof = new LayeredSpriteCache[4];
    final LayeredSpriteCache[][] shank = new LayeredSpriteCache[2][2];

    private RavBruteAssets(LayeredSpriteCache body, LayeredSpriteCache head) {
        this.body = body;
        this.head = head;
    }

    /** Every sprite through loader, or null when any is missing, so the Brute keeps its old art. */
    public static RavBruteAssets load(Function<String, LayeredSpriteCache> loader) {
        RavBruteAssets assets = new RavBruteAssets(loader.apply(ROOT + "body.png"), loader.apply(ROOT + "head.png"));
        if (assets.body == null || assets.head == null) return null;
        assets.bodyFront = loader.apply(ROOT + "body-front.png");
        assets.bodyRear = loader.apply(ROOT + "body-rear.png");
        if (assets.bodyFront == null || assets.bodyRear == null) return null;
        for (int i = 0; i < 4; i++) {
            assets.hoof[i] = loader.apply(ROOT + "hoof-" + HOOVES[i] + ".png");
            if (assets.hoof[i] == null) return null;
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
        all.add(bodyFront);
        all.add(bodyRear);
        for (LayeredSpriteCache h : hoof) all.add(h);
        for (LayeredSpriteCache[] pair : shank) {
            all.add(pair[0]);
            all.add(pair[1]);
        }
        return all;
    }
}
