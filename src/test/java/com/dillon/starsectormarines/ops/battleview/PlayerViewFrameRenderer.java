package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.ui.highlight.HighlightOverlay;
import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
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
 * Renders one frame of a live battle <b>as the player sees it</b>: the ordinary
 * world passes with the fog overlay in its shipped place between terrain and
 * units, roofs closed over interiors the player's vision has not reached, and
 * markers drawn only for units the fog service says are currently visible.
 *
 * <p>Deliberately <em>not</em> {@link BattleReviewFrameRenderer}, which is the
 * neutral-observer camera: that one shows the whole battlefield regardless of
 * what anyone believes, which is the right artifact for reviewing a mission's
 * balance and exactly the wrong one for evidence about visibility. A reveal is
 * only interesting against what was hidden a moment earlier, so this renderer
 * is the player's picture and nothing else — it draws its markers straight off
 * {@link FogOfWarService#getUnitVisibility}, the same byte the shipped unit
 * pass gates on.
 */
public final class PlayerViewFrameRenderer {

    public static final int HEADER_HEIGHT = 30;

    private static final int CAPTION_MARGIN = 12;
    private static final int MAX_CAPTION_POINTS = 14;
    private static final int MIN_CAPTION_POINTS = 9;

    private static final Color MARINE_MARKER = new Color(68, 214, 255, 240);
    private static final Color DEFENDER_MARKER = new Color(255, 83, 83, 240);
    private static final Color CIVILIAN_MARKER = new Color(255, 218, 73, 240);

    /**
     * The player's world passes. FOG and ROOFS are the two the neutral review
     * omits, and they are the two this artifact exists to show.
     */
    private static final EnumSet<RenderLayer> PLAYER_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.VEHICLES, RenderLayer.DOODADS,
            RenderLayer.FOG, RenderLayer.UNITS, RenderLayer.HAZARDS,
            RenderLayer.SMOKE, RenderLayer.ROOFS, RenderLayer.DRONES,
            RenderLayer.OBJECTIVES, RenderLayer.COMPOUND, RenderLayer.CONVOY);

    private final HeadlessUiRenderer renderer;
    private final int width;
    private final int height;

    public PlayerViewFrameRenderer(Path modRoot, int width, int height) {
        Path root = modRoot.toAbsolutePath().normalize();
        this.renderer = new HeadlessUiRenderer(
                new HeadlessBattleSceneRenderer(root, true), root);
        this.width = width;
        this.height = height;
    }

    public int width() { return width; }

    public int height() { return height; }

    /** One captioned frame of what the player can presently see. */
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
        drawVisibleUnitMarkers(graphics, simulation);
        graphics.setColor(new Color(0, 0, 0, 205));
        graphics.fillRect(0, 0, image.getWidth(), HEADER_HEIGHT);
        graphics.setColor(Color.WHITE);
        graphics.setFont(captionFont(graphics, caption, image.getWidth() - 2 * CAPTION_MARGIN));
        graphics.drawString(caption, CAPTION_MARGIN, 20);
        graphics.dispose();
    }

    /**
     * A marker per unit the player can currently see, and none for the rest.
     * The visibility byte is read by dense index because that is how the fog
     * service keys it and how the shipped renderer reads it — using anything
     * else here would make this a picture of the simulation rather than of the
     * player's screen.
     */
    private void drawVisibleUnitMarkers(Graphics2D graphics, BattleSimulation simulation) {
        BattleCamera camera = cameraFor(width, height, simulation);
        FogOfWarService fog = simulation.getFogOfWar();
        float radius = Math.max(2.5f, Math.min(4.5f, camera.cellPxSize() * 0.7f));
        graphics.setStroke(new BasicStroke(1.25f));
        for (int index = 0; index < simulation.getRoster().liveCount(); index++) {
            if (fog.getUnitVisibility(index) != FogOfWarService.VIS_VISIBLE) continue;
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
                return new BattleSceneFrame(context, PLAYER_LAYERS);
            }

            @Override
            public void draw(CanvasHostViewport viewport, float alphaMult) {
                throw new IllegalStateException("Player-view evidence requires the headless drain");
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
