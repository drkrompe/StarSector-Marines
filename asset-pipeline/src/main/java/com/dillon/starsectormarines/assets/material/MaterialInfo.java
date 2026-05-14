package com.dillon.starsectormarines.assets.material;

public record MaterialInfo(String name, String texturePath, float r, float g, float b, float a, byte[] textureData, int width, int height) {
    public boolean hasTexture() {
        return (texturePath != null && !texturePath.isEmpty()) || textureData != null;
    }

    // Convenience constructors
    public MaterialInfo(String texturePath, float r, float g, float b, float a) {
        this(null, texturePath, r, g, b, a, null, 0, 0);
    }

    public MaterialInfo(String texturePath, float r, float g, float b, float a, byte[] textureData, int width, int height) {
        this(null, texturePath, r, g, b, a, textureData, width, height);
    }
}
