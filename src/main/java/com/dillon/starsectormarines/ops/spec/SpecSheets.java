package com.dillon.starsectormarines.ops.spec;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.InfantryCombatStats;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.mech.MechCatalog;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.EquipmentTemplateCard;
import com.dillon.starsectormarines.marine.MarineArmorCatalogDef;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.dillon.starsectormarines.ui.spec.SpecSheet;
import com.dillon.starsectormarines.ui.spec.SpecSheet.Stat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes one {@link SpecSheet} per catalog item ({@code spec-sheet.md}).
 *
 * <p>Every screen that lets a player hover an item's name asks here, so the same
 * thing is described the same way wherever it is read. Before this the copy
 * existed on four screens in three shapes, and the polity doctrine panel — which
 * names eleven armour patterns and a mech — had none of it.
 *
 * <p><b>This reports what the simulation applies, not what a catalog declares.</b>
 * Damage and reach come through {@code InfantryCombatStats} at the grade the item
 * is actually issued at; a suit's capability comes through
 * {@link IntegralSystemCopy}, which has the same rule and states it at length.
 * Nothing here invents a number.
 *
 * <p>Meters are measured against {@link CatalogCeilings} rather than against
 * whatever else is on screen, so cycling one item produces a meaningful change.
 */
public final class SpecSheets {

    /** Accent tokens the stylesheet keys a border colour on. */
    public static final String ACCENT_WEAPON = "weapon";
    public static final String ACCENT_ARMOR = "armor";
    public static final String ACCENT_SPECIAL = "special";
    public static final String ACCENT_SYSTEM = "system";
    public static final String ACCENT_MECH = "mech";

    private static final String SEPARATOR = "  ·  ";

    /**
     * Whose hands the accuracy and sustained-output rows are quoted for.
     *
     * <p>Both depend on the shooter as well as the weapon, and a catalog entry
     * has no shooter — so a sheet quotes the neutral profile the sim itself uses
     * where none is supplied. A card that knows its marine passes that marine's
     * profile instead.
     */
    private static final SoldierProfile CATALOG_SHOOTER = SoldierProfile.REGULAR;

    private SpecSheets() { }

    /**
     * One marine primary at one grade.
     *
     * <p>The title is the catalog name at that grade — {@code PLS-2 Lancer} —
     * which is what the Armory's own card shows; an untiered designation such as
     * the Rook's {@code FR-1} simply carries no suffix, so the two cases need no
     * branch here.
     */
    public static SpecSheet weapon(WeaponDef def, EquipmentGrade grade) {
        if (def == null) throw new IllegalArgumentException("A weapon sheet needs a weapon");
        EquipmentGrade issued = grade != null ? grade : EquipmentGrade.SERVICE;
        return new SpecSheet(
                def.catalogName(issued),
                joined(def.designation(issued), def.catalogRole, issued.displayName),
                def.catalogFactionLogo,
                ACCENT_WEAPON,
                weaponStats(def, issued, CATALOG_SHOOTER),
                paragraphs(def.catalogDescription));
    }

    /**
     * The four rows the Armory's weapon block shows, as stat rows.
     *
     * <p>Takes the shooter because accuracy and sustained output are partly the
     * marine's; a sheet written for a catalog entry rather than for a billet
     * passes the neutral profile.
     */
    public static List<Stat> weaponStats(WeaponDef def, EquipmentGrade grade,
                                         SoldierProfile profile) {
        SoldierProfile shooter = profile != null ? profile : CATALOG_SHOOTER;
        float damage = InfantryCombatStats.damage(def, grade);
        float range = InfantryCombatStats.range(def, grade);
        float accuracy = InfantryCombatStats.accuracy(def, grade, shooter);
        float dps = InfantryCombatStats.estimatedDps(def, grade, shooter);
        return List.of(
                new Stat("DMG", oneDecimal(damage),
                        StatMeter.fraction(damage, CatalogCeilings.weaponDamage())),
                new Stat("RNG", oneDecimal(range),
                        StatMeter.fraction(range, CatalogCeilings.weaponRange())),
                new Stat("ACC", percent(accuracy), StatMeter.fraction(accuracy, 1f)),
                new Stat("DPS", oneDecimal(dps),
                        StatMeter.fraction(dps, CatalogCeilings.weaponDps(shooter))));
    }

    /**
     * One armour pattern, with its integral system named and summarised beneath
     * the authored description when it carries one.
     *
     * <p>The system gets its own paragraph rather than a stat row because it is
     * a capability with a clock, not a magnitude — and because a pattern that
     * carries nothing then simply has one paragraph fewer instead of a row
     * reading zero.
     */
    public static SpecSheet armor(MarineArmorCatalogDef def) {
        if (def == null) throw new IllegalArgumentException("An armour sheet needs a pattern");
        List<String> notes = new ArrayList<>(paragraphs(def.description()));
        if (IntegralSystemCopy.carried(def)) {
            notes.add(IntegralSystemCopy.flavorName(def) + " — " + IntegralSystemCopy.summary(def));
        }
        return new SpecSheet(
                def.displayName(),
                joined("TIER " + def.tier(), def.role().displayName(), def.tradition().displayName()),
                def.tradition().factionLogo(),
                ACCENT_ARMOR,
                armorStats(def),
                notes);
    }

