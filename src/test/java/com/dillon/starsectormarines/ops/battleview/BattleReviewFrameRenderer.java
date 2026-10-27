package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.ui.picking.Selection;
import com.dillon.starsectormarines.battle.combat.fx.SmokingWreck;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.render2d.BattleCamera;

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
 * Renders one neutral-observer frame of a live battle through the chosen
 * {@link ReviewSceneBackend} (Java2D unless a run asks for the GPU): the whole map fitted to the image, high-contrast faction
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

    /**
     * Cells across a 1920-pixel frame at the game's play zoom: 32 px a cell. The framing a player sees a fight at,
     * so the one a look frame shown to the owner is drawn at.
     */
    public static final float PLAY_ZOOM_CELLS = 60f;

    /**
     * The animation clock a review frame is drawn at. Pinned rather than read from the wall, so the same battle state
     * draws the same pixels twice; zero is also what the shield dome and fort buildings already saw here.
     */
    static final float REVIEW_CLOCK_SECONDS = 0f;

    private float clockSeconds = REVIEW_CLOCK_SECONDS;
    /** What the player has selected while this renderer draws, or null for nothing. */
    private Selection selection;
    private static final int CAPTION_MARGIN = 12;
    private static final int MAX_CAPTION_POINTS = 14;
    private static final int MIN_CAPTION_POINTS = 9;

    private static final Color MARINE_MARKER = new Color(68, 214, 255, 235);
    private static final Color DEFENDER_MARKER = new Color(255, 83, 83, 235);
    private static final Color CIVILIAN_MARKER = new Color(255, 218, 73, 235);
    /** In-flight ordnance a point-defence mount is allowed to engage. Its own colour because "a warhead is on the way" is the state the reader is watching for. */
    private static final Color ORDNANCE_MARKER = new Color(255, 150, 40, 240);
    /** Burning wreckage. Alpha is set per wreck from how much of its life is left. */
    private static final Color WRECK_MARKER = new Color(255, 96, 24, 255);

    /**
     * GL-owned custom and ribbon decorations are deliberately absent — the Java2D drain cannot produce them. For the
     * same reason {@link RenderLayer#MISSION_MARKERS} is absent: its cards are a solid plate with their text drawn as
     * custom labels, so headless they came out as large empty black panels across the map, hiding what the frame is
     * for. Places are named by the frame's own annotations; the live HUD is unchanged.
     */
    static final EnumSet<RenderLayer> REVIEW_LAYERS = EnumSet.of(
            RenderLayer.GROUND, RenderLayer.DECALS, RenderLayer.CREEP, RenderLayer.VEHICLES, RenderLayer.DOODADS,
            RenderLayer.UNITS, RenderLayer.HAZARDS, RenderLayer.SMOKE,
            RenderLayer.DRONES, RenderLayer.OBJECTIVES, RenderLayer.COMPOUND,
            RenderLayer.CONVOY, RenderLayer.SHUTTLES, RenderLayer.NIGHT_LIGHTING, RenderLayer.AIRCRAFT_LIGHTS);

    private final ReviewSceneBackend backend;
    private final int width;
    private final int height;
    private float focusX = Float.NaN, focusY, focusWidth;
    private boolean impactEffects;
    private boolean unitShadows;
    private boolean roofs;
    private boolean missionMarkers;
    private float coverLeft = Float.POSITIVE_INFINITY, coverBottom = Float.POSITIVE_INFINITY;
    private boolean presentation;

    public BattleReviewFrameRenderer(Path modRoot, int width, int height) {
        this.backend = ReviewSceneBackend.select(modRoot.toAbsolutePath().normalize());
        this.width = width;
        this.height = height;
    }

    /** Camera-only close view, collected through the same production layers. */
    public BattleReviewFrameRenderer(Path modRoot, int width, int height, float focusX, float focusY, float cellsAcross) {
        this(modRoot, width, height);
        this.focusX = focusX; this.focusY = focusY; this.focusWidth = cellsAcross;
    }

    /**
     * The owner-facing look preset: a camera on {@code (focusX, focusY)} {@code cellsAcross} wide, with everything the
     * live screen draws that a review frame can (shots, launch, trail, contrail, smoke, impact and aftermath effects,
     * the sun's body shadows, roofs) and nothing it does not: no faction markers, no ordnance diamonds, no wreck rings,
     * no annotations and no caption band. Verdict scenes keep the diagnostic framing; a picture put in front of the
     * owner to judge how something looks comes from this one (see {@code LookReview}).
     */
    public static BattleReviewFrameRenderer look(Path modRoot, int width, int height,
                                                 float focusX, float focusY, float cellsAcross) {
        BattleReviewFrameRenderer renderer = new BattleReviewFrameRenderer(modRoot, width, height,
                focusX, focusY, cellsAcross);
        return renderer.asPresentation().withImpactEffects().withUnitShadows().withRoofs();
    }

    /**
     * Draws the battle as a player would see it, for a look review rather than a behaviour one: no caption band, no
     * unit, wreck or ordnance markers and no annotations, and a focused camera fills the whole frame. Markers are
     * how a reader follows bodies across a whole-map frame; on a close look at art they read as debug marks.
     * {@link #look} is this with the effects, shadows and roofs a look frame wants turned on as well.
     */
    public BattleReviewFrameRenderer asPresentation() {
        this.presentation = true;
        return this;
    }

    /**
     * Opts this renderer into the shot and impact-effect layers on a whole-map
     * framing. They are off by default because most reviews are about where
     * bodies are, and a blast drawn over the bodies would hide the evidence;
     * a scene whose subject is the landing itself asks for them.
     */
    public BattleReviewFrameRenderer withImpactEffects() {
        this.impactEffects = true;
        return this;
    }

    /**
     * Opts this renderer into the sun's body shadows (marines, aircraft and
     * hovering warships). Off by default for the same reason impacts are: most
     * reviews are about where bodies stand, and a shadow laid across the ground
     * beside each of them is noise to a reader counting them. A review whose
     * subject is a high-hovering hull asks for it, since the shadow is what shows
     * the hull is off the ground.
     */
    public BattleReviewFrameRenderer withUnitShadows() {
        this.unitShadows = true;
        return this;
    }

    /**
     * Opts this renderer into the roof layer: building roofs, and a fort's own building art, drawn over the interiors
     * they fade away from when somebody is inside. Off by default because most reviews want to see inside.
     */
    public BattleReviewFrameRenderer withRoofs() {
        this.roofs = true;
        return this;
    }

    /**
     * Opts this renderer into {@link RenderLayer#MISSION_MARKERS}, for a look whose subject is one of its readouts
     * (the lift timer). Its labels are battle glass, haloed text with no plate, so headless they draw as they do live.
     */
    public BattleReviewFrameRenderer withMissionMarkers() {
        this.missionMarkers = true;
        return this;
    }

    /** Takes the roof layer back off, for a look still that shows what stands under a building's roof. */
    /** The screen corner a composited HUD rail covers (pixels, y up), so mission labels stay out from under it. */
    public BattleReviewFrameRenderer withHudCover(float left, float bottom) {
        coverLeft = left;
        coverBottom = bottom;
        return this;
    }

    public BattleReviewFrameRenderer withoutRoofs() {
        this.roofs = false;
        return this;
    }

    /**
     * Pins the presentation clock to {@code seconds} instead of {@link #REVIEW_CLOCK_SECONDS}, for a recording whose
     * subject is an animation driven by that clock (the creep's pulse). The frame stays a pure function of what it
     * is given.
     */
    public BattleReviewFrameRenderer atClock(float seconds) {
        this.clockSeconds = seconds;
        return this;
    }

    /** Draws as though the player had {@code selection} selected: the marks the game shows only on a selection. */
    public BattleReviewFrameRenderer selecting(Selection selection) {
        this.selection = selection;
        return this;
    }

    /** Moves a close-view camera: for a recording that follows the fight rather than framing the whole map. */
    public BattleReviewFrameRenderer refocus(float focusX, float focusY, float cellsAcross) {
        this.focusX = focusX; this.focusY = focusY; this.focusWidth = cellsAcross;
        return this;
    }

    public int width() { return width; }

    public int height() { return height; }

    /** One captioned frame of {@code simulation} as it stands right now. */
    public BufferedImage render(BattleSimulation simulation, String caption) throws IOException {
        return render(simulation, caption, ReviewAnnotations.NONE);
    }

    /**
     * The same frame with {@code annotations} drawn over it — the marks that
     * say where a place is and what it is for, which no amount of looking at
     * the ground itself will tell a reader.
     *
     * <p>Drawn after the unit markers, so a box never hides a body, and before
     * the caption band, so the band stays the one thing nothing overlaps.
     */
    public BufferedImage render(BattleSimulation simulation, String caption,
                                ReviewAnnotations annotations) throws IOException {
        return render(simulation, caption, annotations, null);
    }

    /**
     * A look frame with {@code marks} painted over it and nothing else: no unit markers, no caption band. For a map
     * read at a glance (where the mission's places are), never for judging how the ground looks.
     */
    public BufferedImage renderMarked(BattleSimulation simulation, ReviewAnnotations marks) throws IOException {
        return renderMarked(simulation, marks, null);
    }

    /** {@link #renderMarked(BattleSimulation, ReviewAnnotations)} with the impact particles {@code impacts} has fed. */
    public BufferedImage renderMarked(BattleSimulation simulation, ReviewAnnotations marks, ReviewImpactFx impacts)
            throws IOException {
        BufferedImage image = render(simulation, "", ReviewAnnotations.NONE, impacts);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        ReviewAnnotationPainter.paint(graphics, cameraFor(image.getWidth(), image.getHeight(), simulation),
                image.getWidth(), image.getHeight(), marks);
        graphics.dispose();
        return image;
    }

    /**
     * The same frame with the game's impact particles painted over the world,
     * under the unit markers and annotations. {@code impacts} is the recorder
     * the caller has been feeding every tick; null draws none.
     */
    public BufferedImage render(BattleSimulation simulation, String caption,
                                ReviewAnnotations annotations,
                                ReviewImpactFx impacts) throws IOException {
        long start = System.nanoTime();
        BufferedImage image = backend.draw(new ReviewSceneBackend.Scene(simulation,
                cameraFor(width, height, simulation), layers(), clockSeconds, impacts, selection), width, height);
        long scene = System.nanoTime();
        if (impacts != null) {
            impacts.paint(image, cameraFor(image.getWidth(), image.getHeight(), simulation));
        }
        long particles = System.nanoTime();
        if (!presentation) annotate(image, simulation, caption, annotations);
        if (ReviewRenderProfile.ENABLED) {
            long end = System.nanoTime();
            ReviewRenderProfile.phase("scene (collect+drain)", scene - start);
            ReviewRenderProfile.phase("impact particles", particles - scene);
            ReviewRenderProfile.phase("annotate", end - particles);
            ReviewRenderProfile.phase("frame total", end - start);
            ReviewRenderProfile.frameDone();
        }
        return image;
    }

    private void annotate(BufferedImage image, BattleSimulation simulation,
                          String caption, ReviewAnnotations annotations) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        drawWreckMarkers(graphics, simulation);
        drawUnitMarkers(graphics, simulation);
        drawOrdnanceMarkers(graphics, simulation);
        ReviewAnnotationPainter.paint(graphics,
                cameraFor(image.getWidth(), image.getHeight(), simulation),
                image.getWidth(), image.getHeight(), annotations);
        graphics.setColor(new Color(0, 0, 0, 205));
        graphics.fillRect(0, 0, image.getWidth(), HEADER_HEIGHT);
        graphics.setColor(Color.WHITE);
        graphics.setFont(captionFont(graphics, caption, image.getWidth() - 2 * CAPTION_MARGIN));
        graphics.drawString(caption, CAPTION_MARGIN, 20);
        graphics.dispose();
    }

    /**
     * Marks the ground still burning. A destroyed turret, hub, vehicle, mech,
     * or airframe leaves a smoking wreck where it stood, and it is the only
     * part of the FX layer that lasts long enough for a review frame to catch
     * — the fireball itself is a single tick's event and a review samples
     * every few hundred. Without this a raid on an airfield reads as three
     * markers that stop being drawn, which is indistinguishable from three
     * markers that walked off the edge of the frame.
     *
     * <p>Fades as the wreck cools, so a frame says how recently it burned as
     * well as that it did.
     */
    private void drawWreckMarkers(Graphics2D graphics, BattleSimulation simulation) {
        BattleCamera camera = cameraFor(width, height, simulation);
        float radius = Math.max(4f, Math.min(9f, camera.cellPxSize() * 1.4f));
        graphics.setStroke(new BasicStroke(2f));
        for (SmokingWreck wreck : simulation.getSmokingWrecks()) {
            float heat = wreck.totalLifetime <= 0f ? 1f
                    : Math.max(0f, Math.min(1f, wreck.remainingLifetime / wreck.totalLifetime));
            int centerX = Math.round(camera.cellToScreenX(wreck.x));
            int centerY = Math.round(height - camera.cellToScreenY(wreck.y));
            int diameter = Math.round(radius * 2f);
            graphics.setColor(new Color(WRECK_MARKER.getRed(), WRECK_MARKER.getGreen(),
                    WRECK_MARKER.getBlue(), Math.round(60 + 175 * heat)));
            graphics.drawOval(Math.round(centerX - radius), Math.round(centerY - radius),
                    diameter, diameter);
            graphics.fillOval(Math.round(centerX - radius * 0.45f),
                    Math.round(centerY - radius * 0.45f),
                    Math.round(radius * 0.9f), Math.round(radius * 0.9f));
        }
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
            case ALLY -> new Color(130, 240, 120);
            case CIVILIAN -> CIVILIAN_MARKER;
            default -> null;
        };
    }

    private EnumSet<RenderLayer> layers() {
        EnumSet<RenderLayer> layers = EnumSet.copyOf(REVIEW_LAYERS);
        if (impactEffects || Float.isFinite(focusX)) { layers.add(RenderLayer.SHOTS); layers.add(RenderLayer.IMPACT_FX); }
        if (unitShadows) layers.add(RenderLayer.UNIT_SHADOWS);
        if (roofs) layers.add(RenderLayer.ROOFS);
        if (missionMarkers) layers.add(RenderLayer.MISSION_MARKERS);
        return layers;
    }

    /** Fits the whole grid under the caption band, so a frame never crops the battlefield. */
    private BattleCamera cameraFor(float viewportWidth, float viewportHeight,
                                          BattleSimulation simulation) {
        int header = presentation ? 0 : HEADER_HEIGHT;
        if (!Float.isFinite(focusX)) return cameraFor(viewportWidth, viewportHeight + HEADER_HEIGHT - header,
                simulation.getGrid().getWidth(), simulation.getGrid().getHeight());
        BattleCamera camera = new BattleCamera(simulation.getGrid().getWidth(), simulation.getGrid().getHeight());
        camera.setViewport(0, 0, viewportWidth, viewportHeight - header, (float)Math.floor(viewportWidth / focusWidth));
        camera.centerOn(focusX, focusY);
        camera.coverTopRight(coverLeft, coverBottom);
        return camera;
    }

    /**
     * The same fit for a grid of a stated size. Separated so a test can build
     * the camera an annotation will be projected through without standing up a
     * battle to hold the grid.
     */
    static BattleCamera cameraFor(float viewportWidth, float viewportHeight,
                                  int gridWidth, int gridHeight) {
        float mapHeight = viewportHeight - HEADER_HEIGHT;
        float cellPx = Math.min(viewportWidth / gridWidth, mapHeight / gridHeight);
        // Offline whole-map framing uses complete pixels per cell. Fractional
        // Java2D tile edges otherwise leave black sampling seams on large maps.
        // Keep subpixel fitting available when even one pixel would crop the map.
        if (cellPx >= 1f) cellPx = (float) Math.floor(cellPx);
        BattleCamera camera = new BattleCamera(gridWidth, gridHeight);
        camera.setViewport(0f, 0f, viewportWidth, mapHeight, cellPx);
        return camera;
    }
}
