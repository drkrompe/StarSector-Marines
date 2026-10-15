package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The same fight, played and photographed twice from scratch, comes out as the same pixels. The frame carries
 * particle jitter (seeded), objective pulses (pinned to the review clock) and a feed's whole history, and any of them
 * reading the wall clock or an unseeded generator would show up here as two frames that differ.
 */
class ReviewFrameDeterminismTest {

    private static final int TICKS = 900;
    private static final int PIXELS_W = 640;
    private static final int PIXELS_H = 360;

    private static BufferedImage play() throws IOException {
        Path root = Path.of("mod").toAbsolutePath().normalize();
        try {
            SceneRegistries.installArmoury(Path.of(".").toAbsolutePath().normalize());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
        SceneBuilder builder = SceneBuilder.openGround(60, 30).missionCompletion(false);
        builder.squad("marines").faction(Faction.MARINE).type(UnitType.MARINE).size(4)
                .inFile(24, 15, 1, 0).kit(1L).health(100_000f)
                .assigned(id -> ObjectiveAssignment.defendArea(id, 26, 15)).done();
        builder.squad("defenders").faction(Faction.DEFENDER).type(UnitType.MARINE_RED).size(4)
                .inFile(32, 15, 1, 0).kit(2L).health(100_000f).stationary().done();
        SceneWorld world = builder.build();
        BattleSimulation sim = world.sim();
        sim.random().setSeed(20261016L);

        ReviewImpactFx fx = new ReviewImpactFx(root);
        // Photographed on the tick a round lands, when its impact particles are alive.
        boolean landed = false;
        for (int tick = 0; tick < TICKS && !landed; tick++) {
            sim.advance(BattleSimulation.TICK_DT);
            fx.feed(sim);
            landed = sim.getShotsExpiredThisFrame().stream().anyMatch(shot -> shot.impacts());
        }
        assertTrue(landed, "no round landed in " + TICKS + " ticks");
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(root, PIXELS_W, PIXELS_H,
                26f, 15f, 30f).withImpactEffects();
        BufferedImage bare = renderer.render(sim, "determinism", ReviewAnnotations.NONE, null);
        BufferedImage withFx = renderer.render(sim, "determinism", ReviewAnnotations.NONE, fx);
        assertTrue(differs(bare, withFx), "the feed drew nothing, so the jitter it carries was never exercised");
        return withFx;
    }

    private static boolean differs(BufferedImage a, BufferedImage b) {
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) if (a.getRGB(x, y) != b.getRGB(x, y)) return true;
        }
        return false;
    }

    @Test
    void theSameBattlePhotographedTwiceIsTheSamePicture() throws IOException {
        BufferedImage first = play();
        BufferedImage second = play();

        int different = 0;
        int lit = 0;
        for (int y = 0; y < PIXELS_H; y++) {
            for (int x = 0; x < PIXELS_W; x++) {
                if (first.getRGB(x, y) != second.getRGB(x, y)) different++;
                if ((first.getRGB(x, y) & 0xFFFFFF) != 0) lit++;
            }
        }
        assertEquals(0, different, "pixels differing between two runs");
        assertTrue(lit > PIXELS_W * PIXELS_H / 4, "the frame is mostly empty, so equality proves little");
    }
}
