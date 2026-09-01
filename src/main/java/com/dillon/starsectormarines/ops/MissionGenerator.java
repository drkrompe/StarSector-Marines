package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.setup.GroundRosterProfile;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.campaign.BriefingComposer;
import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.ContractOperationTierPolicy;
import com.dillon.starsectormarines.campaign.OfficerMoodReader;
import com.dillon.starsectormarines.campaign.PlanetaryAssaultMissionKey;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.campaign.PatronBriefingContextComposer;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.detachment.MissionForceEnvelope;
import com.dillon.starsectormarines.ops.intel.DefenseLevel;
import com.dillon.starsectormarines.ops.intel.IntelReader;
import com.dillon.starsectormarines.ops.intel.PlanetIntel;
import com.dillon.starsectormarines.ops.mission.story.StoryEligibilityContext;
import com.dillon.starsectormarines.ops.mission.story.StoryMissionRegistry;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Projects the work a client actually owns at one planet. Patron clients read
 * persisted contract rows; ordinary faction clients expose only authored story
 * missions. The industry catalog remains candidate content for campaign offer
 * policy, but opening this screen never manufactures an unpersisted mission.
 *
 * <p>Story eligibility and placement remain deterministic per
 * {@code (planet.name, client.factionId)}, so revisits do not shuffle authored
 * work under the player.
 */
public final class MissionGenerator {

    /** Cap on total emitted missions, keeps the tactical map readable on dense colonies. */
    private static final int MAX_MISSIONS = 6;

    private MissionGenerator() {}

    public static List<Mission> generate(PlanetAPI planet, Client client) {
        if (planet == null || client == null) return Collections.emptyList();

        // Debug client — every eligible MissionType × OperationTier pairing at
        // medium risk, with no caps. Gated upstream by DevConfig.DEBUG_CLIENT.
        if (MarineOpsContext.DEBUG_CLIENT_FACTION_ID.equals(client.factionId)) {
            return generateDebugGrid(planet, client);
        }

        // Patron client — emit Missions from OFFERED contracts. Industry catalog
        // and story missions don't apply: patron contracts are first-class.
        if (client.patronHouseId != -1L) {
            return generateFromContracts(planet, client);
        }

        PlanetIntel intel = IntelReader.read(planet);

        long seed = (planet.getName() + ":" + client.factionId).hashCode();
        Random r = new Random(seed);

        List<Mission> out = new ArrayList<>();

        // Story missions first — eligibility-gated, one-shot. Prepended so they read
        // as marquee entries on the tactical map.
        MarineRosterScript rosterScript = MarineRosterScript.getInstance();
        MarineRoster roster = rosterScript != null ? rosterScript.roster() : null;
        if (roster != null) {
            StoryEligibilityContext storyCtx = new StoryEligibilityContext(
                    planet, client, intel, roster, seed);
            out.addAll(StoryMissionRegistry.eligibleFor(storyCtx));
        }

        return out;
    }

