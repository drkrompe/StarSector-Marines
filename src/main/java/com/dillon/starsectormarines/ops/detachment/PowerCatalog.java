package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.power.EmergencyResupply;
import com.dillon.starsectormarines.battle.power.OrbitalBarrage;
import com.dillon.starsectormarines.battle.power.MarineInsertion;
import com.dillon.starsectormarines.ops.Mission;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

        if (committedShips != null) {
            for (FleetMemberAPI ship : committedShips) contribute(ship, byId);
        }
        for (String id : employerPowerIds(m)) {
            CommandPower p = forId(id);
            if (p != null) byId.putIfAbsent(p.id, p);
        }

        LOG.info("PowerCatalog: resolved " + byId.size() + " power(s) " + byId.keySet()
                + " from " + (committedShips == null ? 0 : committedShips.size()) + " committed ship(s)");
        return new ArrayList<>(byId.values());
    }

    /** Adds the powers a single committed ship contributes (by hull mod or base hull). */
    private static void contribute(FleetMemberAPI ship, Map<String, CommandPower> byId) {
        if (ship == null) return;
        ShipVariantAPI v = ship.getVariant();
        if (v != null) {
            Collection<String> mods = v.getHullMods();
            if (mods != null && (mods.contains(HIRES_SENSORS_MOD) || mods.contains(SURVEYING_EQUIPMENT_MOD))) {
                byId.putIfAbsent(ReconPing.ID, new ReconPing());
            }
            if (mods != null && (mods.contains(GROUND_SUPPORT_MOD)
                    || mods.contains(ADVANCED_GROUND_SUPPORT_MOD))) {
                byId.putIfAbsent(MechSupport.ID, new MechSupport());
                byId.putIfAbsent(OrbitalBarrage.ID, new OrbitalBarrage());
            }
        }
        String baseId = ship.getHullSpec() != null ? ship.getHullSpec().getBaseHullId() : null;
        if (APOGEE_HULL.equals(baseId)) byId.putIfAbsent(ReconPing.ID, new ReconPing());
        if (VALKYRIE_HULL.equals(baseId)) byId.putIfAbsent(MechSupport.ID, new MechSupport());
        if (VALKYRIE_HULL.equals(baseId)) byId.putIfAbsent(MarineInsertion.ID, new MarineInsertion());
        if (TARSUS_HULL.equals(baseId) || ATLAS_HULL.equals(baseId)) {
            byId.putIfAbsent(EmergencyResupply.ID, new EmergencyResupply());
        }
        if (ONSLAUGHT_HULL.equals(baseId) || INVICTUS_HULL.equals(baseId)) {
            byId.putIfAbsent(OrbitalBarrage.ID, new OrbitalBarrage());
        }
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
        return null;
    }
}
