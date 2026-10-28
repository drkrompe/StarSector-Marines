package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.dillon.starsectormarines.ui.retained.UiSpriteCache;
import com.dillon.starsectormarines.ops.battleview.CompanyDeck;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

/**
 * Thin router behind the Marine Ops custom dialog. Owns the shared
 * {@link MarineOpsContext} plus one pre-built {@link Screen} instance per
 * {@link ScreenId}, and delegates the Starsector panel lifecycle (position,
 * advance, render, input) to whichever screen the context says is current.
 *
 * <p>Screen instances persist across transitions — bouncing between mission
 * select and briefing preserves the selected client + mission cache because
 * those live on the context, not on a screen.
 *
 * <p>{@link #setOnBack} is wired by {@link MarineOpsDialogDelegate} to the
 * dialog's dismiss callback; screens receive it on attach and decide whether
 * to invoke it (mission select's Back does; briefing's Back goes to
 * {@link ScreenId#MISSION_SELECT} instead).
 *
 * <p><b>Routing constraint.</b> Operational screens assume a market, a mission,
 * or a battle, and therefore a non-null {@code MarineOpsContext.planet}. A host
 * opened planet-free — the campaign-map company entry from
 * {@link CompanyViewAbility} — starts on {@code COMPANY_HQ} and must never route
 * to {@code MISSION_SELECT}, {@code BRIEFING},
 * {@code SQUAD_DEPLOYMENT}, {@code BATTLE}, {@code RESULTS}, or {@code LOOT}.
 * {@code BARRACKS}, {@code FLEET_ARMORY},
 * {@code MECH_LAB}, {@code BOAT_DECK}, and the
 * dev-only {@code UI_WORKBENCH} are safe: none reads a planet. A planet-scoped
 * host reaches everything as before.
 */
public class MarineOpsPanelPlugin extends BaseCustomUIPanelPlugin {

    private final MarineOpsContext ctx;
    private final EnumMap<ScreenId, Screen> screens = new EnumMap<>(ScreenId.class);

    private PositionAPI position;
    private Runnable dismissDialog;
    private ScreenId lastScreenId;
    private final OpsMusic music = new OpsMusic(MenuTheme.shared());
    /** A frame's share of preloading the deck's art: small, so the page being read never hitches. */
    private static final long DECK_PRELOAD_FRAME_NANOS = 4_000_000L;
    /** The dialog's own retained-UI images, given back when it closes ({@code battle-textures.md}). */
    private final TextureLease uiImages = TextureLedger.shared().lease("ops dialog ui");

    public MarineOpsPanelPlugin(PlanetAPI planet) {
        this(planet, null);
    }

    /**
     * @param seed applied to the fresh context before the first screen attaches, so a
     *             caller that already knows what the player is here to do (an event
     *             popup answering a stationing response) can open directly on it
     *             instead of making them navigate back to it. Null for the ordinary
     *             planet-menu entry, which starts at mission select.
     */
    public MarineOpsPanelPlugin(PlanetAPI planet, Consumer<MarineOpsContext> seed) {
        this.ctx = new MarineOpsContext(planet);
        UiSpriteCache.shared().enter(uiImages);
        screens.put(ScreenId.COMPANY_HQ,     new CompanyHqScreen());
        screens.put(ScreenId.BARRACKS,       new BarracksScreen());
        screens.put(ScreenId.SHIP_TRANSFER,  new ShipTransferScreen());
        screens.put(ScreenId.UI_WORKBENCH,   new UiWorkbenchScreen());
        screens.put(ScreenId.MISSION_SELECT, new MissionSelectScreen());
        screens.put(ScreenId.DEBUG_MISSIONS, new DebugMissionScreen());
        screens.put(ScreenId.FLEET_ARMORY,  new FleetArmoryScreen());
        screens.put(ScreenId.MECH_LAB,      new MechLabScreen());
        screens.put(ScreenId.BOAT_DECK,     new BoatDeckScreen());
        screens.put(ScreenId.STORE,         new StoreScreen());
        screens.put(ScreenId.BRIEFING,       new BriefingScreen());
        screens.put(ScreenId.SQUAD_DEPLOYMENT, new SquadDeploymentScreen());
        screens.put(ScreenId.STATIONING,     new StationingScreen());
        screens.put(ScreenId.POLITY_DOCTRINE, new PolityDoctrineScreen());
        screens.put(ScreenId.SHIP_VIEW,      new ShipViewScreen());
        screens.put(ScreenId.BATTLE,         new BattleScreen());
        screens.put(ScreenId.RESULTS,        new ResultsScreen());
        screens.put(ScreenId.LOOT,           new LootScreen());
        // The founding company moves aboard the converted Second Wind by itself; the
        // transfer screen below is for a company that genuinely has a choice to make.
        CompanyShipDesignation.designateFoundingShip();
        if (seed != null) seed.accept(ctx);
        // A company with nowhere to live has nowhere to be shown. Every room
        // view is a camera on the company ship, so until the player has chosen
        // one there is no operations screen to open — the choice is the screen.
        if (!CompanyShipDesignation.quartered()) ctx.goTo(ScreenId.SHIP_TRANSFER);
        else ctx.goTo(ctx.getCurrentScreen()); // Recapture default and externally seeded entrances alike.
    }

