package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.DevConfig;
import com.dillon.starsectormarines.battle.BattleSetup;
import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.flyby.PlayerFleetWings;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * The console region of the mission-select screen — owns the briefing-room
 * layout: officer summary header on top, thumbnail planet map on the left
 * of the body, dossier stack of offered missions on the right. When a
 * dossier is selected (clicked), it inline-expands in place with the full
 * briefing prose + salvage slider + transport toggles + captain rows +
 * accept/decline. The old separate BriefingScreen is unreachable from
 * this surface as of step 4 of the list-view plan.
 *
 * <p>Inline-expand state lives on {@link MarineOpsContext#getSelectedMission()}
 * — null = no expansion; non-null = that mission is the expanded one. The
 * screen observes changes and rebuilds. See
 * {@code [[project_comms_officer_narrator]]} memory.
 */
public class CommsConsolePanel extends OpsPanel {

    private static final Logger LOG = Global.getLogger(CommsConsolePanel.class);

    private static final Color FRAME_COLOR    = new Color(0x4A, 0x6B, 0x8C);
    private static final Color EMPTY_HINT     = new Color(0x88, 0xA8, 0xCC);
    private static final Color LABEL_COLOR    = new Color(0x88, 0xA8, 0xCC);
    private static final Color VALUE_COLOR    = new Color(0xE0, 0xE8, 0xF4);
    private static final Color HEADER_COLOR   = new Color(0xC8, 0xE0, 0xFF);
    private static final Color ACCEPT_COLOR   = new Color(0xC8, 0xFF, 0xE0);
    private static final Color BLOCKED_COLOR  = new Color(0xFF, 0x80, 0x80);

    private static final float PAD            = 12f;
    private static final float HEADER_H       = 28f;
    private static final float HEADER_GAP     = 10f;
    private static final float MAP_W          = 320f;
    private static final float MAP_GAP        = 12f;
    private static final float NODE_SIZE      = 22f;
    private static final float CARD_H         = 72f;
    private static final float CARD_GAP       = 8f;
    private static final float SLIDER_BTN_W   = 24f;
    private static final float ROW_H          = 26f;
    private static final float ROW_GAP        = 4f;
    private static final float SECTION_GAP    = 14f;
    private static final float BTN_H          = 32f;
    private static final float BTN_GAP        = 12f;
    private static final float SQUAD_ROW_H    = 32f;

    /** Employer Aeroshuttle cap — keeps the ramp from feeling like a deluge. */
    private static final int   EMPLOYER_PHYSICAL_CAP = 3;

    /** Track these so the panel can re-render the map sprite + node frame each tick. */
    private float mapDrawX;
    private float mapDrawY;
    private float mapDrawW;
    private float mapDrawH;
    private final List<MissionNodeWidget> mapNodes = new ArrayList<>();

    /** Per-mission UI state — reset on mission switch via {@link #lastExpandedMissionId}. */
    private final Set<Integer> deselectedTransports = new HashSet<>();
    private String lastExpandedMissionId;
    private List<ShuttleType> cachedAvailable = Collections.emptyList();

    @Override
    public String getHeaderKey() {
        return "colConsole";
    }

    @Override
    protected void onLayout(WidgetRoot widgets) {
        mapNodes.clear();

        // 1. Officer header — full panel width, single line at top.
        float headerY = rect.y + rect.h - HEADER_H;
        widgets.add(new OfficerHeaderWidget(ctx,
                rect.x + PAD, headerY, rect.w - 2 * PAD, HEADER_H));

        // 2. Body row split: map on the left, dossier stack on the right.
        float bodyTop    = headerY - HEADER_GAP;
        float bodyBottom = rect.y + PAD;
        float bodyH      = bodyTop - bodyBottom;
        if (bodyH <= 0f) return;

        // Map area — fixed width, square-ish (clamped to bodyH so it never overflows vertically).
        float mapW = Math.min(MAP_W, rect.w - 2 * PAD - 200f);
        float mapH = Math.min(bodyH, mapW * 0.5f);
        mapDrawX   = rect.x + PAD;
        mapDrawY   = bodyTop - mapH;
        mapDrawW   = mapW;
        mapDrawH   = mapH;

        layoutMapNodes(widgets);

        // Dossier stack — right of the map, fills remaining width.
        float stackX = mapDrawX + mapDrawW + MAP_GAP;
        float stackW = rect.x + rect.w - PAD - stackX;
        if (stackW < 240f) {
            // Console too narrow for both — drop the map area and use full width.
            stackX = rect.x + PAD;
            stackW = rect.w - 2 * PAD;
            mapDrawW = 0f;
        }
        layoutDossierStack(widgets, stackX, bodyTop, stackW, bodyH);
    }

    private void layoutMapNodes(WidgetRoot widgets) {
        if (mapDrawW <= 0f || mapDrawH <= 0f) return;
        Client selected = ctx.getSelectedClient();
        if (selected == null) return;
        List<Mission> missions = ctx.getMissionsFor(selected);
        for (Mission m : missions) {
            float cx = mapDrawX + m.normalizedX * mapDrawW;
            float cy = mapDrawY + m.normalizedY * mapDrawH;
            MissionNodeWidget node = new MissionNodeWidget(m, cx, cy, NODE_SIZE, this::onCardClicked);
            mapNodes.add(node);
            widgets.add(node);
        }
    }

    private void layoutDossierStack(WidgetRoot widgets,
                                    float stackX, float stackTop,
                                    float stackW, float stackH) {
        Client selected = ctx.getSelectedClient();
        if (selected == null) {
            float hintY = stackTop - 24f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Select a client to see their dossiers.",
                    stackX, hintY, EMPTY_HINT));
            return;
        }

        List<Mission> missions = ctx.getMissionsFor(selected);
        if (missions.isEmpty()) {
            float hintY = stackTop - 24f;
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Nothing pending from this client.",
                    stackX, hintY, EMPTY_HINT));
            return;
        }

        Mission expanded = ctx.getSelectedMission();
        // Reset per-mission state when the expanded mission changes.
        String expandedId = expanded != null ? expanded.id : null;
        if (expandedId == null ? lastExpandedMissionId != null
                                : !expandedId.equals(lastExpandedMissionId)) {
            lastExpandedMissionId = expandedId;
            deselectedTransports.clear();
            // Default captain — first ACTIVE, if nothing's set. Mirrors the
            // pre-inline-expand BriefingScreen behavior so accept works
            // without forcing a captain click.
            if (expanded != null && ctx.getSelectedCaptainId() == null) {
                MarineRosterScript scr = MarineRosterScript.getInstance();
                if (scr != null) {
                    List<MarineCaptain> active = scr.roster().active();
                    if (!active.isEmpty()) ctx.setSelectedCaptainId(active.get(0).id());
                }
            }
        }
        cachedAvailable = PlayerFleetShuttles.queryAvailable();

        float y = stackTop - CARD_H;
        float bottom = stackTop - stackH;
        for (Mission m : missions) {
            if (y < bottom) break;
            boolean isExpanded = expanded != null && expanded.id.equals(m.id);
            if (isExpanded) {
                // Reserve the full remaining vertical space for the expanded card.
                float remaining = y + CARD_H - bottom;
                float cardH = computeExpandedHeight(m, stackW);
                cardH = Math.min(cardH, remaining);
                float cardY = y + CARD_H - cardH;
                ExpandedCardWidget card = new ExpandedCardWidget(m, stackX, cardY, stackW, cardH);
                widgets.add(card);
                layoutExpandedSubWidgets(widgets, card);
                y = cardY - CARD_GAP - CARD_H;
            } else {
                DossierCardWidget card = new DossierCardWidget(m, stackX, y, stackW, CARD_H,
                        this::onCardClicked);
                widgets.add(card);
                y -= CARD_H + CARD_GAP;
            }
        }
    }

    /**
     * Lays out the slider buttons, transport toggles, captain rows, and
     * accept/decline buttons inside the expanded card. Sub-widgets are added
     * AFTER the card body to the same widget tree so they get input priority
     * in {@link WidgetRoot}'s reverse-order dispatch.
     */
    private void layoutExpandedSubWidgets(WidgetRoot widgets, ExpandedCardWidget card) {
        Mission m = card.mission;
        float subX = card.x + ExpandedCardWidget.PAD_X;
        float subW = card.w - 2 * ExpandedCardWidget.PAD_X;
        float y = card.subWidgetTopY();

        // Salvage slider — only when the mission carries a salvage cap (contract-bound).
        int salvageBaseline = m.salvageBaseline & 0xFF;
        if (salvageBaseline > 0) {
            int negotiated = m.salvageNegotiated & 0xFF;
            int cashMult = m.cashMultiplier & 0xFF;
            if (cashMult <= 0) cashMult = 100;
            int cashBonus = cashMult - 100;
            String label = "Salvage: " + negotiated + "%  (cash bonus +" + cashBonus + "%)";
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, label, subX, y, LABEL_COLOR));

            float btnY = y - SLIDER_BTN_W + 4f;
            float plusX  = card.x + card.w - ExpandedCardWidget.PAD_X - SLIDER_BTN_W;
            float minusX = plusX - SLIDER_BTN_W - 4f;
            widgets.add(new ButtonWidget(minusX, btnY, SLIDER_BTN_W, SLIDER_BTN_W,
                    () -> adjustSalvage(-10)));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "–", minusX + 8f, y, HEADER_COLOR));
            widgets.add(new ButtonWidget(plusX, btnY, SLIDER_BTN_W, SLIDER_BTN_W,
                    () -> adjustSalvage(+10)));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "+", plusX + 8f, y, HEADER_COLOR));
            y -= ROW_H + ROW_GAP;
        }

        // Transport selection — one toggle row per available shuttle.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Transport:", subX, y, LABEL_COLOR));
        y -= ROW_H;
        for (int i = 0; i < cachedAvailable.size(); i++) {
            final int idx = i;
            ShuttleType type = cachedAvailable.get(i);
            boolean selectedShuttle = !deselectedTransports.contains(idx);
            String marker = selectedShuttle ? "[x]" : "[ ]";
            String rowLabel = marker + " 1× " + shuttleDisplayName(type)
                    + (selectedShuttle ? "" : " — held back");
            Color rowColor = selectedShuttle ? VALUE_COLOR : LABEL_COLOR;
            ButtonWidget toggle = new ButtonWidget(subX, y - ROW_H + 6f, subW, ROW_H,
                    () -> {
                        if (deselectedTransports.contains(idx)) deselectedTransports.remove(idx);
                        else deselectedTransports.add(idx);
                    });
            widgets.add(toggle);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, rowLabel,
                    subX + 6f, y, rowColor));
            y -= ROW_H + ROW_GAP;
        }
        if (m.employerShuttles > 0) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    m.employerShuttles + "× employer Aeroshuttle",
                    subX + 6f, y, VALUE_COLOR));
            y -= ROW_H + ROW_GAP;
        }

        // Captain rows.
        MarineRosterScript scr = MarineRosterScript.getInstance();
        List<MarineCaptain> captains = scr != null
                ? scr.roster().active() : Collections.<MarineCaptain>emptyList();
        y -= SECTION_GAP - ROW_GAP;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "Captain:", subX, y, HEADER_COLOR));
        y -= SQUAD_ROW_H;
        if (captains.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "No active captains available.", subX, y, LABEL_COLOR));
            y -= SQUAD_ROW_H;
        } else {
            float buttonsTop = card.y + PAD + BTN_H + SECTION_GAP;
            for (MarineCaptain c : captains) {
                if (y < buttonsTop) break;
                widgets.add(new CaptainRowWidget(c, subX, y - SQUAD_ROW_H + 4f,
                        subW, SQUAD_ROW_H,
                        ctx::getSelectedCaptainId, ctx::setSelectedCaptainId));
                y -= SQUAD_ROW_H + ROW_GAP;
            }
        }

        // Accept / Decline.
        float btnY = card.y + PAD;
        float btnW = (subW - BTN_GAP) / 2f;
        boolean canAccept = isTransportSufficient(m, effectivePlayerShuttles());
        ButtonWidget accept = new ButtonWidget(subX, btnY, btnW, BTN_H,
                canAccept ? () -> onAccept(m) : null);
        widgets.add(accept);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                canAccept ? "Accept" : "Insufficient Transport",
                subX + 12f, btnY + BTN_H - 6f,
                canAccept ? ACCEPT_COLOR : BLOCKED_COLOR));
        float declineX = subX + btnW + BTN_GAP;
        ButtonWidget decline = new ButtonWidget(declineX, btnY, btnW, BTN_H,
                this::onDecline);
        widgets.add(decline);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Decline",
                declineX + 12f, btnY + BTN_H - 6f, HEADER_COLOR));
    }

    /** Total height required for the expanded card based on its contents. */
    private float computeExpandedHeight(Mission m, float w) {
        float header = ExpandedCardWidget.PAD_Y
                + Fonts.ORBITRON_20_BOLD.getLineHeight()
                + ExpandedCardWidget.ROW_GAP
                + Fonts.ORBITRON_20.getLineHeight()
                + ExpandedCardWidget.SECTION_GAP
                + ExpandedCardWidget.measureBriefingHeight(m.flavor, w - 2 * ExpandedCardWidget.PAD_X);

        int transportRows = cachedAvailable.size() + (m.employerShuttles > 0 ? 1 : 0);
        int captainRows = 0;
        MarineRosterScript scr = MarineRosterScript.getInstance();
        if (scr != null) captainRows = Math.min(3, scr.roster().active().size());
        if (captainRows == 0) captainRows = 1; // "no active captains" line

        float sliderH = (m.salvageBaseline & 0xFF) > 0 ? (ROW_H + ROW_GAP) : 0f;
        float transportH = ROW_H + transportRows * (ROW_H + ROW_GAP); // label + rows
        float captainH = SECTION_GAP + SQUAD_ROW_H + captainRows * (SQUAD_ROW_H + ROW_GAP);
        float buttonsH = SECTION_GAP + BTN_H + PAD;

        return header + ExpandedCardWidget.SECTION_GAP
                + sliderH + transportH + captainH + buttonsH;
    }

    private void onCardClicked(Mission mission) {
        Mission current = ctx.getSelectedMission();
        if (current != null && current.id.equals(mission.id)) {
            // Toggle off — clicking the expanded card collapses it.
            LOG.info("MarineOps: dossier collapsed id=" + mission.id);
            ctx.setSelectedMission(null);
        } else {
            LOG.info("MarineOps: dossier expanded id=" + mission.id + " name='" + mission.name + "'");
            ctx.setSelectedMission(mission);
        }
    }

    private void onDecline() {
        ctx.setSelectedMission(null);
    }

    /**
     * Apply the player's salvage/cash negotiation to the expanded mission. The
     * Mission object is immutable so we replace {@link MarineOpsContext#setSelectedMission}
     * with a new instance — same id, adjusted negotiated + cashMultiplier.
     *
     * <p>Curve per {@code roadmap/campaign/contracts.md} §"Salvage Layer 2":
     * cashMultiplier = 100 + (baseline - negotiated) * 0.5.
     */
    private void adjustSalvage(int delta) {
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        int baseline = m.salvageBaseline & 0xFF;
        if (baseline <= 0) return;
        int current = m.salvageNegotiated & 0xFF;
        int next = Math.max(0, Math.min(baseline, current + delta));
        if (next == current) return;
        int cashMult = 100 + (baseline - next) / 2;
        Mission replaced = new Mission(
                m.id, m.name, m.type, m.source, m.payout, m.risk, m.requirements, m.flavor,
                m.normalizedX, m.normalizedY, m.clientFighterSupport, m.enemyFighterSupport,
                m.requiredDrops, m.employerShuttles, m.targetPlanetName, m.targetIndustryId,
                m.contractId, m.salvageBaseline, (byte) next, (byte) cashMult);
        ctx.setSelectedMission(replaced);
    }

    /**
     * Build the BattleSimulation and transition to BATTLE. Ported from the old
     * BriefingScreen — same accept semantics so resolver writeback + battle setup
     * are unchanged.
     */
    private void onAccept(Mission m) {
        if (m == null) return;
        MarineCaptain c = ctx.getSelectedCaptain();
        String captainStr = c != null ? c.id() + " (" + c.name() + ")" : "none";
        LOG.info("MarineOps: accept (inline) mission id=" + m.id + " name='" + m.name
                + "' type=" + m.type + " captain=" + captainStr);

        List<ShuttleAssignment> manifest = buildShuttleManifest(m, effectivePlayerShuttles());
        boolean enemyHasHeavyArmor = planetHasHeavyArmaments(m.targetPlanetName);

        BattleSimulation sim;
        long seed = System.currentTimeMillis();
        switch (m.type) {
            case SABOTAGE:
                sim = BattleSetup.createSabotage(seed, manifest, enemyHasHeavyArmor, m.risk);
                break;
            case CONQUEST:
                sim = BattleSetup.createConquest(seed, manifest, enemyHasHeavyArmor, m.risk);
                break;
            case ASSAULT:
            case RAID:
            case EXTRACTION:
            default:
                sim = BattleSetup.createPlaceholder(seed, manifest, enemyHasHeavyArmor, m.risk, m.type);
        }
        sim.setFlybyRoster(FlybyRoster.combine(
                effectiveAlliedRoster(m), m.enemyFighterSupport));
        ctx.setBattleSimulation(sim);
        ctx.goTo(ScreenId.BATTLE);
    }

    private FlybyRoster effectiveAlliedRoster(Mission m) {
        return FlybyRoster.combine(m.clientFighterSupport, PlayerFleetWings.fromPlayerFleet());
    }

    private List<ShuttleType> effectivePlayerShuttles() {
        List<ShuttleType> out = new ArrayList<>();
        for (int i = 0; i < cachedAvailable.size(); i++) {
            if (!deselectedTransports.contains(i)) out.add(cachedAvailable.get(i));
        }
        return out;
    }

    private static boolean isTransportSufficient(Mission m, List<ShuttleType> selectedShuttles) {
        return m.employerShuttles >= 1 || !selectedShuttles.isEmpty();
    }

    /**
     * Mirror of the old BriefingScreen helper — keeps acceptance behavior
     * identical: employer Aeroshuttles cycle first; player shuttles cycle next
     * to cover the remaining drops in priority order.
     */
    private static List<ShuttleAssignment> buildShuttleManifest(
            Mission m, List<ShuttleType> playerShuttles) {
        List<ShuttleAssignment> out = new ArrayList<>();
        int employerPhysical = employerPhysicalShipCount(m);
        if (employerPhysical > 0) {
            int employerDrops = m.employerShuttles;
            int eBase = employerDrops / employerPhysical;
            int eExtra = employerDrops % employerPhysical;
            ShuttleType employerType = DevConfig.FORCE_EMPLOYER_VALKYRIE
                    ? ShuttleType.VALKYRIE : ShuttleType.AEROSHUTTLE;
            for (int i = 0; i < employerPhysical; i++) {
                int cycles = eBase + (i < eExtra ? 1 : 0);
                out.add(new ShuttleAssignment(employerType, cycles));
            }
        }
        int playerDrops = Math.max(0, m.requiredDrops - m.employerShuttles);
        if (playerDrops == 0) return out;
        int transportsUsed = Math.min(playerDrops, playerShuttles.size());
        if (transportsUsed == 0) {
            for (int i = 0; i < playerDrops; i++) {
                out.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
            }
            return out;
        }
        int baseCycles = playerDrops / transportsUsed;
        int extraCycles = playerDrops % transportsUsed;
        for (int i = 0; i < transportsUsed; i++) {
            int cycles = baseCycles + (i < extraCycles ? 1 : 0);
            out.add(new ShuttleAssignment(playerShuttles.get(i), cycles));
        }
        return out;
    }

    private static int employerPhysicalShipCount(Mission m) {
        if (m.employerShuttles <= 0) return 0;
        return Math.min(m.employerShuttles, EMPLOYER_PHYSICAL_CAP);
    }

    private static String shuttleDisplayName(ShuttleType t) {
        String n = t.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    /**
     * True if the target planet hosts an industry that produces or demands
     * heavy armaments — drives the defender's mech slot. Ported from
     * BriefingScreen.
     */
    private static boolean planetHasHeavyArmaments(String targetPlanetName) {
        if (targetPlanetName == null) return false;
        for (com.fs.starfarer.api.campaign.econ.MarketAPI market
                : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.getPrimaryEntity() == null) continue;
            if (!targetPlanetName.equals(market.getPrimaryEntity().getName())) continue;
            return market.hasIndustry(Industries.HEAVYINDUSTRY)
                || market.hasIndustry(Industries.ORBITALWORKS)
                || market.hasIndustry(Industries.GROUNDDEFENSES)
                || market.hasIndustry(Industries.HEAVYBATTERIES);
        }
        return false;
    }

    @Override
    public void onRender(float alphaMult) {
        if (mapDrawW <= 0f || mapDrawH <= 0f) return;
        renderMapBackground(alphaMult);
        renderMapFrame(alphaMult);
    }

    private void renderMapBackground(float alphaMult) {
        if (ctx.planetTexture == null) return;
        try {
            SpriteAPI flat = Global.getSettings().getSprite(ctx.planetTexture);
            if (flat == null) return;
            flat.setTexX(0f);
            flat.setTexY(0f);
            flat.setTexWidth(flat.getTextureWidth());
            flat.setTexHeight(flat.getTextureHeight());
            flat.setSize(mapDrawW, mapDrawH);
            flat.setAlphaMult(alphaMult);
            flat.setNormalBlend();
            flat.render(mapDrawX, mapDrawY);
        } catch (Exception e) {
            LOG.error("CommsConsolePanel: thumbnail map draw failed", e);
        }
    }

    private void renderMapFrame(float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(
                FRAME_COLOR.getRed()   / 255f,
                FRAME_COLOR.getGreen() / 255f,
                FRAME_COLOR.getBlue()  / 255f,
                0.7f * alphaMult);
        glLineWidth(1f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(mapDrawX,             mapDrawY);
        glVertex2f(mapDrawX + mapDrawW,  mapDrawY);
        glVertex2f(mapDrawX + mapDrawW,  mapDrawY + mapDrawH);
        glVertex2f(mapDrawX,             mapDrawY + mapDrawH);
        glEnd();
    }
}