    /**
     * Emits every eligible {@link MissionType} × {@link OperationTier} pairing
     * at medium risk for the debug client, followed by three swarm-rescue risk
     * entries. Bypasses {@link #MAX_MISSIONS} so every scale is reachable from
     * a single planet.
     *
     * <p>Industry id is the first non-disrupted industry on the planet (or null
     * — disruption writeback no-ops in that case). Payouts + drop counts use
     * the production curves so the debug missions feel like real missions.
     */
    private static List<Mission> generateDebugGrid(PlanetAPI planet, Client client) {
        if (planet.getMarket() == null) return Collections.emptyList();
        MarketAPI market = planet.getMarket();
        String industryId = pickFirstNonDisruptedIndustry(market);

        long seed = ("debug:" + planet.getName()).hashCode();
        Random r = new Random(seed);

        List<Mission> out = new ArrayList<>();
        int index = 0;
        // The debug board enumerates (type x tier), not (type x risk): tier is
        // the axis a force-ratio playtest varies, and a type below its floor
        // is not a thing that exists. Risk rides along at MEDIUM so the board
        // shows each tier's nominal fight rather than its variance.
        for (MissionType type : MissionType.values()) {
            for (OperationTier tier : OperationTier.values()) {
                if (!tier.atLeast(type.tierFloor)) continue;
                RiskLevel risk = RiskLevel.MEDIUM;
                int payout = computePayout(market.getSize(), risk, type, r);

                float x = 0.08f + r.nextFloat() * 0.84f;
                float y = 0.08f + r.nextFloat() * 0.84f;

                FlybyRoster clientSupport = rollFighterSupport(r, client.factionId, risk, Faction.MARINE);
                FlybyRoster enemySupport  = rollFighterSupport(r, client.factionId, risk, Faction.DEFENDER);

                int requiredDrops = requiredDropsFor(type, tier);
                if (com.dillon.starsectormarines.DevConfig.DROP_COUNT_OVERRIDE > 0) {
                    requiredDrops = com.dillon.starsectormarines.DevConfig.DROP_COUNT_OVERRIDE;
                }
                int employerShuttles = rollEmployerShuttles(r, risk, requiredDrops);
                String id = "debug:" + type.name() + ":" + tier.name() + ":" + index++;
                String name = type.name() + " — " + tier.displayName;
                int recommendedSquads = MissionForceEnvelope.recommendedSquads(type, tier);
                String flavor = "DEBUG: " + type.name() + " at " + tier.displayName
                        + " — wants " + recommendedSquads
                        + (recommendedSquads == 1 ? " squad." : " squads.");

                out.add(Mission.builder()
                        .id(id)
                        .name(name)
                        .type(type)
                        .source(MissionSource.DEBUG)
                        .payout(payout)
                        .risk(risk)
                        .tier(tier)
                        .requirements(requirementsFor(risk))
                        .flavor(flavor)
                        .mapPosition(x, y)
                        .clientFighterSupport(clientSupport)
                        .enemyFighterSupport(enemySupport)
                        .requiredDrops(requiredDrops)
                        .employerShuttles(employerShuttles)
                        .targetPlanetName(planet.getName())
                        .targetIndustryId(industryId)
                        .build());
            }
        }
        List<Mission> rescues = debugCivilianRescueMissions(planet.getName(), r, index);
        out.addAll(rescues);
        // Appended last, and drawing from r only after every entry above has
        // taken its rolls, so adding this group cannot shift the board it joins.
        out.addAll(debugFactionComparisonMissions(
                planet.getName(), r, index + rescues.size()));
        return out;
    }

    /**
     * The CONQUEST faction-comparison group: one Full Strength entry per
     * catalogued {@link GroundRosterProfile}, all pinned to a single battlefield
     * so the only thing that differs between two launches is who is defending
     * it. Everything the player brings and everything the map is stays fixed;
     * the defender's doctrine is the single free variable.
     *
     * <p>The list comes from {@link GroundRosterRegistry#profiles()} rather than
     * a faction list written here, so a newly catalogued roster shows up on the
     * board with no code change.
     */
    static List<Mission> debugFactionComparisonMissions(
            String planetName, Random random, int startIndex) {
        if (planetName == null || random == null) return Collections.emptyList();
        MissionType type = MissionType.CONQUEST;
        OperationTier tier = OperationTier.FULL_STRENGTH;
        RiskLevel risk = RiskLevel.MEDIUM;
        long battleSeed = factionComparisonBattleSeed(planetName, type, tier);
        int requiredDrops = requiredDropsFor(type, tier);
        if (DevConfig.DROP_COUNT_OVERRIDE > 0) {
            requiredDrops = DevConfig.DROP_COUNT_OVERRIDE;
        }

        List<GroundRosterProfile> profiles = GroundRosterRegistry.profiles();
        List<Mission> missions = new ArrayList<>(profiles.size());
        int index = Math.max(0, startIndex);
        for (GroundRosterProfile profile : profiles) {
            // Map position is the one per-entry roll: it is where the pin sits on
            // the tactical screen and reaches nothing the battle reads.
            float x = 0.08f + random.nextFloat() * 0.84f;
            float y = 0.08f + random.nextFloat() * 0.84f;
            String defender = defenderDisplayName(profile);
            missions.add(Mission.builder()
                    .id("debug:CONQUEST_DEFENDER:" + profile.id() + ":" + index++)
                    .name("CONQUEST — " + defender)
                    .type(type)
                    .source(MissionSource.DEBUG)
                    .risk(risk)
                    .tier(tier)
                    .requirements(requirementsFor(risk))
                    .flavor("DEBUG COMPARISON: " + tier.displayName + " CONQUEST on one"
                            + " pinned battlefield — only the defender changes. Holding it: "
                            + defender + " (" + profile.id() + ").")
                    .mapPosition(x, y)
                    .requiredDrops(requiredDrops)
                    .targetPlanetName(planetName)
                    .defenderFactionOverride(profile.primaryFactionId())
                    .battleSeed(battleSeed)
                    .build());
        }
        return missions;
    }

