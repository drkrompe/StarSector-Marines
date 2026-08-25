package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;
import com.dillon.starsectormarines.battle.squad.CampaignSquadTag;
import com.dillon.starsectormarines.battle.weapon.WeaponDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentDef;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;

/**
 * Per-slot loadout for a shuttle's marine roster. One {@code MarineLoadout}
 * describes one marine that will deboard: their {@link UnitRole}, their
 * {@link MarineWeapon primary} weapon, an optional {@link MarineSecondary}
 * with ammo, and (if the role needs it) the {@link Objective} they're
 * assigned to. SABOTAGE missions put a PLANTER slot at the front of each
 * shuttle pointing at a specific charge site; everyone else stays
 * {@link UnitRole#COMBATANT}.
 *
 * <p>Held as an array on the air craft's {@code ShuttleMission}; index N gets popped off when the
 * N-th marine deboards. Null entries fall back to a plain combatant with
 * the default pulse-rifle primary and no secondary.
 */
public final class MarineLoadout {

    public static final MarineLoadout COMBATANT = new MarineLoadout(UnitRole.COMBATANT, null, MarineWeapon.PULSE_RIFLE, null, 0);

    public final UnitRole role;
    public final Objective objective;
    /** Primary handheld weapon. Null = use the {@link UnitType} default stats with no per-weapon FX. */
    public final MarineWeapon primary;
    /** Authoritative primary definition for contributed faction issue; null on legacy callers. */
    public final WeaponDef primaryDef;
    /** Manufacturing/condition tier of {@link #primary}. */
    public final EquipmentGrade equipmentGrade;
    /** Individual aptitude and earned field experience. */
    public final SoldierProfile soldierProfile;
    /** Optional secondary weapon. Null = no secondary slot. */
    public final MarineSecondary secondary;
    /** Authoritative special definition for data-authored faction issue; null means no special. */
    public final SpecialEquipmentDef specialDef;
    /** Starting ammo for the secondary. Ignored when {@link #secondary} is null. */
    public final int secondaryAmmo;
    /** Stable campaign identity, null for generated defender/employer soldiers. */
    public final String campaignSoldierId;
    /**
     * Campaign squad this seat belongs to; null for generated personnel, which
     * keeps them on the per-shuttle squad minting. Grouped into one value
     * because the id, the label and the leader flag always travel together.
     */
    public final CampaignSquadTag campaignSquad;
    /** Persisted modular armor allocation; null keeps the archetype default. */
    public final LayeredArmorFamily armorFamily;
    public final float armorPool;
    public final float armorRating;
    public final float armorMoveSpeedMult;
    public final float armorIncomingAccuracyMult;

    public MarineLoadout(UnitRole role, Objective objective) {
        this(role, objective, MarineWeapon.PULSE_RIFLE, null, 0);
    }

    public MarineLoadout(UnitRole role, Objective objective, MarineWeapon primary,
                         MarineSecondary secondary, int secondaryAmmo) {
        this(role, objective, primary, EquipmentGrade.SERVICE, SoldierProfile.REGULAR,
                secondary, secondaryAmmo, null, null);
    }

    public MarineLoadout(UnitRole role, Objective objective, MarineWeapon primary,
                         EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                         MarineSecondary secondary, int secondaryAmmo) {
        this(role, objective, primary, equipmentGrade, soldierProfile, secondary,
                secondaryAmmo, null, null);
    }

    public MarineLoadout(UnitRole role, Objective objective, MarineWeapon primary,
                         EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                         MarineSecondary secondary, int secondaryAmmo,
                         String campaignSoldierId, LayeredArmorFamily armorFamily) {
        this(role, objective, primary, equipmentGrade, soldierProfile, secondary,
                secondaryAmmo, campaignSoldierId, armorFamily, 0f, 0f, 1f, 1f);
    }

    public MarineLoadout(UnitRole role, Objective objective, MarineWeapon primary,
                         EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                         MarineSecondary secondary, int secondaryAmmo,
                         String campaignSoldierId, LayeredArmorFamily armorFamily,
                         float armorPool, float armorRating,
                         float armorMoveSpeedMult, float armorIncomingAccuracyMult) {
        this(role, objective, primary, equipmentGrade, soldierProfile, secondary,
                secondaryAmmo, campaignSoldierId, armorFamily, armorPool,
                armorRating, armorMoveSpeedMult, armorIncomingAccuracyMult,
                null);
    }

    public MarineLoadout(UnitRole role, Objective objective, MarineWeapon primary,
                         EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                         MarineSecondary secondary, int secondaryAmmo,
                         String campaignSoldierId, LayeredArmorFamily armorFamily,
                         float armorPool, float armorRating,
                         float armorMoveSpeedMult, float armorIncomingAccuracyMult,
                         CampaignSquadTag campaignSquad) {
        this.campaignSquad = campaignSquad;
        this.role = role;
        this.objective = objective;
        this.primary = primary;
        this.primaryDef = null;
        this.equipmentGrade = equipmentGrade != null ? equipmentGrade : EquipmentGrade.SERVICE;
        this.soldierProfile = soldierProfile != null ? soldierProfile : SoldierProfile.REGULAR;
        this.secondary = secondary;
        this.specialDef = null;
        this.secondaryAmmo = secondaryAmmo;
        this.campaignSoldierId = campaignSoldierId;
        this.armorFamily = armorFamily;
        this.armorPool = armorPool;
        this.armorRating = armorRating;
        this.armorMoveSpeedMult = armorMoveSpeedMult;
        this.armorIncomingAccuracyMult = armorIncomingAccuracyMult;
    }

