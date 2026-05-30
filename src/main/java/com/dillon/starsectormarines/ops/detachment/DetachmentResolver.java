package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.ops.Mission;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns the player's committed detachment (plus the mission's employer
 * offerings) into a {@link Detachment} — the single resolver both pre-battle
 * entry points ({@code BriefingScreen}, {@code CommsConsolePanel}) route
 * through. Centralizes the shuttle-manifest / employer-cap / heavy-armaments
 * logic the two screens used to duplicate verbatim.
 *
 * <p>Stateless statics. Reads campaign state ({@code FleetMemberAPI}, the
 * economy) here so the battle tier never has to.
 */
public final class DetachmentResolver {

    /**
     * Physical employer Aeroshuttles a mission fields — the employer's drops are
     * distributed across at most this many cycling ships so the wave reads as
     * recurring activity rather than a one-shot deluge.
     */
    private static final int EMPLOYER_PHYSICAL_CAP = 3;

    private DetachmentResolver() {}

    /**
     * Resolve the detachment for a battle.
     *
     * @param m                 the mission (employer support co-source)
     * @param committedShuttles the player's committed transports, priority-sorted
     * @param committedWings    the player's committed marine-side fighter cover
     *                          (Slice 1: the whole fleet's fitted bays)
     */
    public static Detachment resolve(Mission m,
                                     List<ShuttleType> committedShuttles,
                                     FlybyRoster committedWings) {
        List<ShuttleAssignment> manifest = buildShuttleManifest(m, committedShuttles);
        FlybyRoster marineWings = FlybyRoster.combine(m.clientFighterSupport, committedWings);
        List<CommandPower> powers = PowerCatalog.resolve(committedShips(), m);
        return new Detachment(manifest, marineWings, powers);
    }

    /**
     * Ships that source command powers. Slice 1 = the whole player fleet (same
     * scan set the wing/shuttle resolvers use), preserving today's "your fleet is
     * your spellbook" reach; Slice 2 narrows this to the committed subset.
     */
    private static List<FleetMemberAPI> committedShips() {
        if (Global.getSector() == null) return Collections.emptyList();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return Collections.emptyList();
        return new ArrayList<>(fleet.getFleetData().getMembersListCopy());
    }

    // ---- shuttle manifest (moved verbatim from the briefing screens) ----

    /**
     * Number of physical employer Aeroshuttles for a mission — the employer still
     * delivers {@code m.employerShuttles} drops total, spread across at most
     * {@link #EMPLOYER_PHYSICAL_CAP} cycling ships. Returns 0 when the employer
     * contributes nothing.
     */
    public static int employerPhysicalShipCount(Mission m) {
        if (m.employerShuttles <= 0) return 0;
        return Math.min(m.employerShuttles, EMPLOYER_PHYSICAL_CAP);
    }

    /**
     * Builds the full {@link ShuttleAssignment} list — what ships fly and how many
     * sorties each performs. Employer slots come first as cycling Aeroshuttles;
     * the player's committed transports cover the remaining drops via cycling.
     *
     * <p>Distribution: with {@code N} ships covering {@code D} drops, the first
     * {@code (D mod N)} ships get {@code ceil(D/N)} cycles and the rest
     * {@code floor(D/N)}. Player ships are priority-sorted, so a Valkyrie works
     * harder than a Mudskipper when the math is uneven.
     *
     * <p>When the player commits zero transports and the employer doesn't cover
     * all the drops, the manifest only covers the employer's portion — the
     * briefing gate blocks before that's an issue, but the function doesn't assume
     * gate enforcement.
     */
    public static List<ShuttleAssignment> buildShuttleManifest(Mission m, List<ShuttleType> playerShuttles) {
        List<ShuttleAssignment> out = new ArrayList<>();
        int employerPhysical = employerPhysicalShipCount(m);
        if (employerPhysical > 0) {
            int employerDrops = m.employerShuttles;
            int eBase = employerDrops / employerPhysical;
            int eExtra = employerDrops % employerPhysical;
            // Dev toggle — swap employer Aeroshuttles for Valkyries so the full
            // A2G turret kit flies without needing a player Valkyrie. See DevConfig.
            ShuttleType employerType = DevConfig.FORCE_EMPLOYER_VALKYRIE
                    ? ShuttleType.VALKYRIE : ShuttleType.AEROSHUTTLE;
            for (int i = 0; i < employerPhysical; i++) {
                int cycles = eBase + (i < eExtra ? 1 : 0);
                out.add(new ShuttleAssignment(employerType, cycles));
            }
        }
        int playerDrops = Math.max(0, m.requiredDrops - m.employerShuttles);
        if (playerDrops == 0) return out;
        int transportsUsed = Math.min(playerDrops, playerShuttles.size());
        if (transportsUsed == 0) {
            // Gated normally, but if somehow we get here pad with employer-style
            // aeroshuttles so the battle still functions.
            for (int i = 0; i < playerDrops; i++) {
                out.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
            }
            return out;
        }
        int baseCycles = playerDrops / transportsUsed;
        int extraCycles = playerDrops % transportsUsed;
        for (int i = 0; i < transportsUsed; i++) {
            int cycles = baseCycles + (i < extraCycles ? 1 : 0);
            out.add(new ShuttleAssignment(playerShuttles.get(i), cycles));
        }
        return out;
    }

    // ---- enemy heavy-armor gate (moved verbatim from the briefing screens) ----

    /**
     * True when the target planet's market hosts at least one industry that
     * produces or demands heavy armaments (Heavy Industry / Orbital Works /
     * Ground Defenses / Heavy Batteries). Drives the defender's mech slot in
     * {@code BattleSetup} — a planet with no organic source of heavy armaments
     * shouldn't field mechs. Returns false for story / non-industry ops (no target
     * planet) or when no market matches the name.
     */
    public static boolean planetHasHeavyArmaments(String targetPlanetName) {
        if (targetPlanetName == null) return false;
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.getPrimaryEntity() == null) continue;
            if (!targetPlanetName.equals(market.getPrimaryEntity().getName())) continue;
            return market.hasIndustry(Industries.HEAVYINDUSTRY)
                    || market.hasIndustry(Industries.ORBITALWORKS)
                    || market.hasIndustry(Industries.GROUNDDEFENSES)
                    || market.hasIndustry(Industries.HEAVYBATTERIES);
        }
        return false;
    }
}
