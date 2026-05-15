package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Status;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.apache.log4j.Logger;

/**
 * Turns a finished {@link BattleSimulation} + the briefing's selected
 * {@link Mission} and {@link MarineCaptain} into a {@link MissionOutcome},
 * then applies the outcome to the player's game state (credits, cargo marines,
 * captain XP/status).
 *
 * <p>{@link #compute} is pure — same inputs, same outcome — so the RESULTS
 * screen could preview it without committing. {@link #apply} is the
 * side-effectful counterpart that mutates the sector.
 *
 * <p>Placeholder ruleset (v1):
 * <ul>
 *   <li>Victory: full payout, XP = payout/100 with a light malus when casualties
 *       are heavy (3+ marines lost).</li>
 *   <li>Defeat with survivors: captain INJURED for 7 in-game days, no payout, no XP.</li>
 *   <li>Defeat total wipe: captain KIA, no payout, no XP.</li>
 *   <li>Cargo marines decremented by exact casualty count.</li>
 * </ul>
 */
public final class MissionResolver {

    private static final Logger LOG = Global.getLogger(MissionResolver.class);

    private static final float INJURED_DAYS = 7f;

    private MissionResolver() {}

    public static MissionOutcome compute(BattleSimulation sim, Mission mission, MarineCaptain captain) {
        boolean victory = sim.getWinner() == Faction.MARINE;

        int marinesEngaged = 0;
        int marinesLost    = 0;
        for (Unit u : sim.getUnits()) {
            if (u.faction != Faction.MARINE) continue;
            marinesEngaged++;
            if (!u.isAlive()) marinesLost++;
        }

        int payoutEarned = victory ? mission.payout : 0;

        Status priorStatus = captain != null ? captain.status() : null;
        Status newStatus   = priorStatus;
        int   xpGained        = 0;
        float injuredUntilDay = 0f;

        if (captain != null) {
            if (victory) {
                xpGained = marinesLost <= 2
                        ? payoutEarned / 100
                        : payoutEarned / 150;
            } else {
                if (marinesLost >= marinesEngaged) {
                    newStatus = Status.KIA;
                } else {
                    newStatus = Status.INJURED;
                    float currentDay = Global.getSector() != null
                            ? Global.getSector().getClock().getDay()
                            : 0f;
                    injuredUntilDay = currentDay + INJURED_DAYS;
                }
            }
        }

        return new MissionOutcome(
                victory, mission.name,
                payoutEarned, marinesEngaged, marinesLost,
                captain != null ? captain.id()   : null,
                captain != null ? captain.name() : null,
                priorStatus, newStatus, xpGained, injuredUntilDay);
    }

    public static void apply(MissionOutcome outcome) {
        CargoAPI cargo = Global.getSector() != null && Global.getSector().getPlayerFleet() != null
                ? Global.getSector().getPlayerFleet().getCargo()
                : null;

        if (cargo != null) {
            if (outcome.payoutEarned > 0) {
                cargo.getCredits().add(outcome.payoutEarned);
            }
            if (outcome.marinesLost > 0) {
                cargo.removeCommodity(Commodities.MARINES, outcome.marinesLost);
            }
        }

        if (outcome.captainId != null) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            if (script != null) {
                MarineCaptain captain = script.roster().byId(outcome.captainId);
                if (captain != null) {
                    if (outcome.xpGained > 0) {
                        captain.addXp(outcome.xpGained);
                    }
                    if (outcome.newCaptainStatus != null
                            && outcome.newCaptainStatus != outcome.priorCaptainStatus) {
                        captain.setStatus(outcome.newCaptainStatus);
                        if (outcome.newCaptainStatus == Status.INJURED) {
                            captain.setInjuredUntilDay(outcome.injuredUntilDay);
                        }
                    }
                }
            }
        }

        LOG.info("MarineOps: applied outcome — victory=" + outcome.victory
                + " payout=" + outcome.payoutEarned
                + " losses=" + outcome.marinesLost + "/" + outcome.marinesEngaged
                + " xp=" + outcome.xpGained
                + " captainStatus=" + outcome.newCaptainStatus);
    }
}
