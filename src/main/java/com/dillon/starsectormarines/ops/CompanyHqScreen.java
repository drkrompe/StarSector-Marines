package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.List;

/**
 * The company's between-contracts home — reached from the campaign map through
 * {@link CompanyViewAbility}, not from a planet.
 *
 * <p><b>Slice 1 stub.</b> This is the shell: the entry point, the planet-free host, and
 * a working dismissal. The three panes it will carry — standing (runway in months of
 * payroll), the deadline-first list of every running clock, and the company itself —
 * arrive in slices 2 through 4. See {@code c10-company-between-contracts.md}.
 *
 * <p>Unlike every other {@link Screen} here, this one runs with a null
 * {@code ctx.planet} and no market. It must therefore read nothing off the context
 * except navigation, and must never route anywhere that assumes a market, a mission,
 * or a battle.
 */
public final class CompanyHqScreen implements Screen {

    private static final Color HEADER = new Color(0xC8, 0xE0, 0xFF);
    private static final Color VALUE = new Color(0xE0, 0xE8, 0xFF);
    private static final Color PENDING = new Color(0x8A, 0x9A, 0xB4);
    private static final float PAD = 24f;
    private static final float ROW = 34f;
    private static final float BTN_H = 32f;
    private static final float BTN_W = 180f;

    private final WidgetRoot widgets = new WidgetRoot();
    private PositionAPI position;
    private Runnable dismissDialog;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.dismissDialog = dismissDialog;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null) return;

        float x = position.getX() + PAD;
        float top = position.getY() + position.getHeight() - PAD;

        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                Strings.get("companyHqHeader"), x, top, HEADER));

        float y = top - 56f;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("companyHqStandingPending"), x, y, PENDING));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("companyHqDeadlinesPending"), x, y, PENDING));
        y -= ROW;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("companyHqRosterPending"), x, y, PENDING));

        float buttonY = position.getY() + PAD;
        widgets.add(new ButtonWidget(x, buttonY, BTN_W, BTN_H, this::onClose));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                x + 14f, buttonY + BTN_H - 8f, VALUE));
    }

    private void onClose() {
        if (dismissDialog != null) dismissDialog.run();
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void render(float alphaMult) {
        widgets.render(alphaMult);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }
}
