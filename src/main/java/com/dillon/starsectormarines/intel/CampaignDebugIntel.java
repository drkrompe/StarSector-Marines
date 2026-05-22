package com.dillon.starsectormarines.intel;

import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.CampaignSystem;
import com.dillon.starsectormarines.campaign.ContractState;
import com.dillon.starsectormarines.campaign.ContractType;
import com.dillon.starsectormarines.campaign.HouseFlavor;
import com.dillon.starsectormarines.campaign.HouseRank;
import com.dillon.starsectormarines.campaign.HouseSeeder;
import com.dillon.starsectormarines.campaign.HouseStatus;
import java.util.Random;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import java.awt.Color;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dev-only intel screen for inspecting and mutating {@link CampaignState}.
 * Registration is gated by {@code DevConfig.CAMPAIGN_DEBUG_INTEL}; flip the
 * flag off (or strip this class) for prod builds.
 *
 * <p>Surfaces:
 * <ul>
 *   <li>Counters (house / stake / chain / rep totals).</li>
 *   <li>Bypass-gating toggle — lets {@code MissionGenerator} skip rep/rank
 *       checks for any-house-any-mission playtesting.</li>
 *   <li>Per-house row with rank, flavor, status, and a promote button.</li>
 *   <li>Reseed: wipes the houses table and re-seeds from current sector
 *       state. Useful when iterating on seeder logic.</li>
 * </ul>
 */
public class CampaignDebugIntel extends BaseIntelPlugin {

    public static final String TAG = "marines_campaign_debug";
    private static final String TITLE = "Marines Debug";

    private static final String BTN_TOGGLE_BYPASS  = "toggle-bypass";
    private static final String BTN_TOGGLE_FILTER  = "toggle-filter";
    private static final String BTN_RESEED         = "reseed";
    private static final String BTN_PROMOTE        = "promote:";
    private static final String BTN_DEMOTE         = "demote:";
    private static final String BTN_FORCE_TICK     = "force-tick";
    private static final String BTN_SPAWN_LOCAL    = "spawn-local-offers";
    private static final String BTN_CLEAR_TERMINAL = "clear-terminal";
    private static final String BTN_ACCEPT         = "accept:";
    private static final String BTN_FORCE_COMPLETE = "complete:";
    private static final String BTN_FORCE_FAIL     = "fail:";

    /** UI-state only; not persisted. Default ON when the player is in a system. */
    private boolean filterToLocalSystem = true;

    @Override
    protected String getName() {
        return TITLE;
    }

    @Override
    public String getSmallDescriptionTitle() {
        return TITLE;
    }

