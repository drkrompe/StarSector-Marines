package com.dillon.starsectormarines.battle.turret.preview;

import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.weapon.fx.FxBlend;
import com.dillon.starsectormarines.battle.weapon.fx.FxCompositionContext;
import com.dillon.starsectormarines.battle.weapon.fx.FxLayerKind;
import com.dillon.starsectormarines.battle.weapon.fx.FxParticleCommand;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxComposer;
import com.dillon.starsectormarines.ops.battleview.TurretLayerPose;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deterministic, headless contact-sheet renderer for data-authored turret
 * mounts. It consumes the same body pose and composed particle commands as the
 * runtime, with {@link Graphics2D} serving only as the final preview backend.
 */
public final class TurretCatalogPreviewRenderer {

    public static final int STATE_COUNT = 6;
    public static final int PANEL_WIDTH = 240;
    public static final int STRIP_WIDTH = PANEL_WIDTH * STATE_COUNT;
    public static final int STRIP_HEIGHT = 220;

    private static final int HEADER_HEIGHT = 34;
    private static final float CELL_PX = 42f;
    private static final float FACING_DEGREES = 90f;
    private static final float TURRET_X = -1.45f;
    private static final float TURRET_Y = 0f;
    private static final float IMPACT_X = 1.65f;
    private static final float IMPACT_Y = 0f;
    private static final float RECOIL_DURATION = 0.12f;
    private static final float RECOIL_DISTANCE_FRACTION = 0.10f;

    private static final String PARTICLE_SHEET = "graphics/particle/smokeAndFire.png";
    private static final String GLOW_SPRITE = "graphics/fx/particlealpha64linear.png";
    private static final String EXPLOSION_RING = "graphics/fx/explosion_ring0.png";

    private static final Color BACKGROUND = new Color(0x0D, 0x12, 0x19);
    private static final Color PANEL_BACKGROUND = new Color(0x16, 0x1D, 0x27);
    private static final Color GRID = new Color(0x2A, 0x36, 0x45);
    private static final Color BORDER = new Color(0x45, 0x57, 0x6B);
    private static final Color LABEL = new Color(0xE0, 0xE8, 0xF2);

    private static final List<State> STATES = List.of(
            new State("REST", Phase.REST),
            new State("RECOIL + MUZZLE", Phase.MUZZLE),
            new State("PROJECTILE + TRAIL", Phase.PROJECTILE),
            new State("IMPACT", Phase.IMPACT),
            new State("EARLY AFTERMATH", Phase.EARLY_AFTERMATH),
            new State("LATE SMOKE", Phase.LATE_SMOKE));

    private final Path modRoot;
    private final Path coreRoot;
    private final Map<String, BufferedImage> imageCache = new LinkedHashMap<>();

    public TurretCatalogPreviewRenderer(Path modRoot, Path coreRoot) {
        if (modRoot == null || coreRoot == null) {
            throw new IllegalArgumentException("mod and Starsector core roots are required");
        }
        this.modRoot = modRoot.toAbsolutePath().normalize();
        this.coreRoot = coreRoot.toAbsolutePath().normalize();
    }

    /** Renders the six authored states and reports how many commands each slot contributed. */
    public RenderedPreview render(TurretMountDef mount) throws IOException {
        if (mount == null || mount.weapon == null || mount.weapon.fx == null) {
            throw new IllegalArgumentException("a turret mount with authored weapon FX is required");
        }
        if (mount.spritePath == null || mount.visualCells <= 0f) {
            throw new IllegalArgumentException("turret mount '" + mount.id
                    + "' has no previewable body appearance");
        }

        BufferedImage base = load(mount.spritePath);
        BufferedImage recoil = mount.recoilSpritePath != null ? load(mount.recoilSpritePath) : null;
        BufferedImage projectile = mount.weapon.projectileSpritePath != null
                ? load(mount.weapon.projectileSpritePath) : null;

        float muzzleX = TURRET_X + directionX() * mount.muzzleOffsetCells;
        float muzzleY = TURRET_Y + directionY() * mount.muzzleOffsetCells;
        float midpointX = (muzzleX + IMPACT_X) * 0.5f;
        float midpointY = (muzzleY + IMPACT_Y) * 0.5f;
        float seedTime = stableSeedTimeSeconds(mount.id);

        EnumMap<FxSlot, List<FxParticleCommand>> commands = new EnumMap<>(FxSlot.class);
        compose(commands, mount, FxSlot.MUZZLE, muzzleX, muzzleY, seedTime);
        compose(commands, mount, FxSlot.TRACER, midpointX, midpointY, seedTime);
        compose(commands, mount, FxSlot.TRAIL, midpointX, midpointY, seedTime);
        compose(commands, mount, FxSlot.IMPACT, IMPACT_X, IMPACT_Y, seedTime);
        compose(commands, mount, FxSlot.AFTERMATH, IMPACT_X, IMPACT_Y, seedTime);

        BufferedImage strip = new BufferedImage(STRIP_WIDTH, STRIP_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = strip.createGraphics();
        configure(graphics);
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, strip.getWidth(), strip.getHeight());

        EnumMap<FxSlot, Integer> contributions = new EnumMap<>(FxSlot.class);
        for (int i = 0; i < STATES.size(); i++) {
            int panelX = i * PANEL_WIDTH;
            Shape oldClip = graphics.getClip();
            graphics.clipRect(panelX, 0, PANEL_WIDTH, STRIP_HEIGHT);
            drawPanel(graphics, panelX, STATES.get(i), mount, base, recoil,
                    projectile, midpointX, midpointY, commands, contributions);
            graphics.setClip(oldClip);
        }
        graphics.dispose();
        return new RenderedPreview(strip, contributions);
    }

