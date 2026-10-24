package com.dillon.starsectormarines.render2d;

import com.fs.starfarer.api.graphics.SpriteAPI;

import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;

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
 * ({@code programmable-quad-batch.md}).
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
 * <p><b>Effects ride in z's fraction.</b> A quad's hit flash is the fraction of its z past the lane
 * ({@link #lane(int, boolean, float)}), so effects need no array beyond the three the fixed-function contract
 * carries: a secondary colour or normal array would want entry points LWJGL 3's headless binding does not keep.
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

    private static final String VERTEX = "#version 120\n"
            + "varying vec2 uv; varying vec4 tint; varying float page; varying float additive; varying float flash;\n"
            + "void main(){\n"
            + "  gl_Position = gl_ModelViewProjectionMatrix * vec4(gl_Vertex.xy, 0.0, 1.0);\n"
            + "  uv = gl_MultiTexCoord0.xy; tint = gl_Color;\n"
            + "  float lane = floor(gl_Vertex.z);\n"
            + "  flash = (gl_Vertex.z - lane) / " + EFFECT_SPAN + ";\n"
            + "  additive = lane > " + MAX_PAGES + ".0 - 0.5 ? 1.0 : 0.0;\n"
            + "  page = lane - additive * " + MAX_PAGES + ".0;\n"
            + "}\n";

    private static final String FRAGMENT;

    static {
        StringBuilder fragment = new StringBuilder("#version 120\n");
        for (int i = 0; i < MAX_PAGES; i++) fragment.append("uniform sampler2D page").append(i).append(";\n");
        fragment.append("varying vec2 uv; varying vec4 tint; varying float page; varying float additive; varying float flash;\n")
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
                // A hit flash: the quad's own colour pushed toward white, its shape and alpha untouched.
                .append("  c.rgb = mix(c.rgb, vec3(1.0), flash);\n")
                .append("  gl_FragColor = vec4(c.rgb * c.a, c.a * (1.0 - additive));\n}\n");
        FRAGMENT = fragment.toString();
    }

    private static final int VERTS_PER_QUAD = 4;
    static final int POS_FLOATS_PER_QUAD = VERTS_PER_QUAD * 3;
    static final int UV_FLOATS_PER_QUAD = VERTS_PER_QUAD * 2;
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
        for (int i = pageCount - 1; i >= 0; i--) {
            glActiveTexture(GL_TEXTURE0 + i);
            if (i == whitePage) glBindTexture(GL_TEXTURE_2D, white);
            else pages[i].bindTexture();
        }
        program.use();
        if (!samplersSet) {
            for (int i = 0; i < MAX_PAGES; i++) program.set1i("page" + i, i);
            samplersSet = true;
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
        glVertexPointer(3, 0, posArray.upload(posData, verts * 3));
        glTexCoordPointer(2, 0, uvArray.upload(uvData, verts * 2));
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
