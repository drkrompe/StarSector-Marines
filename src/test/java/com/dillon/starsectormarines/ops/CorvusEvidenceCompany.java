package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.fixture.FiniteCommandPowerResources;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.mech.MechDeploymentSpec;
import com.dillon.starsectormarines.battle.mech.MechWeaponComponent;
import com.dillon.starsectormarines.battle.mech.WaylineHound;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.power.MechSupport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.campaign.CorvusWaylineProgress;
import com.dillon.starsectormarines.marine.CampaignMech;
import com.dillon.starsectormarines.marine.LoadoutCard;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;
import com.dillon.starsectormarines.marine.SquadEquipmentResult;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * The company a Corvus chapter is played with outside the campaign: the evidence harness and the look suites stand it
 * up the same way. The chapter's squads in the named kit with field fatigues, each with an acting NCO and no officer
 * buffs; the recovered Hound in the bay; aeroshuttles; thirty supplies for the Hound's drop. Test support only.
 */
final class CorvusEvidenceCompany {

    /** Campaign marks the chapters read at their fixture boundary, comma separated; none by default. */
    static final String MARKS_PROPERTY = "battle.corvus.marks";
    /** The squads' weapon card, a {@link SquadEquipmentDoctrines} id; Field Security by default. */
    static final String SQUAD_WEAPONS_PROPERTY = "battle.corvus.squadWeapons";
    /** The Hound's left-shoulder pod, a {@link MechWeaponComponent} name; none by default, as recovered. */
    static final String HOUND_SHOULDER_PROPERTY = "battle.corvus.houndShoulder";
    /** The armor-drop pick that names the recovered Hound, the one machine in the bay. */
    static final List<Integer> HOUND_PICK = List.of(0);
    /** Supplies the company brings for its command powers. */
    static final int SUPPLIES = 30;

    /**
     * What the company carries.
     *
     * @param squadWeapons the weapon card every squad is issued
     * @param houndPod     the Hound's left-shoulder pod, or {@code null} for none
     */
    record Kit(String squadWeapons, MechWeaponComponent houndPod) {
        /** The first contract's kit: Field Security rifles and the Hound as recovered. */
        static final Kit RECOVERED = new Kit(SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, null);
        /** What a prepared company brings to the Corvus account: the Convoy Escort Issue and the Hound's SRMs. */
        static final Kit PREPARED = new Kit(SquadEquipmentDoctrines.CONVOY_ESCORT_WEAPONS, MechWeaponComponent.SRM_5);

        /** The kit the system properties name, {@link #RECOVERED} where they name nothing. */
        static Kit fromSystemProperties() {
            String pod = System.getProperty(HOUND_SHOULDER_PROPERTY, "");
            return new Kit(System.getProperty(SQUAD_WEAPONS_PROPERTY, RECOVERED.squadWeapons()),
                    pod.isBlank() ? null : MechWeaponComponent.valueOf(pod));
        }
    }

    /** The company stood up for one chapter: the fixture to build and the launch overlay to apply to it. */
    record Muster(BattleFixture fixture, CampaignMarineDeployment deployment, List<CommandPower> powers,
                  FiniteCommandPowerResources resources, BattleLaunchOverlay overlay) {
        /** Builds the battle and lands the company's seats and powers on it. */
        BattleSimulation build() {
            BattleSimulation sim = fixture.build();
            overlay.applyTo(sim, resources);
            return sim;
        }
    }

    private CorvusEvidenceCompany() { }

    /** The marks {@link #MARKS_PROPERTY} names. */
    static Set<String> marksFromSystemProperties() {
        Set<String> marks = new HashSet<>();
        for (String mark : System.getProperty(MARKS_PROPERTY, "").split(","))
            if (!mark.isBlank()) marks.add(mark.trim());
        return marks;
    }

