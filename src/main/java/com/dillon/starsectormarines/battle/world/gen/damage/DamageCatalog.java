package com.dillon.starsectormarines.battle.world.gen.damage;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

/** Opening checkpoint damage, loaded once alongside authored roads and terrain. */
public final class DamageCatalog {
    public static final String RELIEF = "data/world/damage/relief.damage.json";
    private static volatile DamagePlan relief;
    private DamageCatalog() {}
    @FunctionalInterface public interface Source { JSONObject read(String path) throws Exception; }
    public static boolean installed() { return relief != null; }
    public static DamagePlan relief() {
        if (relief == null) throw new IllegalStateException("Damage catalog has not been installed");
        return relief;
    }
    public static void validate(DamagePlan plan) {
        if (plan.width() != 144 || plan.height() != 64) throw new IllegalArgumentException("Relief damage requires 144 x 64 cells");
    }
    public static void loadBuiltins(Source source) throws Exception {
        DamagePlan plan = DamagePlan.read(source.read(RELIEF)); validate(plan); relief = plan;
    }
    public static void loadBuiltins() throws Exception { loadBuiltins(path -> Global.getSettings().loadJSON(path, true)); }
}
