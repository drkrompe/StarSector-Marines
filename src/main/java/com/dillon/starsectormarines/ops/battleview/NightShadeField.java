package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.GlErrors;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.opengl.GL11.GL_CLIENT_PIXEL_STORE_BIT;
import static org.lwjgl.opengl.GL11.GL_LINEAR;
import static org.lwjgl.opengl.GL11.GL_LUMINANCE;
import static org.lwjgl.opengl.GL11.GL_LUMINANCE16;
import static org.lwjgl.opengl.GL11.GL_LUMINANCE8_ALPHA8;
import static org.lwjgl.opengl.GL11.GL_LUMINANCE_ALPHA;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA16;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11.GL_UNPACK_ALIGNMENT;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_SHORT;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glPixelStorei;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL20.glUseProgram;

/**
 * The night shade computed per pixel on the card instead of built as quads ({@code gpu-night-shade.md}).
 *
 * <p>The shade is a function of light pools sampled bilinearly at half-cell resolution
 * ({@link NightLightingRenderSystem}): the lamps' pools and colours, baked once per map, and fire's pools, refilled
 * every frame. The CPU path evaluates that function at a grid of points in every lit cell and appends a tinted quad
 * per point, which on a burning lane is a hundred thousand quads a frame. Here the pools are textures, the GPU's own
 * bilinear filter is the same interpolation, and one quad over the visible cells runs the same shade function in a
 * fragment shader: the picture of the CPU path at every pixel rather than at its sample points.
 *
 * <p>Textures: the lamps' field over the whole map, RGBA16 of {@code reds, greens, blues, pool}; fire's pool over the
 * visible window, LUMINANCE16; and a cell mask over the visible window, nearest-sampled, luminance for "the player
 * sees this cell" and alpha for "there is ground here". Pools are stored over {@link #RANGE}, which no sum of lights
 * reaches in practice; the shade saturates at one well before it.
 *
 * <p>Only for a host that can run a custom pass owning its own GL ({@link BattleRenderHostProfile#residentGroundAllowed}),
 * like the resident ground; a Java2D drain keeps the quads. A failure logs once and the next frame builds quads.
 */
final class NightShadeField {
    private static final Logger LOG = Global.getLogger(NightShadeField.class);

