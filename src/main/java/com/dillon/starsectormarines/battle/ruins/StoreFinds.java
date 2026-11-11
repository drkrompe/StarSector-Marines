package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.salvage.SalvageCache;
import com.dillon.starsectormarines.battle.salvage.SalvagePlacement;
import com.dillon.starsectormarines.battle.salvage.SalvageResource;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleFunction;

/**
 * A plant site's supply finds ({@code ruins-expedition.md}, Finds lie where the plant kept its goods): each is one of
 * the loads its stores still hold ({@link RuinsDelve.Interior#loads}), taken as the goods it is, never a bundle dropped
 * on open floor. The delve keeps its count and its far share: the far share from finished goods, the rest from the
 * other stores (raw stock, the maintenance shop's spares), either topping up the other when it runs short. A picked
 * load stands on its recovery skid (its {@code -cache} art); the rest stand as cover.
 */
public final class StoreFinds {

    /** The space whose loads are the far finds. */
    static final String FAR_SPACE = RuinsDelve.Interior.spaceName("finished-goods");
    /** A load's art on the recovery skid: its own id with this suffix. */
    public static final String CACHE_SUFFIX = "-cache";
    private static final String PREFIX = "doodad.ruins-store-";

    /** The goods a load is: the commodity it settles as, its name, and the field resource that stands for it. */
    record Goods(String commodity, String name, SalvageResource resource) { }

    /** The base game's goods by the load that holds them ({@code commodities.csv}). */
    static final Map<String, Goods> GOODS = Map.of(
            "metals", new Goods("metals", "Metals", SalvageResource.METALS),
            "transplutonics", new Goods("rare_metals", "Transplutonics", SalvageResource.METALS),
            "volatiles", new Goods("volatiles", "Volatiles", SalvageResource.SUPPLIES),
            "organics", new Goods("organics", "Organics", SalvageResource.SUPPLIES),
            "ore", new Goods("ore", "Ore", SalvageResource.METALS),
            "transplutonic-ore", new Goods("rare_ore", "Transplutonic Ore", SalvageResource.METALS),
            "fuel", new Goods("fuel", "Fuel", SalvageResource.SUPPLIES),
            "domestic-goods", new Goods("domestic_goods", "Domestic Goods", SalvageResource.SUPPLIES),
            "supplies", new Goods("supplies", "Supplies", SalvageResource.SUPPLIES),
            "heavy-machinery", new Goods("heavy_machinery", "Heavy Machinery", SalvageResource.MACHINERY));

    /**
     * The base game's base prices, for a host with no settings loaded (headless runs); in the game the commodity's own
     * spec is read instead, so a mod's prices hold.
     */
    public static final Map<String, Double> BASE_PRICE = Map.of("metals", 30d, "rare_metals", 200d, "volatiles", 250d,
            "organics", 30d, "ore", 10d, "rare_ore", 75d, "fuel", 25d, "domestic_goods", 50d, "supplies", 100d,
            "heavy_machinery", 150d);

    /** A commodity's base price: its own spec's in the game, else the base game's ({@link #BASE_PRICE}). */
    public static double basePrice(String commodity) {
        SettingsAPI settings = Global.getSettings();
        CommoditySpecAPI spec = settings == null ? null : settings.getCommoditySpec(commodity);
        return spec != null ? spec.getBasePrice() : BASE_PRICE.getOrDefault(commodity, 0d);
    }

    /** Stands a load's recovery-skid art at a cell, or null where that art is missing. */
    public interface Skid {
        Doodad at(String doodadId, int x, int y);
    }

    /**
     * One load taken as a find.
     *
     * @param load     the load it is
     * @param x        the cell the find is recovered at: open floor beside the load
     * @param y        see {@code x}
     * @param quantity how much of its goods it holds
     */
    public record Pick(RuinsDelve.Load load, int x, int y, int quantity) {
        /** The commodity it settles as. */
        public String commodity() { return goodsOf(load.goods()).commodity(); }
    }

    private StoreFinds() { }

    /** The goods a load's doodad id holds, or null for one that is no store load. */
    static Goods goodsOf(String loadId) {
        if (loadId == null || !loadId.startsWith(PREFIX)) return null;
        return GOODS.get(loadId.substring(PREFIX.length()));
    }

