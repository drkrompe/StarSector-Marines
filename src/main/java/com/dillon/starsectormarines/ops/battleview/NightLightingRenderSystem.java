package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.VisibleCellRect;

import java.util.Arrays;


/**
 * Outdoor night treatment shared by battle and map-review hosts. Lamps never reveal fog; a burn's
 * light shows through it ({@link FireFogClearing}).
 * A burn lights the night as a lamp does, in fire's colour and flickering
 * ({@link FireLight}); its pools are refilled each frame, only where its lights reach.
 */
final class NightLightingRenderSystem implements RenderSystem {
    private final PolyMesh shade = new PolyMesh(4096);
    private final NightLightingPass pass = new NightLightingPass();
    private final DoodadRenderSystem doodads;

    NightLightingRenderSystem(BattleSprites sprites) { doodads = new DoodadRenderSystem(sprites); }
    void dispose() { pass.dispose(); field.dispose(); }
    /** The shade computed on the card, for a host that can run a custom pass owning its GL. */
    private final NightShadeField field = new NightShadeField();
    private CellTopology topology;
    private NightLighting lighting;
    private float[] pools, reds, greens, blues;
    private int width;
    private final FireLight fire = new FireLight();
    /** Fire's pool at half-cell resolution, like {@link #pools}; zero outside the boxes last filled. */
    private float[] firePools;
    /** The most fire lights one frame fills: a whole-map view of a country alight keeps its brightest. */
    static final int MAX_FILLED = 160;
    private int[] filled = new int[MAX_FILLED * 4];
    private int filledCount;
    private int[] inView = new int[64];

    public RenderLayer layer() { return RenderLayer.NIGHT_LIGHTING; }

    public void collect(RenderContext ctx, DrawList out) {
        var ground = ctx.sim.getTopology();
        var night = ground.nightLighting();
        if (night == null) return;
        if (ground != topology || night != lighting) bake(ground, night);
        var camera = ctx.camera;
        var visible = camera.visibleCells(1, ground.getWidth(), ground.getHeight());
        boolean burning = fillFire(ground, FireRenderSystem.enabled() ? ctx.sim.fireFieldIfPresent() : null,
                FireLight.clock(ctx.animationTimeSeconds),
                visible.minX(), visible.minY(), visible.maxX(), visible.maxY());
        var fog=ctx.sim.getFogOfWar();
        boolean useFog=ctx.hostProfile.fogClipsLighting() && fog.isInitialized();
        pass.reset(camera);
        if (useFog) doodads.collectLightingSurfaces(ctx, pass.surfaces());
        boolean masked = pass.surfaces().count(RenderLayer.FOG) > 0;
        if (NightShadeField.ENABLED && ctx.hostProfile.residentGroundAllowed() && !field.broken()) {
            field.frame(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(), burning ? firePools : null,
                    ground, useFog ? fog.cellShownArray() : null, night.darkness(), ctx.alphaMult, camera);
            pass.useField(field, masked, useFog);
            out.addCustom(layer(), pass);
            lamps(ctx, out, night, visible, useFog, fog);
            return;
        }
        int steps = shadeSteps(camera.cellPxSize());
        shade.reset();
        PolyMesh dimMesh = masked ? pass.fogShade() : shade;
        int gridW = ground.getWidth();
        boolean[] shown = useFog ? fog.cellShownArray() : null;
        // The shade where no light reaches is one colour; a run of such cells is drawn as one quad.
        float[] unlitColour = shadeColour(night, 0f, 0f, 0f, 0f, 0f, ctx.alphaMult);
        for (int y = visible.minY(); y <= visible.maxY(); y++) {
            float rowY0 = Math.round(camera.cellToScreenY(y)), rowY1 = Math.round(camera.cellToScreenY(y + 1f));
            dimRun.start(dimMesh, rowY0, rowY1);
            litRun.start(pass.litShade(), rowY0, rowY1);
            for (int x = visible.minX(); x <= visible.maxX(); x++) {
                if (ground.getGroundKind(x,y) == CellTopology.GroundKind.VOID) { dimRun.flush(); litRun.flush(); continue; }
                int cellIndex = y * gridW + x;
                boolean hidden = shown != null && !shown[cellIndex];
                boolean lampDark = zero9(pools, (2 * y) * width + 2 * x);
                boolean fireDark = !burning || zero9(firePools, (2 * y) * width + 2 * x);
                boolean unlit = lampDark && fireDark;
                // Fog hides a lamp's light; a burn's is seen from anywhere (FireFogClearing).
                boolean dimUniform = (hidden || lampDark) && fireDark, litUniform = unlit;
                float cellX0 = Math.round(camera.cellToScreenX(x)), cellX1 = Math.round(camera.cellToScreenX(x + 1f));
                if (dimUniform) dimRun.extend(cellX0, cellX1, unlitColour); else dimRun.flush();
                if (masked) { if (litUniform) litRun.extend(cellX0, cellX1, unlitColour); else litRun.flush(); }
                if (dimUniform && (!masked || litUniform)) continue;
                for (int sy=0; sy<steps; sy++) for (int sx=0; sx<steps; sx++) {
                    float px=x+(sx+.5f)/steps, py=y+(sy+.5f)/steps;
                    float x0=Math.round(camera.cellToScreenX(x+(float)sx/steps));
                    float x1=Math.round(camera.cellToScreenX(x+(sx+1f)/steps));
                    float y0=Math.round(camera.cellToScreenY(y+(float)sy/steps));
                    float y1=Math.round(camera.cellToScreenY(y+(sy+1f)/steps));
                    if (!dimUniform) {
                        float pool=hidden ? 0f : sample(pools,px,py);
                        float firePool=burning ? sample(firePools,px,py) : 0f;
                        appendShade(dimMesh, night, pool, firePool, px, py, x0, y0, x1, y1, ctx.alphaMult);
                    }
                    if (masked && !litUniform) appendShade(pass.litShade(), night, sample(pools,px,py),
                            burning ? sample(firePools,px,py) : 0f, px, py,
                            x0, y0, x1, y1, ctx.alphaMult);
                }
            }
            dimRun.flush(); litRun.flush();
        }
        if (masked) out.addCustom(layer(), pass);
        else out.addPoly(layer(),shade);
        lamps(ctx, out, night, visible, useFog, fog);
    }

