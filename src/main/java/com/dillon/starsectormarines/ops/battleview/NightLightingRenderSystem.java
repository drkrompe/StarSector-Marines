package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.fire.FireField;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.render2d.PolyMesh;
import com.dillon.starsectormarines.render2d.VisibleCellRect;

import com.fs.starfarer.api.graphics.SpriteAPI;

import java.util.Arrays;
import java.util.List;

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

    private final BattleSprites sprites;

    NightLightingRenderSystem(BattleSprites sprites) {
        this.sprites = sprites;
        doodads = new DoodadRenderSystem(sprites);
    }
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
    /** Half-cell box fire's pools changed in this frame, the boxes cleared and filled; empty when x1 < x0. */
    private int changedX0, changedY0, changedX1, changedY1;
    private int[] inView = new int[64];

    public RenderLayer layer() { return RenderLayer.NIGHT_LIGHTING; }

    /** The battle's event lights, which light the night as fire does; null leaves the night to lamps and fire. */
    private GroundLightService events;

    /** Lights the night from {@code lights}' muzzles, impacts and blasts as well as from lamps and fire. */
    void useLights(GroundLightService lights) {
        events = lights;
    }

    public void collect(RenderContext ctx, DrawList out) {
        var ground = ctx.sim.getTopology();
        var night = ground.nightLighting();
        if (night == null) return;
        // Warm-up runs on battle time: a paused battle holds its flicker, and a still frame taken after time has
        // passed shows the lamps lit.
        float now = ctx.sim.getSimTickIndex() * BattleSimulation.TICK_DT;
        if (ground != topology || night != lighting) {
            // The same lamps with other circuits powered: a circuit cut goes dark at once, one powered warms up.
            if (ground == topology && lighting != null && pools != null && night.lamps() == lighting.lamps()) {
                repower(night, now);
            } else {
                bake(ground, night, now);
            }
        }
        if (warming) warm(now);
        var camera = ctx.camera;
        var visible = camera.visibleCells(1, ground.getWidth(), ground.getHeight());
        boolean burning = fillFire(ground, FireRenderSystem.enabled() ? ctx.sim.fireFieldIfPresent() : null,
                FireLight.clock(ctx.animationTimeSeconds),
                visible.minX(), visible.minY(), visible.maxX(), visible.maxY());
        field.fire(firePools, burning, changedX0, changedY0, changedX1, changedY1);
        var fog=ctx.sim.getFogOfWar();
        boolean useFog=ctx.hostProfile.fogClipsLighting() && fog.isInitialized();
        // Cones are cut to what is shown as they are laid, so the field filters their fog edge soft.
        boolean coning = fillCones(ctx, ground, night, useFog ? fog.cellShownArray() : null,
                visible.minX(), visible.minY(), visible.maxX(), visible.maxY());
        field.cones(conePools, coning, coneX0, coneY0, coneX1, coneY1);
        pass.reset(camera);
        if (useFog) doodads.collectLightingSurfaces(ctx, pass.surfaces());
        boolean masked = pass.surfaces().count(RenderLayer.FOG) > 0;
        if (ctx.hostProfile.residentGroundAllowed() && !field.broken()) {
            field.cells(ground, useFog ? fog.cellShownArray() : null, useFog ? fog.revealChanges() : null);
            field.frame(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(), night.darkness(),
                    night.roofedDarkness(), ctx.alphaMult, camera);
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
        // The shade where no light reaches is one colour in the open and one under a roof; a run of such cells is
        // drawn as one quad.
        float[] unlitOpen = shadeColour(night.darkness(), 0f, 0f, 0f, 0f, 0f, ctx.alphaMult);
        float[] unlitRoofed = night.roofedDarkness() == night.darkness() ? unlitOpen
                : shadeColour(night.roofedDarkness(), 0f, 0f, 0f, 0f, 0f, ctx.alphaMult);
        for (int y = visible.minY(); y <= visible.maxY(); y++) {
            float rowY0 = Math.round(camera.cellToScreenY(y)), rowY1 = Math.round(camera.cellToScreenY(y + 1f));
            dimRun.start(dimMesh, rowY0, rowY1);
            litRun.start(pass.litShade(), rowY0, rowY1);
            for (int x = visible.minX(); x <= visible.maxX(); x++) {
                if (ground.getGroundKind(x,y) == CellTopology.GroundKind.VOID) { dimRun.flush(); litRun.flush(); continue; }
                int cellIndex = y * gridW + x;
                float darkness = night.darknessAt(night.roofedDarkness() != night.darkness() && NightLighting.roofed(ground, x, y));
                float[] unlitColour = darkness == night.darkness() ? unlitOpen : unlitRoofed;
                boolean hidden = shown != null && !shown[cellIndex];
                boolean lampDark = zero9(pools, (2 * y) * width + 2 * x)
                        && (!coning || zero9(conePools, (2 * y) * width + 2 * x));
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
                        float cone=coning ? sample(conePools,px,py) : 0f;
                        float pool=hidden ? 0f : sample(pools,px,py)+cone;
                        float firePool=burning ? sample(firePools,px,py) : 0f;
                        appendShade(dimMesh, darkness, pool, hidden ? 0f : cone, firePool, px, py, x0, y0, x1, y1,
                                ctx.alphaMult);
                    }
                    if (masked && !litUniform) {
                        float cone=coning ? sample(conePools,px,py) : 0f;
                        appendShade(pass.litShade(), darkness, sample(pools,px,py)+cone, cone,
                                burning ? sample(firePools,px,py) : 0f, px, py, x0, y0, x1, y1, ctx.alphaMult);
                    }
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
        emissives(ctx, out, useFog, fog);
        var camera = ctx.camera;
        var all=night.lamps();
        for (int index=0;index<all.size();index++) {
            var lamp=all.get(index);
            if (!visible.contains((int)lamp.x(),(int)lamp.y())) continue;
            if (useFog
                    && !fog.cellShownArray()[(int)lamp.y()*fog.gridWidth()+(int)lamp.x()]) continue;
            float x=camera.cellToScreenX(lamp.x()), y=camera.cellToScreenY(lamp.y()), px=camera.cellPxSize();
            if (lamp.fixture() == NightLighting.Fixture.TRIPOD_FLOODLIGHT) {
                floodlight(out, lamp, x, y, px, lampOn[index], ctx.alphaMult);
                continue;
            }
            String art = RuinsLightArt.fixture(lamp.fixture());
            if (art != null) {
                fixture(out, ctx.sim.getTopology(), night, lamp, art, x, y, px, lampOn[index], ctx.alphaMult);
                continue;
            }
            if (!lampOn[index] || lamp.fixture() == NightLighting.Fixture.GLOW) continue;
            for (int halo=3;halo>=1;halo--)
                out.addLine(layer(),x-px*.22f,y,x+px*.22f,y,px*(.14f+halo*.13f),lamp.red(),lamp.green(),lamp.blue(),.08f*ctx.alphaMult);
            StripFixtureArt.draw(out,layer(),x,y,px,false,lamp.red(),lamp.green(),lamp.blue(),1f,ctx.alphaMult);
        }
    }

    /** {@code pool} is all the lamp light at the point, {@code cone} the company's share of it. */
    private void appendShade(PolyMesh mesh, float darkness, float pool, float cone, float firePool, float px, float py,
                             float x0, float y0, float x1, float y1, float alphaMult) {
        boolean warmed = pool + firePool > 0f;
        float[] c = shadeColour(darkness, pool, firePool,
                warmed ? sample(reds,px,py) + cone * UnitLights.RED : 0f,
                warmed ? sample(greens,px,py) + cone * UnitLights.GREEN : 0f,
                warmed ? sample(blues,px,py) + cone * UnitLights.BLUE : 0f,
                alphaMult);
        if (c != null) mesh.appendQuad(x0,y0,x1,y0,x1,y1,x0,y1,c[0],c[1],c[2],c[3]);
    }

    private final float[] colour = new float[4];

    /** The shade over a point lit by {@code pool} of lamp light and {@code firePool} of fire; null where it is clear. */
    private float[] shadeColour(float darkness, float pool, float firePool, float red, float green, float blue,
                                float alphaMult) {
        float[] c = pool == 0f && firePool == 0f ? new float[4] : colour;
        return shade(darkness, pool, firePool, red, green, blue, alphaMult, c) ? c : null;
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
    /**
     * The ruins' machinery and consoles glowing where their room's light is on: each placement from a sheet with an
     * emissive overlay ({@code lights/emissive/<sheet>-lit.png}, the sheet's layout at half its resolution) wears its
     * cut of it, coming up with the lamps over it. A melt or a pour keeps its own glow.
     */
    private void emissives(RenderContext ctx, DrawList out, boolean useFog, FogOfWarService fog) {
        if (pools == null || sprites == null) return;
        var camera = ctx.camera;
        var ground = ctx.sim.getTopology();
        int gw = ground.getWidth(), gh = ground.getHeight();
        VisibleCellRect view = camera.visibleCells(VisibleCellRect.GEOMETRY_MARGIN_CELLS, gw, gh);
        boolean[] shown = useFog ? fog.cellShownArray() : null;
        for (Doodad d : ctx.sim.getDoodads()) {
            String overlayPath = RuinsLightArt.overlayPath(d.sheetPath);
            if (overlayPath == null || !view.intersectsCells(d.cellX, d.cellY, d.footprintCellsX, d.footprintCellsY)) continue;
            String id = d.definitionId();
            if (id != null && (id.endsWith("-melt") || id.contains("-pour"))) continue;
            float cx = d.cellX + d.footprintCellsX * 0.5f, cy = d.cellY + d.footprintCellsY * 0.5f;
            int cellX = Math.max(0, Math.min(gw - 1, (int) cx)), cellY = Math.max(0, Math.min(gh - 1, (int) cy));
            if (shown != null && !shown[cellY * gw + cellX]) continue;
            float glow = running(sample(pools, cx, cy));
            if (glow <= 0f) continue;
            SpriteAPI overlay = sprites.lightArt(overlayPath);
            if (overlay != null) doodads.emitOverlay(out, camera, overlay, 2, d, layer(), 1f, 1f, 1f, glow * ctx.alphaMult);
        }
    }

    /**
     * How hard a machine glows under a lamp pool of {@code pool}: not at all under an emergency strip's dim light,
     * fully once its room's own lamps are on.
     */
    static float running(float pool) {
        return Math.max(0f, Math.min(1f, (pool - RUNNING_FROM) / RUNNING_SPAN));
    }

    static final float RUNNING_FROM = 0.45f, RUNNING_SPAN = 0.35f;

    /**
     * A painted fixture over its cell, lit or dark: drawn after the shade, so a dark one wears its cell's darkness
     * itself and a lit one glows.
     */
    private void fixture(DrawList out, CellTopology ground, NightLighting night, NightLighting.Lamp lamp, String art,
                         float x, float y, float px, boolean lit, float alpha) {
        SpriteAPI sprite = sprites == null ? null : sprites.lightArt(RuinsLightArt.fixturePath(art, lit));
        if (sprite == null) return;
        float shade = 1f;
        if (!lit) {
            int cx = Math.max(0, Math.min(ground.getWidth() - 1, (int) lamp.x()));
            int cy = Math.max(0, Math.min(ground.getHeight() - 1, (int) lamp.y()));
            shade = 1f - FIXTURE_SHADE * night.darknessAt(NightLighting.roofed(ground, cx, cy));
        }
        out.addSprite(layer(), sprite, x, y, px, px, lamp.bearingDegrees(), shade, shade, shade, alpha);
    }

    /** How much of its cell's darkness a dark fixture wears, drawn as it is after the shade. */
    static final float FIXTURE_SHADE = 0.85f;

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
        changedX0 = changedY0 = Integer.MAX_VALUE;
        changedX1 = changedY1 = -1;
        for (int i = 0; i < filledCount; i += 4) {
            for (int y = filled[i + 1]; y <= filled[i + 3]; y++)
                Arrays.fill(firePools, y * width + filled[i], y * width + filled[i + 2] + 1, 0f);
            changed(filled[i], filled[i + 1], filled[i + 2], filled[i + 3]);
        }
        filledCount = 0;
        int height = pools.length / width;
        if (fire.count() == 0) return fillEvents(ground, height, viewMinX, viewMinY, viewMaxX, viewMaxY);
        // Only the lights whose pools reach the view, and of those the brightest.
        int n = 0;
        for (int l = 0; l < fire.count(); l++) {
            float r = fire.radius(l);
            if (fire.x(l) + r < viewMinX || fire.x(l) - r > viewMaxX + 1
                    || fire.y(l) + r < viewMinY || fire.y(l) - r > viewMaxY + 1) continue;
            if (n == inView.length) inView = Arrays.copyOf(inView, n * 2);
            inView[n++] = l;
        }
        if (n == 0) return fillEvents(ground, height, viewMinX, viewMinY, viewMaxX, viewMaxY);
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
            changed(gx0, gy0, gx1, gy1);
        }
        fillEvents(ground, height, viewMinX, viewMinY, viewMaxX, viewMaxY);
        return true;
    }

    /**
     * Adds the event lights in view to fire's pools: a muzzle flash or a blast lights the night round it for as long
     * as it lasts, on its own side of a roof, with the ground composite's falloff. They share fire's pool and its
     * warm colour, which suits nearly all of them. True when any reached the view.
     */
    private boolean fillEvents(CellTopology ground, int height, int viewMinX, int viewMinY, int viewMaxX,
                               int viewMaxY) {
        if (events == null) return false;
        boolean any = false;
        for (int i = 0, n = events.eventCount(); i < n && filledCount < filled.length; i++) {
            GroundLightService.Light light = events.event(i);
            float r = light.radius, strength = light.effectiveIntensity() * EVENT_POOL;
            if (strength <= 0f || r <= 0f) continue;
            if (light.x + r < viewMinX || light.x - r > viewMaxX + 1
                    || light.y + r < viewMinY || light.y - r > viewMaxY + 1) continue;
            int gx0 = Math.max(0, (int) ((light.x - r) * 2)), gx1 = Math.min(width - 1, (int) ((light.x + r) * 2) + 1);
            int gy0 = Math.max(0, (int) ((light.y - r) * 2)), gy1 = Math.min(height - 1, (int) ((light.y + r) * 2) + 1);
            int owner = ground.getBuildingId(Math.max(0, Math.min(ground.getWidth() - 1, (int) light.x)),
                    Math.max(0, Math.min(ground.getHeight() - 1, (int) light.y)));
            for (int y = gy0; y <= gy1; y++)
                for (int x = gx0; x <= gx1; x++) {
                    if (ground.getBuildingId(Math.min(ground.getWidth() - 1, x / 2), Math.min(ground.getHeight() - 1, y / 2)) != owner) continue;
                    float dx = x * .5f - light.x, dy = y * .5f - light.y;
                    float radial = 1f - (float) Math.sqrt(dx * dx + dy * dy) / r;
                    if (radial > 0f) firePools[y * width + x] += radial * radial * strength;
                }
            filled[filledCount++] = gx0;
            filled[filledCount++] = gy0;
            filled[filledCount++] = gx1;
            filled[filledCount++] = gy1;
            changed(gx0, gy0, gx1, gy1);
            any = true;
        }
        return any;
    }

    /** An event light's pool against its intensity: a heavy blast lights the night as a well-fed fire does. */
    static final float EVENT_POOL = 1.6f;

    private void changed(int x0, int y0, int x1, int y1) {
        changedX0 = Math.min(changedX0, x0);
        changedY0 = Math.min(changedY0, y0);
        changedX1 = Math.max(changedX1, x1);
        changedY1 = Math.max(changedY1, y1);
    }

    private void bake(CellTopology ground, NightLighting night, float now) {
        topology=ground; lighting=night; width=ground.getWidth()*2+1;
        bakePools(ground, night, now);
        field.bakeLamps(pools, reds, greens, blues, width, pools.length / width);
    }

    /** Whether each lamp's pool is in the baked field now, by index in the lighting's lamps. */
    private boolean[] lampOn = new boolean[0];
    /** When each lamp began warming up (battle seconds), NaN when it is not warming. */
    private float[] warmStart = new float[0];
    /** Whether any lamp is warming up. */
    private boolean warming;

    /**
     * The same lamps under other circuits: each lamp whose circuit is cut leaves the field now, and each whose
     * circuit is newly powered warms up from when it came on ({@link LampWarmUp}), or is lit at once when the lighting
     * gives no time.
     */
    private void repower(NightLighting night, float now) {
        lighting = night;
        boolean changed = false;
        List<NightLighting.Lamp> lamps = night.lamps();
        for (int i = 0; i < lamps.size(); i++) {
            boolean want = night.powered(lamps.get(i));
            if (!want) {
                warmStart[i] = Float.NaN;
                if (lampOn[i]) { pool(i, -1f); changed = true; }
            } else if (!lampOn[i] && Float.isNaN(warmStart[i])) {
                float at = night.poweredAt(lamps.get(i));
                if (Float.isNaN(at)) { pool(i, 1f); changed = true; }
                else { warmStart[i] = at; warming = true; }
            }
        }
        if (changed) field.bakeLamps(pools, reds, greens, blues, width, pools.length / width);
    }

    /** Strikes and flickers the warming lamps for {@code now}, and settles those whose warm-up is over. */
    private void warm(float now) {
        boolean changed = false, left = false;
        for (int i = 0; i < warmStart.length; i++) {
            if (Float.isNaN(warmStart[i])) continue;
            float seconds = now - warmStart[i];
            boolean on = LampWarmUp.lit(i, seconds);
            if (seconds >= LampWarmUp.settled(i)) warmStart[i] = Float.NaN;
            else left = true;
            if (on != lampOn[i]) { pool(i, on ? 1f : -1f); changed = true; }
        }
        warming = left;
        if (changed) field.bakeLamps(pools, reds, greens, blues, width, pools.length / width);
    }

    private void bakePools(CellTopology ground, NightLighting night, float now) {
        int height=ground.getHeight()*2+1;
        pools=new float[width*height]; reds=new float[pools.length]; greens=new float[pools.length]; blues=new float[pools.length];
        int n=night.lamps().size();
        lampOn=new boolean[n]; warmStart=new float[n]; warming=false;
        Arrays.fill(warmStart, Float.NaN);
        for (int i=0;i<n;i++) {
            var lamp=night.lamps().get(i);
            if (!night.powered(lamp)) continue;
            // A circuit that came on moments ago is still warming up, whenever this field is laid.
            float at=night.poweredAt(lamp);
            if (!Float.isNaN(at) && now-at<LampWarmUp.settled(i)) { warmStart[i]=at; warming=true; }
            else pool(i, 1f);
        }
    }

    /** Adds the pool of lamp {@code i} into the field ({@code sign} 1), or takes it out (-1). */
    private void pool(int i, float sign) {
        CellTopology ground=topology;
        int height=pools.length/width;
        var lamp=lighting.lamps().get(i);
        lampOn[i]=sign>0;
        int owner=ground.getBuildingId((int)lamp.x(),(int)lamp.y());
        for (int y=Math.max(0,(int)((lamp.y()-lamp.radius())*2));y<=Math.min(height-1,(int)((lamp.y()+lamp.radius())*2));y++)
            for (int x=Math.max(0,(int)((lamp.x()-lamp.radius())*2));x<=Math.min(width-1,(int)((lamp.x()+lamp.radius())*2));x++) {
                // Roofed interiors use their own fixtures; yard spill does not light through a roof. A wall
                // catches the light of the room it bounds.
                if (!litBy(ground, Math.min(ground.getWidth()-1,x/2), Math.min(ground.getHeight()-1,y/2), owner)) continue;
                float value=lamp.pool(x*.5f,y*.5f);
                // A lamp's tint weighs its colour against its pool: the shade divides the colour by the pool.
                float stain=sign*value*lamp.tint();
                int c=y*width+x;
                pools[c]=settle(pools[c]+sign*value); reds[c]=settle(reds[c]+stain*lamp.red());
                greens[c]=settle(greens[c]+stain*lamp.green()); blues[c]=settle(blues[c]+stain*lamp.blue());
            }
    }

    private final UnitLights units = new UnitLights();
    /** The company's cone pools at half-cell resolution, like {@link #pools}; zero outside the boxes last filled. */
    private float[] conePools;
    private int[] coneFilled = new int[UnitLights.MAX_CONES * 4];
    private int coneFilledCount;
    /** Half-cell box the cone pools changed in this frame; empty when x1 < x0. */
    private int coneX0, coneY0, coneX1, coneY1;
    /** Whether each cell is dark enough for a cone to show, and the ground change count it was read at. */
    private boolean[] coneDark;
    private long coneDarkSeen = -1;
    private NightLighting coneDarkNight;

    /**
     * Refills the company's cone pools for this frame ({@link UnitLights}): each lit body in view lays its cone on the
     * cells of its own building that are shown ({@code shown} null when fog does not clip lighting), and only where
     * the night is dark enough to show it. False, having cleared them, when
     * no cone lit anything.
     */
    private boolean fillCones(RenderContext ctx, CellTopology ground, NightLighting night, boolean[] shown,
                              int viewMinX, int viewMinY, int viewMaxX, int viewMaxY) {
        if (conePools == null || conePools.length != pools.length) {
            conePools = new float[pools.length];
            coneFilledCount = 0;
        }
        coneX0 = coneY0 = Integer.MAX_VALUE;
        coneX1 = coneY1 = -1;
        for (int i = 0; i < coneFilledCount; i += 4) {
            for (int y = coneFilled[i + 1]; y <= coneFilled[i + 3]; y++)
                Arrays.fill(conePools, y * width + coneFilled[i], y * width + coneFilled[i + 2] + 1, 0f);
            coneBox(coneFilled[i], coneFilled[i + 1], coneFilled[i + 2], coneFilled[i + 3]);
        }
        coneFilledCount = 0;
        if (Math.max(night.darkness(), night.roofedDarkness()) < UnitLights.MIN_DARKNESS
                || ctx.camera.cellPxSize() < UnitLights.MIN_CELL_PX) return false;
        refreshConeDark(ground, night);
        units.gather(ctx.sim, viewMinX, viewMinY, viewMaxX, viewMaxY);
        int height = pools.length / width, gw = ground.getWidth(), gh = ground.getHeight();
        boolean any = false;
        for (int k = 0; k < units.count(); k++) {
            UnitLights.Kind kind = units.kind(k);
            float lx = units.x(k), ly = units.y(k), dx0 = units.dirX(k), dy0 = units.dirY(k), r = kind.reach;
            int cx = Math.max(0, Math.min(gw - 1, (int) lx)), cy = Math.max(0, Math.min(gh - 1, (int) ly));
            int owner = ground.getBuildingId(cx, cy);
            int gx0 = Math.max(0, (int) ((lx - r) * 2)), gx1 = Math.min(width - 1, (int) ((lx + r) * 2) + 1);
            int gy0 = Math.max(0, (int) ((ly - r) * 2)), gy1 = Math.min(height - 1, (int) ((ly + r) * 2) + 1);
            for (int y = gy0; y <= gy1; y++) {
                int cellY = Math.min(gh - 1, y / 2);
                for (int x = gx0; x <= gx1; x++) {
                    int cellX = Math.min(gw - 1, x / 2);
                    int cell = cellY * gw + cellX;
                    if (!coneDark[cell] || shown != null && !shown[cell] || !litBy(ground, cellX, cellY, owner)) continue;
                    float value = UnitLights.pool(kind, x * .5f - lx, y * .5f - ly, dx0, dy0);
                    if (value > 0f) conePools[y * width + x] += value;
                }
            }
            coneFilled[coneFilledCount++] = gx0;
            coneFilled[coneFilledCount++] = gy0;
            coneFilled[coneFilledCount++] = gx1;
            coneFilled[coneFilledCount++] = gy1;
            coneBox(gx0, gy0, gx1, gy1);
            any = true;
        }
        return any;
    }

    /** Which cells are dark enough for a cone, reread when the ground or the night changes. */
    private void refreshConeDark(CellTopology ground, NightLighting night) {
        int n = ground.getWidth() * ground.getHeight();
        if (coneDark != null && coneDark.length == n && coneDarkSeen == ground.changeCount()
                && coneDarkNight != null && coneDarkNight.darkness() == night.darkness()
                && coneDarkNight.roofedDarkness() == night.roofedDarkness()) return;
        if (coneDark == null || coneDark.length != n) coneDark = new boolean[n];
        int w = ground.getWidth();
        for (int y = 0; y < ground.getHeight(); y++)
            for (int x = 0; x < w; x++)
                coneDark[y * w + x] = night.darknessAt(NightLighting.roofed(ground, x, y)) >= UnitLights.MIN_DARKNESS;
        coneDarkSeen = ground.changeCount();
        coneDarkNight = night;
    }

    private void coneBox(int x0, int y0, int x1, int y1) {
        coneX0 = Math.min(coneX0, x0);
        coneY0 = Math.min(coneY0, y0);
        coneX1 = Math.max(coneX1, x1);
        coneY1 = Math.max(coneY1, y1);
    }

    /** A field value with the rounding a lamp taken out leaves behind cleared, so unlit cells stay exactly unlit. */
    private static float settle(float value) {
        return value < 1e-5f ? 0f : value;
    }

    /**
     * Whether a lamp in building {@code owner} lights cell {@code (x, y)}: the building's own, or a wall touching it.
     * A lamp in the open ({@code owner} 0) never lights a wall under a roof: the roof stands storeys over the street,
     * and lighting its edge alone drew a bright band round every roof with the rooms behind it dark.
     */
    static boolean litBy(CellTopology ground, int x, int y, int owner) {
        if (owner == 0 && ground.isWall(x, y) && NightLighting.roofed(ground, x, y)) return false;
        if (ground.getBuildingId(x, y) == owner) return true;
        if (!ground.isWall(x, y)) return false;
        return ground.getBuildingId(x - 1, y) == owner || ground.getBuildingId(x + 1, y) == owner
                || ground.getBuildingId(x, y - 1) == owner || ground.getBuildingId(x, y + 1) == owner;
    }

    private float sample(float[] values,float x,float y) {
        float gx=x*2,gy=y*2; int ix=(int)gx,iy=(int)gy;
        float dx=gx-ix,dy=gy-iy; int i=iy*width+ix;
        return (values[i]*(1-dx)+values[i+1]*dx)*(1-dy)+(values[i+width]*(1-dx)+values[i+width+1]*dx)*dy;
    }
}
