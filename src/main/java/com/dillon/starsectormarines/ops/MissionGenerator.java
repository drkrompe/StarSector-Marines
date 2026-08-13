package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationTracker;
import com.dillon.starsectormarines.campaign.BriefingComposer;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.OfficerMoodReader;
import com.dillon.starsectormarines.campaign.PlanetaryAssaultMissionKey;
import com.dillon.starsectormarines.campaign.PatronArchetype;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ops.intel.DefenseLevel;
import com.dillon.starsectormarines.ops.intel.IndustryEntry;
import com.dillon.starsectormarines.ops.intel.IndustryMissionCatalog;
import com.dillon.starsectormarines.ops.intel.IntelReader;
import com.dillon.starsectormarines.ops.intel.MissionArchetype;
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
 * Generates the mission list for a (planet, client) pair. Mission set is now
 * derived from the planet's industries — every refinery is a "Cripple the Refinery"
 * candidate, every Patrol HQ is a barracks assault, etc. — so the list is
 * recognizably about the *specific* planet, not generic.
 *
 * <p>Deterministic per (planet.name, client.factionId): same combination always
 * produces the same set, so revisits feel stable and mission positions don't
 * shuffle on screen rebuild. Industries that are currently {@code disrupted}
 * are skipped — no point staging another sabotage on a downed factory.
 *
 * <p>Risk is derived from {@link DefenseLevel} (planet's defense rating), then
 * softened one tier for stealth-leaning mission types (Sabotage, Extraction).
 * Payout scales with size × risk × per-type multiplier.
 */
public final class MissionGenerator {

    /** Cap on total emitted missions, keeps the tactical map readable on dense colonies. */
    private static final int MAX_MISSIONS = 6;

    private MissionGenerator() {}

    public static List<Mission> generate(PlanetAPI planet, Client client) {
        if (planet == null || client == null) return Collections.emptyList();

        // Debug client — the full MissionType × RiskLevel grid, no caps,
        // for playtesting. Gated upstream by DevConfig.DEBUG_CLIENT.
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

        // Industry-driven candidates: for each non-disrupted industry, pick one archetype.
        // Iterating intel.industries (not the catalog) preserves the order Starsector
        // returns from the market, giving stable positioning on revisit.
        for (IndustryEntry ind : intel.industries) {
            if (ind.disrupted) continue;
            List<MissionArchetype> archetypes = IndustryMissionCatalog.archetypesFor(ind.id);
            if (archetypes.isEmpty()) continue;
            MissionArchetype archetype = archetypes.get(r.nextInt(archetypes.size()));
            out.add(buildMission(r, planet, client, intel, ind, archetype, out.size()));
            if (out.size() >= MAX_MISSIONS) break;
        }

        return out;
    }

    /**
     * Emits the full {@link MissionType} × {@link RiskLevel} grid for the debug
     * client — 15 ordinary type/risk entries plus three swarm-rescue risk
     * entries. Bypasses {@link #MAX_MISSIONS} so every scenario is reachable
     * from a single planet.
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
        for (MissionType type : MissionType.values()) {
            for (RiskLevel risk : RiskLevel.values()) {
                int payout = computePayout(market.getSize(), risk, type, r);

                float x = 0.08f + r.nextFloat() * 0.84f;
                float y = 0.08f + r.nextFloat() * 0.84f;

                FlybyRoster clientSupport = rollFighterSupport(r, client.factionId, risk, Faction.MARINE);
                FlybyRoster enemySupport  = rollFighterSupport(r, client.factionId, risk, Faction.DEFENDER);

                int requiredDrops = requiredDropsFor(type, risk);
                if (com.dillon.starsectormarines.DevConfig.DROP_COUNT_OVERRIDE > 0) {
                    requiredDrops = com.dillon.starsectormarines.DevConfig.DROP_COUNT_OVERRIDE;
                }
                int employerShuttles = rollEmployerShuttles(r, risk, requiredDrops);
                String id = "debug:" + type.name() + ":" + risk.name() + ":" + index++;
                String name = type.name() + " — " + risk.name();
                String flavor = "DEBUG: " + type.name() + " at " + risk.name() + " risk.";

                out.add(new Mission(id, name, type, MissionSource.DEBUG,
                        payout, risk, requirementsFor(risk), flavor, x, y,
                        clientSupport, enemySupport, requiredDrops, employerShuttles,
                        planet.getName(), industryId));
            }
        }
        out.addAll(debugCivilianRescueMissions(
                planet.getName(), r, index));
        return out;
    }

    static List<Mission> debugCivilianRescueMissions(
            String planetName, Random random, int startIndex) {
        if (planetName == null || random == null) return Collections.emptyList();
        List<Mission> missions = new ArrayList<>(RiskLevel.values().length);
        int index = Math.max(0, startIndex);
        for (RiskLevel risk : RiskLevel.values()) {
            int requiredDrops = requiredDropsFor(MissionType.EXTRACTION, risk);
            if (com.dillon.starsectormarines.DevConfig.DROP_COUNT_OVERRIDE > 0) {
                requiredDrops = com.dillon.starsectormarines.DevConfig.DROP_COUNT_OVERRIDE;
            }
            int employerShuttles = rollEmployerShuttles(
                    random, risk, requiredDrops);
            float x = 0.08f + random.nextFloat() * 0.84f;
            float y = 0.08f + random.nextFloat() * 0.84f;
            String id = "debug:CIVILIAN_RESCUE:"
                    + risk.name() + ":" + index++;
            missions.add(new Mission(id,
                    "SWARM RESCUE — " + risk.name(),
                    MissionType.EXTRACTION,
                    MissionSource.DEBUG_CIVILIAN_RESCUE,
                    0, risk, requirementsFor(risk),
                    "DEBUG: evacuate the registered civilian cohort under "
                            + risk.name() + " swarm pressure.",
                    x, y, FlybyRoster.EMPTY, FlybyRoster.EMPTY,
                    requiredDrops, employerShuttles,
                    planetName, null, null,
                    -1L, -1L, -1,
                    CivilianEvacuationTracker.V1_REPRESENTATIVE_COUNT,
                    (byte) 0, (byte) 0, (byte) 100,
                    (byte) 0, (byte) 0, Collections.emptyList()));
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
                ? (int) Global.getSector().getClock().getDay() : 0;
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

        int requiredDrops = requiredDropsFor(missionType, risk);
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
        String flavor = BriefingComposer.compose(archetype, OfficerMoodReader.currentMood(),
                contractId, client.displayName, targetPlanetName, payoutFormatted, negotiatedPct);
        String id = assaultPhase != null
                ? PlanetaryAssaultMissionKey.encode(contractId, assaultPhase.index,
                        state.contractPhaseAttempts[row])
                : "contract:" + contractId;

        return new Mission(id, name, missionType, MissionSource.GENERATED,
                basePayout, risk, requirementsFor(risk), flavor, x, y,
                clientSupport, enemySupport, requiredDrops, employerShuttles,
                targetPlanetName, targetIndustryId, targetMarket.getFactionId(),
                contractId,
                missionSalvageBaseline,
                missionSalvageNegotiated,
                state.contractCashMultiplier[row],
                state.contractSalvageBaseline[row],
                state.contractSalvageNegotiated[row],
                employerPowers);
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

    private static Mission buildMission(Random r,
                                        PlanetAPI planet, Client client, PlanetIntel intel,
                                        IndustryEntry industry, MissionArchetype archetype,
                                        int index) {
        RiskLevel risk = deriveRisk(intel.defenseLevel, archetype.type);
        int payout = computePayout(intel.size, risk, archetype.type, r);

        float x = 0.08f + r.nextFloat() * 0.84f;
        float y = 0.08f + r.nextFloat() * 0.84f;

        FlybyRoster clientSupport = rollFighterSupport(r, client.factionId, risk, Faction.MARINE);
        FlybyRoster enemySupport  = rollFighterSupport(r, client.factionId, risk, Faction.DEFENDER);

        int requiredDrops = requiredDropsFor(archetype.type, risk);
        int employerShuttles = rollEmployerShuttles(r, risk, requiredDrops);
        String requirements = requirementsFor(risk);
        String id = client.factionId + ":" + industry.id + ":" + index;

        return new Mission(id, archetype.name, archetype.type, MissionSource.GENERATED,
                payout, risk, requirements, archetype.flavor, x, y,
                clientSupport, enemySupport, requiredDrops, employerShuttles,
                planet.getName(), industry.id);
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
     * Per-(type, risk) drop count. Drops feed marines onto the field via
     * shuttle cycling — with capacity-4 Aeroshuttles, drop count × 4 ≈ marines
     * on the field. CONQUEST gets the biggest commitments; SABOTAGE stays
     * smallest for covert flavor.
     */
    private static int requiredDropsFor(MissionType type, RiskLevel risk) {
        if (type == null || risk == null) return 3;
        switch (type) {
            case ASSAULT:
                switch (risk) { case LOW: return 5; case MEDIUM: return 13; case HIGH: return 25; }
                break;
            case SABOTAGE:
                switch (risk) { case LOW: return 3; case MEDIUM: return 6;  case HIGH: return 12; }
                break;
            case RAID:
                switch (risk) { case LOW: return 5; case MEDIUM: return 11; case HIGH: return 22; }
                break;
            case EXTRACTION:
                switch (risk) { case LOW: return 5; case MEDIUM: return 11; case HIGH: return 22; }
                break;
            case CONQUEST:
                switch (risk) { case LOW: return 6; case MEDIUM: return 18; case HIGH: return 40; }
                break;
        }
        return 3;
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
     * supplies the bulk of any non-trivial mission's lift; for playtesting
     * without a curated fleet, seed player transports via
     * {@link com.dillon.starsectormarines.DevConfig#DEBUG_SEED_PLAYER_VALKYRIES}.
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
