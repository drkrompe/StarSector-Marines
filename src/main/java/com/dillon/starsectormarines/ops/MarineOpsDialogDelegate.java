package com.dillon.starsectormarines.ops;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomVisualDialogDelegate;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import org.apache.log4j.Logger;

import java.util.function.Consumer;

/**
 * Delegate for {@link InteractionDialogAPI#showCustomVisualDialog}. Unlike the
 * {@code CustomDialogDelegate} variant, this one does NOT force confirm/cancel
 * buttons onto the dialog chrome — we own the dismiss action entirely via the
 * {@link DialogCallbacks#dismissDialog()} callback delivered to {@link #init}.
 *
 * <p>That property is the reason we use this variant: it means no accidental
 * keyboard shortcut (G/Esc) can yank the player out mid-action. The Back button
 * is a widget in {@link MarineOpsPanelPlugin}'s tree, fully under our control —
 * to gate exit during an in-progress mini-game, just no-op the onBack callback.
 *
 * <p>Holds a reference to the parent interaction dialog so {@link #reportDismissed}
 * can restore the text/visual panels {@code MarineOpsCMD} hid on open, keeping
 * the planet menu intact when the player backs out. A self-triggered opener has no
 * menu to go back to, so it supplies its own {@code onDismissed} that closes the
 * whole interaction instead — restoring empty panels there would leave the player in
 * a dialog with no options and no exit.
 */
public class MarineOpsDialogDelegate implements CustomVisualDialogDelegate {

    private static final Logger LOG = Global.getLogger(MarineOpsDialogDelegate.class);

    private final MarineOpsPanelPlugin panel;
    private final Runnable onDismissed;

    public MarineOpsDialogDelegate(InteractionDialogAPI parent, PlanetAPI planet) {
        this(planet, null, () -> {
            parent.showTextPanel();
            parent.showVisualPanel();
        });
    }

    /**
     * @param seed        applied to the fresh {@link MarineOpsContext}; see
     *                    {@link MarineOpsPanelPlugin#MarineOpsPanelPlugin(PlanetAPI, Consumer)}
     * @param onDismissed what to do with the parent interaction once our panel closes
     */
    public MarineOpsDialogDelegate(PlanetAPI planet, Consumer<MarineOpsContext> seed,
                                   Runnable onDismissed) {
        this.panel = new MarineOpsPanelPlugin(planet, seed);
        this.onDismissed = onDismissed;
    }

    @Override
    public void init(CustomPanelAPI p, DialogCallbacks callbacks) {
        LOG.info("MarineOps: dialog created ("
                + p.getPosition().getWidth() + "x" + p.getPosition().getHeight() + ")");
        panel.setOnBack(callbacks::dismissDialog);
    }

    @Override
    public CustomUIPanelPlugin getCustomPanelPlugin() {
        return panel;
    }

    @Override
    public float getNoiseAlpha() {
        return 0f;
    }

    @Override
    public void advance(float amount) {
    }

    @Override
    public void reportDismissed(int option) {
        panel.dismiss();
        if (onDismissed != null) onDismissed.run();
        LOG.info("MarineOps: dialog dismissed");
    }
}
