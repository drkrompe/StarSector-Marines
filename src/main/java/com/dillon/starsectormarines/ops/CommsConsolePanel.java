package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.glBegin;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glColor4f;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnd;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL11.glVertex2f;

/**
 * The console region of the mission-select screen — owns the briefing-room
 * layout: officer summary header on top, thumbnail planet map on the left
 * of the body, dossier stack of offered missions on the right.
 *
 * <p>Replaces the old {@link TacticalMapPanel} + {@link PlanetIntelPanel}
 * pair under the list-centric design (see
 * {@code [[project_comms_officer_narrator]]} memory and
 * {@code roadmap/campaign/contracts.md} §"Comms officer is the narrator").
 *
 * <p>Layout (inside {@code rect}):
 * <pre>
 *   y_top ── OfficerHeaderWidget (1 line)
 *            ──────────────────────────────────────────
 *            [ planet thumbnail map ]  [ dossier stack ]
 *            (left fixed-width)        (rest of width)
 * </pre>
 *
 * <p>The dossier stack shows offers for {@link MarineOpsContext#getSelectedClient()}.
 * When no client is selected the stack is empty and the officer header reads
 * the no-client overview line.
 */
public class CommsConsolePanel extends OpsPanel {

    private static final Logger LOG = Global.getLogger(CommsConsolePanel.class);

    private static final Color FRAME_COLOR = new Color(0x4A, 0x6B, 0x8C);
    private static final Color EMPTY_HINT  = new Color(0x88, 0xA8, 0xCC);

    private static final float PAD            = 12f;
    private static final float HEADER_H       = 28f;
    private static final float HEADER_GAP     = 10f;
    private static final float MAP_W          = 320f;
    private static final float MAP_GAP        = 12f;
    private static final float NODE_SIZE      = 22f;
    private static final float CARD_H         = 72f;
    private static final float CARD_GAP       = 8f;

    /** Track these so the panel can re-render the map sprite + node frame each tick. */
    private float mapDrawX;
    private float mapDrawY;
    private float mapDrawW;
    private float mapDrawH;
    private final List<MissionNodeWidget> mapNodes = new ArrayList<>();

    @Override
    public String getHeaderKey() {
        return "colConsole";
    }

    @Override
    protected void onLayout(WidgetRoot widgets) {
        mapNodes.clear();

        // 1. Officer header — full panel width, single line at top.
        float headerY = rect.y + rect.h - HEADER_H;
        widgets.add(new OfficerHeaderWidget(ctx,
                rect.x + PAD, headerY, rect.w - 2 * PAD, HEADER_H));

        // 2. Body row split: map on the left, dossier stack on the right.
        float bodyTop    = headerY - HEADER_GAP;
        float bodyBottom = rect.y + PAD;
        float bodyH      = bodyTop - bodyBottom;
        if (bodyH <= 0f) return;

        // Map area — fixed width, square-ish (clamped to bodyH so it never overflows vertically).
        float mapW = Math.min(MAP_W, rect.w - 2 * PAD - 200f);
        float mapH = Math.min(bodyH, mapW * 0.5f);
        mapDrawX   = rect.x + PAD;
        mapDrawY   = bodyTop - mapH;
        mapDrawW   = mapW;
        mapDrawH   = mapH;

        layoutMapNodes(widgets);

        // Dossier stack — right of the map, fills remaining width.
        float stackX = mapDrawX + mapDrawW + MAP_GAP;
        float stackW = rect.x + rect.w - PAD - stackX;
        if (stackW < 200f) {
            // Console too narrow for both — drop the map area and use full width.
            stackX = rect.x + PAD;
            stackW = rect.w - 2 * PAD;
            mapDrawW = 0f;
        }
        layoutDossierStack(widgets, stackX, bodyTop, stackW, bodyH);
    }

    private void layoutMapNodes(WidgetRoot widgets) {
        if (mapDrawW <= 0f || mapDrawH <= 0f) return;
        Client selected = ctx.getSelectedClient();
        if (selected == null) return;
        List<Mission> missions = ctx.getMissionsFor(selected);
        for (Mission m : missions) {
            float cx = mapDrawX + m.normalizedX * mapDrawW;
            float cy = mapDrawY + m.normalizedY * mapDrawH;
            MissionNodeWidget node = new MissionNodeWidget(m, cx, cy, NODE_SIZE, this::onMissionClicked);
            mapNodes.add(node);
            widgets.add(node);
        }
    }

    private void layoutDossierStack(WidgetRoot widgets,
                                    float stackX, float stackTop,
                                    float stackW, float stackH) {
        Client selected = ctx.getSelectedClient();
        if (selected == null) {
            // Empty-state hint sits where the first card would go so the stack
            // doesn't look broken when nothing's selected — the officer header
            // is doing the heavy lifting in this state anyway.
            float hintY = stackTop - 24f;
            widgets.add(new com.dillon.starsectormarines.ui.LabelWidget(
                    Fonts.ORBITRON_20,
                    "Select a client to see their dossiers.",
                    stackX, hintY, EMPTY_HINT));
            return;
        }

        List<Mission> missions = ctx.getMissionsFor(selected);
        if (missions.isEmpty()) {
            float hintY = stackTop - 24f;
            widgets.add(new com.dillon.starsectormarines.ui.LabelWidget(
                    Fonts.ORBITRON_20,
                    "Nothing pending from this client.",
                    stackX, hintY, EMPTY_HINT));
            return;
        }

        float y = stackTop - CARD_H;
        float bottom = stackTop - stackH;
        for (Mission m : missions) {
            if (y < bottom) break;
            DossierCardWidget card = new DossierCardWidget(m, stackX, y, stackW, CARD_H,
                    this::onMissionClicked);
            widgets.add(card);
            y -= CARD_H + CARD_GAP;
        }
    }

    private void onMissionClicked(Mission mission) {
        LOG.info("MarineOps: dossier selected id=" + mission.id + " name='" + mission.name + "'");
        ctx.setSelectedMission(mission);
        ctx.goTo(ScreenId.BRIEFING);
    }

    @Override
    public void onRender(float alphaMult) {
        if (mapDrawW <= 0f || mapDrawH <= 0f) return;
        renderMapBackground(alphaMult);
        renderMapFrame(alphaMult);
    }

    private void renderMapBackground(float alphaMult) {
        if (ctx.planetTexture == null) return;
        try {
            SpriteAPI flat = Global.getSettings().getSprite(ctx.planetTexture);
            if (flat == null) return;
            flat.setTexX(0f);
            flat.setTexY(0f);
            flat.setTexWidth(flat.getTextureWidth());
            flat.setTexHeight(flat.getTextureHeight());
            flat.setSize(mapDrawW, mapDrawH);
            flat.setAlphaMult(alphaMult);
            flat.setNormalBlend();
            flat.render(mapDrawX, mapDrawY);
        } catch (Exception e) {
            LOG.error("CommsConsolePanel: thumbnail map draw failed", e);
        }
    }

    private void renderMapFrame(float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(
                FRAME_COLOR.getRed()   / 255f,
                FRAME_COLOR.getGreen() / 255f,
                FRAME_COLOR.getBlue()  / 255f,
                0.7f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(mapDrawX,             mapDrawY);
        glVertex2f(mapDrawX + mapDrawW,  mapDrawY);
        glVertex2f(mapDrawX + mapDrawW,  mapDrawY + mapDrawH);
        glVertex2f(mapDrawX,             mapDrawY + mapDrawH);
        glEnd();
    }
}
