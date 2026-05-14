package com.dillon.starsectormarines.assets;

import com.dillon.starsectormarines.assets.animation.Animation;
import com.dillon.starsectormarines.assets.animation.AnimationExtractor;
import com.dillon.starsectormarines.assets.animation.BoneRemapConfig;
import com.dillon.starsectormarines.assets.material.MaterialExtractor;
import com.dillon.starsectormarines.assets.material.MaterialInfo;
import com.dillon.starsectormarines.assets.mesh.MeshExtractor;
import com.dillon.starsectormarines.assets.animation.Skeleton;
import com.dillon.starsectormarines.assets.mesh.MeshData;
import lombok.extern.log4j.Log4j2;
import org.lwjgl.assimp.AIScene;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.lwjgl.assimp.Assimp.aiGetErrorString;
import static org.lwjgl.assimp.Assimp.aiImportFile;
import static org.lwjgl.assimp.Assimp.aiProcess_FlipUVs;
import static org.lwjgl.assimp.Assimp.aiProcess_GenSmoothNormals;
import static org.lwjgl.assimp.Assimp.aiProcess_JoinIdenticalVertices;
import static org.lwjgl.assimp.Assimp.aiProcess_LimitBoneWeights;
import static org.lwjgl.assimp.Assimp.aiProcess_Triangulate;
import static org.lwjgl.assimp.Assimp.aiReleaseImport;

/**
 * A robust model loader using LWJGL's Assimp bindings.
 * This loader supports a wide range of formats (OBJ, FBX, GLTF, DAE, etc.).
 */
@Log4j2
public class ModelLoader {

    private ModelLoader() {
    }

    /**
     * Loads a 3D model. Coordinate-system correction (e.g. Z-up → Y-up) is handled
     * automatically by applying the Assimp node hierarchy transforms to non-skinned meshes.
     */
    public static Optional<LoadedModel> load(String path) {
        return load(path, null);
    }

    /**
     * Backward-compatible overload — the boolean parameter is ignored.
     * Coordinate correction is now derived from the scene's node transforms automatically.
     *
     * @deprecated Use {@link #load(String)} or {@link #load(String, BoneRemapConfig)} instead.
     */
    @Deprecated
    public static Optional<LoadedModel> load(String path, boolean applyZUpCorrection) {
        return load(path, (BoneRemapConfig) null);
    }

    /**
     * Backward-compatible overload — the boolean parameter is ignored.
     *
     * @deprecated Use {@link #load(String, BoneRemapConfig)} instead.
     */
    @Deprecated
    public static Optional<LoadedModel> load(String path, boolean applyZUpCorrection, BoneRemapConfig boneRemap) {
        return load(path, boneRemap);
    }

    /**
     * Loads a 3D model with optional bone name remapping for animation compatibility.
     * Coordinate-system correction is applied automatically from the scene's node hierarchy.
     *
     * @param path The classpath path to the model file.
     * @param boneRemap If non-null, remaps animation bone names (e.g., B-* → HumanIK).
     * @return An Optional containing the LoadedModel (Mesh + Material) if successful.
     */
    public static Optional<LoadedModel> load(String path, BoneRemapConfig boneRemap) {
        log.debug("Loading model from: {}", path);

        File tempFile = null;
        try {
            tempFile = extractToTempFile(path);
        } catch (IOException e) {
            log.error("Failed to extract model file from classpath: {}", path, e);
            return Optional.empty();
        }

        int flags = aiProcess_Triangulate | aiProcess_JoinIdenticalVertices | aiProcess_GenSmoothNormals | aiProcess_FlipUVs | aiProcess_LimitBoneWeights;
        AIScene scene = aiImportFile(tempFile.getAbsolutePath(), flags);

        if (scene == null || scene.mRootNode() == null) {
            log.error("Failed to load model '{}': {}", path, aiGetErrorString());
            return Optional.empty();
        }

        // --- Material Processing ---
        List<MaterialInfo> materials = MaterialExtractor.extract(scene);
        log.debug("Loaded {} materials", materials.size());

        // --- Animation & Skeleton Processing ---
        Map<String, Integer> boneNameToIndex = new HashMap<>();
        Skeleton skeleton = AnimationExtractor.extractSkeleton(scene, boneNameToIndex);
        log.debug("Loaded skeleton with {} bones", skeleton.getBones().size());
        List<Animation> animations = AnimationExtractor.extractAnimations(scene);
        if (boneRemap != null && !animations.isEmpty()) {
            animations = animations.stream()
                    .map(a -> new Animation(a.getName(), a.getDuration(), a.getTicksPerSecond(),
                            boneRemap.remapAnimations(a.getNodeAnimations())))
                    .toList();
            log.debug("Remapped animation bone names to HumanIK standard");
        }
        log.debug("Loaded {} animations", animations.size());
        for (Animation animation : animations) {
            log.debug("- animation {}", animation.getName());
        }

        // --- Mesh Processing ---
        Map<Integer, MeshData> meshDataParts = MeshExtractor.extract(scene, materials, boneNameToIndex);
        log.debug("Loaded mesh parts: {}", meshDataParts.size());
        meshDataParts.forEach((idx, data) ->
            log.debug("  - Part {}: {} vertices, {} indices", idx, data.getVertices().length / 8, data.getIndices().length)
        );

        aiReleaseImport(scene);
        if (!tempFile.delete()) {
            log.warn("Failed to delete temporary model file: {}", tempFile.getAbsolutePath());
        }

        return Optional.of(new LoadedModel(meshDataParts, materials, skeleton, animations));
    }

    private static File extractToTempFile(String classpathPath) throws IOException {
        InputStream is = ModelLoader.class.getClassLoader().getResourceAsStream(classpathPath);
        if (is == null) throw new IOException("Model file not found: " + classpathPath);

        String ext = classpathPath.contains(".") ? classpathPath.substring(classpathPath.lastIndexOf('.')) : ".tmp";
        File tempFile = File.createTempFile("assimp_model_", ext);
        tempFile.deleteOnExit();
        Files.copy(is, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        is.close();
        return tempFile;
    }
}
