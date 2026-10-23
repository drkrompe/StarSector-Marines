package com.dillon.starsectormarines.render2d.texture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every mod texture load goes through {@link ModTextures}, and so through the ledger
 * ({@code battle-textures.md}, law 4). A load that goes round it is invisible to the reference
 * count: a battle that later acquires the same path believes it is the texture's only holder and
 * unloads it from under the screen that loaded it.
 */
class TextureLoadsGoThroughTheLedgerTest {

    /** The two classes that stand between the mod and the game's store. */
    private static final Set<String> ALLOWED = Set.of("ModTextures.java", "GameTextureBackend.java");

    @Test
    void noModCodeLoadsOrUnloadsATextureRoundTheLedger() throws IOException {
        List<String> offenders;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            offenders = files.parallel()
                    .filter(f -> f.toString().endsWith(".java"))
                    .filter(f -> !ALLOWED.contains(f.getFileName().toString()))
                    .filter(TextureLoadsGoThroughTheLedgerTest::loadsRoundTheLedger)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
        assertEquals(List.of(), offenders);
    }

    /** Read as Latin-1: the calls searched for are ASCII, and decoding UTF-8 is most of the cost. */
    private static boolean loadsRoundTheLedger(Path file) {
        try {
            String source = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
            return source.contains("getSettings().loadTexture(") || source.contains(".unloadTexture(");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
