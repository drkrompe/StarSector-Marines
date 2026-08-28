package com.dillon.starsectormarines;

import com.dillon.starsectormarines.campaign.CampaignClock;
import com.dillon.starsectormarines.campaign.CampaignState;
import com.dillon.starsectormarines.campaign.CampaignStateScript;
import com.dillon.starsectormarines.campaign.HouseSeeder;
import com.dillon.starsectormarines.campaign.personnel.CaptainDiscoverySalvageListener;
import com.dillon.starsectormarines.campaign.systems.PatronEquipmentRewardSystem;
import com.dillon.starsectormarines.catalog.MarineCatalogManifest;
import com.dillon.starsectormarines.combathybrid.probe.CombatHybridCampaignPlugin;
import com.dillon.starsectormarines.combathybrid.probe.CombatHybridInputListener;
import com.dillon.starsectormarines.battle.appearance.UnitLayerLayouts;
import com.dillon.starsectormarines.battle.world.gen.GenMappingRegistry;
import com.dillon.starsectormarines.battle.weapon.WeaponRegistry;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.turret.DefensePostLayoutRegistry;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.battle.setup.GroundRosterRegistry;
import com.dillon.starsectormarines.intel.BridgeIntel;
import com.dillon.starsectormarines.intel.CampaignDebugIntel;
import com.dillon.starsectormarines.intel.CivilianRescueIntel;
import com.dillon.starsectormarines.intel.DefectorAsylumIntel;
import com.dillon.starsectormarines.intel.DeadLetterIntel;
import com.dillon.starsectormarines.intel.LastTestamentIntel;
import com.dillon.starsectormarines.ops.CompanyShipLossListener;
import com.dillon.starsectormarines.ops.CompanyViewAbility;
import com.dillon.starsectormarines.ops.event.PlayerEventPresenter;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineArmorCatalogRegistry;
import com.dillon.starsectormarines.marine.EquipmentTemplateCatalog;
import com.dillon.starsectormarines.marine.EquipmentTemplateCardInventory;
import com.dillon.starsectormarines.marine.FactionEquipmentCatalog;
import com.dillon.starsectormarines.marine.FactionEquipmentMarketStock;
import com.dillon.starsectormarines.marine.SquadLoadoutPresentationRegistry;
import com.dillon.starsectormarines.marine.MarineRoster;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.marine.Rank;
import com.dillon.starsectormarines.marine.SpecialEquipmentRegistry;
import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import org.apache.log4j.Logger;

import java.util.Set;

public class StarsectorMarinesModPlugin extends BaseModPlugin {

    public static final String MOD_ID = "starsector_marines";

    /** Ability bars the player can page through; vanilla's {@code AddAbility} uses five. */
    private static final int ABILITY_BARS = 5;

