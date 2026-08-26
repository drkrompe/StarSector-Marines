package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.infantry.EquipmentGrade;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;

import java.util.List;
import java.util.Set;

/** Built-in fire-team template library. Player-authored templates join these entries. */
public final class FireTeamTemplateCards {

    public static final String FIELD_ID = "field";
    public static final String LINE_ID = "line";
    public static final String RECON_ID = "recon";
    public static final String FIRE_SUPPORT_ID = "fire_support";
    public static final String ANTI_MATERIEL_ID = "anti_materiel";
    public static final String SCREEN_ID = "screen";
    public static final String BREACH_ID = "breach";

    private static final Set<String> STARTER_IDS = Set.of(
            FIELD_ID, LINE_ID, RECON_ID, FIRE_SUPPORT_ID, ANTI_MATERIEL_ID, SCREEN_ID,
            BREACH_ID);

    private FireTeamTemplateCards() {}

    public static List<FireTeamTemplateCard> starterCards() {
        return List.of(
                card(FIELD_ID, "Field",
                        billet("Team Leader", WeaponRegistry.STARTER_PRIMARY_ID, null,
                                MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID, null,
                                MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID, null,
                                MarineArmorPattern.ARMORLESS),
                        billet("Rifleman", WeaponRegistry.STARTER_PRIMARY_ID, null,
                                MarineArmorPattern.ARMORLESS)),
                card(LINE_ID, "Line",
                        billet("Team Leader", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Rifleman", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Rifleman", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Rifleman", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL)),
                card(RECON_ID, "Recon",
                        billet("Team Leader", WeaponRegistry.SMG_ID, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Scout", WeaponRegistry.SMG_ID, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Marksman", WeaponRegistry.DMR_ID, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Marksman", WeaponRegistry.DMR_ID, null,
                                MarineArmorPattern.ARMY_GREEN)),
                card(FIRE_SUPPORT_ID, "Fire Support",
                        billet("Team Leader", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Automatic Rifleman", WeaponRegistry.SQUAD_AUTOMATIC_ID, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Marksman", WeaponRegistry.DMR_ID, null,
                                MarineArmorPattern.ARMY_GREEN),
                        billet("Anti-Armor", WeaponRegistry.PULSE_RIFLE_ID,
                                SpecialEquipmentRegistry.ROCKET_LAUNCHER_ID,
                                MarineArmorPattern.ARMY_GREEN)),
                card(ANTI_MATERIEL_ID, "Anti-Materiel",
                        billet("Team Leader", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Spotter", WeaponRegistry.DMR_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Security", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Heavy Marksman", WeaponRegistry.PULSE_RIFLE_ID,
                                SpecialEquipmentRegistry.ANTI_MATERIEL_RIFLE_ID,
                                MarineArmorPattern.CHARCOAL)),
                card(SCREEN_ID, "Screen",
                        billet("Team Leader", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Grenadier", WeaponRegistry.SMG_ID,
                                SpecialEquipmentRegistry.SMOKE_GRENADE_ID,
                                MarineArmorPattern.CHARCOAL),
                        billet("Rifleman", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Marksman", WeaponRegistry.DMR_ID, null,
                                MarineArmorPattern.CHARCOAL)),
                card(BREACH_ID, "Breach",
                        billet("Team Leader", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Demolitions", WeaponRegistry.SMG_ID,
                                SpecialEquipmentRegistry.SATCHEL_CHARGE_ID,
                                MarineArmorPattern.CHARCOAL),
                        billet("Rifleman", WeaponRegistry.PULSE_RIFLE_ID, null,
                                MarineArmorPattern.CHARCOAL),
                        billet("Marksman", WeaponRegistry.DMR_ID, null,
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

    private static FireTeamBillet billet(String name, String primaryId,
                                         String specialEquipmentId,
                                         MarineArmorPattern armor) {
        return new FireTeamBillet(name, primaryId, EquipmentGrade.SERVICE,
                specialEquipmentId, armor.id);
    }
}
