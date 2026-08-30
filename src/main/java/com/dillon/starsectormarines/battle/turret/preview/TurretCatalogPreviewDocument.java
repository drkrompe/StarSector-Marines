package com.dillon.starsectormarines.battle.turret.preview;

import com.dillon.starsectormarines.battle.turret.TurretMountDef;
import com.dillon.starsectormarines.battle.turret.TurretMountGeometry;
import com.dillon.starsectormarines.battle.combat.Projectile;
import com.dillon.starsectormarines.battle.weapon.ContrailProfile;
import com.dillon.starsectormarines.battle.weapon.fx.FxBlend;
import com.dillon.starsectormarines.battle.weapon.fx.FxCompositionContext;
import com.dillon.starsectormarines.battle.weapon.fx.FxLayerKind;
import com.dillon.starsectormarines.battle.weapon.fx.FxParticleCommand;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxComposer;
import com.dillon.starsectormarines.ops.battleview.TurretLayerPose;
import com.dillon.starsectormarines.render2d.ContrailStyle;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.Insets;
import com.dillon.starsectormarines.ui.retained.Overflow;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.UiElement;
import com.dillon.starsectormarines.ui.retained.UiLayout;
import com.dillon.starsectormarines.ui.retained.UiTag;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Retained six-state turret storyboard backed by the shared procedural-canvas
 * contract. The document owns composition and layout; the selected paint
 * target owns rasterization or live Starsector drawing.
 */
public final class TurretCatalogPreviewDocument {

    public static final int STATE_COUNT = 6;
    public static final int PANEL_WIDTH = 240;
    public static final int STRIP_WIDTH = PANEL_WIDTH * STATE_COUNT;
    public static final int STRIP_HEIGHT = 220;

    private static final int HEADER_HEIGHT = 34;
    private static final int SCENE_HEIGHT = STRIP_HEIGHT - HEADER_HEIGHT;
    private static final float CELL_PX = 42f;
    // Starsector sprite angles are north-based and counter-clockwise, so an
    // eastbound storyboard uses -90 degrees.
    static final float FACING_DEGREES = -90f;
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
    private static final Color WHITE = Color.WHITE;

    private TurretCatalogPreviewDocument() {}

    /** Builds a fresh document and contribution ledger for one catalog mount. */
    public static Preview create(TurretMountDef mount, Assets assets) {
        requirePreviewable(mount);
        if (assets == null) throw new IllegalArgumentException("preview assets are required");

        Scene scene = Scene.compose(mount, assets);
        List<State> states = statesFor(mount);
        EnumMap<FxSlot, Integer> contributions = new EnumMap<>(FxSlot.class);
        UiElement root = new UiElement("turret-preview")
                .layout(UiLayout.ROW)
                .preferredSize(STRIP_WIDTH, STRIP_HEIGHT)
                .background(BACKGROUND);
        UiDocument document = new UiDocument(root);

        for (int index = 0; index < states.size(); index++) {
            State state = states.get(index);
            UiElement panel = new UiElement("turret-preview-panel-" + index)
                    .layout(UiLayout.COLUMN)
                    .preferredSize(PANEL_WIDTH, STRIP_HEIGHT)
                    .border(1f, BORDER)
                    .overflow(Overflow.HIDDEN);
            UiElement label = new UiElement("turret-preview-label-" + index)
                    .preferredSize(PANEL_WIDTH - 2f, HEADER_HEIGHT - 1f)
                    .padding(new Insets(7f, 8f, 0f, 8f))
                    .background(BACKGROUND)
                    .text(Fonts.ORBITRON_12_BOLD, state.label(), LABEL);
            UiElement canvas = new UiElement("turret-preview-canvas-" + index)
                    .tag(UiTag.CANVAS)
                    .canvasSize(PANEL_WIDTH - 2, SCENE_HEIGHT - 1)
                    .preferredSize(PANEL_WIDTH - 2f, SCENE_HEIGHT - 1f)
                    .overflow(Overflow.HIDDEN);
            panel.child(label).child(canvas);
            root.child(panel);
            document.canvases().set(canvas,
                    new StateCanvas(scene, state.phase(), contributions));
        }
        return new Preview(document, contributions,
                states.stream().map(State::label).toList());
    }

