package com.dillon.starsectormarines.marine;

import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.vehicle.VehicleKit;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.dillon.starsectormarines.marine.UnlockStation.A_SMALL_WAR;
import static com.dillon.starsectormarines.marine.UnlockStation.BLIND_WATCH;
import static com.dillon.starsectormarines.marine.UnlockStation.COLD_ACCOUNT;
import static com.dillon.starsectormarines.marine.UnlockStation.COLD_START;
import static com.dillon.starsectormarines.marine.UnlockStation.COUNTERATTACK;
import static com.dillon.starsectormarines.marine.UnlockStation.DUST_LEDGER;
import static com.dillon.starsectormarines.marine.UnlockStation.JUNCTION;
import static com.dillon.starsectormarines.marine.UnlockStation.NONE;
import static com.dillon.starsectormarines.marine.UnlockStation.RELIEF;
import static com.dillon.starsectormarines.marine.UnlockStation.SALVAGE_RIGHTS;
import static com.dillon.starsectormarines.marine.UnlockStation.START;
import static com.dillon.starsectormarines.marine.UnlockStation.THUNDER_RUN;
import static com.dillon.starsectormarines.marine.UnlockStation.WORKING_PASSAGE;

/**
 * The Kit Ladder: the station after which the company store sells each loadout card and machine pattern
 * ({@code progression-nouns.md}, Kit Ladder). Authored by the owner on the Kit Ladder page; {@code tools/kits/build.py}
 * reads this table to seed that page, so keep every row in the {@code card(...)} / {@code pattern(...)} shape.
 *
 * <p>The ladder gates only the store's channels (open market and licence). {@link UnlockStation#START} rows are what a
 * new company holds ({@link MechPattern#STARTER}, the armory's starter issue); {@link UnlockStation#NONE} rows are
 * never sold, though patrons and recovery may still hand them out by tier. An item with no row is not sold.
 */
public final class KitLadder {

    private static final Map<String, UnlockStation> STATIONS = new LinkedHashMap<>();

