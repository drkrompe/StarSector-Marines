package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.render2d.BattleCamera;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;

/**
 * What draws a review frame's scene: the production layers collected for one camera and turned into pixels. The
 * caption, markers, annotations and impact particles are painted over the result on the CPU whichever backend drew
 * it, so a backend owns the world and nothing else.
 *
 * <p>Chosen per run with {@code -Dsnapshot.backend=java2d|gl} (Gradle forwards every {@code snapshot.*} property).
 * {@code java2d} is the default and the deterministic reference; {@code gl} draws through the shipping renderer on
 * the GPU and falls back to {@code java2d}, saying so once, where this machine cannot give a context.
 */
interface ReviewSceneBackend {

    String PROPERTY = "snapshot.backend";

    /**
     * One frame's world: who is drawn, through which camera, and what a review feed adds ahead of each layer;
     * {@code selection} is what the player has selected, for a mark drawn only on a selected squad, or null for none.
     */
    record Scene(BattleSimulation simulation, BattleCamera camera, EnumSet<RenderLayer> layers,
                 float clockSeconds, ReviewImpactFx impacts, Selection selection) { }

    /** The scene as a {@code width}x{@code height} image, top row first. */
    BufferedImage draw(Scene scene, int width, int height);

    /** The backend this run asked for, or the Java2D one where that is not available here. */
    static ReviewSceneBackend select(Path modRoot) {
        String asked = System.getProperty(PROPERTY, "java2d").trim().toLowerCase(Locale.ROOT);
        return switch (asked) {
            case "java2d" -> new Java2dReviewBackend(modRoot);
            case "gl" -> {
                GlReviewDrain gpu = GlReviewDrain.shared(modRoot);
                yield gpu != null ? gpu : new Java2dReviewBackend(modRoot);
            }
            default -> throw new IllegalArgumentException(
                    "-D" + PROPERTY + "=" + asked + ": expected java2d or gl");
        };
    }

    /** True when this run draws on the GPU; a review feed then gives its own sprite-backed commands GL tokens. */
    static GlReviewDrain gpu(Path modRoot) {
        return "gl".equalsIgnoreCase(System.getProperty(PROPERTY, "java2d").trim())
                ? GlReviewDrain.shared(modRoot) : null;
    }
}
