package com.dillon.starsectormarines.assets.material;

import lombok.extern.log4j.Log4j2;
import org.lwjgl.assimp.AIColor4D;
import org.lwjgl.assimp.AIMaterial;
import org.lwjgl.assimp.AIScene;
import org.lwjgl.assimp.AIString;
import org.lwjgl.assimp.AITexel;
import org.lwjgl.assimp.AITexture;
import org.lwjgl.assimp.Assimp;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Log4j2
public class MaterialExtractor {

    public static List<MaterialInfo> extract(AIScene scene) {
        int numMaterials = scene.mNumMaterials();
        List<MaterialInfo> materialInfos = new ArrayList<>();
        boolean anyHasTexture = false;

        for (int i = 0; i < numMaterials; i++) {
            AIMaterial material = AIMaterial.create(scene.mMaterials().get(i));
            MaterialInfo info = extractMaterialInfo(scene, material, i);
            materialInfos.add(info);
            if (info.hasTexture()) {
                anyHasTexture = true;
            }
        }

        boolean usePaletteMode = !anyHasTexture && numMaterials > 0;

        if (usePaletteMode) {
            log.info("Model has {} materials but no textures. Generating palette texture.", numMaterials);
            for (int i = 0; i < numMaterials; i++) {
                MaterialInfo info = materialInfos.get(i);
                log.info("  Material [{}]: name='{}', color=({}, {}, {})",
                    i, info.name(), info.r(), info.g(), info.b());
            }
            // Generate a 1D palette texture (Nx1)
            byte[] paletteData = new byte[numMaterials * 4]; // RGBA
            for (int i = 0; i < numMaterials; i++) {
                MaterialInfo info = materialInfos.get(i);
                paletteData[i * 4] = (byte) (info.r() * 255);
                paletteData[i * 4 + 1] = (byte) (info.g() * 255);
                paletteData[i * 4 + 2] = (byte) (info.b() * 255);
                paletteData[i * 4 + 3] = (byte) (info.a() * 255);
            }
            MaterialInfo paletteMaterial = new MaterialInfo(null, 1, 1, 1, 1, paletteData, numMaterials, 1);
            return Collections.singletonList(paletteMaterial);
        } else if (numMaterials > 0) {
            return materialInfos;
        } else {
            // No materials at all
            return Collections.singletonList(new MaterialInfo(null, 1, 1, 1, 1));
        }
    }

    private static MaterialInfo extractMaterialInfo(AIScene scene, AIMaterial material, int index) {
        // Extract material name
        AIString nameStr = AIString.calloc();
        String materialName = null;
        if (Assimp.aiGetMaterialString(material, Assimp.AI_MATKEY_NAME, 0, 0, nameStr) == Assimp.aiReturn_SUCCESS) {
            materialName = nameStr.dataString();
        }
        nameStr.free();

        AIString pathStr = AIString.calloc();
        String texturePath = null;
        byte[] textureData = null;
        int width = 0;
        int height = 0;

        // Check for diffuse texture
        if (Assimp.aiGetMaterialTexture(material, Assimp.aiTextureType_DIFFUSE, 0, pathStr, (IntBuffer) null, null, null, null, null, null) == Assimp.aiReturn_SUCCESS ||
            Assimp.aiGetMaterialTexture(material, Assimp.aiTextureType_BASE_COLOR, 0, pathStr, (IntBuffer) null, null, null, null, null, null) == Assimp.aiReturn_SUCCESS) {

            String foundPath = pathStr.dataString();
            if (foundPath != null && !foundPath.isEmpty()) {
                if (foundPath.startsWith("*")) {
                    try {
                        int textureIndex = Integer.parseInt(foundPath.substring(1));
                        EmbeddedTexture extracted = extractEmbeddedTexture(scene, textureIndex);
                        if (extracted != null) {
                            textureData = extracted.data();
                            width = extracted.width();
                            height = extracted.height();
                        }
                    } catch (Exception e) {
                        log.error("Failed to extract embedded texture", e);
                    }
                } else {
                    // Check if the path refers to an embedded texture by filename (common in FBX)
                    int embeddedIndex = findEmbeddedTextureIndex(scene, foundPath);
                    if (embeddedIndex != -1) {
                        try {
                            EmbeddedTexture extracted = extractEmbeddedTexture(scene, embeddedIndex);
                            if (extracted != null) {
                                textureData = extracted.data();
                                width = extracted.width();
                                height = extracted.height();
                            }
                        } catch (Exception e) {
                            log.error("Failed to extract embedded texture by name: {}", foundPath, e);
                            texturePath = foundPath; // Fallback to original path
                        }
                    } else {
                        texturePath = foundPath;
                    }
                }
            }
        }
        pathStr.free();

        // Get Diffuse Color
        AIColor4D color = AIColor4D.calloc();
        float r = 1, g = 1, b = 1, a = 1;
        if (Assimp.aiGetMaterialColor(material, Assimp.AI_MATKEY_COLOR_DIFFUSE, Assimp.aiTextureType_NONE, 0, color) == Assimp.aiReturn_SUCCESS) {
            r = color.r();
            g = color.g();
            b = color.b();
            a = color.a();
        }
        color.free();

        return new MaterialInfo(materialName, texturePath, r, g, b, a, textureData, width, height);
    }

