package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;

import java.awt.Color;

/**
 * Left column: list of client {@link ClientRowWidget}s the player can contract
 * with from this planet, with a Back footer that dismisses the dialog.
 *
 * <p>Selection is sticky on the {@link MarineOpsContext} — clicking a row sets
 * {@code ctx.selectedClient}, which {@code TacticalMapPanel} will key off when
 * we wire up missions next slice.
 */
public class ClientListPanel extends OpsPanel {

    private static final Color HEADER_COLOR = new Color(0xC8, 0xE0, 0xFF);

    private static final float BACK_H        = 32f;
    private static final float ROW_H         = 60f;
    private static final float ROW_GAP       = 6f;
    private static final float SIDE_PAD      = 8f;
    private static final float FOOTER_GAP    = 12f;

    private Runnable onBack;

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    @Override
    public String getHeaderKey() {
        return "colClients";
    }

    @Override
    protected void onLayout(WidgetRoot widgets) {
        // Back footer at column bottom
        if (onBack != null) {
            ButtonWidget back = new ButtonWidget(rect.x, rect.y, rect.w, BACK_H, onBack);
            widgets.add(back);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("actionBack"),
                    rect.x + 12f, rect.y + BACK_H - 6f, HEADER_COLOR));
        }

        // Client rows — top-down from the column top
        float rowX = rect.x + SIDE_PAD;
        float rowW = rect.w - 2 * SIDE_PAD;
        float rowsTop = rect.y + rect.h - SIDE_PAD;
        float rowsBottom = rect.y + BACK_H + FOOTER_GAP;

        float y = rowsTop - ROW_H;
        for (Client client : ctx.clients) {
            if (y < rowsBottom) break;
            ClientRowWidget row = new ClientRowWidget(
                    client,
                    rowX, y, rowW, ROW_H,
                    ctx::getSelectedClient,
                    ctx::setSelectedClient);
            widgets.add(row);
            y -= ROW_H + ROW_GAP;
        }
    }

    @Override
    public void onRender(float alphaMult) {
        // Rows render themselves via the widget tree.
    }
}
