package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;

import java.awt.Color;
import java.util.Arrays;
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
 * The three-column mission picker: clients on the left, tactical map in the
 * middle, planet intel on the right. Owns its {@link WidgetRoot} + the three
 * {@link OpsPanel}s and the column-frame chrome around them.
 *
 * <p>Back from the clients column dismisses the dialog (the screen received
 * {@code dismissDialog} at attach time). Clicking a client row mutates
 * {@code ctx.selectedClient}; this screen observes that and rebuilds so the
 * tactical map's mission markers refresh.
 */
public class MissionSelectScreen implements Screen {

    private static final Color FRAME_COLOR  = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER_COLOR = new Color(0xC8, 0xE0, 0xFF);

    private final WidgetRoot widgets = new WidgetRoot();

    private final ClientListPanel clientList = new ClientListPanel();
    private final TacticalMapPanel tacticalMap = new TacticalMapPanel();
    private final PlanetIntelPanel planetIntel = new PlanetIntelPanel();
    private final List<OpsPanel> panels = Arrays.asList(clientList, tacticalMap, planetIntel);

    private PositionAPI position;
    private MarineOpsContext ctx;
    private Runnable dismissDialog;
    private ColumnLayout layout;
    private Client lastSelectedClient;

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        this.dismissDialog = dismissDialog;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        layout = new ColumnLayout(position);

        clientList.setOnBack(dismissDialog);
        clientList.setOnTilesetDebug(() -> ctx.goTo(ScreenId.TILESET_DEBUG));
        clientList.setOnUnitDebug(() -> ctx.goTo(ScreenId.UNIT_DEBUG));
        clientList.attach(layout.left,   ctx, widgets);
        tacticalMap.attach(layout.middle, ctx, widgets);
        planetIntel.attach(layout.right, ctx, widgets);

        // Column headers — built off each panel's i18n key so every column gets
        // the same look without each panel reimplementing.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get(clientList.getHeaderKey()),
                layout.left.x, layout.left.headerTextY, HEADER_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get(tacticalMap.getHeaderKey()),
                layout.middle.x, layout.middle.headerTextY, HEADER_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get(planetIntel.getHeaderKey()),
                layout.right.x, layout.right.headerTextY, HEADER_COLOR));

        lastSelectedClient = ctx.getSelectedClient();
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
        for (OpsPanel p : panels) p.onAdvance(dt);

        // Re-layout when the player picks a different client so the tactical
        // map's mission markers refresh. ClientRowWidgets lose their hover
        // state for one frame on rebuild — acceptable for now.
        Client current = ctx.getSelectedClient();
        if (current != lastSelectedClient) {
            lastSelectedClient = current;
            rebuild();
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (layout == null) return;

        drawFrame(layout.left,   alphaMult);
        drawFrame(layout.middle, alphaMult);
        drawFrame(layout.right,  alphaMult);

        for (OpsPanel p : panels) p.onRender(alphaMult);

        widgets.render(alphaMult);
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
}