    /** Writes one stable PNG strip per catalog mount, in registry order. */
    public List<Path> writeCatalog(TurretCatalogRegistry registry, Path outputDirectory)
            throws IOException {
        if (registry == null || outputDirectory == null) {
            throw new IllegalArgumentException("registry and output directory are required");
        }
        Files.createDirectories(outputDirectory);
        List<Path> outputs = new ArrayList<>();
        for (TurretMountDef mount : registry.mounts()) {
            Path output = outputDirectory.resolve(fileStem(mount.id) + ".png");
            if (!ImageIO.write(render(mount).image(), "PNG", output.toFile())) {
                throw new IOException("No PNG writer available for " + output);
            }
            outputs.add(output);
        }
        return List.copyOf(outputs);
    }

    /** Mod assets override vanilla assets, matching Starsector's resolution order. */
    public Path resolveAsset(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IOException("asset path may not be blank");
        }
        Path modAsset = safeResolve(modRoot, relativePath);
        if (Files.isRegularFile(modAsset)) return modAsset;
        Path coreAsset = safeResolve(coreRoot, relativePath);
        if (Files.isRegularFile(coreAsset)) return coreAsset;
        throw new IOException("Preview asset not found in mod or Starsector core: " + relativePath);
    }

    /** Stable authored event time used by every slot in one mount's strip. */
    public static float stableSeedTimeSeconds(String mountId) {
        if (mountId == null || mountId.isBlank()) {
            throw new IllegalArgumentException("mount id may not be blank");
        }
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < mountId.length(); i++) {
            hash ^= mountId.charAt(i);
            hash *= 0x100000001b3L;
        }
        return 1f + (hash & 0xffffL) / 64f;
    }

    private void drawPanel(Graphics2D graphics, int panelX, State state,
                           TurretMountDef mount, BufferedImage base,
                           BufferedImage recoil, BufferedImage projectile,
                           float midpointX, float midpointY,
                           EnumMap<FxSlot, List<FxParticleCommand>> commands,
                           EnumMap<FxSlot, Integer> contributions) throws IOException {
        graphics.setColor(PANEL_BACKGROUND);
        graphics.fillRect(panelX + 2, HEADER_HEIGHT, PANEL_WIDTH - 4,
                STRIP_HEIGHT - HEADER_HEIGHT - 2);
        graphics.setColor(GRID);
        graphics.setStroke(new BasicStroke(1f));
        for (int grid = -2; grid <= 2; grid++) {
            int x = Math.round(panelCenterX(panelX) + grid * CELL_PX);
            int y = Math.round(sceneCenterY() + grid * CELL_PX);
            graphics.drawLine(x, HEADER_HEIGHT, x, STRIP_HEIGHT);
            graphics.drawLine(panelX, y, panelX + PANEL_WIDTH, y);
        }

        boolean recoiling = state.phase == Phase.MUZZLE;
        TurretLayerPose pose = TurretLayerPose.resolve(
                worldToX(panelX, TURRET_X), worldToY(TURRET_Y), FACING_DEGREES,
                mount.visualCells, CELL_PX, recoiling ? 0f : RECOIL_DURATION,
                RECOIL_DURATION, RECOIL_DISTANCE_FRACTION);
        if (recoil != null) {
            drawRotated(graphics, recoil, pose.recoilCenterX(), pose.recoilCenterY(),
                    pose.spriteHeightPx(), pose.facingDegrees());
        }
        drawRotated(graphics, base, pose.baseCenterX(), pose.baseCenterY(),
                pose.spriteHeightPx(), pose.facingDegrees());

        switch (state.phase) {
            case REST -> { }
            case MUZZLE -> addContribution(contributions, FxSlot.MUZZLE,
                    drawVisible(graphics, panelX, commands.get(FxSlot.MUZZLE), 0.04f));
            case PROJECTILE -> {
                if (projectile != null && mount.weapon.projectileVisualCells > 0f) {
                    drawRotated(graphics, projectile, worldToX(panelX, midpointX),
                            worldToY(midpointY), mount.weapon.projectileVisualCells * CELL_PX,
                            FACING_DEGREES);
                }
                addContribution(contributions, FxSlot.TRACER,
                        drawVisible(graphics, panelX, commands.get(FxSlot.TRACER), 0.04f));
                addContribution(contributions, FxSlot.TRAIL,
                        drawVisible(graphics, panelX, commands.get(FxSlot.TRAIL), 0.12f));
            }
            case IMPACT -> addContribution(contributions, FxSlot.IMPACT,
                    drawVisible(graphics, panelX, commands.get(FxSlot.IMPACT), 0.06f));
            case EARLY_AFTERMATH -> {
                addContribution(contributions, FxSlot.IMPACT,
                        drawCommands(graphics, panelX, commands.get(FxSlot.IMPACT), 0.42f));
                addContribution(contributions, FxSlot.AFTERMATH,
                        drawVisible(graphics, panelX, commands.get(FxSlot.AFTERMATH), 0.42f));
            }
            case LATE_SMOKE -> {
                addContribution(contributions, FxSlot.IMPACT,
                        drawCommands(graphics, panelX, commands.get(FxSlot.IMPACT), 1.15f));
                addContribution(contributions, FxSlot.AFTERMATH,
                        drawVisible(graphics, panelX, commands.get(FxSlot.AFTERMATH), 1.15f));
            }
        }

        graphics.setColor(BACKGROUND);
        graphics.fillRect(panelX, 0, PANEL_WIDTH, HEADER_HEIGHT);
        graphics.setColor(LABEL);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 12));
        graphics.drawString(state.label, panelX + 9, 21);
        graphics.setColor(BORDER);
        graphics.drawRect(panelX, 0, PANEL_WIDTH - 1, STRIP_HEIGHT - 1);
    }

    private void compose(EnumMap<FxSlot, List<FxParticleCommand>> commands,
                         TurretMountDef mount, FxSlot slot,
                         float x, float y, float seedTime) {
        if (mount.weapon.fx.layers(slot).isEmpty()) return;
        commands.put(slot, WeaponFxComposer.compose(mount.weapon.fx, slot,
                new FxCompositionContext(x, y, FACING_DEGREES, false, seedTime)));
    }

    private int drawVisible(Graphics2D graphics, int panelX,
                            List<FxParticleCommand> commands, float preferredAge)
            throws IOException {
        if (commands == null || commands.isEmpty()) return 0;
        if (hasLiveCommand(commands, preferredAge)) {
            return drawCommands(graphics, panelX, commands, preferredAge);
        }
        FxParticleCommand first = commands.get(0);
        float visibleAge = first.delaySeconds()
                + Math.min(0.03f, first.lifetimeSeconds() * 0.25f);
        return drawCommands(graphics, panelX, commands, visibleAge);
    }

    private int drawCommands(Graphics2D graphics, int panelX,
                             List<FxParticleCommand> commands, float snapshotAge)
            throws IOException {
        if (commands == null || commands.isEmpty()) return 0;
        int drawn = 0;
        for (FxParticleCommand command : commands) {
            float age = snapshotAge - command.delaySeconds();
            if (age < 0f || age >= command.lifetimeSeconds()) continue;
            float lifeFraction = 1f - age / command.lifetimeSeconds();
            float x = command.x() + command.velocityX() * age;
            float y = command.y() + command.velocityY() * age;
            float radius = command.radiusCells() + command.radiusGrowthPerSecond() * age;
            BufferedImage sprite = particleSprite(command, age);
            BufferedImage tinted = tint(sprite, command.color(), lifeFraction,
                    command.blend() == FxBlend.ADDITIVE);
            drawRotated(graphics, tinted, worldToX(panelX, x), worldToY(y),
                    Math.max(1f, radius * 2f * CELL_PX), command.angleDegrees());
            drawn++;
        }
        return drawn;
    }

    private BufferedImage particleSprite(FxParticleCommand command, float age)
            throws IOException {
        return switch (command.kind()) {
            case GLOW, DUST -> load(GLOW_SPRITE);
            case RING -> load(EXPLOSION_RING);
            case EXPLOSION -> load("graphics/fx/explosion"
                    + Math.floorMod(command.variantIndex(), 7) + ".png");
            case FIRE, SMOKE -> particleFrame(command.kind(), age,
                    command.lifetimeSeconds());
        };
    }

    private BufferedImage particleFrame(FxLayerKind kind, float age, float lifetime)
            throws IOException {
        BufferedImage sheet = load(PARTICLE_SHEET);
        int frame = Math.min(7, (int) (age / Math.max(0.001f, lifetime) * 8f));
        int index = (kind == FxLayerKind.SMOKE ? 8 : 0) + frame;
        int cellWidth = sheet.getWidth() / 4;
        int cellHeight = sheet.getHeight() / 4;
        return sheet.getSubimage((index % 4) * cellWidth,
                (index / 4) * cellHeight, cellWidth, cellHeight);
    }

    private BufferedImage load(String path) throws IOException {
        BufferedImage cached = imageCache.get(path);
        if (cached != null) return cached;
        Path resolved = resolveAsset(path);
        BufferedImage loaded = ImageIO.read(resolved.toFile());
        if (loaded == null) throw new IOException("Unsupported preview image: " + resolved);
        imageCache.put(path, loaded);
        return loaded;
    }

    private static BufferedImage tint(BufferedImage source, Color color,
                                      float alpha, boolean additive) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        float lift = additive ? 1.15f : 1f;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int sourceAlpha = argb >>> 24;
                int sourceRed = argb >>> 16 & 0xff;
                int sourceGreen = argb >>> 8 & 0xff;
                int sourceBlue = argb & 0xff;
                int outAlpha = clamp(Math.round(sourceAlpha * alpha));
                int outRed = clamp(Math.round(sourceRed * color.getRed() / 255f * lift));
                int outGreen = clamp(Math.round(sourceGreen * color.getGreen() / 255f * lift));
                int outBlue = clamp(Math.round(sourceBlue * color.getBlue() / 255f * lift));
                result.setRGB(x, y, outAlpha << 24 | outRed << 16 | outGreen << 8 | outBlue);
            }
        }
        return result;
    }

    private static void drawRotated(Graphics2D graphics, BufferedImage image,
                                    float centerX, float centerY, float height,
                                    float facingDegrees) {
        float width = height * image.getWidth() / image.getHeight();
        AffineTransform oldTransform = graphics.getTransform();
        Composite oldComposite = graphics.getComposite();
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.translate(centerX, centerY);
        graphics.rotate(Math.toRadians(facingDegrees));
        graphics.drawImage(image, Math.round(-width * 0.5f), Math.round(-height * 0.5f),
                Math.round(width), Math.round(height), null);
        graphics.setTransform(oldTransform);
        graphics.setComposite(oldComposite);
    }

    private static boolean hasLiveCommand(List<FxParticleCommand> commands, float age) {
        for (FxParticleCommand command : commands) {
            float commandAge = age - command.delaySeconds();
            if (commandAge >= 0f && commandAge < command.lifetimeSeconds()) return true;
        }
        return false;
    }

    private static void addContribution(EnumMap<FxSlot, Integer> counts,
                                        FxSlot slot, int contribution) {
        if (contribution > 0) counts.merge(slot, contribution, Integer::sum);
    }

    private static Path safeResolve(Path root, String relativePath) throws IOException {
        Path resolved = root.resolve(relativePath.replace('/', File.separatorChar))
                .normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Preview asset escapes its root: " + relativePath);
        }
        return resolved;
    }

    private static String fileStem(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static float panelCenterX(int panelX) {
        return panelX + PANEL_WIDTH * 0.5f;
    }

    private static int sceneCenterY() {
        return HEADER_HEIGHT + (STRIP_HEIGHT - HEADER_HEIGHT) / 2;
    }

    private static float worldToX(int panelX, float worldX) {
        return panelCenterX(panelX) + worldX * CELL_PX;
    }

    private static float worldToY(float worldY) {
        return sceneCenterY() - worldY * CELL_PX;
    }

    private static float directionX() {
        return (float) Math.sin(Math.toRadians(FACING_DEGREES));
    }

    private static float directionY() {
        return (float) Math.cos(Math.toRadians(FACING_DEGREES));
    }

    public record RenderedPreview(
            BufferedImage image,
            Map<FxSlot, Integer> slotContributions) {

        public RenderedPreview {
            if (image == null || slotContributions == null) {
                throw new IllegalArgumentException("preview image and contributions are required");
            }
            slotContributions = Map.copyOf(slotContributions);
        }
    }

    private record State(String label, Phase phase) {}

    private enum Phase {
        REST,
        MUZZLE,
        PROJECTILE,
        IMPACT,
        EARLY_AFTERMATH,
        LATE_SMOKE
    }
}
