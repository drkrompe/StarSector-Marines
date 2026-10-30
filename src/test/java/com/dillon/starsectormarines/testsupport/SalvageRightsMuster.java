package com.dillon.starsectormarines.testsupport;

import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.fixture.FiniteCommandPowerResources;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.vehicle.VehicleType;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.DebugMissionCatalog;
import com.dillon.starsectormarines.ops.SalvageRightsCompany;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Salvage Rights with the company the debug briefing lands ({@link SalvageRightsCompany}): its squads in the debug
 * preset's boats and, when asked, its bay behind Mech Support for {@link #callMachine} to call down. Shared by
 * {@code battleProbe}'s {@code salvage-rights} source and the story command evidence.
 */
public final class SalvageRightsMuster {
    private SalvageRightsMuster() {}

    /** Supplies the company brings; enough for every machine. */
    static final int SUPPLIES = 30;

    /** The battle at {@code seed} with {@code squads} of the company landing, and its bay when {@code machines}. */
    public static BattleSimulation build(long seed, int squads, boolean machines) {
        var mission = ShepherdRecoveryCampaign.offer(new ShepherdRecoveryProgress(), "Asharu");
        MarineRoster roster = DebugMissionCatalog.expectedCompany(DebugMissionCatalog.Scenario.SALVAGE_RIGHTS.mission(), squads);
        if (machines) board(roster);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, squads * 12);
        var preset = DebugMissionCatalog.preset(DebugMissionCatalog.Scenario.SALVAGE_RIGHTS.mission());
        var fitted = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, fitted, deployment.size());
        BattleSimulation sim = BattleSetup.createSalvageRights(seed, manifest,
                new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD));
        deployment.applyToAuthoredPlayerLift(sim);
        List<CommandPower> powers = machines ? PowerCatalog.resolve(List.of(), mission, roster.mechBay().activeDeployment(),
                roster.mechBay().activeVehicleDeployment(), fitted) : List.of();
        BattleLaunchOverlay.capture(0, CampaignMarineDeployment.EMPTY, FlybyRoster.EMPTY, FlybyRoster.EMPTY, powers,
                SUPPLIES, mission.fieldPresencePolicy).applyTo(sim, new FiniteCommandPowerResources(SUPPLIES));
        return sim;
    }

    /** The recovered Hound, then the company's other machines, into the active lance. */
    private static void board(MarineRoster roster) {
        if (!roster.mechBay().receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment())))
            throw new IllegalStateException("the Hound did not board");
        String lance = roster.mechBay().activeSquad().id();
        for (MechVariant variant : SalvageRightsCompany.MECHS)
            if (roster.mechBay().fabricateChassis(lance, variant) == null)
                throw new IllegalStateException(variant + " did not board");
        for (int i = 0; i < SalvageRightsCompany.VEHICLES.size(); i++) {
            VehicleType type = SalvageRightsCompany.VEHICLES.get(i);
            if (!roster.mechBay().addVehicle(lance, new CampaignVehicle("salvage_vehicle_" + i, type.name(), type)))
                throw new IllegalStateException(type + " did not board");
        }
    }

    /**
     * Calls the machine at manifest index {@code pick} down once the drop is ready, on the first landing pad from the
     * pick's own that the drop can reach. Returns true on the tick it is requested.
     */
    public static boolean callMachine(BattleSimulation sim, int pick) {
        var service = sim.getCommandPowerService();
        var support = service.getPower(MechSupport.ID);
        if (support == null || !service.canActivate(support)) return false;
        List<Integer> selection = List.of(pick);
        var pads = sim.getMarineLandingPads();
        for (int i = 0; i < pads.size(); i++) {
            var pad = pads.get((pick + i) % pads.size());
            if (!support.canTarget(pad.centerX, pad.centerY, sim, selection)) continue;
            service.requestActivation(MechSupport.ID, pad.centerX, pad.centerY, selection);
            return true;
        }
        return false;
    }
}
