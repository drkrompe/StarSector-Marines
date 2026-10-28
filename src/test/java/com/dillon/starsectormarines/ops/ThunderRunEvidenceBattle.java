package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.LiftAdmission;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.power.BayManifest;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.marine.BoatFitting;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.testsupport.InstalledWeaponSlots;
import org.json.JSONObject;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One Thunder Run, stood up the way the evidence harness stands it up: a Broken Supply Route at the Thunder Run
 * offer, a named company frozen from a roster, two fitted Valkyries, and the production supply-route launch.
 *
 * <p>Shared by {@code ThunderRunEvidenceTest} and the battle probe, so a battle questioned in the probe is the battle
 * the evidence reports on. Fixture fields read here: {@code seed}, {@code companySize} (default 24), {@code boats=hermes} (the company's one Hermes in place of the two Valkyries), {@code mech=true} (a Hound in the bay, armed as the campaign arms it) and
 * {@code retry} (a prior attempt that delivered load 0 and lost load 1). Diagnostic fixture fields the evidence test
 * applies after construction (absent arrival, cleared flak, destroyed cargo) are not construction and stay there.
 */
public final class ThunderRunEvidenceBattle {

    /** The staged battle, before its first tick, with the campaign objects it settles into. */
    public record Staged(BattleSimulation sim, BrokenSupplyRouteProgress route, Mission mission,
                         List<ShuttleAssignment> manifest, CampaignMarineDeployment deployment,
                         int companySize) {
    }

    private ThunderRunEvidenceBattle() {
    }

    /** Builds the battle. The player lift is not yet applied and the route attempt not yet begun; see {@link #launch}. */
    public static Staged build(JSONObject fixture) throws Exception {
        InstalledWeaponSlots.install();
        var route = new BrokenSupplyRouteProgress("jangala", "Jangala", "Corvus");
        String id = route.missionId();
        route.begin(id);
        route.settle(id, true, null);
        if (fixture.optBoolean("retry")) {
            id = route.missionId();
            route.begin(id);
            route.settle(id, false, null, new FreightRunProgress.Outcome(route.freight().previewAttempt(),
                    List.of(0), List.of(1), false));
        }
        Mission mission = SupplyRouteCampaign.offers(route).get(0);
        int companySize = fixture.optInt("companySize", 24);
        var roster = new MarineRoster();
        roster.ensureActiveSoldiers(companySize);
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, companySize);
        var boats = fixture.optString("boats").equals("hermes")
                ? List.of(new FittedBoat(ShuttleType.HERMES, BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "freight-hermes"))
                : List.of(
                new FittedBoat(ShuttleType.VALKYRIE, BoatFitting.STANDARD_PLATING, BoatFitting.STANDARD_DRIVE, "freight-alpha"),
                new FittedBoat(ShuttleType.VALKYRIE, BoatFitting.requireById("reinforced_plating"),
                        BoatFitting.requireById("tuned_drive"), "freight-bravo"));
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, boats, companySize);
        var profile = new TargetProfile(0, 0, 0, 0, "", Set.of(), SurfacePalette.VERDANT, SettlementLink.ROAD);
        BattleSimulation sim = MissionLaunch.supplyRouteBuilder(route, fixture.getLong("seed"), manifest, profile).get();
        if (fixture.optBoolean("mech")) {
            // As PowerCatalog arms it here: on the company boat that lifts the bay, with no employer lift behind her.
            MechSupport bay = MechSupport.configured(List.of(MechDeploymentSpec.standard(MechVariant.HOUND)));
            FittedBoat carrier = LiftAdmission.bestCarrier(boats,
                    bay.manifest().entries().stream().map(BayManifest.Entry::weightClass).toList(), Set.of());
            if (carrier != null)
                sim.setCommandPowers(List.of(MechSupport.configured(bay.deployments(), bay.vehicles(), carrier, false)));
        }
        return new Staged(sim, route, mission, manifest, deployment, companySize);
    }

    /** Puts the frozen company aboard its lift and begins the route attempt; true when the attempt began. */
    public static boolean launch(Staged staged) {
        staged.deployment().applyToAuthoredPlayerLift(staged.sim());
        return staged.route().begin(staged.mission().id);
    }

    /** Builds and launches: the battle exactly as the evidence harness plays it from its first tick. */
    public static BattleSimulation launched(JSONObject fixture) throws Exception {
        Staged staged = build(fixture);
        if (!launch(staged)) throw new IllegalStateException("Thunder Run attempt did not begin");
        return staged.sim();
    }
}
