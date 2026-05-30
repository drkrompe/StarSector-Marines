package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
import com.dillon.starsectormarines.battle.flyby.FlybyRoster;
import com.dillon.starsectormarines.battle.flyby.PlayerFleetWings;
import com.dillon.starsectormarines.ops.detachment.DetachmentResolver;
import com.dillon.starsectormarines.i18n.Strings;
import com.dillon.starsectormarines.marine.MarineCaptain;
import com.dillon.starsectormarines.marine.MarineRosterScript;
import com.dillon.starsectormarines.ui.ButtonWidget;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.LabelWidget;
import com.dillon.starsectormarines.ui.WidgetRoot;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
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
 * The canonical pre-battle surface (roadmap command-powers S8). Reached from
 * the mission-select list via the dossier card's <em>Brief &amp; Deploy</em>
 * action ({@link CommsConsolePanel}).
 *
 * <p>Full-canvas, two columns (S8 Slice B): the left "mission" column holds the
 * briefing details, salvage negotiation, and captain selection; the right
 * "detachment" column splits into <em>Your Fleet Brings</em> (the player's
 * committed transports + fighter cover, opt-in toggles) and <em>Employer
 * Provides</em> (the contract's shuttles / fighter support / offered powers,
 * read-only), with Deploy / Back below. The old decorative planet map is gone —
 * its space is reclaimed for the action area.
 *
 * <p>Deploy resolves the committed detachment and launches the battle via
 * {@link MissionLaunch#buildSimulation} → {@link ScreenId#BATTLE}. Back returns
 * to {@link ScreenId#MISSION_SELECT}; client + cache state on the context
 * survive the trip.
 */
public class BriefingScreen implements Screen {

    private static final Logger LOG = Global.getLogger(BriefingScreen.class);

    private static final Color FRAME_COLOR   = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER_COLOR  = new Color(0xC8, 0xE0, 0xFF);
    private static final Color LABEL_COLOR   = new Color(0x8F, 0xA8, 0xC0);
    private static final Color VALUE_COLOR   = new Color(0xE0, 0xE8, 0xFF);
    private static final Color FLAVOR_COLOR  = new Color(0xC0, 0xD0, 0xE8);
    private static final Color ACCEPT_COLOR  = new Color(0xC8, 0xFF, 0xE0);
    /** Red used for the Transport row + Deploy label when the player can't actually fly the mission. */
    private static final Color BLOCKED_COLOR = new Color(0xFF, 0x80, 0x80);

    private static final float INNER_PAD   = 12f;
    private static final float ROW_GAP     = 28f;
    private static final float LABEL_COL_W = 96f;
    private static final float BTN_H       = 32f;
    private static final float BTN_GAP     = 12f;
    private static final float SECTION_GAP = 16f;
    private static final float SQUAD_ROW_H = 32f;
    private static final float SQUAD_ROW_GAP = 4f;

    /** Top-y of the flavor paragraph in the left column, cached for renderFlavor. */
    private float flavorY;
    /** Wrap width for the flavor paragraph (left column width minus pads). */
    private float flavorW;
    /** Left edge of the flavor paragraph. */
    private float flavorX;

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BriefingLayout layout;

    /**
     * Indices into {@link #cachedAvailable} the player has deselected for the
     * current mission's dropship loadout. Default empty = all selected. Reset
     * when the selected mission changes (tracked via {@link #lastSelectedMissionId}).
     */
    private final java.util.Set<Integer> deselectedTransports = new java.util.HashSet<>();
    private String lastSelectedMissionId;
    /**
     * Snapshot of {@link PlayerFleetShuttles#queryAvailable()} taken at the start of
     * each {@link #rebuild()}. Indices are stable within a single briefing layout,
     * so the deselection set keeps referring to the same ships even as rows are redrawn.
     */
    private List<ShuttleType> cachedAvailable = java.util.Collections.emptyList();

    /**
     * Indices into {@link #cachedCarriers} the player has deselected for the
     * current mission's fighter cover. Default empty = all carriers committed.
     * Reset alongside {@link #deselectedTransports} when the mission changes.
     */
    private final java.util.Set<Integer> deselectedCarriers = new java.util.HashSet<>();
    /** Snapshot of {@link PlayerFleetWings#committableCarriers()} taken per {@link #rebuild()}, so toggle indices stay stable across a layout. */
    private List<PlayerFleetWings.CarrierBay> cachedCarriers = java.util.Collections.emptyList();

    @Override
    public void attach(PositionAPI position, MarineOpsContext ctx, Runnable dismissDialog) {
        this.position = position;
        this.ctx = ctx;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        if (position == null || ctx == null) return;
        layout = new BriefingLayout(position);

        // Default to the first ACTIVE captain if nothing's selected yet — saves
        // a click for the common case. User's pick survives across re-attaches.
        if (ctx.getSelectedCaptainId() == null) {
            MarineRosterScript script = MarineRosterScript.getInstance();
            if (script != null) {
                List<MarineCaptain> active = script.roster().active();
                if (!active.isEmpty()) ctx.setSelectedCaptainId(active.get(0).id());
            }
        }

        Mission m = ctx.getSelectedMission();

        // Selection scope is per-mission — clear the deselection set when the
        // player switches missions so they don't carry over hidden state.
        if (m != null && !m.id.equals(lastSelectedMissionId)) {
            lastSelectedMissionId = m.id;
            deselectedTransports.clear();
            deselectedCarriers.clear();
        }
        // Snapshot the available transports + carriers once per build so toggle indices are stable.
        cachedAvailable = PlayerFleetShuttles.queryAvailable();
        cachedCarriers = PlayerFleetWings.committableCarriers();

        // Header — mission name (large), spanning the canvas top.
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                m != null ? m.name : "",
                layout.headerX, layout.headerTextY, HEADER_COLOR));

        if (m != null) {
            buildMissionColumn(m);
            buildDetachmentColumn(m);
        }
        buildButtons();
    }

    // ---- left column: mission details + salvage + captain ----

    private void buildMissionColumn(Mission m) {
        float x = layout.leftCol.x + INNER_PAD;
        float y = layout.leftCol.y + layout.leftCol.h - INNER_PAD;
        float labelX = x;
        float valueX = x + LABEL_COL_W;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, Strings.get("briefingHeader"),
                labelX, y, HEADER_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupType"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get(m.type.displayKey),
                valueX, y, m.type.color));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupRisk"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get(m.risk.displayKey),
                valueX, y, m.risk.color));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupPayout"),
                labelX, y, LABEL_COLOR));
        int cashMult = m.cashMultiplier & 0xFF;
        if (cashMult <= 0) cashMult = 100;
        long effectivePayout = (long) m.payout * cashMult / 100L;
        String payoutStr = MessageFormat.format(
                Strings.get("payoutFmt"),
                NumberFormat.getIntegerInstance().format(effectivePayout));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, payoutStr, valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        // Salvage negotiation — contract-bound missions only. −/+ trade salvage
        // for cash per contracts/overview.md §"Salvage Layer 2".
        int salvageBaseline = m.salvageBaseline & 0xFF;
        if (salvageBaseline > 0) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingSalvage"),
                    labelX, y, LABEL_COLOR));
            int negotiated = m.salvageNegotiated & 0xFF;
            int cashBonus = cashMult - 100;
            String salvageStr = MessageFormat.format(
                    Strings.get("briefingSalvageFmt"), negotiated, cashBonus);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, salvageStr, valueX, y, VALUE_COLOR));

            float btnSize = 22f;
            float btnY = y - btnSize + 6f;
            float plusX = layout.leftCol.x + layout.leftCol.w - INNER_PAD - btnSize;
            float minusX = plusX - btnSize - 4f;
            widgets.add(new ButtonWidget(minusX, btnY, btnSize, btnSize, () -> adjustSalvage(-10)));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingSalvageMinus"),
                    minusX + 6f, y, HEADER_COLOR));
            widgets.add(new ButtonWidget(plusX, btnY, btnSize, btnSize, () -> adjustSalvage(+10)));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingSalvagePlus"),
                    plusX + 6f, y, HEADER_COLOR));
            y -= ROW_GAP;
        }

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupRequires"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, m.requirements, valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        // Opposition intel — enemy air. Neither side's contribution; it's what
        // the target fields against the drop.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingEnemyAir"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                summarizeWings(m.enemyFighterSupport, Faction.DEFENDER), valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        // Flavor paragraph extent — drawn wrapped in renderFlavor (LabelWidget
        // is single-line). Captain section sits below it.
        flavorX = x;
        flavorY = y - SECTION_GAP / 2f;
        flavorW = layout.leftCol.w - 2 * INNER_PAD;

        buildCaptainSection(m);
    }

    private void buildCaptainSection(Mission m) {
        float x = layout.leftCol.x + INNER_PAD;
        float w = layout.leftCol.w - 2 * INNER_PAD;

        float flavorHeight = m.flavor != null
                ? Fonts.ORBITRON_20.measureWrappedHeight(m.flavor, flavorW)
                : 0f;
        float sectionTop = flavorY - flavorHeight - SECTION_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, Strings.get("briefingSquadLead"),
                x, sectionTop, HEADER_COLOR));

        float listTop = sectionTop - 28f;
        // Leave room for the Deploy/Back row that sits at the bottom of the columns.
        float buttonsTop = layout.leftCol.y + INNER_PAD + BTN_H + SECTION_GAP;

        MarineRosterScript script = MarineRosterScript.getInstance();
        List<MarineCaptain> captains = script != null
                ? script.roster().active()
                : java.util.Collections.<MarineCaptain>emptyList();

        if (captains.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingNoCaptains"),
                    x, listTop, LABEL_COLOR));
            return;
        }

        float rowY = listTop - SQUAD_ROW_H;
        for (MarineCaptain c : captains) {
            if (rowY < buttonsTop) break; // out of room, overflow handled in a polish pass
            widgets.add(new CaptainRowWidget(c, x, rowY, w, SQUAD_ROW_H,
                    ctx::getSelectedCaptainId, ctx::setSelectedCaptainId));
            rowY -= SQUAD_ROW_H + SQUAD_ROW_GAP;
        }
    }

    // ---- right column: Your Fleet Brings / Employer Provides ----

    private void buildDetachmentColumn(Mission m) {
        float x = layout.rightCol.x + INNER_PAD;
        float valueX = x + LABEL_COL_W;
        float y = layout.rightCol.y + layout.rightCol.h - INNER_PAD;

        // === YOUR FLEET BRINGS ===
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "Your Fleet Brings", x, y, HEADER_COLOR));
        y -= ROW_GAP;

        // Transport — one toggle row per available shuttle, with sortie-cycle annotation.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingTransport"), x, y, LABEL_COLOR));
        y -= ROW_GAP;
        List<ShuttleAssignment> manifest = DetachmentResolver.buildShuttleManifest(m, effectivePlayerShuttles());
        java.util.Map<Integer, Integer> playerCyclesByIndex = computePlayerCyclesByIndex(m, manifest);
        for (int i = 0; i < cachedAvailable.size(); i++) {
            final int idx = i;
            ShuttleType type = cachedAvailable.get(i);
            boolean selected = !deselectedTransports.contains(idx);
            String marker = selected ? "[x]" : "[ ]";
            int cycles = playerCyclesByIndex.getOrDefault(idx, 0);
            StringBuilder rowLabel = new StringBuilder();
            rowLabel.append(marker).append(" 1× ").append(shuttleDisplayName(type));
            if (selected && cycles > 1) rowLabel.append(" (").append(cycles).append(" sorties)");
            else if (!selected) rowLabel.append(" — held back");
            Color rowColor = selected ? VALUE_COLOR : LABEL_COLOR;
            ButtonWidget toggle = new ButtonWidget(x, y - BTN_H + 6f,
                    layout.rightCol.w - 2 * INNER_PAD, BTN_H,
                    () -> {
                        if (deselectedTransports.contains(idx)) deselectedTransports.remove(idx);
                        else deselectedTransports.add(idx);
                        rebuild();
                    });
            widgets.add(toggle);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, rowLabel.toString(), x + 6f, y, rowColor));
            y -= ROW_GAP;
        }
        boolean transportOk = isTransportSufficient(m, effectivePlayerShuttles());
        if (!transportOk) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Need at least 1 transport (your fleet or employer)", x + 6f, y, BLOCKED_COLOR));
            y -= ROW_GAP;
        }

        // Fighter cover — one opt-in toggle per committable carrier.
        if (!cachedCarriers.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Fighter cover:", x, y, LABEL_COLOR));
            y -= ROW_GAP;
            for (int i = 0; i < cachedCarriers.size(); i++) {
                final int idx = i;
                PlayerFleetWings.CarrierBay carrier = cachedCarriers.get(i);
                boolean committed = !deselectedCarriers.contains(idx);
                String marker = committed ? "[x]" : "[ ]";
                String rowLabel = marker + " " + carrier.shipName
                        + " (" + carrier.bayCount() + (carrier.bayCount() == 1 ? " bay" : " bays") + ")"
                        + (committed ? "" : " — held back");
                Color rowColor = committed ? VALUE_COLOR : LABEL_COLOR;
                ButtonWidget toggle = new ButtonWidget(x, y - BTN_H + 6f,
                        layout.rightCol.w - 2 * INNER_PAD, BTN_H,
                        () -> {
                            if (deselectedCarriers.contains(idx)) deselectedCarriers.remove(idx);
                            else deselectedCarriers.add(idx);
                            rebuild();
                        });
                widgets.add(toggle);
                widgets.add(new LabelWidget(Fonts.ORBITRON_20, rowLabel, x + 6f, y, rowColor));
                y -= ROW_GAP;
            }
        }

        // === EMPLOYER PROVIDES === (read-only co-source)
        y -= SECTION_GAP;
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD, "Employer Provides", x, y, HEADER_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingTransport"), x, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                m.employerShuttles > 0 ? m.employerShuttles + "× Aeroshuttle" : Strings.get("briefingAirNone"),
                valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingAlliedAir"), x, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                summarizeWings(m.clientFighterSupport, Faction.MARINE), valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        if (m.employerPowerIds != null && !m.employerPowerIds.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, "Powers:", x, y, LABEL_COLOR));
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    summarizePowerIds(m.employerPowerIds), valueX, y, VALUE_COLOR));
            y -= ROW_GAP;
        }
    }

    private void buildButtons() {
        // Deploy / Back at the bottom of the detachment (right) column.
        float availableW = layout.rightCol.w - 2 * INNER_PAD;
        float btnW = (availableW - BTN_GAP) / 2f;
        float btnY = layout.rightCol.y + INNER_PAD;
        float deployX = layout.rightCol.x + INNER_PAD;
        float backX   = deployX + btnW + BTN_GAP;

        // Deploy gating — when transport is short, the button is non-functional
        // and the label flips to a red "Insufficient Transport".
        Mission m = ctx.getSelectedMission();
        boolean canAccept = m == null || isTransportSufficient(m, effectivePlayerShuttles());

        ButtonWidget deploy = new ButtonWidget(deployX, btnY, btnW, BTN_H,
                canAccept ? this::onAccept : null);
        widgets.add(deploy);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get(canAccept ? "briefingAccept" : "briefingAcceptBlocked"),
                deployX + INNER_PAD, btnY + BTN_H - 6f,
                canAccept ? ACCEPT_COLOR : BLOCKED_COLOR));

        ButtonWidget back = new ButtonWidget(backX, btnY, btnW, BTN_H, this::onBack);
        widgets.add(back);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                backX + INNER_PAD, btnY + BTN_H - 6f, HEADER_COLOR));
    }

    /** Currently-committed carriers — {@link #cachedCarriers} minus the deselected. */
    private List<PlayerFleetWings.CarrierBay> committedCarriers() {
        List<PlayerFleetWings.CarrierBay> out = new java.util.ArrayList<>();
        for (int i = 0; i < cachedCarriers.size(); i++) {
            if (!deselectedCarriers.contains(i)) out.add(cachedCarriers.get(i));
        }
        return out;
    }

    /** Marine-side fighter cover from the committed carriers (player side only). */
    private FlybyRoster committedWings() {
        return PlayerFleetWings.rosterFrom(committedCarriers());
    }

    /**
     * Compact one-line summary of the wings on a side. Example output:
     * {@code "2× Broadsword, 1× Talon"} or "None" when empty. Counts collapse
     * multiple wings of the same profile.
     */
    private static String summarizeWings(FlybyRoster roster, Faction side) {
        if (roster == null) return Strings.get("briefingAirNone");
        java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (FighterWing w : roster.wingsForSide(side)) {
            String key = profileDisplayName(w.profile.name());
            counts.merge(key, w.sortieCount, Integer::sum);
        }
        if (counts.isEmpty()) return Strings.get("briefingAirNone");
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : counts.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append("× ").append(e.getKey());
        }
        return sb.toString();
    }

    /** Title-cases the enum name (TALON → Talon, BROADSWORD → Broadsword). */
    private static String profileDisplayName(String enumName) {
        if (enumName == null || enumName.isEmpty()) return "";
        return enumName.charAt(0) + enumName.substring(1).toLowerCase();
    }

    /** Friendly summary of employer-offered power ids ("recon_ping" → "Recon Ping"). */
    private static String summarizePowerIds(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            if (id == null || id.isEmpty()) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(prettifyId(id));
        }
        return sb.length() == 0 ? Strings.get("briefingAirNone") : sb.toString();
    }

    /** "recon_ping" → "Recon Ping". */
    private static String prettifyId(String id) {
        StringBuilder s = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (s.length() > 0) s.append(' ');
            s.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return s.toString();
    }

    /**
     * Mission has a transport — the floor is "at least one source delivering
     * marines." Employer contributes drops up to their cover; selected player
     * transports cycle to fill any remaining gap.
     */
    private static boolean isTransportSufficient(Mission m, List<ShuttleType> selectedShuttles) {
        return m.employerShuttles >= 1 || !selectedShuttles.isEmpty();
    }

    /** Title-cased shuttle name for display (VALKYRIE → "Valkyrie"). */
    private static String shuttleDisplayName(ShuttleType t) {
        String n = t.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    /**
     * Currently-selected subset of {@link #cachedAvailable}, preserving the
     * priority-sorted order. Feeds the manifest + gate logic.
     */
    private List<ShuttleType> effectivePlayerShuttles() {
        List<ShuttleType> out = new java.util.ArrayList<>();
        for (int i = 0; i < cachedAvailable.size(); i++) {
            if (!deselectedTransports.contains(i)) out.add(cachedAvailable.get(i));
        }
        return out;
    }

    /**
     * Maps each {@link #cachedAvailable} index to the cycle count the manifest
     * actually assigned it. Deselected and unused transports are absent (caller
     * treats missing as 0).
     */
    private java.util.Map<Integer, Integer> computePlayerCyclesByIndex(
            Mission m, List<ShuttleAssignment> manifest) {
        java.util.Map<Integer, Integer> out = new java.util.HashMap<>();
        List<Integer> selectedIndices = new java.util.ArrayList<>();
        for (int i = 0; i < cachedAvailable.size(); i++) {
            if (!deselectedTransports.contains(i)) selectedIndices.add(i);
        }
        int employerPhysical = DetachmentResolver.employerPhysicalShipCount(m);
        for (int k = 0; k < selectedIndices.size()
                && (employerPhysical + k) < manifest.size(); k++) {
            ShuttleAssignment a = manifest.get(employerPhysical + k);
            out.put(selectedIndices.get(k), a.cycles);
        }
        return out;
    }

    private void onAccept() {
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        MarineCaptain c = ctx.getSelectedCaptain();
        String captainStr = c != null ? c.id() + " (" + c.name() + ")" : "none";
        LOG.info("MarineOps: deploy mission id=" + m.id + " name='" + m.name
                + "' type=" + m.type + " captain=" + captainStr);

        // Resolve the committed detachment (transports + marine fighter cover +
        // command powers) and build the battle. The deselected transports are
        // already filtered out of effectivePlayerShuttles().
        BattleSimulation sim = MissionLaunch.buildSimulation(
                ctx, m, effectivePlayerShuttles(), committedWings());
        ctx.setBattleSimulation(sim);
        ctx.goTo(ScreenId.BATTLE);
    }

    private void onBack() {
        ctx.goTo(ScreenId.MISSION_SELECT);
    }

    /**
     * Trades salvage for cash (or vice versa) by {@code delta} percentage
     * points, clamping to {@code [0, salvageBaseline]}. Mission is immutable so
     * we build a new instance carrying the updated negotiated + cash multiplier
     * and swap it into the context.
     *
     * <p>Curve per {@code roadmap/campaign/contracts/overview.md} §"Salvage Layer 2":
     * {@code cashMultiplier = 100 + (baseline − negotiated) * 0.5}.
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
                m.contractId, m.salvageBaseline, (byte) next, (byte) cashMult,
                m.employerPowerIds);
        ctx.setSelectedMission(replaced);
        rebuild();
    }

    @Override
    public void advance(float dt) {
        widgets.advance(dt);
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        widgets.processInput(events);
    }

    @Override
    public void render(float alphaMult) {
        if (layout == null) return;
        drawFrame(layout.leftCol,  alphaMult);
        drawFrame(layout.rightCol, alphaMult);
        renderFlavor(alphaMult);
        widgets.render(alphaMult);
    }

    private void renderFlavor(float alphaMult) {
        if (ctx == null) return;
        Mission m = ctx.getSelectedMission();
        if (m == null || m.flavor == null || m.flavor.isEmpty()) return;
        Fonts.ORBITRON_20.drawStringWrapped(m.flavor, flavorX, flavorY, flavorW, FLAVOR_COLOR, alphaMult);
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
