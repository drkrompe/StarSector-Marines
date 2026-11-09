package com.dillon.starsectormarines.campaign.raid;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.MarineLossesStatModifier;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.graid.GroundRaidObjectivePlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;

import java.util.List;

/**
 * Turns a fought raid's outcome into the raid the game's own command then commits ({@code raid-resolution.md}).
 *
 * <p>The mod never applies a raid's consequences itself. It adjusts the raid the player set up, which the raid
 * command keeps on the market as its {@link TempData}, and the command (vanilla's {@code MarketCMD}, or
 * Nexerelin's subclass of it) does the rest exactly as for a rolled raid: hostile timeout, military response,
 * cooldown, stability, reputation, loot, disruption, listeners and the post-raid rules.
 *
 * <ul>
 *   <li>A won battle commits the raid as it was set up, at the effectiveness the command already computed.</li>
 *   <li>A lost battle commits it with no objectives: the attack still happened, but it achieves nothing, takes no
 *       loot, disrupts nothing and reports no objective to the listeners.</li>
 *   <li>Either way the fleet's cargo marines did not fight, so the command's marine-loss roll is zeroed for that
 *       one confirm through {@link #LOSSES}, armed only around it. The company's own casualties are written back
 *       by Results, as for every operation.</li>
 * </ul>
 */
public final class RaidOutcomeCommit {

    /** Market memory key under which the raid command keeps the raid being set up (vanilla's and Nexerelin's). */
    public static final String TEMP_KEY = "$MarketCMD_temp";

    static final String LOSSES_SOURCE = "starsector_marines_company_raid";

    /** Zeroes the raid command's marine-loss roll while armed; added and removed around one confirm. */
    public static final MarineLossesStatModifier LOSSES = new CompanyFoughtLosses();

    private RaidOutcomeCommit() {}

    /** The raid being set up on {@code market}, or null when there is none. */
    public static TempData raidOn(MarketAPI market) {
        if (market == null) return null;
        Object temp = market.getMemoryWithoutUpdate().get(TEMP_KEY);
        return temp instanceof TempData data ? data : null;
    }

    /** Adjusts {@code temp} for the battle's outcome, before the raid command's confirm runs. */
    public static void prepare(TempData temp, boolean won) {
        prepare(temp, won, 1f);
    }

    /**
     * Adjusts {@code temp} for the battle's outcome. A won disrupting raid commits at the effectiveness the command
     * computed times the share of the works wrecked, so every machine wrecked adds days down
     * ({@code raid-mission.md}, Break); a raid for valuables commits as it was set up.
     */
    public static void prepare(TempData temp, boolean won, float worksShare) {
        if (temp == null) return;
        if (!won) {
            if (temp.objectives != null) temp.objectives.clear();
            return;
        }
        if (temp.raidType == RaidType.DISRUPT && worksShare > 0f && worksShare < 1f) {
            temp.raidMult *= worksShare;
        }
    }

    /**
     * The industry a disrupting raid targets, or null for any other raid. The intent is the raid's type: a raid for
     * valuables names no industry, even when the goods it takes come from one.
     */
    public static String targetIndustryId(TempData temp) {
        if (temp == null || temp.raidType != RaidType.DISRUPT) return null;
        if (temp.target != null) return temp.target.getId();
        if (temp.objectives == null) return null;
        for (GroundRaidObjectivePlugin objective : temp.objectives) {
            if (objective == null || objective.getMarinesAssigned() <= 0) continue;
            if (objective.getSource() != null) return objective.getSource().getId();
        }
        return null;
    }

    static final class CompanyFoughtLosses implements MarineLossesStatModifier {
        @Override
        public void modifyMarineLossesStatPreRaid(MarketAPI market, List<GroundRaidObjectivePlugin> objectives,
                                                  MutableStat stat) {
            if (stat != null) stat.modifyMultAlways(LOSSES_SOURCE, 0f, "Fought by the company");
        }
    }
}
