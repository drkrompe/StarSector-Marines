package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINES;
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
 * Two-zone briefing screen. The left zone shows a zoomed crop of the planet's
 * equirectangular unwrap around the mission's normalized coords with a target
 * reticle marking the spot; the right zone lists mission details and the
 * Accept / Back actions.
 *
 * <p>Accept currently no-ops with a log line — mission resolution lands in the
 * next slice. Back returns to {@link ScreenId#MISSION_SELECT}; client + cache
 * state on the context survive the trip.
 */
public class BriefingScreen implements Screen {

    private static final Logger LOG = Global.getLogger(BriefingScreen.class);

    private static final Color FRAME_COLOR   = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER_COLOR  = new Color(0xC8, 0xE0, 0xFF);
    private static final Color LABEL_COLOR   = new Color(0x8F, 0xA8, 0xC0);
    private static final Color VALUE_COLOR   = new Color(0xE0, 0xE8, 0xFF);
    private static final Color ACCEPT_COLOR  = new Color(0xC8, 0xFF, 0xE0);
    private static final Color RETICLE_COLOR = new Color(0xFF, 0xB8, 0x00);

    /** Fraction of the texture's width/height visible inside the map zone. */
    private static final float CROP_FRAC = 0.30f;

    private static final float INNER_PAD     = 12f;
    private static final float ROW_GAP       = 28f;
    private static final float LABEL_COL_W   = 96f;
    private static final float BTN_H         = 32f;
    private static final float BTN_GAP       = 12f;

    private static final int   RETICLE_SEGS  = 24;
    private static final float RETICLE_INNER = 6f;
    private static final float RETICLE_OUTER = 18f;

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BriefingLayout layout;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;
        layout = new BriefingLayout(position);

        Mission m = ctx.getSelectedMission();

        // Map zone header — mission name (large)
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                m != null ? m.name : "",
                layout.mapZone.x, layout.mapZone.headerTextY, HEADER_COLOR));

        // Info zone header — fixed "Briefing"
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("briefingHeader"),
                layout.infoZone.x, layout.infoZone.headerTextY, HEADER_COLOR));

        if (m != null) buildInfoRows(m);
        buildButtons();
    }

    private void buildInfoRows(Mission m) {
        float x = layout.infoZone.x + INNER_PAD;
        float y = layout.infoZone.y + layout.infoZone.h - INNER_PAD;
        float labelX = x;
        float valueX = x + LABEL_COL_W;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupType"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get(m.type.displayKey),
                valueX, y, m.type.color));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupRisk"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get(m.risk.displayKey),
                valueX, y, m.risk.color));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupPayout"),
                labelX, y, LABEL_COLOR));
        String payoutStr = MessageFormat.format(
                Strings.get("payoutFmt"),
                NumberFormat.getIntegerInstance().format(m.payout));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, payoutStr,
                valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupRequires"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, m.requirements,
                valueX, y, VALUE_COLOR));
    }

    private void buildButtons() {
        float availableW = layout.infoZone.w - 2 * INNER_PAD;
        float btnW = (availableW - BTN_GAP) / 2f;
        float btnY = layout.infoZone.y + INNER_PAD;
        float acceptX = layout.infoZone.x + INNER_PAD;
        float backX   = acceptX + btnW + BTN_GAP;

        ButtonWidget accept = new ButtonWidget(acceptX, btnY, btnW, BTN_H, this::onAccept);
        widgets.add(accept);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingAccept"),
                acceptX + INNER_PAD, btnY + BTN_H - 6f, ACCEPT_COLOR));

        ButtonWidget back = new ButtonWidget(backX, btnY, btnW, BTN_H, this::onBack);
        widgets.add(back);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                backX + INNER_PAD, btnY + BTN_H - 6f, HEADER_COLOR));
    }

    private void onAccept() {
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        LOG.info("MarineOps: accept mission id=" + m.id + " name='" + m.name + "'");
        // Mission resolution (consume marines, roll outcome, apply XP/casualties) lands next slice.
    }

    private void onBack() {
        ctx.goTo(ScreenId.MISSION_SELECT);
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (layout == null) return;

        renderMapZone(alphaMult);
        drawFrame(layout.mapZone,  alphaMult);
        drawFrame(layout.infoZone, alphaMult);
        renderReticle(alphaMult);
        widgets.render(alphaMult);
    }

    private void renderMapZone(float alphaMult) {
        if (ctx == null || ctx.planetTexture == null) return;
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        try {
            SpriteAPI sprite = Global.getSettings().getSprite(ctx.planetTexture);
            if (sprite == null) return;

            // setTex* methods take texture-fraction coords (POT-aware) — multiply
            // our normalized image coords by getTextureWidth/Height to scale into
            // the right range. The bare 0.25f examples in the vanilla API work
            // only because those sprites' images fully fill their POT textures.
            float texW = sprite.getTextureWidth();
            float texH = sprite.getTextureHeight();

            float[] crop = cropForMission(m);
            sprite.setTexX(crop[0] * texW);
            sprite.setTexY(crop[1] * texH);
            sprite.setTexWidth(CROP_FRAC * texW);
            sprite.setTexHeight(CROP_FRAC * texH);
            sprite.setSize(layout.mapZone.w, layout.mapZone.h);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.render(layout.mapZone.x, layout.mapZone.y);
        } catch (Exception e) {
            LOG.error("BriefingScreen: cropped terrain render failed", e);
        }
    }

    private void renderReticle(float alphaMult) {
        if (ctx == null) return;
        Mission m = ctx.getSelectedMission();
        if (m == null) return;

        float[] crop = cropForMission(m);
        float cropTexU = crop[0];
        float cropTexV = crop[1];
        float missionTexU = m.normalizedX;
        float missionTexV = m.normalizedY;

        // Mission's position within the visible crop (0..1, V from bottom of crop)
        float fracU = (missionTexU - cropTexU) / CROP_FRAC;
        float fracV = (missionTexV - cropTexV) / CROP_FRAC;

        float screenX = layout.mapZone.x + fracU * layout.mapZone.w;
        float screenY = layout.mapZone.y + fracV * layout.mapZone.h;

        drawCircleLine(screenX, screenY, RETICLE_OUTER, alphaMult);
        drawCircleLine(screenX, screenY, RETICLE_INNER, alphaMult);
        drawCrosshair(screenX, screenY, RETICLE_OUTER + 4f, alphaMult);
    }

    /**
     * @return {@code [cropTexU, cropTexV]} — top-left of the crop window in
     *         normalized image coords (V from top), clamped so the crop stays
     *         inside the image even when the mission sits near an edge.
     */
    /**
     * @return {@code [cropTexU, cropTexV]} — bottom-left of the crop window in
     *         normalized image coords (V from bottom, matching Starsector's GL
     *         load convention), clamped so the crop stays inside the image even
     *         when the mission sits near an edge.
     */
    private static float[] cropForMission(Mission m) {
        float halfCrop = CROP_FRAC / 2f;
        float missionTexU = m.normalizedX;
        float missionTexV = m.normalizedY;
        float cropTexU = clamp(missionTexU - halfCrop, 0f, 1f - CROP_FRAC);
        float cropTexV = clamp(missionTexV - halfCrop, 0f, 1f - CROP_FRAC);
        return new float[] { cropTexU, cropTexV };
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private static void drawFrame(ColumnRect r, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(
                FRAME_COLOR.getRed()   / 255f,
                FRAME_COLOR.getGreen() / 255f,
                FRAME_COLOR.getBlue()  / 255f,
                0.85f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(r.x,         r.y);
        glVertex2f(r.x + r.w,   r.y);
        glVertex2f(r.x + r.w,   r.y + r.h);
        glVertex2f(r.x,         r.y + r.h);
        glEnd();
    }

    private static void drawCircleLine(float cx, float cy, float radius, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(
                RETICLE_COLOR.getRed()   / 255f,
                RETICLE_COLOR.getGreen() / 255f,
                RETICLE_COLOR.getBlue()  / 255f,
                0.9f * alphaMult);
        glLineWidth(1.5f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < RETICLE_SEGS; i++) {
            float a = (float)(2 * Math.PI * i / RETICLE_SEGS);
            glVertex2f(cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius);
        }
        glEnd();
    }

    private static void drawCrosshair(float cx, float cy, float arm, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(
                RETICLE_COLOR.getRed()   / 255f,
                RETICLE_COLOR.getGreen() / 255f,
                RETICLE_COLOR.getBlue()  / 255f,
                0.9f * alphaMult);
        glLineWidth(1.5f);
        // Four arms with a small gap in the middle so the inner circle stays clean.
        float gap = arm * 0.4f;
        glBegin(GL_LINES);
        glVertex2f(cx - arm, cy); glVertex2f(cx - gap, cy);
        glVertex2f(cx + gap, cy); glVertex2f(cx + arm, cy);
        glVertex2f(cx, cy - arm); glVertex2f(cx, cy - gap);
        glVertex2f(cx, cy + gap); glVertex2f(cx, cy + arm);
        glEnd();
    }
}
