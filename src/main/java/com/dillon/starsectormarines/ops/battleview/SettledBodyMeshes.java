package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.RavBruteRig;
import com.dillon.starsectormarines.battle.appearance.RavRunnerRig;
import com.dillon.starsectormarines.battle.appearance.RavSpitterRig;
import com.dillon.starsectormarines.render2d.QuadMesh;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The burst Rav dead that have stopped moving, each recorded once as a {@link QuadMesh} and drawn from it every
 * frame afterwards (`colony-breach-nouns.md`, Body pile cap).
 *
 * <p>A burst Runner is about thirty-three sprites — legs, trunk halves, head, spike arms, the pool and every splat
 * of ichor — each placed by trigonometry over its rig every frame. Once its burst has settled
 * ({@code RavBurst.settled}) none of that changes again: the pieces lie where they came down, the pool has
 * spread, the droplets have landed. So the composition is recorded once, in the body's own frame with the cell as
 * its unit, and every later frame draws it as one command, scaled by the frame's pixels per cell and the melt or
 * pile-cap shrink and faded by their alpha. Recorded through the same composer the live draw uses, the quads are
 * the same quads in the same order; only the arithmetic placing them is done once instead of every frame.
 *
 * <p>Keyed weakly on the rig, which lives exactly as long as its corpse, so a body taken off the field takes its
 * mesh with it and nothing here has to be told.
 *
 * <p>{@code -Dbattle.render.settledBodyMesh=false} is the control: every body is composed every frame, as before.
 */
final class SettledBodyMeshes {

    /** Control-run switch; see the class note. On by default. */
    static final String PROPERTY = "battle.render.settledBodyMesh";
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(PROPERTY, "true"));

    /**
     * Pixels per cell the body is recorded at. A power of two, so dividing it back out loses nothing, and large
     * enough that the composers' sub-pixel cut-offs (a bone shorter than 1e-4 px has no direction of its own, a
     * pool under half a pixel is left out) answer as they do at any framing a body is seen at.
     */
    private static final float RECORD_PPC = 64f;

    private final Map<Object, Entry> meshes = new WeakHashMap<>();

    private record Entry(Object art, QuadMesh mesh) { }

    /** The settled Runner's mesh, recorded on first asking; null while its burst is still moving. */
    QuadMesh runner(RavRunnerRig rig, RavRunnerAssets art) {
        if (!ENABLED || !rig.dead() || !rig.burst().settled()) return null;
        Entry entry = meshes.get(rig);
        if (entry != null && entry.art == art) return entry.mesh;
        QuadMesh mesh = new QuadMesh();
        RavRunnerComposer.emit(recorder(mesh), art, rig, 0f, 0f, RECORD_PPC, 1f, 1f);
        meshes.put(rig, new Entry(art, mesh));
        return mesh;
    }

    /** The settled Brute's mesh, recorded on first asking; null while its burst is still moving. */
    QuadMesh brute(RavBruteRig rig, RavBruteAssets art, LayeredSpriteCache[] splat, LayeredSpriteCache drop) {
        if (!ENABLED || !rig.dead() || !rig.burst().settled()) return null;
        Entry entry = meshes.get(rig);
        if (entry != null && entry.art == art) return entry.mesh;
        QuadMesh mesh = new QuadMesh();
        RavBruteComposer.emitDead(recorder(mesh), art, splat, drop, rig, 0f, 0f, RECORD_PPC, 1f);
        meshes.put(rig, new Entry(art, mesh));
        return mesh;
    }

    /** The settled Spitter's mesh, recorded on first asking; null while its burst is still moving. */
    QuadMesh spitter(RavSpitterRig rig, RavSpitterAssets art, LayeredSpriteCache[] splat, LayeredSpriteCache drop) {
        if (!ENABLED || !rig.dead() || !rig.burst().settled()) return null;
        Entry entry = meshes.get(rig);
        if (entry != null && entry.art == art) return entry.mesh;
        QuadMesh mesh = new QuadMesh();
        RavSpitterComposer.emitDead(recorder(mesh), art, splat, drop, rig, 0f, 0f, RECORD_PPC, 1f);
        meshes.put(rig, new Entry(art, mesh));
        return mesh;
    }

    /** Settled bodies holding a mesh, for evidence. */
    int size() {
        return meshes.size();
    }

    private static LayeredUnitComposer.SpriteEmitter recorder(QuadMesh mesh) {
        return (layer, x, y, w, h, angle, r, g, b, a) -> mesh.add(layer.sprite,
                x / RECORD_PPC, y / RECORD_PPC, w / RECORD_PPC, h / RECORD_PPC, angle, r, g, b, a);
    }
}
