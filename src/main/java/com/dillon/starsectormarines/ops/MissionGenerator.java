package com.dillon.starsectormarines.ops;

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

    private MissionGenerator() {}

    public static List<Mission> generate(PlanetAPI planet, Client client) {
        long seed = ((planet != null ? planet.getName() : "") + ":" + client.factionId).hashCode();
        Random r = new Random(seed);

        int count = 3 + r.nextInt(3); // 3..5 missions
        List<Mission> out = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            MissionType type = MissionType.values()[r.nextInt(MissionType.values().length)];
            RiskLevel   risk = RiskLevel.values()[r.nextInt(RiskLevel.values().length)];

            String[] pool;
            switch (type) {
                case ASSAULT:    pool = ASSAULT_NAMES;    break;
                case SABOTAGE:   pool = SABOTAGE_NAMES;   break;
                case RAID:       pool = RAID_NAMES;       break;
                case EXTRACTION: pool = EXTRACTION_NAMES; break;
                default:         pool = ASSAULT_NAMES;
            }
            String name = pool[r.nextInt(pool.length)];

            int payout = (1 + r.nextInt(8)) * 5000;
            switch (risk) {
                case MEDIUM: payout = (int)(payout * 1.6f); break;
                case HIGH:   payout = (int)(payout * 2.4f); break;
                default: break;
            }

            String requirements = requirementsFor(risk);
            float x = 0.08f + r.nextFloat() * 0.84f;
            float y = 0.08f + r.nextFloat() * 0.84f;

            String id = client.factionId + ":" + i;
            out.add(new Mission(id, name, type, payout, risk, requirements, x, y));
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
}
