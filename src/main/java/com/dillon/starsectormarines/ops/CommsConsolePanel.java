package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
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
 * of the body, dossier stack of offered missions on the right. When a
 * dossier is selected (clicked), it inline-expands in place as a
 * <em>read-only summary</em> (briefing prose + meta) with a
 * <em>Brief &amp; Deploy</em> action. Brief &amp; Deploy hands off to the
 * full-screen {@link BriefingScreen} — the canonical pre-battle surface that
 * owns the commitment controls (transports, fighter cover, captain, salvage)
 * and launches the battle (roadmap command-powers S8 Slice A).
 *
 * <p>Inline-expand state lives on {@link MarineOpsContext#getSelectedMission()}
 * — null = no expansion; non-null = that mission is the expanded one. The
 * screen observes changes and rebuilds. See
 * {@code [[project_comms_officer_narrator]]} memory.
 */
public class CommsConsolePanel extends OpsPanel {

    private static final Logger LOG = Global.getLogger(CommsConsolePanel.class);

    private static final Color FRAME_COLOR    = new Color(0x4A, 0x6B, 0x8C);
    private static final Color EMPTY_HINT     = new Color(0x88, 0xA8, 0xCC);
    private static final Color HEADER_COLOR   = new Color(0xC8, 0xE0, 0xFF);
    private static final Color ACCEPT_COLOR   = new Color(0xC8, 0xFF, 0xE0);

    private static final float PAD            = 12f;
    private static final float HEADER_H       = 28f;
    private static final float HEADER_GAP     = 10f;
    private static final float MAP_W          = 320f;
    private static final float MAP_GAP        = 12f;
    private static final float NODE_SIZE      = 22f;
    private static final float CARD_H         = 72f;
    private static final float CARD_GAP       = 8f;
    private static final float BTN_H          = 32f;
    private static final float BTN_GAP        = 12f;

    /** Track these so the panel can re-render the map sprite + node frame each tick. */
    private float mapDrawX;
    private float mapDrawY;
    private float mapDrawW;
    private float mapDrawH;
    private final List<MissionNodeWidget> mapNodes = new ArrayList<>();

    /** Dossier-stack scroll offset (in cards). Reset on client switch. */
    private int scrollOffset;
    private String lastClientForScroll;

    /**
     * Screen-supplied callback to trigger a full {@code MissionSelectScreen.rebuild()}.
     * Used when the panel needs to re-layout itself in response to a scroll
     * (the screen owns the widget tree, so the panel can't rebuild on its own).
     */
    private Runnable requestRebuild;

    /** Set by {@link MissionSelectScreen} during attach. */
    public void setRequestRebuild(Runnable requestRebuild) {
        this.requestRebuild = requestRebuild;
    }

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
        if (stackW < 240f) {
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
            MissionNodeWidget node = new MissionNodeWidget(m, cx, cy, NODE_SIZE, this::onCardClicked);
            mapNodes.add(node);
            widgets.add(node);
        }
    }

    private void layoutDossierStack(WidgetRoot widgets,
                                    float stackX, float stackTop,
                                    float stackW, float stackH) {
        Client selected = ctx.getSelectedClient();
        if (selected == null) {
            float hintY = stackTop - 24f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Select a client to see their dossiers.",
                    stackX, hintY, EMPTY_HINT));
            return;
        }

        List<Mission> missions = ctx.getMissionsFor(selected);
        if (missions.isEmpty()) {
            float hintY = stackTop - 24f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Nothing pending from this client.",
                    stackX, hintY, EMPTY_HINT));
            return;
        }

        Mission expanded = ctx.getSelectedMission();
        // The expanded card is a read-only summary now (S8 Slice A) — the
        // commitment controls (transports, fighter cover, captain, salvage)
        // live on the full-screen BriefingScreen reached via Brief & Deploy.

        // Reset scroll on client switch — the stack position holds until the
        // user picks a different client.
        String clientKey = selected.identity();
        if (!clientKey.equals(lastClientForScroll)) {
            lastClientForScroll = clientKey;
            scrollOffset = 0;
        }
        // Clamp to a valid range — the offset can outrun the mission list
        // when contracts lapse between rebuilds.
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, missions.size() - 1)));

        // Scroll-capture region — covers the entire stack area, added FIRST so
        // cards drawn on top get click priority while scroll-wheel still falls
        // through (ScrollRegionWidget never absorbs LMB).
        widgets.add(new ScrollRegionWidget(stackX, stackTop - stackH, stackW, stackH,
                this::onScroll));

        float y = stackTop - CARD_H;
        float bottom = stackTop - stackH;
        int lastRendered = scrollOffset - 1;
        for (int i = scrollOffset; i < missions.size(); i++) {
            if (y < bottom) break;
            Mission m = missions.get(i);
            boolean isExpanded = expanded != null && expanded.id.equals(m.id);
            // Render the expanded card from ctx.selectedMission so the
            // negotiated salvage / cash multiplier reflects in-progress edits
            // — the cached mission list is never replaced on adjust, so
            // sourcing the render from there leaves the values stale.
            Mission render = isExpanded ? expanded : m;
            if (isExpanded) {
                float remaining = y + CARD_H - bottom;
                float cardH = computeExpandedHeight(render, stackW);
                cardH = Math.min(cardH, remaining);
                float cardY = y + CARD_H - cardH;
                ExpandedCardWidget card = new ExpandedCardWidget(render, stackX, cardY, stackW, cardH);
                widgets.add(card);
                layoutExpandedSubWidgets(widgets, card);
                y = cardY - CARD_GAP - CARD_H;
            } else {
                DossierCardWidget card = new DossierCardWidget(render, stackX, y, stackW, CARD_H,
                        this::onCardClicked);
                widgets.add(card);
                y -= CARD_H + CARD_GAP;
            }
            lastRendered = i;
        }

        // Scroll indicators — small arrows top/bottom right when more exist.
        if (scrollOffset > 0) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "▲ scroll up",
                    stackX + stackW - 110f, stackTop - 4f, EMPTY_HINT));
        }
        if (lastRendered < missions.size() - 1) {
            int hidden = missions.size() - 1 - lastRendered;
            String s = "▼ " + hidden + " more";
            widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, s,
                    stackX + stackW - 110f, stackTop - stackH + 18f, EMPTY_HINT));
        }
    }

    private void onScroll(int delta) {
        // LWJGL: positive delta = wheel up (scroll content up, show earlier
        // entries). Our offset is the index of the first visible mission, so
        // wheel up DECREASES the offset (show entries above).
        int step = delta > 0 ? -1 : 1;
        Client selected = ctx.getSelectedClient();
        if (selected == null) return;
        List<Mission> missions = ctx.getMissionsFor(selected);
        int next = Math.max(0, Math.min(scrollOffset + step, Math.max(0, missions.size() - 1)));
        if (next == scrollOffset) return;
        scrollOffset = next;
        if (requestRebuild != null) requestRebuild.run();
    }

    /**
     * Lays out the expanded card's action buttons. As of S8 Slice A the card is
     * a read-only summary (title + meta + briefing prose drawn by
     * {@link ExpandedCardWidget}); the only controls are <em>Brief &amp;
     * Deploy</em> (→ full-screen {@link BriefingScreen} for commitment) and
     * <em>Decline</em> (collapse). Added AFTER the card body so they get input
     * priority in {@link WidgetRoot}'s reverse-order dispatch.
     */
    private void layoutExpandedSubWidgets(WidgetRoot widgets, ExpandedCardWidget card) {
        Mission m = card.mission;
        float subX = card.x + ExpandedCardWidget.PAD_X;
        float subW = card.w - 2 * ExpandedCardWidget.PAD_X;

        float btnY = card.y + PAD;
        float btnW = (subW - BTN_GAP) / 2f;
        ButtonWidget brief = new ButtonWidget(subX, btnY, btnW, BTN_H,
                () -> onBriefAndDeploy(m));
        widgets.add(brief);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Brief & Deploy ▸",
                subX + 12f, btnY + BTN_H - 6f, ACCEPT_COLOR));

        float declineX = subX + btnW + BTN_GAP;
        ButtonWidget decline = new ButtonWidget(declineX, btnY, btnW, BTN_H,
                this::onDecline);
        widgets.add(decline);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Decline",
                declineX + 12f, btnY + BTN_H - 6f, HEADER_COLOR));
    }

    /**
     * Height for the read-only expanded card: the header block (title + meta +
     * briefing prose) plus a gap and the action-button row.
     */
    private float computeExpandedHeight(Mission m, float w) {
        float header = ExpandedCardWidget.PAD_Y
                + Fonts.ORBITRON_20_BOLD.getLineHeight()
                + ExpandedCardWidget.ROW_GAP
                + Fonts.ORBITRON_20.getLineHeight()
                + ExpandedCardWidget.SECTION_GAP
                + ExpandedCardWidget.measureBriefingHeight(m.flavor, w - 2 * ExpandedCardWidget.PAD_X);
        float buttonsH = ExpandedCardWidget.SECTION_GAP + BTN_H + PAD;
        return header + ExpandedCardWidget.SECTION_GAP + buttonsH;
    }

    private void onCardClicked(Mission mission) {
        Mission current = ctx.getSelectedMission();
        if (current != null && current.id.equals(mission.id)) {
            // Toggle off — clicking the expanded card collapses it.
            LOG.info("MarineOps: dossier collapsed id=" + mission.id);
            ctx.setSelectedMission(null);
        } else {
            LOG.info("MarineOps: dossier expanded id=" + mission.id + " name='" + mission.name + "'");
            ctx.setSelectedMission(mission);
        }
    }

    private void onDecline() {
        ctx.setSelectedMission(null);
    }

    /**
     * Open the full-screen {@link BriefingScreen} for this mission — the
     * canonical pre-battle surface where the player commits the detachment
     * (transports, fighter cover, captain), negotiates salvage, and deploys.
     * The mission is already the selected one (the card only expands when
     * selected); we set it again defensively and transition.
     */
    private void onBriefAndDeploy(Mission m) {
        if (m == null) return;
        LOG.info("MarineOps: brief & deploy mission id=" + m.id + " name='" + m.name
                + "' type=" + m.type);
        ctx.setSelectedMission(m);
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
