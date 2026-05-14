package com.dillon.starsectormarines.assets.pipeline;

import com.dillon.starsectormarines.assets.LoadedModel;
import com.dillon.starsectormarines.assets.ModelLoader;
import com.dillon.starsectormarines.assets.animation.BvhParser;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Build-time task that processes FBX model files into pre-processed .mlmodel binary format.
 * Invoked by Gradle's JavaExec task during the asset-pipeline build.
 *
 * <p>Usage: {@code java ProcessModelsTask <inputDir> <outputDir>}
 */
public final class ProcessModelsTask {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: ProcessModelsTask <inputDir> <outputDir>");
            System.exit(1);
        }

        Path inputDir = Path.of(args[0]);
        Path outputDir = Path.of(args[1]);

        if (!Files.isDirectory(inputDir)) {
            System.err.println("Input directory does not exist: " + inputDir);
            System.exit(1);
        }

        Files.createDirectories(outputDir);

        // Copy non-FBX resources (textures, etc.) to output so they're on core's classpath
        copyPassthroughResources(inputDir, outputDir);

        AssetConventionConfig conventions = AssetConventionConfig.load();

        int success = 0;
        int failed = 0;
        int skipped = 0;

        try (Stream<Path> walk = Files.walk(inputDir)) {
            var modelFiles = walk
                    .filter(p -> {
                        String name = p.toString().toLowerCase();
                        return name.endsWith(".fbx") || name.endsWith(".bvh");
                    })
                    .toList();

            System.out.printf("Found %d model files (FBX/BVH)%n", modelFiles.size());

            for (Path sourceFile : modelFiles) {
                Path relative = inputDir.relativize(sourceFile);
                String mlmodelName = relative.toString().replaceFirst("\\.[^.]+$", ".mlmodel");
                Path outputFile = outputDir.resolve(mlmodelName);

                // Skip if output exists and is newer than input
                if (Files.exists(outputFile)
                        && Files.getLastModifiedTime(outputFile).compareTo(Files.getLastModifiedTime(sourceFile)) >= 0) {
                    skipped++;
                    continue;
                }

                System.out.printf("  Processing: %s", relative);

                try {
                    String relativePath = relative.toString().replace('\\', '/');
                    Optional<LoadedModel> modelOpt = loadSource(relativePath);

                    if (modelOpt.isEmpty()) {
                        System.out.println(" -> FAILED (loader returned empty)");
                        failed++;
                        continue;
                    }

                    LoadedModel normalized = ConventionNormalizer.normalize(
                        modelOpt.get(), relativePath, conventions);

                    Files.createDirectories(outputFile.getParent());
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outputFile.toFile()))) {
                        ModelSerializer.write(normalized, bos);
                    }

                    long sizeKb = Files.size(outputFile) / 1024;
                    System.out.printf(" -> %s (%d KB)%n", outputFile.getFileName(), sizeKb);
                    success++;
                } catch (Exception e) {
                    System.out.printf(" -> ERROR: %s%n", e.getMessage());
                    failed++;
                }
            }
        }

        System.out.printf("%nDone! %d succeeded, %d failed, %d up-to-date%n", success, failed, skipped);
    }

    /**
     * Loads a model source by extension. FBX goes through Assimp; BVH goes
     * through {@link BvhParser} (no mesh, no materials — just skeleton + one
     * animation track).
     */
    private static Optional<LoadedModel> loadSource(String classpathPath) {
        String lower = classpathPath.toLowerCase();
        if (lower.endsWith(".bvh")) {
            BvhParser.ParsedBvh parsed = BvhParser.parse(classpathPath);
            return Optional.of(new LoadedModel(
                    Map.of(), List.of(),
                    parsed.skeleton(), List.of(parsed.animation())));
        }
        // FBX (and anything else Assimp can read). Retargeted animations
        // already have HumanIK bone names so no remap is applied here.
        return ModelLoader.load(classpathPath);
    }

    private static final Set<String> PASSTHROUGH_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".tga", ".bmp", ".hdr"
    );

    private static void copyPassthroughResources(Path inputDir, Path outputDir) throws IOException {
        try (Stream<Path> walk = Files.walk(inputDir)) {
            var resources = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return PASSTHROUGH_EXTENSIONS.stream().anyMatch(name::endsWith);
                    })
                    .toList();

            int copied = 0;
            for (Path resource : resources) {
                Path relative = inputDir.relativize(resource);
                Path dest = outputDir.resolve(relative);
                if (Files.exists(dest)
                        && Files.getLastModifiedTime(dest).compareTo(Files.getLastModifiedTime(resource)) >= 0) {
                    continue;
                }
                Files.createDirectories(dest.getParent());
                Files.copy(resource, dest, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }

            if (copied > 0) {
                System.out.printf("Copied %d texture/image files to output (%d up-to-date)%n", copied, resources.size() - copied);
            }
        }
    }
}
