package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.shield.ShieldDomeService;
import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

import java.util.Locale;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_CURRENT_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_ENABLE_BIT;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_BIT;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColorMask;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glPopAttrib;
import static org.lwjgl.opengl.GL11.glPushAttrib;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * The planetary shield dome's GL effect: one shader drawn into a quad over each
 * dome, an energy bubble seen from above.
 *
 * <p>The quad's texture coordinates are the dome-local frame (the radius is
 * {@code 1}, screen orientation), so the fragment shader works in the dome's own
 * units and needs no camera. Everything it draws, the bright rim, the faint
 * interior, the drifting hexagonal field, the cells lit in proportion to the
 * flux held, the collapse and the rim re-forming, and up to
 * {@link ShieldDomeDraw#MAX_IMPACTS} impacts, is a function of the uniforms
 * below; {@link ShieldDomeShading} is the same function on the CPU.
 *
 * <p><b>Fail-soft.</b> Built lazily on the first draw. If it does not compile,
 * {@link #isBroken()} turns true for good and {@link ShieldDomeRenderSystem}
 * collects the flat placeholder drawing from the next frame on. Switch it off
 * for a control run with {@code -Dbattle.render.domeShader=false}.
 */
final class ShieldDomeShader {

    /** System property that turns the shader off, leaving the flat fill and rim. */
    static final String PROPERTY = "battle.render.domeShader";

    private static final Logger LOG = Global.getLogger(ShieldDomeShader.class);

    static final String VERTEX_SRC = ""
            + "#version 120\n"
            + "varying vec2 vLocal;\n"
            + "void main() {\n"
            + "    vLocal = gl_MultiTexCoord0.xy;\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

    static final String FRAGMENT_SRC = ""
            + "#version 120\n"
            + "varying vec2 vLocal;\n"
            + "uniform float radiusPx;\n"
            + "uniform float radiusCells;\n"
            + "uniform vec3 tint;\n"
            + "uniform float level;\n"
            + "uniform float overloaded;\n"
            + "uniform float recovery;\n"
            + "uniform float hitFlash;\n"
            + "uniform float dropFlash;\n"
            + "uniform float alpha;\n"
            + "uniform float time;\n"
            + "uniform int impactCount;\n"
            + "uniform vec4 impact[" + ShieldDomeDraw.MAX_IMPACTS + "];\n"
            + "const float HEX_CELLS = " + literal(ShieldDomeShading.HEX_CELLS) + ";\n"
            + "const float RING_SPEED = " + literal(ShieldDomeShading.RING_SPEED) + ";\n"
            + "const float RIM_PX = " + literal(ShieldDomeShading.RIM_PX) + ";\n"
            + "const float IMPACT_LIFE = " + literal(ShieldDomeService.IMPACT_SECONDS) + ";\n"
            + "const vec3 AMBER = vec3(1.0, 0.75, 0.47);\n"
            + "const float TWO_PI = 6.2831853;\n"
            + "float hash2(vec2 c) {\n"
            + "    vec3 p3 = fract(vec3(c.xyx) * 0.1031);\n"
            + "    p3 += dot(p3, p3.yzx + 33.33);\n"
            + "    return fract((p3.x + p3.y) * p3.z);\n"
            + "}\n"
            + "float sq(float v) { return v * v; }\n"
            + "void main() {\n"
            + "    vec2 q = vLocal;\n"
            + "    float r = length(q);\n"
            + "    float edge = 1.0 - r;\n"
            + "    float rimW = RIM_PX / radiusPx;\n"
            + "    float inside = edge >= 0.0 ? 1.0 : 0.0;\n"
            + "    float rimIn = (1.0 - smoothstep(0.0, rimW, edge)) * inside;\n"
            + "    float rimOut = (1.0 - smoothstep(0.0, rimW * 2.2, -edge)) * (1.0 - inside);\n"
            + "    float glowIn = (1.0 - smoothstep(0.0, rimW * 6.0, edge)) * inside;\n"
            + "    float fres = pow(clamp(r, 0.0, 1.0), 3.2) * inside;\n"
            + "    vec2 p0 = q * radiusCells;\n"
            + "    vec2 p = p0 + 0.12 * vec2(sin(p0.y * 0.6 + time * 0.9), cos(p0.x * 0.55 - time * 0.75));\n"
            + "    vec2 hp = p / HEX_CELLS;\n"
            + "    vec2 hr = vec2(1.0, 1.7320508);\n"
            + "    vec2 a = mod(hp, hr) - hr * 0.5;\n"
            + "    vec2 b = mod(hp - hr * 0.5, hr) - hr * 0.5;\n"
            + "    vec2 gv = dot(a, a) < dot(b, b) ? a : b;\n"
            + "    vec2 cid = hp - gv;\n"
            + "    vec2 av = abs(gv);\n"
            + "    float hd = max(dot(av, vec2(0.5, 0.8660254)), av.x);\n"
            + "    float hexEdge = 1.0 - smoothstep(0.0, 0.085, 0.5 - hd);\n"
            + "    float ch = hash2(cid);\n"
            + "    vec2 cc = cid * HEX_CELLS;\n"
            + "    float flow = 0.5 + 0.5 * sin(dot(p, vec2(0.62, 0.78)) * 0.42 - time * 1.15\n"
            + "            + 1.6 * sin(p.x * 0.21 + time * 0.31));\n"
            + "    float shimmer = 0.78 + 0.44 * flow;\n"
            + "    float amberMix = smoothstep(0.55, 1.0, level) * 0.65;\n"
            + "    vec3 base = mix(tint, AMBER, amberMix);\n"
            + "    vec3 bright = mix(base, vec3(1.0), 0.45);\n"
            + "    float u = 1.0 - overloaded;\n"
            + "    float down = overloaded;\n"
            + "    float uIn = u * inside;\n"
            + "    vec3 col = vec3(0.0);\n"
            + "    float ca = 0.0;\n"
            + "    float fillA = (0.028 + 0.030 * level + 0.17 * fres + 0.10 * glowIn) * shimmer * uIn;\n"
            + "    col += base * fillA; ca += fillA;\n"
            + "    float lineA = hexEdge * (0.045 + 0.20 * fres + 0.07 * level) * shimmer * uIn;\n"
            + "    float twinkle = 0.5 + 0.5 * sin(time * 2.3 + ch * 40.0);\n"
            + "    float litA = (ch <= level * 0.55 ? 1.0 : 0.0) * twinkle * (1.0 - hexEdge) * 0.055 * uIn;\n"
            + "    float cellGlow = lineA + litA;\n"
            + "    col += bright * cellGlow; ca += cellGlow * 0.4;\n"
            + "    float rimA = (rimIn * (0.62 + 0.28 * hitFlash) + rimOut * 0.30) * u;\n"
            + "    vec3 rimCol = mix(bright, vec3(1.0), hitFlash);\n"
            + "    col += rimCol * rimA; ca += rimA * 0.5;\n"
            + "    if (down > 0.0) {\n"
            + "        float recovered = 1.0 - recovery;\n"
            + "        float ang = atan(q.x, -q.y) / TWO_PI + 0.5;\n"
            + "        float arc = ang <= recovered ? 1.0 : 0.0;\n"
            + "        vec3 dcol = mix(AMBER, tint, smoothstep(0.6, 1.0, recovered));\n"
            + "        float dd = (ang - recovered) * TWO_PI * radiusCells;\n"
            + "        float head = exp(-dd * dd / 1.5);\n"
            + "        float downRim = (rimIn * (0.10 + 0.55 * arc) + rimOut * 0.10 * arc\n"
            + "                + glowIn * head * 0.35) * down;\n"
            + "        col += dcol * downRim; ca += downRim * 0.5;\n"
            + "        float reform = (ch <= recovered * 0.5 ? 1.0 : 0.0) * hexEdge * 0.07 * inside * down;\n"
            + "        col += dcol * reform; ca += reform * 0.4;\n"
            + "        float shatter = (ch <= dropFlash ? 1.0 : 0.0) * (hexEdge * 0.40 + 0.06) * inside * dropFlash * down;\n"
            + "        col += AMBER * shatter; ca += shatter * 0.4;\n"
            + "    }\n"
            + "    if (dropFlash > 0.0) {\n"
            + "        float ringR = 1.0 + 0.12 * (1.0 - dropFlash);\n"
            + "        float dRing = (r - ringR) / (3.5 / radiusPx);\n"
            + "        float collapse = exp(-dRing * dRing) * dropFlash * 0.8;\n"
            + "        col += AMBER * collapse; ca += collapse * 0.5;\n"
            + "    }\n"
            + "    if (impactCount > 0) {\n"
            + "        float flashGlow = 0.0;\n"
            + "        float cellFill = 0.0;\n"
            + "        float lineBoost = 0.0;\n"
            + "        for (int i = 0; i < " + ShieldDomeDraw.MAX_IMPACTS + "; i++) {\n"
            + "            if (i >= impactCount) break;\n"
            + "            vec4 im = impact[i];\n"
            + "            vec2 ip = im.xy * radiusCells;\n"
            + "            float age = im.z;\n"
            + "            float s = im.w;\n"
            + "            float life = clamp(1.0 - age / IMPACT_LIFE, 0.0, 1.0);\n"
            + "            float dist = length(p0 - ip);\n"
            + "            float dc = length(cc - ip);\n"
            + "            float ringRadius = age * RING_SPEED;\n"
            + "            float ringW = 0.55 + 0.9 * age;\n"
            + "            float atten = life * life / (1.0 + 0.10 * ringRadius);\n"
            + "            float ring = exp(-sq((dist - ringRadius) / ringW)) * s * atten;\n"
            + "            float ringCell = exp(-sq((dc - ringRadius) / (ringW * 1.7))) * s * atten;\n"
            + "            float flash = s * exp(-dist * dist / (1.0 + 2.4 * s)) * exp(-age * 5.5);\n"
            + "            float cellFlash = s * exp(-dc * dc / (2.0 + 4.0 * s)) * exp(-age * 3.2);\n"
            + "            flashGlow += ring * 0.35 + flash * 0.80;\n"
            + "            cellFill += (ringCell * 0.7 + cellFlash * 0.7) * (1.0 - hexEdge * 0.6);\n"
            + "            lineBoost += ring + ringCell + cellFlash;\n"
            + "        }\n"
            + "        float within = 1.0 - smoothstep(0.97, 1.0, r);\n"
            + "        float g = flashGlow * within;\n"
            + "        col += mix(tint, vec3(1.0), 0.7) * g; ca += g * 0.4;\n"
            + "        float cf = cellFill * 0.30 * within;\n"
            + "        col += mix(tint, vec3(1.0), 0.35) * cf; ca += cf * 0.4;\n"
            + "        float lb = lineBoost * hexEdge * 0.5 * within;\n"
            + "        col += vec3(lb); ca += lb * 0.4;\n"
            + "    }\n"
            + "    gl_FragColor = vec4(clamp(col, 0.0, 1.0), clamp(ca, 0.0, 1.0)) * alpha;\n"
            + "}\n";

    private final ShaderProgram program = new ShaderProgram("ShieldDome", VERTEX_SRC, FRAGMENT_SRC);
    private final boolean disabled = "false".equalsIgnoreCase(System.getProperty(PROPERTY));
    private boolean drawFailed;

    /** True when the shader should be collected for: not switched off, and not known to have failed. */
    boolean usable() {
        return !disabled && !drawFailed && !program.isBroken();
    }

    boolean isBroken() {
        return drawFailed || program.isBroken();
    }

    ShaderProgram program() {
        return program;
    }

    /** Draws every dome of a pass. Never throws: a failure turns the effect off for good. */
    void draw(ShieldDomePass pass) {
        if (!program.ensure()) return;
        glPushAttrib(GL_COLOR_BUFFER_BIT | GL_ENABLE_BIT | GL_TEXTURE_BIT | GL_CURRENT_BIT);
        try {
            glColorMask(true, true, true, true);
            glEnable(GL_BLEND);
            // Premultiplied: glow terms carry colour beyond their alpha.
            glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glDisable(GL_DEPTH_TEST);
            program.use();
            program.set1f("time", pass.timeSeconds());
            for (ShieldDomeDraw d : pass.draws()) {
                upload(d);
                float e = ShieldDomeShading.EXTENT;
                float x0 = d.sx - e * d.radiusPx;
                float x1 = d.sx + e * d.radiusPx;
                float y0 = d.sy - e * d.radiusPx;
                float y1 = d.sy + e * d.radiusPx;
                glBegin(GL_QUADS);
                glTexCoord2f(-e, -e); glVertex2f(x0, y0);
                glTexCoord2f(e, -e); glVertex2f(x1, y0);
                glTexCoord2f(e, e); glVertex2f(x1, y1);
                glTexCoord2f(-e, e); glVertex2f(x0, y1);
                glEnd();
            }
        } catch (RuntimeException e) {
            LOG.error("ShieldDomeShader draw failed; falling back to the flat dome", e);
            drawFailed = true;
        } finally {
            ShaderProgram.useNone();
            glPopAttrib();
        }
    }

    private void upload(ShieldDomeDraw d) {
        program.set1f("radiusPx", d.radiusPx);
        program.set1f("radiusCells", d.radiusCells);
        program.set3f("tint", d.tint[0], d.tint[1], d.tint[2]);
        program.set1f("level", d.level);
        program.set1f("overloaded", d.overloaded ? 1f : 0f);
        program.set1f("recovery", d.recovery);
        program.set1f("hitFlash", d.hitFlash);
        program.set1f("dropFlash", d.dropFlash);
        program.set1f("alpha", d.alpha);
        program.set1i("impactCount", d.impactCount);
        for (int i = 0; i < d.impactCount; i++) {
            int at = i * 4;
            program.set4f("impact[" + i + "]", d.impacts[at], d.impacts[at + 1],
                    d.impacts[at + 2], d.impacts[at + 3]);
        }
    }

    /** Releases the GL program. Safe whether or not it ever built. */
    void dispose() {
        program.dispose();
    }

    private static String literal(float v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }
}