    private static final Logger LOG = Global.getLogger(StarsectorMarinesModPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        LOG.info("Starsector Marines: jar loaded");
        MarineCatalogManifest marineCatalogs = MarineCatalogManifest.discoverEnabled();
        // Tile definitions and generation mappings use the same enabled-provider
        // manifest as equipment. Mappings follow tiles so every referenced visual
        // resolves against the complete additive catalog before installation.
        TileRegistry.loadContributions(marineCatalogs.tilesets());
        GenMappingRegistry.loadContributions(marineCatalogs.tileMappings());
        // Weapon catalog → id-addressed registry (moddable-weapons W1). Unlike the
        // tile registries this is NOT self-defensive: a weapon whose stats failed to
        // load would read zero range and zero damage, so a bad catalog must stop
        // startup rather than produce a silently unwinnable battle. Must precede any
        // consumer that walks the catalog at load time — BattleSprites preloads every
        // primary's projectile sprite through it.
        WeaponRegistry.loadContributions(marineCatalogs.weapons());
        // Turret mounts and static platforms resolve weapon ids eagerly, so this
        // catalog necessarily follows the weapon registry.
        TurretCatalogRegistry.loadBuiltins();
        // Layout placements resolve turret structure ids, so stamp geometry
        // loads only after the turret platform catalog is installed.
        DefensePostLayoutRegistry.loadBuiltins();
        // Loadout identity, activation, AI policy, and equipment presentation.
        // Weapon-like items validate their referenced WeaponDef, so this follows
        // the weapon catalog and remains fail-loud for malformed built-in data.
        SpecialEquipmentRegistry.loadContributions(marineCatalogs.specialEquipment());
        // Player-facing armor class and descriptive copy are data-authored separately
        // from the save-compatible armor enum and its battle-facing values.
        MarineArmorCatalogRegistry.loadContributions(marineCatalogs.armor());
        // Collectible card identity and issue cost are also additive data. Cards
        // validate their referenced equipment only after all three equipment
        // registries are installed.
        EquipmentTemplateCatalog.loadContributions(marineCatalogs.equipmentTemplates());
        // Faction acquisition pools consume stable template-card ids and may be
        // extended additively by later providers, so they necessarily load next.
        FactionEquipmentCatalog.loadContributions(marineCatalogs.factionEquipment());
        // Collectible-facing tier, rarity, provenance, and lore remain authored data;
        // their rarity is presentation scarcity rather than random selection weight.
        SquadLoadoutPresentationRegistry.loadBuiltins();
        // Campaign-faction doctrine references primary weapons, special issue,
        // armor, and mech identities, so it validates after those catalogs.
        GroundRosterRegistry.loadContributions(marineCatalogs.groundRosters());
        // Modular unit clips turn simulation-authored locomotion/action phases
        // into layer transforms. The standalone editor reads the same document.
        UnitLayerLayouts.loadBuiltins();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        LOG.info("Starsector Marines: game loaded (newGame=" + newGame + ")");
        ensureBridgeIntel();
        // Before the roster: CampaignClock anchors its day counter on CampaignState, and
        // starter-captain creation stamps a day.
        ensureCampaignState();
        ensureMarineRoster();
        repairEquipmentCollectionProgression();
        deliverPendingPatronEquipmentRewards();
        ensureCaptainDiscoverySalvageListener();
        ensureCompanyShipLossListener();
        ensureFactionEquipmentMarketStock();
        ensurePlayerEventPresenter();
        ensureCompanyViewAbility();
        ensureCivilianRescueIntel();
        ensureDefectorAsylumIntel();
        ensureDeadLetterIntel();
        ensureLastTestamentIntel();
        if (DevConfig.CAMPAIGN_DEBUG_INTEL) {
            ensureCampaignDebugIntel();
        }
        if (DevConfig.S0_COMBAT_PROBE) {
            ensureCombatHybridProbe();
        }
        logRosterContents();
    }

    /**
     * Registers the S0 vanilla-combat-bridge probe: a {@link CombatHybridCampaignPlugin}
     * that can route {@code startBattle} to our minimal battle definition, and a
     * {@link CombatHybridInputListener} that arms it from a campaign-map hotkey.
     * Both are transient (not save-persisted), so we re-register each load and
     * de-dup defensively.
     */
    private static void ensureCombatHybridProbe() {
        SectorAPI sector = Global.getSector();

        sector.unregisterPlugin(CombatHybridCampaignPlugin.PLUGIN_ID);
        sector.registerPlugin(new CombatHybridCampaignPlugin());

        if (sector.getListenerManager().getListeners(CombatHybridInputListener.class).isEmpty()) {
            sector.getListenerManager().addListener(new CombatHybridInputListener(), true);
        }
        LOG.info("Starsector Marines: S0 combat-bridge probe registered (Ctrl+Shift+B on the campaign map)");
    }