    /** The protection block: what the suit holds, what it turns, and what it costs to wear. */
    public static List<Stat> armorStats(MarineArmorCatalogDef def) {
        float move = UnitType.MARINE.moveSpeed * def.moveSpeedMult();
        float evasion = CatalogCeilings.evasionOf(def);
        return List.of(
                new Stat("ARMOR", whole(def.armorCapacity()),
                        StatMeter.fraction(def.armorCapacity(), CatalogCeilings.armorCapacity())),
                new Stat("RESIST", whole(def.armorRating()),
                        StatMeter.fraction(def.armorRating(), CatalogCeilings.armorRating())),
                new Stat("MOVE", oneDecimal(move),
                        StatMeter.fraction(def.moveSpeedMult(), CatalogCeilings.moveSpeedMult())),
                // Signed: three shipped patterns are easier to hit than a bare
                // marine, and a sheet that hid that would be recommending them.
                new Stat("EVASION", signedPercent(evasion),
                        StatMeter.fraction(evasion, CatalogCeilings.armorEvasion())));
    }

    /**
     * One special item, with the gun behind it when it has one.
     *
     * <p><b>A special fires at its authored weapon values with no equipment
     * grade applied at all</b> — {@code InfantryWeapons} reads
     * {@code SpecialEquipmentDef.damage()}, which is the raw
     * {@code WeaponDef} field, and nothing on that path consults a grade. Those
     * are numerically the Service-grade values, since every Service multiplier
     * is 1.0, but they are quoted as the raw ones because that is what the
     * simulation does; a grade row here would imply an issue decision the player
     * does not have.
     */
    public static SpecSheet special(SpecialEquipmentDef def) {
        if (def == null) throw new IllegalArgumentException("A special sheet needs an item");
        List<Stat> stats = new ArrayList<>();
        if (def.weaponId() != null && !def.weaponId().isBlank()) {
            stats.add(new Stat("DMG", oneDecimal(def.damage()),
                    StatMeter.fraction(def.damage(), CatalogCeilings.specialDamage())));
            stats.add(new Stat("RNG", oneDecimal(def.range()),
                    StatMeter.fraction(def.range(), CatalogCeilings.specialRange())));
        }
        if (def.usesAmmunition()) {
            stats.add(Stat.of("AMMO", uses(def.startingAmmo())));
        }
        return new SpecSheet(
                def.displayName(),
                def.catalogSubtitle(),
                def.catalogFactionLogo(),
                ACCENT_SPECIAL,
                stats,
                paragraphs(def.catalogDescription()));
    }

    /**
     * One integral system, described by the class that already owns that copy.
     *
     * <p>{@link IntegralSystemCopy} reads the pattern rather than the system,
     * because a system's effect numbers hang off the suit's own definition, so
     * this takes the pattern and quotes the system it carries.
     */
    public static SpecSheet integralSystem(MarineArmorCatalogDef carrier) {
        if (carrier == null || !IntegralSystemCopy.carried(carrier)) {
            throw new IllegalArgumentException(
                    "An integral-system sheet needs a pattern that carries one");
        }
        return new SpecSheet(
                IntegralSystemCopy.flavorName(carrier),
                IntegralSystemCopy.summary(carrier),
                IntegralSystemCopy.iconPath(carrier),
                ACCENT_SYSTEM,
                List.of(),
                IntegralSystemCopy.detailParagraphs(carrier));
    }

    /**
     * One mech chassis and the hardware it is carrying.
     *
     * <p>Its meters are measured against the mech catalog's own ceilings rather
     * than against anything infantry, because the two populations share no axis
     * a bar could honestly compare — a chassis's structure is an order of
     * magnitude past a marine's, and a meter that pegged for all three variants
     * would say nothing about choosing between them.
     */
    public static SpecSheet mech(MechVariant variant) {
        if (variant == null) throw new IllegalArgumentException("A mech sheet needs a chassis");
        MechCatalog.ChassisEntry entry = MechCatalog.require(variant);
        List<String> notes = new ArrayList<>(paragraphs(entry.description()));
        for (MechWeaponComponent fitted : fittedComponents(variant)) {
            notes.add(fitted.displayName + " — "
                    + MechCatalog.require(fitted).description());
        }
        return new SpecSheet(
                variant.displayName,
                joined(entry.designation(), entry.role()),
                null,
                ACCENT_MECH,
                mechStats(variant),
                notes);
    }