    /** {@code chapter}'s mission as the closed route offers it once every chapter before it has settled. */
    static Mission missionFor(CorvusChapter chapter) {
        var progress = new CorvusWaylineProgress();
        var route = CorvusWaylineCampaignTest.closedRoute(true);
        for (CorvusChapter earlier : CorvusChapter.values()) {
            Mission mission = CorvusWaylineCampaign.offer(progress, earlier.planetName, route);
            if (earlier == chapter) return mission;
            if (!progress.begin(mission.id, earlier.planetName))
                throw new IllegalStateException("could not begin " + earlier);
            progress.settle(mission.id, earlier.planetName, true, earlier.payment);
        }
        throw new IllegalArgumentException(chapter.name());
    }

    /** Stands the company up for {@code mission} in {@code kit}, on {@code boats} aeroshuttles. */
    static Muster muster(CorvusChapter chapter, Mission mission, int boats, Predicate<String> campaignHas, Kit kit)
            throws Exception {
        var roster = new MarineRoster();
        roster.ensureActiveSoldiers(chapter.squads * 12);
        if (!roster.mechBay().receiveRecoveredMech(CampaignMech.fromDeployment(WaylineHound.ID,
                houndDeployment(kit.houndPod()))))
            throw new IllegalStateException("the Hound did not board");
        // A company buys its later cards; the evidence company is simply handed the one it is issued.
        roster.armory().learnLoadoutCard(new LoadoutCard(LoadoutCard.Kind.WEAPON, kit.squadWeapons()));
        var selected = roster.squads().stream().filter(s -> !s.reserve()).map(s -> s.id()).collect(Collectors.toSet());
        for (String squad : selected) {
            var result = roster.applySquadEquipment(squad, kit.squadWeapons(), SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);
            if (result != SquadEquipmentResult.APPLIED)
                throw new IllegalStateException(kit.squadWeapons() + " not issued to " + squad + ": " + result);
        }
        // Same campaign organization path; each squad has an acting NCO, with no officer buffs.
        var deployment = CampaignMarineDeployment.freezeSelection(roster, selected, chapter.squads * 12, null);
        var fitted = IntStream.range(0, boats).mapToObj(i -> FittedBoat.standard(ShuttleType.AEROSHUTTLE)).toList();
        var manifest = DetachmentResolver.buildShuttleManifestForPersonnel(mission, fitted, deployment.size());
        var fixture = chapter.fixture(mission.battleSeed, manifest, chapter.profile(chapter.surface), campaignHas);
        fixture = BattleFixtureJson.fromJson(BattleFixtureJson.toJson(fixture));
        var powers = PowerCatalog.resolve(List.of(), mission, roster.mechBay().activeDeployment());
        if (powers.size() != 1) throw new IllegalStateException("expected the Hound's drop alone, got " + powers);
        var resources = new FiniteCommandPowerResources(SUPPLIES);
        var overlay = BattleLaunchOverlay.capture(0, deployment, FlybyRoster.EMPTY, FlybyRoster.EMPTY, powers,
                resources.availableSupplies(), mission.fieldPresencePolicy);
        return new Muster(fixture, deployment, powers, resources, overlay);
    }

    /**
     * Calls the Hound down on the first landing pad the drop can reach, once the drop is ready. Returns true on the
     * tick it is requested; call it before each tick until then.
     */
    static boolean callHound(BattleSimulation sim) {
        var service = sim.getCommandPowerService();
        var support = service.getPower(MechSupport.ID);
        if (!service.canActivate(support)) return false;
        for (var pad : sim.getMarineLandingPads()) if (support.canTarget(pad.centerX, pad.centerY, sim, HOUND_PICK)) {
            service.requestActivation(MechSupport.ID, pad.centerX, pad.centerY, HOUND_PICK);
            return true;
        }
        return false;
    }

    /** The recovered Hound, with {@code leftShoulderPod} fitted when one is named. */
    static MechDeploymentSpec houndDeployment(MechWeaponComponent leftShoulderPod) {
        var spec = WaylineHound.deployment();
        if (leftShoulderPod == null) return spec;
        return new MechDeploymentSpec(spec.variant(), spec.role(), spec.missileReplenisher(), spec.arms(),
                spec.leftArm(), spec.rightArm(), leftShoulderPod, spec.rightShoulder(), spec.displayName(),
                spec.frame(), spec.campaignId(), spec.refit(), spec.wear());
    }
}
