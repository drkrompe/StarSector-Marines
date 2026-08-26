package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.CommanderSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageSiteSnapshot;
import com.dillon.starsectormarines.battle.command.SabotageDefenseSnapshot;

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
        SabotageDefenseSnapshot defense = commander != null
                && commander.detail() instanceof SabotageDefenseSnapshot detail
                ? detail : null;
        if (snapshot == null && defense == null) {
            clear(overlay);
            return;
        }
        List<CellHighlight> sites = new ArrayList<>();
        if (snapshot != null) {
            for (SabotageSiteSnapshot.SiteState site : snapshot.sites()) {
                Color color = site.complete() ? COMPLETE_SITE
                        : site.planterOnSite() || site.progress() > 0f
                        ? PLANTING_SITE : ACTIVE_SITE;
                sites.add(new CellHighlight(site.cellX(), site.cellY(), color));
            }
        } else {
            for (SabotageDefenseSnapshot.SiteState site : defense.sites()) {
                Color color = site.complete() ? COMPLETE_SITE
                        : site.alarmActive() ? PLANTING_SITE : ACTIVE_SITE;
                sites.add(new CellHighlight(site.cellX(), site.cellY(), color));
            }
        }
        List<CellHighlight> selected = new ArrayList<>();
        int selectedSite = snapshot != null
                ? selectedSite(snapshot, selectedSquadId)
                : selectedSite(defense, selectedSquadId);
        int selectedX = snapshot != null && snapshot.site(selectedSite) != null
                ? snapshot.site(selectedSite).cellX()
                : defense != null && defense.site(selectedSite) != null
                ? defense.site(selectedSite).cellX() : -1;
        int selectedY = snapshot != null && snapshot.site(selectedSite) != null
                ? snapshot.site(selectedSite).cellY()
                : defense != null && defense.site(selectedSite) != null
                ? defense.site(selectedSite).cellY() : -1;
        if (selectedX >= 0 && selectedY >= 0) {
            selected.add(new CellHighlight(selectedX - 1, selectedY - 1,
                    3, 3, SELECTED_SITE));
        }
        overlay.put(HighlightOverlay.SRC_SABOTAGE_SITES, sites);
        overlay.put(HighlightOverlay.SRC_SABOTAGE_SELECTED_SITE, selected);
    }

    private static int selectedSite(SabotageSiteSnapshot snapshot, int squadId) {
        SabotageSiteSnapshot.SquadDirective row = snapshot.directiveFor(squadId);
        return row != null ? row.siteIndex() : -1;
    }

    private static int selectedSite(SabotageDefenseSnapshot snapshot, int squadId) {
        SabotageDefenseSnapshot.SquadDirective row = snapshot.directiveFor(squadId);
        return row != null ? row.siteIndex() : -1;
    }

    public static void clear(HighlightOverlay overlay) {
        overlay.clear(HighlightOverlay.SRC_SABOTAGE_SITES);
        overlay.clear(HighlightOverlay.SRC_SABOTAGE_SELECTED_SITE);
    }
}
