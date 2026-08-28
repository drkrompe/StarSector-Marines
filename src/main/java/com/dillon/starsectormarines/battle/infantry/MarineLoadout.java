package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

/** Per-seat marine issue carried from campaign or faction catalogs into battle. */
public final class MarineLoadout {

    public static final String DEFAULT_PRIMARY_ID = "weapon.pulse-rifle";
    public static final MarineLoadout COMBATANT = new MarineLoadout(
            UnitRole.COMBATANT, null, DEFAULT_PRIMARY_ID, null, 0);

    public final UnitRole role;
    public final Objective objective;
    public final String primaryWeaponId;
    public final EquipmentGrade equipmentGrade;
    public final SoldierProfile soldierProfile;
    public final String specialEquipmentId;
    public final int secondaryAmmo;
    public final String campaignSoldierId;
    public final CampaignSquadTag campaignSquad;
    public final LayeredArmorFamily armorFamily;
    public final float armorCapacity;
    public final float armorRating;
    public final float armorMoveSpeedMult;
    public final float armorIncomingAccuracyMult;

    public MarineLoadout(UnitRole role, Objective objective) {
        this(role, objective, DEFAULT_PRIMARY_ID, null, 0);
    }

    public MarineLoadout(UnitRole role, Objective objective, String primaryWeaponId,
                         String specialEquipmentId, int secondaryAmmo) {
        this(role, objective, primaryWeaponId, EquipmentGrade.SERVICE,
                SoldierProfile.REGULAR, specialEquipmentId, secondaryAmmo,
                null, null);
    }

    public MarineLoadout(UnitRole role, Objective objective, WeaponDef primary,
                         SpecialEquipmentDef special, int secondaryAmmo) {
        this(role, objective, primary != null ? primary.id : null,
                special != null ? special.id() : null, secondaryAmmo);
    }

    public MarineLoadout(UnitRole role, Objective objective, String primaryWeaponId,
                         EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                         String specialEquipmentId, int secondaryAmmo) {
        this(role, objective, primaryWeaponId, equipmentGrade, soldierProfile,
                specialEquipmentId, secondaryAmmo, null, null);
    }

    public MarineLoadout(UnitRole role, Objective objective, WeaponDef primary,
                         EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                         SpecialEquipmentDef special, int secondaryAmmo) {
        this(role, objective, primary != null ? primary.id : null, equipmentGrade,
                soldierProfile, special != null ? special.id() : null, secondaryAmmo);
    }

    public MarineLoadout(UnitRole role, Objective objective, String primaryWeaponId,
                         EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                         String specialEquipmentId, int secondaryAmmo,
                         String campaignSoldierId, LayeredArmorFamily armorFamily) {
        this(role, objective, primaryWeaponId, equipmentGrade, soldierProfile,
                specialEquipmentId, secondaryAmmo, campaignSoldierId, armorFamily,
                0f, 0f, 1f, 1f, null);
    }

    public MarineLoadout(UnitRole role, Objective objective, String primaryWeaponId,
                         EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                         String specialEquipmentId, int secondaryAmmo,
                         String campaignSoldierId, LayeredArmorFamily armorFamily,
                         float armorCapacity, float armorRating,
                         float armorMoveSpeedMult, float armorIncomingAccuracyMult,
                         CampaignSquadTag campaignSquad) {
        this.campaignSquad = campaignSquad;
        this.role = role;
        this.objective = objective;
        this.primaryWeaponId = primaryWeaponId;
        this.equipmentGrade = equipmentGrade != null ? equipmentGrade : EquipmentGrade.SERVICE;
        this.soldierProfile = soldierProfile != null ? soldierProfile : SoldierProfile.REGULAR;
        this.specialEquipmentId = specialEquipmentId;
        this.secondaryAmmo = secondaryAmmo;
        this.campaignSoldierId = campaignSoldierId;
        this.armorFamily = armorFamily;
        this.armorCapacity = armorCapacity;
        this.armorRating = armorRating;
        this.armorMoveSpeedMult = armorMoveSpeedMult;
        this.armorIncomingAccuracyMult = armorIncomingAccuracyMult;
    }

    /** Builds generated faction issue directly from contributed catalog definitions. */
    public static MarineLoadout fromCatalog(
            UnitRole role, Objective objective, WeaponDef primary,
            EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
            SpecialEquipmentDef special, String campaignSoldierId,
            LayeredArmorFamily armorFamily, float armorCapacity, float armorRating,
            float armorMoveSpeedMult, float armorIncomingAccuracyMult) {
        return fromCatalog(role, objective, primary, equipmentGrade, soldierProfile,
                special, campaignSoldierId, armorFamily, armorCapacity, armorRating,
                armorMoveSpeedMult, armorIncomingAccuracyMult, null);
    }

    public static MarineLoadout fromCatalog(
            UnitRole role, Objective objective, WeaponDef primary,
            EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
            SpecialEquipmentDef special, String campaignSoldierId,
            LayeredArmorFamily armorFamily, float armorCapacity, float armorRating,
            float armorMoveSpeedMult, float armorIncomingAccuracyMult,
            CampaignSquadTag campaignSquad) {
        return new MarineLoadout(role, objective, primary != null ? primary.id : null,
                equipmentGrade, soldierProfile, special != null ? special.id() : null,
                special != null ? special.startingAmmo() : 0, campaignSoldierId,
                armorFamily, armorCapacity, armorRating, armorMoveSpeedMult,
                armorIncomingAccuracyMult, campaignSquad);
    }

    public WeaponDef primaryDef() {
        return primaryWeaponId != null ? WeaponRegistry.require(primaryWeaponId) : null;
    }

    public SpecialEquipmentDef specialDef() {
        return SpecialEquipmentRegistry.get(specialEquipmentId);
    }

    /** Applies this issued loadout to one deboarding marine construction spec. */
    public void seedInto(EntitySpec marine) {
        marine.role(role);
        marine.assignedObjective(objective);
        WeaponDef primary = primaryDef();
        if (primary != null) {
            marine.primaryWeapon(primary, equipmentGrade, soldierProfile);
        } else {
            marine.soldierProfile(soldierProfile);
        }
        SpecialEquipmentDef special = specialDef();
        if (special != null && special.hasAvailableUse(secondaryAmmo)) {
            marine.specialEquipment(special, secondaryAmmo);
        }
        marine.campaignSoldierId(campaignSoldierId);
        marine.campaignSquadId(campaignSquad != null ? campaignSquad.squadId : null);
        if (armorFamily != null) {
            marine.layeredArmorFamily(armorFamily);
            marine.armor(armorCapacity, armorRating,
                    armorMoveSpeedMult, armorIncomingAccuracyMult);
        }
    }
}
