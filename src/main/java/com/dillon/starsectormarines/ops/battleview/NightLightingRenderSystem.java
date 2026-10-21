package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.NightLighting;
import com.dillon.starsectormarines.render2d.PolyMesh;


/** Outdoor night treatment shared by battle and map-review hosts. Lamps never reveal fog. */
final class NightLightingRenderSystem implements RenderSystem {
    private final PolyMesh shade = new PolyMesh(4096);
    private final NightLightingPass pass = new NightLightingPass();
    private final DoodadRenderSystem doodads;

    NightLightingRenderSystem(BattleSprites sprites) { doodads = new DoodadRenderSystem(sprites); }
    void dispose() { pass.dispose(); }
    private CellTopology topology;
    private NightLighting lighting;
    private float[] pools, reds, greens, blues;
    private int width;

    public RenderLayer layer() { return RenderLayer.NIGHT_LIGHTING; }

    public void collect(RenderContext ctx, DrawList out) {
        var ground = ctx.sim.getTopology();
        var night = ground.nightLighting();
        if (night == null) return;
        if (ground != topology || night != lighting) bake(ground, night);
        var camera = ctx.camera;
        var visible = camera.visibleCells(1, ground.getWidth(), ground.getHeight());
        var fog=ctx.sim.getFogOfWar();
        boolean useFog=ctx.hostProfile.fogClipsLighting() && fog.isInitialized();
        pass.reset(camera);
        if (useFog) doodads.collectLightingSurfaces(ctx, pass.surfaces());
        boolean masked = pass.surfaces().count(RenderLayer.FOG) > 0;
        int steps = camera.cellPxSize() >= 24 ? 8 : camera.cellPxSize() >= 12 ? 4 : 2;
        shade.reset();
        for (int y = visible.minY(); y <= visible.maxY(); y++) for (int x = visible.minX(); x <= visible.maxX(); x++) {
            if (ground.getGroundKind(x,y) == CellTopology.GroundKind.VOID) continue;
            for (int sy=0; sy<steps; sy++) for (int sx=0; sx<steps; sx++) {
                float px=x+(sx+.5f)/steps, py=y+(sy+.5f)/steps;
                int cellIndex = y * ground.getWidth() + x;
                float pool=useFog && !fog.cellShownArray()[cellIndex]
                        ? 0f : sample(pools,px,py);
                float x0=Math.round(camera.cellToScreenX(x+(float)sx/steps));
                float x1=Math.round(camera.cellToScreenX(x+(sx+1f)/steps));
                float y0=Math.round(camera.cellToScreenY(y+(float)sy/steps));
                float y1=Math.round(camera.cellToScreenY(y+(sy+1f)/steps));
                appendShade(masked ? pass.fogShade() : shade, night, pool, px, py,
                        x0, y0, x1, y1, ctx.alphaMult);
                if (masked) appendShade(pass.litShade(), night, sample(pools,px,py), px, py,
                        x0, y0, x1, y1, ctx.alphaMult);
            }
        }
        if (masked) out.addCustom(layer(), pass);
        else out.addPoly(layer(),shade);
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
            out.addLine(layer(),x-px*.3f,y,x+px*.3f,y,Math.max(2,px*.24f),.08f,.08f,.07f,ctx.alphaMult);
            for (int halo=3;halo>=1;halo--)
                out.addLine(layer(),x-px*.22f,y,x+px*.22f,y,px*(.14f+halo*.13f),lamp.red(),lamp.green(),lamp.blue(),.08f*ctx.alphaMult);
            out.addLine(layer(),x-px*.22f,y,x+px*.22f,y,Math.max(1,px*.07f),1,.94f,.75f,ctx.alphaMult);
        }
    }

    private void appendShade(PolyMesh mesh, NightLighting night, float pool, float px, float py,
                             float x0, float y0, float x1, float y1, float alphaMult) {
        float dark=night.darkness()*(1-.92f*Math.min(1,pool));
        float warm=.11f*Math.min(1,pool);
        float alpha=dark+warm*(1-dark), weight=dark*(1-warm);
        if (alpha<=0f) return;
        float divisor=Math.max(.001f,pool);
        float r=(.012f*weight+warm*sample(reds,px,py)/divisor)/alpha;
        float g=(.019f*weight+warm*sample(greens,px,py)/divisor)/alpha;
        float b=(.037f*weight+warm*sample(blues,px,py)/divisor)/alpha;
        mesh.appendQuad(x0,y0,x1,y0,x1,y1,x0,y1,r,g,b,alpha*alphaMult);
    }
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

    private void bake(CellTopology ground, NightLighting night) {
        topology=ground; lighting=night; width=ground.getWidth()*2+1;
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
