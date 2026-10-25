package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftAdmission;
import com.dillon.starsectormarines.battle.air.LiftClass;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.VehicleSupportPayload;
import com.dillon.starsectormarines.battle.power.CloseAirSupport;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.EmergencyResupply;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.MarineInsertion;
import com.dillon.starsectormarines.battle.power.VehicleSupport;
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
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
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
 * three scanner ships still surface one Recon Ping.
 */
public final class PowerCatalog {

    private static final Logger LOG = Global.getLogger(PowerCatalog.class);

    // ---- Recon Ping sources (survey § Recon/Intel/EW, through the projection lens) ----
    /** High Resolution Sensors — flavor text literally "increases the ship's in-combat vision range." No {@code ids.HullMods} constant exists for it, hence the literal. */
    private static final String HIRES_SENSORS_MOD = "hiressensors";
    /** Surveying Equipment — the planetary-investigation tool. */
    private static final String SURVEYING_EQUIPMENT_MOD = HullMods.SURVEYING_EQUIPMENT;
    /** Apogee — built-in survey/sensor suite (the canonical scanner cruiser). Base hull id. */
    private static final String APOGEE_HULL = "apogee";
    private static final String VALKYRIE_HULL = "valkyrie";
    private static final String GROUND_SUPPORT_MOD = "ground_support";
    private static final String ADVANCED_GROUND_SUPPORT_MOD = "advanced_ground_support";
    private static final String TARSUS_HULL = "tarsus";
    private static final String ATLAS_HULL = "atlas";
    private static final String ONSLAUGHT_HULL = "onslaught";
    private static final String INVICTUS_HULL = "invictus";

    private PowerCatalog() {}

    /**
     * Resolve the command-power roster for a battle from the committed ships plus
     * the mission's employer offerings, deduped by power id.
     *
     * <p>Recon ping is now <em>sourced</em> — from a committed ship's kit (below)
     * or the employer's offer. The {@link com.dillon.starsectormarines.DevConfig#ALWAYS_GRANT_RECON_PING}
     * dev flag seeds it unconditionally so the loop stays demoable on a fleet
     * with no recon ship (the power UI hides itself on an empty roster); flip it
     * off to feel the real gating.
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
     * As above, plus the mech bay's frozen vehicles for Armour Support. {@code null}
     * vehicles means no bay was consulted (debug, detached fixtures): a sourced Armour
     * Support keeps its fallback hull. An empty list means the bay holds no vehicle, and
     * Armour Support has nothing to bring down.
     */
    public static List<CommandPower> resolve(List<FleetMemberAPI> committedShips, Mission m,
                                             List<MechDeploymentSpec> configuredMechs,
                                             List<VehicleDeploymentSpec> configuredVehicles) {
        return resolve(committedShips, m, configuredMechs, configuredVehicles, List.of());
    }

    /**
     * As above, with the company boats committed to the operation. A cradled
     * boat that lifts the drop flies Mech Support or Armour Support in place of
     * the synthetic Valkyrie when a fleet ship grants the power; where the
     * mission or the employer supplies the lift the synthetic carrier stays.
     * Who may call is unchanged: this only decides who flies.
     */
    public static List<CommandPower> resolve(List<FleetMemberAPI> committedShips, Mission m,
                                             List<MechDeploymentSpec> configuredMechs,
                                             List<VehicleDeploymentSpec> configuredVehicles,
                                             List<FittedBoat> committedBoats) {
        List<CommandPower> powers = bayManifest(withBayVehicles(
                resolveSourced(committedShips, m, configuredMechs),
                arrangedHeavyLift(m), configuredVehicles), configuredMechs, configuredVehicles);
        if (committedBoats == null || committedBoats.isEmpty()) return powers;
        Map<String, CommandPower> byId = new LinkedHashMap<>();
        for (CommandPower power : powers) byId.put(power.id, power);
        assignCompanyLift(byId, fleetGranted(committedShips, m), committedBoats);
        return List.copyOf(byId.values());
    }

