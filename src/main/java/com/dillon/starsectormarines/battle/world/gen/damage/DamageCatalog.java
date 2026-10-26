package com.dillon.starsectormarines.battle.world.gen.damage;

import com.fs.starfarer.api.Global;
import org.json.JSONObject;

/** Authored battle damage for story maps, loaded once alongside authored roads and terrain. */
public final class DamageCatalog {
    public static final String RELIEF = "data/world/damage/relief.damage.json";
    public static final String SHEPHERD_BASIN = "data/world/damage/shepherd-basin.damage.json";
    public static final String DUST_LEDGER = "data/world/damage/dust-ledger.damage.json";
    public static final String BLIND_WATCH = "data/world/damage/blind-watch.damage.json";
    private static volatile DamagePlan relief, shepherdBasin, dustLedger, blindWatch;
    private DamageCatalog() {}
    @FunctionalInterface public interface Source { JSONObject read(String path) throws Exception; }
    public static boolean installed() { return relief != null; }
    public static DamagePlan relief() {
        if (relief == null) throw new IllegalStateException("Damage catalog has not been installed");
        return relief;
    }
    /** The fight for Asharu's recovery basin, before Vale's crew held it. */
    public static DamagePlan shepherdBasin() {
        if (shepherdBasin == null) throw new IllegalStateException("Damage catalog has not been installed");
        return shepherdBasin;
    }
    /** Saltwell Cross on Asharu: old terraforming ruins and fall debris, not a battle. */
    public static DamagePlan dustLedger() {
        if (dustLedger == null) throw new IllegalStateException("Damage catalog has not been installed");
        return dustLedger;
    }
    /** The Sounding on Warion: a season's rover ruts and micrometeorite pitting, not a battle. */
    public static DamagePlan blindWatch() {
        if (blindWatch == null) throw new IllegalStateException("Damage catalog has not been installed");
        return blindWatch;
    }
    public static void validateBlindWatch(DamagePlan plan) {
        if (plan.width() != 216 || plan.height() != 112) throw new IllegalArgumentException("Blind Watch damage requires 216 x 112 cells");
    }
    public static void validateDustLedger(DamagePlan plan) {
        if (plan.width() != 240 || plan.height() != 112) throw new IllegalArgumentException("Dust Ledger damage requires 240 x 112 cells");
    }
    public static void validateShepherdBasin(DamagePlan plan) {
        if (plan.width() != 304 || plan.height() != 224) throw new IllegalArgumentException("Shepherd basin damage requires 304 x 224 cells");
    }
    public static void validate(DamagePlan plan) {
        if (plan.width() != 144 || plan.height() != 64) throw new IllegalArgumentException("Relief damage requires 144 x 64 cells");
    }
    public static void loadBuiltins(Source source) throws Exception {
        DamagePlan plan = DamagePlan.read(source.read(RELIEF)); validate(plan);
        DamagePlan basin = DamagePlan.read(source.read(SHEPHERD_BASIN)); validateShepherdBasin(basin);
        DamagePlan crossing = DamagePlan.read(source.read(DUST_LEDGER)); validateDustLedger(crossing);
        DamagePlan sounding = DamagePlan.read(source.read(BLIND_WATCH)); validateBlindWatch(sounding);
        relief = plan; shepherdBasin = basin; dustLedger = crossing; blindWatch = sounding;
    }
    public static void loadBuiltins() throws Exception { loadBuiltins(path -> Global.getSettings().loadJSON(path, true)); }
}