    @Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        info.addPara(TITLE, getTitleColor(mode), 0f);
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = new LinkedHashSet<>();
        tags.add(TAG);
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return null;
    }

    @Override
    public boolean shouldRemoveIntel() {
        return false;
    }

    @Override
    public String getSortString() {
        return "ZZZ_" + TITLE; // sort to bottom of intel list
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        TooltipMakerAPI ui = panel.createUIElement(width, height, true);

        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) {
            ui.addPara("CampaignStateScript not registered — open a save first.", 10f);
            panel.addUIElement(ui).inTL(0f, 0f);
            return;
        }
        CampaignState s = script.state();

        // ---- Counters ----
        ui.addSectionHeading("Campaign state", Color.WHITE, new Color(40, 40, 40), com.fs.starfarer.api.ui.Alignment.LMID, 0f);
        ui.addPara("Houses: %s    Stakes: %s    Chains: %s    Contracts: %s    Player rep rows: %s    LastTickDay: %s",
                10f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(s.houseCount),
                String.valueOf(s.stakeCount),
                String.valueOf(s.chainCount),
                String.valueOf(s.contractCount),
                String.valueOf(s.repCount),
                String.valueOf(s.lastTickDay));
        ui.addPara("Faction registry: %s    Industry registry: %s    Market registry: %s    Captain registry: %s",
                4f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(s.factionRegistry.size()),
                String.valueOf(s.industryRegistry.size()),
                String.valueOf(s.marketRegistry.size()),
                String.valueOf(s.captainRegistry.size()));

        int[] byState = countContractsByState(s);
        ui.addPara("Contracts — OFFERED:%s  ACTIVE:%s  IN_PROGRESS:%s  COMPLETED:%s  FAILED:%s  DEFAULTED:%s  ABANDONED:%s",
                4f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(byState[ContractState.OFFERED.ordinal()]),
                String.valueOf(byState[ContractState.ACTIVE.ordinal()]),
                String.valueOf(byState[ContractState.IN_PROGRESS.ordinal()]),
                String.valueOf(byState[ContractState.COMPLETED.ordinal()]),
                String.valueOf(byState[ContractState.FAILED.ordinal()]),
                String.valueOf(byState[ContractState.DEFAULTED.ordinal()]),
                String.valueOf(byState[ContractState.ABANDONED.ordinal()]));

        // ---- Toggles ----
        ui.addSectionHeading("Toggles", Color.WHITE, new Color(40, 40, 40), com.fs.starfarer.api.ui.Alignment.LMID, 14f);

        StarSystemAPI playerSystem = currentPlayerSystem();
        Set<Integer> localMarketSlots = (playerSystem != null && filterToLocalSystem)
                ? collectLocalMarketSlots(s, playerSystem)
                : null;
        String filterLabel = playerSystem == null
                ? "Filter to local system: N/A (not in a system)"
                : "Filter to local system: " + (filterToLocalSystem ? "ON (" + playerSystem.getName() + ")" : "OFF");

        ui.addButton(filterLabel, BTN_TOGGLE_FILTER, 320f, 24f, 8f);
        String bypassLabel = "Bypass house gating: " + (s.debugBypassHouseGating ? "ON" : "OFF");
        ui.addButton(bypassLabel, BTN_TOGGLE_BYPASS, 320f, 24f, 8f);
        ui.addButton("Force daily tick (run all systems)", BTN_FORCE_TICK, 320f, 24f, 8f);
        ui.addButton("Spawn offers for local patrons", BTN_SPAWN_LOCAL, 320f, 24f, 8f);
        ui.addButton("Clear terminal contracts (cleanup)", BTN_CLEAR_TERMINAL, 320f, 24f, 8f);
        ui.addButton("Reseed houses (wipes existing)", BTN_RESEED, 320f, 24f, 8f);

        // ---- Contracts list ----
        ui.addSectionHeading("Contracts", Color.WHITE, new Color(40, 40, 40), com.fs.starfarer.api.ui.Alignment.LMID, 14f);
        int shownContracts = 0;
        int skippedContracts = 0;
        for (int i = 0; i < s.contractCount; i++) {
            if (localMarketSlots != null && !contractMatchesFilter(s, i, localMarketSlots)) {
                skippedContracts++;
                continue;
            }
            if (shownContracts >= 100) { skippedContracts++; continue; }
            renderContractRow(ui, s, i);
            shownContracts++;
        }
        if (shownContracts == 0 && skippedContracts == 0) ui.addPara("(none)", 8f);
        if (skippedContracts > 0) {
            ui.addPara("... %s more contracts (filtered / truncated)", 6f, Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(skippedContracts));
        }

        // ---- House list ----
        ui.addSectionHeading("Houses", Color.WHITE, new Color(40, 40, 40), com.fs.starfarer.api.ui.Alignment.LMID, 14f);
        int shownHouses = 0;
        int skippedHouses = 0;
        for (int i = 0; i < s.houseCount; i++) {
            if (localMarketSlots != null && !localMarketSlots.contains(s.houseMarketId[i])) {
                skippedHouses++;
                continue;
            }
            if (shownHouses >= 200) { skippedHouses++; continue; }
            renderHouseRow(ui, s, i);
            shownHouses++;
        }
        if (shownHouses == 0 && skippedHouses == 0) ui.addPara("(none)", 8f);
        if (skippedHouses > 0) {
            ui.addPara("... %s more houses (filtered / truncated)", 6f, Color.LIGHT_GRAY, Color.WHITE,
                    String.valueOf(skippedHouses));
        }

        panel.addUIElement(ui).inTL(0f, 0f);
    }

    private void renderContractRow(TooltipMakerAPI ui, CampaignState s, int i) {
        long id = s.contractId[i];
        ContractType type   = ContractType.fromByte(s.contractType[i]);
        ContractState state = ContractState.fromByte(s.contractState[i]);
        long patronId = s.contractPatronHouseId[i];
        long targetId = s.contractTargetHouseId[i];
        String patronName = displayNameFor(s, patronId);
        String targetName = targetId == -1L ? "—" : displayNameFor(s, targetId);
        int phasesDone  = s.contractPhasesDone[i] & 0xFF;
        int phasesTotal = s.contractPhasesTotal[i] & 0xFF;
        int salvageBase = s.contractSalvageBaseline[i] & 0xFF;
        int salvageNeg  = s.contractSalvageNegotiated[i] & 0xFF;
        int cashMult    = s.contractCashMultiplier[i] & 0xFF;

        ui.addPara("[%s] %s — %s — patron=%s — target=%s — payout=%s — salvage=%s/%s%% — cash=%s%% — phases=%s/%s",
                8f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(id),
                type.name(),
                state.name(),
                patronName,
                targetName,
                String.valueOf(s.contractBasePayout[i]),
                String.valueOf(salvageNeg),
                String.valueOf(salvageBase),
                String.valueOf(cashMult),
                String.valueOf(phasesDone),
                String.valueOf(phasesTotal));

        if (state == ContractState.OFFERED) {
            ui.addButton("Accept", BTN_ACCEPT + id, 90f, 20f, 2f);
        } else if (state == ContractState.ACTIVE || state == ContractState.IN_PROGRESS) {
            ui.addButton("Force complete", BTN_FORCE_COMPLETE + id, 130f, 20f, 2f);
            ui.addButton("Force fail",     BTN_FORCE_FAIL     + id, 100f, 20f, 2f);
        }
    }

    private static String displayNameFor(CampaignState s, long houseId) {
        int idx = s.houseIndex(houseId);
        if (idx < 0) return "house#" + houseId;
        return s.houseDisplayName[idx] != null ? s.houseDisplayName[idx] : ("house#" + houseId);
    }

    private static int[] countContractsByState(CampaignState s) {
        int[] counts = new int[ContractState.values().length];
        for (int i = 0; i < s.contractCount; i++) {
            counts[s.contractState[i] & 0xFF]++;
        }
        return counts;
    }

    /** Player's current star system, or null if in hyperspace / between systems. */
    private static StarSystemAPI currentPlayerSystem() {
        if (Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return null;
        LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
        return (loc instanceof StarSystemAPI) ? (StarSystemAPI) loc : null;
    }

    /**
     * Pre-computes the set of {@code marketRegistry} slots whose vanilla
     * {@link MarketAPI#getStarSystem()} matches {@code playerSystem}. Returned
     * set is queried against {@code houseMarketId[i]} so the filter is O(1)
     * per row.
     */
    private static Set<Integer> collectLocalMarketSlots(CampaignState s, StarSystemAPI playerSystem) {
        Set<Integer> slots = new HashSet<>();
        for (int slot = 0; slot < s.marketRegistry.size(); slot++) {
            String marketId = s.marketRegistry.get(slot);
            if (marketId == null) continue;
            MarketAPI m = Global.getSector().getEconomy().getMarket(marketId);
            if (m == null) continue;
            if (m.getStarSystem() == playerSystem) slots.add(slot);
        }
        return slots;
    }

    /**
     * A contract matches the filter when either party's market lives in the
     * player's current system — keeps strikes against off-system targets
     * visible when the patron is local.
     */
    private static boolean contractMatchesFilter(CampaignState s, int row, Set<Integer> localMarketSlots) {
        if (localMarketSlots.contains(s.contractMarketId[row])) return true;
        long targetId = s.contractTargetHouseId[row];
        if (targetId != -1L) {
            int targetIdx = s.houseIndex(targetId);
            if (targetIdx >= 0 && localMarketSlots.contains(s.houseMarketId[targetIdx])) return true;
        }
        return false;
    }

    private void renderHouseRow(TooltipMakerAPI ui, CampaignState s, int i) {
        long id = s.houseId[i];
        HouseRank rank = HouseRank.fromByte(s.houseRank[i]);
        HouseFlavor flavor = HouseFlavor.fromByte(s.houseFlavor[i]);
        HouseStatus status = HouseStatus.fromByte(s.houseStatus[i]);
        String factionId = s.factionRegistry.get(s.houseFactionId[i]);
        String marketId  = s.marketRegistry.get(s.houseMarketId[i]);

        String name = s.houseDisplayName[i] != null ? s.houseDisplayName[i] : ("house#" + id);
        ui.addPara("[%s] %s — %s %s — faction=%s — market=%s — status=%s",
                8f, Color.LIGHT_GRAY, Color.WHITE,
                String.valueOf(id),
                name,
                rank.displayName(flavor),
                "(" + flavor.name().toLowerCase() + ")",
                factionId != null ? factionId : "?",
                marketId != null ? marketId : "?",
                status.name().toLowerCase());

        ui.addButton("Promote", BTN_PROMOTE + id, 90f, 20f, 2f);
        ui.addButton("Demote",  BTN_DEMOTE  + id, 90f, 20f, 2f);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, com.fs.starfarer.api.ui.IntelUIAPI ui) {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return;
        CampaignState s = script.state();

        if (BTN_TOGGLE_BYPASS.equals(buttonId)) {
            s.debugBypassHouseGating = !s.debugBypassHouseGating;
        } else if (BTN_TOGGLE_FILTER.equals(buttonId)) {
            filterToLocalSystem = !filterToLocalSystem;
        } else if (BTN_RESEED.equals(buttonId)) {
            wipeHouses(s);
            HouseSeeder.seed(s);
        } else if (BTN_FORCE_TICK.equals(buttonId)) {
            forceTick(script, s);
        } else if (BTN_SPAWN_LOCAL.equals(buttonId)) {
            spawnOffersForLocalPatrons(s);
        } else if (BTN_CLEAR_TERMINAL.equals(buttonId)) {
            clearTerminalContracts(s);
        } else if (buttonId instanceof String) {
            String b = (String) buttonId;
            if (b.startsWith(BTN_PROMOTE)) {
                long id = parseLong(b.substring(BTN_PROMOTE.length()), -1);
                int idx = s.houseIndex(id);
                if (idx >= 0) {
                    HouseRank current = HouseRank.fromByte(s.houseRank[idx]);
                    s.houseRank[idx] = current.next().toByte();
                    s.housePromotionProgress[idx] = 0;
                }
            } else if (b.startsWith(BTN_DEMOTE)) {
                long id = parseLong(b.substring(BTN_DEMOTE.length()), -1);
                int idx = s.houseIndex(id);
                if (idx >= 0) {
                    HouseRank current = HouseRank.fromByte(s.houseRank[idx]);
                    HouseRank prev = current.ordinal() > 0
                            ? HouseRank.values()[current.ordinal() - 1]
                            : current;
                    s.houseRank[idx] = prev.toByte();
                }
            } else if (b.startsWith(BTN_ACCEPT)) {
                long id = parseLong(b.substring(BTN_ACCEPT.length()), -1);
                acceptOffer(s, id);
            } else if (b.startsWith(BTN_FORCE_COMPLETE)) {
                long id = parseLong(b.substring(BTN_FORCE_COMPLETE.length()), -1);
                forceComplete(s, id);
            } else if (b.startsWith(BTN_FORCE_FAIL)) {
                long id = parseLong(b.substring(BTN_FORCE_FAIL.length()), -1);
                forceFail(s, id);
            }
        }

        ui.updateUIForItem(this);
    }

    /** Walks each registered system once at the current sector day. Bypasses the
     *  script's lastTickDay guard so multiple presses in one day still advance. */
    private static void forceTick(CampaignStateScript script, CampaignState s) {
        int day = Global.getSector() != null
                ? (int) Global.getSector().getClock().getDay()
                : s.lastTickDay + 1;
        List<CampaignSystem> list = script.systems();
        for (int i = 0; i < list.size(); i++) {
            list.get(i).tick(s, day);
        }
        s.lastTickDay = day;
    }

    /**
     * Direct-creates Strike offers for every T1 active patron whose market lives
     * in the player's current system, skipping patrons that already have an
     * outstanding offer (matches {@link com.dillon.starsectormarines.campaign.systems.ContractGenerator}'s
     * per-patron cap). Defaults mirror the generator so debug-spawned offers
     * are indistinguishable from rolled ones.
     */
    private static void spawnOffersForLocalPatrons(CampaignState s) {
        StarSystemAPI system = currentPlayerSystem();
        if (system == null) return;
        Set<Integer> localMarketSlots = collectLocalMarketSlots(s, system);
        if (localMarketSlots.isEmpty()) return;
        int day = Global.getSector() != null ? (int) Global.getSector().getClock().getDay() : 0;

        for (int i = 0; i < s.houseCount; i++) {
            if (HouseRank.fromByte(s.houseRank[i]) != HouseRank.TIER_1) continue;
            if (HouseStatus.fromByte(s.houseStatus[i]) != HouseStatus.ACTIVE) continue;
            if (!localMarketSlots.contains(s.houseMarketId[i])) continue;

            long patronId = s.houseId[i];
            if (patronHasOpenOffer(s, patronId)) continue;
            long targetId = pickAnyOtherT1Active(s, i);
            if (targetId == -1L) continue;

            s.addContract(
                    patronId, targetId, -1L,
                    ContractType.STRIKE, ContractState.OFFERED,
                    day, -1, (byte) 1, -1,
                    s.houseMarketId[i], -1,
                    25_000, 0,
                    (byte) 60, (byte) 60, (byte) 100);
        }
    }

    private static boolean patronHasOpenOffer(CampaignState s, long patronId) {
        for (int i = 0; i < s.contractCount; i++) {
            if (s.contractPatronHouseId[i] != patronId) continue;
            if (ContractState.fromByte(s.contractState[i]) == ContractState.OFFERED) return true;
        }
        return false;
    }

    /** Deterministic-ish pick across runs: walks rows in order and returns the first
     *  T1 active house that isn't the patron itself. Sufficient for debug spawning. */
    private static long pickAnyOtherT1Active(CampaignState s, int patronRow) {
        long patronId = s.houseId[patronRow];
        Random r = new Random(patronId);
        int candidates = 0;
        for (int j = 0; j < s.houseCount; j++) {
            if (j == patronRow) continue;
            if (HouseRank.fromByte(s.houseRank[j]) != HouseRank.TIER_1) continue;
            if (HouseStatus.fromByte(s.houseStatus[j]) != HouseStatus.ACTIVE) continue;
            candidates++;
        }
        if (candidates == 0) return -1L;
        int pick = r.nextInt(candidates);
        int seen = 0;
        for (int j = 0; j < s.houseCount; j++) {
            if (j == patronRow) continue;
            if (HouseRank.fromByte(s.houseRank[j]) != HouseRank.TIER_1) continue;
            if (HouseStatus.fromByte(s.houseStatus[j]) != HouseStatus.ACTIVE) continue;
            if (seen++ == pick) return s.houseId[j];
        }
        return -1L;
    }

    /** Compact terminal contracts out of the table to keep the list browsable. */
    private static void clearTerminalContracts(CampaignState s) {
        int write = 0;
        s.contractIndexById.clear();
        for (int read = 0; read < s.contractCount; read++) {
            ContractState st = ContractState.fromByte(s.contractState[read]);
            if (st.isTerminal()) continue;
            if (write != read) copyContractRow(s, read, write);
            s.contractIndexById.put(s.contractId[write], write);
            write++;
        }
        s.contractCount = write;
    }

    /** Moves contract row {@code from} to {@code to} across every parallel array. */
    private static void copyContractRow(CampaignState s, int from, int to) {
        s.contractId[to]                = s.contractId[from];
        s.contractPatronHouseId[to]     = s.contractPatronHouseId[from];
        s.contractTargetHouseId[to]     = s.contractTargetHouseId[from];
        s.contractChainId[to]           = s.contractChainId[from];
        s.contractType[to]              = s.contractType[from];
        s.contractState[to]             = s.contractState[from];
        s.contractAcceptedTick[to]      = s.contractAcceptedTick[from];
        s.contractExpiresTick[to]       = s.contractExpiresTick[from];
        s.contractPhasesTotal[to]       = s.contractPhasesTotal[from];
        s.contractPhasesDone[to]        = s.contractPhasesDone[from];
        s.contractCaptainId[to]         = s.contractCaptainId[from];
        s.contractMarketId[to]          = s.contractMarketId[from];
        s.contractIndustryId[to]        = s.contractIndustryId[from];
        s.contractBasePayout[to]        = s.contractBasePayout[from];
        s.contractRetainerPerMonth[to]  = s.contractRetainerPerMonth[from];
        s.contractSalvageBaseline[to]   = s.contractSalvageBaseline[from];
        s.contractSalvageNegotiated[to] = s.contractSalvageNegotiated[from];
        s.contractCashMultiplier[to]    = s.contractCashMultiplier[from];
    }

    private static void acceptOffer(CampaignState s, long id) {
        int row = s.contractIndex(id);
        if (row < 0) return;
        if (ContractState.fromByte(s.contractState[row]) != ContractState.OFFERED) return;
        s.contractState[row] = ContractState.ACTIVE.toByte();
        s.contractAcceptedTick[row] = Global.getSector() != null
                ? (int) Global.getSector().getClock().getDay()
                : s.contractAcceptedTick[row];
    }

    /** Debug-only contract closure — mirrors {@code MissionResolver.applyContractBridge}
     *  victory path so this exercises the same state-write surface end-to-end. */
    private static void forceComplete(CampaignState s, long id) {
        int row = s.contractIndex(id);
        if (row < 0) return;
        ContractState prior = ContractState.fromByte(s.contractState[row]);
        if (prior.isTerminal()) return;
        s.contractPhasesDone[row] = s.contractPhasesTotal[row];
        s.contractState[row] = ContractState.COMPLETED.toByte();
        bumpPatronRep(s, s.contractPatronHouseId[row], +1, true);
    }

    private static void forceFail(CampaignState s, long id) {
        int row = s.contractIndex(id);
        if (row < 0) return;
        ContractState prior = ContractState.fromByte(s.contractState[row]);
        if (prior.isTerminal()) return;
        s.contractState[row] = ContractState.FAILED.toByte();
        bumpPatronRep(s, s.contractPatronHouseId[row], -2, false);
    }

    private static void bumpPatronRep(CampaignState s, long patronId, int repDelta, boolean completed) {
        int repRow = s.ensureRepRow(patronId);
        s.repValue[repRow] = Math.max(-100, Math.min(100, s.repValue[repRow] + repDelta));
        int day = Global.getSector() != null ? (int) Global.getSector().getClock().getDay() : 0;
        s.repLastContractTick[repRow] = day;
        if (completed) {
            int n = (s.repContractsCompleted[repRow] & 0xFFFF) + 1;
            if (n > 65535) n = 65535;
            s.repContractsCompleted[repRow] = (short) n;
        } else {
            int n = (s.repContractsFailed[repRow] & 0xFFFF) + 1;
            if (n > 65535) n = 65535;
            s.repContractsFailed[repRow] = (short) n;
        }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        return BTN_RESEED.equals(buttonId);
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        if (BTN_RESEED.equals(buttonId)) {
            prompt.addPara("Wipe all houses and re-seed from current sector state?", 0f);
        }
    }

    @Override
    public String getConfirmText(Object buttonId) {
        return "Reseed";
    }

    @Override
    public String getCancelText(Object buttonId) {
        return "Cancel";
    }

    private static long parseLong(String s, long fallback) {
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return fallback; }
    }

    /**
     * Resets the houses table for reseed. Clears the id→index map alongside
     * the row count so reseed doesn't accumulate stale lookup entries. Does
     * NOT touch the string registries (xstream-persists their slots; clearing
     * would break stake/rep rows that still reference the old slots).
     */
    private static void wipeHouses(CampaignState s) {
        s.houseCount = 0;
        s.houseIndexById.clear();
        // Optional: also wipe stakes/chains/rep — for now keep them, they'll just point at dead ids.
        // Reseed will rebuild houses; any stale stake/rep rows can be cleaned up by a later pass.
    }
}