    /**
     * One bay, one call: where both drop powers are in the roster and the bay was read,
     * Mech Support carries the bay's vehicles in its manifest beside its mechs and
     * Armour Support leaves the roster, so the player picks any bay space from one picker
     * ({@code mechs-nouns.md}, Armor drop). Armour Support alone (no mech source) is unchanged.
     */
    static List<CommandPower> bayManifest(List<CommandPower> powers,
                                          List<MechDeploymentSpec> configuredMechs,
                                          List<VehicleDeploymentSpec> configuredVehicles) {
        if (configuredMechs == null || configuredVehicles == null || configuredVehicles.isEmpty()) {
            return powers;
        }
        boolean mech = false;
        boolean vehicles = false;
        for (CommandPower power : powers) {
            mech |= power instanceof MechSupport;
            vehicles |= power instanceof VehicleSupport;
        }
        if (!mech || !vehicles) return powers;
        List<CommandPower> out = new ArrayList<>(powers.size());
        for (CommandPower power : powers) {
            if (power instanceof VehicleSupport) continue;
            out.add(power instanceof MechSupport
                    ? MechSupport.configured(configuredMechs, configuredVehicles, null, true)
                    : power);
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
     * Hands each fleet-granted drop power the cradled company boat best suited
     * to it, one boat per power, Mech Support first. A power the employer
     * offered keeps its synthetic carrier, and the synthetic lift stays behind
     * a company boat for the calls it cannot fly.
     */
    static void assignCompanyLift(Map<String, CommandPower> byId, Set<String> fleetGranted,
                                  List<FittedBoat> committedBoats) {
        if (committedBoats == null || committedBoats.isEmpty()) return;
        Set<FittedBoat> taken = new LinkedHashSet<>();
        if (fleetGranted.contains(MechSupport.ID)
                && byId.get(MechSupport.ID) instanceof MechSupport mech) {
            List<LiftClass> load = mech.manifest().entries().stream()
                    .map(BayManifest.Entry::weightClass).toList();
            FittedBoat boat = LiftAdmission.bestCarrier(committedBoats, load, taken);
            if (boat != null) {
                byId.put(MechSupport.ID, MechSupport.configured(mech.deployments(),
                        mech.vehicles(), boat, true));
                taken.add(boat);
            }
        }
        if (fleetGranted.contains(VehicleSupport.ID)
                && byId.get(VehicleSupport.ID) instanceof VehicleSupport vehicles) {
            List<LiftClass> load = vehicles.deployments().isEmpty()
                    ? List.of(VehicleSupportPayload.FALLBACK.liftClass())
                    : vehicles.deployments().stream()
                            .map(deployment -> deployment.type().liftClass()).toList();
            FittedBoat boat = LiftAdmission.bestCarrier(committedBoats, load, taken);
            if (boat != null) {
                byId.put(VehicleSupport.ID,
                        VehicleSupport.configured(vehicles.deployments(), boat, true));
            }
        }
    }

    /**
     * Story battles whose employer arranges the heavy lift grant Armour Support whenever
     * the bay holds a vehicle, as they grant Mech Support for its mechs; elsewhere it
     * stays sourced by a committed Valkyrie.
     */
    private static List<CommandPower> withBayVehicles(List<CommandPower> powers, boolean arrangedLift,
                                                      List<VehicleDeploymentSpec> vehicles) {
        if (vehicles == null) return powers;
        List<CommandPower> out = new ArrayList<>(powers.size() + 1);
        boolean sourced = false;
        for (CommandPower power : powers) {
            if (power instanceof VehicleSupport) {
                sourced = true;
                if (!vehicles.isEmpty()) out.add(VehicleSupport.configured(vehicles));
            } else {
                out.add(power);
            }
        }
        if (!sourced && arrangedLift && !vehicles.isEmpty()) out.add(VehicleSupport.configured(vehicles));
        return List.copyOf(out);
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

        // Dev convenience — grant recon ping for free (see DevConfig). Off in prod.
        if (com.dillon.starsectormarines.DevConfig.ALWAYS_GRANT_RECON_PING) {
            byId.put(ReconPing.ID, new ReconPing());
        }
        if (com.dillon.starsectormarines.DevConfig.DEBUG_GRANT_MECH_SUPPORT) {
            byId.put(MechSupport.ID, new MechSupport());
        }
        if (com.dillon.starsectormarines.DevConfig.DEBUG_GRANT_EMERGENCY_RESUPPLY) {
            byId.put(EmergencyResupply.ID, new EmergencyResupply());
        }
        if (com.dillon.starsectormarines.DevConfig.DEBUG_GRANT_ORBITAL_BARRAGE) {
            byId.put(OrbitalBarrage.ID, new OrbitalBarrage());
        }
        if (com.dillon.starsectormarines.DevConfig.DEBUG_GRANT_MARINE_INSERTION) {
            byId.put(MarineInsertion.ID, new MarineInsertion());
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

    /** As above, plus the frozen company vehicles an Armour Support commitment carries. */
    public static CommandPower restore(String id,
                                       List<MechDeploymentSpec> mechDeployments,
                                       List<ShipSupportSource> ships,
                                       List<VehicleDeploymentSpec> vehicleDeployments) {
        return restore(id, mechDeployments, ships, vehicleDeployments, null);
    }

    /**
     * As above, plus the company boat a drop power was given to fly with
     * (synthetic lift stays behind it), or {@code null}.
     */
    public static CommandPower restore(String id,
                                       List<MechDeploymentSpec> mechDeployments,
                                       List<ShipSupportSource> ships,
                                       List<VehicleDeploymentSpec> vehicleDeployments,
                                       FittedBoat companyCarrier) {
        if (companyCarrier != null) {
            CommandPower base = restore(id, mechDeployments, ships, vehicleDeployments, null);
            if (base instanceof VehicleSupport vehicles) {
                return VehicleSupport.configured(vehicles.deployments(), companyCarrier, true);
            }
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
            if (!VehicleSupport.ID.equals(id)) {
                throw new IllegalArgumentException("Power '" + id + "' cannot carry vehicles");
            }
            return VehicleSupport.configured(vehicleDeployments);
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
        if (fitted.contains(HIRES_SENSORS_MOD) || fitted.contains(SURVEYING_EQUIPMENT_MOD)) {
            ids.add(ReconPing.ID);
        }
        if (fitted.contains(GROUND_SUPPORT_MOD) || fitted.contains(ADVANCED_GROUND_SUPPORT_MOD)) {
            ids.add(MechSupport.ID);
            ids.add(OrbitalBarrage.ID);
        }
        if (APOGEE_HULL.equals(baseId)) ids.add(ReconPing.ID);
        if (VALKYRIE_HULL.equals(baseId)) ids.add(MechSupport.ID);
        if (VALKYRIE_HULL.equals(baseId)) ids.add(MarineInsertion.ID);
        if (VALKYRIE_HULL.equals(baseId)) ids.add(VehicleSupport.ID);
        if (baseId != null && ShuttleType.MUDSKIPPER.matchingHullIds.contains(baseId)) {
            ids.add(CloseAirSupport.ID);
        }
        if (TARSUS_HULL.equals(baseId) || ATLAS_HULL.equals(baseId)) {
            ids.add(EmergencyResupply.ID);
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
        if (ReconPing.ID.equals(id)) return new ReconPing();
        if (MechSupport.ID.equals(id)) return new MechSupport();
        if (EmergencyResupply.ID.equals(id)) return new EmergencyResupply();
        if (OrbitalBarrage.ID.equals(id)) return new OrbitalBarrage();
        if (MarineInsertion.ID.equals(id)) return new MarineInsertion();
        if (VehicleSupport.ID.equals(id)) return new VehicleSupport();
        if (CloseAirSupport.ID.equals(id)) return new CloseAirSupport();
        if (SpawnInOnslaught.ID.equals(id)) return new SpawnInOnslaught();
        return null;
    }
}
