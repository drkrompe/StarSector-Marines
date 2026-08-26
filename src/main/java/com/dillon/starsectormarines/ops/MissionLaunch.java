package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.evacuation.SwarmDefenseRoster;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.fixture.CivilianRescueBattleFixture;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.fixture.SabotageBattleFixture;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.campaign.CivilianRescueMissionKey;
import com.dillon.starsectormarines.campaign.SilentColonyMissionKey;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.TargetProfileResolver;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.CampaignCommandPowerResources;
import com.dillon.starsectormarines.ops.detachment.CommandDeck;
import com.dillon.starsectormarines.ops.detachment.DebugCompany;
import com.dillon.starsectormarines.ops.detachment.PersonnelReadiness;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The single accept-path both pre-battle entry points ({@link BriefingScreen},
 * {@link CommsConsolePanel}) route through: resolve the committed
 * {@link Detachment}, build the type-specific {@link BattleSimulation}, and wire
 * the detachment's support (fighter cover + command powers) into it.
 *
 * <p>Collapses logic the two screens used to copy-paste. The screens keep only
 * their own transient toggle state (which transports / wings are committed) and
 * hand the resolved lists in; everything from "resolve" onward lives here so the
 * two paths can't drift.
 */
public final class MissionLaunch {

    private MissionLaunch() {}

    /**
     * Build the battle for {@code m} from the player's committed support and
     * install its simulation, construction fixture, and detachment on
     * {@code ctx}. The caller is responsible for the screen transition.
     *
     * @param committedShuttles the player's committed transports (priority-sorted)
     * @param committedWings    the player's committed marine-side fighter cover
     * @param debugWings        force-spawned debug wings (both sides), from the
     *                          {@code DEBUG_AIRCRAFT_PICKER} briefing panel;
     *                          {@link FlybyRoster#EMPTY} in normal play
     */
    public static BattleSimulation buildSimulation(MarineOpsContext ctx,
                                                   Mission m,
                                                   List<ShuttleType> committedShuttles,
                                                   FlybyRoster committedWings,
                                                   FlybyRoster debugWings) {
        Detachment available = m.source == MissionSource.STATIONING
                ? DetachmentResolver.resolveStationed(m)
                : DetachmentResolver.resolve(m, committedShuttles, committedWings);
        return buildSimulation(ctx, m, committedShuttles, committedWings, debugWings,
                CommandDeck.defaultSelection(available.powers));
    }

    public static BattleSimulation buildSimulation(MarineOpsContext ctx,
                                                   Mission m,
                                                   List<ShuttleType> committedShuttles,
                                                   FlybyRoster committedWings,
                                                   FlybyRoster debugWings,
                                                   Collection<String> selectedPowerIds) {
        return buildSimulation(ctx, m, committedShuttles, committedWings, debugWings,
                selectedPowerIds, null);
    }

    /** Build using an explicit member-level command-power source commitment. */
    public static BattleSimulation buildSimulation(MarineOpsContext ctx,
                                                   Mission m,
                                                   List<ShuttleType> committedShuttles,
                                                   FlybyRoster committedWings,
                                                   FlybyRoster debugWings,
                                                   Collection<String> selectedPowerIds,
                                                   List<FleetMemberAPI> committedPowerSources) {
        return buildSimulation(ctx, m, committedShuttles, committedWings, debugWings,
                selectedPowerIds, committedPowerSources, null);
    }

    /** Build with an optional debug-only marine mech-support roster. */
    public static BattleSimulation buildSimulation(MarineOpsContext ctx,
                                                   Mission m,
                                                   List<ShuttleType> committedShuttles,
                                                   FlybyRoster committedWings,
                                                   FlybyRoster debugWings,
                                                   Collection<String> selectedPowerIds,
                                                   List<FleetMemberAPI> committedPowerSources,
                                                   DebugMechRoster debugMechs) {
        PreparedBattle prepared = prepareSimulation(ctx, m, committedShuttles,
                committedWings, debugWings, selectedPowerIds,
                committedPowerSources, debugMechs);
        ctx.setBattle(prepared.simulation(), prepared.fixture(),
                prepared.detachment());
        return prepared.simulation();
    }

