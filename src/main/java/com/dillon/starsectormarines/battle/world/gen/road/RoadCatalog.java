package com.dillon.starsectormarines.battle.world.gen.road;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Point;
import com.dillon.starsectormarines.battle.world.gen.road.RoadPlan.Route;

/** Authored mission road inputs; installed atomically before generation, with no per-map disk I/O. */
public final class RoadCatalog {
    public static final String THUNDER_RUN = "data/world/roads/thunder-run.road.json";
    public static final String RELIEF = "data/world/roads/relief.road.json";
    public static final String COUNTERATTACK = "data/world/roads/counterattack.road.json";
    public static final String JUNCTION = "data/world/roads/junction.road.json";
    public static final String LAST_LIFT = "data/world/roads/last-lift.road.json";
    public static final String RELAY_NINE = "data/world/roads/relay-nine.road.json";
    public static final String SHEPHERD_BASIN = "data/world/roads/shepherd-basin.road.json";
    public static final List<String> BUILTINS = List.of(THUNDER_RUN, RELIEF, COUNTERATTACK, JUNCTION, LAST_LIFT, RELAY_NINE, SHEPHERD_BASIN);
    private static volatile Map<String, RoadPlan> plans = Map.of();
    private RoadCatalog() {}
    @FunctionalInterface public interface Source { JSONObject read(String path) throws Exception; }
    public static RoadPlan plan(String path) {
        RoadPlan plan = plans.get(path);
        if (plan == null) throw new IllegalStateException("Mission roads have not been installed: " + path);
        return plan;
    }
    public static RoadPlan thunderRun() { return plan(THUNDER_RUN); }
    public static boolean installed() { return plans.size() == BUILTINS.size(); }
    /** Relief's larger debug extents extend its two boundary approaches, never scale the compound. */
    public static RoadPlan relief(int width, int height) {
        RoadPlan source = plan(RELIEF);
        if (width == source.width() && height == source.height()) return source;
        if (width < source.width() || height < source.height()) throw new IllegalArgumentException("Relief road extent is too small");
        return new RoadPlan(width, height, source.routes().stream().map(r -> new Route(r.id(),
                r.points().stream().map(p -> new Point(p.x() == source.width()-1 ? width-1 : p.x(),
                        p.y() == source.height()-1 ? height-1 : p.y())).toList(),
                r.width(), r.shoulder(), r.wear(), r.seed(), r.material(), r.endCap(), r.autoEdge())).toList());
    }
    public static void validate(String path, RoadPlan plan) {
        int[] size = switch (path) {
            case THUNDER_RUN -> new int[]{320,144};
            case RELIEF -> new int[]{144,64};
            case COUNTERATTACK -> new int[]{160,96};
            case JUNCTION -> new int[]{192,112};
            case LAST_LIFT -> new int[]{192,128};
            case RELAY_NINE -> new int[]{208,128};
            case SHEPHERD_BASIN -> new int[]{304,224};
            default -> throw new IllegalArgumentException("Unknown mission road plan: " + path);
        };
        if (plan.width() != size[0] || plan.height() != size[1])
            throw new IllegalArgumentException(path + " requires " + size[0] + " × " + size[1]);
        if (path.equals(THUNDER_RUN)) validateThunderRun(plan);
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
    public static void loadBuiltins(Source source) throws Exception {
        Map<String,RoadPlan> next = new LinkedHashMap<>();
        for (String path : BUILTINS) {
            RoadPlan plan = RoadPlan.read(source.read(path));
            validate(path, plan);
            next.put(path, plan);
        }
        plans = Map.copyOf(next);
    }
    public static void loadBuiltins() throws Exception { loadBuiltins(path -> Global.getSettings().loadJSON(path,true)); }
}