    /**
     * {@code -Dbattle.render.gpuNightShade=false} builds the night shade as quads on the CPU, the control for this
     * pass. Temporary: remove it with the quad path for GL hosts once stills and renderEvidence have read the field
     * against it; the quad path itself stays for Java2D hosts.
     */
    static final String GPU_PROPERTY = "battle.render.gpuNightShade";
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(GPU_PROPERTY, "true"));

    /** What a stored pool of one full unit reads as; pools above it are clamped. */
    static final float RANGE = 16f;

    static final String VERTEX_SRC = "#version 120\n"
            + "varying vec2 cell;\n"
            + "void main(){ cell = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }\n";

    static final String FRAGMENT_SRC = "#version 120\n"
            + "uniform sampler2D lampField; uniform sampler2D fireField; uniform sampler2D cellMask;\n"
            + "uniform vec2 lampSize; uniform vec2 fireOrigin; uniform vec2 fireSize;\n"
            + "uniform vec2 cellOrigin; uniform vec2 cellSize;\n"
            + "uniform float range; uniform float darkness; uniform float alphaMult;\n"
            + "uniform float fog; uniform float burning; uniform float premultiplied;\n"
            + "uniform vec3 fireColour;\n"
            + "varying vec2 cell;\n"
            + "void main(){\n"
            + "  vec4 mask = texture2D(cellMask, (floor(cell) - cellOrigin + 0.5) / cellSize);\n"
            + "  if (mask.a < 0.5) discard;\n"
            + "  vec2 g = cell * 2.0;\n"
            + "  vec4 lamp = texture2D(lampField, (g + 0.5) / lampSize) * range;\n"
            // Fog hides a lamp's light; a burn's is seen from anywhere (FireFogClearing).
            + "  float pool = fog > 0.5 && mask.r < 0.5 ? 0.0 : lamp.a;\n"
            + "  float firePool = burning > 0.5 ? texture2D(fireField, (g - fireOrigin + 0.5) / fireSize).r * range : 0.0;\n"
            + "  float lit = pool + firePool;\n"
            + "  float dark = darkness * (1.0 - 0.92 * min(1.0, lit));\n"
            + "  float warm = min(1.0, 0.11 * min(1.0, pool) + 0.2 * min(1.0, firePool));\n"
            + "  float alpha = dark + warm * (1.0 - dark);\n"
            + "  if (alpha <= 0.0) discard;\n"
            + "  float weight = dark * (1.0 - warm);\n"
            + "  vec3 rgb = (vec3(0.012, 0.019, 0.037) * weight\n"
            + "      + warm * (lamp.rgb + firePool * fireColour) / max(0.001, lit)) / alpha;\n"
            + "  float a = alpha * alphaMult;\n"
            + "  gl_FragColor = premultiplied > 0.5 ? vec4(rgb * a, a) : vec4(rgb, a);\n"
            + "}\n";

    private final ShaderProgram program = new ShaderProgram("NightShadeField", VERTEX_SRC, FRAGMENT_SRC);
    private int lampTexture, fireTexture, cellTexture;
    private int fireTexW, fireTexH, cellTexW, cellTexH;
    private boolean broken;

    private ShortBuffer lampTexels;
    private int lampW, lampH;
    private boolean lampDirty;

    private ShortBuffer fireTexels = BufferUtils.createShortBuffer(1);
    private int fireX0, fireY0, fireW, fireH;
    private boolean burning;
    private ByteBuffer cellTexels = BufferUtils.createByteBuffer(2);
    private int cellX0, cellY0, cellW, cellH;
    private float darkness, alphaMult;
    private float screenX0, screenY0, screenX1, screenY1;

    /** Whether a draw has failed, after which the caller builds quads again. */
    boolean broken() { return broken; }

    /** Takes the lamps' baked pools; uploaded on the next draw. */
    void bakeLamps(float[] pools, float[] reds, float[] greens, float[] blues, int width, int height) {
        int n = width * height;
        if (lampTexels == null || lampTexels.capacity() < n * 4) lampTexels = BufferUtils.createShortBuffer(n * 4);
        lampTexels.clear();
        for (int i = 0; i < n; i++) {
            lampTexels.put(stored(reds[i])).put(stored(greens[i])).put(stored(blues[i])).put(stored(pools[i]));
        }
        lampTexels.flip();
        lampW = width;
        lampH = height;
        lampDirty = true;
    }

    /**
     * This frame's window: the visible cells, fire's pools over them ({@code firePools} null when nothing burns),
     * and which of them the player sees ({@code shown} null when fog does not clip lighting).
     */
    void frame(int minX, int minY, int maxX, int maxY, float[] firePools, CellTopology ground, boolean[] shown,
               float darkness, float alphaMult, BattleCamera camera) {
        this.darkness = darkness;
        this.alphaMult = alphaMult;
        int gridW = ground.getWidth();
        cellX0 = minX;
        cellY0 = minY;
        cellW = maxX - minX + 1;
        cellH = maxY - minY + 1;
        cellTexels = room(cellTexels, cellW * cellH * 2);
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                boolean seen = shown == null || shown[y * gridW + x];
                boolean solid = ground.getGroundKind(x, y) != CellTopology.GroundKind.VOID;
                cellTexels.put(seen ? (byte) 255 : 0).put(solid ? (byte) 255 : 0);
            }
        }
        cellTexels.flip();

        burning = firePools != null;
        if (burning) {
            fireX0 = 2 * minX;
            fireY0 = 2 * minY;
            int x1 = Math.min(lampW - 1, 2 * maxX + 3), y1 = Math.min(lampH - 1, 2 * maxY + 3);
            fireW = x1 - fireX0 + 1;
            fireH = y1 - fireY0 + 1;
            if (fireTexels.capacity() < fireW * fireH) fireTexels = BufferUtils.createShortBuffer(fireW * fireH);
            fireTexels.clear();
            for (int y = fireY0; y <= y1; y++) {
                int row = y * lampW;
                for (int x = fireX0; x <= x1; x++) fireTexels.put(stored(firePools[row + x]));
            }
            fireTexels.flip();
        }
        screenX0 = camera.cellToScreenX(minX);
        screenY0 = camera.cellToScreenY(minY);
        screenX1 = camera.cellToScreenX(maxX + 1f);
        screenY1 = camera.cellToScreenY(maxY + 1f);
    }

    /**
     * Draws the shade over the window into whatever target and projection the caller has bound: with {@code fog},
     * a lamp's light stops at what the player cannot see; {@code premultiplied} for a target composited later. The
     * caller owns and restores the server state; this leaves its program bound and texture unit 0 active. False,
     * having marked itself broken, when it could not.
     */
    boolean draw(boolean fog, boolean premultiplied) {
        if (broken || lampTexels == null) return false;
        if (!program.ensure()) {
            broken = true;
            return false;
        }
        GlErrors.clear();
        glPushClientAttrib(GL_CLIENT_PIXEL_STORE_BIT);
        try {
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            glActiveTexture(GL_TEXTURE0);
            if (lampTexture == 0) {
                lampTexture = texture(GL_LINEAR);
                lampDirty = true;
            }
            glBindTexture(GL_TEXTURE_2D, lampTexture);
            if (lampDirty) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16, lampW, lampH, 0, GL_RGBA, GL_UNSIGNED_SHORT, lampTexels);
                lampDirty = false;
            }
            glActiveTexture(GL_TEXTURE0 + 1);
            if (fireTexture == 0) fireTexture = texture(GL_LINEAR);
            glBindTexture(GL_TEXTURE_2D, fireTexture);
            if (burning) {
                if (fireW != fireTexW || fireH != fireTexH) {
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE16, fireW, fireH, 0, GL_LUMINANCE, GL_UNSIGNED_SHORT,
                            fireTexels);
                    fireTexW = fireW;
                    fireTexH = fireH;
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, fireW, fireH, GL_LUMINANCE, GL_UNSIGNED_SHORT, fireTexels);
                }
            }
            glActiveTexture(GL_TEXTURE0 + 2);
            if (cellTexture == 0) cellTexture = texture(GL_NEAREST);
            glBindTexture(GL_TEXTURE_2D, cellTexture);
            if (cellW != cellTexW || cellH != cellTexH) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_LUMINANCE8_ALPHA8, cellW, cellH, 0, GL_LUMINANCE_ALPHA,
                        GL_UNSIGNED_BYTE, cellTexels);
                cellTexW = cellW;
                cellTexH = cellH;
            } else {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, cellW, cellH, GL_LUMINANCE_ALPHA, GL_UNSIGNED_BYTE, cellTexels);
            }
            glActiveTexture(GL_TEXTURE0);
        } finally {
            glPopClientAttrib();
        }

        program.use();
        program.set1i("lampField", 0);
        program.set1i("fireField", 1);
        program.set1i("cellMask", 2);
        program.set2f("lampSize", lampW, lampH);
        program.set2f("fireOrigin", fireX0, fireY0);
        program.set2f("fireSize", Math.max(1, fireTexW), Math.max(1, fireTexH));
        program.set2f("cellOrigin", cellX0, cellY0);
        program.set2f("cellSize", cellW, cellH);
        program.set1f("range", RANGE);
        program.set1f("darkness", darkness);
        program.set1f("alphaMult", alphaMult);
        program.set1f("fog", fog ? 1f : 0f);
        program.set1f("burning", burning ? 1f : 0f);
        program.set1f("premultiplied", premultiplied ? 1f : 0f);
        program.set3f("fireColour", FireLight.RED, FireLight.GREEN, FireLight.BLUE);
        glBegin(GL_QUADS);
        glTexCoord2f(cellX0, cellY0);
        glVertex2f(screenX0, screenY0);
        glTexCoord2f(cellX0 + cellW, cellY0);
        glVertex2f(screenX1, screenY0);
        glTexCoord2f(cellX0 + cellW, cellY0 + cellH);
        glVertex2f(screenX1, screenY1);
        glTexCoord2f(cellX0, cellY0 + cellH);
        glVertex2f(screenX0, screenY1);
        glEnd();
        if (GlErrors.check("night shade field")) {
            broken = true;
            LOG.error("Night shade field failed; building the shade as quads");
            return false;
        }
        return true;
    }

    void dispose() {
        program.dispose();
        if (lampTexture != 0) glDeleteTextures(lampTexture);
        if (fireTexture != 0) glDeleteTextures(fireTexture);
        if (cellTexture != 0) glDeleteTextures(cellTexture);
        lampTexture = fireTexture = cellTexture = 0;
        fireTexW = fireTexH = cellTexW = cellTexH = 0;
        lampDirty = lampTexels != null;
        broken = false;
        glUseProgram(0);
    }

    private static int texture(int filter) {
        int texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static short stored(float pool) {
        return (short) Math.round(Math.max(0f, Math.min(1f, pool / RANGE)) * 65535f);
    }

    private static ByteBuffer room(ByteBuffer buffer, int bytes) {
        if (buffer.capacity() < bytes) return BufferUtils.createByteBuffer(bytes);
        buffer.clear();
        return buffer;
    }
}
