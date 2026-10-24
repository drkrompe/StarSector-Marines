package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_CLIENT_VERTEX_ARRAY_BIT;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL11.GL_NEAREST;
import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_RGBA8;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BIT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH;
import static org.lwjgl.opengl.GL11.glGetTexLevelParameteri;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glDeleteTextures;
import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glTexImage2D;
import static org.lwjgl.opengl.GL11.glTexParameteri;
import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glColorPointer;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glGetInteger;
import static org.lwjgl.opengl.GL11.glPopClientAttrib;
import static org.lwjgl.opengl.GL11.glPushClientAttrib;
import static org.lwjgl.opengl.GL11.glTexCoordPointer;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL20.GL_MAX_TEXTURE_IMAGE_UNITS;

/**
 * One draw over quads cut from several textures: the run {@link DrawListRenderer} keeps open across a change of
 * sheet, so painter order no longer has to cost a flush every time the texture changes
 * ({@code battle-render-nouns.md}, law 27).
 *
 * <p><b>Pages.</b> Up to {@link #MAX_PAGES} textures are bound to as many texture units for the draw, and each
 * vertex carries the number of the page it samples. The fragment shader picks the sampler with a branch on that
 * number, which is constant across a quad, so every fragment of a quad takes the same branch; and no texture here
 * is mipmapped, so sampling under the branch is well defined. Pages are ordinary 2D textures of any size, which is
 * what lets any sheet, atlas or whole image join a run with nothing repacked. GLSL 1.20 and GL 2.1 with no
 * extension, the floor every shader in this mod keeps for the Mac's legacy context.
 *
 * <p><b>The page rides in the position's z.</b> Positions go to GL as {@code x, y, page} through the ordinary
 * vertex array rather than as a generic vertex attribute: the fixed-function arrays are what the client-array
 * contract ({@link ClientArray}) is known to survive under an async-renderer bridge, and a third position
 * component is the most ordinary of them. The vertex shader projects {@code x, y} at depth zero and hands z on.
 *
 * <p><b>Same pixels.</b> The fragment shader is the fixed-function {@code GL_MODULATE}: texel times vertex colour.
 * A run is the same quads in the same order a run per sheet would have drawn.
 *
 * <p><b>Fills are quads of a white texel.</b> A solid rect or polygon joins the run as a quad sampling one opaque
 * white texel on a page of its own, tinted by its colour: white times the colour is the colour a fill draws
 * untextured, so a flame and the ember over it no longer close the run between them.
 *
 * <p><b>Blends share a run.</b> An additive quad carries its page past the page table ({@link #lane}). Every run
 * draws premultiplied with {@code ONE, ONE_MINUS_SRC_ALPHA}: a normal quad's colour is then its colour
 * times its alpha over the rest, as before, and an additive quad writes alpha zero, which adds its colour over
 * everything under it. The alpha channel keeps the normal blend's factors, so the destination alpha a normal quad
 * writes is unchanged (the ground composite blends by it); an additive quad leaves the alpha under it alone where
 * the fixed-function path added its own squared. Always premultiplied, never only when a run holds an additive
 * quad: what a quad's pixels round to must not depend on what else shares its run, which culling changes.
 *
 * <p><b>Effects ride in the spare components.</b> Positions go as {@code x, y, z, w} and texture coordinates as
 * {@code s, t, r, q}: the same three fixed-function arrays at four components each, so effects need no array the
 * fixed-function contract does not carry (a secondary colour or normal array would want entry points LWJGL 3's
 * headless binding does not keep). A quad's hit flash is the fraction of its z past the lane
 * ({@link #lane(int, boolean, float)}); w is the light on it ({@link #packLight}); r the direction that light comes
 * from; q its wear: char, embers and dissolve ({@link #packWear}). All four are zero for an ordinary quad, which then
 * draws exactly as before. The packed values are whole numbers under 2^24, exact in a float, unpacked in the vertex
 * shader where every corner carries the same value.
 *
 * <p><b>Fails soft.</b> {@link #ready()} is false when the program does not build or the driver offers fewer than
 * two texture units to a fragment shader, and the drain then keeps one fixed-function run per sheet, as before.
 * NOT thread-safe, like every batch on the render path.
 */
public final class PagedQuadRun {

    /** The most textures one run binds. Eight is under every GL 2.1 driver's fragment unit count worth supporting. */
    public static final int MAX_PAGES = 8;

