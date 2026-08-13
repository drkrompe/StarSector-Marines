package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationReport;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.engine.ecs.ArchetypeTable;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.CivilianRescueMissionResolution;
import com.dillon.starsectormarines.campaign.ChainIntervention;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractImpactPolicy;
import com.dillon.starsectormarines.campaign.ContractReputation;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.GarrisonDefenseMissionKey;
import com.dillon.starsectormarines.campaign.GarrisonDefensePayload;
import com.dillon.starsectormarines.campaign.GarrisonDefenseResolution;
import com.dillon.starsectormarines.campaign.HousePromotion;
import com.dillon.starsectormarines.campaign.PlanetaryAssaultResolution;
import com.dillon.starsectormarines.campaign.PlanetaryAssaultMissionKey;
import com.dillon.starsectormarines.campaign.StakeLedger;
import com.dillon.starsectormarines.campaign.StationingIncidentMissionKey;
import com.dillon.starsectormarines.campaign.StationingIncidentPayload;
import com.dillon.starsectormarines.campaign.StationingIncidentResolution;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.Status;
import com.dillon.starsectormarines.marine.Trait;
import com.dillon.starsectormarines.ops.loot.LootRecoveryModifier;
import com.dillon.starsectormarines.ops.loot.LootRecoveryModifiers;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import org.apache.log4j.Logger;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.dillon.starsectormarines.marine.MarineSoldierStatus;

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
     * refinement.
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
        return compute(sim, mission, captain, Collections.emptySet());
    }

    public static MissionOutcome compute(BattleSimulation sim, Mission mission,
                                         MarineCaptain captain,
                                         Set<String> deployedFireteamIds) {
        boolean victory = sim.getWinner() == Faction.MARINE;

        // Casualty tally without the legacy units list: survivors come from the
        // dense registry (live-only), casualties from the corpse home — the
        // corpse archetype in the battle EntityWorld retains every death for the
        // whole battle and carries the dead unit's faction (IDENTITY column). A
        // deboarded marine is in exactly one of the two (live registry or a
        // corpse), so engaged = survivors + casualties.
        UnitRosterService roster = sim.getRoster();
        BattleComponents c = sim.getBattleComponents();
        int marinesAlive = 0;
        Set<String> survivingSoldierIds = new HashSet<>();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long unit = roster.get(i);
            if (roster.identity().faction(unit) == Faction.MARINE) {
                marinesAlive++;
                String id = (String) sim.getEntityWorld().getObject(unit, c.IDENTITY,
                        BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID);
                if (id != null) survivingSoldierIds.add(id);
            }
        }
        int rawMarinesLost = 0;
        Set<String> fallenSoldierIds = new HashSet<>();
        for (ArchetypeTable t : sim.getEntityWorld().matched(c.corpses)) {
            Object[] factions = t.objects(c.IDENTITY, BattleComponents.IDENTITY_FACTION).array();
            Object[] soldierIds = t.objects(c.IDENTITY,
                    BattleComponents.IDENTITY_CAMPAIGN_SOLDIER_ID).array();
            for (int r = 0, n = t.rowCount(); r < n; r++) {
                if (factions[r] == Faction.MARINE) {
                    rawMarinesLost++;
                    String id = (String) soldierIds[r];
                    if (id != null) fallenSoldierIds.add(id);
                }
            }
        }
        int marinesEngaged = marinesAlive + rawMarinesLost;

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
        // Freeze the negotiated claim and current Layer-3 recovery modifiers so
        // reopening Results cannot change the manifest with later fleet edits.
        int salvageEntitlement = victory ? (mission.salvageNegotiated & 0xFF) : 0;
        LootRecoveryModifier recoveryModifier = victory
                ? LootRecoveryModifiers.resolve(captain)
                : LootRecoveryModifier.NONE;

        int civiliansRescued = -1;
        int evacuationRepresentatives = -1;
        int representativesEvacuated = -1;
        if (mission.source.isCivilianRescue()) {
            CivilianEvacuationReport report =
                    sim.getCivilianEvacuationTracker().report();
            if (report != null) {
                evacuationRepresentatives = report.initial;
                representativesEvacuated = report.evacuated;
                civiliansRescued = report.campaignRescued(
                        mission.civiliansAtRisk);
            }
        }

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
                mission.id, mission.name, mission.type, mission.risk, mission.source,
                mission.payout, payoutEarned, marinesEngaged, marinesLost,
                captain != null ? captain.id()   : null,
                captain != null ? captain.name() : null,
                priorStatus, newStatus, xpGained, injuredUntilDay, promotedTo,
                mission.targetPlanetName, mission.targetIndustryId, mission.targetFactionId,
                mission.contractId, mission.campaignEventId,
                mission.campaignEventMarketId, mission.civiliansAtRisk,
                civiliansRescued,
                evacuationRepresentatives, representativesEvacuated,
                salvageEntitlement,
                recoveryModifier.recoveryBonusPct, recoveryModifier.highValueChancePct,
                survivingSoldierIds, fallenSoldierIds, deployedFireteamIds);
    }

    public static void apply(MissionOutcome outcome) {
        if (outcome == null) return;
        if (outcome.missionSource.isDebug()) {
            LOG.info("MarineOps: debug mission " + outcome.missionId
                    + " — no campaign writeback");
            return;
        }
        if (outcome.missionSource == MissionSource.STATIONING
                && !isCurrentStationingMission(outcome)) {
            LOG.info("MarineOps: stale stationing mission result " + outcome.missionId
                    + " — no writeback");
            return;
        }
        if (outcome.missionSource == MissionSource.CAMPAIGN_EVENT) {
            CampaignStateScript script = CampaignStateScript.getInstance();
            CivilianRescueMissionResolution.Result result =
                    CivilianRescueMissionResolution.apply(
                            script != null ? script.state() : null,
                            outcome, currentDayInt());
            LOG.info("MarineOps: campaign event " + outcome.missionId
                    + " → " + result);
            if (result != CivilianRescueMissionResolution.Result.RESOLVED) {
                return;
            }
        }
        CargoAPI cargo = Global.getSector() != null && Global.getSector().getPlayerFleet() != null
                ? Global.getSector().getPlayerFleet().getCargo()
                : null;

        if (cargo != null) {
            if (outcome.payoutEarned > 0) {
                cargo.getCredits().add(outcome.payoutEarned);
            }
            // Named persistent marines left the generic cargo pool when enlisted;
            // their battlefield fate is written to the roster below, not charged
            // a second time against unassigned cargo personnel.
        }

        if (outcome.victory && outcome.targetIndustryId != null && outcome.targetPlanetName != null) {
            applyIndustryDisruption(outcome);
        }

        if (outcome.contractId != -1L) {
            applyContractBridge(outcome);
        }

        MarineRosterScript personnelScript = MarineRosterScript.getInstance();
        if (personnelScript != null) {
            int survivorXp = outcome.victory ? switch (outcome.risk) {
                case LOW -> 30;
                case MEDIUM -> 50;
                case HIGH -> 80;
            } : 10;
            float wiaDays = switch (outcome.risk) {
                case LOW -> 7f;
                case MEDIUM -> 12f;
                case HIGH -> 18f;
            };
            personnelScript.roster().applySoldierOutcome(
                    resolvePersonnelOutcomes(outcome), survivorXp, currentDayInt(), wiaDays);
            if (outcome.victory) {
                int materials = switch (outcome.risk) {
                    case LOW -> 2;
                    case MEDIUM -> 4;
                    case HIGH -> 7;
                };
                personnelScript.roster().armory().recordVictory(
                        materials, outcome.risk == RiskLevel.HIGH);
            }
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

    /** Deterministic fate roll: a battlefield casualty can be WIA or MIA, not only KIA. */
    static Map<String, MarineSoldierStatus> resolvePersonnelOutcomes(MissionOutcome outcome) {
        Map<String, MarineSoldierStatus> result = new HashMap<>();
        if (outcome == null) return result;
        for (String id : outcome.survivingSoldierIds) {
            result.put(id, MarineSoldierStatus.ACTIVE);
        }
        for (String id : outcome.fallenSoldierIds) {
            long seed = ((long) (outcome.missionId != null ? outcome.missionId.hashCode() : 0) << 32)
                    ^ id.hashCode();
            float roll = new Random(seed).nextFloat();
            MarineSoldierStatus status;
            if (outcome.victory) {
                status = roll < 0.35f ? MarineSoldierStatus.KIA
                        : roll < 0.95f ? MarineSoldierStatus.WIA
                        : MarineSoldierStatus.MIA;
            } else {
                status = roll < 0.50f ? MarineSoldierStatus.KIA
                        : roll < 0.80f ? MarineSoldierStatus.WIA
                        : MarineSoldierStatus.MIA;
            }
            result.put(id, status);
        }
        return result;
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
        ContractType contractType = ContractType.fromByte(state.contractType[row]);

        if (outcome.missionSource == MissionSource.STATIONING) {
            boolean captainUnavailable = outcome.newCaptainStatus != null
                    && outcome.newCaptainStatus != Status.GARRISONED;
            StationingIncidentMissionKey incidentKey = StationingIncidentMissionKey.parse(
                    outcome.missionId);
            if (incidentKey != null) {
                StationingIncidentResolution.Result result = StationingIncidentResolution.apply(
                        state, outcome.contractId, incidentKey.dueDay, incidentKey.type,
                        outcome.marinesLost, captainUnavailable, day);
                LOG.info("MarineOps: Cadre incident " + outcome.missionId + " → " + result);
                return;
            }
            GarrisonDefenseMissionKey defenseKey = GarrisonDefenseMissionKey.parse(
                    outcome.missionId);
            GarrisonDefenseResolution.Result result = defenseKey != null
                    ? GarrisonDefenseResolution.apply(state, outcome.contractId,
                            defenseKey.eventKey, outcome.marinesLost,
                            captainUnavailable, outcome.victory)
                    : null;
            if (result == GarrisonDefenseResolution.Result.ASSIGNMENT_FAILED) {
                ContractReputation.failed(state, patronId, -1, day);
            }
            LOG.info("MarineOps: Garrison defense " + outcome.missionId + " → " + result);
            return;
        }

        // Leaving OFFERED → the offer window no longer applies. Clear to -1 so
        // any debug readout / future filter doesn't misinterpret the stale value.
        if (prior == ContractState.OFFERED) {
            state.contractOfferExpiresTick[row] = -1;
        }

        if (contractType == ContractType.PLANETARY_ASSAULT) {
            applyPlanetaryAssaultBridge(state, row, outcome, patronId, day);
            return;
        }

        if (outcome.victory) {
            int phasesDone  = (state.contractPhasesDone[row] & 0xFF) + 1;
            int phasesTotal = state.contractPhasesTotal[row] & 0xFF;
            if (phasesDone > 255) phasesDone = 255;
            state.contractPhasesDone[row] = (byte) phasesDone;
            if (phasesDone >= phasesTotal) {
                state.contractState[row] = ContractState.COMPLETED.toByte();
                if (contractType != ContractType.EXTRACTION) {
                    ContractReputation.completed(state, patronId, +1, day);
                }
                LOG.info("MarineOps: contract " + outcome.contractId + " COMPLETED ("
                        + phasesDone + "/" + phasesTotal + ")");
            } else {
                state.contractState[row] = ContractState.IN_PROGRESS.toByte();
                LOG.info("MarineOps: contract " + outcome.contractId + " phase "
                        + phasesDone + "/" + phasesTotal + " done");
            }
            // Every victorious mission leaves a permanent mark on the political map:
            // the patron seizes ground from the target and climbs the rank ladder.
            if (contractType != ContractType.EXTRACTION) {
                applyPoliticalShift(state, row, outcome, day);
            }
            if (ContractState.fromByte(state.contractState[row]) == ContractState.COMPLETED) {
                ChainIntervention.stopOpposedChain(state, row, day);
            }
        } else {
            state.contractState[row] = ContractState.FAILED.toByte();
            if (contractType != ContractType.EXTRACTION) {
                ContractReputation.failed(state, patronId, -2, day);
            }
            LOG.info("MarineOps: contract " + outcome.contractId + " FAILED");
        }
    }

    private static boolean isCurrentStationingMission(MissionOutcome outcome) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return false;
        CampaignState state = script.state();
        StationingIncidentMissionKey incidentKey = StationingIncidentMissionKey.parse(
                outcome.missionId);
        if (incidentKey != null && incidentKey.contractId == outcome.contractId) {
            StationingIncidentPayload payload = StationingIncidentPayload.from(
                    state, outcome.contractId);
            return payload != null && payload.dueDay == incidentKey.dueDay
                    && payload.type == incidentKey.type;
        }
        GarrisonDefenseMissionKey defenseKey = GarrisonDefenseMissionKey.parse(
                outcome.missionId);
        if (defenseKey == null || defenseKey.contractId != outcome.contractId) return false;
        GarrisonDefensePayload payload = GarrisonDefensePayload.from(
                state, outcome.contractId);
        return payload != null && payload.eventKey == defenseKey.eventKey;
    }

    private static void applyPlanetaryAssaultBridge(CampaignState state, int row,
                                                     MissionOutcome outcome,
                                                     long patronId, int day) {
        PlanetaryAssaultResolution.Result result = PlanetaryAssaultResolution.apply(
                state, row, outcome.victory,
                phaseIndex(outcome), phaseAttempt(outcome), day);
        if (result == null) {
            LOG.info("MarineOps: invalid Planetary Assault state for contract "
                    + outcome.contractId + " — no writeback");
            return;
        }
        switch (result) {
            case CONTRACT_COMPLETED:
                ContractReputation.completed(state, patronId, +1, day);
                applyPoliticalShift(state, row, outcome, day);
                LOG.info("MarineOps: Planetary Assault " + outcome.contractId + " COMPLETED");
                break;
            case PHASE_ADVANCED:
                applyPoliticalShift(state, row, outcome, day);
                LOG.info("MarineOps: Planetary Assault " + outcome.contractId
                        + " advanced to phase " + ((state.contractPhasesDone[row] & 0xFF) + 1));
                break;
            case PHASE_REROLLED:
                LOG.info("MarineOps: Planetary Assault " + outcome.contractId
                        + " rerolling phase " + ((state.contractPhasesDone[row] & 0xFF) + 1)
                        + " attempt " + state.contractPhaseAttempts[row]);
                break;
            case CONTRACT_FAILED:
                ContractReputation.failed(state, patronId, -2, day);
                LOG.info("MarineOps: Planetary Assault " + outcome.contractId + " FAILED");
                break;
            default:
                break;
        }
    }

    private static int phaseIndex(MissionOutcome outcome) {
        PlanetaryAssaultMissionKey key = PlanetaryAssaultMissionKey.parse(outcome.missionId);
        return key != null && key.contractId == outcome.contractId ? key.phaseIndex : -1;
    }

    private static int phaseAttempt(MissionOutcome outcome) {
        PlanetaryAssaultMissionKey key = PlanetaryAssaultMissionKey.parse(outcome.missionId);
        return key != null && key.contractId == outcome.contractId ? key.attempt : -1;
    }

    /**
     * Writes a victorious mission's result into the political simulation: the
     * patron accrues promotion progress; territorial contract types also seize a
     * slice of the struck industry from the target. This is the Slice-B impact-ladder rung
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
     * cleanly when the contract has no target / industry.
     */
    private static void applyPoliticalShift(CampaignState state, int row, MissionOutcome outcome, int day) {
        long patronId = state.contractPatronHouseId[row];
        long targetId = state.contractTargetHouseId[row];
        if (patronId == -1L) return;

        ContractType contractType = ContractType.fromByte(state.contractType[row]);
        int gained = 0;
        if (ContractImpactPolicy.transfersIndustryStake(contractType)
                && targetId != -1L && outcome.targetIndustryId != null) {
            int targetRow = state.houseIndex(targetId);
            if (targetRow >= 0) {
                int marketIdx = state.houseMarketId[targetRow];
                // Intern (not get): a strike can be the first time an industry
                // enters the registry. The patron takes its foothold from the
                // target's share or the unclaimed remainder.
                int industryIdx = state.industryRegistry.intern(outcome.targetIndustryId);
                gained = StakeLedger.seizeShare(state, targetId, patronId, marketIdx, industryIdx,
                        CONTRACT_STAKE_SEIZE);
            }
        }

        int patronRow = state.houseIndex(patronId);
        int promotions = patronRow >= 0
                ? HousePromotion.addProgressAndPromote(state, patronRow, CONTRACT_PROMOTION_PROGRESS, day)
                : 0;

        LOG.info("MarineOps: political shift — patron " + patronId
                + (gained > 0 ? " seized " + gained + "/255 of "
                    + outcome.targetIndustryId + " from target " + targetId : " completed " + contractType)
                + (promotions > 0 ? " and promoted " + promotions + " rank(s)" : ""));
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
