package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Middle column: planet's equirectangular unwrap as a flat "tactical map,"
 * with clickable {@link MissionNodeWidget} markers placed at each mission's
 * normalized position. Markers populate from {@code ctx.selectedClient}'s
 * mission list; the {@code MissionPopupOverlay} renders details for whichever
 * marker is hovered.
 *
 * <p>If no client is selected, the column shows just the planet — the empty
 * state for the loop.
 */
public class TacticalMapPanel extends OpsPanel {

    private static final Logger LOG = Global.getLogger(TacticalMapPanel.class);

    private static final float PAD_INNER = 8f;
    private static final float NODE_SIZE = 26f;

    private final List<MissionNodeWidget> nodes = new ArrayList<>();

    @Override
    public String getHeaderKey() {
        return "colTacticalMap";
    }

    @Override
    protected void onLayout(WidgetRoot widgets) {
        nodes.clear();

        Client selected = ctx.getSelectedClient();
        if (selected == null) return;

        List<Mission> missions = ctx.getMissionsFor(selected);
        if (missions.isEmpty()) return;

        float mapX = rect.x + PAD_INNER;
        float mapY = rect.y + PAD_INNER;
        float mapW = rect.w - 2 * PAD_INNER;
        float mapH = rect.h - 2 * PAD_INNER;

        for (Mission m : missions) {
            float cx = mapX + m.normalizedX * mapW;
            float cy = mapY + m.normalizedY * mapH;
            MissionNodeWidget node = new MissionNodeWidget(m, cx, cy, NODE_SIZE, this::onMissionClicked);
            nodes.add(node);
            widgets.add(node);
        }

        // Popup overlay must render AFTER nodes so it paints on top.
        widgets.add(new MissionPopupOverlay(nodes, mapX, mapY, mapW, mapH));
    }

    private void onMissionClicked(Mission mission) {
        LOG.info("MarineOps: mission clicked id=" + mission.id + " name='" + mission.name + "'");
        ctx.setSelectedMission(mission);
        ctx.goTo(ScreenId.BRIEFING);
    }

    @Override
    public void onRender(float alphaMult) {
        if (ctx.planetTexture == null) return;
        try {
            SpriteAPI flat = Global.getSettings().getSprite(ctx.planetTexture);
            if (flat == null) return;
            // Reset texture region — BriefingScreen crops this same sprite, and
            // SpriteAPI's setTex* values persist on the singleton.
            flat.setTexX(0f);
            flat.setTexY(0f);
            flat.setTexWidth(flat.getTextureWidth());
            flat.setTexHeight(flat.getTextureHeight());
            float drawW = rect.w - 2 * PAD_INNER;
            float drawH = rect.h - 2 * PAD_INNER;
            flat.setSize(drawW, drawH);
            flat.setAlphaMult(alphaMult);
            flat.setNormalBlend();
            flat.render(rect.x + PAD_INNER, rect.y + PAD_INNER);
        } catch (Exception e) {
            LOG.error("TacticalMapPanel: flat unwrap draw failed", e);
        }
    }
}
