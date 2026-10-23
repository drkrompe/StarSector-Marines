package com.dillon.starsectormarines.testsupport.gl3;

import com.dillon.starsectormarines.testsupport.GlSurface;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.CGL;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Locale;

/**
 * A headless OpenGL surface from LWJGL 3, for a machine whose game install has no LWJGL 2 native this JVM can load.
 *
 * <p><b>The Mac is the reason it exists.</b> The game's {@code liblwjgl.jnilib} is x86_64 and the project's JDK
 * there is arm64, so {@code HeadlessGl} cannot start. LWJGL 3 ships arm64 natives, and on macOS this asks CGL for a
 * context directly: no window, no window-server session, so it works from an SSH shell as well as a Terminal. It is
 * the legacy 2.1 profile, the one the game itself gets there, which keeps the fixed-function calls and the
 * {@code #version 120} shaders the renderer is written in; framebuffer objects come from
 * {@code ARB_framebuffer_object}. Elsewhere it makes a hidden GLFW window, so the same path can be run on Windows.
 *
 * <p><b>The frame is an FBO.</b> A CGL context has no default framebuffer, so the surface binds its own colour
 * texture and depth renderbuffer and leaves them bound. The battle renderer reads the binding before each of its
 * own passes and restores what it read, so every draw ends up here and the readback reads it.
 *
 * <p>The mod's classes are compiled against LWJGL 2. They run here because LWJGL 3 kept nearly every name and
 * descriptor; the few it did not are bridged on the tool classpath ({@code org.lwjgl.opengl.Display}, the input
 * stubs, and a patched {@code GL11}). See {@code gpu-review-drain.md}.
 */
public final class Lwjgl3Surface implements GlSurface {

    private final long cglContext;
    private final long window;
    private final int framebuffer;
    private final int colour;
    private final int depth;

    private Lwjgl3Surface(long cglContext, long window, int framebuffer, int colour, int depth) {
        this.cglContext = cglContext;
        this.window = window;
        this.framebuffer = framebuffer;
        this.colour = colour;
        this.depth = depth;
    }

    /** Called by name from {@link GlSurface#createOrNull}; null when no accelerated context can be had. */
    public static GlSurface create(int width, int height) {
        boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        long cgl = 0L;
        long window = 0L;
        try {
            if (mac) {
                cgl = cglContext();
                if (cgl == 0L) return null;
            } else {
                window = glfwWindow();
                if (window == 0L) return null;
            }
            GL.createCapabilities();
            int colour = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colour);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            int depth = GL30.glGenRenderbuffers();
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL11.GL_DEPTH_COMPONENT, width, height);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            int framebuffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, colour, 0);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, depth);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                System.out.println("[gl3] the frame target is incomplete");
                return null;
            }
            return new Lwjgl3Surface(cgl, window, framebuffer, colour, depth);
        } catch (RuntimeException | LinkageError failure) {
            System.out.println("[gl3] no context: " + failure);
            return null;
        }
    }

    /** An accelerated legacy-profile context, current on this thread, with no drawable at all. */
    private static long cglContext() {
        IntBuffer attributes = BufferUtils.createIntBuffer(16);
        attributes.put(CGL.kCGLPFAAccelerated)
                .put(CGL.kCGLPFAColorSize).put(24)
                .put(CGL.kCGLPFAAlphaSize).put(8)
                .put(CGL.kCGLPFADepthSize).put(24)
                .put(CGL.kCGLPFAOpenGLProfile).put(CGL.kCGLOGLPVersion_Legacy)
                .put(CGL.kCGLPFAAllowOfflineRenderers)
                .put(0).flip();
        PointerBuffer format = BufferUtils.createPointerBuffer(1);
        IntBuffer formats = BufferUtils.createIntBuffer(1);
        if (CGL.CGLChoosePixelFormat(attributes, format, formats) != 0 || format.get(0) == 0L) return 0L;
        PointerBuffer context = BufferUtils.createPointerBuffer(1);
        int created = CGL.CGLCreateContext(format.get(0), 0L, context);
        CGL.CGLDestroyPixelFormat(format.get(0));
        if (created != 0 || context.get(0) == 0L) return 0L;
        if (CGL.CGLSetCurrentContext(context.get(0)) != 0) {
            CGL.CGLDestroyContext(context.get(0));
            return 0L;
        }
        return context.get(0);
    }

    /** A hidden window whose context is made current on this thread; its own framebuffer is never drawn to. */
    private static long glfwWindow() {
        if (!GLFW.glfwInit()) return 0L;
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long window = GLFW.glfwCreateWindow(16, 16, "gl3-surface", 0L, 0L);
        if (window == 0L) {
            GLFW.glfwTerminate();
            return 0L;
        }
        GLFW.glfwMakeContextCurrent(window);
        return window;
    }

    @Override
    public String rendererDescription() {
        return GL11.glGetString(GL11.GL_RENDERER) + " / GL " + GL11.glGetString(GL11.GL_VERSION) + " (LWJGL 3)";
    }

    @Override
    public void close() {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL30.glDeleteFramebuffers(framebuffer);
        GL30.glDeleteRenderbuffers(depth);
        GL11.glDeleteTextures(colour);
        GL.setCapabilities(null);
        if (cglContext != 0L) {
            CGL.CGLSetCurrentContext(0L);
            CGL.CGLDestroyContext(cglContext);
        }
        if (window != 0L) {
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
