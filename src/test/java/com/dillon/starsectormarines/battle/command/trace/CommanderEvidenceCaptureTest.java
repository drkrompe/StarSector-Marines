package com.dillon.starsectormarines.battle.command.trace;

import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the capture owes when the run around it fails: the GIF is released.
 *
 * <p>The harness deletes its staging tree in a {@code finally} block, and a
 * {@code finally} that throws discards the exception already in flight. So a
 * capture that leaves {@code review.gif} open does not merely leak a handle —
 * it replaces the real failure with "the process cannot access the file", and
 * a thirteen-minute Conquest run reported nothing about what went wrong.
 *
 * <p>The frames come from a stub rather than the battle renderer: the question
 * here is which handles are shut, and drawing a real frame answers none of it
 * while costing a renderer and its art.
 */
class CommanderEvidenceCaptureTest {

    private String previousCadence;
    private String previousAnnotations;
    private SceneWorld world;

    @BeforeEach
    void enableCapture() {
        previousCadence = System.getProperty(
                CommanderEvidenceCapture.CADENCE_PROPERTY);
        previousAnnotations = System.getProperty(
                CommanderEvidenceCapture.ANNOTATIONS_PROPERTY);
        System.setProperty(CommanderEvidenceCapture.CADENCE_PROPERTY, "1");
        // Annotations read the whole battle for compounds and landing zones,
        // which this scene has none of and this question does not involve.
        System.setProperty(CommanderEvidenceCapture.ANNOTATIONS_PROPERTY, "false");
        world = SceneBuilder.openGround(24, 12)
                .unit("marine", Faction.MARINE, UnitType.MARINE, 2, 2)
                .unit("defender", Faction.DEFENDER, UnitType.MARINE, 21, 9)
                .build();
    }

    @AfterEach
    void restoreProperties() {
        if (world != null) world.sim().close();
        restore(CommanderEvidenceCapture.CADENCE_PROPERTY, previousCadence);
        restore(CommanderEvidenceCapture.ANNOTATIONS_PROPERTY, previousAnnotations);
    }

    @Test
    void closeReleasesTheGifWhenTheClosingFrameFails(@TempDir Path temp)
            throws Exception {
        AtomicInteger frames = new AtomicInteger();
        CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(
                temp, "run-a", sim(), (simulation, caption, marks) -> {
                    if (frames.incrementAndGet() > 1) {
                        throw new IllegalStateException("renderer failed");
                    }
                    return blankFrame();
                });

        advanceOneTick();

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, capture::close);
        assertEquals("renderer failed", failure.getMessage());
        assertEquals(2, frames.get(), "the closing frame must have been attempted");
        assertReleased(temp, "run-a");
    }

    @Test
    void openReleasesTheGifWhenTheOpeningFrameFails(@TempDir Path temp)
            throws Exception {
        BattleSimulation sim = sim();
        assertThrows(IllegalStateException.class,
                () -> CommanderEvidenceCapture.open(temp, "run-b", sim,
                        (simulation, caption, marks) -> {
                            throw new IllegalStateException("renderer failed");
                        }));

        // A constructor that throws hands the caller no capture to close, so
        // the opening frame has to release the encoder itself.
        assertReleased(temp, "run-b");
    }

    @Test
    void anOrdinaryCloseWritesTheManifestAndReleasesTheGif(@TempDir Path temp)
            throws Exception {
        CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(
                temp, "run-c", sim(),
                (simulation, caption, marks) -> blankFrame());

        advanceOneTick();
        capture.afterAdvance();
        capture.close();

        Path output = temp.resolve("visuals").resolve("run-c");
        assertTrue(Files.exists(output.resolve("manifest.json")));
        assertTrue(Files.exists(output.resolve("frames")));
        assertReleased(temp, "run-c");
    }

    @Test
    void closingTwiceIsHarmless(@TempDir Path temp) throws Exception {
        CommanderEvidenceCapture capture = CommanderEvidenceCapture.open(
                temp, "run-d", sim(),
                (simulation, caption, marks) -> blankFrame());
        capture.close();
        capture.close();
        assertReleased(temp, "run-d");
    }

    /**
     * The whole point: nothing holds the review file, so the harness's own
     * cleanup can remove the tree instead of failing over the run's report.
     */
    private static void assertReleased(Path reportRoot, String fixtureId)
            throws Exception {
        Path gif = reportRoot.resolve("visuals").resolve(fixtureId)
                .resolve("review.gif");
        assertTrue(Files.exists(gif), "the capture must have opened " + gif);
        assertTrue(Files.deleteIfExists(gif),
                "close must release review.gif");
        EvidenceCleanup.deleteTree(reportRoot.resolve("visuals"));
    }

    private BattleSimulation sim() {
        return world.sim();
    }

    private void advanceOneTick() {
        int before = sim().getSimTickIndex();
        sim().advance(BattleSimulation.TICK_DT);
        assertEquals(before + 1, sim().getSimTickIndex(),
                "a closing frame is only taken for a tick that is new");
    }

    private static BufferedImage blankFrame() {
        return new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    }

    private static void restore(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
