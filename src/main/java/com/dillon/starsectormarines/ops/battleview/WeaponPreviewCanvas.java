package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.appearance.LayeredAppearance;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.appearance.LiveAppearance;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts.LayerPose;
import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.ui.panel.AmmoStrip;
import com.dillon.starsectormarines.ops.BattleDirectControlHudModel;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponFireCycle;
import com.dillon.starsectormarines.battle.weapon.fx.FxCommandPainter;
import com.dillon.starsectormarines.battle.weapon.fx.FxCompositionContext;
import com.dillon.starsectormarines.battle.weapon.fx.FxParticleCommand;
import com.dillon.starsectormarines.battle.weapon.fx.FxSlot;
import com.dillon.starsectormarines.battle.weapon.fx.WeaponFxComposer;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.dillon.starsectormarines.ui.retained.svg.SvgAsset;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * A marine primary on the range, for its catalog sheet: a marine holding the trigger at the weapon's
 * own cycle, its rounds crossing to a target plate, and beneath them the battle HUD's weapon row
 * draining and refilling with the load ({@code moddable-weapons-nouns.md}, catalog preview).
 *
 * <p>Everything shown is read from the weapon's definition through the code battle uses: the cadence
 * from {@link WeaponFireCycle}, the round's body and tail from {@link ShotFx#ofPrimary}, muzzle and
 * impact effects from {@link WeaponFxComposer} painted by {@link FxCommandPainter}, the marine through
 * {@link LayeredUnitComposer} in the pose a firing marine takes, and the HUD weapon row through
 * {@link BattleDirectControlHudModel#marinePrimaryRow}. A new or retuned weapon previews as it plays,
 * with nothing to author.
 *
 * <p>Distances and speeds are the battle's: one cell is {@link #CELL_PX} pixels, and a round crosses the
 * range at its {@code roundVelocity}.
 */
public final class WeaponPreviewCanvas implements CanvasProducer {

    public static final int WIDTH = 360;
    public static final int HEIGHT = 168;

    static final float CELL_PX = 96f;
    private static final float STAGE_HEIGHT = 112f;
    private static final float HUD_TOP = STAGE_HEIGHT + 4f;
    // Starsector sprite angles are north-based and counter-clockwise, so an eastbound range uses -90.
    private static final float FACING_DEGREES = -90f;
    private static final float ACTOR_X = -1.45f;
    private static final float ACTOR_Y = -0.05f;
    private static final float TARGET_X = 1.6f;
    private static final float TARGET_HALF_HEIGHT = 0.36f;
    private static final float TRACER_WIDTH = 2f;
    private static final int IMPACT_VARIANTS = 4;
    private static final LayeredArmorFamily ARMOR = LayeredArmorFamily.CHARCOAL;

    private static final Color STAGE = new Color(0x0E, 0x18, 0x24);
    private static final Color GRID = new Color(0x1A, 0x28, 0x36);
    private static final Color FLOOR = new Color(0x40, 0x5A, 0x78);
    private static final Color PLATE = new Color(0x5B, 0x66, 0x72);
    private static final Color PLATE_EDGE = new Color(0x8D, 0x9A, 0xA8);
    private static final Color RING = new Color(0xC7, 0x5B, 0x4A);
    // The HUD weapon row's own colours (battle-direct-control-weapons.mlx).
    private static final Color HUD_BACKGROUND = new Color(0, 0, 0, 0x66);
    private static final Color HUD_EDGE = new Color(0x45, 0x52, 0x4C);
    private static final Color HUD_NAME = new Color(0xEC, 0xF3, 0xFB);
    private static final Color HUD_READY = new Color(0x7C, 0xD8, 0x9C);
    private static final Color HUD_BUSY = new Color(0xFF, 0xD4, 0x64);
    private static final Color HUD_EMPTY = new Color(0xF0, 0x7C, 0x72);
    private static final Color HUD_TRACK = new Color(0, 0, 0, 0x80);
    private static final Color HUD_FILL = new Color(0xA7, 0xCC, 0xC4);
    private static final Color WHITE = Color.WHITE;

    private static final FxCommandPainter.Projection PROJECTION = new FxCommandPainter.Projection() {
        @Override public float x(float cellX) { return toX(cellX); }
        @Override public float y(float cellY) { return toY(cellY); }
        @Override public float cellPx() { return CELL_PX; }
    };

    private final WeaponDef weapon;
    private final EquipmentGrade grade;
    private final WeaponFireCycle cycle;
    private final ShotFx shot;
    private final ArmoryLoadoutPreviewComposer.Assets assets;
    private final DoubleSupplier seconds;
    private final String name;
    private final List<FxParticleCommand> muzzle;
    private final List<List<FxParticleCommand>> impacts = new ArrayList<>();
    private final float impactSeconds;

    private float muzzleX = Float.NaN;
    private float muzzleY;

    /**
     * @param seconds time since the sheet opened; the trigger is first pulled at zero
     */
    public WeaponPreviewCanvas(WeaponDef weapon, EquipmentGrade grade,
                               ArmoryLoadoutPreviewComposer.Assets assets, DoubleSupplier seconds) {
        if (weapon == null) throw new IllegalArgumentException("a weapon preview needs a weapon");
        if (assets == null) throw new IllegalArgumentException("preview assets are required");
        if (seconds == null) throw new IllegalArgumentException("preview time is required");
        this.weapon = weapon;
        this.grade = grade != null ? grade : EquipmentGrade.SERVICE;
        this.cycle = WeaponFireCycle.of(weapon, this.grade);
        this.shot = ShotFx.ofPrimary(weapon);
        this.assets = assets;
        this.seconds = seconds;
        this.name = weapon.gradedName(this.grade);
        float seed = seedTime(weapon.id);
        this.muzzle = compose(FxSlot.MUZZLE, 0f, 0f, seed);
        float longest = 0f;
        for (int variant = 0; variant < IMPACT_VARIANTS; variant++) {
            List<FxParticleCommand> impact = compose(FxSlot.IMPACT, 0f, 0f, seed + variant * 0.37f);
            impacts.add(impact);
            for (FxParticleCommand command : impact) {
                longest = Math.max(longest, command.delaySeconds() + command.lifetimeSeconds());
            }
        }
        this.impactSeconds = longest;
    }

    public WeaponFireCycle cycle() {
        return cycle;
    }

    @Override
    public void draw(CanvasContext context) {
        float t = (float) Math.max(0d, seconds.getAsDouble());
        WeaponFireCycle.State state = cycle.at(t);
        drawStage(context);
        drawTarget(context);
        drawMarine(context, state, t);
        drawRounds(context, t);
        drawHud(context, state);
    }

    private void drawStage(CanvasContext context) {
        context.fillRect(0f, 0f, WIDTH, STAGE_HEIGHT, STAGE);
        for (int line = -2; line <= 2; line++) {
            float x = toX(line);
            context.line(x, 0f, x, STAGE_HEIGHT, GRID, 1f);
        }
        for (int line = -1; line <= 1; line++) {
            float y = toY(line);
            context.line(0f, y, WIDTH, y, GRID, 1f);
        }
        context.line(0f, STAGE_HEIGHT - 1f, WIDTH, STAGE_HEIGHT - 1f, FLOOR, 1f);
    }

    private void drawTarget(CanvasContext context) {
        float left = toX(TARGET_X);
        float top = toY(ACTOR_Y + TARGET_HALF_HEIGHT);
        float bottom = toY(ACTOR_Y - TARGET_HALF_HEIGHT);
        context.fillRect(left, top, 9f, bottom - top, PLATE);
        context.strokeRect(left, top, 9f, bottom - top, PLATE_EDGE, 1f);
        float centre = toY(ACTOR_Y);
        context.line(left - 1f, centre - 7f, left - 1f, centre + 7f, RING, 2f);
    }

    /** The marine in the pose battle gives a marine whose rounds are leaving, or aiming between them. */
    private void drawMarine(CanvasContext context, WeaponFireCycle.State state, float t) {
        LayeredUnitAssets layered = assets.layered(ARMOR);
        if (layered == null) return;
        int pose;
        float phase;
        int flags = 0;
        LayerPose authored;
        if (state.reloading()) {
            pose = LayeredAppearance.POSE_IDLE;
            phase = 0f;
            authored = ArmoryLoadoutPreviewComposer.idlePose(assets.unitLayerLayouts(), t);
        } else if (state.sinceRelease() <= LayeredAppearance.PRIMARY_FLASH_SECONDS) {
            pose = LayeredAppearance.POSE_FIRING;
            phase = clamp01(state.sinceRelease() / LayeredAppearance.PRIMARY_FLASH_SECONDS);
            flags = LayeredAppearance.FLAG_MUZZLE_FLASH;
            authored = null;
        } else {
            pose = LayeredAppearance.POSE_AIMED;
            phase = clamp01(state.sinceRelease() / LiveAppearance.WEAPON_UP_TIME);
            authored = null;
        }
        if (authored == null) {
            authored = UnitRenderService.infantryPoseDef(assets.unitLayerLayouts(), true, null,
                    pose, 0f, phase, flags);
        }
        if (Float.isNaN(muzzleX)) findMuzzle(layered);
        emitMarine(context, layered, pose, phase, flags, authored);
    }

    private void emitMarine(CanvasContext context, LayeredUnitAssets layered,
                            int pose, float phase, int flags, LayerPose authored) {
        LayeredUnitComposer.emit(
                (layer, centerX, centerY, width, height, angle, red, green, blue, alpha) ->
                        context.sprite(layer.sourcePath, layer.sprite, centerX, HEIGHT - centerY,
                                width, height, angle, new Color(clamp01(red), clamp01(green),
                                        clamp01(blue), clamp01(alpha))),
                layered, layered.head, weapon, true, null, grade,
                toX(ACTOR_X), HEIGHT - toY(ACTOR_Y), shoulderPx(), FACING_DEGREES, 0f, 0f,
                phase, pose, flags, 1f, authored);
    }

    /**
     * Where rounds leave: the centre of the muzzle flash the composer draws at the weapon's tip, taken
     * once from a firing frame so the preview places it exactly where battle draws the flash.
     */
    private void findMuzzle(LayeredUnitAssets layered) {
        muzzleX = ACTOR_X + 0.4f;
        muzzleY = ACTOR_Y;
        if (layered.muzzleFlash == null) return;
        LayerPose firing = UnitRenderService.infantryPoseDef(assets.unitLayerLayouts(), true, null,
                LayeredAppearance.POSE_FIRING, 0f, 0f, LayeredAppearance.FLAG_MUZZLE_FLASH);
        LayeredUnitComposer.emit(
                (layer, centerX, centerY, width, height, angle, red, green, blue, alpha) -> {
                    if (layer != layered.muzzleFlash) return;
                    muzzleX = fromX(centerX);
                    muzzleY = fromY(HEIGHT - centerY);
                },
                layered, layered.head, weapon, true, null, grade,
                toX(ACTOR_X), HEIGHT - toY(ACTOR_Y), shoulderPx(), FACING_DEGREES, 0f, 0f,
                0f, LayeredAppearance.POSE_FIRING, LayeredAppearance.FLAG_MUZZLE_FLASH, 1f, firing);
    }

    /** Every round of this loop and the last that is still in the air or still striking. */
    private void drawRounds(CanvasContext context, float t) {
        float range = Math.max(0.5f, TARGET_X - muzzleX);
        float flight = range / Math.max(1f, weapon.roundVelocity);
        float period = cycle.period();
        float loopStart = (float) Math.floor(t / period) * period;
        for (int loop = -1; loop <= 0; loop++) {
            for (int round = 0; round < cycle.releasesPerLoop(); round++) {
                float age = t - (loopStart + loop * period + cycle.releaseTime(round));
                if (age < 0f || age > flight + impactSeconds) continue;
                float impactY = ACTOR_Y + spread(round);
                FxCommandPainter.draw(context, muzzle, age, muzzleX, muzzleY, PROJECTION, this::sprite);
                if (age < flight) {
                    drawBody(context, age / flight, range, impactY);
                } else {
                    FxCommandPainter.draw(context, impacts.get(round % IMPACT_VARIANTS), age - flight,
                            TARGET_X - 0.05f, impactY, PROJECTION, this::sprite);
                }
            }
        }
    }

    private void drawBody(CanvasContext context, float progress, float range, float impactY) {
        float headX = muzzleX + (TARGET_X - muzzleX) * progress;
        float headY = muzzleY + (impactY - muzzleY) * progress;
        float travelled = range * progress;
        float bearing = (float) Math.toDegrees(Math.atan2(impactY - muzzleY, TARGET_X - muzzleX)) - 90f;
        float dirX = (TARGET_X - muzzleX) / range;
        float dirY = (impactY - muzzleY) / range;
        ShotFx.TracerTail tail = shot.tracerTail();
        if (tail != null) {
            float length = Math.min(tail.lengthCells(), travelled);
            Color color = tail.color() != null ? tail.color() : WHITE;
            context.line(toX(headX - dirX * length), toY(headY - dirY * length), toX(headX), toY(headY),
                    color, TRACER_WIDTH);
        }
        if (shot.body() instanceof ShotFx.Sprite body) {
            LayeredSpriteCache sprite = assets.icon(body.spritePath());
            if (sprite == null || body.visualCells() <= 0f) return;
            float height = body.visualCells() * CELL_PX;
            context.sprite(sprite.sourcePath, sprite.sprite, toX(headX), toY(headY),
                    height * sprite.pxWidth / sprite.pxHeight, height, bearing, WHITE);
        } else if (shot.body() instanceof ShotFx.Bolt bolt) {
            LayeredSpriteCache sprite = assets.icon(bolt.spritePath());
            if (sprite == null) return;
            // Grown out of the muzzle, as battle draws a bolt on its first stretch of flight.
            float length = Math.min(bolt.lengthCells(), Math.max(0.05f, travelled));
            float centreX = headX - dirX * length * 0.5f;
            float centreY = headY - dirY * length * 0.5f;
            context.sprite(sprite.sourcePath, sprite.sprite, toX(centreX), toY(centreY),
                    bolt.widthCells() * CELL_PX, length * CELL_PX, bearing,
                    bolt.color() != null ? bolt.color() : WHITE,
                    CanvasSpriteRegion.FULL, CanvasBlend.ADDITIVE);
        }
    }

    /** The weapon's row as the battle HUD draws it: name, state, cycle bar and the load round by round. */
    private void drawHud(CanvasContext context, WeaponFireCycle.State state) {
        float top = HUD_TOP;
        float height = HEIGHT - top;
        context.fillRect(0f, top, WIDTH, height, HUD_BACKGROUND);
        context.strokeRect(0f, top, WIDTH, height, HUD_EDGE, 1f);

        BattleDirectControlHudModel.PrimaryRow row = BattleDirectControlHudModel.marinePrimaryRow(
                weapon, grade, state.cooldownRemaining(cycle), cycle.cycleSeconds(),
                state.burstRemaining(), state.burstTimer(), state.loaded(),
                state.reloadRemaining(), cycle.reloadSeconds());
        Color statusColor = switch (row.tone()) {
            case READY -> HUD_READY;
            case BUSY -> HUD_BUSY;
            case EMPTY -> HUD_EMPTY;
        };
        context.text(Fonts.INSIGNIA_15_AA, name, 7f, top + 3f, HUD_NAME);
        float statusWidth = Fonts.ORBITRON_12_BOLD.measureWidth(row.state());
        context.text(Fonts.ORBITRON_12_BOLD, row.state(), WIDTH - 7f - statusWidth, top + 5f, statusColor);

        float barY = top + 21f;
        context.fillRect(7f, barY, WIDTH - 14f, 3f, HUD_TRACK);
        context.fillRect(7f, barY, (WIDTH - 14f) * row.cycleFraction(), 3f, HUD_FILL);

        AmmoStrip strip = row.strip();
        if (strip == null) return;
        float x = 7f;
        float iconTop = barY + 6f;
        float iconHeight = HEIGHT - iconTop - 4f;
        for (int icon = 0; icon < strip.icons(); icon++) {
            context.svg(SvgAsset.load(strip.iconPath(icon)), x, iconTop, strip.iconWidth(), iconHeight, WHITE);
            x += strip.iconWidth() + (strip.iconGroupEnd(icon) ? AmmoStrip.GROUP_GAP : strip.iconGap());
        }
    }

    private FxCommandPainter.Sprite sprite(String path) {
        LayeredSpriteCache sprite = assets.icon(path);
        return sprite == null ? null
                : new FxCommandPainter.Sprite(sprite.sourcePath, sprite.sprite, sprite.pxWidth, sprite.pxHeight);
    }

    private List<FxParticleCommand> compose(FxSlot slot, float x, float y, float seed) {
        if (weapon.fx == null || weapon.fx.layers(slot).isEmpty()) return List.of();
        return WeaponFxComposer.compose(weapon.fx, slot,
                new FxCompositionContext(x, y, FACING_DEGREES, false, seed));
    }

    /** A round's fall on the plate: the weapon's own spread, the same for one round every loop. */
    private float spread(int round) {
        int hash = (round * 0x9E3779B1) ^ weapon.id.hashCode();
        hash ^= hash >>> 15;
        float unit = (hash & 0xffff) / 65535f - 0.5f;
        return unit * Math.min(TARGET_HALF_HEIGHT * 1.6f, weapon.hitSpread * 0.6f);
    }

    private static float seedTime(String id) {
        return 1f + (id.hashCode() & 0xffff) / 64f;
    }

    private static float shoulderPx() {
        return UnitRenderService.layeredInfantryShoulderWidth(CELL_PX, 1f);
    }

    private static float toX(float cellX) {
        return WIDTH * 0.5f + cellX * CELL_PX;
    }

    private static float toY(float cellY) {
        return STAGE_HEIGHT * 0.5f - cellY * CELL_PX;
    }

    private static float fromX(float x) {
        return (x - WIDTH * 0.5f) / CELL_PX;
    }

    private static float fromY(float y) {
        return (STAGE_HEIGHT * 0.5f - y) / CELL_PX;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
