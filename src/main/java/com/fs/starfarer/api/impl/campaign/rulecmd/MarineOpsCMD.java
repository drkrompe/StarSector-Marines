package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.dillon.starsectormarines.ops.MarineOpsDialogDelegate;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc.Token;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Rule command referenced from {@code mod/data/campaign/rules.csv} by its simple class
 * name ({@code MarineOpsCMD}). Lives in {@code com.fs.starfarer.api.impl.campaign.rulecmd}
 * because that's one of the five packages Starsector scans when resolving a rulecmd
 * by simple name — mods can't register additional scan packages, so by convention any
 * mod-defined command sits in the same namespace as vanilla ones.
 *
 * <p>Sub-commands:
 * <ul>
 *   <li>{@code MarineOpsCMD open} — swap the current dialog area for the Marine Ops
 *       custom dialog (full-canvas, 3D render via FBO, our own input pipeline).</li>
 * </ul>
 */
public class MarineOpsCMD extends BaseCommandPlugin {

    private static final Logger LOG = Global.getLogger(MarineOpsCMD.class);

    /** Fraction of the parent dialog's reported screen size to occupy. */
    private static final float WIDTH_FRACTION  = 0.92f;
    private static final float HEIGHT_FRACTION = 0.88f;

    @Override
    public boolean execute(String ruleId,
                           InteractionDialogAPI dialog,
                           List<Token> params,
                           Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params == null || params.isEmpty()) return false;

        String sub = params.get(0).getString(memoryMap);
        if ("open".equals(sub)) {
            float w = Global.getSettings().getScreenWidth()  * WIDTH_FRACTION;
            float h = Global.getSettings().getScreenHeight() * HEIGHT_FRACTION;
            LOG.info(String.format("MarineOpsCMD: opening custom dialog at %.0fx%.0f", w, h));

            // Collapse parent dialog chrome so the custom dialog isn't competing for space.
            // The delegate restores these on cancel.
            dialog.hideTextPanel();
            dialog.hideVisualPanel();

            dialog.showCustomVisualDialog(w, h, new MarineOpsDialogDelegate(dialog));
            return true;
        }
        LOG.warn("MarineOpsCMD: unknown sub-command '" + sub + "'");
        return false;
    }
}
