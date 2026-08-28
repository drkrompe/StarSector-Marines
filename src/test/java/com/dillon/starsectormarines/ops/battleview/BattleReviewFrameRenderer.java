package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.headless.HeadlessUiRenderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;

/**
 * Renders one neutral-observer frame of a live battle through the GL-free
 * Java2D drain: the whole map fitted to the image, high-contrast faction
 * markers over the live entities, and a caption band.
 *
 * <p>Shared by every animated battle review — commander evidence for a mission
 * fixture, and focused behavior scenes in the snapshot catalog — so a second
 * kind of review does not arrive with a second camera fit and a second marker
 * palette. The image is explicitly an offline review artifact: it shows the
 * whole battlefield regardless of what either side believes, and is never
 * commander input or player-facing intelligence.
 */
public final class BattleReviewFrameRenderer {

    public static final int HEADER_HEIGHT = 30;

    private static final int CAPTION_MARGIN = 12;
    private static final int MAX_CAPTION_POINTS = 14;
    private static final int MIN_CAPTION_POINTS = 9;

    private static final Color MARINE_MARKER = new Color(68, 214, 255, 235);
    private static final Color DEFENDER_MARKER = new Color(255, 83, 83, 235);
    private static final Color CIVILIAN_MARKER = new Color(255, 218, 73, 235);
    /** In-flight ordnance a point-defence mount is allowed to engage. Its own colour because "a warhead is on the way" is the state the reader is watching for. */
    private static final Color ORDNANCE_MARKER = new Color(255, 150, 40, 240);