    /** The five axes a chassis is chosen on. */
    public static List<Stat> mechStats(MechVariant variant) {
        return List.of(
                new Stat("STRUCTURE", whole(variant.maxStructure),
                        StatMeter.fraction(variant.maxStructure, CatalogCeilings.mechStructure())),
                new Stat("ARMOR", whole(variant.armorCapacity),
                        StatMeter.fraction(variant.armorCapacity,
                                CatalogCeilings.mechArmorCapacity())),
                new Stat("SPEED", oneDecimal(variant.moveSpeed),
                        StatMeter.fraction(variant.moveSpeed, CatalogCeilings.mechMoveSpeed())),
                new Stat("ACCURACY", percent(variant.accuracy),
                        StatMeter.fraction(variant.accuracy, CatalogCeilings.mechAccuracy())),
                new Stat("VISION", whole(variant.visionRange),
                        StatMeter.fraction(variant.visionRange,
                                CatalogCeilings.mechVisionRange())));
    }

    /** One piece of mech hardware, on its own rather than as part of a chassis. */
    public static SpecSheet mechWeapon(MechWeaponComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("A mech-weapon sheet needs a component");
        }
        MechCatalog.ComponentEntry entry = MechCatalog.require(component);
        return new SpecSheet(
                component.displayName,
                joined(entry.designation(), mountLabel(component),
                        titleCase(component.hardpointType.name())),
                null,
                ACCENT_MECH,
                List.of(),
                paragraphs(entry.description()));
    }

    /**
     * One collectible template card, resolved to the equipment it names.
     *
     * <p>The title stays the equipment's — a card is a way of owning a Lancer,
     * not a second thing called something else — and the card's own facts, the
     * access tier and (for a primary) the grade it issues at, lead the subtitle
     * in front of the equipment's.
     */
    public static SpecSheet card(EquipmentTemplateCard card) {
        if (card == null) throw new IllegalArgumentException("A card sheet needs a card");
        SpecSheet resolved = switch (card.kind()) {
            case PRIMARY -> weapon(WeaponRegistry.require(card.equipmentId()), card.grade());
            case ARMOR -> armor(MarineArmorCatalogRegistry.require(card.equipmentId()));
            case SPECIAL -> special(requireSpecial(card.equipmentId()));
        };
        String access = titleCase(card.accessTier().name());
        String lead = card.grade() != null
                ? access + SEPARATOR + card.grade().displayName + " issue"
                : access;
        return new SpecSheet(resolved.title(), joined(lead, resolved.subtitle()),
                resolved.crestPath(), resolved.accent(), resolved.stats(), resolved.notes());
    }

    private static SpecialEquipmentDef requireSpecial(String id) {
        SpecialEquipmentDef def = SpecialEquipmentRegistry.get(id);
        if (def == null) {
            throw new IllegalArgumentException("Unknown special-equipment id '" + id + "'");
        }
        return def;
    }

    private static List<MechWeaponComponent> fittedComponents(MechVariant variant) {
        List<MechWeaponComponent> fitted = new ArrayList<>();
        if (variant.arms != null) fitted.add(variant.arms);
        if (variant.leftShoulder != null) fitted.add(variant.leftShoulder);
        // A symmetric pair is one line: the sheet is describing the hardware,
        // and saying the same sentence twice reads as a rendering fault.
        if (variant.rightShoulder != null && variant.rightShoulder != variant.leftShoulder) {
            fitted.add(variant.rightShoulder);
        }
        return fitted;
    }

    /** An enum constant as a word: {@code MISSILE} reads Missile, not MISSILE. */
    private static String titleCase(String constant) {
        return constant.charAt(0) + constant.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String mountLabel(MechWeaponComponent component) {
        return component.mountFamily == MechWeaponComponent.MountFamily.ARMS
                ? "Arm mount" : "Shoulder mount";
    }

    /**
     * Blank-line-separated paragraphs, in order, with empties dropped.
     *
     * <p>Authored descriptions are one paragraph today; splitting rather than
     * assuming that means the first author who writes two gets two.
     */
    private static List<String> paragraphs(String description) {
        if (description == null || description.isBlank()) return List.of();
        List<String> found = new ArrayList<>();
        for (String paragraph : description.trim().split("\\R\\s*\\R")) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) found.add(trimmed);
        }
        return found;
    }

    /** Joins the parts that are actually present, so a missing role leaves no dangling dot. */
    private static String joined(String... parts) {
        StringBuilder line = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            if (line.length() > 0) line.append(SEPARATOR);
            line.append(part.trim());
        }
        return line.toString();
    }

    private static String oneDecimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String whole(float value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    /** A share the wearer may also be on the wrong side of, so it carries its sign. */
    private static String signedPercent(float fraction) {
        int rounded = Math.round(fraction * 100f);
        return (rounded > 0 ? "+" : "") + rounded + "%";
    }

    private static String percent(float fraction) {
        return String.format(Locale.ROOT, "%.0f%%", fraction * 100f);
    }

    private static String uses(int ammo) {
        return ammo + (ammo == 1 ? " use" : " uses");
    }
}