    /**
     * The battlefield the whole comparison group lands on. Derived from what the
     * group <em>is</em> — the place, the mission type, the scale — and never from
     * an entry's position in it, because a per-entry seed would hand every
     * faction a different map and quietly destroy the comparison.
     */
    static long factionComparisonBattleSeed(String planetName, MissionType type,
                                            OperationTier tier) {
        return (planetName + '|' + type.name() + '|' + tier.name()).hashCode();
    }

    /**
     * A readable defender name taken from the roster profile's own id — the
     * catalog carries no display string, and a lookup table beside it here would
     * be one more hand-maintained list to go stale.
     */
    static String defenderDisplayName(GroundRosterProfile profile) {
        String id = profile.id();
        String slug = id.substring(id.lastIndexOf('.') + 1);
        StringBuilder out = new StringBuilder(slug.length());
        boolean startOfWord = true;
        for (int i = 0; i < slug.length(); i++) {
            char c = slug.charAt(i);
            if (c == '-' || c == '_') {
                out.append(' ');
                startOfWord = true;
                continue;
            }
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = false;
        }
        return out.length() == 0 ? id : out.toString();
    }

    static List<Mission> debugCivilianRescueMissions(
            String planetName, Random random, int startIndex) {
        if (planetName == null || random == null) return Collections.emptyList();
        List<Mission> missions = new ArrayList<>(RiskLevel.values().length + 1);
        int index = Math.max(0, startIndex);
        float canonicalX = 0.08f + random.nextFloat() * 0.84f;
        float canonicalY = 0.08f + random.nextFloat() * 0.84f;
        missions.add(Mission.builder()
                .id("debug:CIVILIAN_RESCUE:CANONICAL:" + index++)
                .name("SWARM RESCUE — CANONICAL")
                .type(MissionType.EXTRACTION)
                .source(MissionSource.DEBUG_CANONICAL_CIVILIAN_RESCUE)
                .risk(RiskLevel.HIGH)
                .requirements(requirementsFor(RiskLevel.HIGH))
                .flavor("DEBUG: production rescue pressure and four-drop response budget.")
                .mapPosition(canonicalX, canonicalY)
                .requiredDrops(4)
                .targetPlanetName(planetName)
                .civiliansAtRisk(CivilianEvacuationTracker.V1_REPRESENTATIVE_COUNT)
                .build());
        for (RiskLevel risk : RiskLevel.values()) {
            int requiredDrops = requiredDropsFor(MissionType.EXTRACTION,
                tierFor(MissionType.EXTRACTION, risk));
            if (com.dillon.starsectormarines.DevConfig.DROP_COUNT_OVERRIDE > 0) {
                requiredDrops = com.dillon.starsectormarines.DevConfig.DROP_COUNT_OVERRIDE;
            }
            int employerShuttles = rollEmployerShuttles(
                    random, risk, requiredDrops);
            float x = 0.08f + random.nextFloat() * 0.84f;
            float y = 0.08f + random.nextFloat() * 0.84f;
            String id = "debug:CIVILIAN_RESCUE:"
                    + risk.name() + ":" + index++;
            missions.add(Mission.builder()
                    .id(id)
                    .name("SWARM RESCUE STRESS — " + risk.name())
                    .type(MissionType.EXTRACTION)
                    .source(MissionSource.DEBUG_CIVILIAN_RESCUE)
                    .risk(risk)
                    .requirements(requirementsFor(risk))
                    .flavor("DEBUG STRESS TEST: evacuate the registered civilian cohort under "
                            + risk.name() + " force-scaled swarm pressure.")
                    .mapPosition(x, y)
                    .requiredDrops(requiredDrops)
                    .employerShuttles(employerShuttles)
                    .targetPlanetName(planetName)
                    .civiliansAtRisk(CivilianEvacuationTracker.V1_REPRESENTATIVE_COUNT)
                    .build());
        }
        return missions;
    }

