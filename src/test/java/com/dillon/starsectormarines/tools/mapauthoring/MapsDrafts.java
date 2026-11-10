package com.dillon.starsectormarines.tools.mapauthoring;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Where the Maps page keeps a map's edits until the owned-map document format ({@code owned-maps}) lands: one file per
 * map under {@code build/map-authoring/}, never under mod/, replaced atomically so a failed write leaves the last one.
 */
final class MapsDrafts {

    private MapsDrafts() { }

    static Path write(Path projectRoot, MapSketch sketch) throws Exception {
        Path dir = projectRoot.resolve("build/map-authoring");
        Files.createDirectories(dir);
        Path target = dir.resolve(sketch.base().replace('/', '-') + ".edits.json");
        Path temp = Files.createTempFile(dir, "map-", ".tmp");
        try {
            MapSketch.read(sketch.json());
            Files.writeString(temp, sketch.json().toString(2) + "\n");
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
        return target;
    }
}