    /** Builds and overlays a battle without publishing it to the UI context. */
    static PreparedBattle prepareSimulation(MarineOpsContext ctx,
                                            Mission m,
                                            List<ShuttleType> committedShuttles,
                                            FlybyRoster committedWings,
                                            FlybyRoster debugWings,
                                            Collection<String> selectedPowerIds,
                                            List<FleetMemberAPI> committedPowerSources,
                                            DebugMechRoster debugMechs) {
        Detachment det = m.source == MissionSource.STATIONING
                ? DetachmentResolver.resolveStationed(m)
                : committedPowerSources == null
                        ? DetachmentResolver.resolve(m, committedShuttles, committedWings)
                        : DetachmentResolver.resolve(m, committedShuttles, committedWings,
                                committedPowerSources);
        if (debugMechs != null) det = debugMechs.applyTo(det);
        det = CommandDeck.apply(det, selectedPowerIds);

        // Heavy-armaments availability on the target world drives whether the
        // defender side fields a HEAVY_MECH (see BattleSetup).
        boolean enemyHasHeavyArmor = DetachmentResolver.planetHasHeavyArmaments(m.targetPlanetName);

        // Campaign → battle bridge: the target world's planetary defenses /
        // market read, distilled once at the boundary so generation can reflect
        // which world the battle is over. NEUTRAL for story ops with no market.
        TargetProfile profile = TargetProfileResolver.resolve(m.targetPlanetName);

        long seed = System.currentTimeMillis();
        int firstPlayerShuttle = m.source == MissionSource.STATIONING
                ? 0 : DetachmentResolver.employerPhysicalShipCount(m);
        ShuttleArrivalPlan conquestArrivalPlan = new ShuttleArrivalPlan(
                m.marineArrivalPolicy, firstPlayerShuttle,
                m.conquestArrivalConfig());
        if (m.type == MissionType.CONQUEST) {
            ShuttleArrivalPlan.ResolvedManifest resolved = conquestArrivalPlan
                    .resolveManifest(det.shuttleManifest,
                            selectedConquestMarineSeats(ctx, m));
            det = new Detachment(resolved.assignments(), det.marineWings, det.powers);
            firstPlayerShuttle = resolved.firstPlayerShuttle();
            conquestArrivalPlan = new ShuttleArrivalPlan(
                    m.marineArrivalPolicy, firstPlayerShuttle,
                    m.conquestArrivalConfig());
        }
        BattleSimulation sim;
        BattleFixture fixture = null;
        OpeningOperationKind openingOperation = OpeningOperationKind.fromMission(m);
        if (openingOperation != null) {
            sim = BattleSetup.createOpeningOperation(seed,
                    det.shuttleManifest,
                    DetachmentResolver.employerPhysicalShipCount(m),
                    openingOperation, profile);
        } else if (isSilentColonyBattle(m)) {
            sim = BattleSetup.createSilentColony(seed,
                    m.campaignEventThreatSeed, m.civiliansAtRisk,
                    det.shuttleManifest, m.risk);
        } else if (isCivilianRescueBattle(m)) {
            int firstWaveMarineSeats = firstWaveSeats(det.shuttleManifest);
            boolean stressTest = m.source == MissionSource.DEBUG_CIVILIAN_RESCUE;
            int swarmCount = stressTest
                    ? SwarmDefenseRoster.debugCountFor(
                            m.risk, firstWaveMarineSeats)
                    : SwarmDefenseRoster.countFor(m.risk);
            CivilianRescueBattleFixture rescueFixture =
                    CivilianRescueBattleFixture.fromFactoryInputs(seed,
                            det.shuttleManifest, enemyHasHeavyArmor, m.risk,
                            swarmCount, profile, stressTest);
            // Production launch and headless replay intentionally meet here:
            // the fixture carries inputs, while BattleSetup remains the only
            // implementation of map/scenario/unit construction.
            sim = rescueFixture.build();
            fixture = rescueFixture;
        } else switch (m.type) {
            case SABOTAGE:
                SabotageBattleFixture sabotageFixture =
                        SabotageBattleFixture.fromFactoryInputs(seed,
                                det.shuttleManifest, enemyHasHeavyArmor,
                                m.tier, m.risk, profile, det.marineWings,
                                m.enemyFighterSupport);
                sim = sabotageFixture.build();
                fixture = sabotageFixture;
                break;
            case CONQUEST:
                ConquestBattleFixture conquestFixture =
                        ConquestBattleFixture.fromFactoryInputs(seed,
                                det.shuttleManifest, enemyHasHeavyArmor,
                                m.tier, m.risk, profile, det.marineWings,
                                m.enemyFighterSupport,
                                conquestArrivalPlan);
                sim = conquestFixture.build();
                fixture = conquestFixture;
                break;
            case ASSAULT:
            case RAID:
            case EXTRACTION:
            default:
                sim = BattleSetup.createPlaceholder(seed, det.shuttleManifest,
                        enemyHasHeavyArmor, m.tier, m.risk, m.type, profile,
                        det.marineWings, m.enemyFighterSupport);
        }

        try {
            // Scenario factories author seat roles/objectives first; the persistent
            // roster then overlays each seat's identity, progression, armor and gear.
            int playerSeats = CampaignMarineDeployment.requiredSeats(
                    det.shuttleManifest, firstPlayerShuttle);
            ctx.setMarineDeploymentCapacity(playerSeats);
            MarineRosterScript personnel = MarineRosterScript.getInstance();
            CampaignMarineDeployment deployment = CampaignMarineDeployment.EMPTY;
            // One deployment shape for both sources: a debug mission fields a
            // detached MarineRoster built by DebugCompany, so it earns the same
            // squad tags, NCO leaders and multi-lift joins the campaign gets.
            if (m.source.isDebug()) {
                MarineRoster company = ctx.getDebugCompanyRoster();
                deployment = CampaignMarineDeployment.freezeSelection(company,
                        new LinkedHashSet<>(DebugCompany.lineSquadIds(company)), playerSeats);
            } else if (personnel != null) {
                deployment = CampaignMarineDeployment.freezeSelection(personnel.roster(),
                        ctx.getSelectedMarineSquadIds(), playerSeats);
            }

            // Generic factories leave only the enemy wings that fit their shared
            // force budget on the sim. Combine those with marine-side cover
            // (committed bays + employer), then any force-spawned debug wings (both
            // sides — each FighterWing carries its own side, so the overlay spawns
            // it right); then install the active command-power roster.
            CampaignCommandPowerResources liveResources =
                    new CampaignCommandPowerResources();
            BattleLaunchOverlay launch = BattleLaunchOverlay.capture(
                    firstPlayerShuttle, deployment, det.marineWings, debugWings,
                    det.powers, Math.max(0, liveResources.availableSupplies()));
            launch.applyTo(sim, liveResources);
            if (fixture != null) {
                fixture = new BattleLaunchFixture(fixture, launch);
            }

            return new PreparedBattle(sim, fixture, det);
        } catch (RuntimeException | Error failure) {
            sim.close();
            throw failure;
        }
    }

