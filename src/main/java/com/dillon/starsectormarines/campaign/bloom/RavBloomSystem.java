package com.dillon.starsectormarines.campaign.bloom;

import com.dillon.starsectormarines.campaign.CampaignEventState;
import com.dillon.starsectormarines.campaign.CampaignEventType;
import com.dillon.starsectormarines.campaign.CampaignEvents;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.CampaignTable;
import com.dillon.starsectormarines.campaign.systems.CivilianRescueSpawnSystem;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The Rav Bloom's clock on the campaign layer ({@code colony-breach-nouns.md}, Campaign: blooms).
 *
 * <p>Once a week it may wake one bloom over a reservoir and lets each dark bloom try to seed a
 * neighbour; every day it walks each running bloom through its stages. A bloom's distress call is
 * the Civilian Rescue event, raised through the shared open-event gate, so this is the only source
 * of rescues and not a second event scheduler ({@code campaign-event-nouns.md}). The rescue's own
 * terminal state, and the mother-bore report the battle leaves on the ledger, are what move a
 * bloom when the player answers it.
 *
 * <p>Every effect on a market goes through {@link BloomWorld}; the system reads the market list
 * only on the weekly pass and on a spread, so a running bloom costs a few comparisons a day.
 */
public final class RavBloomSystem implements CampaignSystem {

    /** Trigger keys of bloom-sourced rescues: clear of the retired epoch keys and the debug bases. */
    public static final long TRIGGER_BASE = 1L << 50;

    private final BloomWorld world;
    private final BloomTuning tuning;
    /** False only in tests that walk one bloom through its life without the weekly draw. */
    private final boolean draws;

    public RavBloomSystem() {
        this(new SectorBloomWorld(), BloomTuning.live());
    }

    public RavBloomSystem(BloomWorld world, BloomTuning tuning) {
        this(world, tuning, true);
    }

    RavBloomSystem(BloomWorld world, BloomTuning tuning, boolean draws) {
        this.world = world;
        this.tuning = tuning;
        this.draws = draws;
    }

    @Override
    public String name() {
        return "RavBloom";
    }

    @Override
    public EnumSet<CampaignTable> reads() {
        return EnumSet.of(CampaignTable.EVENTS, CampaignTable.RAV_BLOOMS);
    }

    @Override
    public EnumSet<CampaignTable> writes() {
        return EnumSet.of(CampaignTable.EVENTS, CampaignTable.RAV_BLOOMS);
    }

    @Override
    public void tick(CampaignState state, int day) {
        if (state == null || world == null || day < 0) return;
        RavBloomLedger ledger = state.ravBlooms();
        List<RavBloom> running = new ArrayList<>();
        for (RavBloom bloom : ledger.blooms()) if (bloom.active()) running.add(bloom);
        for (RavBloom bloom : running) advance(state, ledger, bloom, day);

        int week = day / 7;
        if (week == ledger.lastWeek()) return;
        ledger.lastWeek(week);
        List<BloomMarket> markets = null;
        for (RavBloom bloom : running) {
            if (bloom.stage != BloomStage.DARK || !canSpread(bloom)) continue;
            if (random(week, bloom.id).nextDouble() >= tuning.severity.weeklySpreadChance) continue;
            if (markets == null) markets = world.markets();
            spread(state, ledger, bloom, markets, day);
        }
        if (draws && day >= RavBloomRules.FIRST_WAKE_DAY) {
            if (markets == null) markets = world.markets();
            wake(state, ledger, markets, week, day);
        }
    }

    // ---------- waking and spread ----------

