package com.dillon.starsectormarines.assets.pipeline;

import com.dillon.starsectormarines.assets.LoadedModel;
import com.dillon.starsectormarines.assets.animation.Animation;
import com.dillon.starsectormarines.assets.animation.Animation.KeyFrame;
import com.dillon.starsectormarines.assets.animation.Animation.NodeAnimation;
import com.dillon.starsectormarines.assets.animation.Bone;
import com.dillon.starsectormarines.assets.mesh.MeshData;
import lombok.extern.log4j.Log4j2;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts a freshly-loaded {@link LoadedModel} into the engine's canonical convention
 * (1 unit = 1 meter, Y-up) by applying a uniform scalar to every position-bearing field.
 *
 * <p>Specifically scales:
 * <ul>
 *   <li>Mesh vertex positions (the first 3 floats of each stride; normals and UVs are untouched).</li>
 *   <li>Bone {@code localRestTransform} translation column.</li>
 *   <li>Bone {@code offsetMatrix} translation column (inverse-bind world transform).</li>
 *   <li>Animation position keys.</li>
 * </ul>
 *
 * <p>Rotations and scale-of-rotation are unaffected because we only multiply the translation
 * vectors. Axis convention (Y-up) is handled by Assimp's FBX importer at load time, so this
 * normalizer doesn't need to apply axis swaps.
 *
 * <p>If the path's configured unit is already meters (factor 1.0) the normalizer is a no-op
 * and returns the input unchanged. Bounds on {@link MeshData} are recomputed via a fresh
 * constructor so subsequent consumers see correct {@code min}/{@code max} in canonical units.
 */
@Log4j2
public final class ConventionNormalizer {

    private ConventionNormalizer() {}

    public static LoadedModel normalize(LoadedModel model, String classpathPath, AssetConventionConfig config) {
        double scale = config.scaleFactorFor(classpathPath);
        if (scale == 1.0) {
            return model;
        }
        float fScale = (float) scale;

        Map<Integer, MeshData> rescaledParts = new java.util.HashMap<>(model.meshDataParts().size());
        for (var entry : model.meshDataParts().entrySet()) {
            rescaledParts.put(entry.getKey(), rescaleMesh(entry.getValue(), fScale));
        }

        if (model.skeleton() != null) {
            for (Bone bone : model.skeleton().getBones()) {
                rescaleMatrixTranslation(bone.getLocalRestTransform(), fScale);
                rescaleMatrixTranslation(bone.getOffsetMatrix(), fScale);
            }
        }

        List<Animation> rescaledAnims;
        if (model.animations() != null && !model.animations().isEmpty()) {
            rescaledAnims = new ArrayList<>(model.animations().size());
            for (Animation a : model.animations()) {
                rescaledAnims.add(rescaleAnimation(a, fScale));
            }
        } else {
            rescaledAnims = model.animations();
        }

        log.debug("Normalized '{}' by {} (canonical convention)", classpathPath, scale);
        return new LoadedModel(rescaledParts, model.materials(), model.skeleton(), rescaledAnims);
    }

    private static MeshData rescaleMesh(MeshData src, float scale) {
        float[] verts = src.getVertices();
        int stride = src.getVertexStride();
        float[] scaled = new float[verts.length];
        System.arraycopy(verts, 0, scaled, 0, verts.length);
        for (int i = 0; i < scaled.length; i += stride) {
            scaled[i]     *= scale;
            scaled[i + 1] *= scale;
            scaled[i + 2] *= scale;
        }
        MeshData out = new MeshData(scaled, src.getIndices(), stride);
        out.setWeights(src.getWeights());
        out.setJointIndices(src.getJointIndices());
        return out;
    }

    private static void rescaleMatrixTranslation(org.joml.Matrix4f m, float scale) {
        m.m30(m.m30() * scale);
        m.m31(m.m31() * scale);
        m.m32(m.m32() * scale);
    }

    private static Animation rescaleAnimation(Animation src, float scale) {
        List<NodeAnimation> rescaledNodes = new ArrayList<>(src.getNodeAnimations().size());
        for (NodeAnimation na : src.getNodeAnimations()) {
            List<KeyFrame<Vector3f>> rescaledPos = new ArrayList<>(na.getPositionKeys().size());
            for (KeyFrame<Vector3f> kf : na.getPositionKeys()) {
                Vector3f v = kf.value();
                rescaledPos.add(new KeyFrame<>(kf.time(), new Vector3f(v.x * scale, v.y * scale, v.z * scale)));
            }
            rescaledNodes.add(new NodeAnimation(na.getNodeName(), rescaledPos, na.getRotationKeys(), na.getScalingKeys()));
        }
        return new Animation(src.getName(), src.getDuration(), src.getTicksPerSecond(), rescaledNodes);
    }
}