    /**
     * The part of z's fraction a quad's hit flash takes: a flash of one is a lane plus this. Under one half, so the
     * lane is always the floor of z.
     */
    static final float EFFECT_SPAN = 0.5f;

    /** The brightest light {@link #packLight} carries, as a multiple of the quad's own colour. */
    static final float LIGHT_MAX = 2f;

    private static final String VARYINGS = "varying vec2 uv; varying vec4 tint; varying float page; varying float additive;"
            + " varying float flash; varying vec3 light; varying vec2 lightDir; varying vec3 wear; varying vec2 texels;\n";

    private static final String VERTEX = "#version 120\n"
            + "uniform vec2 pageTexels[" + MAX_PAGES + "];\n"
            + VARYINGS
            + "void main(){\n"
            + "  gl_Position = gl_ModelViewProjectionMatrix * vec4(gl_Vertex.xy, 0.0, 1.0);\n"
            + "  uv = gl_MultiTexCoord0.xy; tint = gl_Color;\n"
            + "  float lane = floor(gl_Vertex.z);\n"
            + "  flash = (gl_Vertex.z - lane) / " + EFFECT_SPAN + ";\n"
            + "  additive = lane > " + MAX_PAGES + ".0 - 0.5 ? 1.0 : 0.0;\n"
            + "  page = lane - additive * " + MAX_PAGES + ".0;\n"
            + "  texels = pageTexels[int(page + 0.5)];\n"
            // Three bytes each, high first: light red, green, blue; wear char, embers, dissolve.
            + "  float l = gl_Vertex.w;\n"
            + "  light = vec3(floor(l / 65536.0), mod(floor(l / 256.0), 256.0), mod(l, 256.0)) * ("
            + LIGHT_MAX + " / 255.0);\n"
            + "  lightDir = vec2(cos(gl_MultiTexCoord0.p), sin(gl_MultiTexCoord0.p));\n"
            + "  float w = gl_MultiTexCoord0.q;\n"
            + "  wear = vec3(floor(w / 65536.0), mod(floor(w / 256.0), 256.0), mod(w, 256.0)) / 255.0;\n"
            + "}\n";

    private static final String FRAGMENT;

    static {
        StringBuilder fragment = new StringBuilder("#version 120\n");
        for (int i = 0; i < MAX_PAGES; i++) fragment.append("uniform sampler2D page").append(i).append(";\n");
        fragment.append(VARYINGS)
                // Value noise over the page's own pixels, so a pattern sticks to the body at any zoom.
                .append("float hash(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }\n")
                .append("float noise(vec2 p){ vec2 i = floor(p); vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f);\n")
                .append("  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),\n")
                .append("             mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0)), f.x), f.y); }\n")
                .append("void main(){\n  vec4 texel;\n");
        // Each page owns the interval around its number, so an interpolated z that is a hair off
        // its integer still finds it.
        for (int i = 0; i < MAX_PAGES; i++) {
            fragment.append(i == 0 ? "  if" : "  else if").append(" (page < ").append(i).append(".5) texel = texture2D(page")
                    .append(i).append(", uv);\n");
        }
        // Premultiplied, an additive quad is one that covers nothing: its colour is added whole and its alpha,
        // zero, keeps everything under it.
        fragment.append("  else texel = vec4(0.0);\n  vec4 c = texel * tint;\n")
                // Light: the quad's colour lifted by the light on it, most on the silhouette edge that faces the
                // light. The edge's outward normal is down the alpha gradient; taken before any branch, as
                // derivatives must be.
                .append("  vec2 g = vec2(dFdx(texel.a), dFdy(texel.a));\n")
                .append("  float gg = dot(g, g);\n")
                .append("  float rim = gg > 1e-6 ? max(dot(-g * inversesqrt(gg), lightDir), 0.0) : 0.0;\n")
                .append("  c.rgb += (vec3(0.1) + c.rgb) * light * (0.6 + 1.8 * rim);\n")
                .append("  if (wear.x + wear.y + wear.z > 0.0) {\n")
                .append("    vec2 p = uv * texels;\n")
                // Char: blackened toward soot in blotches, never quite evenly.
                .append("    float blot = noise(p * 0.07);\n")
                .append("    c.rgb = mix(c.rgb, c.rgb * vec3(0.16, 0.13, 0.11), wear.x * (0.55 + 0.45 * blot));\n")
                // Embers: the brightest specks of a finer noise glow orange.
                .append("    float speck = noise(p * 0.45 + 31.0);\n")
                .append("    c.rgb += vec3(1.0, 0.42, 0.08) * smoothstep(0.86 - 0.3 * wear.y, 0.98, speck) * wear.y * 2.2;\n")
                // Dissolve: eaten away where the blotch noise is under the threshold, edged in dust, or in fire
                // while it burns.
                .append("    float cut = wear.z * 1.12;\n")
                .append("    float edge = blot - cut;\n")
                .append("    if (wear.z > 0.0 && edge < 0.0) c = vec4(0.0);\n")
                .append("    else if (wear.z > 0.0 && edge < 0.07) c.rgb = mix(c.rgb,"
                        + " mix(vec3(0.17, 0.14, 0.11), vec3(1.6, 1.0, 0.35), wear.y),"
                        + " (1.0 - edge / 0.07) * min(1.0, wear.z * 8.0));\n")
                .append("  }\n")
                // A hit flash: the quad's own colour pushed toward white, its shape and alpha untouched.
                .append("  c.rgb = mix(c.rgb, vec3(1.0), flash);\n")
                .append("  gl_FragColor = vec4(c.rgb * c.a, c.a * (1.0 - additive));\n}\n");
        FRAGMENT = fragment.toString();
    }

