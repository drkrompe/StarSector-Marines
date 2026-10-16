package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.air.engine.EngineHullLighting;
import com.dillon.starsectormarines.battle.air.engine.EngineHullLighting.Light;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL20.glUseProgram;

/** Real-driver material clipping, native registration and host-state evidence. */
@Tag("shader-evidence")
class EngineHullLightGlEvidence {
    private static final int SIZE = 64;

    @Test
    void originalMaterialAlphaAndNativeCoordinatesSurviveRotationAndPaddedTextures() {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SIZE, SIZE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            int texture = texture();
            EngineHullLightShader shader = new EngineHullLightShader();
            try {
                Light light = new Light(.3f, -.4f, 0, 1, 5, 6, new Color(255, 128, 64), .8f);
                byte[] normal = draw(shader, texture, light, 0);
                glEnable(GL_CULL_FACE); glCullFace(GL_FRONT_AND_BACK);
                glEnable(GL_STENCIL_TEST); glStencilFunc(GL_NEVER, 0, 255);
                glEnable(GL_COLOR_LOGIC_OP); glLogicOp(GL_CLEAR);
                glEnable(GL_FOG); glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
                byte[] hostile = draw(shader, texture, light, 0);
                for (int c = 0; c < 3; c++)
                    assertEquals(channel(normal, 56, 56, c), channel(hostile, 56, 56, c), 0);
                assertTrue(glIsEnabled(GL_CULL_FACE)); assertTrue(glIsEnabled(GL_STENCIL_TEST));
                assertTrue(glIsEnabled(GL_COLOR_LOGIC_OP)); assertTrue(glIsEnabled(GL_FOG));
                assertEquals(GL_FRONT_AND_BACK, glGetInteger(GL_CULL_FACE_MODE));
                assertEquals(GL_NEVER, glGetInteger(GL_STENCIL_FUNC));
                assertEquals(GL_CLEAR, glGetInteger(GL_LOGIC_OP_MODE));
                IntBuffer polygon = BufferUtils.createIntBuffer(16);
                glGetInteger(GL_POLYGON_MODE, polygon);
                assertEquals(GL_LINE, polygon.get(0)); assertEquals(GL_LINE, polygon.get(1));
                glDisable(GL_CULL_FACE); glDisable(GL_STENCIL_TEST); glDisable(GL_COLOR_LOGIC_OP);
                glDisable(GL_FOG); glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                assertEquals(0, channel(normal, 8, 8, 0), "transparent outer corner");
                assertEquals(0, channel(normal, 24, 24, 0), "transparent internal material hole");
                for (int[] pixel : new int[][]{{56, 56}, {40, 8}, {8, 56}}) {
                    float x = (pixel[0] + .5f) / SIZE * 4 - 2 + .2f;
                    float y = (pixel[1] + .5f) / SIZE * 4 - 2 - .1f;
                    float weight = EngineHullLighting.falloff(light, x, y) * light.strength() * .75f;
                    float coverage = pixel[0] == 40 ? 128 / 255f : 1;
                    int[] material = {128, 64, 32};
                    int[] tint = {255, 128, 64};
                    for (int c = 0; c < 3; c++)
                        assertEquals(material[c] * tint[c] / 255f * weight * coverage,
                                channel(normal, pixel[0], pixel[1], c), 2f,
                                "CPU falloff and painted material channel " + c);
                }
                byte[] rotated = draw(shader, texture, light, 180);
                for (int c = 0; c < 3; c++)
                    assertEquals(channel(normal, 56, 56, c), channel(rotated, 7, 7, c), 1);
                Light half = new Light(light.x(), light.y(), light.inwardX(), light.inwardY(),
                        light.sideRadius(), light.depthRadius(), light.color(), light.strength() / 2);
                byte[] weak = draw(shader, texture, half, 0);
                assertEquals(channel(normal, 56, 56, 0) / 2f, channel(weak, 56, 56, 0), 1f);
                assertFalse(shader.isBroken());
                assertTrue(shader.program().unresolvedUniforms().isEmpty());

                glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture);
                glMatrixMode(GL_TEXTURE); glLoadIdentity(); glTranslatef(.2f, .3f, 0);
                FloatBuffer before = BufferUtils.createFloatBuffer(16); glGetFloat(GL_TEXTURE_MATRIX, before);
                glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, texture);
                glMatrixMode(GL_PROJECTION);
                glDisable(GL_BLEND); glEnable(GL_ALPHA_TEST);
                shader.program().use();
                int program = glGetInteger(GL_CURRENT_PROGRAM);
                pass(shader, texture, light, 0).run();
                assertEquals(program, glGetInteger(GL_CURRENT_PROGRAM));
                assertEquals(GL_TEXTURE1, glGetInteger(GL_ACTIVE_TEXTURE));
                assertEquals(texture, glGetInteger(GL_TEXTURE_BINDING_2D));
                assertEquals(GL_PROJECTION, glGetInteger(GL_MATRIX_MODE));
                assertFalse(glIsEnabled(GL_BLEND)); assertTrue(glIsEnabled(GL_ALPHA_TEST));
                glActiveTexture(GL_TEXTURE0);
                assertEquals(texture, glGetInteger(GL_TEXTURE_BINDING_2D));
                FloatBuffer after = BufferUtils.createFloatBuffer(16); glGetFloat(GL_TEXTURE_MATRIX, after);
                for (int i = 0; i < 16; i++) assertEquals(before.get(i), after.get(i), 0f);
            } finally {
                glUseProgram(0);
                shader.dispose(); glDeleteTextures(texture);
            }
        }
    }

    private static EngineHullLightPass pass(EngineHullLightShader shader, int texture, Light light, float angle) {
        return new EngineHullLightPass(sprite(texture), 32, 32, SIZE, SIZE, angle,
                4, 4, .2f, -.1f, .75f, new Light[]{light}, shader);
    }

    private static byte[] draw(EngineHullLightShader shader, int texture, Light light, float angle) {
        glViewport(0, 0, SIZE, SIZE);
        glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0, SIZE, 0, SIZE, -1, 1);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        glClearColor(0, 0, 0, 0); glClear(GL_COLOR_BUFFER_BIT);
        pass(shader, texture, light, angle).run();
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.remaining()]; pixels.get(result); return result;
    }

    private static int channel(byte[] pixels, int x, int y, int c) {
        return pixels[(y * SIZE + x) * 4 + c] & 255;
    }

    private static int texture() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(8 * 8 * 4);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            boolean padding = x >= 4 || y >= 4;
            int alpha = (x == 0 && y == 0) || (x == 1 && y == 1) ? 0 : x == 2 ? 128 : 255;
            pixels.put((byte) (padding ? 255 : 128)).put((byte) (padding ? 0 : 64))
                    .put((byte) (padding ? 255 : 32)).put((byte) (padding ? 255 : alpha));
        }
        pixels.flip();
        int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 8, 8, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }

    private static SpriteAPI sprite(int texture) {
        return (SpriteAPI) Proxy.newProxyInstance(EngineHullLightGlEvidence.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getHeight" -> 999f; // Mutable display size must not enter UV registration.
                    case "getTextureWidth", "getTextureHeight" -> .5f;
                    case "bindTexture" -> { glBindTexture(GL_TEXTURE_2D, texture); yield null; }
                    case "toString" -> "hull-material-fixture";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