    private static int findEmbeddedTextureIndex(AIScene scene, String texturePath) {
        if (scene.mTextures() == null) return -1;

        // Extract just the filename from the path
        String filename = new File(texturePath).getName();

        for (int i = 0; i < scene.mNumTextures(); i++) {
            AITexture texture = AITexture.create(scene.mTextures().get(i));
            String textureName = texture.mFilename().dataString();

            // Sometimes the embedded texture filename matches the one in the instancePayload
            if (textureName.endsWith(filename) || filename.endsWith(textureName)) {
                return i;
            }
        }
        return -1;
    }

    private record EmbeddedTexture(byte[] data, int width, int height) {
    }

    private static EmbeddedTexture extractEmbeddedTexture(AIScene scene, int textureIndex) {
        if (textureIndex < 0 || textureIndex >= scene.mNumTextures()) return null;
        AITexture texture = AITexture.create(scene.mTextures().get(textureIndex));

        if (texture.mHeight() == 0) {
            // Compressed data (e.g., JPEG, PNG)
            ByteBuffer data = texture.pcDataCompressed();
            int dataSize = texture.mWidth(); // For compressed textures, mWidth is the size in bytes

            if (dataSize <= 0) return null;

            // Validate data size against buffer capacity
            if (data.remaining() < dataSize) {
                log.warn("Embedded texture data size mismatch: mWidth={}, remaining={}", dataSize, data.remaining());
                dataSize = data.remaining();
            }

            // Copy the compressed data to a buffer allocated by MemoryUtil (jemalloc)
            // This ensures compatibility with STB and avoids potential issues with Assimp's memory layout
            ByteBuffer safeData = MemoryUtil.memCalloc(1, dataSize);
            try {
                // Explicitly copy only dataSize bytes
                long srcAddr = MemoryUtil.memAddress(data);
                long dstAddr = MemoryUtil.memAddress(safeData);
                MemoryUtil.memCopy(srcAddr, dstAddr, dataSize);

                // Set limit to dataSize so STB knows the size
                safeData.limit(dataSize);
                safeData.position(0);

                try (MemoryStack stack = MemoryStack.stackPush()) {
                    IntBuffer w = stack.mallocInt(1);
                    IntBuffer h = stack.mallocInt(1);
                    IntBuffer channels = stack.mallocInt(1);

                    STBImage.stbi_set_flip_vertically_on_load(false);
                    ByteBuffer decodedImage = STBImage.stbi_load_from_memory(safeData, w, h, channels, 4); // Force 4 channels (RGBA)

                    if (decodedImage != null) {
                        int width = w.get(0);
                        int height = h.get(0);
                        int size = width * height * 4;
                        byte[] rawPixels = new byte[size];
                        decodedImage.get(rawPixels);

                        // TODO why does freeing this cause corruption of memory? This is a memory leak right here. I think? Are we allocating on the stack?
//                        STBImage.stbi_image_free(decodedImage);

                        return new EmbeddedTexture(rawPixels, width, height);
                    } else {
                        log.error("Failed to decode embedded texture: {}", STBImage.stbi_failure_reason());
                        return null;
                    }
                }
            } finally {
                MemoryUtil.memFree(safeData);
            }
        } else {
            // Raw ARGB data
            // Assimp provides this as an array of aiTexel (B, G, R, A)
            int width = texture.mWidth();
            int height = texture.mHeight();
            int size = width * height * 4;
            byte[] rawPixels = new byte[size];

            // Use pcData with explicit capacity for uncompressed data
            AITexel.Buffer texels = texture.pcData();

            for (int i = 0; i < width * height; i++) {
                AITexel texel = texels.get(i);
                byte b = texel.b();
                byte g = texel.g();
                byte r = texel.r();
                byte a = texel.a();

                rawPixels[i * 4] = r;
                rawPixels[i * 4 + 1] = g;
                rawPixels[i * 4 + 2] = b;
                rawPixels[i * 4 + 3] = a;
            }

            return new EmbeddedTexture(rawPixels, width, height);
        }
    }
}
