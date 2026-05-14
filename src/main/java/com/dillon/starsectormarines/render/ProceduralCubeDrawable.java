package com.dillon.starsectormarines.render;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL11.glDrawElements;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL20.*;

/**
 * 24-vertex colored cube. Owns its shader program + VBO/IBO. Lazy-inits on first draw
 * since GL resources require an active context.
 */
public class ProceduralCubeDrawable implements Drawable {

    private static final Logger LOG = Global.getLogger(ProceduralCubeDrawable.class);

    private static final String VS_SOURCE =
            "#version 120\n" +
            "attribute vec3 aPos;\n" +
            "attribute vec3 aColor;\n" +
            "varying vec3 vColor;\n" +
            "uniform mat4 uMvp;\n" +
            "void main() {\n" +
            "    gl_Position = uMvp * vec4(aPos, 1.0);\n" +
            "    vColor = aColor;\n" +
            "}\n";

    private static final String FS_SOURCE =
            "#version 120\n" +
            "varying vec3 vColor;\n" +
            "void main() {\n" +
            "    gl_FragColor = vec4(vColor, 1.0);\n" +
            "}\n";

    private boolean initialized;
    private int program;
    private int uMvpLoc;
    private int vbo;
    private int ibo;
    private int indexCount;

    private final FloatBuffer mvpBuf = BufferUtils.createFloatBuffer(16);

    @Override
    public void draw(float[] viewProjection, float[] model) {
        if (!initialized) init();

        float[] mvp = Mat4.mul(viewProjection, model);

        glUseProgram(program);
        mvpBuf.clear();
        mvpBuf.put(mvp).flip();
        glUniformMatrix4(uMvpLoc, false, mvpBuf);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 24, 0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 24, 12);

        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_INT, 0);

        glDisableVertexAttribArray(0);
        glDisableVertexAttribArray(1);
    }

    private void init() {
        program = buildProgram();
        uMvpLoc = glGetUniformLocation(program, "uMvp");
        buildGeometry();
        initialized = true;
    }

    private static int buildProgram() {
        int vs = compileShader(GL_VERTEX_SHADER, VS_SOURCE);
        int fs = compileShader(GL_FRAGMENT_SHADER, FS_SOURCE);
        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glBindAttribLocation(prog, 0, "aPos");
        glBindAttribLocation(prog, 1, "aColor");
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

    private void buildGeometry() {
        float[] verts = cubeVertices();
        int[]   idx   = cubeIndices();
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
    }

    private static float[] cubeVertices() {
        float[][] faces = {
                {-1, -1,  1, 1, 0.3f, 0.3f,  1, -1,  1, 1, 0.3f, 0.3f,  1,  1,  1, 1, 0.3f, 0.3f, -1,  1,  1, 1, 0.3f, 0.3f},
                { 1, -1, -1, 0.3f, 1, 0.3f, -1, -1, -1, 0.3f, 1, 0.3f, -1,  1, -1, 0.3f, 1, 0.3f,  1,  1, -1, 0.3f, 1, 0.3f},
                { 1, -1,  1, 0.4f, 0.5f, 1,    1, -1, -1, 0.4f, 0.5f, 1,    1,  1, -1, 0.4f, 0.5f, 1,    1,  1,  1, 0.4f, 0.5f, 1},
                {-1, -1, -1, 1, 0.9f, 0.3f, -1, -1,  1, 1, 0.9f, 0.3f, -1,  1,  1, 1, 0.9f, 0.3f, -1,  1, -1, 1, 0.9f, 0.3f},
                {-1,  1,  1, 1, 0.4f, 0.9f,  1,  1,  1, 1, 0.4f, 0.9f,  1,  1, -1, 1, 0.4f, 0.9f, -1,  1, -1, 1, 0.4f, 0.9f},
                {-1, -1, -1, 0.4f, 0.95f, 0.95f, 1, -1, -1, 0.4f, 0.95f, 0.95f, 1, -1,  1, 0.4f, 0.95f, 0.95f, -1, -1,  1, 0.4f, 0.95f, 0.95f},
        };
        float[] flat = new float[6 * 24];
        int o = 0;
        for (float[] f : faces) {
            System.arraycopy(f, 0, flat, o, 24);
            o += 24;
        }
        return flat;
    }

    private static int[] cubeIndices() {
        int[] idx = new int[36];
        for (int f = 0; f < 6; f++) {
            int b = f * 4;
            int o = f * 6;
            idx[o]     = b;
            idx[o + 1] = b + 1;
            idx[o + 2] = b + 2;
            idx[o + 3] = b;
            idx[o + 4] = b + 2;
            idx[o + 5] = b + 3;
        }
        return idx;
    }
}
