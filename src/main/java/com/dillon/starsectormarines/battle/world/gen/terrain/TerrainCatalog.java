package com.dillon.starsectormarines.battle.world.gen.terrain;

import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.fs.starfarer.api.Global;
import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable mission terrain inputs, installed before generation with no per-map disk I/O. */
public final class TerrainCatalog {
    public static final String RELIEF = "data/world/terrain/relief.terrain.json";
    public static final String COUNTERATTACK = "data/world/terrain/counterattack.terrain.json";
    public static final String JUNCTION = "data/world/terrain/junction.terrain.json";
    public static final String RELAY_NINE = "data/world/terrain/relay-nine.terrain.json";
    public static final String THUNDER_RUN = "data/world/terrain/thunder-run.terrain.json";
    public static final List<String> BUILTINS = List.of(RELIEF, COUNTERATTACK, JUNCTION, RELAY_NINE, THUNDER_RUN);
    private static volatile Map<String, TerrainPlan> plans = Map.of();
    private TerrainCatalog() {}
    @FunctionalInterface public interface Source { JSONObject read(String path) throws Exception; }
    public static boolean installed() { return plans.size() == BUILTINS.size(); }
    public static TerrainPlan plan(String path, SurfacePalette surface) {
        TerrainPlan plan = plans.get(path);
        if (plan == null) throw new IllegalStateException("Mission terrain has not been installed: " + path);
        return forSurface(plan, surface);
    }
    /** Larger Relief fixtures extend the outer ridges without moving the checkpoint. */
    public static TerrainPlan relief(int width, int height, SurfacePalette surface) {
        TerrainPlan source = plan(RELIEF, surface);
        if (width == source.width() && height == source.height()) return source;
        if (width < source.width() || height < source.height()) throw new IllegalArgumentException("Relief terrain extent is too small");
        return new TerrainPlan(width, height, source.finish(), source.faceDepth(), source.ruggedness(), source.seed(),
                source.strokes().stream().map(s -> new TerrainPlan.Stroke(s.mode(), s.radius(),
                        s.points().stream().map(p -> new TerrainPlan.Point(
                                p.x() == source.width() - 1 ? width - 1 : p.x(),
                                p.y() == source.height() - 1 ? height - 1 : p.y())).toList())).toList());
    }
    /** Adapt material without changing a mission's authored obstruction geometry. */
    static TerrainPlan forSurface(TerrainPlan plan, SurfacePalette surface) {
        TerrainPlan.Finish finish=surface==SurfacePalette.FROZEN?TerrainPlan.Finish.ICE
                :surface==SurfacePalette.VERDANT?plan.finish():TerrainPlan.Finish.ROCK;
        if(plan.finish()==finish)return plan;
        return new TerrainPlan(plan.width(), plan.height(), finish,
                plan.faceDepth(), plan.ruggedness(), plan.seed(), plan.strokes());
    }
    public static void validate(String path, TerrainPlan plan) {
        int width, height;
        switch (path) {
            case RELIEF -> { width = 112; height = 64; }
            case COUNTERATTACK -> { width = 160; height = 96; }
            case JUNCTION -> { width = 192; height = 112; }
            case RELAY_NINE -> { width = 208; height = 128; }
            case THUNDER_RUN -> { width = 320; height = 144; }
            default -> throw new IllegalArgumentException("Unknown mission terrain plan: " + path);
        }
        if (plan.width() != width || plan.height() != height)
            throw new IllegalArgumentException(path + " requires " + width + " x " + height);
    }
    public static void loadBuiltins(Source source) throws Exception {
        Map<String, TerrainPlan> next = new LinkedHashMap<>();
        for (String path : BUILTINS) {
            TerrainPlan plan = TerrainPlan.read(source.read(path));
            validate(path, plan);
            next.put(path, plan);
        }
        plans = Map.copyOf(next);
    }
    public static void loadBuiltins() throws Exception { loadBuiltins(path -> Global.getSettings().loadJSON(path, true)); }
}
