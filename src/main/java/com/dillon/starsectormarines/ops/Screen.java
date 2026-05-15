package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.util.List;

/**
 * One full surface of the marine ops dialog. Screens own their own layout,
 * widget tree, and rendering; {@link MarineOpsPanelPlugin} is a thin router
 * that delegates the Starsector panel lifecycle to whichever screen is active.
 *
 * <p>The {@link MarineOpsContext} drives transitions: a screen calls
 * {@code ctx.goTo(...)} and the plugin observes the change and re-attaches.
 *
 * <p>{@link #attach} is idempotent — the plugin calls it on screen activation
 * AND whenever the dialog's position changes, so screens should rebuild their
 * widgets from scratch each call.
 */
public interface Screen {

    /**
     * Make this screen current. Called every time the screen becomes active
     * or the dialog position changes.
     *
     * @param position       the current dialog panel rect (Y-up)
     * @param ctx            shared marine ops state
     * @param dismissDialog  invoke to close the dialog entirely; screens that
     *                       don't need this can ignore it
     */
    void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog);

    void advance(float dt);

    void render(float alphaMult);

    void processInput(List<InputEventAPI> events);

    /**
     * Called by the router when a different screen is becoming active. Default
     * no-op — only override for screens that hold global side-effect state
     * (e.g., custom music, audio loops, suspended default music playback) that
     * must be torn down even though the screen instance lives on for re-entry.
     */
    default void detach() {}
}
