package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.i18n.Strings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One acquirable squad loadout: a Weapon Loadout or a Tactic Sheet, learned wholesale
 * together with the equipment that makes it issuable.
 *
 * <p>The card is what markets sell, patrons pay in and salvage recovers. The
 * equipment template an Armory owns is the permanent capability beneath it; no
 * acquisition channel offers one on its own any more.
 */
public record LoadoutCard(Kind kind, String definitionId) {

    public enum Kind { WEAPON, SHEET }

    static final String PREFIX = "loadout-card:";

    public LoadoutCard {
        if (kind == null || definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("A loadout card needs a kind and a definition id");
        }
    }

    /** Stable cargo payload and picker identity. */
    public String id() {
        return PREFIX + (kind == Kind.WEAPON ? "weapon:" : "sheet:") + definitionId;
    }

    /** Null when the text is not a card id naming a loadout this build defines. */
    public static LoadoutCard parse(String id) {
        if (id == null || !id.startsWith(PREFIX)) return null;
        String rest = id.substring(PREFIX.length());
        LoadoutCard card;
        if (rest.startsWith("weapon:")) {
            card = new LoadoutCard(Kind.WEAPON, rest.substring("weapon:".length()));
        } else if (rest.startsWith("sheet:")) {
            card = new LoadoutCard(Kind.SHEET, rest.substring("sheet:".length()));
        } else {
            return null;
        }
        return card.defined() ? card : null;
    }

    public boolean defined() {
        return switch (kind) {
            case WEAPON -> weaponIssues() != null;
            case SHEET -> SquadEquipmentDoctrines.armorPlanById(definitionId) != null;
        };
    }

    public String displayName() {
        return switch (kind) {
            case WEAPON -> weaponName();
            case SHEET -> SquadEquipmentDoctrines.armorPlanById(definitionId).displayName();
        };
    }

    /** Every equipment template the loadout carries with it, in billet order, without repeats. */
    public List<String> templateIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (kind == Kind.WEAPON) {
            for (SquadWeaponIssue issue : weaponIssues()) {
                add(ids, EquipmentTemplateCatalog.primaryId(issue.primaryId(), issue.grade()));
                if (issue.specialEquipmentId() != null) {
                    add(ids, EquipmentTemplateCatalog.specialId(issue.specialEquipmentId()));
                }
                if (issue.beltEquipmentId() != null) {
                    add(ids, EquipmentTemplateCatalog.specialId(issue.beltEquipmentId()));
                }
            }
        } else {
            for (String pattern : ArmorSheetBundles.patternsFor(definitionId)) {
                add(ids, EquipmentTemplateCatalog.armorId(pattern));
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * The twelve billets a weapon loadout issues: a built-in definition, or one of the
     * Wayline technology offers whose definition is only written into a company's Armory
     * once it chooses. Null for an id naming neither.
     */
    private List<SquadWeaponIssue> weaponIssues() {
        SquadWeaponDoctrine builtIn = SquadEquipmentDoctrines.weaponById(definitionId);
        if (builtIn != null) return builtIn.issues();
        FirstRefit.Path path = waylinePath();
        return path != null ? FirstRefit.weapons(path) : null;
    }

    private String weaponName() {
        SquadWeaponDoctrine builtIn = SquadEquipmentDoctrines.weaponById(definitionId);
        if (builtIn != null) return builtIn.displayName();
        return Strings.get("loadoutCardWaylinePrefix") + waylinePath().label();
    }

    private FirstRefit.Path waylinePath() {
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            if (path.weapons.equals(definitionId)) return path;
        }
        return null;
    }

    /** The tactic sheet this card teaches, or null for a weapon loadout. */
    public String sheetId() {
        return kind == Kind.SHEET ? definitionId : null;
    }

    /** The scarcest tier among the bundled equipment; a bundle-less card is common. */
    public EquipmentAccessTier accessTier() {
        EquipmentAccessTier tier = EquipmentAccessTier.COMMON;
        for (String id : templateIds()) {
            EquipmentAccessTier candidate = EquipmentTemplateCatalog.require(id).accessTier();
            if (candidate.ordinal() > tier.ordinal()) tier = candidate;
        }
        return tier;
    }

    /** Story-reserved equipment never circulates through ordinary channels, bundled or not. */
    public boolean storyReserved() {
        for (String id : templateIds()) {
            if (EquipmentTemplateCatalog.installed().storySource(id) != null) return true;
        }
        return false;
    }

    /**
     * Whether every armor pattern the card carries has its presentation declared. A card
     * whose pattern still awaits art is defined, inspectable and learnable by explicit test
     * controls, but it is not awardable: no cargo payload can be made for it.
     */
    public boolean presentationDeclared() {
        if (kind != Kind.SHEET) return true;
        MarineArmorCatalogRegistry catalog = MarineArmorCatalogRegistry.installed();
        for (String pattern : ArmorSheetBundles.patternsFor(definitionId)) {
            MarineArmorCatalogDef def = catalog != null ? catalog.get(pattern) : null;
            if (def != null && !def.presentationDeclared()) return false;
        }
        return true;
    }

    private static void add(Set<String> ids, String templateId) {
        if (EquipmentTemplateCatalog.contains(templateId)) ids.add(templateId);
    }
}
