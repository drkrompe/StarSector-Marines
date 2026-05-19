package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
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
import com.fs.starfarer.api.campaign.PlanetAPI;

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
     * game), but never more than {@link #employerCoverageCap}. When
     * {@link com.dillon.starsectormarines.DevConfig#UNLIMITED_TRANSPORT} is
     * on, the cap is dropped entirely and the employer covers every drop —
     * useful for playtesting waves without fielding a player transport.
     */
    private static int rollEmployerShuttles(Random r, RiskLevel risk, int required) {
        if (com.dillon.starsectormarines.DevConfig.UNLIMITED_TRANSPORT) return required;
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
}
