package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.SoundPlayerAPI;
import org.json.JSONObject;
import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

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
 * What a played battle asks of the game's settings and sound player, with no game running ({@code playBattle}).
 *
 * <p>Textures behave as {@code GameLikeTextureStore} reads them off the game: a path loads once, an image is padded up
 * to power-of-two sides with its bottom row first, a sprite keeps the texture it was made with, and an unload forgets
 * the path without deleting the texture. Anything not answered here goes to the settings underneath, which are the
 * shipped strings.
 */
final class PlayBattleSettings {

    private record Texture(int id, int width, int height, int texWidth, int texHeight) { }

    private final List<Path> roots;
    private final SettingsAPI underneath;
    private final int screenWidth;
    private final int screenHeight;
    private final IntSupplier mouseX;
    private final IntSupplier mouseY;
    private final Map<String, Texture> loaded = new HashMap<>();
    /** Grown to the largest upload and kept: a direct buffer per sheet outruns the collector during a load. */
    private ByteBuffer scratch;

    /** @param roots where a path is looked for, in order: the mod first, the game second */
    PlayBattleSettings(List<Path> roots, SettingsAPI underneath, int screenWidth, int screenHeight,
                       IntSupplier mouseX, IntSupplier mouseY) {
        this.roots = List.copyOf(roots);
        this.underneath = underneath;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    SettingsAPI settings() {
        return (SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(),
                new Class<?>[]{SettingsAPI.class}, (proxy, method, args) -> answer(proxy, method, args));
    }

    private Object answer(Object proxy, Method method, Object[] args) throws Throwable {
        int arity = args == null ? 0 : args.length;
        switch (method.getName()) {
            case "loadTexture" -> { load((String) args[0]); return null; }
            case "unloadTexture" -> { loaded.remove((String) args[0]); return null; }
            case "getSprite" -> {
                if (arity != 1) throw new UnsupportedOperationException("getSprite with " + arity + " arguments");
                Texture t = loaded.get((String) args[0]);
                return t == null ? new PlaySprite(0, 0f, 0f, 0f, 0f) : new PlaySprite(t.id(), t.width(), t.height(),
                        (float) t.width() / t.texWidth(), (float) t.height() / t.texHeight());
            }
            case "openStream" -> { return Files.newInputStream(resolve((String) args[0])); }
            case "loadText" -> { return Files.readString(resolve((String) args[0])); }
            case "getHullSpec" -> { if (PlayCompany.HULL_ID.equals(args[0])) return PlayCompany.hull(); else return method.invoke(underneath, args); }
            case "loadJSON" -> {
                // The game's JSON allows # comment lines.
                return new JSONObject(Files.readString(resolve((String) args[0])).lines()
                        .filter(line -> !line.stripLeading().startsWith("#")).collect(Collectors.joining("\n")));
            }
            case "getScreenWidth" -> { return (float) screenWidth; }
            case "getScreenHeight" -> { return (float) screenHeight; }
            case "getScreenScaleMult" -> { return PlayInput.UI_SCALE; }
            case "getMouseX" -> { return mouseX.getAsInt(); }
            case "getMouseY" -> { return mouseY.getAsInt(); }
            case "hashCode" -> { return System.identityHashCode(proxy); }
            case "equals" -> { return proxy == args[0]; }
            case "toString" -> { return "PlayBattleSettings"; }
            default -> {
                try {
                    return method.invoke(underneath, args);
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            }
        }
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
        int bytes = texW * texH * 4;
        if (scratch == null || scratch.capacity() < bytes) scratch = BufferUtils.createByteBuffer(bytes);
        scratch.clear();
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        int padding = (texW - w) * 4;
        for (int y = h - 1; y >= 0; y--) {
            for (int x = 0; x < w; x++) {
                int p = argb[y * w + x];
                scratch.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
            }
            for (int i = 0; i < padding; i++) scratch.put((byte) 0);
        }
        while (scratch.position() < bytes) scratch.put((byte) 0);
        scratch.flip();
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, texW, texH, 0, GL_RGBA, GL_UNSIGNED_BYTE, scratch);
        glBindTexture(GL_TEXTURE_2D, 0);
        loaded.put(path, new Texture(id, w, h, texW, texH));
    }

    private Path resolve(String path) throws IOException {
        for (Path root : roots) {
            Path file = root.resolve(path);
            if (Files.isRegularFile(file)) return file;
        }
        throw new IOException("no such resource: " + path);
    }

    private static int powerOfTwo(int n) {
        int v = 2;
        while (v < n) v *= 2;
        return v;
    }

    /** A sound player that plays nothing, for {@code -Psilent} or no sound device; the voices read a null handle as no recording. */
    static SoundPlayerAPI silence() {
        return (SoundPlayerAPI) Proxy.newProxyInstance(SoundPlayerAPI.class.getClassLoader(),
                new Class<?>[]{SoundPlayerAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "silence";
                    default -> null;
                });
    }
}
