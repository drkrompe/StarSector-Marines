package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.ShaderProgram;
import com.dillon.starsectormarines.ui.retained.CanvasHostPass;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.UiImage;
import com.dillon.starsectormarines.ui.retained.UiSpriteCache;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glTexCoord2f;
import static org.lwjgl.opengl.GL11.glVertex2f;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE1;
import static org.lwjgl.opengl.GL13.glActiveTexture;

/**
 * The world a ship is parked at, turning slowly behind her.
 *
 * <p>The game's own planet renderer has no API, so this draws the same thing
 * from the same data: the planet's equirectangular texture, its cloud layer, its
 * tilt and its spin, wrapped on a sphere worked out per pixel on one quad. It is
 * kept dark and lit from the side, because it is set dressing behind a deck plan
 * the player is here to read.
 */
final class BackdropPlanetPass implements CanvasHostPass {

    static final String VERTEX_SRC = ""
            + "#version 120\n"
            + "varying vec2 vDisc;\n"
            + "void main() {\n"
            + "    vDisc = gl_MultiTexCoord0.xy;\n"
            + "    gl_Position = ftransform();\n"
            + "}\n";

    /** {@code vDisc} is the plane through the planet's middle, in radii with Y up. */
    static final String FRAGMENT_SRC = ""
            + "#version 120\n"
            + "varying vec2 vDisc;\n"
            + "uniform sampler2D surface;\n"
            + "uniform sampler2D clouds;\n"
            + "uniform vec2 surfaceExtent;\n"
            + "uniform vec2 cloudExtent;\n"
            + "uniform vec2 turn;\n"
            + "uniform vec2 tilt;\n"
            + "uniform vec2 pitch;\n"
            + "uniform vec3 sun;\n"
            + "uniform vec4 tint;\n"
            + "uniform vec4 cloudTint;\n"
            + "uniform vec4 atmosphere;\n"
            + "uniform float atmosphereReach;\n"
            + "uniform float brightness;\n"
            + "uniform float edge;\n"
            + "uniform float alpha;\n"
            + "const float PI = 3.14159265;\n"
            + "vec2 wrap(vec3 s, float spin) {\n"
            + "    return vec2(fract(atan(s.x, s.z) / (2.0 * PI) + 0.5 + spin), asin(clamp(s.y, -1.0, 1.0)) / PI + 0.5);\n"
            + "}\n"
            + "void main() {\n"
            + "    float d = length(vDisc);\n"
            + "    if (d >= 1.0) {\n"
            + "        float a = clamp(1.0 - (d - 1.0) / atmosphereReach, 0.0, 1.0);\n"
            + "        float side = clamp(dot(vDisc / d, normalize(sun.xy)) + 0.35, 0.0, 1.0);\n"
            + "        gl_FragColor = vec4(atmosphere.rgb, a * a * side * atmosphere.a * alpha);\n"
            + "        return;\n"
            + "    }\n"
            + "    vec3 n = vec3(vDisc, sqrt(1.0 - d * d));\n"
            + "    vec3 s = vec3(n.x, n.y * pitch.x - n.z * pitch.y, n.y * pitch.y + n.z * pitch.x);\n"
            + "    s = vec3(s.x * tilt.x - s.y * tilt.y, s.x * tilt.y + s.y * tilt.x, s.z);\n"
            + "    vec3 color = texture2D(surface, wrap(s, turn.x) * surfaceExtent).rgb * tint.rgb;\n"
            + "    float cover = texture2D(clouds, wrap(s, turn.y) * cloudExtent).a * cloudTint.a;\n"
            + "    color = mix(color, cloudTint.rgb, cover);\n"
            + "    float light = max(dot(n, sun), 0.0);\n"
            + "    float rim = pow(1.0 - n.z, 3.0) * light;\n"
            + "    color = color * (light * brightness + 0.012) + atmosphere.rgb * atmosphere.a * rim;\n"
            + "    gl_FragColor = vec4(color, alpha * smoothstep(1.0, 1.0 - edge, d));\n"
            + "}\n";

    /** Where the light falls from, as a direction towards it, and how much of it reaches the surface. */
    record Light(float x, float y, float z, float brightness) {
        /** Over the far shoulder, so the lit limb is the part the hull leaves showing. */
        static final Light BEHIND_THE_HULL = new Light(0.74f, 0.42f, 0.30f, 0.62f);
    }

