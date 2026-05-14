package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;

import java.awt.Color;

/**
 * Left column: faction client list (placeholder until the client-discovery
 * slice lands) and the Back footer that dismisses the dialog. Owns the back
 * button entirely — exit gating during a future mid-mission state lives here
 * by no-oping the onBack callback.
 */
public class ClientListPanel extends OpsPanel {

    private static final Color HEADER_COLOR = new Color(0xC8, 0xE0, 0xFF);

    private static final float BACK_H = 32f;

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
        if (onBack == null) return;

        ButtonWidget back = new ButtonWidget(rect.x, rect.y, rect.w, BACK_H, onBack);
        widgets.add(back);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("actionBack"),
                rect.x + 12f, rect.y + BACK_H - 6f, HEADER_COLOR));

        // TODO: faction client rows above the back footer
    }

    @Override
    public void onRender(float alphaMult) {
        // No non-widget rendering yet — widgets only.
    }
}
