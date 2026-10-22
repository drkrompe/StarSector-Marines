package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.unit.SwarmStage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Rav Bloom Brute's rig sprites, exported by {@code art-source/rav-redesign/export_brute_rig.py}:
 * the approved hero cut into a body, a head, four hooves and two shanks, all head-up, and the body again in
 * the front and rear halves a dead Brute bursts into. Hooves are indexed front left, front right, hind left,
 * hind right; shanks {@code [front/hind][left/right]}. A whelp and an elder have their own recoloured
 * sets ({@link #forStage}), cut from the same hero so they pose on the same skeleton.
 */
public final class RavBruteAssets {

    public static final String ROOT = "graphics/battle/rav-brute/";
    public static final String WHELP_ROOT = "graphics/battle/rav-brute-whelp/";
    public static final String ELDER_ROOT = "graphics/battle/rav-brute-elder/";
    private static final String[] HOOVES = {"fl", "fr", "hl", "hr"};

    final LayeredSpriteCache body;
    final LayeredSpriteCache head;
    LayeredSpriteCache bodyFront, bodyRear;
    final LayeredSpriteCache[] hoof = new LayeredSpriteCache[4];
    final LayeredSpriteCache[][] shank = new LayeredSpriteCache[2][2];
    /** The stage sets, or null where one is missing and the adult's stands in. */
    private RavBruteAssets whelp, elder;

    private RavBruteAssets(LayeredSpriteCache body, LayeredSpriteCache head) {
        this.body = body;
        this.head = head;
    }

    /** Every sprite through loader, or null when any is missing, so the Brute keeps its old art. */
    public static RavBruteAssets load(Function<String, LayeredSpriteCache> loader) {
        RavBruteAssets assets = load(ROOT, loader);
        if (assets == null) return null;
        assets.whelp = load(WHELP_ROOT, loader);
        assets.elder = load(ELDER_ROOT, loader);
        return assets;
    }

    /** The set a body of {@code stage} is drawn with: its own, or the adult's where it has none. */
    public RavBruteAssets forStage(SwarmStage stage) {
        RavBruteAssets own = stage == SwarmStage.WHELP ? whelp : stage == SwarmStage.ELDER ? elder : null;
        return own != null ? own : this;
    }

    private static RavBruteAssets load(String root, Function<String, LayeredSpriteCache> loader) {
        RavBruteAssets assets = new RavBruteAssets(loader.apply(root + "body.png"), loader.apply(root + "head.png"));
        if (assets.body == null || assets.head == null) return null;
        assets.bodyFront = loader.apply(root + "body-front.png");
        assets.bodyRear = loader.apply(root + "body-rear.png");
        if (assets.bodyFront == null || assets.bodyRear == null) return null;
        for (int i = 0; i < 4; i++) {
            assets.hoof[i] = loader.apply(root + "hoof-" + HOOVES[i] + ".png");
            if (assets.hoof[i] == null) return null;
        }
        for (int part = 0; part < 2; part++) {
            for (int side = 0; side < 2; side++) {
                assets.shank[part][side] = loader.apply(root + "shank-" + (part == 0 ? "f" : "h") + "-"
                        + (side == 0 ? "l" : "r") + ".png");
                if (assets.shank[part][side] == null) return null;
            }
        }
        return assets;
    }

    /** Every image, the stage sets' too, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> all = new ArrayList<>();
        add(all);
        if (whelp != null) whelp.add(all);
        if (elder != null) elder.add(all);
        return all;
    }

    private void add(List<LayeredSpriteCache> all) {
        all.add(body);
        all.add(head);
        all.add(bodyFront);
        all.add(bodyRear);
        for (LayeredSpriteCache h : hoof) all.add(h);
        for (LayeredSpriteCache[] pair : shank) {
            all.add(pair[0]);
            all.add(pair[1]);
        }
    }
}