    /** GL-owned custom and ribbon decorations are deliberately absent — the Java2D drain cannot produce them. */
    private static final EnumSet<RenderLayer> REVIEW_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.VEHICLES, RenderLayer.DOODADS,
            RenderLayer.UNITS, RenderLayer.HAZARDS, RenderLayer.SMOKE,
            RenderLayer.DRONES, RenderLayer.OBJECTIVES, RenderLayer.COMPOUND,
            RenderLayer.CONVOY, RenderLayer.SHUTTLES);

    private final HeadlessUiRenderer renderer;
    private final int width;
    private final int height;

    public BattleReviewFrameRenderer(Path modRoot, int width, int height) {
        Path root = modRoot.toAbsolutePath().normalize();
        this.renderer = new HeadlessUiRenderer(
                new HeadlessBattleSceneRenderer(root, true), root);
        this.width = width;
        this.height = height;
    }

    public int width() { return width; }

    public int height() { return height; }

    /** One captioned frame of {@code simulation} as it stands right now. */
    public BufferedImage render(BattleSimulation simulation, String caption) throws IOException {
        BufferedImage image = renderer.renderHostPass(pass(simulation), width, height);
        annotate(image, simulation, caption);
        return image;
    }

    private void annotate(BufferedImage image, BattleSimulation simulation, String caption) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawUnitMarkers(graphics, simulation);
        drawOrdnanceMarkers(graphics, simulation);
        graphics.setColor(new Color(0, 0, 0, 205));
        graphics.fillRect(0, 0, image.getWidth(), HEADER_HEIGHT);
        graphics.setColor(Color.WHITE);
        graphics.setFont(captionFont(graphics, caption, image.getWidth() - 2 * CAPTION_MARGIN));
        graphics.drawString(caption, CAPTION_MARGIN, 20);
        graphics.dispose();
    }

    private void drawUnitMarkers(Graphics2D graphics, BattleSimulation simulation) {
        BattleCamera camera = cameraFor(width, height, simulation);
        float radius = Math.max(2.5f, Math.min(4.5f, camera.cellPxSize() * 0.7f));
        graphics.setStroke(new BasicStroke(1.25f));
        for (int index = 0; index < simulation.getRoster().liveCount(); index++) {
            long entity = simulation.getRoster().get(index);
            Color color = markerColor(simulation.identity().faction(entity));
            if (color == null) continue;
            float centerX = camera.cellToScreenX(simulation.world().renderX(entity));
            float centerY = height - camera.cellToScreenY(simulation.world().renderY(entity));
            int diameter = Math.round(radius * 2f);
            int left = Math.round(centerX - radius);
            int top = Math.round(centerY - radius);
            graphics.setColor(new Color(0, 0, 0, 220));
            graphics.drawOval(left, top, diameter, diameter);
            graphics.setColor(color);
            graphics.fillOval(left + 1, top + 1,
                    Math.max(1, diameter - 2), Math.max(1, diameter - 2));
        }
    }

    /**
     * Marks every warhead currently in the air that a point-defence mount may
     * engage. The Java2D drain cannot produce the GL projectile pass, so
     * without this a review of an artillery exchange shows explosions with
     * nothing travelling between the gun and the ground — and a review of an
     * <em>interception</em> shows nothing at all, since the whole event is a
     * round that stops existing. A diamond rather than a disc, so ordnance is
     * never confused with a body at marker scale.
     */
    private void drawOrdnanceMarkers(Graphics2D graphics, BattleSimulation simulation) {
        BattleCamera camera = cameraFor(width, height, simulation);
        int reach = Math.round(Math.max(3f, Math.min(5.5f, camera.cellPxSize() * 0.55f)));
        graphics.setStroke(new BasicStroke(1.5f));
        for (Projectile round : simulation.snapshotActiveProjectiles()) {
            if (!round.pointDefenseTarget) continue;
            int centerX = Math.round(camera.cellToScreenX(round.currentX()));
            int centerY = Math.round(height - camera.cellToScreenY(round.currentY()));
            int[] xs = {centerX, centerX + reach, centerX, centerX - reach};
            int[] ys = {centerY - reach, centerY, centerY + reach, centerY};
            graphics.setColor(ORDNANCE_MARKER);
            graphics.fillPolygon(xs, ys, 4);
            graphics.setColor(new Color(0, 0, 0, 220));
            graphics.drawPolygon(xs, ys, 4);
        }
    }

    /**
     * Largest caption font that still fits the frame, down to
     * {@link #MIN_CAPTION_POINTS}. A review caption carries the state that
     * explains the frame, so silently running it off the right edge loses
     * exactly the part a reader came for; narrowing the type is the cheaper
     * loss.
     */
    private static Font captionFont(Graphics2D graphics, String caption, int available) {
        for (int points = MAX_CAPTION_POINTS; points > MIN_CAPTION_POINTS; points--) {
            Font candidate = new Font(Font.SANS_SERIF, Font.BOLD, points);
            if (graphics.getFontMetrics(candidate).stringWidth(caption) <= available) {
                return candidate;
            }
        }
        return new Font(Font.SANS_SERIF, Font.BOLD, MIN_CAPTION_POINTS);
    }

    private static Color markerColor(Faction faction) {
        return switch (faction) {
            case MARINE -> MARINE_MARKER;
            case DEFENDER -> DEFENDER_MARKER;
            case CIVILIAN -> CIVILIAN_MARKER;
            default -> null;
        };
    }

    private static BattleSceneHostPass pass(BattleSimulation simulation) {
        return new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport viewport, float alphaMult) {
                BattleCamera camera = cameraFor(viewport.width(), viewport.height(), simulation);
                RenderContext context = new RenderContext(simulation, camera,
                        null, alphaMult, 0f, false,
                        new HighlightOverlay(), new Selection(),
                        BattleRenderHostProfile.EMBEDDED_SCENE);
                return new BattleSceneFrame(context, REVIEW_LAYERS);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                throw new IllegalStateException("Visual review requires the headless scene drain");
            }
        };
    }

    /** Fits the whole grid under the caption band, so a frame never crops the battlefield. */
    private static BattleCamera cameraFor(float viewportWidth, float viewportHeight,
                                          BattleSimulation simulation) {
        int gridWidth = simulation.getGrid().getWidth();
        int gridHeight = simulation.getGrid().getHeight();
        float mapHeight = viewportHeight - HEADER_HEIGHT;
        float cellPx = Math.min(viewportWidth / gridWidth, mapHeight / gridHeight);
        BattleCamera camera = new BattleCamera(gridWidth, gridHeight);
        camera.setViewport(0f, 0f, viewportWidth, mapHeight, cellPx);
        return camera;
    }
}
