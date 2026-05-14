package com.dillon.starsectormarines.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL11.glEnable;
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
 * Textured quad drawable that reuses Starsector's already-loaded planet sprite
 * GL texture. Lazy-loads {@link SpriteAPI} on first draw (need an active GL
 * context); aborts permanently if the texture path can't be resolved.
 *
 * <p>The quad spans [-1, +1] in XY at z=0. Caller controls placement/size via
 * the node's transform.
 *
 * <p>UV bounds come from {@link SpriteAPI#getTextureWidth} / {@link SpriteAPI#getTextureHeight},
 * which is the fraction of the (power-of-2) GL texture the image actually
 * occupies — Starsector pads to POT, so sampling 0..1 would include padding.
 * V is flipped (0 at top of image to match texture-coordinate convention).
 */
public class PlanetBillboardDrawable implements Drawable {

    private static final Logger LOG = Global.getLogger(PlanetBillboardDrawable.class);

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
            "    vec4 c = texture2D(uTex, vUv);\n" +
            "    if (c.a < 0.01) discard;\n" +
            "    gl_FragColor = c;\n" +
            "}\n";

    private final String texturePath;

    private SpriteAPI sprite;
    private boolean failed;

    private boolean shaderInitialized;
    private boolean quadInitialized;
    private int program;
    private int uMvpLoc;
    private int uTexLoc;
    private int vbo;
    private int ibo;

    private final FloatBuffer mvpBuf = BufferUtils.createFloatBuffer(16);

    public PlanetBillboardDrawable(String texturePath) {
        this.texturePath = texturePath;
    }

    @Override
    public void draw(float[] viewProjection, float[] model) {
        if (failed) return;
        if (!shaderInitialized) initShader();
        if (!ensureSprite()) return;
        if (!quadInitialized) buildQuad();

        float[] mvp = Mat4.mul(viewProjection, model);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_CULL_FACE);
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

        glDrawElements(GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0);

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
            LOG.error("PlanetBillboardDrawable shader init failed", e);
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
            LOG.info("Loaded planet texture: " + texturePath
                    + " (uvFrac=" + sprite.getTextureWidth() + "x" + sprite.getTextureHeight() + ")");
            return true;
        } catch (Exception e) {
            LOG.error("Failed to load planet texture: " + texturePath, e);
            failed = true;
            return false;
        }
    }

    private void buildQuad() {
        float u = sprite.getTextureWidth();
        float v = sprite.getTextureHeight();

        // Position (3f) + UV (2f), interleaved. V mapped so image-top is at +Y
        // (Starsector's texture coordinate convention is image-y matches GL-y,
        // so V=0 is at the bottom of the image, not the top).
        float[] verts = {
                -1f, -1f, 0f,   0f, 0f,
                 1f, -1f, 0f,   u,  0f,
                 1f,  1f, 0f,   u,  v,
                -1f,  1f, 0f,   0f, v,
        };
        int[] idx = {0, 1, 2, 0, 2, 3};

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

        quadInitialized = true;
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