    /**
     * Builds the mission list for a patron client — one Mission per OFFERED
     * contract whose patron matches the client and whose pickup market matches
     * the planet. Each Mission carries the contract id so the resolver bridge
     * can write the outcome back to {@link CampaignState}.
     */
    private static List<Mission> generateFromContracts(PlanetAPI planet, Client client) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return Collections.emptyList();
        CampaignState state = script.state();

        MarketAPI pickupMarket = planet.getMarket();
        if (pickupMarket == null) return Collections.emptyList();
        int pickupSlot = state.marketRegistry.intern(pickupMarket.getId());

        List<Mission> out = new ArrayList<>();
        long seed = (planet.getName() + ":patron:" + client.patronHouseId).hashCode();
        Random r = new Random(seed);

        int emitted = 0;
        int currentDay = Global.getSector() != null
                ? CampaignClock.day() : 0;
        for (int i = 0; i < state.contractCount && emitted < MAX_MISSIONS; i++) {
            if (!contractMissionAvailable(state, i, currentDay)) continue;
            if (state.contractPatronHouseId[i] != client.patronHouseId) continue;
            if (state.contractMarketId[i] != pickupSlot) continue;

            Mission m = buildContractMission(state, i, planet, client, r, emitted);
            if (m == null) continue;
            out.add(m);
            emitted++;
        }
        return out;
    }

    private static Mission buildContractMission(CampaignState state, int row,
                                                PlanetAPI pickupPlanet, Client client,
                                                Random r, int index) {
        ContractType contractType = ContractType.fromByte(state.contractType[row]);
        PlanetaryAssaultPhase assaultPhase = contractType == ContractType.PLANETARY_ASSAULT
                ? PlanetaryAssaultPhase.create(
                        state.contractPhasesDone[row] & 0xFF,
                        state.contractPhasesTotal[row] & 0xFF,
                        state.contractBasePayout[row],
                        state.contractSalvageBaseline[row] & 0xFF,
                        state.contractSalvageNegotiated[row] & 0xFF)
                : null;
        ContractMissionProfile profile = assaultPhase == null
                ? ContractMissionProfile.from(contractType) : null;
        if (assaultPhase == null && profile == null) return null;

        long contractId = state.contractId[row];
        int targetMarketSlot = targetMarketSlot(state, row);
        if (targetMarketSlot < 0) return null;
        String targetMarketStr = state.marketRegistry.get(targetMarketSlot);
        if (targetMarketStr == null) return null;
        MarketAPI targetMarket = Global.getSector() != null
                ? Global.getSector().getEconomy().getMarket(targetMarketStr)
                : null;
        if (targetMarket == null || targetMarket.getPrimaryEntity() == null) return null;

        String targetPlanetName = targetMarket.getPrimaryEntity().getName();
        String targetIndustryId = contractIndustry(state, row, targetMarket);
        MissionType missionType = assaultPhase != null
                ? assaultPhase.missionType : profile.missionType;
        String missionTitle = assaultPhase != null
                ? "Planetary Assault — " + assaultPhase.title : profile.title;
        if (assaultPhase != null) {
            long phaseSeed = contractId * 0x9E3779B97F4A7C15L
                    ^ (long) assaultPhase.index * 0xC2B2AE3D27D4EB4FL
                    ^ state.contractPhaseAttempts[row];
            r = new Random(phaseSeed);
        }
        DefenseLevel defense = readDefense(targetMarket);
        RiskLevel risk = deriveRisk(defense, missionType);

        int cashMult = state.contractCashMultiplier[row] & 0xFF;
        if (cashMult <= 0) cashMult = 100;
        int basePayout = assaultPhase != null
                ? assaultPhase.payout : state.contractBasePayout[row];
        int effectivePayout = (int) ((long) basePayout * cashMult / 100L);

        FlybyRoster clientSupport = rollFighterSupport(r, client.factionId, risk, Faction.MARINE);
        FlybyRoster enemySupport  = rollFighterSupport(r, client.factionId, risk, Faction.DEFENDER);

        OperationTier tier = tierForContract(state, row, missionType);
        int requiredDrops = requiredDropsFor(missionType, tier);
        int employerShuttles = rollEmployerShuttles(r, risk, requiredDrops);
        java.util.List<String> employerPowers = rollEmployerPowers(r, risk);

        float x = 0.08f + r.nextFloat() * 0.84f;
        float y = 0.08f + r.nextFloat() * 0.84f;

        String name = missionTitle + " — " + targetPlanetName;
        // Briefing reads as a comms-officer dispatch: an officer-mood prefix
        // wraps the archetype-driven body, with an optional closing aside.
        // The patron-archetype byte is looked up via the patron's row index,
        // mood comes from the company's current state, all variant picks are
        // seeded from the contract id so re-renders + save/load produce the
        // same text. Payout/salvage values match the briefing UI.
        int patronRow = state.houseIndex(state.contractPatronHouseId[row]);
        PatronArchetype archetype = patronRow >= 0
                ? PatronArchetype.fromByte(state.houseArchetype[patronRow])
                : PatronArchetype.TIME_RUSHED;
        String payoutFormatted = "$" + NumberFormat.getIntegerInstance().format(effectivePayout);
        byte missionSalvageBaseline = assaultPhase != null
                ? assaultPhase.salvageBaseline : state.contractSalvageBaseline[row];
        byte missionSalvageNegotiated = assaultPhase != null
                ? assaultPhase.salvageNegotiated : state.contractSalvageNegotiated[row];
        int negotiatedPct = missionSalvageNegotiated & 0xFF;
        String patronMemory = PatronBriefingContextComposer.compose(state,
                state.contractPatronHouseId[row],
                state.contractMarketId[row], contractId,
                state.contractAcceptedTick[row], client.displayName,
                marketSlot -> marketDisplayName(state, marketSlot));
        String flavor = BriefingComposer.compose(archetype, OfficerMoodReader.currentMood(),
                contractId, patronMemory, client.displayName, targetPlanetName,
                payoutFormatted, negotiatedPct);
        String id = assaultPhase != null
                ? PlanetaryAssaultMissionKey.encode(contractId, assaultPhase.index,
                        state.contractPhaseAttempts[row])
                : "contract:" + contractId;

        return Mission.builder()
                .id(id)
                .name(name)
                .type(missionType)
                .source(MissionSource.GENERATED)
                .payout(basePayout)
                .risk(risk)
                .tier(tier)
                .requirements(forceEnvelopeRequirements(missionType, tier))
                .flavor(flavor)
                .mapPosition(x, y)
                .clientFighterSupport(clientSupport)
                .enemyFighterSupport(enemySupport)
                .requiredDrops(requiredDrops)
                .employerShuttles(employerShuttles)
                .targetPlanetName(targetPlanetName)
                .targetIndustryId(targetIndustryId)
                .targetFactionId(targetMarket.getFactionId())
                .contractId(contractId)
                .salvageBaseline(missionSalvageBaseline)
                .salvageNegotiated(missionSalvageNegotiated)
                .cashMultiplier(state.contractCashMultiplier[row])
                .contractSalvageBaseline(state.contractSalvageBaseline[row])
                .contractSalvageNegotiated(state.contractSalvageNegotiated[row])
                .employerPowerIds(employerPowers)
                .build();
    }

    static boolean contractMissionAvailable(CampaignState state, int row, int currentDay) {
        if (state == null || row < 0 || row >= state.contractCount) return false;
        ContractState contractState = ContractState.fromByte(state.contractState[row]);
        if (contractState == ContractState.OFFERED) return true;
        if (contractState != ContractState.IN_PROGRESS
                || ContractType.fromByte(state.contractType[row])
                != ContractType.PLANETARY_ASSAULT) {
            return false;
        }
        int readyDay = state.contractNextPhaseReadyTick[row];
        return readyDay < 0 || currentDay >= readyDay;
    }

    static int targetMarketSlot(CampaignState state, int row) {
        if (state == null || row < 0 || row >= state.contractCount) return -1;
        ContractType type = ContractType.fromByte(state.contractType[row]);
        if (type == ContractType.EXTRACTION) return state.contractMarketId[row];
        long targetHouseId = state.contractTargetHouseId[row];
        if (targetHouseId < 0L) return -1;
        int targetHouseRow = state.houseIndex(targetHouseId);
        return targetHouseRow >= 0 ? state.houseMarketId[targetHouseRow] : -1;
    }

    private static String marketDisplayName(CampaignState state,
                                            int marketSlot) {
        String marketId = state != null
                ? state.marketRegistry.get(marketSlot) : null;
        if (marketId == null || Global.getSector() == null) return null;
        MarketAPI market = Global.getSector().getEconomy().getMarket(marketId);
        if (market == null) return null;
        if (market.getPrimaryEntity() != null
                && market.getPrimaryEntity().getName() != null
                && !market.getPrimaryEntity().getName().trim().isEmpty()) {
            return market.getPrimaryEntity().getName();
        }
        return market.getName();
    }

    private static String pickFirstNonDisruptedIndustry(MarketAPI market) {
        if (market.getIndustries() == null) return null;
        for (com.fs.starfarer.api.campaign.econ.Industry ind : market.getIndustries()) {
            if (ind == null || ind.isDisrupted()) continue;
            return ind.getId();
        }
        return null;
    }

    private static String contractIndustry(CampaignState state, int row,
                                           MarketAPI targetMarket) {
        int industrySlot = state.contractIndustryId[row];
        if (industrySlot >= 0) {
            String industryId = state.industryRegistry.get(industrySlot);
            com.fs.starfarer.api.campaign.econ.Industry industry = industryId != null
                    ? targetMarket.getIndustry(industryId) : null;
            if (industry != null && !industry.isDisrupted()) return industryId;
        }
        return pickFirstNonDisruptedIndustry(targetMarket);
    }

    /** Mirrors IntelReader's defense classification just enough for contract risk. */
    private static DefenseLevel readDefense(MarketAPI market) {
        int size = market.getSize();
        boolean hasMilitary = market.hasIndustry("militarybase") || market.hasIndustry("highcommand");
        boolean hasPatrol   = market.hasIndustry("patrolhq");
        if (hasMilitary && size >= 6) return DefenseLevel.FORTRESS;
        if (hasMilitary)              return DefenseLevel.HEAVY;
        if (hasPatrol && size >= 5)   return DefenseLevel.MODERATE;
        if (hasPatrol)                return DefenseLevel.LIGHT;
        return DefenseLevel.UNDEFENDED;
    }

    /**
     * Maps the planet's 5-tier defense level into a 3-tier mission risk, then drops
     * one tier for stealth-leaning mission types — sabotage and extraction reward
     * sneaking past the defenders, not punching through them.
     */
    private static RiskLevel deriveRisk(DefenseLevel defense, MissionType type) {
        RiskLevel base;
        switch (defense) {
            case FORTRESS:
            case HEAVY:
                base = RiskLevel.HIGH;
                break;
            case MODERATE:
                base = RiskLevel.MEDIUM;
                break;
            case LIGHT:
            case UNDEFENDED:
            default:
                base = RiskLevel.LOW;
                break;
        }
        if (type == MissionType.SABOTAGE || type == MissionType.EXTRACTION) {
            // Step down one tier, bottoming at LOW.
            if (base == RiskLevel.HIGH)   return RiskLevel.MEDIUM;
            if (base == RiskLevel.MEDIUM) return RiskLevel.LOW;
        }
        return base;
    }

    /**
     * Payout = base × risk × type × small random noise, rounded to nearest 500.
     * Larger colonies pay better (more is at stake); covert ops pay a premium over
     * straight assault.
     */
    private static int computePayout(int size, RiskLevel risk, MissionType type, Random r) {
        int base = Math.max(1, size) * 2000;
        float riskMult = riskMultiplier(risk);
        float typeMult = typeMultiplier(type);
        float noise    = 0.85f + r.nextFloat() * 0.30f; // 0.85..1.15
        int   raw      = (int) (base * riskMult * typeMult * noise);
        return Math.max(500, (raw / 500) * 500);
    }

    private static float riskMultiplier(RiskLevel risk) {
        switch (risk) {
            case HIGH:   return 2.5f;
            case MEDIUM: return 1.5f;
            default:     return 1.0f;
        }
    }

    private static float typeMultiplier(MissionType type) {
        switch (type) {
            case CONQUEST:   return 1.8f; // largest payouts — biggest commitment, biggest target
            case EXTRACTION: return 1.4f;
            case SABOTAGE:   return 1.3f;
            case RAID:       return 1.2f;
            case ASSAULT:
            default:         return 1.0f;
        }
    }

    private static String requirementsFor(RiskLevel risk) {
        switch (risk) {
            case LOW:    return "20+ marines";
            case MEDIUM: return "50+ marines, officer recommended";
            case HIGH:   return "100+ marines, veteran officer";
            default:     return "";
        }
    }

    /**
     * Rolls a {@link FlybyRoster} for one side of the battle. Probability of any
     * support scales with risk; profile pool is faction-appropriate via
     * {@link FighterProfile#poolForFaction}.
     */
    private static FlybyRoster rollFighterSupport(Random r, String factionId, RiskLevel risk, Faction side) {
        float chance = (side == Faction.MARINE) ? 0.55f : 0.4f;
        switch (risk) {
            case MEDIUM: chance += 0.10f; break;
            case HIGH:   chance += 0.20f; break;
            default: break;
        }
        if (r.nextFloat() > chance) return FlybyRoster.EMPTY;

        int maxWings = (risk == RiskLevel.HIGH) ? 3 : 2;
        int wingCount = 1 + r.nextInt(maxWings);

        List<FighterProfile> pool = FighterProfile.poolForFaction(factionId);
        List<FighterWing> wings = new ArrayList<>(wingCount);
        for (int i = 0; i < wingCount; i++) {
            FighterProfile profile = pool.get(r.nextInt(pool.size()));
            int sorties;
            switch (risk) {
                case HIGH:   sorties = 2 + r.nextInt(3); break; // 2-4
                case MEDIUM: sorties = 1 + r.nextInt(3); break; // 1-3
                default:     sorties = 1 + r.nextInt(2); break; // 1-2
            }
            float firstArrival = 5f + r.nextFloat() * 25f;
            float interval = 9f + r.nextFloat() * 9f;
            wings.add(new FighterWing(profile, side, sorties, firstArrival, interval));
        }
        return new FlybyRoster(wings);
    }

    /**
     * Per-(type, tier) drop count. Drops feed marines onto the field via
     * shuttle cycling — with capacity-4 Aeroshuttles, drop count × 4 ≈ marines
     * on the field. CONQUEST gets the biggest commitments; SABOTAGE stays
     * smallest for covert flavor.
     */
    /**
     * Lift the work is written for: the tier's drop budget scaled by what the
     * mission type is, so the attacker-to-defender ratio the designer picked
     * holds across types. Floors at two — one drop is not an operation.
     */
    /**
     * Tier for a mission the generator only has a risk level for. The
     * compatibility mapping, floored by the type — so a CONQUEST offered at
     * any risk still comes out at least REINFORCED.
     */
    static OperationTier tierFor(MissionType type, RiskLevel risk) {
        return OperationTier.clampTo(OperationTier.forRisk(risk),
                type != null ? type.tierFloor : null);
    }

    static int requiredDropsFor(MissionType type, OperationTier tier) {
        OperationTier resolved = tier != null ? tier : OperationTier.ESTABLISHED;
        if (type == MissionType.CONQUEST) {
            int seats = MissionForceEnvelope.recommendedPersonnel(type, resolved);
            int seatsPerSortie = MarineArrivalPolicy.PAIRED_HALF_SQUAD
                    .seatsPerSortie(ShuttleType.AEROSHUTTLE);
            return (seats + seatsPerSortie - 1) / seatsPerSortie;
        }
        float weight = type != null ? type.liftWeight : 0.6f;
        return Math.max(2, Math.round(resolved.drops * weight));
    }

    /** Reads the offer-frozen scale; legacy rows derive from patron rank, never target risk. */
    static OperationTier tierForContract(CampaignState state, int row,
                                         MissionType missionType) {
        OperationTier stored = state != null && row >= 0 && row < state.contractCount
                ? OperationTier.fromPersistedByte(state.contractOperationTier[row])
                : null;
        ContractType contractType = state != null && row >= 0 && row < state.contractCount
                ? ContractType.fromByte(state.contractType[row]) : null;
        OperationTier selected = stored != null ? stored
                : ContractOperationTierPolicy.select(state,
                        state != null && row >= 0 && row < state.contractCount
                                ? state.contractPatronHouseId[row] : -1L,
                        contractType);
        if (selected == null) selected = OperationTier.ESTABLISHED;
        return OperationTier.clampTo(selected,
                missionType != null ? missionType.tierFloor : null);
    }

    static String forceEnvelopeRequirements(MissionType type, OperationTier tier) {
        OperationTier resolved = tier != null ? tier : OperationTier.ESTABLISHED;
        int recommendedSquads = MissionForceEnvelope.recommendedSquads(type, resolved);
        return "Minimum 1 fire team · Recommended " + recommendedSquads
                + (recommendedSquads == 1 ? " squad" : " squads");
    }

    /**
     * Hard cap on how many drops the employer covers via single-cycle
     * Aeroshuttles. The employer is a token force, not the bulk — bigger
     * missions are <em>your</em> commitment. Without this, a 40-drop CONQUEST
     * could roll all 40 onto employer Aeroshuttles and let the player skip
     * the LZ entirely, contradicting the flavor.
     */
    private static int employerCoverageCap(RiskLevel risk) {
        if (risk == null) return 3;
        switch (risk) {
            case LOW:    return 3;
            case MEDIUM: return 4;
            case HIGH:   return 5;
        }
        return 3;
    }

    /**
     * Rolls how many dropships the employer covers. Higher-risk missions tend
     * to come with more transport support (the client has more skin in the
     * game), but never more than {@link #employerCoverageCap}. The player
     * supplies the bulk of any non-trivial mission's lift. Debug missions use
     * the briefing's exact transport type/count picker instead of this roll.
     */
    private static int rollEmployerShuttles(Random r, RiskLevel risk, int required) {
        int cap = Math.min(required, employerCoverageCap(risk));
        if (cap <= 0) return 0;
        float roll = r.nextFloat();
        int coverage;
        switch (risk) {
            case HIGH:
                if (roll < 0.30f)      coverage = cap;
                else if (roll < 0.60f) coverage = cap - 1;
                else                   coverage = r.nextInt(cap);
                break;
            case MEDIUM:
                if (roll < 0.20f)      coverage = cap;
                else if (roll < 0.55f) coverage = cap - 1;
                else                   coverage = r.nextInt(cap);
                break;
            default: // LOW
                if (roll < 0.10f)      coverage = cap;
                else if (roll < 0.35f) coverage = cap - 1;
                else                   coverage = r.nextInt(cap);
                break;
        }
        return Math.max(0, Math.min(coverage, cap));
    }

    /**
     * Rolls the command powers the employer/contract offers for this mission —
     * the patron co-source for the player's command-power roster
     * ([[feedback_patron_narrative_discoverable]]). Returns power ids
     * ({@code ReconPing.ID}, …) that {@code ops.detachment.PowerCatalog} maps to
     * instances. Modest, risk-scaled chance; empty most of the time so a player
     * who brings no recon ship can't lean on the employer every mission.
     *
     * <p>Only recon ping exists today, so that's the whole offer pool; widen as
     * the catalog grows.
     */
    private static java.util.List<String> rollEmployerPowers(Random r, RiskLevel risk) {
        float chance;
        switch (risk) {
            case HIGH:   chance = 0.45f; break;
            case MEDIUM: chance = 0.30f; break;
            default:     chance = 0.15f; break;
        }
        if (r.nextFloat() >= chance) return java.util.Collections.emptyList();
        return java.util.List.of(com.dillon.starsectormarines.battle.power.ReconPing.ID);
    }
}
