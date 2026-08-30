package com.dillon.starsectormarines.battle.sim;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * No unseeded random source anywhere under {@code battle/}.
 *
 * <p>{@link BattleDeterminismTest} proves the sim replays from its seed today. This
 * proves it stays that way: a single {@code ThreadLocalRandom.current()} added to a
 * behavior months from now would break replay everywhere while every other test kept
 * passing, because nothing else observes the property globally.
 *
 * <p>The fix when this fails is always the same — draw from
 * {@code BattleSimulation.random()}, which every system and every {@code BattleView}
 * holder can already reach.
 */
public class NoUnseededRandomTest {

    /** Sources of randomness that cannot be seeded, and so cannot be replayed. */
    private static final String[] BANNED = {
            "ThreadLocalRandom",
            "Math.random(",
            // A no-arg Random seeds itself from the system clock, which is exactly the
            // problem in a different shape.
            "new Random()",
    };

    /**
     * Map generation is seeded through its own explicit {@code Random(seed)} instances
     * and is not part of the sim's tick stream, so it is out of scope here — it was
     * already reproducible before this rule existed.
     */
    private static final Path BATTLE = Paths.get(
            "src/main/java/com/dillon/starsectormarines/battle");

    /**
     * Presentation layers, exempt on purpose.
     *
     * <p>The rule protects the <b>outcome</b> of a battle, and none of these can change
     * it: particle jitter, overlay sprites, and which radio bark plays are read by the
     * renderer and the audio layer, never by the sim. They are also constructed by hosts
     * that hold no seed. Replaying a battle reproduces the fight exactly; the smoke will
     * curl differently, and that is an acceptable price for not threading a stream
     * through the render tier.
     *
     * <p>An entry here is a claim that the file cannot affect sim state. Do not add one
     * to silence a failure — check first.
     */
    private static final String[] PRESENTATION_ONLY = {
            "audio/BattleRadioChatter.java",
            "combat/fx/ImpactFx.java",
    };

    @Test
    public void nothingInTheSimDrawsFromAnUnseededSource() {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(BATTLE)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String relative = BATTLE.relativize(p).toString()
                        .replace(File.separatorChar, '/');
                for (String exempt : PRESENTATION_ONLY) {
                    if (relative.equals(exempt)) return;
                }
                String body = read(p);
                for (String banned : BANNED) {
                    if (!body.contains(banned)) continue;
                    // The rule's own statement of itself, in BattleSimulation's javadoc
                    // and BattleView's, is not a violation of it.
                    if (onlyInComments(body, banned)) continue;
                    offenders.add(BATTLE.relativize(p) + " uses " + banned);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(offenders.isEmpty(),
                "the battle sim must draw only from its seeded stream "
                        + "(BattleSimulation.random()); found: " + offenders);
    }

    /** True when every occurrence sits on a comment line. */
    private static boolean onlyInComments(String body, String needle) {
        for (String line : body.split("\n")) {
            if (!line.contains(needle)) continue;
            String trimmed = line.trim();
            boolean comment = trimmed.startsWith("//") || trimmed.startsWith("*")
                    || trimmed.startsWith("/*");
            if (!comment) return false;
        }
        return true;
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
