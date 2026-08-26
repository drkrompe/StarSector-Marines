package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.DoubleSupplier;

/** One selected squad rendered from its bounded shipboard battle scene. */
public final class BarracksCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private static final Color TEAM_FILL = new Color(0x48, 0x94, 0xB3, 18);
    private static final Color TEAM_EDGE = new Color(0x76, 0xB9, 0xD4, 105);

    private final Supplier<List<MarineSoldier>> marines;
    private final BarracksBattleScene battleScene;
    private final DoubleSupplier elapsedSeconds;

    public BarracksCanvas(Supplier<List<MarineSoldier>> marines,
                          BarracksBattleScene battleScene,
                          DoubleSupplier elapsedSeconds) {
        if (marines == null || battleScene == null || elapsedSeconds == null) {
            throw new IllegalArgumentException("marines and battle scene are required");
        }
        this.marines = marines;
        this.battleScene = battleScene;
        this.elapsedSeconds = elapsedSeconds;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        List<MarineSoldier> visible = marines.get();
        float elapsed = (float) elapsedSeconds.getAsDouble();
        CanvasHostViewport[] viewport = new CanvasHostViewport[1];
        BattleSceneHostPass backdrop = battleScene.backdropPass(visible, elapsed);
        boolean rendered = context.hostPass(new BattleSceneHostPass() {
            @Override
            public BattleSceneFrame prepare(CanvasHostViewport value, float alphaMult) {
                viewport[0] = value;
                return backdrop.prepare(value, alphaMult);
            }

            @Override
            public void draw(CanvasHostViewport value, float alphaMult) {
                viewport[0] = value;
                backdrop.draw(value, alphaMult);
            }
        });
        if (!rendered) {
            context.fillRect(0f, 0f, width, height, BACKGROUND);
            return;
        }

        Projection projection = viewport[0] != null
                ? Projection.forHost(viewport[0])
                : Projection.forCanvas(width, height);
        drawRangeGuides(context, projection);
        context.hostPass(battleScene.actorPass(visible, elapsed));
    }

    private static void drawRangeGuides(CanvasContext context, Projection projection) {
        float left = projection.x(24.3f);
        float right = projection.x(30.7f);
        float top = projection.y(13.8f);
        float bottom = projection.y(4.3f);
        context.fillRect(left, top, right - left, bottom - top, TEAM_FILL);
        context.strokeRect(left, top, right - left, bottom - top, TEAM_EDGE, 1f);
        for (float laneX : new float[]{26.5f, 28.5f}) {
            float x = projection.x(laneX);
            context.fillRect(x, top, 1f, bottom - top, TEAM_EDGE);
        }
    }

    private record Projection(float surfaceHeight, float scaleX, float scaleY,
                              BattleCamera camera) {

        static Projection forCanvas(float width, float height) {
            BattleCamera camera = BarracksBattleScene.cameraForSurface(width, height);
            return new Projection(height, 1f, 1f, camera);
        }

        static Projection forHost(CanvasHostViewport viewport) {
            BattleCamera camera = BarracksBattleScene.cameraForSurface(
                    viewport.width(), viewport.height());
            return new Projection(viewport.surfaceHeight(),
                    1f / viewport.scaleX(), 1f / viewport.scaleY(), camera);
        }

        float x(float worldX) {
            return camera.cellToScreenX(worldX) * scaleX;
        }

        float y(float worldY) {
            return surfaceHeight - camera.cellToScreenY(worldY) * scaleY;
        }
    }
}