    static {
        // Weapon loadout cards. The Wayline paths are Relief's free pick (FirstRefit); the path not picked is sold.
        weapon(SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, START);
        weapon(FirstRefit.Path.STEADY_LINE.weapons, RELIEF);
        weapon(FirstRefit.Path.FORWARD_SCREEN.weapons, RELIEF);
        weapon(SquadEquipmentDoctrines.CHECKPOINT_WEAPONS, RELIEF);
        weapon(SquadEquipmentDoctrines.TRENCH_CLEARING_WEAPONS, COUNTERATTACK);
        weapon(SquadEquipmentDoctrines.CONVOY_ESCORT_WEAPONS, THUNDER_RUN);
        weapon(SquadEquipmentDoctrines.COUNTER_BATTERY_WEAPONS, BLIND_WATCH);
        weapon(SquadEquipmentDoctrines.FIRE_SUPPORT_WEAPONS, SALVAGE_RIGHTS);
        weapon(SquadEquipmentDoctrines.BUILDERS_GUARD_WEAPONS, WORKING_PASSAGE);
        weapon(SquadEquipmentDoctrines.LINE_INFANTRY_WEAPONS, WORKING_PASSAGE);
        weapon(SquadEquipmentDoctrines.LUDDIC_PATH_ASSAULT_WEAPONS, NONE);
        weapon(SquadEquipmentDoctrines.ASSAULT_WEAPONS, NONE);

        // Tactic sheets. Relief's free pick carries its path's sheet.
        sheet(SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR, START);
        sheet(SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_ARMOR, RELIEF);
        sheet(SquadEquipmentDoctrines.LEAGUE_MOBILE_ARMOR, RELIEF);
        sheet(SquadEquipmentDoctrines.OUTLAW_RAIDER_ARMOR, JUNCTION);
        sheet(SquadEquipmentDoctrines.RECON_ARMOR, THUNDER_RUN);
        sheet(SquadEquipmentDoctrines.FLEET_COMBAT_ARMOR, BLIND_WATCH);
        sheet(SquadEquipmentDoctrines.SINDRIAN_LINE_ARMOR, BLIND_WATCH);
        sheet(SquadEquipmentDoctrines.HEGEMONY_AUXILIARY_SECTION_ARMOR, COLD_ACCOUNT);
        sheet(SquadEquipmentDoctrines.OUTLAW_HEAVY_ARMOR, COLD_START);
        sheet(SquadEquipmentDoctrines.HEGEMONY_SHOCK_ARMOR, WORKING_PASSAGE);
        sheet(SquadEquipmentDoctrines.CORPORATE_HEAVY_ARMOR, WORKING_PASSAGE);
        sheet(SquadEquipmentDoctrines.LEAGUE_HEAVY_ARMOR, WORKING_PASSAGE);
        sheet(SquadEquipmentDoctrines.KNIGHTS_HEAVY_ARMOR, WORKING_PASSAGE);
        sheet(SquadEquipmentDoctrines.LIONS_GUARD_HEAVY_ARMOR, WORKING_PASSAGE);
        sheet(SquadEquipmentDoctrines.CORPORATE_SPECTER_SECTION_ARMOR, A_SMALL_WAR);
        sheet(SquadEquipmentDoctrines.HEGEMONY_ARBALEST_SECTION_ARMOR, A_SMALL_WAR);
        sheet(SquadEquipmentDoctrines.BOARDING_JACK_SECTION_ARMOR, A_SMALL_WAR);
        sheet(SquadEquipmentDoctrines.SINDRIAN_SECURITY_ARMOR, NONE);
        sheet(SquadEquipmentDoctrines.CHURCH_WARDEN_ARMOR, NONE);
        sheet(SquadEquipmentDoctrines.LEAGUE_LINE_ARMOR, NONE);
        sheet(SquadEquipmentDoctrines.CORPORATE_LINE_ARMOR, NONE);
        sheet(SquadEquipmentDoctrines.CHURCH_LINE_ARMOR, NONE);
        sheet(SquadEquipmentDoctrines.OUTLAW_LINE_ARMOR, NONE);
        sheet(SquadEquipmentDoctrines.FREEPORT_ASSAULT_ARMOR, NONE);
        sheet(SquadEquipmentDoctrines.SALVAGE_ASSISTED_ARMOR, NONE);

        // Mech chassis.
        pattern(MechVariant.HOUND, START);
        pattern(MechVariant.WARDEN, JUNCTION);
        pattern(MechVariant.BULWARK, BLIND_WATCH);

        // Mech weapons.
        pattern(MechWeaponComponent.LIGHT_MACHINE_GUN, START);
        pattern(MechWeaponComponent.SRM_5_FRAG, START);
        pattern(MechWeaponComponent.CHAINGUN, COUNTERATTACK);
        pattern(MechWeaponComponent.AC_6_QS, COUNTERATTACK);
        pattern(MechWeaponComponent.DC_4_QS, COUNTERATTACK);
        pattern(MechWeaponComponent.SRM_3_QS, COUNTERATTACK);
        pattern(MechWeaponComponent.SRM_5, COUNTERATTACK);
        pattern(MechWeaponComponent.LRM_5, COUNTERATTACK);
        pattern(MechWeaponComponent.LRM_5_FRAG, COUNTERATTACK);
        pattern(MechWeaponComponent.LIGHT_PULSE_LASER, COUNTERATTACK);
        pattern(MechWeaponComponent.LIGHT_BURST_LASER, COUNTERATTACK);
        pattern(MechWeaponComponent.CHAINGUN_HF, JUNCTION);
        pattern(MechWeaponComponent.SRM_15, JUNCTION);
        pattern(MechWeaponComponent.SRM_15_FRAG, JUNCTION);
        pattern(MechWeaponComponent.LRM_15, JUNCTION);
        pattern(MechWeaponComponent.LRM_15_FRAG, JUNCTION);
        pattern(MechWeaponComponent.MEDIUM_BURST_LASER, JUNCTION);
        pattern(MechWeaponComponent.PD_LASER, JUNCTION);
        pattern(MechWeaponComponent.AC_6, THUNDER_RUN);
        pattern(MechWeaponComponent.HC_10, THUNDER_RUN);
        pattern(MechWeaponComponent.HC_20, THUNDER_RUN);
        pattern(MechWeaponComponent.DC_5, THUNDER_RUN);
        pattern(MechWeaponComponent.PULSE_LASER, THUNDER_RUN);
        pattern(MechWeaponComponent.BURST_LASER, THUNDER_RUN);
        pattern(MechWeaponComponent.HEAVY_PD_LASER, THUNDER_RUN);
        pattern(MechWeaponComponent.SHOULDER_LASER_CANNON, THUNDER_RUN);
        pattern(MechWeaponComponent.THERMAL_LANCE, THUNDER_RUN);

        // Mech internals.
        pattern(MechInternal.INDUSTRIAL_ENGINE, START);
        pattern(MechInternal.FACTORY_PLATING, START);
        pattern(MechInternal.FACTORY_CAPACITOR_BANK, START);
        pattern(MechInternal.STANDARD_LOADER, START);
        pattern(MechInternal.UPRATED_ENGINE, COUNTERATTACK);
        pattern(MechInternal.COMPOSITE_PLATING, COUNTERATTACK);
        pattern(MechInternal.DOUBLE_CAPACITOR_BANK, COUNTERATTACK);
        pattern(MechInternal.AUXILIARY_VENT_ARRAY, COUNTERATTACK);
        pattern(MechInternal.ACCELERATED_LOADER, COUNTERATTACK);
        pattern(MechInternal.MILITARY_ENGINE, JUNCTION);
        pattern(MechInternal.HEAVY_PLATING, JUNCTION);
        pattern(MechInternal.MILITARY_CAPACITOR_BANK, JUNCTION);

        // Vehicle hulls. The troop carrier, supply truck and scout car are never sold: encounters still field them.
        pattern(VehicleType.MILITIA_TECHNICAL, COUNTERATTACK);
        pattern(VehicleType.SRM_TECHNICAL, JUNCTION);
        pattern(VehicleType.FIRE_SUPPORT_APC, JUNCTION);
        pattern(VehicleType.BREAKTHROUGH_TANK, THUNDER_RUN);
        pattern(VehicleType.LRM_CARRIER, THUNDER_RUN);
        pattern(VehicleType.DRONE_SKIMMER, THUNDER_RUN);
        pattern(VehicleType.HOVER_SCOUT, DUST_LEDGER);
        pattern(VehicleType.DOZER_BREACHER, DUST_LEDGER);
        pattern(VehicleType.LASER_TANK, BLIND_WATCH);
        pattern(VehicleType.HEAVY_APC, NONE);
        pattern(VehicleType.SUPPLY_TRUCK, NONE);
        pattern(VehicleType.SCOUT_CAR, NONE);

        // Vehicle kits.
        pattern(VehicleKit.SPALL_LINER, RELIEF);
        pattern(VehicleKit.READY_RACK, RELIEF);
        pattern(VehicleKit.SENSOR_PAIR, THUNDER_RUN);
        pattern(VehicleKit.ACCELERATED_LOADER, THUNDER_RUN);
        pattern(VehicleKit.ACTIVE_PROTECTION, THUNDER_RUN);
    }

