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
                float pool=useFog && !fog.cellRevealedArray()[cellIndex]
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
                    && !fog.cellRevealedArray()[(int)lamp.y()*fog.gridWidth()+(int)lamp.x()]) continue;
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
    /** Ground contact, splayed feet and an elevated head remain visible during the outage. */
    private void floodlight(DrawList out, NightLighting.Lamp lamp, float x, float y,
                            float px, boolean powered, float alpha) {
        out.addLine(layer(),x-px*.48f,y+px*.12f,x+px*.48f,y+px*.12f,px*.42f,0,0,0,.4f*alpha);
        for (int leg=0;leg<3;leg++) {
            double angle=Math.toRadians(30+leg*120);
            float footX=x+(float)Math.cos(angle)*px*.48f;
            float footY=y+(float)Math.sin(angle)*px*.34f;
            out.addLine(layer(),x,y-px*.12f,footX,footY,Math.max(2,px*.12f),.09f,.1f,.11f,alpha);
            out.addLine(layer(),x,y-px*.12f,footX,footY,Math.max(1,px*.05f),.46f,.43f,.34f,alpha);
        }
        float headX=x+px*.16f, headY=y-px*.82f;
        out.addLine(layer(),x,y,headX,headY,Math.max(2,px*.15f),.09f,.1f,.12f,alpha);
        out.addLine(layer(),x-px*.025f,y,headX-px*.025f,headY,Math.max(1,px*.055f),.58f,.54f,.39f,alpha);
        double bearing=Math.toRadians(lamp.bearingDegrees());
        float forwardX=(float)Math.cos(bearing),forwardY=-(float)Math.sin(bearing);
        float crossX=-forwardY,crossY=forwardX;
        float faceX=headX+forwardX*px*.18f,faceY=headY+forwardY*px*.18f;
        out.addLine(layer(),headX-crossX*px*.38f,headY-crossY*px*.38f,
                headX+crossX*px*.38f,headY+crossY*px*.38f,px*.36f,.08f,.09f,.11f,alpha);
        out.addLine(layer(),headX-crossX*px*.31f,headY-crossY*px*.31f,
                headX+crossX*px*.31f,headY+crossY*px*.31f,px*.22f,.4f,.42f,.43f,alpha);
        if (powered) for (int halo=3;halo>=1;halo--)
            out.addLine(layer(),faceX-crossX*px*.28f,faceY-crossY*px*.28f,
                    faceX+crossX*px*.28f,faceY+crossY*px*.28f,px*(.12f+halo*.1f),
                    lamp.red(),lamp.green(),lamp.blue(),.07f*alpha);
        out.addLine(layer(),faceX-crossX*px*.28f,faceY-crossY*px*.28f,
                faceX+crossX*px*.28f,faceY+crossY*px*.28f,Math.max(1,px*.08f),
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