    /**
     * Picks a delve's {@code caches} finds from its stores' loads, the far share from finished goods first.
     *
     * @param walkable whether a cell is open floor a squad can stand on
     * @param price    a commodity's base price
     * @return the finds, fewer when the stores hold fewer loads or a load has no open floor beside it
     */
    public static List<Pick> pick(List<RuinsDelve.Load> loads, int caches, long seed,
                                  BiPredicate<Integer, Integer> walkable, ToDoubleFunction<String> price) {
        List<RuinsDelve.Load> far = new ArrayList<>(), near = new ArrayList<>();
        for (RuinsDelve.Load load : loads) {
            if (goodsOf(load.goods()) == null || beside(load.footprint(), walkable) == null) continue;
            (FAR_SPACE.equals(load.space()) ? far : near).add(load);
        }
        Random rng = new Random(seed ^ 0x53544f5245L);
        Collections.shuffle(far, rng);
        Collections.shuffle(near, rng);
        List<RuinsDelve.Load> order = new ArrayList<>();
        int farShare = Math.min(SalvagePlacement.Delve.farQuota(caches), far.size());
        order.addAll(far.subList(0, farShare));
        order.addAll(near);
        order.addAll(far.subList(farShare, far.size()));
        double value = fieldCacheValue(price);
        List<Pick> picks = new ArrayList<>();
        for (RuinsDelve.Load load : order) {
            if (picks.size() >= caches) break;
            int[] at = beside(load.footprint(), walkable);
            double each = price.applyAsDouble(goodsOf(load.goods()).commodity());
            picks.add(new Pick(load, at[0], at[1], quantity(value, each)));
        }
        return List.copyOf(picks);
    }

    /** What one field cache is worth: its metals at their base price. */
    static double fieldCacheValue(ToDoubleFunction<String> price) {
        return SalvageResource.METALS.cacheQuantity * price.applyAsDouble(SalvageResource.METALS.commodityId);
    }

    /** How much of goods worth {@code each} a find worth {@code value} holds: at least one. */
    static int quantity(double value, double each) {
        return each > 0 ? Math.max(1, (int) Math.round(value / each)) : 1;
    }

    /** The open cell beside a footprint's edge (never its corners), nearest its middle, or null. */
    static int[] beside(RuinsDelve.Area footprint, BiPredicate<Integer, Integer> walkable) {
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        double cx = (footprint.left() + footprint.right()) / 2.0, cy = (footprint.bottom() + footprint.top()) / 2.0;
        for (int y = footprint.bottom() - 1; y <= footprint.top() + 1; y++) {
            for (int x = footprint.left() - 1; x <= footprint.right() + 1; x++) {
                boolean outX = x < footprint.left() || x > footprint.right();
                boolean outY = y < footprint.bottom() || y > footprint.top();
                if (outX == outY || !walkable.test(x, y)) continue;
                double d = (x - cx) * (x - cx) + (y - cy) * (y - cy);
                if (d < bestD) { bestD = d; best = new int[] {x, y}; }
            }
        }
        return best;
    }

    /** The finds as field caches, each recovered at its open cell and settled as its own goods. */
    public static List<SalvageCache> caches(List<Pick> picks) {
        List<SalvageCache> out = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) {
            Pick pick = picks.get(i);
            Goods goods = goodsOf(pick.load().goods());
            out.add(new SalvageCache("store-find-" + i, pick.x(), pick.y(), goods.resource(), pick.quantity())
                    .ofGoods(goods.commodity(), goods.name()));
        }
        return List.copyOf(out);
    }

    /**
     * Stands each picked load on its recovery skid: the doodad at the load's cell with the load's id is replaced by its
     * {@code -cache} art. A load whose skid art is missing keeps its own.
     *
     * @return how many were replaced
     */
    public static int dress(List<Doodad> doodads, List<Pick> picks, Skid skid) {
        int dressed = 0;
        for (Pick pick : picks) {
            RuinsDelve.Load load = pick.load();
            for (int i = 0; i < doodads.size(); i++) {
                Doodad d = doodads.get(i);
                if (d.cellX != load.at().x() || d.cellY != load.at().y() || !load.goods().equals(d.definitionId())) continue;
                Doodad cache = skid.at(load.goods() + CACHE_SUFFIX, d.cellX, d.cellY);
                if (cache != null) {
                    doodads.set(i, cache);
                    dressed++;
                }
                break;
            }
        }
        return dressed;
    }
}
