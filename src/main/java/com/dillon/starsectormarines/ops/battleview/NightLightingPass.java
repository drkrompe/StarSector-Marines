package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.render2d.BattleCamera;
import com.dillon.starsectormarines.render2d.PolyMesh;

/** One frame's night treatments and alpha-faithful observed artwork coverage. */
final class NightLightingPass implements Runnable {
    private final NightLightingShader shader = new NightLightingShader();
    private final PolyMesh fogShade = new PolyMesh(4096);
    private final PolyMesh litShade = new PolyMesh(4096);
    private final DrawList surfaces = new DrawList();
    private float x, y, width, height;

    void reset(BattleCamera camera) {
        fogShade.reset(); litShade.reset(); surfaces.clear();
        x = camera.vpX(); y = camera.vpY(); width = camera.vpW(); height = camera.vpH();
    }
    PolyMesh fogShade() { return fogShade; }
    PolyMesh litShade() { return litShade; }
    DrawList surfaces() { return surfaces; }
    float viewportX() { return x; }
    float viewportY() { return y; }
    float viewportWidth() { return width; }
    float viewportHeight() { return height; }
    NightLightingShader shader() { return shader; }
    public void run() { shader.draw(this); }
    void dispose() { shader.dispose(); }
}