    private static final int VERTS_PER_QUAD = 4;
    static final int POS_FLOATS_PER_QUAD = VERTS_PER_QUAD * 4;
    static final int UV_FLOATS_PER_QUAD = VERTS_PER_QUAD * 4;
    private static final String[] PAGE_TEXEL_UNIFORMS = new String[MAX_PAGES];

    static {
        for (int i = 0; i < MAX_PAGES; i++) PAGE_TEXEL_UNIFORMS[i] = "pageTexels[" + i + "]";
    }
    static final int COL_FLOATS_PER_QUAD = VERTS_PER_QUAD * 4;

    private final ShaderProgram program = new ShaderProgram("paged quad run", VERTEX, FRAGMENT);
    private final SpriteAPI[] pages = new SpriteAPI[MAX_PAGES];
    /** The page holding {@link #white} in the open run, or -1. */
    private int whitePage = -1;
    /** One opaque white texel: what a solid fill samples, so it can be a quad of the run. */
    private int white;
    private int pageCount;
    /** Pages this driver lets one run bind; settled on the first {@link #ready()}. */
    private int pageLimit = -1;
    private boolean samplersSet;
    /** Each texture's size in pixels, asked of the driver once: what the effects' noise is laid over. */
    private final Map<Integer, long[]> texelSizes = new HashMap<>();

    float[] posData = new float[256 * POS_FLOATS_PER_QUAD];
    float[] uvData = new float[256 * UV_FLOATS_PER_QUAD];
    float[] colData = new float[256 * COL_FLOATS_PER_QUAD];
    int quadCount;

    private final ClientArray posArray = new ClientArray();
    private final ClientArray uvArray = new ClientArray();
    private final ClientArray colArray = new ClientArray();