    public void setOnBack(Runnable dismissDialog) {
        this.dismissDialog = dismissDialog;
        tryAttach();
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
        tryAttach();
    }

    private void tryAttach() {
        if (position == null || dismissDialog == null) return;
        ScreenId id = ctx.getCurrentScreen();
        lastScreenId = id;
        screens.get(id).attach(position, ctx, dismissDialog);
        music.entered(id);
    }

    @Override
    public void advance(float amount) {
        if (position == null || dismissDialog == null) return;

        // Pick up screen transitions requested via ctx.goTo() during the
        // previous frame's input handling. Attach the new screen before
        // ticking it so it has its widgets in place.
        ScreenId id = ctx.getCurrentScreen();
        if (id != lastScreenId) {
            if (lastScreenId != null) screens.get(lastScreenId).detach();
            // The deck's art stays resident across the dialog's pages, so going aboard is instant;
            // a battle takes the card for itself and loads what it shares with her behind its own
            // loading screen.
            if (screens.get(id).releasesCompanyDeckTextures()) {
                CompanyDeck deck = ctx.companyDeck();
                if (deck != null) deck.releaseTextures();
            }
            lastScreenId = id;
            screens.get(id).attach(position, ctx, dismissDialog);
            music.entered(id);
        }
        // The ship runs before the page does, and regardless of which page it
        // is. Time aboard belongs to the ship: leave the berthing screen for
        // the lab and the marine who was walking to the mess should be eating
        // when you come back, not still walking. Ticking this from the current
        // screen instead would stop the crew the moment nobody was watching
        // them, which is the difference between a ship and a diorama.
        CompanyDeck ship = ctx.companyDeck();
        if (ship != null) ship.advance(amount);
        // Her art loads a slice a frame while she is not on screen, from the moment the dialog
        // opens and again after a battle, so a deck screen finds it already on the card.
        if (ship != null && !screens.get(id).releasesCompanyDeckTextures()) {
            ship.preloadTextures(DECK_PRELOAD_FRAME_NANOS);
        }
        // Retained room navigation reads the new readiness during screen advance;
        // readiness must not detach/re-attach the player's current page.
        screens.get(id).advance(amount);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        if (position == null || dismissDialog == null) return;
        screens.get(ctx.getCurrentScreen()).processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (position == null || dismissDialog == null) return;
        screens.get(ctx.getCurrentScreen()).render(alphaMult);
    }

    /**
     * Tear down the active screen on dialog close. The Starsector panel
     * lifecycle never calls detach for us — without this hook, screens that
     * hold global side-effect state (custom music, audio loops, suspended
     * default playback) would leak past the dialog's lifetime if the player
     * dismissed mid-screen.
     */
    public void dismiss() {
        if (lastScreenId != null) {
            screens.get(lastScreenId).detach();
            lastScreenId = null;
        }
        // The context is abandoned with the dialog. Release a battle even if
        // the player dismissed mid-fight rather than reaching Results/Loot.
        ctx.setBattleSimulation(null);
        CompanyDeck ship = ctx.companyDeck();
        if (ship != null) ship.dismiss();
        UiSpriteCache.shared().leave(uiImages);
        music.closed();
    }
}
