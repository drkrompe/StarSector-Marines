package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.HousePromotion;
import com.dillon.starsectormarines.campaign.StakeLedger;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.Trait;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
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

    /** Industry disruption durations applied on victory, by mission type. */
    private static final float DISRUPT_DAYS_SABOTAGE   = 60f;
    private static final float DISRUPT_DAYS_RAID       = 30f;
    private static final float DISRUPT_DAYS_ASSAULT    = 90f;
    private static final float DISRUPT_DAYS_EXTRACTION = 0f;  // extraction doesn't damage infra

    /**
     * Damage points each mission type contributes to the per-industry counter.
     * The counter has to cross {@link #DAMAGE_THRESHOLD} before disruption actually
     * fires — so one successful sabotage softens a refinery but doesn't take it
     * offline. Sustained pressure (two sabotages, or one sabotage + two raids) does.
     */
    private static final int DAMAGE_SABOTAGE   = 2;
    private static final int DAMAGE_RAID       = 1;
    private static final int DAMAGE_ASSAULT    = 2;
    private static final int DAMAGE_EXTRACTION = 0;
    private static final int DAMAGE_THRESHOLD  = 4;

    /** Memory key prefix on the target {@link MarketAPI} for the per-industry counter. */
    private static final String DAMAGE_KEY_PREFIX = "$marines_industry_damage_";

    /**
     * Stake (0..255 byte-share) the patron seizes from the contract's target on a
     * victorious contract mission. ~8% of an industry — a seeded plurality is
     * ~110/255, so a backed patron flips one over a handful of strikes, while
     * autonomous drift would take many months (the decisive-accelerant principle,
     * {@code living-world/overview.md}). Tier-scaling (T2/T3 move more) is a later
     * refinement; STRIKE/RAID is the only generated contract today.
     */
    private static final int CONTRACT_STAKE_SEIZE = 20;

    /**
     * Promotion progress the patron accrues per victorious contract mission. T1→T2
     * threshold is 100 ({@link com.dillon.starsectormarines.campaign.HouseRank}),
     * so ~7 wins for a backed Baron to make Count — fast next to autonomous creep.
     */
    private static final int CONTRACT_PROMOTION_PROGRESS = 15;

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

        // Cash multiplier applies the salvage-traded-for-cash bump from briefing
        // acceptance (see contracts/overview.md §"Salvage Layer 2"). 100 = baseline.
        int cashMult = mission.cashMultiplier & 0xFF;
        if (cashMult <= 0) cashMult = 100;
        int payoutEarned = victory ? (int) ((long) mission.payout * cashMult / 100L) : 0;

        // Salvage entitlement carries into the (deferred) loot UI. For now it's
        // just the negotiated % — captain SALVAGE_EXPERT trait + fleet Salvage
        // Rig modifiers are layered on at the loot-roll step when that lands.
        int salvageEntitlement = victory ? (mission.salvageNegotiated & 0xFF) : 0;

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
                victory,
                mission.id, mission.name, mission.type, mission.source,
                payoutEarned, marinesEngaged, marinesLost,
                captain != null ? captain.id()   : null,
                captain != null ? captain.name() : null,
                priorStatus, newStatus, xpGained, injuredUntilDay, promotedTo,
                mission.targetPlanetName, mission.targetIndustryId,
                mission.contractId, salvageEntitlement);
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

        if (outcome.victory && outcome.targetIndustryId != null && outcome.targetPlanetName != null) {
            applyIndustryDisruption(outcome);
        }

        if (outcome.contractId != -1L) {
            applyContractBridge(outcome);
        }

        if (outcome.victory && outcome.missionSource == MissionSource.STORY
                && outcome.missionId != null) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            if (script != null) script.roster().markStoryComplete(outcome.missionId);
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

    /**
     * Charges the per-industry damage counter on the target market. If the new total
     * crosses {@link #DAMAGE_THRESHOLD}, fires {@code Industry.setDisrupted} for the
     * type-specific duration and resets the counter. Otherwise just saves the
     * incremented counter — no visible economic effect yet, but pressure accumulates
     * for the next op.
     *
     * <p>The counter lives in the market's {@code MemoryAPI} under a namespaced key,
     * so vanilla persists it across saves and per-planet locality is automatic.
     */
    private static void applyIndustryDisruption(MissionOutcome outcome) {
        if (Global.getSector() == null) return;
        int damageAdded = damagePointsFor(outcome.missionType);
        if (damageAdded <= 0) return;
        float disruptDays = disruptionDaysFor(outcome.missionType);
        if (disruptDays <= 0f) return;

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.getPrimaryEntity() == null) continue;
            if (!outcome.targetPlanetName.equals(market.getPrimaryEntity().getName())) continue;
            Industry industry = market.getIndustry(outcome.targetIndustryId);
            if (industry == null) return;

            String key = DAMAGE_KEY_PREFIX + outcome.targetIndustryId;
            MemoryAPI mem = market.getMemoryWithoutUpdate();
            int prior = mem.contains(key) ? mem.getInt(key) : 0;
            int total = prior + damageAdded;

            if (total >= DAMAGE_THRESHOLD) {
                // setDisrupted(days, useMax=true) stacks on existing disruption — never
                // shortens what's already there. Reset the counter on fire so subsequent
                // ops have to charge up again from zero.
                industry.setDisrupted(disruptDays, true);
                mem.set(key, 0);
                LOG.info("MarineOps: disrupted " + outcome.targetIndustryId
                        + " on " + outcome.targetPlanetName + " for " + disruptDays
                        + " days (damage " + prior + "+" + damageAdded + " ≥ "
                        + DAMAGE_THRESHOLD + ")");
            } else {
                mem.set(key, total);
                LOG.info("MarineOps: damaged " + outcome.targetIndustryId
                        + " on " + outcome.targetPlanetName
                        + " (" + total + "/" + DAMAGE_THRESHOLD + ")");
            }
            return;
        }
    }

    /**
     * Writes the mission's outcome back to the campaign-tier {@link CampaignState}:
     * advance {@code contractPhasesDone}, flip {@code contractState} on terminal
     * conditions, and tick the player↔patron rep row. No-ops if no campaign
     * script is registered yet (skeleton path — predates the campaign script's
     * install).
     *
     * <p>Resolution rules (per contracts/overview.md §Lifecycle):
     * <ul>
     *   <li>Victory advances {@code phasesDone}; on {@code phasesDone >= phasesTotal}
     *       the state flips ACTIVE/IN_PROGRESS → COMPLETED.</li>
     *   <li>Defeat flips the state to FAILED immediately (mission-mode contracts
     *       are short and terminal on failure; stationing failure paths are the
     *       lifecycle system's concern).</li>
     *   <li>First phase resolution transitions ACTIVE → IN_PROGRESS.</li>
     * </ul>
     */
    private static void applyContractBridge(MissionOutcome outcome) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) {
            LOG.info("MarineOps: contractId=" + outcome.contractId
                    + " but no CampaignStateScript registered — skipping campaign writeback");
            return;
        }
        CampaignState state = script.state();
        int row = state.contractIndex(outcome.contractId);
        if (row < 0) {
            LOG.info("MarineOps: contract " + outcome.contractId + " not found in campaign state — orphan mission?");
            return;
        }

        ContractState prior = ContractState.fromByte(state.contractState[row]);
        if (prior.isTerminal()) {
            LOG.info("MarineOps: contract " + outcome.contractId + " already " + prior + " — no writeback");
            return;
        }

        int day = currentDayInt();
        long patronId = state.contractPatronHouseId[row];

        // Leaving OFFERED → the offer window no longer applies. Clear to -1 so
        // any debug readout / future filter doesn't misinterpret the stale value.
        if (prior == ContractState.OFFERED) {
            state.contractOfferExpiresTick[row] = -1;
        }

        if (outcome.victory) {
            int phasesDone  = (state.contractPhasesDone[row] & 0xFF) + 1;
            int phasesTotal = state.contractPhasesTotal[row] & 0xFF;
            if (phasesDone > 255) phasesDone = 255;
            state.contractPhasesDone[row] = (byte) phasesDone;
            if (phasesDone >= phasesTotal) {
                state.contractState[row] = ContractState.COMPLETED.toByte();
                tickPatronRep(state, patronId, +1, day, true);
                LOG.info("MarineOps: contract " + outcome.contractId + " COMPLETED ("
                        + phasesDone + "/" + phasesTotal + ")");
            } else {
                state.contractState[row] = ContractState.IN_PROGRESS.toByte();
                LOG.info("MarineOps: contract " + outcome.contractId + " phase "
                        + phasesDone + "/" + phasesTotal + " done");
            }
            // Every victorious mission leaves a permanent mark on the political map:
            // the patron seizes ground from the target and climbs the rank ladder.
            applyPoliticalShift(state, row, outcome, day);
        } else {
            state.contractState[row] = ContractState.FAILED.toByte();
            tickPatronRep(state, patronId, -2, day, false);
            LOG.info("MarineOps: contract " + outcome.contractId + " FAILED");
        }
    }

    /**
     * Writes a victorious mission's result into the political simulation: the
     * patron seizes a slice of the struck industry from the contract's target and
     * accrues promotion progress. This is the Slice-B impact-ladder rung
     * ({@code living-world/overview.md}) — the first time player ops leave a
     * *permanent* mark on the houses graph rather than just on contract state.
     *
     * <p>The contested ground is the <em>target's</em> market + the struck
     * industry: patron and target are picked sector-wide by {@code ContractGenerator}
     * and usually sit on different markets, so the patron expands into the rival's
     * turf rather than consolidating a shared one. Market-local targeting (so
     * transfers contest a single market) is a {@code ContractGenerator} refinement
     * tracked for a later slice.
     *
     * <p>Mechanism lives in {@link StakeLedger#seizeShare} and
     * {@link HousePromotion#addProgressAndPromote}; the magnitudes are policy here
     * ({@link #CONTRACT_STAKE_SEIZE}, {@link #CONTRACT_PROMOTION_PROGRESS}). No-ops
     * cleanly when the contract has no target / industry (e.g. a debug-spawned or
     * non-strike contract).
     */
    private static void applyPoliticalShift(CampaignState state, int row, MissionOutcome outcome, int day) {
        long patronId = state.contractPatronHouseId[row];
        long targetId = state.contractTargetHouseId[row];
        if (patronId == -1L || targetId == -1L) return;
        if (outcome.targetIndustryId == null) return;

        int targetRow = state.houseIndex(targetId);
        if (targetRow < 0) return;
        int marketIdx   = state.houseMarketId[targetRow];
        // intern (not get): a strike can be the first time an industry enters the
        // registry — seeding only interns industries that existed at game start.
        // The patron then takes its foothold purely from the unclaimed remainder.
        int industryIdx = state.industryRegistry.intern(outcome.targetIndustryId);

        int gained = StakeLedger.seizeShare(state, targetId, patronId, marketIdx, industryIdx,
                CONTRACT_STAKE_SEIZE);

        int patronRow = state.houseIndex(patronId);
        int promotions = patronRow >= 0
                ? HousePromotion.addProgressAndPromote(state, patronRow, CONTRACT_PROMOTION_PROGRESS, day)
                : 0;

        LOG.info("MarineOps: political shift — patron " + patronId + " seized " + gained
                + "/255 of " + outcome.targetIndustryId + " from target " + targetId
                + (promotions > 0 ? " and promoted " + promotions + " rank(s)" : ""));
    }

    /** Find-or-create the patron's rep row and apply a small delta. */
    private static void tickPatronRep(CampaignState state, long patronHouseId,
                                      int repDelta, int day, boolean completed) {
        int repRow = state.ensureRepRow(patronHouseId);
        state.repValue[repRow] = Math.max(-100, Math.min(100, state.repValue[repRow] + repDelta));
        state.repLastContractTick[repRow] = day;
        if (completed) {
            int n = (state.repContractsCompleted[repRow] & 0xFFFF) + 1;
            if (n > 65535) n = 65535;
            state.repContractsCompleted[repRow] = (short) n;
        } else {
            int n = (state.repContractsFailed[repRow] & 0xFFFF) + 1;
            if (n > 65535) n = 65535;
            state.repContractsFailed[repRow] = (short) n;
        }
    }

    private static int damagePointsFor(MissionType type) {
        if (type == null) return 0;
        switch (type) {
            case SABOTAGE:   return DAMAGE_SABOTAGE;
            case RAID:       return DAMAGE_RAID;
            case ASSAULT:    return DAMAGE_ASSAULT;
            case EXTRACTION: return DAMAGE_EXTRACTION;
            default:         return 0;
        }
    }

    private static float disruptionDaysFor(MissionType type) {
        if (type == null) return 0f;
        switch (type) {
            case SABOTAGE:   return DISRUPT_DAYS_SABOTAGE;
            case RAID:       return DISRUPT_DAYS_RAID;
            case ASSAULT:    return DISRUPT_DAYS_ASSAULT;
            case EXTRACTION: return DISRUPT_DAYS_EXTRACTION;
            default:         return 0f;
        }
    }
}
