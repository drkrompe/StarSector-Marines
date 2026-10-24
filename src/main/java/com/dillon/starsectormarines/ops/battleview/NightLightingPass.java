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
    private NightShadeField field;
    private boolean masked, fieldFog;

    void reset(BattleCamera camera) {
        fogShade.reset(); litShade.reset(); surfaces.clear();
        field = null; masked = true; fieldFog = false;
        x = camera.vpX(); y = camera.vpY(); width = camera.vpW(); height = camera.vpH();
    }
    /**
     * Draw this frame's shade from {@code field} rather than the meshes; {@code masked} when artwork coverage
     * gates it, and {@code fog} when a lamp's light stops at what the player cannot see.
     */
    void useField(NightShadeField field, boolean masked, boolean fog) {
        this.field = field; this.masked = masked; fieldFog = fog;
    }
    /** The field this frame's shade draws from, or null for the meshes. */
    NightShadeField field() { return field; }
    /** Whether artwork coverage gates the shade; always so for the meshes, which are only passed when it does. */
    boolean masked() { return masked; }
    boolean fieldFog() { return fieldFog; }
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
