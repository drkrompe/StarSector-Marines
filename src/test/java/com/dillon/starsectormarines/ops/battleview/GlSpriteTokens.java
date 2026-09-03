package com.dillon.starsectormarines.ops.battleview;

import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * Sprite tokens backed by real OpenGL textures.
 *
 * <p>The ordinary headless token answers its own size and draws nothing,
 * because the drain behind it is Java2D. The render-budget evidence drains
 * through a real driver, and a token that cannot bind a texture or draw itself
 * would report a pipeline in which the ground never binds and no sprite layer
 * costs anything — which is exactly the shape of the answer the profile is
 * looking for, arrived at by measuring nothing.
 *
 * <p><b>Lazily uploaded.</b> The catalog loads several hundred images and a
 * frame binds a couple of dozen of them, so a token keeps the file it came from
 * and uploads on its first bind. Every sheet uploaded eagerly is hundreds of
 * megabytes of texture memory for pixels nothing samples.
 *
 * <p><b>{@code getTextureWidth} answers 1.</b> In the game it is the normalised
 * max-U of the image inside a possibly padded texture; here the upload is the
 * image, so the image fills its texture and the whole range is its own. Getting
 * this wrong is silent — {@link com.dillon.starsectormarines.render2d.QuadBatch}
 * multiplies by it, so a pixel-valued answer sends every UV thousands of times
 * off the sheet and draws a frame of solid edge-clamp.
 *
 * <p>Textures belong to the context that made them, so this must be closed
 * while that context is still current.
 */
final class GlSpriteTokens implements HeadlessBattleSprites.SpriteTokens, AutoCloseable {

    /** Uploaded texture names by resource path, so a shared sheet uploads once. */
    private final Map<String, Integer> uploaded = new HashMap<>();
    private final List<Integer> owned = new ArrayList<>();

    @Override
    public SpriteAPI token(String path, Path file, BufferedImage image) {
        return new Token(path, file, image.getWidth(), image.getHeight()).proxy();
    }

    /** How many distinct sheets a run actually bound — the working set, not the catalog. */
    int uploadedTextures() {
        return owned.size();
    }

    @Override
    public void close() {
        for (int texture : owned) glDeleteTextures(texture);
        owned.clear();
        uploaded.clear();
    }

    private int texture(String path, Path file) {
        Integer existing = uploaded.get(path);
        if (existing != null) return existing;
        int name = upload(file);
        uploaded.put(path, name);
        owned.add(name);
        return name;
    }

    private int upload(Path file) {
        BufferedImage image;
        try {
            image = ImageIO.read(file.toFile());
        } catch (IOException failure) {
            throw new IllegalStateException("Could not re-read " + file + " to upload it", failure);
        }
        if (image == null) throw new IllegalStateException("Unsupported image " + file);
        int w = image.getWidth();
        int h = image.getHeight();
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        for (int y = h - 1; y >= 0; y--) {          // GL's V runs bottom-up
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                pixels.put((byte) ((p >> 16) & 0xFF));
                pixels.put((byte) ((p >> 8) & 0xFF));
                pixels.put((byte) (p & 0xFF));
                pixels.put((byte) ((p >> 24) & 0xFF));
            }
        }
        pixels.flip();
        int name = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, name);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        return name;
    }

    /**
     * One token's mutable draw state.
     *
     * <p>{@code SpriteAPI} is a setter-then-draw interface, so a whole-sprite
     * command arrives as half a dozen calls followed by
     * {@code renderAtCenter}. The state has to live somewhere between them, and
     * it is per token because the drain shares one sprite object across a run.
     */
    private final class Token {
        private final String path;
        private final Path file;
        private final int pxW;
        private final int pxH;

        private float width;
        private float height;
        private float angleDegrees;
        private float alpha = 1f;
        private Color color = Color.WHITE;
        private boolean additive;

        Token(String path, Path file, int pxW, int pxH) {
            this.path = path;
            this.file = file;
            this.pxW = pxW;
            this.pxH = pxH;
            this.width = pxW;
            this.height = pxH;
        }

        SpriteAPI proxy() {
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getWidth": return (float) pxW;
                    case "getHeight": return (float) pxH;
                    case "getTextureWidth":
                    case "getTextureHeight": return 1f;
                    case "bindTexture":
                        glBindTexture(GL_TEXTURE_2D, texture(path, file));
                        return null;
                    case "setSize":
                        width = (Float) args[0];
                        height = (Float) args[1];
                        return null;
                    case "setAngle": angleDegrees = (Float) args[0]; return null;
                    case "setAlphaMult": alpha = (Float) args[0]; return null;
                    case "setColor": color = (Color) args[0]; return null;
                    case "setAdditiveBlend": additive = true; return null;
                    case "setNormalBlend": additive = false; return null;
                    case "renderAtCenter":
                        render((Float) args[0], (Float) args[1]);
                        return null;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "toString": return "GlSprite[" + path + "]";
                    default: return HeadlessBattleSprites.primitiveDefault(method.getReturnType());
                }
            };
            return (SpriteAPI) Proxy.newProxyInstance(
                    SpriteAPI.class.getClassLoader(), new Class<?>[]{SpriteAPI.class}, handler);
        }

        /**
         * One bind and one quad, which is what the game's own whole-sprite draw
         * costs and the reason a sprite layer cannot coalesce.
         */
        private void render(float cx, float cy) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, texture(path, file));
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, additive ? GL_ONE : GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(color.getRed() / 255f, color.getGreen() / 255f,
                    color.getBlue() / 255f, alpha);
            float halfW = width * 0.5f;
            float halfH = height * 0.5f;
            double rad = Math.toRadians(angleDegrees);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            glBegin(GL_QUADS);
            corner(cx, cy, -halfW, -halfH, cos, sin, 0f, 0f);
            corner(cx, cy, halfW, -halfH, cos, sin, 1f, 0f);
            corner(cx, cy, halfW, halfH, cos, sin, 1f, 1f);
            corner(cx, cy, -halfW, halfH, cos, sin, 0f, 1f);
            glEnd();
        }

        private void corner(float cx, float cy, float dx, float dy,
                            float cos, float sin, float u, float v) {
            glTexCoord2f(u, v);
            glVertex2f(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos);
        }
    }
}
