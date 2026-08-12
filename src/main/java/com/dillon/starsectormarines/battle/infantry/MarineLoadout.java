package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.appearance.LayeredArmorFamily;

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
    /** Manufacturing/condition tier of {@link #primary}. */
    public final EquipmentGrade equipmentGrade;
    /** Individual aptitude and earned field experience. */
    public final SoldierProfile soldierProfile;
    /** Optional secondary weapon. Null = no secondary slot. */
    public final MarineSecondary secondary;
    /** Starting ammo for the secondary. Ignored when {@link #secondary} is null. */
    public final int secondaryAmmo;
    /** Stable campaign identity, null for generated defender/employer soldiers. */
    public final String campaignSoldierId;
    /** Persisted modular armor allocation; null keeps the archetype default. */
    public final LayeredArmorFamily armorFamily;

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
        this.role = role;
        this.objective = objective;
        this.primary = primary;
        this.equipmentGrade = equipmentGrade != null ? equipmentGrade : EquipmentGrade.SERVICE;
        this.soldierProfile = soldierProfile != null ? soldierProfile : SoldierProfile.REGULAR;
        this.secondary = secondary;
        this.secondaryAmmo = secondaryAmmo;
        this.campaignSoldierId = campaignSoldierId;
        this.armorFamily = armorFamily;
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
        if (primary != null) {
            marine.primaryWeapon(primary, equipmentGrade, soldierProfile);
        } else {
            marine.soldierProfile(soldierProfile);
        }
        if (secondary != null && secondaryAmmo > 0) {
            marine.secondary(secondary, secondaryAmmo);
        }
        marine.campaignSoldierId(campaignSoldierId);
        if (armorFamily != null) marine.layeredArmorFamily(armorFamily);
    }
}
