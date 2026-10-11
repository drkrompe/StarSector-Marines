package com.dillon.starsectormarines.battle.world.gen.road;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

/** Authored mission road input; loaded once before generation, never through per-map disk I/O. */
public final class RoadCatalog {
    public static final String THUNDER_RUN = "data/world/roads/thunder-run.road.json";
    private static volatile RoadPlan thunderRun;
    private RoadCatalog() {}
    public static RoadPlan thunderRun() {
        if(thunderRun==null) throw new IllegalStateException("Mission roads have not been installed");
        return thunderRun;
    }
    public static boolean installed() {return thunderRun!=null;}
    public static void installThunderRun(JSONObject json) throws Exception {
        RoadPlan plan=RoadPlan.read(json);
        if(plan.width()!=320 || plan.height()!=144) throw new IllegalArgumentException("Thunder Run road plan must be 320 × 144");
        validateThunderRun(plan);
        thunderRun=plan;
    }
    public static void validateThunderRun(RoadPlan plan) {
        if(plan.width()!=320 || plan.height()!=144) throw new IllegalArgumentException("Thunder Run road plan must be 320 × 144");
        var raster=RoadRasterizer.rasterize(plan);
        for(int y=0;y<144;y++) for(int x=0;x<320;x++) {
            boolean reserved=(y>=68&&y<=82)||(x>=159&&x<=171)||(x>=269&&x<=281);
            if(raster.road(x,y)!=reserved || (raster.shoulder()[y*320+x] && !reserved))
                throw new IllegalArgumentException("Thunder Run keeps its reserved freight and ambush corridors; edit its material, wear or seed. New geometry requires a matching mission layout.");
        }
    }
    public static void loadBuiltins() throws Exception {installThunderRun(Global.getSettings().loadJSON(THUNDER_RUN,true));}
}
