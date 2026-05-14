package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.render.BridgeRenderer;
import com.dillon.starsectormarines.render.Camera;
import com.dillon.starsectormarines.render.PlanetSphereDrawable;
import com.dillon.starsectormarines.render.SceneNode;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;

import java.awt.Color;
import java.util.List;

/**
 * Right column: planet name (H2), spinning 3D globe of the same equirectangular
 * texture the tactical map shows, and a live-data intel block (type, faction,
 * population, conditions) pulled from {@code planet.getMarket()}.
 *
 * <p>Owns its own {@link BridgeRenderer}, scene node, and globe rotation tick
 * — the plugin doesn't know there's a 3D scene here. Globe area is square
 * sized to column inner width.
 */
public class PlanetIntelPanel extends OpsPanel {

    private static final Color HEADER_COLOR = new Color(0xC8, 0xE0, 0xFF);
    private static final Color INTEL_LABEL  = new Color(0x88, 0xA8, 0xCC);
    private static final Color INTEL_VALUE  = new Color(0xE0, 0xE8, 0xF4);

    private static final float PAD_INNER  = 8f;
    private static final float GLOBE_SPIN = 0.35f;
    private static final int   MAX_CONDS  = 4;

    private final BridgeRenderer renderer = new BridgeRenderer();
    private final SceneNode globeNode = new SceneNode();
    private float globeRot;
    private boolean sceneBuilt;

    @Override
    public String getHeaderKey() {
        return "colIntel";
    }

    @Override
    protected void onLayout(WidgetRoot widgets) {
        ensureScene();

        String planetName = ctx.planet != null ? ctx.planet.getName() : Strings.get("noPlanetFallback");
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                planetName,
                rect.x + PAD_INNER, rect.y + rect.h - 4f, HEADER_COLOR));

        buildIntelBlock(widgets);
    }

    @Override
    public void onAdvance(float dt) {
        if (sceneBuilt) {
            globeRot += dt * GLOBE_SPIN;
            globeNode.rotation[1] = globeRot;
        }
    }

    @Override
    public void onRender(float alphaMult) {
        if (!sceneBuilt) return;
        float globeSide = rect.w - 2 * PAD_INNER;
        float nameRowH = Fonts.ORBITRON_24_BOLD.getLineHeight() + 12f;
        float globeY = rect.y + rect.h - nameRowH - globeSide;
        renderer.render(
                rect.x + PAD_INNER,
                globeY,
                globeSide,
                globeSide,
                alphaMult);
    }

    private void ensureScene() {
        if (sceneBuilt || ctx.planetTexture == null) return;
        globeNode.drawable = new PlanetSphereDrawable(ctx.planetTexture);
        SceneNode root = new SceneNode();
        root.addChild(globeNode);
        Camera camera = new Camera();
        camera.eye[2] = 2.4f;  // unit-radius sphere fills ~72% of its square FBO
        renderer.setScene(root, camera);
        sceneBuilt = true;
    }

    private void buildIntelBlock(WidgetRoot widgets) {
        if (ctx.planet == null) return;

        float nameRowH  = Fonts.ORBITRON_24_BOLD.getLineHeight() + 12f;
        float globeSide = rect.w - 2 * PAD_INNER;
        float intelTop  = rect.y + rect.h - nameRowH - globeSide - PAD_INNER;
        float lineH     = Fonts.ORBITRON_20.getLineHeight() + 6f;
        float x         = rect.x + PAD_INNER;
        float y         = intelTop;

        String typeId = ctx.planet.getTypeId();
        if (typeId != null) {
            addIntelRow(widgets, x, y, Strings.get("intelTypeLabel"), prettifyTypeId(typeId));
            y -= lineH;
        }

        if (ctx.market == null || ctx.market.isPlanetConditionMarketOnly()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("intelUninhabited"),
                    x, y, INTEL_LABEL));
            return;
        }

        if (ctx.market.getFaction() != null) {
            addIntelRow(widgets, x, y,
                    Strings.get("intelFactionLabel"),
                    ctx.market.getFaction().getDisplayName());
            y -= lineH;
        }

        String sizeText = Strings.get("intelSizeFmt").replace("{0}", String.valueOf(ctx.market.getSize()));
        addIntelRow(widgets, x, y, Strings.get("intelSizeLabel"), sizeText);
        y -= lineH;

        List<MarketConditionAPI> conds = ctx.market.getConditions();
        if (conds != null && !conds.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("intelConditionsLabel"),
                    x, y, INTEL_LABEL));
            y -= lineH;
            int shown = 0;
            for (MarketConditionAPI c : conds) {
                if (shown >= MAX_CONDS) break;
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

    private static void addIntelRow(WidgetRoot widgets, float x, float y, String label, String value) {
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
}
