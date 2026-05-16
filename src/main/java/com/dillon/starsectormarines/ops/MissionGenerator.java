package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.fs.starfarer.api.campaign.PlanetAPI;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a deterministic mission list for a (planet, client) pair. Seeded
 * from {@code planet.getName() + client.factionId} so the same combination
 * always produces the same set — markers don't shuffle on relayout, and the
 * "missions for Hegemony at Jangala" feel stable between sessions.
 *
 * <p>Placement is currently a uniform random scatter in the interior of the
 * tactical map (10..90% on both axes to avoid edge clipping). The
 * texture-aware placement the user wants long-term goes here.
 *
 * <p>Names are hardcoded English templates for now — a future i18n pass
 * should move target nouns into strings.json and use template formatting.
 */
public final class MissionGenerator {

    /**
     * Total marine drops every mission needs to land the strike team. With
     * cycling, a single transport can cover all three by flying multiple
     * sorties — so this counts drops, not physical ships.
     */
    private static final int REQUIRED_DROPS = 3;

    private static final String[] ASSAULT_NAMES = {
            "Storm the Outpost",
            "Liberate Compound 7",
            "Capture Comm Array",
            "Breach the Spire",
    };
    private static final String[] SABOTAGE_NAMES = {
            "Disrupt the Refinery",
            "Demolish Power Conduits",
            "Cripple the Foundry",
            "Sabotage Listening Post",
    };
    private static final String[] RAID_NAMES = {
            "Plunder Supply Convoy",
            "Pillage the Warehouses",
            "Raid the Tithe Caravan",
            "Strip the Drydocks",
    };
    private static final String[] EXTRACTION_NAMES = {
            "Extract Defector",
            "Recover Lost Asset",
            "Pull the Survivor",
            "Smuggle Out Engineer",
    };

    private static final String[] ASSAULT_FLAVORS = {
            "Heavy resistance expected at the perimeter. Breach team will need to push fast or get pinned behind cover.",
            "Garrison estimated as company-grade. Anticipate hardened entry points and at least one fortified strongpoint inside.",
            "Defenders are dug in deep. Plan on prolonged engagement before reaching the objective; bring extra ammunition.",
            "Intel reports rotating patrols every twenty minutes. Strike between rotations or get caught in the response curve.",
    };
    private static final String[] SABOTAGE_FLAVORS = {
            "Stealth is the priority. Visible casualties will trigger lockdown protocols and pull reinforcements from the surrounding sector.",
            "Target infrastructure is networked; coordinated charges across multiple nodes are needed for full disruption.",
            "Security relies on automated systems. Expect surveillance drones and motion-triggered alarms throughout the facility.",
            "Charges must be placed and detonated within a thirty-minute window before the night shift comes on duty.",
    };
    private static final String[] RAID_FLAVORS = {
            "Cargo containers are scattered across the receiving yard. Prioritize high-value goods and get out before the response team mobilizes.",
            "Convoy schedule is predictable but escorts are heavy. A diversionary action elsewhere would significantly improve odds.",
            "Warehouses are lightly guarded but the alarm reaches local militia in under five minutes. In and out is the only viable plan.",
            "Most of the take will be in sealed containers — bring breaching tools or accept that half the score stays behind.",
    };
    private static final String[] EXTRACTION_FLAVORS = {
            "The asset is mobile but compromised. Approach window is narrow; arrive before the opposition consolidates their hold.",
            "Subject is held in a secured medical wing. Expect at least one armed escort during transit.",
            "Local sympathizers may help, but trust is cheap and the cordon tightens hourly. Move quickly.",
            "Extraction route depends on the dropship window. Miss it and the team is on foot through hostile territory.",
    };

    private MissionGenerator() {}