    private void wake(CampaignState state, RavBloomLedger ledger, List<BloomMarket> markets,
                      int week, int day) {
        if (ledger.activeCount() >= RavBloomRules.MAX_RUNNING) return;
        SplittableRandom random = random(week, 0L);
        if (random.nextDouble() >= RavBloomRules.weeklyWakeChance(tuning)) return;
        double total = 0.0;
        double[] weights = new double[markets.size()];
        for (int i = 0; i < markets.size(); i++) {
            BloomMarket market = markets.get(i);
            if (!eligible(ledger, market, day)
                    || !RavBloomRules.hasReservoir(world.seed(), market)) continue;
            weights[i] = RavBloomRules.wakeWeight(market);
            total += weights[i];
        }
        if (total <= 0.0) return;
        double pick = random.nextDouble() * total;
        int chosen = -1;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0.0) continue;
            chosen = i;
            pick -= weights[i];
            if (pick < 0.0) break;
        }
        BloomMarket market = markets.get(chosen);
        ledger.open(market.id, 0, -1L, day, RavBloomRules.stirDays(world.seed(), market.id, day));
    }

    private boolean canSpread(RavBloom bloom) {
        return bloom.generation < RavBloomRules.MAX_GENERATION
                && bloom.seeded < tuning.severity.maxSeeded;
    }

    private void spread(CampaignState state, RavBloomLedger ledger, RavBloom from,
                        List<BloomMarket> markets, int day) {
        BloomMarket target = spreadTarget(ledger, from, markets, day);
        if (target == null) return;
        from.seeded++;
        RavBloom seeded = ledger.open(target.id, from.generation + 1, from.id, day, 0);
        erupt(state, ledger, seeded, day);
    }

    /** Where a dark bloom's cargo lands: another world in its system first, else the nearest in range. */
    BloomMarket spreadTarget(RavBloomLedger ledger, RavBloom from, List<BloomMarket> markets, int day) {
        BloomMarket source = world.market(from.marketId);
        if (source == null) return null;
        BloomMarket target = null;
        float best = Float.MAX_VALUE;
        for (BloomMarket market : markets) {
            if (market.id.equals(source.id) || !eligible(ledger, market, day)
                    || market.size < RavBloomRules.MIN_SIZE) continue;
            boolean sameSystem = market.systemId != null && market.systemId.equals(source.systemId);
            float distance = sameSystem ? -1f : source.distanceLy(market);
            if (!sameSystem && distance > RavBloomRules.SPREAD_RANGE_LY) continue;
            if (distance < best) {
                best = distance;
                target = market;
            }
        }
        return target;
    }

    private boolean eligible(RavBloomLedger ledger, BloomMarket market, int day) {
        if (market.hidden || market.size < RavBloomRules.MIN_SIZE
                || ledger.activeAt(market.id) != null || ledger.resting(market.id, day)) {
            return false;
        }
        if (!market.playerOwned) return true;
        switch (tuning.playerColonies) {
            case NEVER: return false;
            case AFTER_FIRST: return ledger.playerAnswered();
            default: return true;
        }
    }

    /** Wakes a bloom at one market now, past the weekly draw: the debug spawner and tests. */
    public RavBloom wakeAt(CampaignState state, String marketId, int day) {
        RavBloomLedger ledger = state.ravBlooms();
        if (ledger.activeAt(marketId) != null || world.market(marketId) == null) return null;
        return ledger.open(marketId, 0, -1L, day, RavBloomRules.stirDays(world.seed(), marketId, day));
    }

    // ---------- the lifecycle ----------

    private void advance(CampaignState state, RavBloomLedger ledger, RavBloom bloom, int day) {
        if (world.market(bloom.marketId) == null) {
            end(state, ledger, bloom, BloomEnd.BURNED_OUT, day);
            return;
        }
        switch (bloom.stage) {
            case STIRRING:
                if (day >= bloom.wakeDay + bloom.stirDays) erupt(state, ledger, bloom, day);
                break;
            case ERUPTED:
                raiseCall(state, bloom, day);
                if (readRescue(state, ledger, bloom, day)) return;
                if (bloom.stage == BloomStage.ERUPTED && shouldGoDark(state, bloom, day)) {
                    goDark(bloom, day);
                }
                break;
            case DARK:
                if (readRescue(state, ledger, bloom, day)) return;
                if (bloom.stage != BloomStage.DARK) return;
                eatDown(state, ledger, bloom, day);
                if (bloom.active() && day >= bloom.darkDay + tuning.severity.burnoutDays) {
                    end(state, ledger, bloom, BloomEnd.BURNED_OUT, day);
                }
                break;
            case STARVED:
                if (readRescue(state, ledger, bloom, day)) return;
                if (day >= bloom.starvedDay + RavBloomRules.STARVED_DAYS) {
                    end(state, ledger, bloom, BloomEnd.STARVED_OUT, day);
                }
                break;
            default:
                break;
        }
    }

    private void erupt(CampaignState state, RavBloomLedger ledger, RavBloom bloom, int day) {
        BloomMarket market = world.market(bloom.marketId);
        bloom.stage = BloomStage.ERUPTED;
        bloom.eruptDay = day;
        bloom.quarantined = market != null
                && RavBloomRules.hegemonyPolices(market, world.hegemonyAlive());
        world.erupted(bloom);
        raiseCall(state, bloom, day);
    }

    /** The distress call: a Civilian Rescue raised through the shared gate, while its window lasts. */
    private void raiseCall(CampaignState state, RavBloom bloom, int day) {
        if (bloom.rescueEventId > 0L
                || day > bloom.eruptDay + RavBloomRules.CALL_WINDOW_DAYS
                || CampaignEvents.hasOpenEvent(state)) {
            return;
        }
        BloomMarket market = world.market(bloom.marketId);
        if (market == null || market.size < RavBloomRules.MIN_SIZE) return;
        int slot = state.marketRegistry.intern(bloom.marketId);
        int window = Math.max(1, bloom.eruptDay + RavBloomRules.CALL_WINDOW_DAYS - day);
        long eventId = CivilianRescueSpawnSystem.prepareEvent(state, TRIGGER_BASE + bloom.id,
                slot, market.size, day, window);
        if (eventId > 0L) bloom.rescueEventId = eventId;
    }

    private boolean shouldGoDark(CampaignState state, RavBloom bloom, int day) {
        if (day >= bloom.eruptDay + RavBloomRules.DARK_AFTER_DAYS) return true;
        // A call that never got through the gate is a call nobody answered.
        if (bloom.rescueEventId <= 0L) return day > bloom.eruptDay + RavBloomRules.CALL_WINDOW_DAYS;
        CampaignEventState rescue = rescueState(state, bloom);
        return rescue == CampaignEventState.REFUSED || rescue == CampaignEventState.EXPIRED;
    }

    private void goDark(RavBloom bloom, int day) {
        bloom.stage = BloomStage.DARK;
        bloom.darkDay = day;
        bloom.lastSizeLossDay = day;
        world.wentDark(bloom);
    }

    private void eatDown(CampaignState state, RavBloomLedger ledger, RavBloom bloom, int day) {
        BloomTuning.Severity severity = tuning.severity;
        if (day < bloom.lastSizeLossDay + severity.sizeLossDays
                || bloom.sizeLost >= severity.maxSizeLoss) {
            return;
        }
        bloom.lastSizeLossDay = day;
        if (world.reduceSize(bloom.marketId)) {
            bloom.sizeLost++;
        } else if (severity.decivilizesAtFloor) {
            world.decivilize(bloom.marketId);
            end(state, ledger, bloom, BloomEnd.DECIVILIZED, day);
        }
    }

    /**
     * Reads the rescue's terminal state once: a refusal is remembered (and costs the owner's
     * regard), an evacuation is applied. True when the bloom ended here.
     */
    private boolean readRescue(CampaignState state, RavBloomLedger ledger, RavBloom bloom, int day) {
        if (bloom.rescueEventId <= 0L || bloom.resultApplied) return false;
        CampaignEventState rescue = rescueState(state, bloom);
        if (rescue == CampaignEventState.REFUSED && !bloom.refused) {
            bloom.refused = true;
            BloomMarket market = world.market(bloom.marketId);
            if (market != null && !market.playerOwned) world.reputation(market.factionId, -2, bloom);
            return false;
        }
        if (rescue != CampaignEventState.RESOLVED) return false;
        int row = state.eventIndex(bloom.rescueEventId);
        bloom.resultApplied = true;
        bloom.evacuated = state.eventCiviliansRescued[row];
        bloom.atRisk = state.eventCiviliansAtRisk[row];
        bloom.motherCollapsed = ledger.motherCollapsedFor(bloom.rescueEventId);
        ledger.markAnswered();

        BloomMarket market = world.market(bloom.marketId);
        if (market != null && !market.playerOwned) {
            int regard = RavBloomRules.ownerReputationFor(bloom.evacuated, bloom.atRisk);
            if (regard > 0) world.reputation(market.factionId, regard, bloom);
        }
        if (bloom.evacuated > 0) world.reputation(RavBloomRules.CHURCH, 1, bloom);
        if (bloom.motherCollapsed) {
            if (bloom.quarantined) world.reputation(RavBloomRules.HEGEMONY, 3, bloom);
            end(state, ledger, bloom, BloomEnd.CONTAINED, day);
            return true;
        }
        if (bloom.evacuated * 2 >= bloom.atRisk && bloom.stage != BloomStage.STARVED) {
            bloom.stage = BloomStage.STARVED;
            bloom.starvedDay = day;
            world.starved(bloom);
        }
        return false;
    }

    private void end(CampaignState state, RavBloomLedger ledger, RavBloom bloom, BloomEnd end, int day) {
        bloom.stage = BloomStage.ENDED;
        bloom.end = end;
        bloom.endDay = day;
        ledger.rest(bloom.marketId, day + (end == BloomEnd.CONTAINED
                ? RavBloomRules.REST_AFTER_CONTAINED_DAYS : RavBloomRules.REST_AFTER_OTHER_DAYS));
        // A call still open when its bloom is over would hang; close it as expired.
        if (bloom.rescueEventId > 0L) {
            int row = state.eventIndex(bloom.rescueEventId);
            CampaignEventState rescue = rescueState(state, bloom);
            if (row >= 0 && (rescue == CampaignEventState.PENDING_CHOICE
                    || rescue == CampaignEventState.COMMITTED)) {
                state.eventState[row] = CampaignEventState.EXPIRED.toByte();
            }
        }
        world.ended(bloom);
    }

    private static CampaignEventState rescueState(CampaignState state, RavBloom bloom) {
        int row = state.eventIndex(bloom.rescueEventId);
        if (row < 0 || CampaignEventType.fromByte(state.eventType[row])
                != CampaignEventType.CIVILIAN_RESCUE) {
            return null;
        }
        return CampaignEventState.fromByte(state.eventState[row]);
    }

    private SplittableRandom random(int week, long salt) {
        return new SplittableRandom(RavBloomRules.mix(world.seed() ^ 0x424C4F4F4DL, ((long) week << 20) ^ salt));
    }
}
