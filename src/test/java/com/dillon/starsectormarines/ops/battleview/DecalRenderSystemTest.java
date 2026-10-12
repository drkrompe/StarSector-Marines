package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.combat.fx.Decal;
import com.dillon.starsectormarines.battle.world.tiles.SpriteSheetFrames;
import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.DrawCommand;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DecalRenderSystemTest {
    @Test void retainedMarksUseTheirRealPoseAndAspectWithoutACustomGlCommand() {
        SpriteAPI sheet = (SpriteAPI)Proxy.newProxyInstance(SpriteAPI.class.getClassLoader(),
                new Class<?>[]{SpriteAPI.class},(p,m,a)->null);
        var frames = new SpriteSheetFrames(64,32,new SpriteSheetFrames.Frame[]{
                new SpriteSheetFrames.Frame(4,6,20,10)});
        BattleCamera camera = new BattleCamera(10,10);
        camera.setViewport(0,0,320,320,32);
        DrawList out = new DrawList();
        DecalRenderSystem.collect(List.of(new Decal(2,3,0,45,2),new Decal(200,200,0,0,1),
                new Decal(3,3,5,0,1)),sheet,frames,camera,.6f,out);
        assertEquals(1,out.count(RenderLayer.DECALS));
        var command = out.buffer(RenderLayer.DECALS)[0];
        assertEquals(DrawCommand.Kind.SHEET_QUAD,command.kind());
        assertEquals(64,command.width(),.001);
        assertEquals(32,command.height(),.001);
        assertEquals(45,command.angleDegrees(),.001);
        assertEquals(camera.cellToScreenX(2),command.centerX(),.001);
        assertEquals(camera.cellToScreenY(3),command.centerY(),.001);
        assertFalse(BattleRenderHostProfile.EMBEDDED_SCENE.residentDecalsAllowed());
        assertTrue(BattleRenderHostProfile.STANDALONE_BATTLE.residentDecalsAllowed());
    }
}
