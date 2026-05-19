package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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
 */
public class MarineOpsPanelPlugin extends BaseCustomUIPanelPlugin {

    /** Looping intel-screen track declared in mod/data/config/sounds.json. */
    private static final String MUSIC_INTEL = "marines_intel_music";
    /** Crossfade duration (seconds, whole numbers required) for the intel track. */
    private static final int MUSIC_FADE_SECS = 2;
    /**
     * Screens that share the intel track. While the active screen stays inside
     * this set, the music is left alone so MissionSelect → Briefing (and back)
     * is a seamless continue rather than a stop/restart click.
     * {@link ScreenId#BATTLE} is intentionally absent — BattleScreen owns its
     * own crossfade to the battle track via {@code playCustomMusic}.
     */
    private static final Set<ScreenId> INTEL_MUSIC_SCREENS =
            EnumSet.of(ScreenId.MISSION_SELECT, ScreenId.BRIEFING);

    private final MarineOpsContext ctx;
    private final EnumMap<ScreenId, Screen> screens = new EnumMap<>(ScreenId.class);

    private PositionAPI position;
    private Runnable dismissDialog;
    private ScreenId lastScreenId;
    private boolean intelAudioActive;

    public MarineOpsPanelPlugin(PlanetAPI planet) {
        this.ctx = new MarineOpsContext(planet);
        screens.put(ScreenId.MISSION_SELECT, new MissionSelectScreen());
        screens.put(ScreenId.BRIEFING,       new BriefingScreen());
        screens.put(ScreenId.BATTLE,         new BattleScreen());
        screens.put(ScreenId.RESULTS,        new ResultsScreen());
        screens.put(ScreenId.TILESET_DEBUG,  new TilesetDebugScreen());
        screens.put(ScreenId.UNIT_DEBUG,     new UnitSliceDebugScreen());
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
        updateIntelAudio(id);
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
            lastScreenId = id;
            screens.get(id).attach(position, ctx, dismissDialog);
            updateIntelAudio(id);
        }
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
        if (lastScreenId == null) return;
        screens.get(lastScreenId).detach();
        lastScreenId = null;
        stopIntelAudio();
    }

    /**
     * Drive the intel-track lifecycle from screen transitions. Called after
     * the new screen has attached, so when Battle is entering it has already
     * called {@code playCustomMusic} for the battle track and the engine is
     * crossfading; here we just clear our flag so the next return to an
     * intel screen restarts the intel track.
     */
    private void updateIntelAudio(ScreenId id) {
        if (INTEL_MUSIC_SCREENS.contains(id)) {
            startIntelAudio();
        } else if (id == ScreenId.BATTLE) {
            intelAudioActive = false;
        } else {
            stopIntelAudio();
        }
    }

    private void startIntelAudio() {
        if (intelAudioActive) return;
        intelAudioActive = true;
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(true);
        Global.getSoundPlayer().playCustomMusic(MUSIC_FADE_SECS, MUSIC_FADE_SECS, MUSIC_INTEL, true);
    }

    private void stopIntelAudio() {
        if (!intelAudioActive) return;
        intelAudioActive = false;
        Global.getSoundPlayer().playCustomMusic(MUSIC_FADE_SECS, 0, null);
        Global.getSoundPlayer().setSuspendDefaultMusicPlayback(false);
    }
}
