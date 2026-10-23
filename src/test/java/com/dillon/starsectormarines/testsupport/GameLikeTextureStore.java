package com.dillon.starsectormarines.testsupport;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;

/**
 * A {@link SettingsAPI} whose texture store behaves as the game's does, over a real GL context.
 *
 * <p>Read off the game's own {@code TextureLoader} and {@code Sprite}: a load of a path already
 * loaded is the same texture; a texture is padded up to power-of-two sides and a sprite reports
 * the image's share of it; a sprite captures the texture its path had when it was made and keeps
 * it; and {@code unloadTexture} forgets the path <em>without deleting the texture</em>. The last
 * two are the reasons the texture lifecycle exists, so a stand-in that was kinder than the game
 * here would let the evidence pass on a lifecycle that leaks or draws blank in play.
 *
 * <p>Everything else on the settings throws, so a caller that reaches for more of the game than
 * this provides finds out at once.
 */
public final class GameLikeTextureStore {

    /** One loaded texture: its GL id, the image's size and the padded texture's. */
    public record Texture(int id, int width, int height, int texWidth, int texHeight) { }

    private final List<Path> roots;
    private final Map<String, Texture> loaded = new HashMap<>();
    private int loads;

    /** @param roots where a path is looked for, in order: the mod first, the game second */
    public GameLikeTextureStore(List<Path> roots) {
        this.roots = List.copyOf(roots);
    }

    private int screenWidth;
    private int screenHeight;

    /** Answers the game's screen size, at a UI scale of one, for a caller that draws a frame. */
    public GameLikeTextureStore screen(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        return this;
    }

    private void requireScreen() {
        if (screenWidth <= 0) throw new UnsupportedOperationException("SettingsAPI screen size: call screen() first");
    }

    /** The settings to install with {@code Global.setSettings}. */
    public SettingsAPI settings() {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "loadTexture" -> { load((String) args[0]); yield null; }
            case "unloadTexture" -> { loaded.remove((String) args[0]); yield null; }
            case "getSprite" -> {
                if (args.length != 1) throw new UnsupportedOperationException("getSprite" + args.length);
                yield sprite(loaded.get((String) args[0]));
            }
            case "openStream" -> Files.newInputStream(resolve((String) args[0]));
            case "getScreenWidth" -> { requireScreen(); yield (float) screenWidth; }
            case "getScreenHeight" -> { requireScreen(); yield (float) screenHeight; }
            case "getScreenScaleMult" -> { requireScreen(); yield 1f; }
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "GameLikeTextureStore";
            default -> throw new UnsupportedOperationException("SettingsAPI." + method.getName());
        };
        return (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, handler);
    }

    /** The texture a path has now, or null when the store has no texture for it. */
    public Texture texture(String path) {
        return loaded.get(path);
    }

    /** How many images were decoded and uploaded. */
    public int loads() {
        return loads;
    }

    private void load(String path) throws IOException {
        if (loaded.containsKey(path)) return;
        BufferedImage image;
        try (InputStream in = Files.newInputStream(resolve(path))) {
            image = ImageIO.read(in);
        }
        if (image == null) throw new IOException("not an image: " + path);
        int w = image.getWidth();
        int h = image.getHeight();
        int texW = powerOfTwo(w);
        int texH = powerOfTwo(h);
        ByteBuffer pixels = BufferUtils.createByteBuffer(texW * texH * 4);
        for (int y = 0; y < texH; y++) {
            for (int x = 0; x < texW; x++) {
                int argb = x < w && y < h ? image.getRGB(x, y) : 0;
                pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put((byte) (argb >>> 24));
            }
        }
        pixels.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texW, texH, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glBindTexture(GL_TEXTURE_2D, 0);
        loaded.put(path, new Texture(id, w, h, texW, texH));
        loads++;
    }

    private Path resolve(String path) throws IOException {
        for (Path root : roots) {
            Path file = root.resolve(path);
            if (Files.isRegularFile(file)) return file;
        }
        throw new IOException("no such resource: " + path);
    }

    static int powerOfTwo(int n) {
        int v = 2;
        while (v < n) v *= 2;
        return v;
    }

    /** A sprite holding the texture it was made with, as the game's does; 0 when there was none. */
    private static SpriteAPI sprite(Texture texture) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getTextureId" -> texture == null ? 0 : texture.id();
            case "getWidth" -> texture == null ? 0f : (float) texture.width();
            case "getHeight" -> texture == null ? 0f : (float) texture.height();
            case "getTextureWidth" -> texture == null ? 0f : (float) texture.width() / texture.texWidth();
            case "getTextureHeight" -> texture == null ? 0f : (float) texture.height() / texture.texHeight();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "Sprite" + texture;
            default -> {
                Class<?> type = method.getReturnType();
                if (type == void.class) yield null;
                if (type == boolean.class) yield false;
                if (type == float.class) yield 0f;
                if (type == int.class) yield 0;
                yield null;
            }
        };
        return (SpriteAPI) Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, handler);
    }
}