    /** One per process: a program belongs to the GL context, not to one visit to a page. */
    private static final ShaderProgram PROGRAM =
            new ShaderProgram("backdrop planet", VERTEX_SRC, FRAGMENT_SRC);

    private final ShipBackdrop.Planet planet;
    private final float centerX, centerY, radius, time;
    private final Light light;

    /** The centre and radius are in host pixels from the viewport's lower left. */
    BackdropPlanetPass(ShipBackdrop.Planet planet, float centerX, float centerY, float radius, float time, Light light) {
        this.planet = planet;
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        this.time = time;
        this.light = light;
    }

    @Override
    public void draw(CanvasHostViewport viewport, float alphaMult) {
        UiImage surface = UiSpriteCache.shared().resolve(planet.texture());
        if (surface == null || surface.liveSprite() == null || !PROGRAM.ensure()) return;
        UiImage clouds = planet.clouds() == null ? null : UiSpriteCache.shared().resolve(planet.clouds());
        SpriteAPI cloudSprite = clouds == null ? null : clouds.liveSprite();
        SpriteAPI surfaceSprite = surface.liveSprite();

        float reach = Math.max(0.02f, planet.atmosphereReach());
        PROGRAM.use();
        glActiveTexture(GL_TEXTURE1);
        if (cloudSprite != null) cloudSprite.bindTexture();
        else glBindTexture(GL_TEXTURE_2D, 0);
        glActiveTexture(GL_TEXTURE0);
        surfaceSprite.bindTexture();
        PROGRAM.set1i("surface", 0);
        PROGRAM.set1i("clouds", 1);
        PROGRAM.set2f("surfaceExtent", surfaceSprite.getTextureWidth(), surfaceSprite.getTextureHeight());
        PROGRAM.set2f("cloudExtent", cloudSprite == null ? 0f : cloudSprite.getTextureWidth(),
                cloudSprite == null ? 0f : cloudSprite.getTextureHeight());
        PROGRAM.set2f("turn", time * planet.spin() / 360f, time * planet.cloudSpin() / 360f);
        PROGRAM.set2f("tilt", cos(planet.tilt()), sin(planet.tilt()));
        PROGRAM.set2f("pitch", cos(planet.pitch()), sin(planet.pitch()));
        float sun = (float) Math.sqrt(light.x() * light.x() + light.y() * light.y() + light.z() * light.z());
        PROGRAM.set3f("sun", light.x() / sun, light.y() / sun, light.z() / sun);
        setColor("tint", planet.tint(), 1f);
        // No cloud texture samples black with no alpha, so an absent layer needs no branch.
        setColor("cloudTint", planet.cloudTint(), cloudSprite == null ? 0f : 1f);
        setColor("atmosphere", planet.atmosphere(), 1f);
        PROGRAM.set1f("atmosphereReach", reach);
        PROGRAM.set1f("brightness", light.brightness());
        PROGRAM.set1f("edge", 2f / radius);
        PROGRAM.set1f("alpha", alphaMult);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(1f, 1f, 1f, 1f);
        float extent = 1f + reach;
        float x = viewport.screenX() + centerX, y = viewport.screenY() + centerY, half = radius * extent;
        glBegin(GL_QUADS);
        glTexCoord2f(-extent, -extent);
        glVertex2f(x - half, y - half);
        glTexCoord2f(extent, -extent);
        glVertex2f(x + half, y - half);
        glTexCoord2f(extent, extent);
        glVertex2f(x + half, y + half);
        glTexCoord2f(-extent, extent);
        glVertex2f(x - half, y + half);
        glEnd();
        ShaderProgram.useNone();
    }

    private static void setColor(String uniform, Color color, float alpha) {
        if (color == null) {
            PROGRAM.set4f(uniform, 1f, 1f, 1f, 0f);
            return;
        }
        PROGRAM.set4f(uniform, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f,
                color.getAlpha() / 255f * alpha);
    }

    private static float cos(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }

    private static float sin(float degrees) {
        return (float) Math.sin(Math.toRadians(degrees));
    }
}
