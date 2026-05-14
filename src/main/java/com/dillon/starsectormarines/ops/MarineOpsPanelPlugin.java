package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.render.BridgeRenderer;
import com.dillon.starsectormarines.render.Camera;
import com.dillon.starsectormarines.render.PlanetSphereDrawable;
import com.dillon.starsectormarines.render.SceneNode;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
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
 * Three-column Marine Ops screen. Layout (Starsector UI coords, Y-up):
 * <pre>
 *   ┌──────────────────────────────────────┐
 *   │ ┌────┐  ┌────────────┐  ┌─────────┐ │
 *   │ │ L  │  │   Middle   │  │   R     │ │
 *   │ │    │  │ (flat map) │  │ (globe  │ │
 *   │ │    │  │            │  │  + info)│ │
 *   │ └────┘  └────────────┘  └─────────┘ │
 *   └──────────────────────────────────────┘
 * </pre>
 *
 * Left column hosts the faction client list (placeholder until next slice).
 * Middle column draws the planet's equirectangular unwrap directly via
 * {@link SpriteAPI#render} — no FBO needed since it's flat 2D content.
 * Right column hosts the spinning planet globe via {@link BridgeRenderer}
 * bounded to that column's rect; clipping is impossible because the
 * renderer's FBO is sized to the rect.
 *
 * Each column also gets a header label (translated via {@link Strings}) and a
 * thin frame so the structure reads even before content fills in. The Back
 * button is the bottom of the left column — natural footer for an "ops menu."
 */
public class MarineOpsPanelPlugin extends BaseCustomUIPanelPlugin {

    private static final Logger LOG = Global.getLogger(MarineOpsPanelPlugin.class);

    private static final float PAD            = 12f;
    private static final float GAP            = 12f;
    private static final float LEFT_W         = 260f;
    private static final float RIGHT_W        = 340f;
    private static final float HEADER_H       = 28f;
    private static final float HEADER_PAD     = 8f;
    private static final float BACK_H         = 32f;

    private static final Color FRAME_COLOR    = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER_COLOR   = new Color(0xC8, 0xE0, 0xFF);
    private static final Color INTEL_LABEL    = new Color(0x88, 0xA8, 0xCC);
    private static final Color INTEL_VALUE    = new Color(0xE0, 0xE8, 0xF4);

    private static final float GLOBE_SPIN     = 0.35f;

    private final BridgeRenderer renderer = new BridgeRenderer();
    private final WidgetRoot widgets = new WidgetRoot();
    private final SceneNode globeNode = new SceneNode();
    private final PlanetAPI planet;
    private final String planetTexture;

    private PositionAPI position;
    private boolean widgetsBuilt;
    private Runnable onBack;
    private float globeRot;

    public MarineOpsPanelPlugin(PlanetAPI planet) {
        this.planet = planet;
        this.planetTexture = planet != null && planet.getSpec() != null
                ? planet.getSpec().getTexture()
                : null;

        // Scene contains only the spinning globe — the flat unwrap and UI
        // chrome are drawn outside the FBO scene with cheaper 2D primitives.
        SceneNode root = new SceneNode();
        if (planetTexture != null) {
            globeNode.drawable = new PlanetSphereDrawable(planetTexture);
            root.addChild(globeNode);
            LOG.info("MarineOps: planet=" + planet.getName() + " texture=" + planetTexture);
        } else {
            LOG.info("MarineOps: no planet bound; canvas will render empty");
        }
        Camera camera = new Camera();
        camera.eye[2] = 2.4f;  // pulled close so unit-radius sphere fills ~72% of its square FBO
        renderer.setScene(root, camera);
    }

    public void setOnBack(Runnable onBack) {
        this.onBack = onBack;
        tryBuildWidgets();
    }

    @Override
    public void positionChanged(PositionAPI position) {
        this.position = position;
        tryBuildWidgets();
    }

    private void tryBuildWidgets() {
        if (position == null || onBack == null) return;
        widgets.clear();

        Layout L = computeLayout();

        // Column headers — top-left anchored. font baseline accounted for via the label widget.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("colClients"),
                L.leftX, L.headerTextY, HEADER_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("colTacticalMap"),
                L.middleX, L.headerTextY, HEADER_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("colIntel"),
                L.rightX, L.headerTextY, HEADER_COLOR));

        // Planet name as H2 — top of right column body, above the globe
        String planetName = planet != null ? planet.getName() : Strings.get("noPlanetFallback");
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                planetName,
                L.rightX + 8f, L.bodyTop - 4f, HEADER_COLOR));

        // Planet intel block — Orbitron 20 rows under the globe area
        buildIntelBlock(L);

        // Back button — bottom of left column, full-width
        widgets.add(new ButtonWidget(L.leftX, L.backY, LEFT_W, BACK_H, onBack));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get("actionBack"),
                L.leftX + 12f, L.backY + BACK_H - 6f, HEADER_COLOR));

        widgetsBuilt = true;
    }

    @Override
    public void advance(float amount) {
        widgets.advance(amount);
        if (planetTexture != null) {
            globeRot += amount * GLOBE_SPIN;
            globeNode.rotation[1] = globeRot;
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (position == null) return;

        Layout L = computeLayout();

        // Column frames first so they sit under everything else
        drawFrame(L.leftX,   L.bodyBottom, LEFT_W,   L.bodyTop - L.bodyBottom, alphaMult);
        drawFrame(L.middleX, L.bodyBottom, L.middleW, L.bodyTop - L.bodyBottom, alphaMult);
        drawFrame(L.rightX,  L.bodyBottom, RIGHT_W,  L.bodyTop - L.bodyBottom, alphaMult);

        // Middle column — flat planet unwrap. Direct sprite draw, no FBO.
        if (planetTexture != null) {
            try {
                SpriteAPI flat = Global.getSettings().getSprite(planetTexture);
                if (flat != null) {
                    float padInner = 8f;
                    float drawW = L.middleW - 2 * padInner;
                    float drawH = L.bodyTop - L.bodyBottom - 2 * padInner;
                    flat.setSize(drawW, drawH);
                    flat.setAlphaMult(alphaMult);
                    flat.setNormalBlend();
                    flat.render(L.middleX + padInner, L.bodyBottom + padInner);
                }
            } catch (Exception e) {
                LOG.error("MarineOps: flat unwrap draw failed", e);
            }
        }

        // Right column — spinning globe. Square 1:1 area sized to the column's
        // inner width, positioned right under the planet-name H2. FBO sub-rect
        // is the clipping region, so no global clipping needed.
        if (planetTexture != null) {
            float padInner = 8f;
            float globeSide = RIGHT_W - 2 * padInner;
            float nameRowH = Fonts.ORBITRON_24_BOLD.getLineHeight() + 12f;
            float globeY = L.bodyTop - nameRowH - globeSide;
            renderer.render(
                    L.rightX + padInner,
                    globeY,
                    globeSide,
                    globeSide,
                    alphaMult);
        }

        // Widgets last so labels + buttons paint on top of everything.
        if (widgetsBuilt) widgets.render(alphaMult);
    }

    /**
     * Lays out the planet intel block in the right column below the globe.
     * Reads market data when available (faction, size, conditions); falls back
     * to a single "Uninhabited" line otherwise. All labels routed through
     * {@link Strings} so translators can localize.
     */
    private void buildIntelBlock(Layout L) {
        if (planet == null) return;

        float padInner = 8f;
        float nameRowH = Fonts.ORBITRON_24_BOLD.getLineHeight() + 12f;
        float globeAreaH = RIGHT_W - 2 * padInner;  // square area, matches column width
        float intelTop = L.bodyTop - nameRowH - globeAreaH - padInner;
        float lineH = Fonts.ORBITRON_20.getLineHeight() + 6f;
        float x = L.rightX + padInner;
        float y = intelTop;

        String typeId = planet.getTypeId();
        if (typeId != null) {
            String pretty = prettifyTypeId(typeId);
            addIntelRow(x, y, Strings.get("intelTypeLabel"), pretty);
            y -= lineH;
        }

        MarketAPI market = planet.getMarket();
        if (market == null || market.isPlanetConditionMarketOnly()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("intelUninhabited"),
                    x, y, INTEL_LABEL));
            return;
        }

        if (market.getFaction() != null) {
            addIntelRow(x, y,
                    Strings.get("intelFactionLabel"),
                    market.getFaction().getDisplayName());
            y -= lineH;
        }

        String sizeText = Strings.get("intelSizeFmt").replace("{0}", String.valueOf(market.getSize()));
        addIntelRow(x, y, Strings.get("intelSizeLabel"), sizeText);
        y -= lineH;

        List<MarketConditionAPI> conds = market.getConditions();
        if (conds != null && !conds.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("intelConditionsLabel"),
                    x, y, INTEL_LABEL));
            y -= lineH;
            int shown = 0;
            for (MarketConditionAPI c : conds) {
                if (shown >= 4) break;
                String name = c.getSpec() != null ? c.getSpec().getName() : c.getId();
                if (name == null || name.isEmpty()) continue;
                widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                        "  " + name,
                        x, y, INTEL_VALUE));
                y -= lineH;
                shown++;
            }
        }
    }

    private void addIntelRow(float x, float y, String label, String value) {
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, label, x, y, INTEL_LABEL));
        float labelW = Fonts.ORBITRON_20.measureWidth(label);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, value, x + labelW + 8f, y, INTEL_VALUE));
    }

    /** "terran_eccentric" -> "Terran Eccentric" — vanilla type ids are snake_case. */
    private static String prettifyTypeId(String id) {
        StringBuilder out = new StringBuilder(id.length());
        boolean upper = true;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '_') {
                out.append(' ');
                upper = true;
            } else if (upper) {
                out.append(Character.toUpperCase(c));
                upper = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Static layout per current panel size — cheap to recompute, avoids stale cached coords. */
    private Layout computeLayout() {
        Layout L = new Layout();
        L.contentX = position.getX() + PAD;
        L.contentY = position.getY() + PAD;
        L.contentW = position.getWidth() - 2 * PAD;
        L.contentH = position.getHeight() - 2 * PAD;

        L.leftX   = L.contentX;
        L.middleW = L.contentW - LEFT_W - RIGHT_W - 2 * GAP;
        L.middleX = L.leftX + LEFT_W + GAP;
        L.rightX  = L.middleX + L.middleW + GAP;

        L.bodyTop      = L.contentY + L.contentH - HEADER_H - HEADER_PAD;
        L.bodyBottom   = L.contentY;
        L.headerTextY  = L.contentY + L.contentH;   // top of canvas; LabelWidget draws downward
        L.subHeaderTextY = L.bodyTop - 4f;          // just inside column body, top-aligned

        L.backY = L.contentY;
        return L;
    }

    /** Thin border rectangle in immediate mode. */
    private static void drawFrame(float x, float y, float w, float h, float alphaMult) {
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
        glVertex2f(x,     y);
        glVertex2f(x + w, y);
        glVertex2f(x + w, y + h);
        glVertex2f(x,     y + h);
        glEnd();
    }

    private static final class Layout {
        float contentX, contentY, contentW, contentH;
        float leftX, middleX, middleW, rightX;
        float bodyTop, bodyBottom;
        float headerTextY, subHeaderTextY;
        float backY;
    }
}