    private KitLadder() {}

    /** The station of a loadout card id or mech pattern id; {@link UnlockStation#NONE} for one with no row. */
    public static UnlockStation station(String id) {
        UnlockStation station = id != null ? STATIONS.get(id) : null;
        return station != null ? station : NONE;
    }

    /** Whether the store may sell {@code id} to a company that has passed {@code reached}. */
    public static boolean forSale(String id, Set<UnlockStation> reached) {
        UnlockStation station = station(id);
        return station != NONE && reached != null && reached.contains(station);
    }

    /** Every row, card and pattern ids alike, in authored order. */
    public static Map<String, UnlockStation> stations() {
        return Collections.unmodifiableMap(STATIONS);
    }

    private static void weapon(String doctrineId, UnlockStation station) {
        put(new LoadoutCard(LoadoutCard.Kind.WEAPON, doctrineId).id(), station);
    }

    private static void sheet(String planId, UnlockStation station) {
        put(new LoadoutCard(LoadoutCard.Kind.SHEET, planId).id(), station);
    }

    private static void pattern(MechVariant variant, UnlockStation station) {
        put(MechPattern.of(variant).id(), station);
    }

    private static void pattern(MechWeaponComponent component, UnlockStation station) {
        put(MechPattern.of(component).id(), station);
    }

    private static void pattern(MechInternal internal, UnlockStation station) {
        put(MechPattern.of(internal).id(), station);
    }

    private static void pattern(VehicleType type, UnlockStation station) {
        put(MechPattern.of(type).id(), station);
    }

    private static void pattern(VehicleKit kit, UnlockStation station) {
        put(MechPattern.of(kit).id(), station);
    }

    /** One row per item: placing an item twice is an authoring error, refused when the ladder loads. */
    private static void put(String id, UnlockStation station) {
        if (STATIONS.putIfAbsent(id, station) != null) {
            throw new IllegalStateException("Kit Ladder places " + id + " twice");
        }
    }
}