    private static void ensureBridgeIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(BridgeIntel.class) != null) return;
        mgr.addIntel(new BridgeIntel(), true);
        LOG.info("Starsector Marines: Bridge intel registered");
    }

    private static void ensureMarineRoster() {
        SectorAPI sector = Global.getSector();
        MarineRosterScript script = MarineRosterScript.getInstance();
        if (script == null) {
            script = new MarineRosterScript();
            sector.addScript(script);
            LOG.info("Starsector Marines: MarineRosterScript registered");
        }
        MarineRoster roster = script.roster();
        if (roster.size() == 0) {
            float currentDay = CampaignClock.dayFloat();
            MarineCaptain starter = new MarineCaptain(
                    "Mira Hale",
                    "graphics/portraits/portrait_mercenary01.png",
                    Rank.LIEUTENANT,
                    currentDay);
            roster.add(starter);
            LOG.info("Starsector Marines: injected starter captain " + starter.name() + " [" + starter.id() + "]");
        }
        script.ensureStartingCompany();
    }

    /**
     * Watches the player's engagements so a company ship shot out from under
     * them reads differently from one they sold. See
     * {@link com.dillon.starsectormarines.ops.CompanyShipLossListener}.
     */
    private static void ensureCompanyShipLossListener() {
        SectorAPI sector = Global.getSector();
        sector.getListenerManager().removeListenerOfClass(CompanyShipLossListener.class);
        sector.getListenerManager().addListener(new CompanyShipLossListener(), true);
        LOG.info("Starsector Marines: company ship loss listener registered");
    }

    private static void ensureCaptainDiscoverySalvageListener() {
        SectorAPI sector = Global.getSector();
        sector.getListenerManager().removeListenerOfClass(
                CaptainDiscoverySalvageListener.class);
        sector.getListenerManager().addListener(
                new CaptainDiscoverySalvageListener(), true);
        LOG.info("Starsector Marines: captain discovery salvage listener registered");
    }

    private static void deliverPendingPatronEquipmentRewards() {
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) return;
        int granted = PatronEquipmentRewardSystem.deliverPending(script.state());
        if (granted > 0) {
            LOG.info("Starsector Marines: delivered " + granted
                    + " pending patron equipment reward(s)");
        }
    }

    private static void repairEquipmentCollectionProgression() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        Set<String> acquiredOrCarried =
                EquipmentTemplateCardInventory.playerUnavailableTemplateIds();
        if (script != null && acquiredOrCarried != null) {
            script.roster().armory().repairCollectionProgression(acquiredOrCarried);
        }
    }

    private static void ensureFactionEquipmentMarketStock() {
        SectorAPI sector = Global.getSector();
        sector.getListenerManager().removeListenerOfClass(FactionEquipmentMarketStock.class);
        sector.getListenerManager().addListener(new FactionEquipmentMarketStock(), true);
        FactionEquipmentMarketStock.refreshAllMarkets();
        LOG.info("Starsector Marines: faction equipment market stock registered");
    }

    private static void ensureCampaignState() {
        SectorAPI sector = Global.getSector();
        CampaignStateScript script = CampaignStateScript.getInstance();
        if (script == null) {
            script = new CampaignStateScript();
            sector.addScript(script);
            LOG.info("Starsector Marines: CampaignStateScript registered");
        }
        CampaignState state = script.state();
        if (state.houseCount == 0) {
            HouseSeeder.seed(state);
        }
    }

    private static void ensurePlayerEventPresenter() {
        SectorAPI sector = Global.getSector();
        if (PlayerEventPresenter.getInstance() != null) return;
        sector.addScript(new PlayerEventPresenter());
        LOG.info("Starsector Marines: PlayerEventPresenter registered");
    }

    /**
     * Grants the campaign-map company button and puts it on the ability bar.
     *
     * <p>Granting and slot assignment are separate concerns: the player may legitimately
     * drag the ability off the bar, and re-adding it every load would fight them. So the
     * slot scan mirrors vanilla's {@code AddAbility} rulecmd — walk all five bars first,
     * and only claim a free slot when the ability is on none of them.
     *
     * <p>Self-defensive: a malformed {@code abilities.csv} row would otherwise take game
     * load down with it, and an entry point is not worth that.
     */
    private static void ensureCompanyViewAbility() {
        SectorAPI sector = Global.getSector();
        try {
            if (sector.getCharacterData() == null) return;
            boolean granted = sector.getCharacterData().getAbilities()
                    .contains(CompanyViewAbility.ABILITY_ID);
            if (!granted) {
                sector.getCharacterData().addAbility(CompanyViewAbility.ABILITY_ID);
                LOG.info("Starsector Marines: company view ability granted");
            }
            assignToFreeAbilitySlot(sector, CompanyViewAbility.ABILITY_ID);
        } catch (RuntimeException e) {
            LOG.warn("Starsector Marines: company view ability unavailable", e);
        }
    }

    /** No-op when the ability already occupies a slot on any bar, or when all are full. */
    private static void assignToFreeAbilitySlot(SectorAPI sector, String abilityId) {
        if (sector.getUIData() == null) return;
        AbilitySlotsAPI slots = sector.getUIData().getAbilitySlotsAPI();
        if (slots == null) return;
        int restoreBar = slots.getCurrBarIndex();
        try {
            for (int bar = 0; bar < ABILITY_BARS; bar++) {
                slots.setCurrBarIndex(bar);
                for (AbilitySlotAPI slot : slots.getCurrSlotsCopy()) {
                    if (abilityId.equals(slot.getAbilityId())) return;
                }
            }
            for (int bar = 0; bar < ABILITY_BARS; bar++) {
                slots.setCurrBarIndex(bar);
                for (AbilitySlotAPI slot : slots.getCurrSlotsCopy()) {
                    if (slot.getAbilityId() == null) {
                        slot.setAbilityId(abilityId);
                        LOG.info("Starsector Marines: company view ability placed on bar "
                                + (bar + 1));
                        return;
                    }
                }
            }
        } finally {
            slots.setCurrBarIndex(restoreBar);
        }
    }

    private static void ensureCampaignDebugIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(CampaignDebugIntel.class) != null) return;
        mgr.addIntel(new CampaignDebugIntel(), true);
        LOG.info("Starsector Marines: CampaignDebugIntel registered (dev)");
    }

    private static void ensureCivilianRescueIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(CivilianRescueIntel.class) != null) return;
        mgr.addIntel(new CivilianRescueIntel(), true);
        LOG.info("Starsector Marines: Distress Net intel registered");
    }

    private static void ensureDefectorAsylumIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(DefectorAsylumIntel.class) != null) return;
        mgr.addIntel(new DefectorAsylumIntel(), true);
        LOG.info("Starsector Marines: Encrypted Channel intel registered");
    }

    private static void ensureDeadLetterIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(DeadLetterIntel.class) != null) return;
        mgr.addIntel(new DeadLetterIntel(), false);
        LOG.info("Starsector Marines: Dead Letter intel registered");
    }

    private static void ensureLastTestamentIntel() {
        IntelManagerAPI mgr = Global.getSector().getIntelManager();
        if (mgr.getFirstIntel(LastTestamentIntel.class) != null) return;
        mgr.addIntel(new LastTestamentIntel(), false);
        LOG.info("Starsector Marines: Last Testament intel registered");
    }

    private static void logRosterContents() {
        MarineRosterScript script = MarineRosterScript.getInstance();
        if (script == null) {
            LOG.warn("Starsector Marines: no roster script after ensure; this should not happen");
            return;
        }
        MarineRoster roster = script.roster();
        LOG.info("Starsector Marines: roster has " + roster.size() + "/" + roster.capacity() + " captains");
        for (MarineCaptain c : roster.all()) {
            LOG.info("  - " + c.name()
                    + " [" + c.id() + "]"
                    + " rank=" + c.rank().displayName()
                    + " status=" + c.status()
                    + " xp=" + c.xp());
        }
    }
}
