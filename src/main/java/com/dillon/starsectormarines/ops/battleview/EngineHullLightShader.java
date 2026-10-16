package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ShaderProgram;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL14.glBlendEquation;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20.glUseProgram;

/** Lazy, material-preserving engine spill; the original hull alpha is its only surface mask. */
public final class EngineHullLightShader {
    static final String VERTEX_SOURCE = """
            #version 120
            varying vec2 hullUV;
            void main() {
                hullUV = gl_MultiTexCoord0.xy;
                gl_Position = ftransform();
            }
            """;
    static final String FRAGMENT_SOURCE = """
            #version 120
            uniform sampler2D hull;
            uniform vec2 extent;
            uniform vec2 hullSize;
            uniform vec2 pivot;
            uniform vec2 lightCenter;
            uniform vec2 inward;
            uniform vec2 radii;
            uniform vec3 tint;
            uniform float strength;
            uniform float alpha;
            varying vec2 hullUV;
            void main() {
                vec4 material = texture2D(hull, hullUV * extent);
                vec2 local = (hullUV - vec2(0.5)) * hullSize + pivot;
                vec2 delta = local - lightCenter;
                float depth = dot(delta, inward) / radii.y;
                float side = dot(delta, vec2(-inward.y, inward.x)) / radii.x;
                float falloff = max(0.0, 1.0 - depth * depth - side * side);
                falloff *= falloff;
                gl_FragColor = vec4(material.rgb * tint * strength * falloff, material.a * alpha);
            }
            """;
    private final ShaderProgram program = new ShaderProgram("aircraft engine hull light", VERTEX_SOURCE, FRAGMENT_SOURCE);

    public boolean isBroken() { return program.isBroken(); }
    ShaderProgram program() { return program; }

    public void draw(EngineHullLightPass pass) {
        if (pass.hullSprite() == null || pass.lights() == null || pass.lights().length == 0
                || pass.alphaMult() <= 0 || !program.ensure()) return;
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int previousActive = glGetInteger(GL_ACTIVE_TEXTURE);
        int previousMatrix = glGetInteger(GL_MATRIX_MODE);
        glActiveTexture(GL_TEXTURE0);
        int previousBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glMatrixMode(GL_TEXTURE); glPushMatrix(); glLoadIdentity();
        try {
            glDisable(GL_DEPTH_TEST); glDisable(GL_ALPHA_TEST); glDisable(GL_LIGHTING);
            glDisable(GL_CULL_FACE); glDisable(GL_STENCIL_TEST); glDisable(GL_COLOR_LOGIC_OP);
            glDisable(GL_FOG); glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            glEnable(GL_TEXTURE_2D); glEnable(GL_BLEND);
            glBlendEquation(GL_FUNC_ADD); glBlendFunc(GL_SRC_ALPHA, GL_ONE);
            glColorMask(true, true, true, true);
            pass.hullSprite().bindTexture();
            program.use();
            program.set1i("hull", 0);
            program.set2f("extent", pass.hullSprite().getTextureWidth(), pass.hullSprite().getTextureHeight());
            program.set2f("hullSize", pass.hullWidthCells(), pass.hullLengthCells());
            program.set2f("pivot", pass.pivotX(), pass.pivotY());
            program.set1f("alpha", pass.alphaMult());
            for (var light : pass.lights()) {
                if (light.strength() <= 0 || light.sideRadius() <= 0 || light.depthRadius() <= 0) continue;
                program.set2f("lightCenter", light.x(), light.y());
                program.set2f("inward", light.inwardX(), light.inwardY());
                program.set2f("radii", light.sideRadius(), light.depthRadius());
                program.set3f("tint", light.color().getRed() / 255f,
                        light.color().getGreen() / 255f, light.color().getBlue() / 255f);
                program.set1f("strength", light.strength());
                quad(pass);
            }
        } finally {
            glActiveTexture(GL_TEXTURE0);
            glMatrixMode(GL_TEXTURE); glPopMatrix();
            glPopAttrib();
            glBindTexture(GL_TEXTURE_2D, previousBinding);
            glUseProgram(previousProgram);
            glMatrixMode(previousMatrix);
            glActiveTexture(previousActive);
        }
    }

    private static void quad(EngineHullLightPass pass) {
        float radians = (float) Math.toRadians(pass.angleDegrees());
        float cos = (float) Math.cos(radians), sin = (float) Math.sin(radians);
        glBegin(GL_QUADS);
        for (int corner = 0; corner < 4; corner++) {
            float u = corner == 0 || corner == 3 ? 0 : 1;
            float v = corner < 2 ? 0 : 1;
            float x = (u - .5f) * pass.widthPx(), y = (v - .5f) * pass.heightPx();
            glTexCoord2f(u, v);
            glVertex2f(pass.centerX() + x * cos - y * sin, pass.centerY() + x * sin + y * cos);
        }
        glEnd();
    }

    public void dispose() { program.dispose(); }
}