    /**
     * Whether runs may be paged on this context: the program built and the driver gives a fragment shader at least
     * two texture units. Builds the program and asks the driver once, on the first call; needs a current context.
     */
    public boolean ready() {
        if (pageLimit < 0) {
            int units = glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS);
            pageLimit = Math.min(MAX_PAGES, Math.max(0, units));
        }
        if (pageLimit < 2 || !program.ensure()) return false;
        if (white == 0) white = whiteTexel();
        return white != 0;
    }

    /** A 1x1 opaque white texture; zero when the driver refused one. */
    private static int whiteTexel() {
        int texture = glGenTextures();
        if (texture == 0) return 0;
        ByteBuffer texel = BufferUtils.createByteBuffer(4);
        texel.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255).flip();
        glPushAttrib(GL_TEXTURE_BIT);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, texel);
        glPopAttrib();
        return texture;
    }

    /** The page of the white texel in the open run, adding it if there is room; -1 when the run is full. */
    public int whitePage() {
        if (whitePage >= 0) return whitePage;
        if (pageCount >= pageLimit) return -1;
        pages[pageCount] = null;
        whitePage = pageCount;
        return pageCount++;
    }

    /** The texture units a run may bind here; zero before {@link #ready()} has asked. */
    public int pageLimit() {
        return Math.max(0, pageLimit);
    }

    /**
     * The page {@code sheet} samples in the open run, adding it if the run has a free page; -1 when the run is
     * full of other sheets, and the caller flushes and asks again.
     */
    public int pageOf(SpriteAPI sheet) {
        for (int i = 0; i < pageCount; i++) if (pages[i] == sheet && i != whitePage) return i;
        if (pageCount >= pageLimit) return -1;
        pages[pageCount] = sheet;
        return pageCount++;
    }

    /**
     * The z a quad of {@code page} carries: the page itself for a normal-blend quad, and past the page table for an
     * additive one.
     */
    public static int lane(int page, boolean additive) {
        return additive ? page + MAX_PAGES : page;
    }

    /**
     * The z a quad carries with its effects: {@link #lane(int, boolean)}, and in the fraction its hit flash, the share
     * of white mixed into its colour (law 27).
     */
    public static float lane(int page, boolean additive, float flash) {
        return lane(page, additive) + Math.max(0f, Math.min(1f, flash)) * EFFECT_SPAN;
    }

    /**
     * The w a quad carries for the light on it: each channel a multiple of the quad's own colour added to it, up to
     * {@link #LIGHT_MAX}, in a byte. Zero is no light.
     */
    public static float packLight(float r, float g, float b) {
        return pack(r / LIGHT_MAX, g / LIGHT_MAX, b / LIGHT_MAX);
    }

    /**
     * The q a quad carries for its wear, each share 0 to 1 in a byte: {@code charred}, blackened as by fire;
     * {@code embers}, glowing specks while it burns; {@code dissolved}, eaten away with a hot edge until gone at one.
     */
    public static float packWear(float charred, float embers, float dissolved) {
        return pack(charred, embers, dissolved);
    }

    private static float pack(float a, float b, float c) {
        return (float) (toByte(a) * 65536 + toByte(b) * 256 + toByte(c));
    }

    private static int toByte(float v) {
        return Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
    }

    public boolean isEmpty() {
        return quadCount == 0;
    }

    /** Textures the open run will bind when flushed. */
    public int pageCount() {
        return pageCount;
    }

    /** Room for {@code more} quads past those queued. */
    void ensureCapacity(int more) {
        int needed = quadCount + more;
        if (posData.length >= needed * POS_FLOATS_PER_QUAD) return;
        int quads = posData.length / POS_FLOATS_PER_QUAD;
        while (quads < needed) quads *= 2;
        posData = grow(posData, quads * POS_FLOATS_PER_QUAD, quadCount * POS_FLOATS_PER_QUAD);
        uvData = grow(uvData, quads * UV_FLOATS_PER_QUAD, quadCount * UV_FLOATS_PER_QUAD);
        colData = grow(colData, quads * COL_FLOATS_PER_QUAD, quadCount * COL_FLOATS_PER_QUAD);
    }

    /**
     * Queues the whole of {@code sprite} on {@code lane} ({@link #lane}): the quad {@link SpriteAPI#renderAtCenter}
     * draws after {@code setSize(w, h)}, {@code setAngle(angleDeg)}, {@code setColor} and {@code setAlphaMult(a)}.
     * That is the sprite's {@code texX..texX + texWidth} by {@code texY..texY + texHeight}, its padded extent unless
     * a caller narrowed it, upright from {@code texY} at the bottom, turned counter-clockwise about its middle; and
     * its colour in bytes as the engine sends it, each channel rounded as {@link java.awt.Color} rounds a float and
     * the alpha cut down from {@code 255 * a}. A sprite given a centre of its own turns about that instead, and no
     * caller of the drain sets one, so this does not follow it.
     */
    public void appendSprite(SpriteAPI sprite, float lane, float light, float lightAngle, float wear,
                             float cx, float cy, float w, float h, float angleDeg,
                             float r, float g, float b, float a) {
        ensureCapacity(1);
        float halfW = w * 0.5f;
        float halfH = h * 0.5f;
        double rad = Math.toRadians(angleDeg);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float z = lane;
        int p = quadCount * POS_FLOATS_PER_QUAD;
        // BL BR TR TL, as every other quad of the run.
        p = corner(p, cx, cy, -halfW, -halfH, cos, sin, z, light);
        p = corner(p, cx, cy, halfW, -halfH, cos, sin, z, light);
        p = corner(p, cx, cy, halfW, halfH, cos, sin, z, light);
        corner(p, cx, cy, -halfW, halfH, cos, sin, z, light);

        float u0 = sprite.getTexX();
        float v0 = sprite.getTexY();
        float u1 = u0 + sprite.getTexWidth();
        float v1 = v0 + sprite.getTexHeight();
        int t = quadCount * UV_FLOATS_PER_QUAD;
        t = texCorner(t, u0, v0, lightAngle, wear);
        t = texCorner(t, u1, v0, lightAngle, wear);
        t = texCorner(t, u1, v1, lightAngle, wear);
        texCorner(t, u0, v1, lightAngle, wear);

        float red = (int) (r * 255f + 0.5f) / 255f;
        float green = (int) (g * 255f + 0.5f) / 255f;
        float blue = (int) (b * 255f + 0.5f) / 255f;
        float alpha = Math.min(255, Math.max(0, (int) (255f * a))) / 255f;
        int c = quadCount * COL_FLOATS_PER_QUAD;
        for (int v = 0; v < VERTS_PER_QUAD; v++) {
            colData[c++] = red; colData[c++] = green; colData[c++] = blue; colData[c++] = alpha;
        }
        quadCount++;
    }

    private int corner(int p, float cx, float cy, float x, float y, float cos, float sin, float z, float light) {
        posData[p] = cx + x * cos - y * sin;
        posData[p + 1] = cy + x * sin + y * cos;
        posData[p + 2] = z;
        posData[p + 3] = light;
        return p + 4;
    }

    private int texCorner(int t, float u, float v, float lightAngle, float wear) {
        uvData[t] = u;
        uvData[t + 1] = v;
        uvData[t + 2] = lightAngle;
        uvData[t + 3] = wear;
        return t + 4;
    }

    /** {@code texture}'s size in pixels, asked of the driver the first time; it must be bound to the active unit. */
    private long[] texelSize(int texture) {
        long[] size = texelSizes.get(texture);
        if (size == null) {
            size = new long[]{glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH),
                    glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT)};
            texelSizes.put(texture, size);
        }
        return size;
    }

    /**
     * Draws the open run in one call and starts a new one. The caller owns the server state around it
     * ({@link GlStateBracket#textured2D()} and the blend); this binds its pages, uses its program, and leaves
     * texture unit 0 active, the other units it touched unbound and no program in use.
     */
    public void flush() {
        if (quadCount == 0) {
            pageCount = 0;
            whitePage = -1;
            return;
        }
        program.use();
        if (!samplersSet) {
            for (int i = 0; i < MAX_PAGES; i++) program.set1i("page" + i, i);
            samplersSet = true;
        }
        for (int i = pageCount - 1; i >= 0; i--) {
            glActiveTexture(GL_TEXTURE0 + i);
            if (i == whitePage) {
                glBindTexture(GL_TEXTURE_2D, white);
                program.set2f(PAGE_TEXEL_UNIFORMS[i], 1f, 1f);
            } else {
                pages[i].bindTexture();
                long[] size = texelSize(pages[i].getTextureId());
                program.set2f(PAGE_TEXEL_UNIFORMS[i], size[0], size[1]);
            }
        }
        // Colour premultiplied, alpha as the normal blend writes it, so the destination alpha a normal quad leaves
        // is the same either way; only an additive quad's differs: it leaves the alpha under it where the
        // fixed-function additive blend adds its own squared.
        glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        int verts = quadCount * VERTS_PER_QUAD;
        // As QuadBatch.flush: client arrays need no buffer bound, and reading the old binding back would stall.
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glPushClientAttrib(GL_CLIENT_VERTEX_ARRAY_BIT);
        glEnableClientState(GL_VERTEX_ARRAY);
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glEnableClientState(GL_COLOR_ARRAY);
        glVertexPointer(4, 0, posArray.upload(posData, verts * 4));
        glTexCoordPointer(4, 0, uvArray.upload(uvData, verts * 4));
        glColorPointer(4, 0, colArray.upload(colData, verts * 4));
        glDrawArrays(GL_QUADS, 0, verts);
        glPopClientAttrib();
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        ShaderProgram.useNone();
        for (int i = pageCount - 1; i >= 1; i--) {
            glActiveTexture(GL_TEXTURE0 + i);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        glActiveTexture(GL_TEXTURE0);
        quadCount = 0;
        pageCount = 0;
        whitePage = -1;
    }

    /** Releases the program; the next {@link #ready()} builds it again. The context that made it must be current. */
    public void dispose() {
        program.dispose();
        texelSizes.clear();
        if (white != 0) glDeleteTextures(white);
        white = 0;
        whitePage = -1;
        samplersSet = false;
        pageLimit = -1;
        quadCount = 0;
        pageCount = 0;
    }

    private static float[] grow(float[] src, int newLen, int used) {
        float[] grown = new float[newLen];
        System.arraycopy(src, 0, grown, 0, used);
        return grown;
    }
}