    /** Builds generated faction issue directly from contributed catalog definitions. */
    public static MarineLoadout fromCatalog(
            UnitRole role, Objective objective, WeaponDef primary,
            EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
            SpecialEquipmentDef special, String campaignSoldierId,
            LayeredArmorFamily armorFamily, float armorPool, float armorRating,
            float armorMoveSpeedMult, float armorIncomingAccuracyMult) {
        return new MarineLoadout(role, objective, primary, equipmentGrade, soldierProfile,
                special, campaignSoldierId, armorFamily, armorPool, armorRating,
                armorMoveSpeedMult, armorIncomingAccuracyMult, null, true);
    }

    public static MarineLoadout fromCatalog(
            UnitRole role, Objective objective, WeaponDef primary,
            EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
            SpecialEquipmentDef special, String campaignSoldierId,
            LayeredArmorFamily armorFamily, float armorPool, float armorRating,
            float armorMoveSpeedMult, float armorIncomingAccuracyMult,
            CampaignSquadTag campaignSquad) {
        return new MarineLoadout(role, objective, primary, equipmentGrade, soldierProfile,
                special, campaignSoldierId, armorFamily, armorPool, armorRating,
                armorMoveSpeedMult, armorIncomingAccuracyMult, campaignSquad, true);
    }

    private MarineLoadout(UnitRole role, Objective objective, WeaponDef primary,
                          EquipmentGrade equipmentGrade, SoldierProfile soldierProfile,
                          SpecialEquipmentDef special, String campaignSoldierId,
                          LayeredArmorFamily armorFamily, float armorPool, float armorRating,
                          float armorMoveSpeedMult, float armorIncomingAccuracyMult,
                          CampaignSquadTag campaignSquad, boolean catalogAuthored) {
        this.campaignSquad = campaignSquad;
        this.role = role;
        this.objective = objective;
        this.primaryDef = primary;
        MarineWeapon primaryHandle;
        try {
            primaryHandle = primary != null ? MarineWeapon.fromId(primary.id) : null;
        } catch (IllegalArgumentException ignored) {
            primaryHandle = null;
        }
        this.primary = primaryHandle;
        this.equipmentGrade = equipmentGrade != null ? equipmentGrade : EquipmentGrade.SERVICE;
        this.soldierProfile = soldierProfile != null ? soldierProfile : SoldierProfile.REGULAR;
        this.specialDef = special;
        this.secondary = special != null
                ? SpecialEquipmentRegistry.compatibilityHandle(special.id()) : null;
        if (special != null && this.secondary == null) {
            throw new IllegalArgumentException("Special equipment '" + special.id()
                    + "' has no battle compatibility handle yet");
        }
        this.secondaryAmmo = special != null ? special.startingAmmo() : 0;
        this.campaignSoldierId = campaignSoldierId;
        this.armorFamily = armorFamily;
        this.armorPool = armorPool;
        this.armorRating = armorRating;
        this.armorMoveSpeedMult = armorMoveSpeedMult;
        this.armorIncomingAccuracyMult = armorIncomingAccuracyMult;
    }

    /**
     * Applies this loadout onto a deboard {@link EntitySpec}: role + objective, and
     * — when set — the primary weapon (its {@link EntitySpec#primaryWeapon} setter
     * derives the range/damage/accuracy/cooldown stat block) and the secondary
     * weapon + ammo. The one shared deboard loadout path — both the air
     * ({@code AirSystem}) and ground ({@code GroundSystem}) deboards call this so the
     * sequence lives in one place. (The BFS free-cell search around each LZ stays a
     * deliberate per-host copy.)
     */
    public void seedInto(EntitySpec marine) {
        marine.role(role);
        marine.assignedObjective(objective);
        if (primaryDef != null) {
            marine.primaryWeapon(primaryDef, equipmentGrade, soldierProfile);
        } else if (primary != null) {
            marine.primaryWeapon(primary, equipmentGrade, soldierProfile);
        } else {
            marine.soldierProfile(soldierProfile);
        }
        if (secondary != null && secondary.hasAvailableUse(secondaryAmmo)) {
            marine.secondary(secondary, secondaryAmmo);
        }
        marine.campaignSoldierId(campaignSoldierId);
        if (armorFamily != null) {
            marine.layeredArmorFamily(armorFamily);
            marine.armor(armorPool, armorRating,
                    armorMoveSpeedMult, armorIncomingAccuracyMult);
        }
    }
}
