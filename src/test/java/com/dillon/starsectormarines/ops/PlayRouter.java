package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.render2d.texture.TextureLease;
import com.dillon.starsectormarines.render2d.texture.TextureLedger;
import com.dillon.starsectormarines.ui.retained.UiSpriteCache;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The screens a mission needs, routed as {@link MarineOpsPanelPlugin} routes them, for {@code playBattle}. Not the
 * plugin itself because its constructor sends a company with no ship to choose one, and the company fleet has not moved
 * behind {@code CampaignBackend} yet; when it has, this can give way to the plugin ({@code campaign-backend.md}).
 *
 * <p>A route to a screen not held here, the mission board or the company's rooms, lands on the debug catalog instead
 * ({@link Context}), which is the way home from a finished mission.
 */
final class PlayRouter {

    /** The shared context, keeping every route inside what is hosted. */
    static class Context extends MarineOpsContext {
        private final Map<ScreenId, Screen> screens;

        Context(Map<ScreenId, Screen> screens) {
            super(null);
            this.screens = screens;
        }

        @Override public void goTo(ScreenId screen) {
            super.goTo(screens.containsKey(screen) ? screen : ScreenId.DEBUG_MISSIONS);
        }
    }

    static Map<ScreenId, Screen> screens() {
        Map<ScreenId, Screen> screens = new EnumMap<>(ScreenId.class);
        screens.put(ScreenId.DEBUG_MISSIONS, new DebugMissionScreen());
        screens.put(ScreenId.BRIEFING, new BriefingScreen());
        screens.put(ScreenId.SQUAD_DEPLOYMENT, new SquadDeploymentScreen());
        screens.put(ScreenId.BATTLE, new BattleScreen());
        screens.put(ScreenId.RESULTS, new ResultsScreen());
        screens.put(ScreenId.LOOT, new LootScreen());
        return screens;
    }

    private final Map<ScreenId, Screen> screens;
    private final MarineOpsContext context;
    private final PositionAPI position;
    private final Runnable close;
    private final OpsMusic music = new OpsMusic(MenuTheme.shared());
    private final TextureLease images = TextureLedger.shared().lease("playBattle ui");
    private ScreenId attached;

    PlayRouter(Map<ScreenId, Screen> screens, MarineOpsContext context, PositionAPI position, Runnable close) {
        this.screens = screens;
        this.context = context;
        this.position = position;
        this.close = close;
        UiSpriteCache.shared().enter(images);
    }

    /** Attaches the screen the context has moved to, before anything ticks or draws it. */
    void sync() {
        ScreenId id = context.getCurrentScreen();
        if (id == attached) return;
        if (attached != null) screens.get(attached).detach();
        attached = id;
        screens.get(id).attach(position, context, close);
        music.entered(id);
    }

    /** Attaches the current screen again, as a dialog does for a new position: a new battle on the battle screen. */
    void reattach() {
        screens.get(attached).attach(position, context, close);
    }

    void processInput(List<InputEventAPI> events) {
        sync();
        screens.get(attached).processInput(events);
    }

    void advance(float dt) {
        sync();
        screens.get(attached).advance(dt);
    }

    void render(float alphaMult) {
        screens.get(attached).render(alphaMult);
    }

    void close() {
        if (attached != null) screens.get(attached).detach();
        attached = null;
        UiSpriteCache.shared().leave(images);
        music.closed();
    }
}
