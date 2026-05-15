package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
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
 * Thin orchestrator behind the Marine Ops custom dialog. Owns the shared
 * {@link MarineOpsContext}, the {@link WidgetRoot}, and the three column
 * {@link OpsPanel}s — and delegates every meaningful piece of work to them.
 *
 * <p>Lifecycle responsibilities:
 * <ul>
 *   <li>compute the {@link ColumnLayout} from the current panel size</li>
 *   <li>{@code attach} each panel to its column rect once both position and
 *       onBack callback are available</li>
 *   <li>render the column frames + header labels (visual chrome that doesn't
 *       belong inside any single panel)</li>
 *   <li>route advance/render/processInput through to panels and widgets</li>
 * </ul>
 *
 * <p>No screen-specific logic lives here. When we add a second screen, this
 * class either stays put (one screen per delegate) or grows a screen router —
 * the panel layout is screen-agnostic.
 */
public class MarineOpsPanelPlugin extends BaseCustomUIPanelPlugin {

    private static final Color FRAME_COLOR  = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER_COLOR = new Color(0xC8, 0xE0, 0xFF);

    private final MarineOpsContext ctx;
    private final WidgetRoot widgets = new WidgetRoot();

    private final ClientListPanel clientList = new ClientListPanel();
    private final TacticalMapPanel tacticalMap = new TacticalMapPanel();
    private final PlanetIntelPanel planetIntel = new PlanetIntelPanel();
    private final List<OpsPanel> panels = Arrays.asList(clientList, tacticalMap, planetIntel);

    private PositionAPI position;
    private ColumnLayout layout;
    private Runnable onBack;
    private boolean attached;
    private Client lastSelectedClient;

    public MarineOpsPanelPlugin(PlanetAPI planet) {
        this.ctx = new MarineOpsContext(planet);
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
        clientList.setOnBack(onBack);
        tryAttach();
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
        tryAttach();
    }

    private void tryAttach() {
        if (position == null || onBack == null) return;
        widgets.clear();

        layout = new ColumnLayout(position);
        clientList.attach(layout.left,   ctx, widgets);
        tacticalMap.attach(layout.middle, ctx, widgets);
        planetIntel.attach(layout.right, ctx, widgets);

        // Column headers — built by orchestrator off each panel's i18n key so
        // every column gets the same look without each panel reimplementing.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get(clientList.getHeaderKey()),
                layout.left.x, layout.left.headerTextY, HEADER_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get(tacticalMap.getHeaderKey()),
                layout.middle.x, layout.middle.headerTextY, HEADER_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get(planetIntel.getHeaderKey()),
                layout.right.x, layout.right.headerTextY, HEADER_COLOR));

        attached = true;
    }

    @Override
    public void advance(float amount) {
        widgets.advance(amount);
        for (OpsPanel p : panels) p.onAdvance(amount);

        // Rebuild widget tree when the player picks a different client so the
        // tactical map's mission markers refresh. ClientRowWidgets lose their
        // hover state for one frame on rebuild — acceptable for now.
        Client current = ctx.getSelectedClient();
        if (current != lastSelectedClient) {
            lastSelectedClient = current;
            if (position != null && onBack != null) tryAttach();
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (!attached) return;

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
