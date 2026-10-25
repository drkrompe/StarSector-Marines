package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavRunnerRig;
import com.dillon.starsectormarines.battle.unit.SwarmStage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The Rav Bloom Runner's bone sprites, exported by {@code art-source/rav-redesign/export_runner_rig.py}.
 * Each runs from its root at the bottom to its end at the top with the bone's midpoint at the
 * sprite's centre. Limb bones come in left and right, the right mirrored: {@code [bone][side]}.
 * A whelp and an elder have their own recoloured sets ({@link #forStage}) beside the adult's, cut
 * from the same bones so they pose on the same skeleton; the ichor is the adult's.
 */
public final class RavRunnerAssets {

    public static final String ROOT = "graphics/battle/rav-runner/";
    /** The whelp's and the elder's sets, by the same export. */
    public static final String WHELP_ROOT = "graphics/battle/rav-runner-whelp/";
    public static final String ELDER_ROOT = "graphics/battle/rav-runner-elder/";
    /**
     * The skitterer's own bones (`colony-breach-nouns.md`, Skitterer), cut to the Runner's registration so they pose on
     * its skeleton: trunk, head and legs, no spike arms. By {@code art-source/rav-redesign/worker/export_worker_rig.py}.
     */
    public static final String WORKER_ROOT = "graphics/battle/rav-worker/";

    /** The trunk in two halves that meet at mid-spine, so it bends through a turn. */
    final LayeredSpriteCache trunkFront;
    final LayeredSpriteCache trunkRear;
    final LayeredSpriteCache fore;
    final LayeredSpriteCache[][] front = new LayeredSpriteCache[2][2];
    final LayeredSpriteCache[][] hind = new LayeredSpriteCache[3][2];
    final LayeredSpriteCache[][] arm = new LayeredSpriteCache[3][2];
    /** Ichor a burst Runner leaves, by {@code art-source/rav-redesign/export_ichor.py}. */
    final LayeredSpriteCache[] splat = new LayeredSpriteCache[4];
    LayeredSpriteCache drop;
    /** What a skitterer carries in its mandibles: feed for the spire, pulp off a tree. Null where missing. */
    LayeredSpriteCache loadFeed, loadPulp;
    /** The stage sets, or null where one is missing and the adult's stands in. */
    private RavRunnerAssets whelp, elder;
    /** The skitterer's set, or null where it is missing and the Runner's stands in. */
    private RavRunnerAssets worker;

    private RavRunnerAssets(LayeredSpriteCache trunkFront, LayeredSpriteCache trunkRear,
                            LayeredSpriteCache fore) {
        this.trunkFront = trunkFront;
        this.trunkRear = trunkRear;
        this.fore = fore;
    }

    /** Every sprite through loader, or null when any is missing, so the Runner keeps its old art. */
    public static RavRunnerAssets load(Function<String, LayeredSpriteCache> loader) {
        RavRunnerAssets assets = bones(ROOT, loader);
        if (assets == null) return null;
        for (int i = 0; i < assets.splat.length; i++) {
            assets.splat[i] = loader.apply(ROOT + "ichor-splat-" + i + ".png");
            if (assets.splat[i] == null) return null;
        }
        assets.drop = loader.apply(ROOT + "ichor-drop.png");
        if (assets.drop == null) return null;
        assets.whelp = bones(WHELP_ROOT, loader);
        assets.elder = bones(ELDER_ROOT, loader);
        assets.worker = armless(WORKER_ROOT, loader);
        for (RavRunnerAssets stage : new RavRunnerAssets[]{assets.whelp, assets.elder, assets.worker}) {
            if (stage == null) continue;
            System.arraycopy(assets.splat, 0, stage.splat, 0, assets.splat.length);
            stage.drop = assets.drop;
        }
        return assets;
    }

    /** The set a body of {@code stage} is drawn with: its own, or the adult's where it has none. */
    public RavRunnerAssets forStage(SwarmStage stage) {
        RavRunnerAssets own = stage == SwarmStage.WHELP ? whelp : stage == SwarmStage.ELDER ? elder : null;
        return own != null ? own : this;
    }

    /** The set {@code rig} is drawn with: a skitterer's own, else its stage's. */
    public RavRunnerAssets forRig(RavRunnerRig rig) {
        if (rig.worker() && worker != null) return worker;
        return forStage(rig.stage());
    }

    /** A set with no spike arms, the skitterer's; null when any bone is missing. */
    private static RavRunnerAssets armless(String root, Function<String, LayeredSpriteCache> loader) {
        RavRunnerAssets assets = new RavRunnerAssets(loader.apply(root + "trunk-front.png"),
                loader.apply(root + "trunk-rear.png"), loader.apply(root + "fore.png"));
        if (assets.trunkFront == null || assets.trunkRear == null || assets.fore == null) return null;
        if (!fill(assets.front, root + "front", loader) || !fill(assets.hind, root + "hind", loader)) return null;
        assets.loadFeed = loader.apply(root + "load-feed.png");
        assets.loadPulp = loader.apply(root + "load-pulp.png");
        return assets;
    }

    private static RavRunnerAssets bones(String root, Function<String, LayeredSpriteCache> loader) {
        RavRunnerAssets assets = new RavRunnerAssets(loader.apply(root + "trunk-front.png"),
                loader.apply(root + "trunk-rear.png"), loader.apply(root + "fore.png"));
        if (assets.trunkFront == null || assets.trunkRear == null || assets.fore == null) return null;
        if (!fill(assets.front, root + "front", loader) || !fill(assets.hind, root + "hind", loader)
                || !fill(assets.arm, root + "arm", loader)) return null;
        return assets;
    }

    /** Every bone image, the stage sets' too, for the unit atlas. */
    public List<LayeredSpriteCache> layers() {
        List<LayeredSpriteCache> all = new ArrayList<>();
        addBones(all);
        all.addAll(List.of(splat));
        all.add(drop);
        if (whelp != null) whelp.addBones(all);
        if (elder != null) elder.addBones(all);
        if (worker != null) worker.addBones(all);
        return all;
    }

    private void addBones(List<LayeredSpriteCache> all) {
        all.add(trunkFront);
        all.add(trunkRear);
        all.add(fore);
        if (loadFeed != null) all.add(loadFeed);
        if (loadPulp != null) all.add(loadPulp);
        for (LayeredSpriteCache[][] bones : new LayeredSpriteCache[][][]{front, hind, arm}) {
            for (LayeredSpriteCache[] pair : bones) {
                if (pair[0] == null) continue;
                all.add(pair[0]);
                all.add(pair[1]);
            }
        }
    }

    private static boolean fill(LayeredSpriteCache[][] bones, String name,
                                Function<String, LayeredSpriteCache> loader) {
        for (int i = 0; i < bones.length; i++) {
            bones[i][0] = loader.apply(name + "-" + i + "-l.png");
            bones[i][1] = loader.apply(name + "-" + i + "-r.png");
            if (bones[i][0] == null || bones[i][1] == null) return false;
        }
        return true;
    }
}
