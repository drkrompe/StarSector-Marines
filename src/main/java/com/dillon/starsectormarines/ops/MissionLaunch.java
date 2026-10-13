package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.marine.OfficerCommandProfile;
import com.dillon.starsectormarines.campaign.start.CompanyShipRestoration;

import com.dillon.starsectormarines.battle.air.FittedBoat;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.setup.ShuttleArrivalPlan;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.evacuation.SwarmDefenseRoster;
import com.dillon.starsectormarines.battle.fixture.BattleFixture;
import com.dillon.starsectormarines.battle.fixture.FighterWingCommitment;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchFixture;
import com.dillon.starsectormarines.battle.fixture.BattleLaunchOverlay;
import com.dillon.starsectormarines.battle.fixture.CivilianRescueBattleFixture;
import com.dillon.starsectormarines.battle.fixture.ConquestBattleFixture;
import com.dillon.starsectormarines.battle.fixture.ExtractionBattleFixture;
import com.dillon.starsectormarines.battle.fixture.SabotageBattleFixture;
import com.dillon.starsectormarines.battle.fixture.RaidBattleFixture;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SettlementZoning;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.campaign.CivilianRescueMissionKey;
import com.dillon.starsectormarines.campaign.BrokenSupplyRouteProgress;
import com.dillon.starsectormarines.campaign.LastLiftProgress;
import com.dillon.starsectormarines.campaign.FreightRunProgress;
import com.dillon.starsectormarines.campaign.SilentColonyMissionKey;
import com.dillon.starsectormarines.DevConfig;
import com.fs.starfarer.api.Global;
import com.dillon.starsectormarines.battle.power.CommandPower;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.power.HostileShipSupport;
import com.dillon.starsectormarines.battle.warship.DebugWarship;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.warship.MissionStance;
import com.dillon.starsectormarines.ops.detachment.HostileShipFleets;
import com.dillon.starsectormarines.ops.detachment.HostileShipReport;
import com.dillon.starsectormarines.ops.detachment.HostileShipSelection;
import com.dillon.starsectormarines.ops.detachment.PowerCatalog;
import com.dillon.starsectormarines.ops.detachment.Detachment;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.ops.detachment.TargetProfileResolver;
import com.dillon.starsectormarines.ops.detachment.CampaignMarineDeployment;
import com.dillon.starsectormarines.ops.detachment.CampaignCommandPowerResources;
import com.dillon.starsectormarines.battle.power.CommandPowerResources;
import com.dillon.starsectormarines.ops.detachment.CommandDeck;
import com.dillon.starsectormarines.ops.detachment.DebugCompany;
import com.dillon.starsectormarines.ops.detachment.PersonnelReadiness;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.Global;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

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

    static boolean requiresOfficerInduction(MarineRoster roster, MissionSource source) {
        return source != null && !source.isDebug() && roster != null && roster.officerInductionRequired();
    }

    static boolean requiresOfficerInduction(Mission mission) {
        if (mission == null || mission.source.isDebug() || Global.getSector() == null) return false;
        MarineRosterScript script = MarineRosterScript.getInstance();
        return requiresOfficerInduction(script == null ? null : script.roster(), mission.source);
    }

    static void requireOfficerInductionComplete(MarineRoster roster, MissionSource source) {
        if (requiresOfficerInduction(roster, source))
            throw new IllegalStateException("Complete Castle's First Command lesson in Barracks before deployment.");
    }

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
                                                   List<FittedBoat> committedShuttles,
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
                                                   List<FittedBoat> committedShuttles,
                                                   FlybyRoster committedWings,
                                                   FlybyRoster debugWings,
                                                   Collection<String> selectedPowerIds) {
        return buildSimulation(ctx, m, committedShuttles, committedWings, debugWings,
                selectedPowerIds, null);
    }

    /** Build using an explicit member-level command-power source commitment. */
    public static BattleSimulation buildSimulation(MarineOpsContext ctx,
                                                   Mission m,
                                                   List<FittedBoat> committedShuttles,
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
                                                   List<FittedBoat> committedShuttles,
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
                                            List<FittedBoat> committedShuttles,
                                            FlybyRoster committedWings,
                                            FlybyRoster debugWings,
                                            Collection<String> selectedPowerIds,
                                            List<FleetMemberAPI> committedPowerSources,
                                            DebugMechRoster debugMechs) {
        ScenarioPlan plan = captureScenario(ctx, m, committedShuttles, committedWings,
                selectedPowerIds, committedPowerSources, debugMechs,
                m.battleSeed != null ? m.battleSeed : System.currentTimeMillis());
        return finishSimulation(ctx, m, debugWings, plan, plan.builder().get());
    }

    /** Snapshot campaign inputs on the UI thread before handing pure construction to a worker. */
    static ScenarioPlan captureScenario(MarineOpsContext ctx, Mission m,
                                        List<FittedBoat> committedShuttles,
                                        FlybyRoster committedWings,
                                        Collection<String> selectedPowerIds,
                                        List<FleetMemberAPI> committedPowerSources,
                                        DebugMechRoster debugMechs, long seed) {
        if (!DetachmentResolver.hasRequiredTransport(m, committedShuttles))
            throw new IllegalStateException("Company boats are required to cover this operation's lift.");
        int personnel = !m.source.isDebug() && DetachmentResolver.usesSelectedPersonnel(m)
                ? selectedMarineSeats(ctx) : 0;
        return captureScenario(ctx, m, committedShuttles, committedWings, selectedPowerIds,
                committedPowerSources, debugMechs, seed, personnel);
    }

    /** Same launch boundary with the company's selected ready count already captured. */
    static ScenarioPlan captureScenario(MarineOpsContext ctx, Mission m,
                                        List<FittedBoat> committedShuttles,
                                        FlybyRoster committedWings,
                                        Collection<String> selectedPowerIds,
                                        List<FleetMemberAPI> committedPowerSources,
                                        DebugMechRoster debugMechs, long seed, int companyPersonnel) {
        if (!DetachmentResolver.hasRequiredTransport(m, committedShuttles))
            throw new IllegalStateException("Company boats are required to cover this operation's lift.");
        DebugMissionCatalog.Scenario debugScenario = DebugMissionCatalog.scenario(m);
        if (debugScenario != null) {
            var manifest = DebugMissionCatalog.manifest(m, committedShuttles, ctx.getDebugCompanyRoster().lineReadySoldiers().size());
            Detachment det = new Detachment(manifest, FlybyRoster.EMPTY, List.of());
            if (debugMechs != null) det = debugMechs.applyTo(det);
            det = CommandDeck.apply(det, selectedPowerIds);
            Detachment frozen = det;
            return new ScenarioPlan(Arrays.asList(m.id, seed, manifest), det,
                    debugScenario.opening() ? 1 : 0, null,
                    () -> DebugMissionCatalog.build(debugScenario, seed, frozen.shuttleManifest), "pirates",
                    HostileShipReport.UNDEFENDED);
        }
        if (SupplyRouteCampaign.owns(m.id)) {
            SupplyRouteCampaign.requireCurrent(m);
            if (!SupplyRouteCampaign.atSite(ctx.planet))
                throw new IllegalStateException("Travel to the accepted supply-route site before deployment.");
        }
        Detachment det = m.source == MissionSource.STATIONING
                ? DetachmentResolver.resolveStationed(m)
                : committedPowerSources == null && !m.source.isDebug()
                        ? DetachmentResolver.resolve(m, committedShuttles, committedWings)
                        : DetachmentResolver.resolve(m, committedShuttles, m.source.isDebug() ? FlybyRoster.EMPTY : committedWings,
                                m.source.isDebug() ? List.of() : committedPowerSources);
        if (debugMechs != null) det = debugMechs.applyTo(det);
        det = CommandDeck.apply(det, selectedPowerIds);
        if (DetachmentResolver.usesSelectedPersonnel(m)) {
            int selectedPersonnel = m.source.isDebug()
                    ? (ctx == null ? DebugMissionCatalog.preset(m).squads() * 12
                            : ctx.getDebugCompanyRoster().lineReadySoldiers().size())
                    : companyPersonnel;
            det = new Detachment(
                    DetachmentResolver.buildShuttleManifestForPersonnel(
                            m, committedShuttles, selectedPersonnel),
                    det.marineWings, det.powers);
        }

        // Heavy-armaments availability on the target world drives whether the
        // defender side fields a HEAVY_MECH (see BattleSetup).
        if (CorvusWaylineCampaign.owns(m.id)) {
            CorvusWaylineCampaign.requireCurrent(m);
            if (!CorvusWaylineCampaign.atSite(ctx.planet))
                throw new IllegalStateException("Travel to the Corvus chapter's destination before deploying.");
        }
        boolean detachedDebugWorld = m.source.isDebug() && (ctx == null || ctx.planet == null);
        boolean enemyHasHeavyArmor = !detachedDebugWorld && !CorvusWaylineCampaign.isBattle(m)
                && DetachmentResolver.planetHasHeavyArmaments(m.targetPlanetName);

        // Campaign → battle bridge: the target world's planetary defenses /
        // market read, distilled once at the boundary so generation can reflect
        // which world the battle is over. NEUTRAL for story ops with no market.
        // A mission may name a different defender than the market's own owner
        // (the debug board's faction-comparison group). This is the one place
        // that override is applied: it rewrites the profile's faction id and
        // nothing else, so only roster selection sees it and generation cannot.
        TargetProfile profile = (detachedDebugWorld ? TargetProfile.NEUTRAL : CorvusWaylineCampaign.isBattle(m) ? CorvusWaylineCampaign.profile(m)
                : SupplyRouteCampaign.isBattle(m)
                ? TargetProfileResolver.fromMarket(SupplyRouteCampaign.destination().planet().getMarket())
                : TargetProfileResolver.resolve(m.targetPlanetName))
                .withFactionId(m.defenderFactionOverride);

        // How settled the map is: the mission's own statement where it made one,
        // and the market's size where it did not. Resolved once here rather than
        // inside each factory so every mission type reads the same answer.
        PrecinctPlan.Sprawl sprawl = m.sprawl != null
                ? m.sprawl : SettlementZoning.sprawlFor(profile.marketSize());

        // The caller pins the seed for the briefing's lifetime. Launch consumes
        // that scenario; an ordinary later deployment starts with a new seed.
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
        Supplier<BattleSimulation> builder;
        Detachment constructionDetachment = det;
        BattleFixture fixture = null;
        OpeningOperationKind openingOperation = OpeningOperationKind.fromMission(m);
        if (CorvusWaylineCampaign.isBattle(m)) {
            fixture = CorvusWaylineCampaign.requireCurrent(m).chapter().fixture(seed, constructionDetachment.shuttleManifest, profile);
            builder = fixture::build;
        } else if (SupplyRouteCampaign.isBattle(m)) {
            BrokenSupplyRouteProgress route = SupplyRouteCampaign.requireCurrent(m);
            builder = supplyRouteBuilder(route, seed, constructionDetachment.shuttleManifest, profile);
        } else if (openingOperation != null) {
            builder = () -> BattleSetup.createOpeningOperation(seed,
                    constructionDetachment.shuttleManifest,
                    DetachmentResolver.employerPhysicalShipCount(m),
                    openingOperation, profile,
                    m.source == MissionSource.STORY && openingOperation == OpeningOperationKind.COUNTERATTACK);
        } else if (isSilentColonyBattle(m)) {
            builder = () -> BattleSetup.createSilentColony(seed,
                    m.campaignEventThreatSeed, m.civiliansAtRisk,
                    constructionDetachment.shuttleManifest, m.risk);
        } else if (isCivilianRescueBattle(m)) {
            int firstWaveMarineSeats = firstWaveSeats(constructionDetachment.shuttleManifest);
            boolean stressTest = m.source == MissionSource.DEBUG_CIVILIAN_RESCUE;
            int swarmCount = stressTest
                    ? SwarmDefenseRoster.debugCountFor(
                            m.risk, firstWaveMarineSeats)
                    : SwarmDefenseRoster.countFor(m.risk);
            CivilianRescueBattleFixture rescueFixture =
                    CivilianRescueBattleFixture.fromFactoryInputs(seed,
                            constructionDetachment.shuttleManifest, enemyHasHeavyArmor, m.risk,
                            swarmCount, profile, stressTest);
            // Production launch and headless replay intentionally meet here:
            // the fixture carries inputs, while BattleSetup remains the only
            // implementation of map/scenario/unit construction.
            builder = rescueFixture::build;
            fixture = rescueFixture;
        } else switch (m.type) {
            case SABOTAGE:
                SabotageBattleFixture sabotageFixture =
                        SabotageBattleFixture.fromFactoryInputs(seed,
                                constructionDetachment.shuttleManifest, enemyHasHeavyArmor,
                                m.tier, m.risk, profile, constructionDetachment.marineWings,
                                m.enemyFighterSupport, m.sprawl);
                builder = sabotageFixture::build;
                fixture = sabotageFixture;
                break;
            case CONQUEST:
                ConquestBattleFixture conquestFixture =
                        ConquestBattleFixture.fromFactoryInputs(seed,
                                constructionDetachment.shuttleManifest, enemyHasHeavyArmor,
                                m.tier, m.risk, profile, constructionDetachment.marineWings,
                                m.enemyFighterSupport,
                                conquestArrivalPlan, sprawl, m.standoff, m.lanes,
                                m.landing, m.lanePaths);
                builder = conquestFixture::build;
                fixture = conquestFixture;
                break;
            case RAID:
                RaidBattleFixture raidFixture = RaidBattleFixture.fromFactoryInputs(
                        seed, constructionDetachment.shuttleManifest, enemyHasHeavyArmor,
                        m.tier, m.risk, profile, constructionDetachment.marineWings,
                        m.enemyFighterSupport, sprawl);
                builder = raidFixture::build;
                fixture = raidFixture;
                break;
            case EXTRACTION:
                ExtractionBattleFixture extractionFixture =
                        ExtractionBattleFixture.fromFactoryInputs(seed,
                                constructionDetachment.shuttleManifest, enemyHasHeavyArmor,
                                m.tier, m.risk, profile, constructionDetachment.marineWings,
                                m.enemyFighterSupport, sprawl);
                builder = extractionFixture::build;
                fixture = extractionFixture;
                break;
            case ASSAULT:
            default:
                // The allied garrison rides the same profile the defenders were
                // built from. The override above rewrote its faction id and
                // nothing else, and the garrison's size reads the market's own
                // ground-defence strength — which it did not touch.
                builder = () -> BattleSetup.createPlaceholder(seed, constructionDetachment.shuttleManifest,
                        enemyHasHeavyArmor, m.tier, m.risk, m.type, profile,
                        constructionDetachment.marineWings, m.enemyFighterSupport, sprawl,
                        m.alliedGarrisonFactionId, m.alliedGarrisonStrengthMult);
                break;
        }

        Object key = fixture != null ? fixture : Arrays.asList(m.id, m.type, m.tier, m.risk,
                seed, enemyHasHeavyArmor, profile, sprawl, openingOperation, firstPlayerShuttle,
                m.campaignEventThreatSeed, m.civiliansAtRisk, m.alliedGarrisonFactionId,
                m.alliedGarrisonStrengthMult, constructionDetachment.shuttleManifest,
                FighterWingCommitment.captureRoster(constructionDetachment.marineWings),
                FighterWingCommitment.captureRoster(m.enemyFighterSupport));
        String defenderFactionId = SupplyRouteCampaign.isBattle(m) || openingOperation != null || isSilentColonyBattle(m)
                || isCivilianRescueBattle(m) ? "" : profile.factionId();
        return new ScenarioPlan(key, det, firstPlayerShuttle, fixture, builder, defenderFactionId,
                hostileShipFor(m, profile, defenderFactionId, null));
    }

    /**
     * The ship the defending world answers with, frozen at launch. A campaign mission takes the largest
     * eligible hull of a free fleet of the defending faction in the target's system
     * ({@link HostileShipSelection}); a debug, scenario or employer-scripted mission hands the same
     * input in by declaration ({@code declared}) and searches nothing. Never a stationed company's own
     * colony, a scripted chapter that supplies its own ship, or a mission with no named defender.
     */
    static HostileShipReport hostileShipFor(Mission m, TargetProfile profile, String defenderFactionId,
                                            ShipSupportSource declared) {
        if (defenderFactionId == null || defenderFactionId.isEmpty() || m.source == MissionSource.STATIONING
                || CorvusWaylineCampaign.isBattle(m)) return HostileShipReport.declared(declared);
        try {
            boolean campaign = !m.source.isDebug();
            return HostileShipSelection.resolve(declared, campaign, profile,
                    campaign && declared == null
                            ? HostileShipFleets.inSystemOf(m.targetPlanetName, profile) : List.of());
        } catch (RuntimeException e) {
            Global.getLogger(MissionLaunch.class).warn("MarineOps: hostile ship could not be sourced", e);
            return HostileShipReport.UNDEFENDED;
        }
    }

    /** Shared campaign/evidence construction boundary; capture all save inputs before worker execution. */
    static Supplier<BattleSimulation> supplyRouteBuilder(BrokenSupplyRouteProgress route, long seed,
                                                        List<ShuttleAssignment> manifest, TargetProfile profile) {
        int operation = route.operation();
        LastLiftProgress.Attempt attempt = operation == BrokenSupplyRouteProgress.LIFT ? route.crew().previewAttempt() : null;
        FreightRunProgress.Attempt cargo = operation == BrokenSupplyRouteProgress.THUNDER ? route.freight().previewAttempt() : null;
        int delivered = operation == BrokenSupplyRouteProgress.THUNDER ? route.freight().deliveredCount() : 0;
        return switch (operation) {
            case BrokenSupplyRouteProgress.JUNCTION -> () -> BattleSetup.createJunction(seed, manifest, profile);
            case BrokenSupplyRouteProgress.LIFT -> () -> BattleSetup.createLastLift(seed, manifest, profile, attempt);
            case BrokenSupplyRouteProgress.THUNDER -> () -> BattleSetup.createThunderRun(seed, manifest, profile, cargo, delivered);
            case BrokenSupplyRouteProgress.RELAY -> () -> BattleSetup.createRelayNine(seed, manifest, profile);
            default -> throw new IllegalStateException("Unknown supply-route operation");
        };
    }

    /** Pure construction input; contains no campaign API handles. */
    record ScenarioPlan(Object key, Detachment detachment, int firstPlayerShuttle,
                        BattleFixture fixture, Supplier<BattleSimulation> builder,
                        String defenderFactionId, HostileShipReport hostileShip) { }

    /** Applies campaign state only after the player explicitly accepts deployment. */
    static PreparedBattle finishSimulation(MarineOpsContext ctx, Mission m, FlybyRoster debugWings,
                                          ScenarioPlan plan, BattleSimulation sim) {
        return finishSimulation(ctx, m, debugWings, DebugWarship.Spec.OFF, plan, sim);
    }

    /** As above, plus the briefing's WARSHIP DEBUG choice, installed after the launch overlay. */
    static PreparedBattle finishSimulation(MarineOpsContext ctx, Mission m, FlybyRoster debugWings,
                                          DebugWarship.Spec debugWarship,
                                          ScenarioPlan plan, BattleSimulation sim) {
        Detachment det = plan.detachment();
        int firstPlayerShuttle = plan.firstPlayerShuttle();
        BattleFixture fixture = plan.fixture();
        try {
            MarineRosterScript personnel = m.source.isDebug() ? null : MarineRosterScript.getInstance();
            requireOfficerInductionComplete(personnel == null ? null : personnel.roster(), m.source);
            // Scenario factories author seat roles/objectives first; the persistent
            // roster then overlays each seat's identity, progression, armor and gear.
            int playerSeats = CampaignMarineDeployment.requiredSeats(
                    det.shuttleManifest, firstPlayerShuttle);
            ctx.setMarineDeploymentCapacity(playerSeats);
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
                        ctx.getSelectedMarineSquadIds(), playerSeats, ctx.getSelectedCaptain(),
                        CompanyShipRestoration.moraleRecoveryAboard());
            }

            // Generic factories leave only the enemy wings that fit their shared
            // force budget on the sim. Combine those with marine-side cover
            // (committed bays + employer), then any force-spawned debug wings (both
            // sides — each FighterWing carries its own side, so the overlay spawns
            // it right); then install the active command-power roster.
            CommandPowerResources liveResources = m.source.isDebug() ? CommandPowerResources.FREE
                    : new CampaignCommandPowerResources();
            boolean supplyRoute = SupplyRouteCampaign.isBattle(m);
            if (CorvusWaylineCampaign.isBattle(m)
                    && !SupplyRouteCampaign.forceReady(personnel == null ? null : personnel.roster(), ctx.getSelectedMarineSquadIds()))
                throw new IllegalStateException("Select at least two squads with four ready marines each.");
            BrokenSupplyRouteProgress route = supplyRoute ? SupplyRouteCampaign.requireCurrent(m) : null;
            if (supplyRoute) {
                if (!SupplyRouteCampaign.forceReady(personnel == null ? null : personnel.roster(), ctx.getSelectedMarineSquadIds()))
                    throw new IllegalStateException("Select at least two squads with four ready marines each.");
                deployment.applyToAuthoredPlayerLift(sim);
            }
            var debugScenario = DebugMissionCatalog.scenario(m);
            boolean authoredDebugLift = debugScenario != null && !debugScenario.opening()
                    && debugScenario.corvus() == null && debugScenario != DebugMissionCatalog.Scenario.SILENT_COLONY
                    && debugScenario != DebugMissionCatalog.Scenario.VETERANS_JOB;
            if (authoredDebugLift) deployment.applyToAuthoredPlayerLift(sim);
            BattleLaunchOverlay launch = BattleLaunchOverlay.capture(
                    firstPlayerShuttle, supplyRoute || authoredDebugLift ? CampaignMarineDeployment.EMPTY : deployment, det.marineWings, debugWings,
                    debugShipPowers(m, debugWarship, det.powers), Math.max(0, liveResources.availableSupplies()),
                    m.fieldPresencePolicy);
            launch.applyTo(sim, liveResources);
            installHostileShip(m, debugWarship, plan.hostileShip().sourceOrNull(), sim);
            sim.setCompanyStance(m.source == MissionSource.STATIONING ? MissionStance.DEFEND : MissionStance.ATTACK);
            sim.setOperationOfficer(OfficerCommandProfile.capture(m.source.isDebug() ? null : ctx.getSelectedCaptain()));
            if (fixture != null) {
                fixture = new BattleLaunchFixture(fixture, launch);
            }

            if (route != null && !route.begin(m.id)) throw new IllegalStateException("This supply-route attempt has already launched.");
            if (CorvusWaylineCampaign.isBattle(m)) CorvusWaylineCampaign.begin(m);
            return new PreparedBattle(sim, fixture, det);
        } catch (RuntimeException | Error failure) {
            sim.close();
            throw failure;
        }
    }

    /**
     * The briefing's WARSHIP DEBUG pick as a stock source for the Ship on Station power: the
     * same mechanism a committed fleet member uses, with the stock variant and no cost. A
     * hostile pick is not a call the company can make; it is the defender's source ({@link #installHostileShip}).
     */
    static List<CommandPower> debugShipPowers(Mission m, DebugWarship.Spec spec, List<CommandPower> powers) {
        if (!debugShipIsSource(m, spec)) return powers;
        return PowerCatalog.withShipSources(powers, List.of(spec.hull().source()));
    }

    private static boolean debugShipIsSource(Mission m, DebugWarship.Spec spec) {
        return DevConfig.DEBUG_WARSHIP_PICKER && m.source.isDebug() && spec != null && spec.active()
                && spec.side() == Faction.MARINE;
    }

    /**
     * The defending side's ship, on the same mechanism as the company's. A world that warrants one
     * hands the sim its frozen source and the defender's commander calls it at the trigger; the
     * briefing's hostile debug pick (debug missions only) replaces that source and is called at the
     * first look instead of waiting for the trigger. A friendly debug pick is a company source
     * ({@link #debugShipPowers}) and installs nothing here.
     */
    private static void installHostileShip(Mission m, DebugWarship.Spec spec, ShipSupportSource worldShip,
                                           BattleSimulation sim) {
        boolean debugPick = DevConfig.DEBUG_WARSHIP_PICKER && m.source.isDebug() && spec != null && spec.active()
                && spec.side() == Faction.DEFENDER;
        ShipSupportSource source = debugPick ? spec.hull().source() : worldShip;
        if (source == null) return;
        sim.installHostileShipSupport(source, debugPick, debugPick ? ship -> DebugWarship.applyPick(ship, spec) : null);
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

    private static int selectedMarineSeats(MarineOpsContext ctx) {
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
