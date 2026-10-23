// Opens an LWJGL 2 context the way HeadlessGl does (pbuffer) or with a window (display), and reports what it got:
// renderer, GL 3.0 / FBO support, a #version 120 shader compile, and the cost of reading a 1920x1080 frame back.
// A probe for gpu-review-drain.md, run by tools/gl-probe/gl-probe-mac.sh; not part of any build.
import org.lwjgl.opengl.*;
import org.lwjgl.BufferUtils;
import java.nio.ByteBuffer;

public class GlProbe {
    public static void main(String[] args) throws Exception {
        System.out.println("java " + System.getProperty("java.version") + " " + System.getProperty("os.arch"));
        String mode = args.length > 0 ? args[0] : "pbuffer";
        if (mode.equals("pbuffer")) {
            int caps = Pbuffer.getCapabilities();
            System.out.println("pbuffer caps=" + caps + " supported=" + ((caps & Pbuffer.PBUFFER_SUPPORTED) != 0));
            Pbuffer p = new Pbuffer(1920, 1080, new PixelFormat(8, 8, 0), null);
            p.makeCurrent();
            GLContext.useContext(p);
        } else {
            Display.setDisplayMode(new DisplayMode(64, 64));
            Display.create(new PixelFormat(8, 8, 0));
        }
        System.out.println("renderer=" + GL11.glGetString(GL11.GL_RENDERER) + " / " + GL11.glGetString(GL11.GL_VERSION));
        ContextCapabilities c = GLContext.getCapabilities();
        System.out.println("GL20=" + c.OpenGL20 + " GL30=" + c.OpenGL30 + " ARB_fbo=" + c.GL_ARB_framebuffer_object + " EXT_fbo=" + c.GL_EXT_framebuffer_object);
        int sh = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(sh, "#version 120\nuniform sampler2D t; void main(){ gl_FragColor = texture2D(t, gl_TexCoord[0].xy) + vec4(0.25); }");
        GL20.glCompileShader(sh);
        System.out.println("shader120 compiled=" + GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS));
        try {
            int fbo = GL30.glGenFramebuffers();
            System.out.println("GL30.glGenFramebuffers -> " + fbo);
        } catch (Throwable t) { System.out.println("GL30 fbo failed: " + t); }
        GL11.glClearColor(0.2f, 0.4f, 0.6f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glFinish();
        ByteBuffer px = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(0, 0, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
        System.out.println("readback=" + (px.get(0) & 255) + "," + (px.get(1) & 255) + "," + (px.get(2) & 255));
        long t0 = System.nanoTime();
        ByteBuffer big = BufferUtils.createByteBuffer(1920 * 1080 * 4);
        for (int i = 0; i < 20; i++) { GL11.glClear(GL11.GL_COLOR_BUFFER_BIT); GL11.glReadPixels(0, 0, 1920, 1080, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, big); }
        System.out.printf("1080p readback %.2f ms/frame%n", (System.nanoTime() - t0) / 20e6);
        System.out.println("OK");
    }
}
