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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
            for (FleetMemberAPI ship : committedShips) {
                for (CommandPower power : contributedBy(ship)) {
                    byId.putIfAbsent(power.id, power);
                }
            }
        }
        for (String id : employerPowerIds(m)) {
            CommandPower p = forId(id);
            if (p != null) byId.putIfAbsent(p.id, p);
        }

        LOG.info("PowerCatalog: resolved " + byId.size() + " power(s) " + byId.keySet()
                + " from " + (committedShips == null ? 0 : committedShips.size()) + " committed ship(s)");
        return new ArrayList<>(byId.values());
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
        return null;
    }
}
