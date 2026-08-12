package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ops.loot.LootManifest;
import com.dillon.starsectormarines.ops.loot.LootSelection;
import com.dillon.starsectormarines.ops.loot.LootSettlementPlan;
import com.dillon.starsectormarines.ops.loot.LootSettlementService;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_QUADS;
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

/** Budget-aware review screen for the frozen post-battle recovery manifest. */
public final class LootScreen implements Screen {

    private static final Color FRAME = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color BUDGET = new Color(0x80, 0xE0, 0xA0);
    private static final Color META = new Color(0x8F, 0xA8, 0xC0);
    private static final Color NOTICE = new Color(0xE0, 0xB0, 0x70);
    private static final Color FENCE = new Color(0xE0, 0xB0, 0x70);

    private static final float MAX_PANEL_W = 1120f;
    private static final float MAX_PANEL_H = 680f;
    private static final float OUTER_MARGIN = 32f;
    private static final float PAD = 20f;
    private static final float GAP = 12f;
    private static final float CARD_H = 128f;
    private static final float HEADER_H = 104f;
    private static final float FOOTER_H = 104f;
    private static final float BUTTON_W = 190f;
    private static final float BUTTON_H = 36f;

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private LootSelection selection;
    private float panelX;
    private float panelY;
    private float panelW;
    private float panelH;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        LootManifest manifest = ctx != null ? ctx.getLootManifest() : LootManifest.EMPTY;
        if (selection == null || selection.manifest() != manifest) {
            selection = new LootSelection(manifest);
        }
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null || selection == null) return;

        panelW = Math.min(MAX_PANEL_W, Math.max(480f, position.getWidth() - 2f * OUTER_MARGIN));
        panelH = Math.min(MAX_PANEL_H, Math.max(420f, position.getHeight() - 2f * OUTER_MARGIN));
        panelX = position.getX() + (position.getWidth() - panelW) / 2f;
        panelY = position.getY() + (position.getHeight() - panelH) / 2f;

        float headerY = panelY + panelH - PAD;
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD, Strings.get("lootHeader"),
                panelX + PAD, headerY, HEADER));

        LootManifest manifest = selection.manifest();
        String budget = MessageFormat.format(Strings.get("lootBudgetFmt"),
                NumberFormat.getIntegerInstance().format(selection.selectedValue()),
                NumberFormat.getIntegerInstance().format(manifest.selectionBudget));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, budget,
                panelX + PAD, headerY - 34f, BUDGET));

        String pool = MessageFormat.format(Strings.get("lootPoolFmt"),
                manifest.stacks.size(), NumberFormat.getIntegerInstance().format(manifest.totalValue));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, pool,
                panelX + PAD, headerY - 60f, META));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("lootInstructions"),
                panelX + PAD, headerY - 82f, META));

        addGrid(manifest);
        addFooter(LootSettlementService.preview(selection));
    }

    private void addGrid(LootManifest manifest) {
        int count = manifest.stacks.size();
        if (count == 0) return;
        int columns = panelW >= 900f ? 4 : 3;
        int rows = (count + columns - 1) / columns;
        float gridW = panelW - 2f * PAD;
        float cardW = (gridW - (columns - 1) * GAP) / columns;
        float gridTop = panelY + panelH - HEADER_H;
        float gridBottom = panelY + FOOTER_H;
        float availableH = gridTop - gridBottom;
        float cardH = Math.min(CARD_H, (availableH - Math.max(0, rows - 1) * GAP) / rows);

        for (int i = 0; i < count; i++) {
            int column = i % columns;
            int row = i / columns;
            float x = panelX + PAD + column * (cardW + GAP);
            float y = gridTop - (row + 1) * cardH - row * GAP;
            widgets.add(new LootCardWidget(i, manifest.stacks.get(i), selection,
                    x, y, cardW, cardH, this::rebuild));
        }
    }

    private void addFooter(LootSettlementPlan preview) {
        float buttonY = panelY + PAD;
        float backX = panelX + PAD;
        widgets.add(new ButtonWidget(backX, buttonY, BUTTON_W, BUTTON_H,
                () -> ctx.goTo(ScreenId.RESULTS)));
        String back = Strings.get("lootBack");
        float backW = Fonts.ORBITRON_20.measureWidth(back);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, back,
                backX + (BUTTON_W - backW) / 2f, buttonY + BUTTON_H - 6f, HEADER));

        if (preview == null) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("lootCargoUnavailable"),
                    backX + BUTTON_W + 20f, buttonY + BUTTON_H - 6f, NOTICE));
            return;
        }

        float summaryY = buttonY + BUTTON_H + 26f;
        String carry = MessageFormat.format(Strings.get("lootCarryFmt"),
                preview.keptUnits, NumberFormat.getIntegerInstance().format(preview.keptValue));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, carry,
                panelX + PAD, summaryY, BUDGET));
        String fence = MessageFormat.format(Strings.get("lootFenceFmt"),
                preview.fencedUnits, NumberFormat.getIntegerInstance().format(preview.fencedCredits));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, fence,
                panelX + panelW / 2f, summaryY, FENCE));

        float confirmX = panelX + panelW - PAD - BUTTON_W;
        widgets.add(new ButtonWidget(confirmX, buttonY, BUTTON_W, BUTTON_H, this::confirm));
        String confirm = Strings.get("lootConfirm");
        float confirmW = Fonts.ORBITRON_20.measureWidth(confirm);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, confirm,
                confirmX + (BUTTON_W - confirmW) / 2f,
                buttonY + BUTTON_H - 6f, HEADER));
        if (preview.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("lootNothingSelected"),
                    backX + BUTTON_W + 20f, buttonY + BUTTON_H - 6f, NOTICE));
        }
    }

    private void confirm() {
        LootSettlementPlan result = LootSettlementService.settle(ctx, selection);
        if (result == null) {
            rebuild();
            return;
        }
        ctx.clearResolvedMission();
        ctx.goTo(ScreenId.MISSION_SELECT);
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void render(float alphaMult) {
        if (position == null) return;
        drawPanel(alphaMult);
        widgets.render(alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    private void drawPanel(float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(0x10 / 255f, 0x14 / 255f, 0x1E / 255f, 0.96f * alphaMult);
        glBegin(GL_QUADS);
        glVertex2f(panelX, panelY);
        glVertex2f(panelX + panelW, panelY);
        glVertex2f(panelX + panelW, panelY + panelH);
        glVertex2f(panelX, panelY + panelH);
        glEnd();

        glColor4f(FRAME.getRed() / 255f, FRAME.getGreen() / 255f,
                FRAME.getBlue() / 255f, alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(panelX, panelY);
        glVertex2f(panelX + panelW, panelY);
        glVertex2f(panelX + panelW, panelY + panelH);
        glVertex2f(panelX, panelY + panelH);
        glEnd();
    }
}
