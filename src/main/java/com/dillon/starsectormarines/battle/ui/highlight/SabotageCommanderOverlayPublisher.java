package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Projects a published Sabotage site picture into map-cell debug marks. */
@DebugOnly
public final class SabotageCommanderOverlayPublisher {

    private static final Color ACTIVE_SITE = new Color(0x50, 0xD8, 0xFF, 0xB0);
    private static final Color PLANTING_SITE = new Color(0xFF, 0xC8, 0x40, 0xD8);
    private static final Color COMPLETE_SITE = new Color(0x60, 0xE8, 0x78, 0xA0);
    private static final Color SELECTED_SITE = new Color(0xFF, 0xFF, 0xFF, 0xFF);

    private SabotageCommanderOverlayPublisher() { }

    public static void publish(HighlightOverlay overlay,
                               CommanderSnapshot<?> commander,
                               int selectedSquadId) {
        SabotageSiteSnapshot snapshot = commander != null
                && commander.detail() instanceof SabotageSiteSnapshot sabotage
                ? sabotage : null;
        if (snapshot == null) {
            clear(overlay);
            return;
        }
        List<CellHighlight> sites = new ArrayList<>();
        for (SabotageSiteSnapshot.SiteState site : snapshot.sites()) {
            Color color = site.complete() ? COMPLETE_SITE
                    : site.planterOnSite() || site.progress() > 0f
                    ? PLANTING_SITE : ACTIVE_SITE;
            sites.add(new CellHighlight(site.cellX(), site.cellY(), color));
        }
        List<CellHighlight> selected = new ArrayList<>();
        SabotageSiteSnapshot.SquadDirective directive =
                snapshot.directiveFor(selectedSquadId);
        if (directive != null) {
            SabotageSiteSnapshot.SiteState site = snapshot.site(directive.siteIndex());
            if (site != null) {
                selected.add(new CellHighlight(site.cellX() - 1, site.cellY() - 1,
                        3, 3, SELECTED_SITE));
            }
        }
        overlay.put(HighlightOverlay.SRC_SABOTAGE_SITES, sites);
        overlay.put(HighlightOverlay.SRC_SABOTAGE_SELECTED_SITE, selected);
    }

    public static void clear(HighlightOverlay overlay) {
        overlay.clear(HighlightOverlay.SRC_SABOTAGE_SITES);
        overlay.clear(HighlightOverlay.SRC_SABOTAGE_SELECTED_SITE);
    }
}
