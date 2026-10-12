package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.Decal;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.fs.starfarer.api.graphics.SpriteAPI;

/** Command-driven decal path for hosts that cannot own a GL accumulator. */
final class DecalRenderSystem {
    private DecalRenderSystem() {}

    static void collect(Iterable<Decal> decals, SpriteAPI sheet, SpriteSheetFrames frames,
                        BattleCamera camera, float alpha, DrawList out) {
        if (sheet == null || frames == null) return;
        ViewCull view = ViewCull.of(camera);
        for (Decal decal : decals) {
            if (decal.decalIndex < 0 || decal.decalIndex >= frames.frames.length) continue;
            var frame = frames.frames[decal.decalIndex];
            float width = decal.scaleCells, height = width * frame.h / Math.max(1,frame.w);
            if (!view.visible(decal.x,decal.y,(float)Math.hypot(width,height)/2)) continue;
            out.addSheetQuad(RenderLayer.DECALS,sheet,frame.x,frame.y,frame.w,frame.h,
                    camera.cellToScreenX(decal.x),camera.cellToScreenY(decal.y),
                    width*camera.cellPxSize(),height*camera.cellPxSize(),decal.rotationDeg,1,1,1,alpha);
        }
    }
}
