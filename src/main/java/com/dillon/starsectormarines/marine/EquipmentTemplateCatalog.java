package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Stable collectible-template identities and their base-game cargo issue costs. */
public final class EquipmentTemplateCatalog {

    private static final List<MarineWeapon> PLAYER_PRIMARIES = List.of(
            MarineWeapon.FIELD_RIFLE,
            MarineWeapon.PULSE_RIFLE,
            MarineWeapon.SMG,
            MarineWeapon.SQUAD_AUTOMATIC,
            MarineWeapon.DMR);
    private static final Set<String> IDS = buildIds();

    private EquipmentTemplateCatalog() {
    }

    public static List<MarineWeapon> playerPrimaries() {
        return PLAYER_PRIMARIES;
    }

    public static String primaryId(MarineWeapon weapon, EquipmentGrade grade) {
        return "equipment-template:" + weapon.id + ":"
                + grade.name().toLowerCase(Locale.ROOT);
    }

    public static String armorId(MarineArmorPattern armor) {
        return "equipment-template:" + armor.id;
    }

    public static String specialId(MarineSecondary special) {
        return "equipment-template:" + special.specialEquipmentId;
    }

    public static EquipmentTemplateCard primary(MarineWeapon weapon, EquipmentGrade grade) {
        requirePlayerPrimary(weapon);
        if (grade == null) throw new IllegalArgumentException("Primary template grade is required");
        return new EquipmentTemplateCard(primaryId(weapon, grade), weapon.catalogName(grade),
                EquipmentTemplateCard.Kind.PRIMARY, primaryCost(weapon, grade));
    }

    public static EquipmentTemplateCard armor(MarineArmorPattern armor) {
        if (armor == null) throw new IllegalArgumentException("Armor template is required");
        return new EquipmentTemplateCard(armorId(armor), armor.displayName,
                EquipmentTemplateCard.Kind.ARMOR, armorCost(armor));
    }

    public static EquipmentTemplateCard special(MarineSecondary special) {
        if (special == null) throw new IllegalArgumentException("Special template is required");
        return new EquipmentTemplateCard(specialId(special), special.displayName(),
                EquipmentTemplateCard.Kind.SPECIAL, specialCost(special));
    }

    public static EquipmentTemplateCard require(String id) {
        for (MarineWeapon weapon : PLAYER_PRIMARIES) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                if (primaryId(weapon, grade).equals(id)) return primary(weapon, grade);
            }
        }
        for (MarineArmorPattern armor : MarineArmorPattern.values()) {
            if (armorId(armor).equals(id)) return armor(armor);
        }
        for (MarineSecondary special : MarineSecondary.values()) {
            if (specialId(special).equals(id)) return special(special);
        }
        throw new IllegalArgumentException("Unknown equipment template id '" + id + "'");
    }

    public static boolean contains(String id) {
        return id != null && IDS.contains(id);
    }

    public static List<EquipmentTemplateCard> all() {
        List<EquipmentTemplateCard> cards = new ArrayList<>();
        for (MarineWeapon weapon : PLAYER_PRIMARIES) {
            for (EquipmentGrade grade : EquipmentGrade.values()) cards.add(primary(weapon, grade));
        }
        for (MarineArmorPattern armor : MarineArmorPattern.values()) cards.add(armor(armor));
        for (MarineSecondary special : MarineSecondary.values()) cards.add(special(special));
        return Collections.unmodifiableList(cards);
    }

    private static EquipmentTemplateCost primaryCost(
            MarineWeapon weapon, EquipmentGrade grade) {
        if (weapon == MarineWeapon.FIELD_RIFLE && grade == EquipmentGrade.SERVICE) {
            return EquipmentTemplateCost.ZERO;
        }
        int supplies = switch (grade) {
            case SURPLUS -> 1;
            case SERVICE -> 2;
            case MILSPEC -> 3;
            case MASTERWORK -> 5;
        };
        if (weapon == MarineWeapon.DMR || weapon == MarineWeapon.SQUAD_AUTOMATIC) supplies++;
        int armaments = switch (grade) {
            case SURPLUS, SERVICE -> 0;
            case MILSPEC -> 1;
            case MASTERWORK -> 2;
        };
        return new EquipmentTemplateCost(supplies, armaments, 0, 0);
    }

    private static EquipmentTemplateCost armorCost(MarineArmorPattern armor) {
        return switch (armor.tier) {
            case 1 -> EquipmentTemplateCost.ZERO;
            case 2 -> new EquipmentTemplateCost(1, 0, 0, 0);
            case 3 -> new EquipmentTemplateCost(2, 0, 1, 0);
            default -> new EquipmentTemplateCost(3, 1, 2, 0);
        };
    }

    private static EquipmentTemplateCost specialCost(MarineSecondary special) {
        return switch (special) {
            case SMOKE_GRENADE -> new EquipmentTemplateCost(2, 0, 0, 0);
            case SATCHEL_CHARGE -> new EquipmentTemplateCost(2, 1, 1, 0);
            case FRAG_GRENADE -> new EquipmentTemplateCost(2, 1, 0, 0);
            case ROCKET_LAUNCHER, ANTI_MATERIEL_RIFLE ->
                    new EquipmentTemplateCost(3, 2, 1, 0);
        };
    }

    private static Set<String> buildIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (MarineWeapon weapon : PLAYER_PRIMARIES) {
            for (EquipmentGrade grade : EquipmentGrade.values()) {
                ids.add(primaryId(weapon, grade));
            }
        }
        for (MarineArmorPattern armor : MarineArmorPattern.values()) ids.add(armorId(armor));
        for (MarineSecondary special : MarineSecondary.values()) ids.add(specialId(special));
        return Collections.unmodifiableSet(ids);
    }

    private static void requirePlayerPrimary(MarineWeapon weapon) {
        if (!PLAYER_PRIMARIES.contains(weapon)) {
            throw new IllegalArgumentException("Weapon is not a player equipment template: " + weapon);
        }
    }
}
