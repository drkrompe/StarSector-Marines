package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftAdmission;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.vehicle.VehicleDeploymentSpec;
import com.dillon.starsectormarines.battle.power.ShipOnStation;
import com.dillon.starsectormarines.battle.power.SpawnInOnslaught;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.SupplyRouteCampaign;
import com.dillon.starsectormarines.ops.CorvusWaylineCampaign;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.dillon.starsectormarines.ops.AgreusArcCampaign;
import com.dillon.starsectormarines.ops.DistrictEscortCampaign;
import com.dillon.starsectormarines.campaign.AgreusArcProgress;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Maps committed ships (their base hull + fitted hull mods) and the mission's
 * employer offerings to the player's active command-power roster — the diegetic
 * "your fleet is your spellbook" core. A hardcoded Java registry, matching the
 * {@code ShuttleType.forHullId} / {@code PlayerFleetWings.profileFromWingId}
 * precedent: spoils-tier "super" mods are just new rows here, no data files.
 *
 * <p>Mapping is many-to-one and one-to-many (several ships can grant the same
 * power; one ship can grant several); {@link #resolve} dedupes by power id, so
 * two Valkyries still surface one Mech Support.
 */
public final class PowerCatalog {

    private static final Logger LOG = Global.getLogger(PowerCatalog.class);

    /**
     * Ids of command powers since removed (Recon Ping, Resupply, Marine Drop, Armour
     * Support) that a frozen commitment may still name; {@link #restore} drops them.
     */
    private static final Set<String> RETIRED_IDS = Set.of(
            "recon_ping", "emergency_resupply", "marine_insertion", "vehicle_support");
    /** Armour Support's retired id: the bay vehicles it froze now ride Mech Support. */
    private static final String RETIRED_ARMOUR_SUPPORT = "vehicle_support";
    private static final String VALKYRIE_HULL = "valkyrie";
    private static final String GROUND_SUPPORT_MOD = "ground_support";
    private static final String ADVANCED_GROUND_SUPPORT_MOD = "advanced_ground_support";
    private static final String ONSLAUGHT_HULL = "onslaught";
    private static final String INVICTUS_HULL = "invictus";

    private PowerCatalog() {}

    /**
     * Resolve the command-power roster for a battle from the committed ships plus
     * the mission's employer offerings, deduped by power id.
     */
    public static List<CommandPower> resolve(List<FleetMemberAPI> committedShips, Mission m) {
        return resolve(committedShips, m, List.of());
    }

    /** Resolve powers and, when sourced, configure Mech Support from campaign inventory. */
    public static List<CommandPower> resolve(List<FleetMemberAPI> committedShips, Mission m,
                                             List<MechDeploymentSpec> configuredMechs) {
        return resolve(committedShips, m, configuredMechs, null);
    }

    /**
     * As above, plus the mech bay's frozen vehicles, which ride Mech Support's manifest.
     * {@code null} vehicles means no bay was consulted (debug, detached fixtures).
     */
    public static List<CommandPower> resolve(List<FleetMemberAPI> committedShips, Mission m,
                                             List<MechDeploymentSpec> configuredMechs,
                                             List<VehicleDeploymentSpec> configuredVehicles) {
        return resolve(committedShips, m, configuredMechs, configuredVehicles, List.of());
    }

    /**
     * As above, with the company boats committed to the operation. A cradled
     * boat that lifts the drop flies Mech Support in place of
     * the synthetic Valkyrie when a fleet ship grants the power; where the
     * mission or the employer supplies the lift the synthetic carrier stays.
     * Who may call is unchanged: this only decides who flies.
     */
    public static List<CommandPower> resolve(List<FleetMemberAPI> committedShips, Mission m,
                                             List<MechDeploymentSpec> configuredMechs,
                                             List<VehicleDeploymentSpec> configuredVehicles,
                                             List<FittedBoat> committedBoats) {
        List<CommandPower> powers = bayManifest(resolveSourced(committedShips, m, configuredMechs),
                arrangedHeavyLift(m), configuredMechs, configuredVehicles);
        if (committedBoats == null || committedBoats.isEmpty()) return powers;
        Map<String, CommandPower> byId = new LinkedHashMap<>();
        for (CommandPower power : powers) byId.put(power.id, power);
        assignCompanyLift(byId, fleetGranted(committedShips, m), committedBoats);
        return List.copyOf(byId.values());
    }

    /**
     * One bay, one call: Mech Support carries the bay's vehicles in its manifest after its
     * mechs, so the player calls any bay space from one picker ({@code mechs-nouns.md}, Armor
     * drop). Story battles whose employer arranges the heavy lift grant Mech Support for a
     * bay that holds only vehicles, as they do for its mechs. {@code null} vehicles means no
     * bay was consulted, and the roster is unchanged.
     */
    static List<CommandPower> bayManifest(List<CommandPower> powers, boolean arrangedLift,
                                          List<MechDeploymentSpec> configuredMechs,
                                          List<VehicleDeploymentSpec> configuredVehicles) {
        if (configuredVehicles == null || configuredVehicles.isEmpty()) return powers;
        List<CommandPower> out = new ArrayList<>(powers.size() + 1);
        boolean carried = false;
        for (CommandPower power : powers) {
            if (power instanceof MechSupport mech) {
                out.add(MechSupport.configured(configuredMechs != null ? configuredMechs
                        : mech.deployments(), configuredVehicles, null, true));
                carried = true;
            } else {
                out.add(power);
            }
        }
        if (!carried && arrangedLift) {
            out.add(MechSupport.configured(configuredMechs != null ? configuredMechs : List.of(),
                    configuredVehicles, null, true));
        }
        return List.copyOf(out);
    }

    /**
     * Powers a fleet ship (or a debug grant) brings, as opposed to the employer's
     * offer or a story mission's arranged lift: only these may be flown by the
     * company's own boats.
     */
    static Set<String> fleetGranted(List<FleetMemberAPI> committedShips, Mission m) {
        if (AgreusArcCampaign.isBattle(m) || SupplyRouteCampaign.isBattle(m)
                || CorvusWaylineCampaign.isBattle(m) || ShepherdRecoveryCampaign.isBattle(m)
                || DistrictEscortCampaign.isBattle(m)) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        if (com.dillon.starsectormarines.DevConfig.DEBUG_GRANT_MECH_SUPPORT) ids.add(MechSupport.ID);
        if (committedShips != null) {
            for (FleetMemberAPI ship : committedShips) {
                for (CommandPower power : contributedBy(ship)) {
                    if (!(power instanceof ShipOnStation)) ids.add(power.id);
                }
            }
        }
        return ids;
    }

    /**
     * Hands a fleet-granted Mech Support the cradled company boat best suited to
     * its manifest. A power the employer offered keeps its synthetic carrier,
     * and the synthetic lift stays behind a company boat for the calls it
     * cannot fly.
     */
    static void assignCompanyLift(Map<String, CommandPower> byId, Set<String> fleetGranted,
                                  List<FittedBoat> committedBoats) {
        if (committedBoats == null || committedBoats.isEmpty()) return;
        if (fleetGranted.contains(MechSupport.ID)
                && byId.get(MechSupport.ID) instanceof MechSupport mech) {
            List<LiftClass> load = mech.manifest().entries().stream()
                    .map(BayManifest.Entry::weightClass).toList();
            FittedBoat boat = LiftAdmission.bestCarrier(committedBoats, load, Set.of());
            if (boat != null) {
                byId.put(MechSupport.ID, MechSupport.configured(mech.deployments(),
                        mech.vehicles(), boat, true));
            }
        }
    }

    /** The story battles whose employer lifts the company's own armour; see {@link #resolveSourced}. */
    private static boolean arrangedHeavyLift(Mission m) {
        if (AgreusArcCampaign.isBattle(m)) {
            return AgreusArcProgress.yardOwners(m.id) || AgreusArcProgress.termsOfProtection(m.id)
                    || AgreusArcProgress.smallWar(m.id);
        }
        return SupplyRouteCampaign.isBattle(m) || CorvusWaylineCampaign.isBattle(m)
                || ShepherdRecoveryCampaign.isBattle(m) || DistrictEscortCampaign.isBattle(m);
    }

    private static List<CommandPower> resolveSourced(List<FleetMemberAPI> committedShips, Mission m,
                                                     List<MechDeploymentSpec> configuredMechs) {
        if (AgreusArcCampaign.isBattle(m)) {
            // A Small War has no construction step, so it offers no tender call.
            var tenders = AgreusArcCampaign.requiresConstructionTender(m) ? AgreusConstructionSupport.sources(committedShips) : List.<ShipSupportSource>of();
            List<CommandPower> powers = new ArrayList<>();
            if (!tenders.isEmpty()) powers.add(new ShipOnStation(tenders));
            if ((AgreusArcProgress.yardOwners(m.id) || AgreusArcProgress.termsOfProtection(m.id) || AgreusArcProgress.smallWar(m.id)) && configuredMechs != null && !configuredMechs.isEmpty())
                powers.add(MechSupport.configured(configuredMechs));
            return List.copyOf(powers);
        }
        // Wayline arranges the heavy lift, but supplies no free chassis. Empty
        // bays must not fall through MechSupport's legacy debug default.
        if (SupplyRouteCampaign.isBattle(m) || CorvusWaylineCampaign.isBattle(m) || ShepherdRecoveryCampaign.isBattle(m)
                || DistrictEscortCampaign.isBattle(m)) {
            return configuredMechs == null || configuredMechs.isEmpty() ? List.of()
                    : List.of(MechSupport.configured(configuredMechs));
        }
        Map<String, CommandPower> byId = new LinkedHashMap<>();

        if (com.dillon.starsectormarines.DevConfig.DEBUG_GRANT_MECH_SUPPORT) {
            byId.put(MechSupport.ID, new MechSupport());
        }
        if (com.dillon.starsectormarines.DevConfig.DEBUG_GRANT_ORBITAL_BARRAGE) {
            byId.put(OrbitalBarrage.ID, new OrbitalBarrage());
        }
        if (com.dillon.starsectormarines.DevConfig.DEBUG_GRANT_CLOSE_AIR_SUPPORT) {
            byId.put(CloseAirSupport.ID, new CloseAirSupport());
        }

        List<ShipSupportSource> ships = new ArrayList<>();
        if (committedShips != null) {
            for (FleetMemberAPI ship : committedShips) {
                for (CommandPower power : contributedBy(ship)) {
                    // Ships are separate sources of one power: each is its own charge.
                    if (power instanceof ShipOnStation shipPower) ships.addAll(shipPower.sources());
                    else byId.putIfAbsent(power.id, power);
                }
            }
        }
        for (String id : employerPowerIds(m)) {
            ShipSupportSource offered = employerShipSource(id);
            if (offered != null) {
                ships.add(offered);
                continue;
            }
            CommandPower p = forId(id);
            if (p != null) byId.putIfAbsent(p.id, p);
        }
        if (!ships.isEmpty()) byId.put(ShipOnStation.ID, new ShipOnStation(ships));
        if (byId.containsKey(MechSupport.ID)
                && configuredMechs != null && !configuredMechs.isEmpty()) {
            byId.put(MechSupport.ID, MechSupport.configured(configuredMechs));
        }

        LOG.debug("PowerCatalog: resolved " + byId.size() + " power(s) " + byId.keySet()
                + " from " + (committedShips == null ? 0 : committedShips.size()) + " committed ship(s)");
        return new ArrayList<>(byId.values());
    }

    /**
     * Rebuilds one frozen battle commitment through the same constructors that
     * own production power tuning. Mech Support is the sole power with a
     * launch-specific payload; attaching one to another power is invalid.
     */
    public static CommandPower restore(String id, List<MechDeploymentSpec> mechDeployments) {
        return restore(id, mechDeployments, List.of());
    }

    /** As above, plus the frozen ships a Ship on Station commitment carries. */
    public static CommandPower restore(String id,
                                       List<MechDeploymentSpec> mechDeployments,
                                       List<ShipSupportSource> ships) {
        return restore(id, mechDeployments, ships, List.of());
    }

    /** As above, plus the frozen company vehicles a Mech Support commitment carries. */
    public static CommandPower restore(String id,
                                       List<MechDeploymentSpec> mechDeployments,
                                       List<ShipSupportSource> ships,
                                       List<VehicleDeploymentSpec> vehicleDeployments) {
        return restore(id, mechDeployments, ships, vehicleDeployments, null);
    }

    /**
     * As above, plus the company boat a drop power was given to fly with
     * (synthetic lift stays behind it), or {@code null}.
     *
     * <p>Returns {@code null} for a removed power's id, so a frozen commitment that
     * still names one launches without it; a retired Armour Support's vehicles
     * restore as a Mech Support carrying them.
     */
    public static CommandPower restore(String id,
                                       List<MechDeploymentSpec> mechDeployments,
                                       List<ShipSupportSource> ships,
                                       List<VehicleDeploymentSpec> vehicleDeployments,
                                       FittedBoat companyCarrier) {
        if (RETIRED_ARMOUR_SUPPORT.equals(id)
                && vehicleDeployments != null && !vehicleDeployments.isEmpty()) {
            return restore(MechSupport.ID, List.of(), List.of(), vehicleDeployments, companyCarrier);
        }
        if (RETIRED_IDS.contains(id)) {
            LOG.info("PowerCatalog: dropping retired command power '" + id + "' from a frozen commitment");
            return null;
        }
        if (companyCarrier != null) {
            CommandPower base = restore(id, mechDeployments, ships, vehicleDeployments, null);
            if (base instanceof MechSupport mech) {
                return MechSupport.configured(mech.deployments(), mech.vehicles(),
                        companyCarrier, true);
            }
            throw new IllegalArgumentException("Power '" + id + "' cannot carry a company boat");
        }
        if (vehicleDeployments != null && !vehicleDeployments.isEmpty()) {
            if (MechSupport.ID.equals(id)) {
                return MechSupport.configured(mechDeployments == null ? List.of() : mechDeployments,
                        vehicleDeployments, null, true);
            }
            throw new IllegalArgumentException("Power '" + id + "' cannot carry vehicles");
        }
        Objects.requireNonNull(id, "id");
        List<MechDeploymentSpec> deployments = mechDeployments == null
                ? List.of() : List.copyOf(mechDeployments);
        if (ShipOnStation.ID.equals(id)) return new ShipOnStation(ships == null ? List.of() : ships);
        if (ships != null && !ships.isEmpty()) {
            throw new IllegalArgumentException("Power '" + id + "' cannot carry ships");
        }
        if (MechSupport.ID.equals(id)) {
            return deployments.isEmpty()
                    ? new MechSupport() : MechSupport.configured(deployments);
        }
        if (!deployments.isEmpty()) {
            throw new IllegalArgumentException(
                    "Power '" + id + "' cannot carry mech deployments");
        }
        CommandPower power = forId(id);
        if (power == null) {
            throw new IllegalArgumentException("Unknown command power id '" + id + "'");
        }
        return power;
    }

    /**
     * Powers contributed by one fleet member, without employer or debug grants.
     * This is also the source of truth for the briefing's member-level
     * commitment rows, so presentation and launch resolution cannot drift.
     */
    public static List<CommandPower> contributedBy(FleetMemberAPI ship) {
        if (ship == null) return List.of();
        ShipVariantAPI v = ship.getVariant();
        Collection<String> mods = v != null && v.getHullMods() != null
                ? v.getHullMods() : Set.of();
        String baseId = ship.getHullSpec() != null ? ship.getHullSpec().getBaseHullId() : null;
        List<String> ids = contributedPowerIds(baseId, mods);
        List<CommandPower> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            CommandPower power = forId(id);
            if (power != null) out.add(power);
        }
        ShipSupportSource source = shipSourceOf(ship);
        if (source != null) out.add(new ShipOnStation(List.of(source)));
        return out;
    }

    /**
     * The ship a committed member becomes when called over a battle, frozen from its fitted
     * variant: a cruiser or capital with a gun that reaches the ground, or the Shepherd as a
     * tender. Null for any other hull. No campaign object survives in the result.
     */
    public static ShipSupportSource shipSourceOf(FleetMemberAPI ship) {
        if (ship == null || ship.getVariant() == null || ship.getHullSpec() == null) return null;
        String name = ship.getShipName() != null ? ship.getShipName() : "Support ship";
        ShipSupportSource frozen = ShipSupportSource.ofVariant(ship.getVariant(), name, false);
        ShipSupportSource source = ShipSupportSource.of(frozen.hullId(), ship.getHullSpec().getHullSize(), name, frozen.weapons());
        return source == null ? null : source.withMemberId(ship.getId());
    }

    /** The stock ship an employer offers as {@code ship_on_station:<hull>}; null for any other id. */
    static ShipSupportSource employerShipSource(String id) {
        if (id == null || !id.startsWith(ShipOnStation.EMPLOYER_PREFIX)) return null;
        return ShipSupportSource.stockFor(id.substring(ShipOnStation.EMPLOYER_PREFIX.length()));
    }

    /** Adds stock or debug ships to the roster's Ship on Station power, creating the power if the roster has none. */
    public static List<CommandPower> withShipSources(List<CommandPower> powers, List<ShipSupportSource> extra) {
        if (extra == null || extra.isEmpty()) return powers;
        List<ShipSupportSource> ships = new ArrayList<>();
        List<CommandPower> out = new ArrayList<>();
        int at = -1;
        for (CommandPower power : powers) {
            if (power instanceof ShipOnStation existing) {
                ships.addAll(existing.sources());
                at = out.size();
            }
            out.add(power);
        }
        ships.addAll(extra);
        ShipOnStation merged = new ShipOnStation(ships);
        if (at >= 0) out.set(at, merged);
        else out.add(merged);
        return out;
    }

    /** Pure capability mapping used by {@link #contributedBy(FleetMemberAPI)}. */
    static List<String> contributedPowerIds(String baseId, Collection<String> mods) {
        Set<String> ids = new LinkedHashSet<>();
        Collection<String> fitted = mods != null ? mods : Set.of();
        if (fitted.contains(GROUND_SUPPORT_MOD) || fitted.contains(ADVANCED_GROUND_SUPPORT_MOD)) {
            ids.add(MechSupport.ID);
            ids.add(OrbitalBarrage.ID);
        }
        if (VALKYRIE_HULL.equals(baseId)) ids.add(MechSupport.ID);
        if (baseId != null && ShuttleType.MUDSKIPPER.matchingHullIds.contains(baseId)) {
            ids.add(CloseAirSupport.ID);
        }
        if (ONSLAUGHT_HULL.equals(baseId) || INVICTUS_HULL.equals(baseId)) {
            ids.add(OrbitalBarrage.ID);
        }
        return new ArrayList<>(ids);
    }

    /** The employer's offered power ids for this mission (the contract co-source). */
    private static List<String> employerPowerIds(Mission m) {
        return m != null && m.employerPowerIds != null ? m.employerPowerIds : List.of();
    }

    /** Maps a stable power id to a fresh power instance. Returns null for unknown ids. */
    private static CommandPower forId(String id) {
        if (MechSupport.ID.equals(id)) return new MechSupport();
        if (OrbitalBarrage.ID.equals(id)) return new OrbitalBarrage();
        if (CloseAirSupport.ID.equals(id)) return new CloseAirSupport();
        if (SpawnInOnslaught.ID.equals(id)) return new SpawnInOnslaught();
        return null;
    }
}