    /** The fixtures and their halos, over the shade. */
    private void lamps(RenderContext ctx, DrawList out, NightLighting night, VisibleCellRect visible, boolean useFog,
                       FogOfWarService fog) {
        var camera = ctx.camera;
        for (var lamp:night.lamps()) {
            if (!visible.contains((int)lamp.x(),(int)lamp.y())) continue;
            if (useFog
                    && !fog.cellShownArray()[(int)lamp.y()*fog.gridWidth()+(int)lamp.x()]) continue;
            float x=camera.cellToScreenX(lamp.x()), y=camera.cellToScreenY(lamp.y()), px=camera.cellPxSize();
            if (lamp.fixture() == NightLighting.Fixture.TRIPOD_FLOODLIGHT) {
                floodlight(out, lamp, x, y, px, night.powered(lamp), ctx.alphaMult);
                continue;
            }
            if (!night.powered(lamp)) continue;
            for (int halo=3;halo>=1;halo--)
                out.addLine(layer(),x-px*.22f,y,x+px*.22f,y,px*(.14f+halo*.13f),lamp.red(),lamp.green(),lamp.blue(),.08f*ctx.alphaMult);
            StripFixtureArt.draw(out,layer(),x,y,px,false,lamp.red(),lamp.green(),lamp.blue(),1f,ctx.alphaMult);
        }
    }

    private void appendShade(PolyMesh mesh, NightLighting night, float pool, float firePool, float px, float py,
                             float x0, float y0, float x1, float y1, float alphaMult) {
        boolean warmed = pool + firePool > 0f;
        float[] c = shadeColour(night, pool, firePool,
                warmed ? sample(reds,px,py) : 0f, warmed ? sample(greens,px,py) : 0f, warmed ? sample(blues,px,py) : 0f,
                alphaMult);
        if (c != null) mesh.appendQuad(x0,y0,x1,y0,x1,y1,x0,y1,c[0],c[1],c[2],c[3]);
    }

    private final float[] colour = new float[4];

    /** The shade over a point lit by {@code pool} of lamp light and {@code firePool} of fire; null where it is clear. */
    private float[] shadeColour(NightLighting night, float pool, float firePool, float red, float green, float blue,
                                float alphaMult) {
        float[] c = pool == 0f && firePool == 0f ? new float[4] : colour;
        return shade(night.darkness(), pool, firePool, red, green, blue, alphaMult, c) ? c : null;
    }

    /**
     * The night shade over a point into {@code c} (straight colour and alpha); false where it is clear. The one
     * definition: {@link NightShadeField}'s fragment shader is this function, and its GL evidence holds it to it.
     */
    static boolean shade(float darkness, float pool, float firePool, float red, float green, float blue,
                         float alphaMult, float[] c) {
        float lit=pool+firePool;
        float dark=darkness*(1-.92f*Math.min(1,lit));
        // Fire tints what it lights harder than a lamp does: it is the only light there.
        float warm=Math.min(1,.11f*Math.min(1,pool)+.2f*Math.min(1,firePool));
        float alpha=dark+warm*(1-dark), weight=dark*(1-warm);
        if (alpha<=0f) return false;
        float divisor=Math.max(.001f,lit);
        c[0]=(.012f*weight+warm*(red+firePool*FireLight.RED)/divisor)/alpha;
        c[1]=(.019f*weight+warm*(green+firePool*FireLight.GREEN)/divisor)/alpha;
        c[2]=(.037f*weight+warm*(blue+firePool*FireLight.BLUE)/divisor)/alpha;
        c[3]=alpha*alphaMult;
        return true;
    }

