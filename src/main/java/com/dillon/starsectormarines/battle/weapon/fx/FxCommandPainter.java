package com.dillon.starsectormarines.battle.weapon.fx;

import com.dillon.starsectormarines.ui.retained.CanvasBlend;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasSpriteRegion;
import com.fs.starfarer.api.graphics.SpriteAPI;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Paints composed effect commands into a canvas at one age: the one painter every catalog preview
 * shares ({@code moddable-weapons-nouns.md}, catalog preview), so a turret storyboard and a weapon's
 * tooltip scene draw a weapon's {@code fx} the same way.
 *
 * <p>Commands are in world cells; the caller's {@link Projection} places a cell on its canvas.
 */
public final class FxCommandPainter {

    private static final String PARTICLE_SHEET = "graphics/particle/smokeAndFire.png";
    private static final String GLOW_SPRITE = "graphics/fx/particlealpha64linear.png";
    private static final String EXPLOSION_RING = "graphics/fx/explosion_ring0.png";

    private FxCommandPainter() {}

    /** One resolved texture; a headless source leaves {@code liveSprite} null. */
    public record Sprite(String sourcePath, SpriteAPI liveSprite, int pixelWidth, int pixelHeight) {}

    /** Resolves a texture path; returns null for one it cannot supply. */
    @FunctionalInterface
    public interface Sprites {
        Sprite sprite(String sourcePath);
    }

    /** Places a world cell on the canvas. */
    public interface Projection {
        float x(float cellX);

        float y(float cellY);

        float cellPx();
    }

    /** Every texture {@link #draw} may ask for, for a caller that loads ahead of paint. */
    public static List<String> texturePaths() {
        List<String> paths = new ArrayList<>(List.of(PARTICLE_SHEET, GLOW_SPRITE, EXPLOSION_RING));
        for (int i = 0; i < 7; i++) paths.add(explosion(i));
        return List.copyOf(paths);
    }

    /**
     * Draws the commands alive at {@code age} seconds after their event, moved by
     * ({@code translateX}, {@code translateY}) cells. Returns how many were drawn.
     */
    public static int draw(CanvasContext context, List<FxParticleCommand> commands, float age,
                           float translateX, float translateY, Projection projection, Sprites sprites) {
        if (commands == null || commands.isEmpty()) return 0;
        int drawn = 0;
        for (FxParticleCommand command : commands) {
            float commandAge = age - command.delaySeconds();
            if (commandAge < 0f || commandAge >= command.lifetimeSeconds()) continue;
            Sprite sprite = sprites.sprite(texture(command));
            if (sprite == null) continue;
            float lifeFraction = 1f - commandAge / command.lifetimeSeconds();
            float x = command.x() + translateX + command.velocityX() * commandAge;
            float y = command.y() + translateY + command.velocityY() * commandAge;
            float radius = command.radiusCells() + command.radiusGrowthPerSecond() * commandAge;
            Color tint = new Color(command.color().getRed(), command.color().getGreen(),
                    command.color().getBlue(),
                    Math.max(0, Math.min(255, Math.round(255f * lifeFraction))));
            float height = Math.max(1f, radius * 2f * projection.cellPx());
            float width = height * sprite.pixelWidth() / sprite.pixelHeight();
            context.sprite(sprite.sourcePath(), sprite.liveSprite(),
                    projection.x(x), projection.y(y), width, height, command.angleDegrees(), tint,
                    region(command, commandAge),
                    command.blend() == FxBlend.ADDITIVE ? CanvasBlend.ADDITIVE : CanvasBlend.NORMAL);
            drawn++;
        }
        return drawn;
    }

    /** Whether any command is alive at {@code age}. */
    public static boolean anyAlive(List<FxParticleCommand> commands, float age) {
        if (commands == null) return false;
        for (FxParticleCommand command : commands) {
            float commandAge = age - command.delaySeconds();
            if (commandAge >= 0f && commandAge < command.lifetimeSeconds()) return true;
        }
        return false;
    }

    private static String texture(FxParticleCommand command) {
        return switch (command.kind()) {
            case GLOW, DUST -> GLOW_SPRITE;
            case RING -> EXPLOSION_RING;
            case EXPLOSION -> explosion(Math.floorMod(command.variantIndex(), 7));
            case FIRE, SMOKE -> PARTICLE_SHEET;
        };
    }

    /**
     * Frame band for one particle kind on the shared 4x4 sheet: grey smoke occupies its first eight
     * frames, orange fire the last eight.
     *
     * <p>Those indices are the opposite way round from the ones {@code ImpactFx} names for the same
     * sheet, and both are right: {@link CanvasSpriteRegion#frame} counts rows down from the top of the
     * image, while Starsector's {@code SpriteAPI.setTexY} counts them up from the bottom of the
     * texture. A preview is drawn through the image-space renderer, so it reads the sheet the way a
     * person looking at the file would.
     */
    private static CanvasSpriteRegion region(FxParticleCommand command, float age) {
        if (command.kind() != FxLayerKind.FIRE && command.kind() != FxLayerKind.SMOKE) {
            return CanvasSpriteRegion.FULL;
        }
        int frame = Math.min(7, (int) (age / Math.max(0.001f, command.lifetimeSeconds()) * 8f));
        int index = (command.kind() == FxLayerKind.SMOKE ? 0 : 8) + frame;
        return CanvasSpriteRegion.frame(4, 4, index);
    }

    private static String explosion(int variant) {
        return "graphics/fx/explosion" + variant + ".png";
    }
}
