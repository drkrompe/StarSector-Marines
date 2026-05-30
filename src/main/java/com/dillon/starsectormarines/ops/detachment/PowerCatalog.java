package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.ReconPing;
import com.dillon.starsectormarines.ops.Mission;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
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
    /** Surveying Equipment — the planetary-investigation tool ({@code ids.HullMods.SURVEYING_EQUIPMENT}). */
    private static final String SURVEYING_EQUIPMENT_MOD = "surveying_equipment";
    /** Apogee — built-in survey/sensor suite (the canonical scanner cruiser). Base hull id. */
    private static final String APOGEE_HULL = "apogee";

    private PowerCatalog() {}

    /**
     * Resolve the command-power roster for a battle from the committed ships plus
     * the mission's employer offerings, deduped by power id.
     *
     * <p>Slice 1 unconditionally seeds a baseline {@link ReconPing} so the demo
     * loop survives a fleet with no recon-capable ship (the power UI hides itself
     * on an empty roster); Slice 2 gates that baseline behind {@code DevConfig}
     * once real sourcing takes over. The ship/employer mapping below is already
     * live so dropping the baseline is a one-line change.
     */
    public static List<CommandPower> resolve(List<FleetMemberAPI> committedShips, Mission m) {
        Map<String, CommandPower> byId = new LinkedHashMap<>();

        // Slice 1 baseline — always grant recon ping (keeps the power loop demoable).
        byId.put(ReconPing.ID, new ReconPing());

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
        }
        String baseId = ship.getHullSpec() != null ? ship.getHullSpec().getBaseHullId() : null;
        if (APOGEE_HULL.equals(baseId)) byId.putIfAbsent(ReconPing.ID, new ReconPing());
    }

    /** The employer's offered power ids for this mission (the contract co-source). */
    private static List<String> employerPowerIds(Mission m) {
        return m != null && m.employerPowerIds != null ? m.employerPowerIds : List.of();
    }

    /** Maps a stable power id to a fresh power instance. Returns null for unknown ids. */
    private static CommandPower forId(String id) {
        if (ReconPing.ID.equals(id)) return new ReconPing();
        return null;
    }
}
