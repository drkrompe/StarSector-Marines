package com.dillon.starsectormarines.assets.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Build-only, content-addressed encodes shared by independent worktrees. */
final class AudioEncodeCache {
    enum Result { ENCODED, RESTORED, UP_TO_DATE }

    @FunctionalInterface
    interface Encoder {
        void encode(Path source, Path destination) throws IOException, InterruptedException;
    }

    private final Path directory;

    AudioEncodeCache(Path directory) {
        this.directory = directory;
    }

    // Serialize calls through this cache; the file lock also serializes other Gradle processes.
    synchronized Result materialize(Path source, Path output, String recipe, Encoder encoder)
            throws IOException, InterruptedException {
        String key = key(source, recipe);
        Files.createDirectories(directory);
        Path cached = directory.resolve(key + ".ogg");
        try (FileChannel channel = FileChannel.open(directory.resolve(key + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.lock()) {
            boolean encoded = false;
            if (!Files.isRegularFile(cached) || Files.size(cached) == 0) {
                Path temporary = Files.createTempFile(directory, key + "-", ".ogg");
                try {
                    encoder.encode(source, temporary);
                    if (Files.size(temporary) == 0) {
                        throw new IOException("Encoder produced an empty file: " + source);
                    }
                    replace(temporary, cached);
                    encoded = true;
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            if (Files.isRegularFile(output) && Files.mismatch(cached, output) == -1) {
                return encoded ? Result.ENCODED : Result.UP_TO_DATE;
            }
            Files.createDirectories(output.toAbsolutePath().getParent());
            Path temporary = Files.createTempFile(output.toAbsolutePath().getParent(), ".audio-", ".ogg");
            try {
                Files.copy(cached, temporary, StandardCopyOption.REPLACE_EXISTING);
                replace(temporary, output);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return encoded ? Result.ENCODED : Result.RESTORED;
        }
    }

    private static String key(Path source, String recipe) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        digest.update(recipe.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void replace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
