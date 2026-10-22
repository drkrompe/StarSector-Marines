package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.LoadoutEffectiveness;
import com.dillon.starsectormarines.marine.SquadArmorDoctrine;
import com.dillon.starsectormarines.marine.SquadArmorPlan;
import com.dillon.starsectormarines.marine.SquadWeaponDoctrine;
import com.dillon.starsectormarines.ops.FleetArmoryViewModel.CardFace;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The issued-card chip: how every armoury screen names a squad's card at a glance —
 * its face, name, Firepower or Protection Rating, and the Restricted seal when it bears
 * one. Barracks stacks two compact chips on each squad card (the rating as a bare
 * percentage, coloured by kind); the Loadout screen's squad view opens on the same two
 * at full size, where the rating is spelled out on the line above the name. The {@code armory-card-chip}
 * component draws what this builds, so the two screens cannot disagree.
 */
final class CardChips {
    private CardChips() { }

    static Map<String, Object> weapons(String id, SquadWeaponDoctrine doctrine, boolean compact) {
        int rating = LoadoutEffectiveness.weaponRating(doctrine);
        return compact
                ? chip(id, FleetArmoryViewModel.weaponCardFace(doctrine), "", Strings.format("loadoutCardRatingShort", rating), "weapon", true)
                : chip(id, FleetArmoryViewModel.weaponCardFace(doctrine), Strings.format("openingLoadoutFireRating", rating), "", "weapon", false);
    }

    static Map<String, Object> sheet(String id, SquadArmorPlan plan, SquadArmorDoctrine issued, boolean compact) {
        int rating = LoadoutEffectiveness.armorRating(issued);
        return compact
                ? chip(id, FleetArmoryViewModel.armorCardFace(plan, issued), "", Strings.format("loadoutCardRatingShort", rating), "armor", true)
                : chip(id, FleetArmoryViewModel.armorCardFace(plan, issued), Strings.format("openingLoadoutGuardRating", rating), "", "armor", false);
    }

    /** A slot with no card in it: laid out with the same ids, drawn as nothing. */
    static Map<String, Object> empty(String id, boolean compact) {
        return chip(id, null, "", "", "empty", compact);
    }

    private static Map<String, Object> chip(String id, CardFace face, String kind, String rating,
                                            String accent, boolean compact) {
        Map<String, Object> chip = new LinkedHashMap<>();
        chip.put("id", id);
        for (String child : List.of("slot", "seal", "icon", "text", "kind", "name", "rating")) {
            chip.put(child + "Id", id + ":" + child);
        }
        chip.put("classes", "card-chip card-chip-" + accent + (compact ? " card-chip-compact" : ""));
        chip.put("sealClasses", face != null && face.restricted() ? "card-chip-seal" : "card-chip-seal card-chip-seal-absent");
        chip.put("icon", face == null || face.icon() == null ? "" : face.icon());
        chip.put("kind", kind);
        chip.put("name", face == null ? "" : face.name());
        chip.put("rating", rating);
        return chip;
    }
}
