package com.dillon.starsectormarines.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CCW;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glFrontFace;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.*;

/**
 * UV sphere drawable rendering the same Starsector planet texture as a 3D
 * sphere — the texture is equirectangular, so a straight U=longitude / V=latitude
 * mapping wraps correctly. Lazy-loads the sprite on first draw.
 *
 * <p>Sphere is unit radius at origin; caller scales/positions via the node's transform.
 */
public class PlanetSphereDrawable implements Drawable {

    private static final Logger LOG = Global.getLogger(PlanetSphereDrawable.class);

    private static final int LAT_RINGS  = 24;
    private static final int LON_SEGS   = 36;

    private static final String VS_SOURCE =
            "#version 120\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec2 aUv;\n" +
            "varying vec2 vUv;\n" +
            "uniform mat4 uMvp;\n" +
            "void main() {\n" +
            "    gl_Position = uMvp * vec4(aPos, 1.0);\n" +
            "    vUv = aUv;\n" +
            "}\n";

    private static final String FS_SOURCE =
            "#version 120\n" +
            "varying vec2 vUv;\n" +
            "uniform sampler2D uTex;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(uTex, vUv);\n" +
            "}\n";

    private final String texturePath;

    private SpriteAPI sprite;
    private boolean failed;

    private boolean shaderInitialized;
    private boolean meshInitialized;
    private int program;
    private int uMvpLoc;
    private int uTexLoc;
    private int vbo;
    private int ibo;
    private int indexCount;

    private final FloatBuffer mvpBuf = BufferUtils.createFloatBuffer(16);

    public PlanetSphereDrawable(String texturePath) {
        this.texturePath = texturePath;
    }

    @Override
    public void draw(float[] viewProjection, float[] model) {
        if (failed) return;
        if (!shaderInitialized) initShader();
        if (!ensureSprite()) return;
        if (!meshInitialized) buildSphere();

        float[] mvp = Mat4.mul(viewProjection, model);

        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
        glFrontFace(GL_CCW);
        glEnable(GL_TEXTURE_2D);

        glActiveTexture(GL_TEXTURE0);
        sprite.bindTexture();

        glUseProgram(program);
        glUniform1i(uTexLoc, 0);

        mvpBuf.clear();
        mvpBuf.put(mvp).flip();
        glUniformMatrix4(uMvpLoc, false, mvpBuf);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 20, 0);
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 20, 12);

        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);

        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }

    private void initShader() {
        try {
            program = buildProgram();
            uMvpLoc = glGetUniformLocation(program, "uMvp");
            uTexLoc = glGetUniformLocation(program, "uTex");
            shaderInitialized = true;
        } catch (RuntimeException e) {
            LOG.error("PlanetSphereDrawable shader init failed", e);
            failed = true;
        }
    }

    private boolean ensureSprite() {
        if (sprite != null) return true;
        try {
            Global.getSettings().loadTexture(texturePath);
            sprite = Global.getSettings().getSprite(texturePath);
            if (sprite == null) {
                LOG.error("Sprite for planet texture not found: " + texturePath);
                failed = true;
                return false;
            }
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load planet texture: " + texturePath, e);
            failed = true;
            return false;
        }
    }

    private void buildSphere() {
        float u = sprite.getTextureWidth();
        float v = sprite.getTextureHeight();

        int vertCount = (LAT_RINGS + 1) * (LON_SEGS + 1);
        float[] verts = new float[vertCount * 5];
        int vp = 0;
        for (int i = 0; i <= LAT_RINGS; i++) {
            // lat = 0 at north pole (+Y), pi at south pole (-Y)
            float lat = (float) Math.PI * i / LAT_RINGS;
            float y = (float) Math.cos(lat);
            float r = (float) Math.sin(lat);
            // V maps so image-top (V=v with our flip convention) goes to north pole
            float texV = (1f - (float) i / LAT_RINGS) * v;
            for (int j = 0; j <= LON_SEGS; j++) {
                float lon = (float) (2 * Math.PI) * j / LON_SEGS;
                float x = r * (float) Math.cos(lon);
                float z = r * (float) Math.sin(lon);
                float texU = ((float) j / LON_SEGS) * u;
                verts[vp++] = x;
                verts[vp++] = y;
                verts[vp++] = z;
                verts[vp++] = texU;
                verts[vp++] = texV;
            }
        }

        int quadCount = LAT_RINGS * LON_SEGS;
        int[] idx = new int[quadCount * 6];
        int ip = 0;
        int stride = LON_SEGS + 1;
        for (int i = 0; i < LAT_RINGS; i++) {
            for (int j = 0; j < LON_SEGS; j++) {
                int a = i * stride + j;
                int b = a + 1;
                int c = a + stride;
                int d = c + 1;
                // CCW when viewed from outside (camera at +Z, normal pointing outward)
                idx[ip++] = a;
                idx[ip++] = c;
                idx[ip++] = b;
                idx[ip++] = b;
                idx[ip++] = c;
                idx[ip++] = d;
            }
        }
        indexCount = idx.length;

        FloatBuffer vbuf = BufferUtils.createFloatBuffer(verts.length);
        vbuf.put(verts).flip();
        IntBuffer ibuf = BufferUtils.createIntBuffer(idx.length);
        ibuf.put(idx).flip();

        vbo = glGenBuffers();
        ibo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vbuf, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, ibuf, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);

        meshInitialized = true;
        LOG.info("PlanetSphereDrawable mesh built: " + vertCount + " verts, "
                + (indexCount / 3) + " tris");
    }

    private static int buildProgram() {
        int vs = compileShader(GL_VERTEX_SHADER, VS_SOURCE);
        int fs = compileShader(GL_FRAGMENT_SHADER, FS_SOURCE);
        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glBindAttribLocation(prog, 0, "aPos");
        glBindAttribLocation(prog, 1, "aUv");
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link failed: " + glGetProgramInfoLog(prog, 4096));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private static int compileShader(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        if (glGetShaderi(s, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compile failed: " + glGetShaderInfoLog(s, 4096));
        }
        return s;
    }
}
