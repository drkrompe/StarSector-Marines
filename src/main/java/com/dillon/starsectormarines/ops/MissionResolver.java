package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.Unit;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.Trait;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import org.apache.log4j.Logger;

import java.text.MessageFormat;
import java.util.Random;

/**
 * Turns a finished {@link BattleSimulation} + the briefing's selected
 * {@link Mission} and {@link MarineCaptain} into a {@link MissionOutcome},
 * then applies the outcome to the player's game state (credits, cargo marines,
 * captain XP/status/rank, commendation log).
 *
 * <p>{@link #compute} is deterministic — same inputs, same outcome — by seeding
 * the wipe-fate roll from the captain id + mission id. {@link #apply} is the
 * side-effectful counterpart that mutates the sector.
 *
 * <p>Ruleset (v2):
 * <ul>
 *   <li>Victory: full payout, XP = payout/100 with a light malus when casualties
 *       are heavy (3+ marines lost). NATURAL_LEADER multiplies XP by 1.5x.
 *       FIELD_MEDIC reduces marines-lost by 25% (rounded down).</li>
 *   <li>Defeat with survivors: captain INJURED for {@value #INJURED_DAYS} in-game days.</li>
 *   <li>Defeat total wipe: 60% KIA, 40% INJURED for {@value #WIPE_INJURED_DAYS}
 *       days (long recovery, "missing in action — extracted later"). Deterministic
 *       per (captain, mission). Captured branch deferred until CAPTURED status lands.</li>
 *   <li>XP crossing a rank threshold auto-promotes (cascades if a single mission
 *       skips a tier).</li>
 * </ul>
 */
public final class MissionResolver {

    private static final Logger LOG = Global.getLogger(MissionResolver.class);

    private static final float INJURED_DAYS = 7f;
    private static final float WIPE_INJURED_DAYS = 45f;
    private static final float WIPE_KIA_CHANCE = 0.60f;
    private static final float FIELD_MEDIC_REDUCTION = 0.25f;
    private static final float NATURAL_LEADER_MULT = 1.5f;

    private MissionResolver() {}

    public static MissionOutcome compute(BattleSimulation sim, Mission mission, MarineCaptain captain) {
        boolean victory = sim.getWinner() == Faction.MARINE;

        int marinesEngaged = 0;
        int rawMarinesLost = 0;
        for (Unit u : sim.getUnits()) {
            if (u.faction != Faction.MARINE) continue;
            marinesEngaged++;
            if (!u.isAlive()) rawMarinesLost++;
        }

        boolean hasFieldMedic = captain != null && captain.traits().contains(Trait.FIELD_MEDIC);
        int marinesLost = hasFieldMedic
                ? (int) Math.floor(rawMarinesLost * (1f - FIELD_MEDIC_REDUCTION))
                : rawMarinesLost;

        int payoutEarned = victory ? mission.payout : 0;

        Status priorStatus = captain != null ? captain.status() : null;
        Status newStatus   = priorStatus;
        int   xpGained        = 0;
        float injuredUntilDay = 0f;
        Rank  promotedTo      = null;

        if (captain != null) {
            if (victory) {
                xpGained = marinesLost <= 2
                        ? payoutEarned / 100
                        : payoutEarned / 150;
                if (captain.traits().contains(Trait.NATURAL_LEADER)) {
                    xpGained = (int) (xpGained * NATURAL_LEADER_MULT);
                }
                promotedTo = simulatePromotion(captain.rank(), captain.xp(), xpGained);
            } else {
                float currentDay = Global.getSector() != null
                        ? Global.getSector().getClock().getDay()
                        : 0f;
                if (marinesLost >= marinesEngaged) {
                    // FoB overrun — roll fate. Deterministic per (captain, mission) so
                    // the result doesn't change if compute is called twice (e.g. preview).
                    long seed = ((long) captain.id().hashCode() << 32) ^ mission.id.hashCode();
                    Random r = new Random(seed);
                    if (r.nextFloat() < WIPE_KIA_CHANCE) {
                        newStatus = Status.KIA;
                    } else {
                        newStatus = Status.INJURED;
                        injuredUntilDay = currentDay + WIPE_INJURED_DAYS;
                    }
                } else {
                    newStatus = Status.INJURED;
                    injuredUntilDay = currentDay + INJURED_DAYS;
                }
            }
        }

        return new MissionOutcome(
                victory, mission.name,
                payoutEarned, marinesEngaged, marinesLost,
                captain != null ? captain.id()   : null,
                captain != null ? captain.name() : null,
                priorStatus, newStatus, xpGained, injuredUntilDay, promotedTo);
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
                    int day = currentDayInt();
                    if (outcome.xpGained > 0) {
                        captain.addXp(outcome.xpGained);
                        // Mirror compute's promotion logic against the live captain so
                        // rank advances in lockstep with the displayed outcome.
                        while (captain.rank() != Rank.GENERAL
                                && captain.xp() >= captain.rank().xpToNext()) {
                            captain.addXp(-captain.rank().xpToNext());
                            Rank next = captain.rank().promote();
                            captain.setRank(next);
                            captain.commendations().add(MessageFormat.format(
                                    "Day {0}: Promoted to {1}.", day, next.displayName()));
                        }
                    }
                    if (outcome.newCaptainStatus != null
                            && outcome.newCaptainStatus != outcome.priorCaptainStatus) {
                        captain.setStatus(outcome.newCaptainStatus);
                        if (outcome.newCaptainStatus == Status.INJURED) {
                            captain.setInjuredUntilDay(outcome.injuredUntilDay);
                        }
                    }
                    appendOutcomeCommendation(captain, outcome, day);
                }
            }
        }

        LOG.info("MarineOps: applied outcome — victory=" + outcome.victory
                + " payout=" + outcome.payoutEarned
                + " losses=" + outcome.marinesLost + "/" + outcome.marinesEngaged
                + " xp=" + outcome.xpGained
                + " captainStatus=" + outcome.newCaptainStatus
                + " promotedTo=" + outcome.promotedTo);
    }

    /**
     * Simulates the rank a captain will end at after gaining {@code xpGained} on top
     * of {@code currentXp}. Returns null when no promotion crosses a threshold.
     */
    private static Rank simulatePromotion(Rank startRank, int currentXp, int xpGained) {
        Rank rank = startRank;
        int xp = currentXp + xpGained;
        while (rank != Rank.GENERAL && xp >= rank.xpToNext()) {
            xp -= rank.xpToNext();
            rank = rank.promote();
        }
        return rank != startRank ? rank : null;
    }

    private static void appendOutcomeCommendation(MarineCaptain captain, MissionOutcome outcome, int day) {
        if (outcome.victory) {
            captain.commendations().add(MessageFormat.format(
                    "Day {0}: Led successful op — {1}.", day, outcome.missionName));
        } else if (outcome.newCaptainStatus == Status.KIA) {
            captain.commendations().add(MessageFormat.format(
                    "Day {0}: Killed in action — {1}.", day, outcome.missionName));
        } else if (outcome.newCaptainStatus == Status.INJURED
                && outcome.injuredUntilDay - day >= WIPE_INJURED_DAYS - 1f) {
            // Long recovery only happens on a wipe-survived roll.
            captain.commendations().add(MessageFormat.format(
                    "Day {0}: Survived FoB overrun at {1}; extracted with serious wounds.",
                    day, outcome.missionName));
        }
    }

    private static int currentDayInt() {
        return Global.getSector() != null
                ? (int) Global.getSector().getClock().getDay()
                : 0;
    }
}
