package com.dillon.starsectormarines.render2d.texture;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Turns on {@link TextureUsage} for a headless evidence run and writes its record to disk.
 *
 * <p>Asked for with {@code -Dmarines.textureUsage.dir=<dir>} (the Gradle property
 * {@code -PtextureUsage} sets it): the record lands in {@code <dir>/<label>-<pid>.json} and is
 * rewritten as it grows and once more at exit. {@code tools/texture_usage.py merge} folds those
 * files into the checked-in record. Without the property nothing is recorded and every hook stays
 * one volatile read.
 *
 * <p>Installed by the evidence entry points that are plain mains ({@code CreateSnapshotsCli},
 * {@code SceneEvidenceCli}) and, as an auto-detected JUnit extension, by every {@code Test} task,
 * which is how {@code commanderEvidence} and {@code renderEvidence} record.
 */
public final class TextureUsageCapture implements BeforeAllCallback {

    public static final String DIR_PROPERTY = "marines.textureUsage.dir";
    public static final String LABEL_PROPERTY = "marines.textureUsage.label";

    /**
     * {@code -Dmarines.textureUsage.only=true}: record usage and skip painting battle frames. Every
     * world draw is measured when it is collected, so the raster is waste for a measuring run; UI
     * screens still paint, because their images are measured as they are painted.
     */
    public static final String ONLY_PROPERTY = "marines.textureUsage.only";
    private static final boolean MEASURE_ONLY = Boolean.getBoolean(ONLY_PROPERTY);

    private static boolean installed;

    /** Whether this run only measures, leaving battle frames unpainted. */
    public static boolean measureOnly() {
        return MEASURE_ONLY && installed;
    }

    /** Starts recording under {@code label} if the run asked for it; true when it did. */
    public static synchronized boolean install(String label) {
        if (installed) return true;
        String dir = System.getProperty(DIR_PROPERTY);
        if (dir == null || dir.isBlank()) return false;
        String name = System.getProperty(LABEL_PROPERTY, label);
        Path out = Path.of(dir).toAbsolutePath().resolve(
                name.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + ProcessHandle.current().pid() + ".json");
        TextureUsage.enable(name, json -> write(out, json));
        Runtime.getRuntime().addShutdownHook(new Thread(TextureUsage::flush, "texture-usage-flush"));
        installed = true;
        System.out.println("Texture usage recorded to " + out);
        return true;
    }

    private static void write(Path out, String json) {
        try {
            Files.createDirectories(out.getParent());
            Path partial = out.resolveSibling(out.getFileName() + ".part");
            Files.writeString(partial, json);
            Files.move(partial, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write texture usage to " + out, e);
        }
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        install("test");
    }
}
