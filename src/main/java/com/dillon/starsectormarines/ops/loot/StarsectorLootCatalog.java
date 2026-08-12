package com.dillon.starsectormarines.ops.loot;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds recovery candidates from the live vanilla/modded content registry. */
public final class StarsectorLootCatalog {

    private StarsectorLootCatalog() {}

    public static List<LootCandidate> candidates(LootRollRequest request) {
        if (request == null) return Collections.emptyList();
        SettingsAPI settings = Global.getSettings();
        if (settings == null) return Collections.emptyList();

        List<LootCandidate> out = new ArrayList<>();
        addCommodity(out, settings, request, Commodities.SUPPLIES, 5f, 10, 80);
        addCommodity(out, settings, request, Commodities.FUEL, 4f, 20, 120);
        addCommodity(out, settings, request, Commodities.MARINES, 1.5f, 5, 30);
        addCommodity(out, settings, request, Commodities.HEAVY_MACHINERY, 2f, 5, 35);
        addAiCores(out, settings, request);
        addWeapons(out, settings, request);
        return out;
    }

    private static void addAiCores(List<LootCandidate> out, SettingsAPI settings,
                                   LootRollRequest request) {
        if (AiCoreLootRules.permitsAlpha(request)) {
            addRareCommodity(out, settings, Commodities.ALPHA_CORE, 150_000, 0.03f);
        }
        if (AiCoreLootRules.permitsBeta(request)) {
            addRareCommodity(out, settings, Commodities.BETA_CORE, 30_000, 0.12f);
        }
        if (AiCoreLootRules.permitsGamma(request)) {
            addRareCommodity(out, settings, Commodities.GAMMA_CORE, 10_000, 0.30f);
        }
    }

    private static void addRareCommodity(List<LootCandidate> out, SettingsAPI settings,
                                         String id, int fallbackValue, float weight) {
        CommoditySpecAPI spec = settings.getCommoditySpec(id);
        if (spec == null) return;
        int value = spec.getBasePrice() > 0f ? Math.round(spec.getBasePrice()) : fallbackValue;
        out.add(new LootCandidate(LootKind.COMMODITY, id, spec.getName(), spec.getIconName(),
                Math.max(1, value), spec.getCargoSpace(), weight, 1, 1));
    }

    private static void addCommodity(List<LootCandidate> out, SettingsAPI settings,
                                     LootRollRequest request, String id, float baseWeight,
                                     int minQuantity, int maxQuantity) {
        CommoditySpecAPI spec = settings.getCommoditySpec(id);
        if (spec == null || spec.getBasePrice() <= 0f) return;
        float weight = baseWeight * industryCommodityMultiplier(request.targetIndustryId, id);
        out.add(new LootCandidate(LootKind.COMMODITY, id, spec.getName(), spec.getIconName(),
                Math.max(1, Math.round(spec.getBasePrice())), spec.getCargoSpace(), weight,
                minQuantity, maxQuantity));
    }

    private static void addWeapons(List<LootCandidate> out, SettingsAPI settings,
                                   LootRollRequest request) {
        FactionAPI faction = request.targetFactionId != null && Global.getSector() != null
                ? Global.getSector().getFaction(request.targetFactionId)
                : null;
        Set<String> knownWeapons = faction != null ? faction.getKnownWeapons() : null;
        Collection<String> weaponIds = knownWeapons != null && !knownWeapons.isEmpty()
                ? knownWeapons
                : allDroppableWeaponIds(settings);
        Set<String> priority = faction != null && faction.getPriorityWeapons() != null
                ? faction.getPriorityWeapons()
                : Collections.emptySet();
        Map<String, Float> sellFrequency = faction != null && faction.getWeaponSellFrequency() != null
                ? faction.getWeaponSellFrequency()
                : Collections.emptyMap();

        float industryMult = weaponIndustryMultiplier(request.targetIndustryId);
        for (String id : weaponIds) {
            if (id == null) continue;
            WeaponSpecAPI spec = settings.getWeaponSpec(id);
            if (!isDroppable(spec)) continue;
            float frequency = sellFrequency.getOrDefault(id, 1f);
            float weight = Math.max(0.05f, spec.getRarity()) * Math.max(0.1f, frequency);
            if (priority.contains(id)) weight *= 2f;
            weight *= industryMult;
            out.add(new LootCandidate(LootKind.WEAPON, id, spec.getWeaponName(),
                    weaponIcon(spec), Math.max(1, Math.round(spec.getBaseValue())),
                    weaponCargo(spec), weight, 1, 1));
        }
    }

    private static List<String> allDroppableWeaponIds(SettingsAPI settings) {
        List<String> ids = new ArrayList<>();
        for (WeaponSpecAPI spec : settings.getAllWeaponSpecs()) {
            if (isDroppable(spec)) ids.add(spec.getWeaponId());
        }
        return ids;
    }

    private static boolean isDroppable(WeaponSpecAPI spec) {
        if (spec == null || spec.getWeaponId() == null || spec.getBaseValue() <= 0f
                || spec.getRarity() <= 0f || spec.getTier() < 0) {
            return false;
        }
        WeaponAPI.WeaponType type = spec.getType();
        return type != WeaponAPI.WeaponType.DECORATIVE
                && type != WeaponAPI.WeaponType.SYSTEM
                && !spec.hasTag("no_drop");
    }

    private static String weaponIcon(WeaponSpecAPI spec) {
        String icon = spec.getTurretSpriteName();
        return icon != null ? icon : spec.getHardpointSpriteName();
    }

    private static float weaponCargo(WeaponSpecAPI spec) {
        switch (spec.getSize()) {
            case LARGE:  return 20f;
            case MEDIUM: return 10f;
            case SMALL:
            default:     return 5f;
        }
    }

    private static float industryCommodityMultiplier(String industryId, String commodityId) {
        if (industryId == null) return 1f;
        if (Industries.FUELPROD.equals(industryId) && Commodities.FUEL.equals(commodityId)) {
            return 3f;
        }
        if ((Industries.HEAVYINDUSTRY.equals(industryId) || Industries.ORBITALWORKS.equals(industryId))
                && Commodities.HEAVY_MACHINERY.equals(commodityId)) {
            return 3f;
        }
        if ((Industries.MILITARYBASE.equals(industryId) || Industries.HIGHCOMMAND.equals(industryId)
                || Industries.PATROLHQ.equals(industryId))
                && (Commodities.MARINES.equals(commodityId) || Commodities.SUPPLIES.equals(commodityId))) {
            return 2.5f;
        }
        if ((Industries.SPACEPORT.equals(industryId) || Industries.MEGAPORT.equals(industryId))
                && (Commodities.FUEL.equals(commodityId) || Commodities.SUPPLIES.equals(commodityId))) {
            return 2f;
        }
        return 1f;
    }

    private static float weaponIndustryMultiplier(String industryId) {
        if (Industries.HEAVYINDUSTRY.equals(industryId)
                || Industries.ORBITALWORKS.equals(industryId)
                || Industries.MILITARYBASE.equals(industryId)
                || Industries.HIGHCOMMAND.equals(industryId)) {
            return 2f;
        }
        return 1f;
    }
}