    private static List<State> statesFor(TurretMountDef mount) {
        String launch = mount.weapon.burstCount > 1
                ? "SALVO LAUNCH ×" + mount.weapon.burstCount : "RECOIL + MUZZLE";
        String flight;
        if (mount.weapon.boostRamp && mount.weapon.arcHeight > 0f) {
            flight = "BOOSTED ARC + TRAIL";
        } else if (mount.weapon.arcHeight > 0f) {
            flight = "ARC + TRAIL";
        } else {
            flight = "PROJECTILE + TRAIL";
        }
        String impact = mount.weapon.indirectFire || mount.weapon.hitSpread > 0f
                ? "SCATTER IMPACT" : "IMPACT";
        return List.of(
                new State("REST", Phase.REST),
                new State(launch, Phase.MUZZLE),
                new State(flight, Phase.PROJECTILE),
                new State(impact, Phase.IMPACT),
                new State("EARLY AFTERMATH", Phase.EARLY_AFTERMATH),
                new State("LATE SMOKE", Phase.LATE_SMOKE));
    }

    static List<String> stateLabelsFor(TurretMountDef mount) {
        return statesFor(mount).stream().map(State::label).toList();
    }

    static int visibleRoundCount(TurretMountDef mount) {
        return Math.min(3, Math.max(1, mount.weapon.burstCount));
    }

    static float previewBurstProgressSpacing(TurretMountDef mount) {
        float flightSeconds = Math.max(0.1f, mount.weapon.flightSec);
        return Math.max(0.05f, Math.min(0.18f,
                mount.weapon.burstSpacing / flightSeconds * 2f));
    }

    static float previewBearingDegrees(TurretMountDef mount, float rawProgress) {
        return previewBearingDegrees(mount, rawProgress, 0);
    }

