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
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.ShepherdRecoveryProgress;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.CampaignVehicle;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.DebugMissionCatalog;
import com.dillon.starsectormarines.ops.WorkingShipCompany;
import com.dillon.starsectormarines.ops.ShepherdRecoveryCampaign;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An A Working Ship visit with the company its debug briefing lands: its squads in the debug preset's boats (ashore
 * where the visit starts the company on the ground) and, when asked, its bay behind Mech Support for
 * {@link #callMachine} to call down. Shared by {@code battleProbe}'s basin sources, the story command evidence and
 * the basin overview frames.
 */
public final class WorkingShipMuster {
    private WorkingShipMuster() {}

    /** Supplies the company brings; enough for every machine. */
    static final int SUPPLIES = 30;

    /** {@code visit}'s battle at {@code seed} with {@code squads} of the company, and its bay when {@code machines}. */
    public static BattleSimulation build(ShepherdBasin.Visit visit, long seed, int squads, boolean machines) {
        var scenario = switch (visit) {
            case SALVAGE_RIGHTS -> DebugMissionCatalog.Scenario.SALVAGE_RIGHTS;
            case COLD_START -> DebugMissionCatalog.Scenario.COLD_START;
            case WORKING_PASSAGE -> DebugMissionCatalog.Scenario.WORKING_PASSAGE;
        };
        var mission = ShepherdRecoveryCampaign.offer(progress(visit), "Asharu");
        MarineRoster roster = DebugMissionCatalog.expectedCompany(scenario.mission(), squads);
        if (machines) board(roster);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, squads * 12);
        var preset = DebugMissionCatalog.preset(scenario.mission());
        var fitted = Collections.nCopies(preset.boats(), DebugMissionCatalog.maxFitted(preset.boat()));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, fitted, deployment.size());
        var profile = new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ARID, SettlementLink.ROAD);
        BattleSimulation sim = switch (visit) {
            case SALVAGE_RIGHTS -> BattleSetup.createSalvageRights(seed, manifest, profile);
            case COLD_START -> BattleSetup.createColdStart(seed, manifest, profile, false, false);
            case WORKING_PASSAGE -> BattleSetup.createWorkingPassage(seed, manifest, profile);
        };
        deployment.applyToAuthoredPlayerLift(sim);
        List<CommandPower> powers = machines ? PowerCatalog.resolve(List.of(), mission, roster.mechBay().activeDeployment(),
                roster.mechBay().activeVehicleDeployment(), fitted) : List.of();
        BattleLaunchOverlay.capture(0, CampaignMarineDeployment.EMPTY, FlybyRoster.EMPTY, FlybyRoster.EMPTY, powers,
                SUPPLIES, mission.fieldPresencePolicy).applyTo(sim, new FiniteCommandPowerResources(SUPPLIES));
        return sim;
    }

    /** Campaign progress standing at {@code visit}: the claim secured for Cold Start, the hull repaired for the passage. */
    private static ShepherdRecoveryProgress progress(ShepherdBasin.Visit visit) {
        var progress = new ShepherdRecoveryProgress();
        if (visit == ShepherdBasin.Visit.SALVAGE_RIGHTS) return progress;
        String salvage = progress.missionId();
        if (!progress.begin(salvage, "Asharu") || progress.settle(salvage, "Asharu", true, ShepherdRecoveryProgress.PAYMENT, 0, true) < 0)
            throw new IllegalStateException("Salvage Rights did not settle");
        if (visit == ShepherdBasin.Visit.COLD_START) return progress;
        String cold = progress.missionId();
        if (!progress.begin(cold, "Asharu")
                || progress.settleColdStart(cold, "Asharu", true, ShepherdRecoveryProgress.COLD_PAYMENT, 0, true, true, true, true) < 0)
            throw new IllegalStateException("Cold Start did not settle");
        return progress;
    }

    /** The recovered Hound, then the company's other machines, into the active lance. */
    private static void board(MarineRoster roster) {
        if (!roster.mechBay().receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID, WaylineHound.deployment())))
            throw new IllegalStateException("the Hound did not board");
        String lance = roster.mechBay().activeSquad().id();
        for (MechVariant variant : WorkingShipCompany.MECHS)
            if (roster.mechBay().fabricateChassis(lance, variant) == null)
                throw new IllegalStateException(variant + " did not board");
        for (int i = 0; i < WorkingShipCompany.VEHICLES.size(); i++) {
            VehicleType type = WorkingShipCompany.VEHICLES.get(i);
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
