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
 * {@code ctx.selectedClient}, which {@code CommsConsolePanel} keys off to
 * populate its dossier stack + map markers.
 */
public class ClientListPanel extends OpsPanel {

    private static final Color HEADER_COLOR = new Color(0xC8, 0xE0, 0xFF);

    private static final float BACK_H        = 32f;
    private static final float ROW_H         = 60f;
    private static final float ROW_GAP       = 6f;
    private static final float SIDE_PAD      = 8f;
    private static final float FOOTER_GAP    = 12f;

    private Runnable onBack;
    private Runnable onTilesetDebug;
    private Runnable onUnitDebug;

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
    }

    public void setOnTilesetDebug(Runnable onTilesetDebug) {
        this.onTilesetDebug = onTilesetDebug;
    }

    public void setOnUnitDebug(Runnable onUnitDebug) {
        this.onUnitDebug = onUnitDebug;
    }

    @Override
    public String getHeaderKey() {
        return "colClients";
    }

    @Override
    protected void onLayout(WidgetRoot widgets) {
        // Footer at column bottom — Back on the left, optional dev buttons (Tiles, Units)
        // packed evenly along the rest of the row. Layout adapts so we don't leave dead
        // space when only Tiles is wired and a future widening drops the Units button.
        int devCount = (onTilesetDebug != null ? 1 : 0) + (onUnitDebug != null ? 1 : 0);
        float backFrac = devCount == 0 ? 1f : (devCount == 1 ? 0.55f : 0.40f);
        if (onBack != null) {
            float backW = rect.w * backFrac - (devCount > 0 ? 4f : 0f);
            ButtonWidget back = new ButtonWidget(rect.x, rect.y, backW, BACK_H, onBack);
            widgets.add(back);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("actionBack"),
                    rect.x + 12f, rect.y + BACK_H - 6f, HEADER_COLOR));
        }
        if (devCount > 0) {
            float devTotalW = rect.w * (1f - backFrac) - 4f;
            float devSlotW = (devTotalW - 4f * (devCount - 1)) / devCount;
            float devX = rect.x + rect.w * backFrac + 4f;
            if (onTilesetDebug != null) {
                ButtonWidget tiles = new ButtonWidget(devX, rect.y, devSlotW, BACK_H, onTilesetDebug);
                widgets.add(tiles);
                widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                        "Tiles",
                        devX + 12f, rect.y + BACK_H - 6f, HEADER_COLOR));
                devX += devSlotW + 4f;
            }
            if (onUnitDebug != null) {
                ButtonWidget units = new ButtonWidget(devX, rect.y, devSlotW, BACK_H, onUnitDebug);
                widgets.add(units);
                widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                        "Units",
                        devX + 12f, rect.y + BACK_H - 6f, HEADER_COLOR));
            }
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