    private static float previewBearingDegrees(TurretMountDef mount, float rawProgress,
                                               int releaseIndex) {
        FlightPoint before = flightPoint(mount, rawProgress - 0.01f, releaseIndex);
        FlightPoint after = flightPoint(mount, rawProgress + 0.01f, releaseIndex);
        return bearingDegrees(before.x(), before.y(), after.x(), after.y());
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

    private static void requirePreviewable(TurretMountDef mount) {
        if (mount == null || mount.weapon == null || mount.weapon.fx == null) {
            throw new IllegalArgumentException("a turret mount with authored weapon FX is required");
        }
        if (mount.spritePath == null || mount.visualCells <= 0f) {
            throw new IllegalArgumentException("turret mount '" + mount.id
                    + "' has no previewable body appearance");
        }
    }

    /** Backend-specific sprite handles and backend-neutral source dimensions. */
    @FunctionalInterface
    public interface Assets {
        Sprite sprite(String sourcePath);
    }

    /** One resolved preview asset; a headless source leaves {@code liveSprite} null. */
    public record Sprite(String sourcePath, SpriteAPI liveSprite,
                         int pixelWidth, int pixelHeight) {
        public Sprite {
            if (sourcePath == null || sourcePath.isBlank()) {
                throw new IllegalArgumentException("sprite source path is required");
            }
            if (pixelWidth <= 0 || pixelHeight <= 0) {
                throw new IllegalArgumentException("sprite dimensions must be positive");
            }
        }
    }

    /** Document plus the commands actually visible in its rendered storyboard. */
    public static final class Preview {
        private final UiDocument document;
        private final EnumMap<FxSlot, Integer> contributions;
        private final List<String> stateLabels;

        private Preview(UiDocument document, EnumMap<FxSlot, Integer> contributions,
                        List<String> stateLabels) {
            this.document = document;
            this.contributions = contributions;
            this.stateLabels = List.copyOf(stateLabels);
        }

        public UiDocument document() {
            return document;
        }

        public Map<FxSlot, Integer> slotContributions() {
            return Map.copyOf(contributions);
        }

        public List<String> stateLabels() {
            return stateLabels;
        }
    }

    private record Scene(
            TurretMountDef mount,
            Sprite base,
            Sprite recoil,
            Sprite projectile,
            float midpointX,
            float midpointY,
            EnumMap<FxSlot, List<FxParticleCommand>> commands,
            Assets assets) {

        private static Scene compose(TurretMountDef mount, Assets assets) {
            Sprite base = requiredSprite(assets, mount.spritePath);
            Sprite recoil = optionalSprite(assets, mount.recoilSpritePath);
            Sprite projectile = optionalSprite(assets, mount.weapon.projectileSpritePath);
            TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(
                    TURRET_X, TURRET_Y, FACING_DEGREES, mount, 0);
            float muzzleX = muzzle.x();
            float muzzleY = muzzle.y();
            float midpointX = (muzzleX + IMPACT_X) * 0.5f;
            float midpointY = (muzzleY + IMPACT_Y) * 0.5f;
            float seedTime = stableSeedTimeSeconds(mount.id);
            EnumMap<FxSlot, List<FxParticleCommand>> commands =
                    new EnumMap<>(FxSlot.class);
            composeSlot(commands, mount, FxSlot.LAUNCH, TURRET_X, TURRET_Y, seedTime);
            composeSlot(commands, mount, FxSlot.MUZZLE, muzzleX, muzzleY, seedTime);
            composeSlot(commands, mount, FxSlot.TRACER, midpointX, midpointY, seedTime);
            composeSlot(commands, mount, FxSlot.TRAIL, midpointX, midpointY, seedTime);
            composeSlot(commands, mount, FxSlot.IMPACT, IMPACT_X, IMPACT_Y, seedTime);
            composeSlot(commands, mount, FxSlot.AFTERMATH, IMPACT_X, IMPACT_Y, seedTime);
            return new Scene(mount, base, recoil, projectile, midpointX, midpointY,
                    commands, assets);
        }
    }

    private record StateCanvas(Scene scene, Phase phase,
                               EnumMap<FxSlot, Integer> contributions)
            implements CanvasProducer {

        @Override
        public void draw(CanvasContext context) {
            drawGrid(context);
            if (phase == Phase.MUZZLE) {
                addContribution(FxSlot.LAUNCH,
                        drawVisible(context, commands(FxSlot.LAUNCH), 0.12f));
            }
            drawTurret(context);
            switch (phase) {
                case REST -> { }
                case MUZZLE -> {
                    drawProjectile(context, 0.08f);
                    addContribution(FxSlot.MUZZLE,
                            drawVisible(context, commands(FxSlot.MUZZLE), 0.04f));
                }
                case PROJECTILE -> {
                    addContribution(FxSlot.TRACER,
                            drawVisible(context, commands(FxSlot.TRACER), 0.04f));
                    drawProjectileSalvo(context);
                }
                case IMPACT -> addContribution(FxSlot.IMPACT,
                        drawVisible(context, commands(FxSlot.IMPACT), 0.06f));
                case EARLY_AFTERMATH -> {
                    addContribution(FxSlot.IMPACT,
                            drawCommands(context, commands(FxSlot.IMPACT), 0.42f));
                    addContribution(FxSlot.AFTERMATH,
                            drawVisible(context, commands(FxSlot.AFTERMATH), 0.42f));
                }
                case LATE_SMOKE -> {
                    addContribution(FxSlot.IMPACT,
                            drawCommands(context, commands(FxSlot.IMPACT), 1.15f));
                    addContribution(FxSlot.AFTERMATH,
                            drawVisible(context, commands(FxSlot.AFTERMATH), 1.15f));
                }
            }
        }

        private void drawGrid(CanvasContext context) {
            context.fillRect(0f, 0f, PANEL_WIDTH - 2f, SCENE_HEIGHT - 1f,
                    PANEL_BACKGROUND);
            for (int grid = -2; grid <= 2; grid++) {
                float x = sceneCenterX() + grid * CELL_PX;
                float y = sceneCenterY() + grid * CELL_PX;
                context.line(x, 0f, x, SCENE_HEIGHT, GRID, 1f);
                context.line(0f, y, PANEL_WIDTH, y, GRID, 1f);
            }
        }

        private void drawTurret(CanvasContext context) {
            boolean recoiling = phase == Phase.MUZZLE;
            TurretLayerPose pose = TurretLayerPose.resolve(
                    worldToX(TURRET_X), worldToY(TURRET_Y), FACING_DEGREES,
                    scene.mount.visualCells, CELL_PX, recoiling ? 0f : RECOIL_DURATION,
                    RECOIL_DURATION, RECOIL_DISTANCE_FRACTION);
            if (scene.recoil != null) {
                drawSprite(context, scene.recoil, pose.recoilCenterX(), pose.recoilCenterY(),
                        pose.spriteHeightPx(), pose.facingDegrees(), WHITE,
                        CanvasSpriteRegion.FULL, CanvasBlend.NORMAL);
            }
            drawSprite(context, scene.base, pose.baseCenterX(), pose.baseCenterY(),
                    pose.spriteHeightPx(), pose.facingDegrees(), WHITE,
                    CanvasSpriteRegion.FULL, CanvasBlend.NORMAL);
        }

        private void drawProjectileSalvo(CanvasContext context) {
            int visibleRounds = visibleRoundCount(scene.mount);
            float spacing = previewBurstProgressSpacing(scene.mount);
            for (int round = visibleRounds - 1; round >= 0; round--) {
                drawProjectile(context, 0.58f - round * spacing, round);
            }
        }

        private void drawProjectile(CanvasContext context, float rawProgress) {
            drawProjectile(context, rawProgress, 0);
        }

        private void drawProjectile(CanvasContext context, float rawProgress,
                                    int releaseIndex) {
            if (scene.projectile == null || scene.mount.weapon.projectileVisualCells <= 0f) return;
            FlightPoint point = flightPoint(scene.mount, rawProgress, releaseIndex);
            if (scene.mount.weapon.contrailProfile == ContrailProfile.MISSILE_SMOKE) {
                drawMissileContrail(context, rawProgress, releaseIndex);
            }
            float trailX = point.x() - directionX()
                    * scene.mount.weapon.projectileVisualCells * 0.35f;
            float trailY = point.y() - directionY()
                    * scene.mount.weapon.projectileVisualCells * 0.35f;
            addContribution(FxSlot.TRAIL, drawVisibleAt(context,
                    commands(FxSlot.TRAIL), 0.12f, trailX, trailY));
            drawSprite(context, scene.projectile, worldToX(point.x()),
                    worldToY(point.y()),
                    scene.mount.weapon.projectileVisualCells * CELL_PX,
                    previewBearingDegrees(scene.mount, rawProgress, releaseIndex), WHITE,
                    CanvasSpriteRegion.FULL, CanvasBlend.NORMAL);
        }

        private void drawMissileContrail(CanvasContext context, float rawProgress,
                                         int releaseIndex) {
            ContrailStyle style = ContrailStyle.MISSILE_SMOKE;
            FlightPoint previous = flightPoint(scene.mount, rawProgress, releaseIndex);
            for (int sample = 1; sample <= 8; sample++) {
                float sampleProgress = Math.max(0f, rawProgress - sample * 0.025f);
                FlightPoint next = flightPoint(scene.mount, sampleProgress, releaseIndex);
                float age = sample / 8f;
                Color smoke = new Color(
                        lerp(style.startR, style.endR, age),
                        lerp(style.startG, style.endG, age),
                        lerp(style.startB, style.endB, age),
                        lerp(style.startA, style.endA, age));
                float halfWidth = lerp(style.startWidthCells,
                        style.endWidthCells, age);
                context.line(worldToX(previous.x()), worldToY(previous.y()),
                        worldToX(next.x()), worldToY(next.y()), smoke,
                        halfWidth * 2f * CELL_PX);
                previous = next;
            }
        }

        private int drawVisible(CanvasContext context, List<FxParticleCommand> commands,
                                float preferredAge) {
            return drawVisibleAt(context, commands, preferredAge,
                    scene.midpointX, scene.midpointY);
        }

        private int drawVisibleAt(CanvasContext context, List<FxParticleCommand> commands,
                                  float preferredAge, float targetX, float targetY) {
            if (commands == null || commands.isEmpty()) return 0;
            if (hasLiveCommand(commands, preferredAge)) {
                return drawCommandsAt(context, commands, preferredAge, targetX, targetY);
            }
            FxParticleCommand first = commands.get(0);
            float visibleAge = first.delaySeconds()
                    + Math.min(0.03f, first.lifetimeSeconds() * 0.25f);
            return drawCommandsAt(context, commands, visibleAge, targetX, targetY);
        }

        private int drawCommands(CanvasContext context, List<FxParticleCommand> commands,
                                 float snapshotAge) {
            return drawCommandsAt(context, commands, snapshotAge,
                    scene.midpointX, scene.midpointY);
        }

        private int drawCommandsAt(CanvasContext context, List<FxParticleCommand> commands,
                                   float snapshotAge, float targetX, float targetY) {
            if (commands == null || commands.isEmpty()) return 0;
            int drawn = 0;
            float translateX = targetX - scene.midpointX;
            float translateY = targetY - scene.midpointY;
            for (FxParticleCommand command : commands) {
                float age = snapshotAge - command.delaySeconds();
                if (age < 0f || age >= command.lifetimeSeconds()) continue;
                float lifeFraction = 1f - age / command.lifetimeSeconds();
                float x = command.x() + translateX + command.velocityX() * age;
                float y = command.y() + translateY + command.velocityY() * age;
                float radius = command.radiusCells()
                        + command.radiusGrowthPerSecond() * age;
                ParticleSprite particle = particleSprite(command, age);
                Color tint = new Color(command.color().getRed(), command.color().getGreen(),
                        command.color().getBlue(),
                        Math.max(0, Math.min(255, Math.round(255f * lifeFraction))));
                drawSprite(context, particle.sprite(), worldToX(x), worldToY(y),
                        Math.max(1f, radius * 2f * CELL_PX), command.angleDegrees(), tint,
                        particle.region(), command.blend() == FxBlend.ADDITIVE
                                ? CanvasBlend.ADDITIVE : CanvasBlend.NORMAL);
                drawn++;
            }
            return drawn;
        }

        private ParticleSprite particleSprite(FxParticleCommand command, float age) {
            return switch (command.kind()) {
                case GLOW, DUST -> new ParticleSprite(
                        requiredSprite(scene.assets, GLOW_SPRITE), CanvasSpriteRegion.FULL);
                case RING -> new ParticleSprite(
                        requiredSprite(scene.assets, EXPLOSION_RING), CanvasSpriteRegion.FULL);
                case EXPLOSION -> new ParticleSprite(requiredSprite(scene.assets,
                        "graphics/fx/explosion" + Math.floorMod(command.variantIndex(), 7)
                                + ".png"), CanvasSpriteRegion.FULL);
                case FIRE, SMOKE -> particleFrame(command.kind(), age,
                        command.lifetimeSeconds());
            };
        }

        /**
         * Frame band for one particle kind on the shared 4x4 sheet: grey
         * smoke occupies its first eight frames, orange fire the last eight.
         *
         * <p>Those indices are the opposite way round from the ones
         * {@code ImpactFx} and {@code FlybyOverlay} name for the same sheet,
         * and both are right: {@link CanvasSpriteRegion#frame} counts rows
         * down from the top of the image, while Starsector's
         * {@code SpriteAPI.setTexY} counts them up from the bottom of the
         * texture. A preview document is drawn through the image-space
         * renderer, so it reads the sheet the way a person looking at the
         * file would.
         */
        private ParticleSprite particleFrame(FxLayerKind kind, float age, float lifetime) {
            Sprite sheet = requiredSprite(scene.assets, PARTICLE_SHEET);
            int frame = Math.min(7, (int) (age / Math.max(0.001f, lifetime) * 8f));
            int index = (kind == FxLayerKind.SMOKE ? 0 : 8) + frame;
            return new ParticleSprite(sheet, CanvasSpriteRegion.frame(4, 4, index));
        }

        private List<FxParticleCommand> commands(FxSlot slot) {
            return scene.commands.get(slot);
        }

        private void addContribution(FxSlot slot, int contribution) {
            if (contribution > 0) contributions.merge(slot, contribution, Integer::sum);
        }
    }

    private static void drawSprite(CanvasContext context, Sprite sprite,
                                   float centerX, float centerY, float height,
                                   float angleDegrees, Color tint,
                                   CanvasSpriteRegion region, CanvasBlend blend) {
        float width = height * sprite.pixelWidth / sprite.pixelHeight;
        context.sprite(sprite.sourcePath, sprite.liveSprite, centerX, centerY,
                width, height, angleDegrees, tint, region, blend);
    }

    private static void composeSlot(EnumMap<FxSlot, List<FxParticleCommand>> commands,
                                    TurretMountDef mount, FxSlot slot,
                                    float x, float y, float seedTime) {
        if (mount.weapon.fx.layers(slot).isEmpty()) return;
        commands.put(slot, WeaponFxComposer.compose(mount.weapon.fx, slot,
                new FxCompositionContext(x, y, FACING_DEGREES, false, seedTime)));
    }

    private static Sprite requiredSprite(Assets assets, String path) {
        Sprite sprite = assets.sprite(path);
        if (sprite == null) throw new IllegalArgumentException("missing preview sprite " + path);
        return sprite;
    }

    private static Sprite optionalSprite(Assets assets, String path) {
        return path != null ? requiredSprite(assets, path) : null;
    }

    private static boolean hasLiveCommand(List<FxParticleCommand> commands, float age) {
        for (FxParticleCommand command : commands) {
            float commandAge = age - command.delaySeconds();
            if (commandAge >= 0f && commandAge < command.lifetimeSeconds()) return true;
        }
        return false;
    }

    private static float sceneCenterX() {
        return (PANEL_WIDTH - 2f) * 0.5f;
    }

    private static float sceneCenterY() {
        return (SCENE_HEIGHT - 1f) * 0.5f;
    }

    private static float worldToX(float worldX) {
        return sceneCenterX() + worldX * CELL_PX;
    }

    private static float worldToY(float worldY) {
        return sceneCenterY() - worldY * CELL_PX;
    }

    static float directionX() {
        return -(float) Math.sin(Math.toRadians(FACING_DEGREES));
    }

    static float directionY() {
        return (float) Math.cos(Math.toRadians(FACING_DEGREES));
    }

    private static FlightPoint flightPoint(TurretMountDef mount, float rawProgress,
                                           int releaseIndex) {
        float clamped = Math.max(0f, Math.min(1f, rawProgress));
        float progress = mount.weapon.boostRamp
                ? Projectile.applyBoostCurve(clamped) : clamped;
        TurretMountGeometry.Point muzzle = TurretMountGeometry.muzzle(
                TURRET_X, TURRET_Y, FACING_DEGREES, mount, releaseIndex);
        float muzzleX = muzzle.x();
        float muzzleY = muzzle.y();
        float x = muzzleX + (IMPACT_X - muzzleX) * progress;
        float y = muzzleY + (IMPACT_Y - muzzleY) * progress;
        float previewArc = Math.min(1.25f, mount.weapon.arcHeight * 0.35f);
        y += previewArc * 4f * progress * (1f - progress);
        return new FlightPoint(x, y);
    }

    private static float bearingDegrees(float fromX, float fromY, float toX, float toY) {
        float dx = toX - fromX;
        float dy = toY - fromY;
        if (dx == 0f && dy == 0f) return FACING_DEGREES;
        return (float) Math.toDegrees(Math.atan2(dy, dx)) - 90f;
    }

    private static float lerp(float from, float to, float fraction) {
        return from + (to - from) * fraction;
    }

    private record ParticleSprite(Sprite sprite, CanvasSpriteRegion region) {}

    private record FlightPoint(float x, float y) {}

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
