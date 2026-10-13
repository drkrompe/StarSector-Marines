package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The live adapter for {@link HostileShipSelection}: reads the defending faction's real fleets in
 * the target's star system and freezes each eligible hull into plain data at the launch boundary.
 * Nothing campaign-side survives in the result.
 *
 * <p><b>Present.</b> A fleet counts when it is in the target's containing location, belongs to the
 * defending faction, is not the player's, is not empty and is not hidden.
 *
 * <p><b>Free.</b> {@link HostileFleetStatus#classify}: not in a battle (with anyone, the player
 * included), not despawning or returning to despawn, not fleeing, not flagged busy, and not on an
 * assignment that takes it away (intercepting, following, raiding, delivering, resupplying, or
 * travelling or attacking somewhere other than the target world).
 *
 * <p><b>Eligible hull.</b> The company's rule, {@link PowerCatalog#shipSourceOf}: a cruiser or
 * capital with a ground-reaching gun, frozen with the member's own fitted variant. Fighter wings
 * and mothballed ships never count, and the frozen source carries no member id, so no battle
 * result is ever written back to an enemy fleet.
 */
public final class HostileShipFleets {

    private static final Logger LOG = Global.getLogger(HostileShipFleets.class);

    private HostileShipFleets() {}

    /** The fleets of {@code profile}'s owner in the system of the planet named {@code planetName}. */
    public static List<HostileShipSelection.Fleet> inSystemOf(String planetName, TargetProfile profile) {
        List<HostileShipSelection.Fleet> out = new ArrayList<>();
        if (planetName == null || profile == null || profile.factionId().isEmpty()
                || Global.getSector() == null) return out;
        SectorEntityToken target = targetEntity(planetName);
        if (target == null) return out;
        LocationAPI location = target.getContainingLocation();
        if (location == null) return out;
        for (CampaignFleetAPI fleet : new ArrayList<>(location.getFleets())) {
            try {
                HostileShipSelection.Fleet view = view(fleet, profile.factionId(), target);
                if (view != null) out.add(view);
            } catch (RuntimeException e) {
                LOG.warn("HostileShipFleets: fleet could not be read", e);
            }
        }
        return out;
    }

    private static SectorEntityToken targetEntity(String planetName) {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.getPrimaryEntity() == null) continue;
            if (planetName.equals(market.getPrimaryEntity().getName())) return market.getPrimaryEntity();
        }
        return null;
    }

    private static HostileShipSelection.Fleet view(CampaignFleetAPI fleet, String factionId, SectorEntityToken target) {
        if (fleet == null || fleet.isPlayerFleet() || fleet.isEmpty() || fleet.isHidden()) return null;
        if (fleet.getFaction() == null || !factionId.equals(fleet.getFaction().getId())) return null;
        List<HostileShipSelection.Candidate> ships = new ArrayList<>();
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            HostileShipSelection.Candidate c = candidate(member);
            if (c != null) ships.add(c);
        }
        if (ships.isEmpty()) return null;
        return new LiveFleet(fleet.getName(), status(fleet, target), ships);
    }

    private static HostileShipSelection.Candidate candidate(FleetMemberAPI member) {
        if (member == null || member.isFighterWing() || member.isMothballed()) return null;
        ShipSupportSource frozen = PowerCatalog.shipSourceOf(member);
        if (frozen == null || frozen.tender()) return null;
        // An enemy ship has no campaign member of ours to settle against.
        ShipSupportSource source = new ShipSupportSource(frozen.hullId(), frozen.shipName(), false, frozen.weapons());
        return new HostileShipSelection.Candidate(source, member.getHullSpec().getHullName(),
                member.getHullSpec().getHullSize(), member.getFleetPointCost());
    }

    private static HostileFleetStatus status(CampaignFleetAPI fleet, SectorEntityToken target) {
        CampaignFleetAIAPI ai = fleet.getAI();
        FleetAssignmentDataAPI data = ai != null ? ai.getCurrentAssignment() : null;
        FleetAssignment assignment = data != null ? data.getAssignment() : null;
        SectorEntityToken to = data != null ? data.getTarget() : null;
        boolean atTarget = to != null && target.getId() != null && target.getId().equals(to.getId());
        return HostileFleetStatus.classify(fleet.getBattle() != null, fleet.isDespawning(),
                ai != null && ai.isFleeing(),
                fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_BUSY),
                assignment, atTarget);
    }

    private record LiveFleet(String name, HostileFleetStatus status,
                             List<HostileShipSelection.Candidate> ships) implements HostileShipSelection.Fleet { }
}
