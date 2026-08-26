package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.marine.MarineSoldier;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasHostViewport;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;

import java.awt.Color;
import java.util.List;
import java.util.function.Supplier;

/** One selected squad rendered as a bounded, non-advancing battle scene. */
public final class BarracksCanvas implements CanvasProducer {

    private static final Color BACKGROUND = new Color(0x08, 0x0E, 0x15);
    private static final Color TEAM_FILL = new Color(0x48, 0x94, 0xB3, 18);
    private static final Color TEAM_EDGE = new Color(0x76, 0xB9, 0xD4, 105);

    private final Supplier<List<MarineSoldier>> marines;
    private final BarracksBattleScene battleScene;

    public BarracksCanvas(Supplier<List<MarineSoldier>> marines,
                          BarracksBattleScene battleScene) {
        if (marines == null || battleScene == null) {
            throw new IllegalArgumentException("marines and battle scene are required");
        }
        this.marines = marines;
        this.battleScene = battleScene;
    }

    @Override
    public void draw(CanvasContext context) {
        float width = context.metrics().surfaceWidth();
        float height = context.metrics().surfaceHeight();
        List<MarineSoldier> visible = marines.get();
        CanvasHostViewport[] viewport = new CanvasHostViewport[1];
        BattleSceneHostPass backdrop = battleScene.backdropPass(visible);
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
        drawTeamZones(context, projection);
        context.hostPass(battleScene.actorPass(visible));
    }

    private static void drawTeamZones(CanvasContext context, Projection projection) {
        int[] centers = {5, 14, 23};
        for (int center : centers) {
            float left = projection.x(center - 3.5f);
            float right = projection.x(center + 3.5f);
            float top = projection.y(9.5f);
            float bottom = projection.y(3.5f);
            context.fillRect(left, top, right - left, bottom - top, TEAM_FILL);
            context.strokeRect(left, top, right - left, bottom - top, TEAM_EDGE, 1f);
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
