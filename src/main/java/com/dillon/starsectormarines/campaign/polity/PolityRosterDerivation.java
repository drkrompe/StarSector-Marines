package com.dillon.starsectormarines.campaign.polity;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ops.RiskLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds the player faction's ground doctrine from what the polity has been
 * given and what it can make ({@code polity-ground-doctrine.md}).
 *
 * <p>Pure: three arguments in, one {@link GroundRosterProfile} out, and the
 * same arguments always give the same profile. It reads the installed equipment
 * template catalog only to recover the Common floor, and touches no campaign
 * state, no market, and no registry installation. The daily rebuild, the
 * persisted released-card set, and the registration under the player faction id
 * belong to the campaign tier and call in here.
 *
 * <h2>The three laws it implements</h2>
 * <ul>
 *   <li>A release grants a <em>definition</em>: a released card puts its
 *       weapon, pattern, or item in the candidate set and nothing more.</li>
 *   <li>Grade comes from {@link GroundProductionQuality} and nothing else. A
 *       {@link PolityDoctrine} quality point shifts weight toward the top of
 *       the admitted range and can never reach past its cap — at
 *       {@link GroundProductionQuality#NONE} even two points still field
 *       nothing but Surplus.</li>
 *   <li>Experience is issued with armour, so "better troops" is spelled by the
 *       armour table leaning higher rather than by a training axis.</li>
 * </ul>
 *
 * <h2>The weight shapes</h2>
 * <p>Every table is a bell around a target position: an entry {@code d} steps
 * from the target is weighted {@code 100 * 0.45^d}, floored at 1. The target is
 * placed by a <b>lean</b> in {@code 0..5} — the risk level (0/1/2), plus the
 * quality doctrine points (0..2), plus 1 for the elite tier — scaled across the
 * admitted range. So a lean of 0 sits on the bottom entry and a lean of 5 on the
 * top one, and the range itself is whatever the production step admits.
 *
 * <p>Grades use that directly over the admitted grades in ascending tier order;
 * armour uses it over the candidate patterns' catalog tiers, so several patterns
 * of one tier each carry that tier's weight. Specials are a share rather than a
 * bell: the chance of issuing anything at all is 10/20/30 percent by risk, plus
 * ten points for the elite tier, spread evenly across the released items with
 * "no special" taking the remainder.
 *
 * <p>Those numbers are a starting shape rather than a measured balance. What is
 * pinned by test is ordering and inclusion — which grades and patterns can
 * appear at all, and which direction a point of doctrine moves the weight.
 */
public final class PolityRosterDerivation {

    /** The catalog id the derived profile is registered under. */
    public static final String PROFILE_ID = "roster.polity";

    private static final UnitType BULK_UNIT = UnitType.MILITIA;
    private static final UnitType ELITE_UNIT = UnitType.MARINE_RED;

    /**
     * The one chassis a polity lance is built from. The cheapest thing a field
     * shed turns out, and the anchor every authored roster leads its own cycle
     * with; a polity that has bought exactly one point of heavy support is
     * fielding a lance, not a mixed armoured arm.
     */
    private static final MechVariant LANCE_CHASSIS = MechVariant.BULWARK;

    private static final int PRIMARY_WEIGHT = 10;
    private static final int SPECIAL_WEIGHT = 10;
    private static final int PEAK_WEIGHT = 100;
    private static final double FALLOFF = 0.45;

    /** Risk (0..2) + quality doctrine (0..2) + the elite tier's own step. */
    private static final int MAX_LEAN = 5;

    private PolityRosterDerivation() {}

    /**
     * The derived profile for the player faction.
     *
     * @param releasedCards every template card the company has released to the
     *                      polity. The Common floor is unioned in defensively,
     *                      so a caller that forgets it still gets a polity that
     *                      can arm itself.
     */
    public static GroundRosterProfile derive(Collection<EquipmentTemplateCard> releasedCards,
                                             GroundProductionQuality quality,
                                             PolityDoctrine doctrine) {
        return derive(releasedCards, commonFloor(), quality, doctrine);
    }

    /**
     * The derivation with its floor stated rather than read from the installed
     * catalog — the seam a test uses to ask what a restricted release set
     * actually produces. Production always passes the whole Common band.
     */
    static GroundRosterProfile derive(Collection<EquipmentTemplateCard> releasedCards,
                                      Collection<EquipmentTemplateCard> commonFloor,
                                      GroundProductionQuality quality,
                                      PolityDoctrine doctrine) {
        if (quality == null) throw new IllegalArgumentException("Production quality is required");
        if (doctrine == null) throw new IllegalArgumentException("Polity doctrine is required");

        Map<String, EquipmentTemplateCard> cards = new LinkedHashMap<>();
        addAll(cards, commonFloor);
        addAll(cards, releasedCards);

        List<String> primaries = primaryWeaponIds(cards);
        if (primaries.isEmpty()) {
            throw new IllegalStateException("The polity has no primary weapon to issue; the "
                    + "Common floor is empty, which means the equipment-template catalog is "
                    + "missing its Common band rather than that the player released nothing");
        }
        List<MarineArmorCatalogDef> armor = armorCandidates(cards, quality);
        List<SpecialEquipmentDef> specials = specialCandidates(cards);
        List<EquipmentGrade> grades = admittedGrades(quality);

        return GroundRosterProfile.builder(PROFILE_ID)
                .factionId(Factions.PLAYER)
                .bulk(issue(BULK_UNIT, false, primaries, grades, armor, specials, doctrine))
                .elite(issue(ELITE_UNIT, true, primaries, grades, armor, specials, doctrine))
                .heavySupport(lance(quality, doctrine))
                .build();
    }

    /** Every Common-band card in the installed catalog: the floor a market sells. */
    static List<EquipmentTemplateCard> commonFloor() {
        List<EquipmentTemplateCard> floor = new ArrayList<>();
        for (EquipmentTemplateCard card : EquipmentTemplateCatalog.all()) {
            if (card.accessTier() == EquipmentAccessTier.COMMON) floor.add(card);
        }
        return floor;
    }

    private static List<MechVariant> lance(GroundProductionQuality quality,
                                           PolityDoctrine doctrine) {
        boolean fielded = doctrine.wantsHeavySupport() && quality.canFabricateMech();
        return fielded ? List.of(LANCE_CHASSIS) : List.of();
    }

    private static GroundRosterProfile.Issue issue(
            UnitType unitType, boolean elite, List<String> primaries,
            List<EquipmentGrade> grades, List<MarineArmorCatalogDef> armor,
            List<SpecialEquipmentDef> specials, PolityDoctrine doctrine) {
        GroundRosterProfile.Issue.Builder builder =
                GroundRosterProfile.Issue.builder(unitType);
        for (String weaponId : primaries) {
            builder.primary(WeaponRegistry.require(weaponId), PRIMARY_WEIGHT);
        }
        for (RiskLevel risk : RiskLevel.values()) {
            int lean = lean(risk, doctrine, elite);
            fillGrades(builder, risk, grades, lean);
            fillArmor(builder, risk, armor, lean);
            fillSpecials(builder, risk, specials, elite);
        }
        return builder.build();
    }

    private static void fillGrades(GroundRosterProfile.Issue.Builder builder, RiskLevel risk,
                                   List<EquipmentGrade> grades, int lean) {
        double target = lean * (grades.size() - 1) / (double) MAX_LEAN;
        for (int index = 0; index < grades.size(); index++) {
            builder.grade(risk, grades.get(index), weightAt(Math.abs(index - target)));
        }
    }

    private static void fillArmor(GroundRosterProfile.Issue.Builder builder, RiskLevel risk,
                                  List<MarineArmorCatalogDef> armor, int lean) {
        int lowest = armor.get(0).tier();
        int highest = armor.get(armor.size() - 1).tier();
        double target = lowest + lean * (highest - lowest) / (double) MAX_LEAN;
        for (MarineArmorCatalogDef pattern : armor) {
            builder.armor(risk, pattern, weightAt(Math.abs(pattern.tier() - target)));
        }
    }

    /**
     * "No special" carries the remainder of the risk's issue share, so adding a
     * released item spreads the same share wider rather than arming everybody.
     */
    private static void fillSpecials(GroundRosterProfile.Issue.Builder builder, RiskLevel risk,
                                     List<SpecialEquipmentDef> specials, boolean elite) {
        if (specials.isEmpty()) {
            builder.special(risk, null, PEAK_WEIGHT);
            return;
        }
        double share = specialShare(risk) + (elite ? 0.10 : 0.0);
        int issued = SPECIAL_WEIGHT * specials.size();
        builder.special(risk, null, Math.max(1, (int) Math.round(issued * (1 - share) / share)));
        for (SpecialEquipmentDef special : specials) {
            builder.special(risk, special, SPECIAL_WEIGHT);
        }
    }

    private static double specialShare(RiskLevel risk) {
        return switch (risk) {
            case LOW -> 0.10;
            case MEDIUM -> 0.20;
            case HIGH -> 0.30;
        };
    }

    private static int lean(RiskLevel risk, PolityDoctrine doctrine, boolean elite) {
        int riskLean = switch (risk) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
        };
        return Math.min(MAX_LEAN, riskLean + doctrine.quality() + (elite ? 1 : 0));
    }

    private static int weightAt(double distance) {
        return Math.max(1, (int) Math.round(PEAK_WEIGHT * Math.pow(FALLOFF, distance)));
    }

    /** Ascending tier, capped by what the polity's industry can build. */
    private static List<EquipmentGrade> admittedGrades(GroundProductionQuality quality) {
        List<EquipmentGrade> admitted = new ArrayList<>();
        for (EquipmentGrade grade : EquipmentGrade.values()) {
            if (quality.admits(grade)) admitted.add(grade);
        }
        return admitted;
    }

    /** Sorted weapon ids, so the same release set always builds the same table. */
    private static List<String> primaryWeaponIds(Map<String, EquipmentTemplateCard> cards) {
        TreeSet<String> weaponIds = new TreeSet<>();
        for (EquipmentTemplateCard card : cards.values()) {
            if (card.kind() == EquipmentTemplateCard.Kind.PRIMARY) {
                weaponIds.add(card.equipmentId());
            }
        }
        return List.copyOf(weaponIds);
    }

    /**
     * Released patterns the polity can actually manufacture, ascending by tier.
     * A polity whose whole release list is above its industry still issues
     * something — the least demanding patterns it holds — because a militia
     * with no armour at all is a table that cannot be built rather than a
     * poorer militia.
     */
    private static List<MarineArmorCatalogDef> armorCandidates(
            Map<String, EquipmentTemplateCard> cards, GroundProductionQuality quality) {
        List<MarineArmorCatalogDef> released = new ArrayList<>();
        for (EquipmentTemplateCard card : cards.values()) {
            if (card.kind() == EquipmentTemplateCard.Kind.ARMOR) {
                released.add(MarineArmorCatalogRegistry.require(card.equipmentId()));
            }
        }
        released.sort(Comparator.comparingInt(MarineArmorCatalogDef::tier)
                .thenComparing(MarineArmorCatalogDef::id));
        if (released.isEmpty()) {
            throw new IllegalStateException("The polity has no armour pattern to issue; the "
                    + "Common floor is missing its armour band");
        }
        List<MarineArmorCatalogDef> admitted = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : released) {
            if (quality.admitsArmorTier(pattern.tier())) admitted.add(pattern);
        }
        if (!admitted.isEmpty()) return List.copyOf(admitted);

        int lowest = released.get(0).tier();
        List<MarineArmorCatalogDef> floor = new ArrayList<>();
        for (MarineArmorCatalogDef pattern : released) {
            if (pattern.tier() == lowest) floor.add(pattern);
        }
        return List.copyOf(floor);
    }

    private static List<SpecialEquipmentDef> specialCandidates(
            Map<String, EquipmentTemplateCard> cards) {
        TreeSet<String> ids = new TreeSet<>();
        for (EquipmentTemplateCard card : cards.values()) {
            if (card.kind() == EquipmentTemplateCard.Kind.SPECIAL) ids.add(card.equipmentId());
        }
        List<SpecialEquipmentDef> specials = new ArrayList<>();
        for (String id : ids) specials.add(SpecialEquipmentRegistry.require(id));
        return List.copyOf(specials);
    }

    private static void addAll(Map<String, EquipmentTemplateCard> target,
                               Collection<EquipmentTemplateCard> cards) {
        if (cards == null) return;
        for (EquipmentTemplateCard card : cards) {
            if (card != null) target.put(card.id(), card);
        }
    }
}
