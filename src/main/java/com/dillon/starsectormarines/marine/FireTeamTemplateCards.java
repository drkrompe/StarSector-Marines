package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.infantry.MarineSecondary;
import com.dillon.starsectormarines.battle.infantry.MarineWeapon;

import java.util.List;
import java.util.Set;

/** Built-in fire-team template library. Player-authored templates join these entries. */
public final class FireTeamTemplateCards {

    public static final String FIELD_ID = "field";
    public static final String LINE_ID = "line";
    public static final String RECON_ID = "recon";
    public static final String FIRE_SUPPORT_ID = "fire_support";
    public static final String ANTI_MATERIEL_ID = "anti_materiel";

    private static final Set<String> STARTER_IDS = Set.of(
            FIELD_ID, LINE_ID, RECON_ID, FIRE_SUPPORT_ID, ANTI_MATERIEL_ID);

    private FireTeamTemplateCards() {}

    public static List<FireTeamTemplateCard> starterCards() {
        return List.of(
                card(FIELD_ID, "Field",
                        billet("Team Leader", MarineWeapon.FIELD_RIFLE, null,
                                MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", MarineWeapon.FIELD_RIFLE, null,
                                MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", MarineWeapon.FIELD_RIFLE, null,
                                MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", MarineWeapon.FIELD_RIFLE, null,
                                MarineArmorPattern.ARMORLESS)),
                card(LINE_ID, "Line",
                        billet("Team Leader", MarineWeapon.PULSE_RIFLE, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Rifleman", MarineWeapon.PULSE_RIFLE, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Rifleman", MarineWeapon.PULSE_RIFLE, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Rifleman", MarineWeapon.PULSE_RIFLE, null,
                                MarineArmorPattern.CHARCOAL)),
                card(RECON_ID, "Recon",
                        billet("Team Leader", MarineWeapon.SMG, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Scout", MarineWeapon.SMG, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Marksman", MarineWeapon.DMR, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Marksman", MarineWeapon.DMR, null,
                                MarineArmorPattern.ARMY_GREEN)),
                card(FIRE_SUPPORT_ID, "Fire Support",
                        billet("Team Leader", MarineWeapon.PULSE_RIFLE, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Automatic Rifleman", MarineWeapon.SMG, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Marksman", MarineWeapon.DMR, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Anti-Armor", MarineWeapon.PULSE_RIFLE,
                                MarineSecondary.ROCKET_LAUNCHER,
                                MarineArmorPattern.ARMY_GREEN)),
                card(ANTI_MATERIEL_ID, "Anti-Materiel",
                        billet("Team Leader", MarineWeapon.PULSE_RIFLE, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Spotter", MarineWeapon.DMR, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Security", MarineWeapon.PULSE_RIFLE, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Heavy Marksman", MarineWeapon.PULSE_RIFLE,
                                MarineSecondary.ANTI_MATERIEL_RIFLE,
                                MarineArmorPattern.CHARCOAL)));
    }

    /** Built-in templates are permanent library fixtures; players clone them before editing. */
    public static boolean isStarterId(String id) {
        return id != null && STARTER_IDS.contains(id);
    }

    private static FireTeamTemplateCard card(String id, String name,
                                             FireTeamBillet... billets) {
        return new FireTeamTemplateCard(id, name, List.of(billets));
    }

    private static FireTeamBillet billet(String name, MarineWeapon primary,
                                         MarineSecondary secondary,
                                         MarineArmorPattern armor) {
        return new FireTeamBillet(name, primary, EquipmentGrade.SERVICE, secondary, armor);
    }
}
