package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.List;

/**
 * What a loadout card issues, seat by seat, resolved the way a campaign squad would be: the weapon doctrine's twelve
 * billets and the sheet's bundled pattern for each. Shared by the evidence that reports a card's numbers and the
 * evidence that fights it, so the two cannot disagree about what a card is.
 *
 * <p>Definition ids ({@code weapons:...}, {@code armor:...}) are accepted as well as cards, so the starter issue,
 * which is never a card, can stand opposite one.
 */
public final class LoadoutCardIssue {

    private LoadoutCardIssue() {}

    /** The weapon doctrine's billets for a weapon definition id. */
    public static List<SquadWeaponIssue> weapons(String weaponDefinitionId) {
        SquadWeaponDoctrine builtIn = SquadEquipmentDoctrines.weaponById(weaponDefinitionId);
        if (builtIn != null) return builtIn.issues();
        for (FirstRefit.Path path : FirstRefit.Path.values()) {
            if (path.weapons.equals(weaponDefinitionId)) return FirstRefit.weapons(path);
        }
        throw new IllegalArgumentException("No weapon definition " + weaponDefinitionId);
    }

    public static SquadArmorPlan plan(String sheetDefinitionId) {
        SquadArmorPlan plan = SquadEquipmentDoctrines.armorPlanById(sheetDefinitionId);
        if (plan == null) throw new IllegalArgumentException("No tactic sheet " + sheetDefinitionId);
        return plan;
    }

    /** The patterns the sheet actually issues, from exactly its own bundle. */
    public static SquadArmorDoctrine armor(String sheetDefinitionId) {
        return ArmorIssueResolver.issueSheet(plan(sheetDefinitionId));
    }

    /**
     * One battle loadout per billet: the weapon side's primary, grade and special, and the sheet's pattern for the
     * same billet. Every marine is a regular, so the comparison is of equipment and not of experience.
     */
    public static MarineLoadout[] seats(String weaponDefinitionId, String sheetDefinitionId) {
        List<SquadWeaponIssue> weapons = weapons(weaponDefinitionId);
        SquadArmorDoctrine armor = armor(sheetDefinitionId);
        if (weapons.size() != armor.issueIds().size()) {
            throw new IllegalStateException("Weapon card has " + weapons.size() + " billets but the sheet has "
                    + armor.issueIds().size());
        }
        List<MarineLoadout> seats = new ArrayList<>(weapons.size());
        for (int i = 0; i < weapons.size(); i++) {
            SquadWeaponIssue weapon = weapons.get(i);
            MarineArmorCatalogDef pattern = armor.issueDef(i);
            SpecialEquipmentDef special = weapon.specialDef();
            seats.add(MarineLoadout.fromCatalog(UnitRole.COMBATANT, null, weapon.primaryDef(), weapon.grade(),
                    SoldierProfile.REGULAR, special, null, pattern.appearanceFamily(),
                    pattern.armorCapacity(), pattern.armorRating(), pattern.moveSpeedMult(),
                    pattern.incomingAccuracyMult(), null, pattern.integralSystem())
                    .withArmorClass(pattern.armorClass()));
        }
        return seats.toArray(new MarineLoadout[0]);
    }
}
