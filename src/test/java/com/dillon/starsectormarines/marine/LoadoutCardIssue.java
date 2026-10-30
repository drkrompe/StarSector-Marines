package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.battle.infantry.SoldierProfile;
import com.dillon.starsectormarines.battle.unit.UnitRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a loadout card issues, seat by seat, resolved the way a campaign squad would be: the weapon doctrine's twelve
 * billets and the sheet's bundled pattern for each. Shared by the evidence that reports a card's numbers and the
 * evidence that fights it, so the two cannot disagree about what a card is.
 *
 * <p>Definition ids ({@code weapons:...}, {@code armor:...}) are accepted as well as cards, so the starter issue,
 * which is never a card, can stand opposite one. So are two experiment forms, for trying an issue before anybody
 * authors it as a card:
 * <ul>
 *   <li>{@code kit:<primary>@<grade>,<special>*<n>,...} — every seat carries the primary at the grade, and the first
 *       seats carry the listed specials in order ({@code kit:field-rifle@service,frag-grenade*4,srm-1*2}).
 *       The {@code weapon.} and {@code special.} prefixes may be left off.</li>
 *   <li>{@code mix:<pattern>*<n>,...} — a sheet of armor patterns adding up to twelve seats
 *       ({@code mix:militia*10,packframe*2}). The {@code armor.} prefix may be left off.</li>
 * </ul>
 */
public final class LoadoutCardIssue {

    private static final String KIT = "kit:";
    private static final String MIX = "mix:";

    private LoadoutCardIssue() {}

    /** The weapon doctrine's billets for a weapon definition id. */
    public static List<SquadWeaponIssue> weapons(String weaponDefinitionId) {
        if (weaponDefinitionId.startsWith(KIT)) return kit(weaponDefinitionId.substring(KIT.length()));
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
        if (sheetDefinitionId.startsWith(MIX)) return mix(sheetDefinitionId);
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
                    .withArmorClass(pattern.armorClass())
                    .withBelt(weapon.beltEquipmentId(),
                            weapon.beltDef() != null ? weapon.beltDef().startingAmmo() : 0));
        }
        return seats.toArray(new MarineLoadout[0]);
    }

    private static List<SquadWeaponIssue> kit(String spec) {
        String[] parts = spec.split(",");
        String[] primary = parts[0].trim().split("@");
        String primaryId = prefixed("weapon.", primary[0]);
        EquipmentGrade grade = primary.length > 1
                ? EquipmentGrade.valueOf(primary[1].trim().toUpperCase(Locale.ROOT)) : EquipmentGrade.SERVICE;
        List<String> specials = expand(parts, 1, "special.");
        if (specials.size() > MarineSquad.CAPACITY) {
            throw new IllegalArgumentException("Kit lists " + specials.size() + " specials for "
                    + MarineSquad.CAPACITY + " seats: " + spec);
        }
        List<SquadWeaponIssue> issues = new ArrayList<>(MarineSquad.CAPACITY);
        for (int i = 0; i < MarineSquad.CAPACITY; i++) {
            String special = i < specials.size() ? specials.get(i) : null;
            if (special != null && SpecialEquipmentRegistry.get(special) == null) {
                throw new IllegalArgumentException("No special equipment " + special);
            }
            issues.add(new SquadWeaponIssue("Marine", primaryId, grade, special));
        }
        return issues;
    }

    private static SquadArmorDoctrine mix(String id) {
        List<String> patterns = expand(id.substring(MIX.length()).split(","), 0, "armor.");
        if (patterns.size() != MarineSquad.CAPACITY) {
            throw new IllegalArgumentException("Mix issues " + patterns.size() + " patterns for "
                    + MarineSquad.CAPACITY + " seats: " + id);
        }
        return SquadArmorDoctrine.fromIds(id, id, "", patterns);
    }

    /** {@code id*n} entries from {@code from} on, each id given {@code prefix} unless it already has it. */
    private static List<String> expand(String[] entries, int from, String prefix) {
        List<String> ids = new ArrayList<>();
        for (int i = from; i < entries.length; i++) {
            String entry = entries[i].trim();
            if (entry.isEmpty()) continue;
            int star = entry.indexOf('*');
            String id = prefixed(prefix, star < 0 ? entry : entry.substring(0, star));
            int count = star < 0 ? 1 : Integer.parseInt(entry.substring(star + 1).trim());
            for (int n = 0; n < count; n++) ids.add(id);
        }
        return ids;
    }

    private static String prefixed(String prefix, String id) {
        String trimmed = id.trim();
        return trimmed.startsWith(prefix) ? trimmed : prefix + trimmed;
    }
}