    /** Frozen result published only after the caller's launch transaction succeeds. */
    record PreparedBattle(BattleSimulation simulation, BattleFixture fixture,
                          Detachment detachment) implements AutoCloseable {
        @Override
        public void close() {
            simulation.close();
        }
    }

    static boolean isCivilianRescueBattle(Mission mission) {
        if (mission == null) return false;
        return mission.source == MissionSource.DEBUG_CIVILIAN_RESCUE
                || mission.source
                    == MissionSource.DEBUG_CANONICAL_CIVILIAN_RESCUE
                || CivilianRescueMissionKey.parse(mission.id) != null;
    }

    /** All named personnel selected for a Conquest launch; none stay in orbit. */
    private static int selectedConquestMarineSeats(MarineOpsContext ctx,
                                                    Mission mission) {
        if (mission.source.isDebug()) {
            MarineRoster company = ctx.getDebugCompanyRoster();
            return company != null ? company.lineReadySoldiers().size() : 0;
        }
        MarineRosterScript personnel = MarineRosterScript.getInstance();
        if (personnel == null) return 0;
        return PersonnelReadiness.assessSelection(personnel.roster(),
                ctx.getSelectedMarineSquadIds(), 0).selectedReady();
    }

    static boolean isSilentColonyBattle(Mission mission) {
        return mission != null
                && mission.source == MissionSource.CAMPAIGN_EVENT
                && SilentColonyMissionKey.parse(mission.id) != null
                && mission.campaignEventThreatSeed >= 0L;
    }

    static boolean isOpeningOperationBattle(Mission mission) {
        return OpeningOperationKind.fromMission(mission) != null;
    }

    private static int firstWaveSeats(List<ShuttleAssignment> manifest) {
        if (manifest == null) return 0;
        int seats = 0;
        for (ShuttleAssignment assignment : manifest) {
            if (assignment != null) seats += assignment.seatsPerSortie;
        }
        return seats;
    }
}
