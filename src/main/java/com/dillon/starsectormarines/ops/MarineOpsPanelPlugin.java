package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.EnumMap;
import java.util.List;

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

    private final MarineOpsContext ctx;
    private final EnumMap<ScreenId, Screen> screens = new EnumMap<>(ScreenId.class);

    private PositionAPI position;
    private Runnable dismissDialog;
    private ScreenId lastScreenId;

    public MarineOpsPanelPlugin(PlanetAPI planet) {
        this.ctx = new MarineOpsContext(planet);
        screens.put(ScreenId.MISSION_SELECT, new MissionSelectScreen());
        screens.put(ScreenId.BRIEFING,       new BriefingScreen());
        screens.put(ScreenId.BATTLE,         new BattleScreen());
        screens.put(ScreenId.RESULTS,        new ResultsScreen());
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
    }

    @Override
    public void advance(float amount) {
        if (position == null || dismissDialog == null) return;

        // Pick up screen transitions requested via ctx.goTo() during the
        // previous frame's input handling. Attach the new screen before
        // ticking it so it has its widgets in place.
        ScreenId id = ctx.getCurrentScreen();
        if (id != lastScreenId) {
            lastScreenId = id;
            screens.get(id).attach(position, ctx, dismissDialog);
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
}