    public static List<Mission> generate(PlanetAPI planet, Client client) {
        long seed = ((planet != null ? planet.getName() : "") + ":" + client.factionId).hashCode();
        Random r = new Random(seed);

        int count = 3 + r.nextInt(3); // 3..5 missions
        List<Mission> out = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            MissionType type = MissionType.values()[r.nextInt(MissionType.values().length)];
            RiskLevel   risk = RiskLevel.values()[r.nextInt(RiskLevel.values().length)];

            String[] namePool;
            String[] flavorPool;
            switch (type) {
                case ASSAULT:    namePool = ASSAULT_NAMES;    flavorPool = ASSAULT_FLAVORS;    break;
                case SABOTAGE:   namePool = SABOTAGE_NAMES;   flavorPool = SABOTAGE_FLAVORS;   break;
                case RAID:       namePool = RAID_NAMES;       flavorPool = RAID_FLAVORS;       break;
                case EXTRACTION: namePool = EXTRACTION_NAMES; flavorPool = EXTRACTION_FLAVORS; break;
                default:         namePool = ASSAULT_NAMES;    flavorPool = ASSAULT_FLAVORS;
            }
            String name   = namePool[r.nextInt(namePool.length)];
            String flavor = flavorPool[r.nextInt(flavorPool.length)];

            int payout = (1 + r.nextInt(8)) * 5000;
            switch (risk) {
                case MEDIUM: payout = (int)(payout * 1.6f); break;
                case HIGH:   payout = (int)(payout * 2.4f); break;
                default: break;
            }

            String requirements = requirementsFor(risk);
            float x = 0.08f + r.nextFloat() * 0.84f;
            float y = 0.08f + r.nextFloat() * 0.84f;

            FlybyRoster clientSupport = rollFighterSupport(r, client.factionId, risk, Faction.MARINE);
            FlybyRoster enemySupport  = rollFighterSupport(r, client.factionId, risk, Faction.DEFENDER);

            int requiredDrops = REQUIRED_DROPS;
            int employerShuttles = rollEmployerShuttles(r, risk, requiredDrops);

            String id = client.factionId + ":" + i;
            out.add(new Mission(id, name, type, payout, risk, requirements, flavor, x, y,
                    clientSupport, enemySupport, requiredDrops, employerShuttles));
        }

        return out;
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
     * support scales with risk (higher-risk missions get more air involvement);
     * profile pool is faction-appropriate via {@link FighterProfile#poolForFaction}.
     *
     * <p>Marine-side support reads as "what the employer has loaned you";
     * defender-side support reads as "what the target faction can field." Both
     * use the same client faction id as the profile-pool source for now —
     * employer and target are typically separate factions, but until we plumb
     * a defender faction id through the mission we approximate by reusing the
     * client's pool. (Marine-friendly Wasps from a Tri-T employer raiding a
     * Hegemony depot will still feel fine — a touch of cross-pollination is
     * realistic for hired air support.)
     */
    private static FlybyRoster rollFighterSupport(Random r, String factionId, RiskLevel risk, Faction side) {
        // Base chance any support shows up at all per side.
        float chance = (side == Faction.MARINE) ? 0.55f : 0.4f;
        switch (risk) {
            case MEDIUM: chance += 0.10f; break;
            case HIGH:   chance += 0.20f; break;
            default: break;
        }
        if (r.nextFloat() > chance) return FlybyRoster.EMPTY;

        // 1-2 wings on low/medium risk, 1-3 on high.
        int maxWings = (risk == RiskLevel.HIGH) ? 3 : 2;
        int wingCount = 1 + r.nextInt(maxWings);

        List<FighterProfile> pool = FighterProfile.poolForFaction(factionId);
        List<FighterWing> wings = new ArrayList<>(wingCount);
        for (int i = 0; i < wingCount; i++) {
            FighterProfile profile = pool.get(r.nextInt(pool.size()));
            // Sorties scale with risk — high-risk missions get longer commitments.
            int sorties;
            switch (risk) {
                case HIGH:   sorties = 2 + r.nextInt(3); break; // 2-4
                case MEDIUM: sorties = 1 + r.nextInt(3); break; // 1-3
                default:     sorties = 1 + r.nextInt(2); break; // 1-2
            }
            // First arrival window keeps wings from all landing at once, even on the same side.
            float firstArrival = 5f + r.nextFloat() * 25f;
            float interval = 9f + r.nextFloat() * 9f;
            wings.add(new FighterWing(profile, side, sorties, firstArrival, interval));
        }
        return new FlybyRoster(wings);
    }

    /**
     * Rolls how many dropships the employer covers. Higher-risk missions tend
     * to come with more transport support (the client has more skin in the game
     * and arranges logistics); lower-risk gigs lean on the player to bring
     * their own. Sometimes the employer covers nothing — that's the case where
     * the mission turns into a real "you need your own fleet" gate.
     */
    private static int rollEmployerShuttles(Random r, RiskLevel risk, int required) {
        // Bias by risk: average coverage shifts up with risk so high-risk gigs
        // ship the marines for you more often.
        float roll = r.nextFloat();
        int coverage;
        switch (risk) {
            case HIGH:
                // 30% full coverage, 30% partial, 40% none-to-half.
                if (roll < 0.30f)      coverage = required;
                else if (roll < 0.60f) coverage = required - 1;
                else                   coverage = r.nextInt(required);
                break;
            case MEDIUM:
                // Skewed slightly toward partial.
                if (roll < 0.20f)      coverage = required;
                else if (roll < 0.55f) coverage = required - 1;
                else                   coverage = r.nextInt(required);
                break;
            default: // LOW
                // Player usually fields their own.
                if (roll < 0.10f)      coverage = required;
                else if (roll < 0.35f) coverage = required - 1;
                else                   coverage = r.nextInt(required);
                break;
        }
        return Math.max(0, Math.min(coverage, required));
    }
}
