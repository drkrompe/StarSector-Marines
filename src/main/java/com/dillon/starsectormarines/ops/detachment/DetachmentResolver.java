package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.Mission;
import com.dillon.starsectormarines.ops.SupplyRouteCampaign;
import com.dillon.starsectormarines.ops.CorvusWaylineCampaign;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.dillon.starsectormarines.ops.MissionSource;
import com.dillon.starsectormarines.ops.MissionType;
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
     */
    public static Detachment resolve(Mission m,
                                     List<FittedBoat> committedShuttles,
                                     FlybyRoster committedWings) {
        return resolve(m, committedShuttles, committedWings, committedShips());
    }

    /** Resolve using the exact fleet members committed as command-power sources. */
    public static Detachment resolve(Mission m,
                                     List<FittedBoat> committedShuttles,
                                     FlybyRoster committedWings,
                                     List<FleetMemberAPI> committedPowerSources) {
        List<ShuttleAssignment> manifest = buildShuttleManifest(m, committedShuttles);
        FlybyRoster marineWings = SupplyRouteCampaign.isBattle(m) || CorvusWaylineCampaign.isBattle(m) || ShepherdRecoveryCampaign.isBattle(m) ? FlybyRoster.EMPTY
                : FlybyRoster.combine(m.clientFighterSupport, committedWings);
        List<CommandPower> powers = PowerCatalog.resolve(
                committedPowerSources, m, campaignMechDeployment());
        return new Detachment(manifest, marineWings, powers);
    }

    /** Resolves local stationing resources without consulting the player's fleet. */
    public static Detachment resolveStationed(Mission m) {
        List<ShuttleAssignment> manifest = buildShuttleManifest(m, Collections.emptyList());
        List<CommandPower> powers = PowerCatalog.resolve(Collections.emptyList(), m);
        return new Detachment(manifest, m.clientFighterSupport, powers);
    }

    /**
     * Whole-fleet compatibility fallback for callers that do not expose the
     * canonical briefing's member-level commitment surface.
     */
    private static List<FleetMemberAPI> committedShips() {
        if (Global.getSector() == null) return Collections.emptyList();
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData() == null) return Collections.emptyList();
        return new ArrayList<>(fleet.getFleetData().getMembersListCopy());
    }

    private static List<MechDeploymentSpec> campaignMechDeployment() {
        if (Global.getSector() == null) return List.of();
        MarineRosterScript script = MarineRosterScript.getInstance();
        return script != null ? script.roster().mechBay().activeDeployment() : List.of();
    }

    // ---- shuttle manifest (moved verbatim from the briefing screens) ----

    /**
     * Number of physical employer Aeroshuttles for a mission — the employer still
     * delivers {@code m.employerShuttles} drops total, spread across at most
     * {@link #EMPLOYER_PHYSICAL_CAP} cycling ships. Returns 0 when the employer
     * contributes nothing or when a debug briefing owns the complete manifest.
     */
    public static int employerPhysicalShipCount(Mission m) {
        if (m.source.isDebug() || m.employerShuttles <= 0) return 0;
        return Math.min(m.employerShuttles, EMPLOYER_PHYSICAL_CAP);
    }

    /** Player boats can cycle; employer support must cover every drop if flying alone. */
    public static boolean hasRequiredTransport(Mission mission, List<FittedBoat> boats) {
        if (mission.source == MissionSource.STATIONING) return true;
        if (boats != null && !boats.isEmpty()) return true;
        if (mission.source.isDebug() || MissionForceEnvelope.allowsUnderstrength(mission)) return false;
        return mission.employerShuttles > 0 && mission.employerShuttles >= mission.requiredDrops;
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
     * <p>Debug missions are the exception to employer co-sourcing: their briefing
     * picker owns the complete manifest, so the generated employer roll is ignored
     * and the selected type/count covers all required drops.
     *
     * <p>Missing player lift leaves an incomplete manifest; briefing and launch
     * reject it. Only stationed detachments use authored local replacement lift.
     * Employer and local craft stay plain patterns because the company does not
     * own or fit them.
     */
    public static List<ShuttleAssignment> buildShuttleManifest(Mission m, List<FittedBoat> playerShuttles) {
        List<ShuttleAssignment> out = new ArrayList<>();
        int employerPhysical = employerPhysicalShipCount(m);
        if (employerPhysical > 0) {
            int employerDrops = m.employerShuttles;
            int eBase = employerDrops / employerPhysical;
            int eExtra = employerDrops % employerPhysical;
            // Employer flies unarmed Aeroshuttles (the "token force" flavor — the
            // player supplies heavy lift). Debug missions bypass this branch and
            // source their complete roster from the briefing picker instead.
            for (int i = 0; i < employerPhysical; i++) {
                int cycles = eBase + (i < eExtra ? 1 : 0);
                ShuttleType type = ShuttleType.AEROSHUTTLE;
                out.add(new ShuttleAssignment(type, cycles,
                        m.marineArrivalPolicy.seatsPerSortie(type)));
            }
        }
        // Debug briefings own the complete manifest through their type/count
        // picker. Ignore the generated employer roll so the configured number
        // is the exact set of physical transports that appears in battle.
        int employerDrops = m.source.isDebug() ? 0 : m.employerShuttles;
        int playerDrops = Math.max(0, m.requiredDrops - employerDrops);
        if (playerDrops == 0) return out;
        int transportsUsed = Math.min(playerDrops, playerShuttles == null ? 0 : playerShuttles.size());
        if (transportsUsed == 0) {
            if (m.source != MissionSource.STATIONING) return out;
            // A stationed detachment uses local lift, not the player's fleet.
            for (int i = 0; i < playerDrops; i++) {
                ShuttleType type = ShuttleType.AEROSHUTTLE;
                out.add(new ShuttleAssignment(type, 1,
                        m.marineArrivalPolicy.seatsPerSortie(type)));
            }
            return out;
        }
        int baseCycles = playerDrops / transportsUsed;
        int extraCycles = playerDrops % transportsUsed;
        for (int i = 0; i < transportsUsed; i++) {
            int cycles = baseCycles + (i < extraCycles ? 1 : 0);
            // The company's own boat, fit and all. Seats and cycles are still
            // facts about the pattern — a yard's plating does not add a seat.
            FittedBoat boat = playerShuttles.get(i);
            out.add(new ShuttleAssignment(boat, cycles,
                    m.marineArrivalPolicy.seatsPerSortie(boat.pattern())));
        }
        return out;
    }

    /** Company-selected personnel own lift demand; authored minimums remain launch gates. */
    public static boolean usesSelectedPersonnel(Mission mission) {
        return mission.source != MissionSource.STATIONING
                && mission.type != MissionType.CONQUEST;
    }

    /**
     * Keeps employer personnel unchanged while sizing the player's real craft to
     * the exact named force selected for the operation, including fixed story
     * and event missions. A required drop count is not a cap on squad membership.
     * Stationing and the Conquest arrival plan own their separate commitments.
     */
    public static List<ShuttleAssignment> buildShuttleManifestForPersonnel(
            Mission m, List<FittedBoat> playerShuttles, int playerPersonnel) {
        if (!usesSelectedPersonnel(m)) {
            return buildShuttleManifest(m, playerShuttles);
        }
        if ((SupplyRouteCampaign.isBattle(m) || CorvusWaylineCampaign.isBattle(m) || ShepherdRecoveryCampaign.isBattle(m)) && playerShuttles != null && playerShuttles.size() > 4)
            playerShuttles = playerShuttles.subList(0, 4);
        List<ShuttleAssignment> authored = buildShuttleManifest(m, playerShuttles);
        int employerPhysical = employerPhysicalShipCount(m);
        List<ShuttleAssignment> out = new ArrayList<>();
        for (int i = 0; i < Math.min(employerPhysical, authored.size()); i++) {
            out.add(authored.get(i));
        }

        int remaining = Math.max(0, playerPersonnel);
        if (remaining == 0 || playerShuttles == null || playerShuttles.isEmpty()) {
            return out;
        }
        for (int i = 0; i < playerShuttles.size() && remaining > 0; i++) {
            FittedBoat boat = playerShuttles.get(i);
            int seatsPerSortie = m.marineArrivalPolicy.seatsPerSortie(boat.pattern());
            int passengers = i == playerShuttles.size() - 1
                    ? remaining : Math.min(remaining, seatsPerSortie);
            int cycles = (passengers + seatsPerSortie - 1) / seatsPerSortie;
            out.add(new ShuttleAssignment(boat.pattern(), boat, cycles,
                    seatsPerSortie, passengers));
            remaining -= passengers;
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