    /**
     * Shade samples per cell edge at this many pixels a cell. Zoomed out, a sample is a few pixels across
     * and the pools change over cells, so fewer of them draw the same picture: one below six pixels a cell.
     */
    static int shadeSteps(float cellPx) {
        if (cellPx >= 24) return 8;
        if (cellPx >= 12) return 4;
        return cellPx < 6 ? 1 : 2;
    }

    private boolean zero9(float[] values, int i) {
        for (int row = 0; row < 3; row++, i += width)
            if (values[i] != 0f || values[i + 1] != 0f || values[i + 2] != 0f) return false;
        return true;
    }

    /** A row's run of one-colour cells, appended as one quad when it ends. */
    private static final class Run {
        private PolyMesh mesh;
        private float y0, y1, x0, x1;
        private float[] colour;
        void start(PolyMesh target, float rowY0, float rowY1) { mesh = target; y0 = rowY0; y1 = rowY1; colour = null; }
        void extend(float cellX0, float cellX1, float[] c) {
            if (c == null) { flush(); return; }
            if (colour != null && cellX0 == x1) { x1 = cellX1; return; }
            flush();
            colour = c; x0 = cellX0; x1 = cellX1;
        }
        void flush() {
            if (colour != null && x1 != x0)
                mesh.appendQuad(x0,y0,x1,y0,x1,y1,x0,y1,colour[0],colour[1],colour[2],colour[3]);
            colour = null;
        }
    }
    private final Run dimRun = new Run(), litRun = new Run();

    /**
     * A floodlight seen from straight above, as everything on the map is: a
     * square footplate on the ground, the lamp housing across it, the lens on
     * the face it points along, and a short throw of light on the ground ahead
     * when it is powered. Nothing is drawn as if raised toward the viewer, so a
     * lamp sits on its own cell whichever way the camera frames it. The plate
     * and housing stay visible during an outage.
     */
    private void floodlight(DrawList out, NightLighting.Lamp lamp, float x, float y,
                            float px, boolean powered, float alpha) {
        double bearing=Math.toRadians(lamp.bearingDegrees());
        float forwardX=(float)Math.cos(bearing),forwardY=-(float)Math.sin(bearing);
        float crossX=-forwardY,crossY=forwardX;
        if (powered) {
            // The beam's throw on the ground: a narrow fan ahead of the lens, brightest at the lamp.
            for (int ray=-2;ray<=2;ray++) {
                double spread=bearing+Math.toRadians(ray*9);
                float rayX=(float)Math.cos(spread),rayY=-(float)Math.sin(spread);
                float startX=x+forwardX*px*.3f,startY=y+forwardY*px*.3f;
                out.addLine(layer(),startX,startY,startX+rayX*px*1.9f,startY+rayY*px*1.9f,px*.34f,
                        lamp.red(),lamp.green(),lamp.blue(),.06f*alpha);
            }
        }
        // Footplate: a dark square with a lighter rim, flat on the ground.
        float half=px*.3f;
        out.addSolidRect(layer(),x-half,y-half,x+half,y+half,.31f,.31f,.29f,alpha);
        out.addSolidRect(layer(),x-half*.8f,y-half*.8f,x+half*.8f,y+half*.8f,.1f,.11f,.12f,alpha);
        // Housing across the bearing, set back from the lens.
        float bodyX=x-forwardX*px*.04f,bodyY=y-forwardY*px*.04f;
        out.addLine(layer(),bodyX-crossX*px*.3f,bodyY-crossY*px*.3f,
                bodyX+crossX*px*.3f,bodyY+crossY*px*.3f,px*.26f,.08f,.09f,.11f,alpha);
        out.addLine(layer(),bodyX-crossX*px*.24f,bodyY-crossY*px*.24f,
                bodyX+crossX*px*.24f,bodyY+crossY*px*.24f,px*.14f,.38f,.4f,.41f,alpha);
        // Lens on the leading face.
        float faceX=x+forwardX*px*.12f,faceY=y+forwardY*px*.12f;
        out.addLine(layer(),faceX-crossX*px*.24f,faceY-crossY*px*.24f,
                faceX+crossX*px*.24f,faceY+crossY*px*.24f,Math.max(1,px*.07f),
                powered ? 1f : .2f,powered ? .94f : .24f,powered ? .75f : .26f,alpha);
    }

