package com.dillon.starsectormarines.tools.gl3;

import java.io.IOException;
import java.io.InputStream;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_void;

/**
 * Writes a copy of LWJGL 3's {@code org.lwjgl.opengl.GL11} with the LWJGL 2 overloads the mod's draw batches call
 * added to it, for the headless LWJGL 3 classpath only.
 *
 * <p>The mod is compiled against LWJGL 2, and LWJGL 3 kept nearly every {@code GL11} entry point's name and
 * descriptor. It dropped the type-less client-array forms, {@code gl*Pointer(size, stride, FloatBuffer)}, which
 * {@code QuadBatch}, {@code SolidQuadBatch} and {@code LineBatch} use on every flush,
 * {@code glGetInteger(int, IntBuffer)}, and {@code glTexParameter(int, int, FloatBuffer)} (the headless sprite
 * tokens' border colour). Changing the batches to a form both libraries share would change what the
 * game's own GL bridge mods see, so the shipped code stays as it is and LWJGL 3 grows the old forms instead, each a
 * one-line call into the form it kept. The patched class goes on the classpath ahead of the LWJGL 3 jar.
 *
 * <p>Arguments: the {@code lwjgl-opengl} jar, and the directory to write {@code org/lwjgl/opengl/GL11.class} under.
 */
public final class PatchLwjgl3Gl11 {

    private static final String ENTRY = "org/lwjgl/opengl/GL11.class";
    private static final int GL_FLOAT = 0x1406;

    private PatchLwjgl3Gl11() { }

    public static void main(String[] args) throws IOException {
        Path jar = Path.of(args[0]);
        Path out = Path.of(args[1]).resolve(ENTRY);
        byte[] original;
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(ENTRY);
            if (entry == null) throw new IOException(jar + " has no " + ENTRY);
            try (InputStream in = zip.getInputStream(entry)) {
                original = in.readAllBytes();
            }
        }
        ClassFile files = ClassFile.of();
        ClassModel model = files.parse(original);
        ClassDesc gl11 = model.thisClass().asSymbol();
        ClassDesc floatBuffer = ClassDesc.of("java.nio.FloatBuffer");
        ClassDesc intBuffer = ClassDesc.of("java.nio.IntBuffer");
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        byte[] patched = files.transformClass(model, ClassTransform.endHandler(builder -> {
            for (String name : List.of("glVertexPointer", "glTexCoordPointer", "glColorPointer")) {
                MethodTypeDesc legacy = MethodTypeDesc.of(CD_void, CD_int, CD_int, floatBuffer);
                MethodTypeDesc kept = MethodTypeDesc.of(CD_void, CD_int, CD_int, CD_int, floatBuffer);
                builder.withMethodBody(name, legacy, flags, code -> code
                        .iload(0).sipush(GL_FLOAT).iload(1).aload(2)
                        .invokestatic(gl11, name, kept)
                        .return_());
            }
            MethodTypeDesc texParameter = MethodTypeDesc.of(CD_void, CD_int, CD_int, floatBuffer);
            builder.withMethodBody("glTexParameter", texParameter, flags, code -> code
                    .iload(0).iload(1).aload(2)
                    .invokestatic(gl11, "glTexParameterfv", texParameter)
                    .return_());
            MethodTypeDesc getInteger = MethodTypeDesc.of(CD_void, CD_int, intBuffer);
            builder.withMethodBody("glGetInteger", getInteger, flags, code -> code
                    .iload(0).aload(1)
                    .invokestatic(gl11, "glGetIntegerv", getInteger)
                    .return_());
        }));
        Files.createDirectories(out.getParent());
        Files.write(out, patched);
        System.out.println("patched " + ENTRY + " -> " + out);
    }
}
