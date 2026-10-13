package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.testsupport.HeadlessGl;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Real-driver evidence for alpha admission; automatically selected by shaderEvidence. */
@Tag("shader-evidence")
class NightLightingGlEvidence {
    private static final int SIZE = 64;

    @Test
    void transparentGapsKeepFogTreatmentAndOpaqueArtworkReceivesLight() {
        try (HeadlessGl gl = HeadlessGl.createOrNull(SIZE, SIZE)) {
            if (gl == null) {
                System.out.println("[shader-evidence] SKIPPED: " + HeadlessGl.unavailableReason());
                return;
            }
            int texture = alphaTexture();
            NightLightingPass pass = new NightLightingPass();
            try {
                BattleCamera camera = new BattleCamera(4, 4);
                camera.setViewport(0, 0, SIZE, SIZE, 16);
                pass.reset(camera);
                fill(pass.fogShade(), .02f, .03f, .05f, .8f);
                fill(pass.litShade(), .8f, .6f, .3f, .2f);
                byte[] control = draw(pass);
                pass.surfaces().addSheetQuad(RenderLayer.FOG, sheet(texture), 0, 0, 4, 4,
                        SIZE / 2f, SIZE / 2f, SIZE, SIZE, 1, 1, 1, 1);
                byte[] admitted = draw(pass);
                // First texel is a transparent corner; the last is an opaque hull.
                assertPixel(control, admitted, 8, 8, 0);
                assertTrue(channel(admitted, 56, 56, 0) > channel(control, 56, 56, 0) + 80,
                        "opaque artwork must visibly receive the lit treatment");
                // The upper-left two rows form an internal gap, not just an outside margin.
                assertPixel(control, admitted, 24, 24, 0);
                // A half-alpha edge mixes the two premultiplied treatments continuously.
                for (int c = 0; c < 3; c++) {
                    int middle = channel(admitted, 40, 8, c);
                    int dark = channel(control, 40, 8, c);
                    int light = channel(admitted, 56, 56, c);
                    assertEquals((dark + light) / 2f, middle, 3f, "partial alpha channel " + c);
                }
                pass.surfaces().addSheetQuad(RenderLayer.FOG, sheet(texture), 0, 0, 4, 4,
                        SIZE / 2f, SIZE / 2f, SIZE, SIZE, 1, 1, 1, 1);
                byte[] overlapping = draw(pass);
                assertPixel(control, overlapping, 24, 24, 0);
                assertPixel(admitted, overlapping, 56, 56, 0);
                for (int c = 0; c < 3; c++) {
                    int dark = channel(control, 40, 8, c);
                    int light = channel(admitted, 56, 56, c);
                    assertEquals(dark * .25f + light * .75f, channel(overlapping, 40, 8, c), 3f,
                            "overlapping partial edges must accumulate coverage, channel " + c);
                }
                pass.surfaces().clear();
                pass.surfaces().addSheetQuad(RenderLayer.FOG, sheet(texture), 0, 0, 4, 4,
                        SIZE / 2f, SIZE / 2f, SIZE, SIZE, 180, 1, 1, 1, 1);
                byte[] rotated = draw(pass);
                assertPixel(control, rotated, 56, 56, 0);
                for (int c = 0; c < 4; c++)
                    assertEquals(channel(admitted, 56, 56, c), channel(rotated, 8, 8, c), 1);
                pass.surfaces().clear();
                pass.surfaces().addSheetQuadFlippedV(RenderLayer.FOG, sheet(texture), 0, 0, 4, 4,
                        SIZE / 2f, SIZE / 2f, SIZE, SIZE, 1, 1, 1, 1);
                byte[] mirrored = draw(pass);
                assertPixel(control, mirrored, 8, 56, 0);
                assertPixel(admitted, mirrored, 56, 8, 1);
                pass.surfaces().clear();
                pass.surfaces().addSheetQuadFlippedV(RenderLayer.FOG, sheet(texture), 0, 0, 4, 4,
                        SIZE / 2f, SIZE / 2f, SIZE, SIZE, 180, 1, 1, 1, 1);
                byte[] rotatedMirror = draw(pass);
                assertPixel(control, rotatedMirror, 56, 8, 0);
                for (int c = 0; c < 4; c++)
                    assertEquals(channel(admitted, 56, 56, c), channel(rotatedMirror, 8, 56, c), 1);
                assertTrue(pass.shader().program().unresolvedUniforms().isEmpty());
                assertTrue(pass.shader().coverageProgram().unresolvedUniforms().isEmpty());
            } finally {
                pass.dispose();
                glDeleteTextures(texture);
            }
        }
    }

    private static void fill(PolyMesh mesh, float r, float g, float b, float a) {
        mesh.appendQuad(0, 0, SIZE, 0, SIZE, SIZE, 0, SIZE, r, g, b, a);
    }

    private static byte[] draw(NightLightingPass pass) {
        glViewport(0, 0, SIZE, SIZE);
        glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0, SIZE, 0, SIZE, -1, 1);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        glClearColor(.5f, .5f, .5f, 1); glClear(GL_COLOR_BUFFER_BIT);
        pass.run();
        ByteBuffer pixels = BufferUtils.createByteBuffer(SIZE * SIZE * 4);
        glReadPixels(0, 0, SIZE, SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.remaining()]; pixels.get(result); return result;
    }

    private static int channel(byte[] pixels, int x, int y, int c) {
        return pixels[(y * SIZE + x) * 4 + c] & 255;
    }

    private static void assertPixel(byte[] expected, byte[] actual, int x, int y, int tolerance) {
        for (int c = 0; c < 4; c++)
            assertEquals(channel(expected, x, y, c), channel(actual, x, y, c), tolerance);
    }

    private static int alphaTexture() {
        ByteBuffer pixels = BufferUtils.createByteBuffer(4 * 4 * 4);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
            int alpha = x < 2 && y < 2 ? 0 : x == 2 ? 128 : 255;
            pixels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) alpha);
        }
        pixels.flip();
        int texture = glGenTextures(); glBindTexture(GL_TEXTURE_2D, texture);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 4, 4, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        return texture;
    }

    private static SpriteAPI sheet(int texture) {
        return (SpriteAPI) Proxy.newProxyInstance(NightLightingGlEvidence.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getWidth", "getHeight" -> 4f;
                    case "getTextureWidth", "getTextureHeight" -> 1f;
                    case "getTextureId" -> texture;
                    case "bindTexture" -> { glBindTexture(GL_TEXTURE_2D, texture); yield null; }
                    case "toString" -> "night-alpha-fixture";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