    /**
     * Refills fire's pools for this frame; false, having cleared them, when
     * nothing burns. A fire lights only its own side of a roof, as a lamp does.
     */
    private boolean fillFire(CellTopology ground, FireField field, float timeSeconds,
                             int viewMinX, int viewMinY, int viewMaxX, int viewMaxY) {
        fire.update(field);
        if (firePools == null || firePools.length != pools.length) {
            firePools = new float[pools.length];
            filledCount = 0;
        }
        for (int i = 0; i < filledCount; i += 4)
            for (int y = filled[i + 1]; y <= filled[i + 3]; y++)
                Arrays.fill(firePools, y * width + filled[i], y * width + filled[i + 2] + 1, 0f);
        filledCount = 0;
        if (fire.count() == 0) return false;
        int height = pools.length / width;
        // Only the lights whose pools reach the view, and of those the brightest.
        int n = 0;
        for (int l = 0; l < fire.count(); l++) {
            float r = fire.radius(l);
            if (fire.x(l) + r < viewMinX || fire.x(l) - r > viewMaxX + 1
                    || fire.y(l) + r < viewMinY || fire.y(l) - r > viewMaxY + 1) continue;
            if (n == inView.length) inView = Arrays.copyOf(inView, n * 2);
            inView[n++] = l;
        }
        if (n == 0) return false;
        if (n > MAX_FILLED) {
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = inView[i];
            Arrays.sort(order, (a, b) -> Float.compare(fire.baseStrength(b), fire.baseStrength(a)));
            for (int i = 0; i < MAX_FILLED; i++) inView[i] = order[i];
            n = MAX_FILLED;
        }
        for (int k = 0; k < n; k++) {
            int l = inView[k];
            float r = fire.radius(l);
            int gx0 = Math.max(0, (int) ((fire.x(l) - r) * 2)), gx1 = Math.min(width - 1, (int) ((fire.x(l) + r) * 2) + 1);
            int gy0 = Math.max(0, (int) ((fire.y(l) - r) * 2)), gy1 = Math.min(height - 1, (int) ((fire.y(l) + r) * 2) + 1);
            int owner = ground.getBuildingId(Math.min(ground.getWidth() - 1, (int) fire.x(l)),
                    Math.min(ground.getHeight() - 1, (int) fire.y(l)));
            // The flicker once per light, not once per point it lights.
            float strengthNow = fire.strength(l, timeSeconds);
            for (int y = gy0; y <= gy1; y++)
                for (int x = gx0; x <= gx1; x++) {
                    if (ground.getBuildingId(Math.min(ground.getWidth() - 1, x / 2), Math.min(ground.getHeight() - 1, y / 2)) != owner) continue;
                    firePools[y * width + x] += fire.pool(l, x * .5f, y * .5f, timeSeconds, strengthNow);
                }
            filled[filledCount++] = gx0;
            filled[filledCount++] = gy0;
            filled[filledCount++] = gx1;
            filled[filledCount++] = gy1;
        }
        return true;
    }

    private void bake(CellTopology ground, NightLighting night) {
        topology=ground; lighting=night; width=ground.getWidth()*2+1;
        bakePools(ground, night);
        if (NightShadeField.ENABLED) field.bakeLamps(pools, reds, greens, blues, width, pools.length / width);
    }

    private void bakePools(CellTopology ground, NightLighting night) {
        int height=ground.getHeight()*2+1;
        pools=new float[width*height]; reds=new float[pools.length]; greens=new float[pools.length]; blues=new float[pools.length];
        for (var lamp:night.lamps()) {
            if (!night.powered(lamp)) continue;
            int owner=ground.getBuildingId((int)lamp.x(),(int)lamp.y());
            for (int y=Math.max(0,(int)((lamp.y()-lamp.radius())*2));y<=Math.min(height-1,(int)((lamp.y()+lamp.radius())*2));y++)
                for (int x=Math.max(0,(int)((lamp.x()-lamp.radius())*2));x<=Math.min(width-1,(int)((lamp.x()+lamp.radius())*2));x++) {
                    // Roofed interiors use their own fixtures; yard spill does not light through a roof.
                    if (ground.getBuildingId(Math.min(ground.getWidth()-1,x/2),Math.min(ground.getHeight()-1,y/2))!=owner) continue;
                    float value=lamp.pool(x*.5f,y*.5f);
                    int i=y*width+x;
                    pools[i]+=value; reds[i]+=value*lamp.red(); greens[i]+=value*lamp.green(); blues[i]+=value*lamp.blue();
                }
        }
    }

    private float sample(float[] values,float x,float y) {
        float gx=x*2,gy=y*2; int ix=(int)gx,iy=(int)gy;
        float dx=gx-ix,dy=gy-iy; int i=iy*width+ix;
        return (values[i]*(1-dx)+values[i+1]*dx)*(1-dy)+(values[i+width]*(1-dx)+values[i+width+1]*dx)*dy;
    }
}
