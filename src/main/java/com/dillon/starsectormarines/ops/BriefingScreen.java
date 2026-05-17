package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.BattleSetup;
import com.dillon.starsectormarines.battle.BattleSimulation;
import com.dillon.starsectormarines.battle.Faction;
import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.flyby.FighterWing;
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
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.apache.log4j.Logger;

import java.awt.Color;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_LINES;
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
 * Two-zone briefing screen. The left zone shows a zoomed crop of the planet's
 * equirectangular unwrap around the mission's normalized coords with a target
 * reticle marking the spot; the right zone lists mission details and the
 * Accept / Back actions.
 *
 * <p>Accept currently no-ops with a log line — mission resolution lands in the
 * next slice. Back returns to {@link ScreenId#MISSION_SELECT}; client + cache
 * state on the context survive the trip.
 */
public class BriefingScreen implements Screen {

    private static final Logger LOG = Global.getLogger(BriefingScreen.class);

    private static final Color FRAME_COLOR   = new Color(0x4A, 0x6B, 0x8C);
    private static final Color HEADER_COLOR  = new Color(0xC8, 0xE0, 0xFF);
    private static final Color LABEL_COLOR   = new Color(0x8F, 0xA8, 0xC0);
    private static final Color VALUE_COLOR   = new Color(0xE0, 0xE8, 0xFF);
    private static final Color FLAVOR_COLOR  = new Color(0xC0, 0xD0, 0xE8);
    private static final Color ACCEPT_COLOR  = new Color(0xC8, 0xFF, 0xE0);
    private static final Color RETICLE_COLOR = new Color(0xFF, 0xB8, 0x00);
    /** Red used for the Transport row + Accept label when the player can't actually fly the mission. */
    private static final Color BLOCKED_COLOR = new Color(0xFF, 0x80, 0x80);

    /** Fraction of the texture's width/height visible inside the map zone. */
    private static final float CROP_FRAC = 0.30f;

    private static final float INNER_PAD     = 12f;
    private static final float ROW_GAP       = 28f;
    private static final float LABEL_COL_W   = 96f;
    private static final float BTN_H         = 32f;
    private static final float BTN_GAP       = 12f;
    private static final float SECTION_GAP   = 16f;

    /** Top-y of the flavor paragraph in the info zone, cached for renderFlavor. */
    private float flavorY;
    /** Wrap width for the flavor paragraph (info zone width minus pads). */
    private float flavorW;

    private static final int   RETICLE_SEGS  = 24;
    private static final float RETICLE_INNER = 6f;
    private static final float RETICLE_OUTER = 18f;

    private static final float SQUAD_ROW_H   = 32f;
    private static final float SQUAD_ROW_GAP = 4f;

    private final WidgetRoot widgets = new WidgetRoot();

    private PositionAPI position;
    private MarineOpsContext ctx;
    private BriefingLayout layout;

    /**
     * Indices into {@link #cachedAvailable} that the player has deselected for
     * the current mission's dropship loadout. Default empty = all selected. Reset
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
                java.util.List<MarineCaptain> active = script.roster().active();
                if (!active.isEmpty()) ctx.setSelectedCaptainId(active.get(0).id());
            }
        }

        Mission m = ctx.getSelectedMission();

        // Selection scope is per-mission — clear the deselection set when the
        // player switches missions so they don't carry over hidden state.
        if (m != null && !m.id.equals(lastSelectedMissionId)) {
            lastSelectedMissionId = m.id;
            deselectedTransports.clear();
        }
        // Snapshot the available transports once per build so toggle indices are stable.
        cachedAvailable = PlayerFleetShuttles.queryAvailable();

        // Map zone header — mission name (large)
        widgets.add(new LabelWidget(Fonts.ORBITRON_24_BOLD,
                m != null ? m.name : "",
                layout.mapZone.x, layout.mapZone.headerTextY, HEADER_COLOR));

        // Info zone header — fixed "Briefing"
        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("briefingHeader"),
                layout.infoZone.x, layout.infoZone.headerTextY, HEADER_COLOR));

        if (m != null) buildInfoRows(m);
        if (m != null) buildSquadSection(m);
        buildButtons();
    }

    private void buildInfoRows(Mission m) {
        float x = layout.infoZone.x + INNER_PAD;
        float topY = layout.infoZone.y + layout.infoZone.h - INNER_PAD;
        float y = topY;
        float labelX = x;
        float valueX = x + LABEL_COL_W;

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
        String payoutStr = MessageFormat.format(
                Strings.get("payoutFmt"),
                NumberFormat.getIntegerInstance().format(m.payout));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, payoutStr,
                valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("missionPopupRequires"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, m.requirements,
                valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        // Air support rows — what each side brings. Allied = employer support
        // + the player's own fitted fighter bays (queried live so swapping
        // fighters in the refit screen between visits is reflected here).
        FlybyRoster allied = effectiveAlliedRoster(m);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingAlliedAir"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                summarizeWings(allied, Faction.MARINE),
                valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingEnemyAir"),
                labelX, y, LABEL_COLOR));
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                summarizeWings(m.enemyFighterSupport, Faction.DEFENDER),
                valueX, y, VALUE_COLOR));
        y -= ROW_GAP;

        // Drop-ship section — header then toggle rows. Each available transport
        // is a clickable row; selection drives the manifest. Gate becomes
        // "at least 1 selected, or employer covers everything" — cycling lets
        // a single selected transport carry all required drops if needed.
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("briefingTransport"),
                labelX, y, LABEL_COLOR));
        y -= ROW_GAP;

        List<ShuttleAssignment> manifest = buildShuttleManifest(m, effectivePlayerShuttles());
        java.util.Map<Integer, Integer> playerCyclesByIndex = computePlayerCyclesByIndex(m, manifest);
        for (int i = 0; i < cachedAvailable.size(); i++) {
            final int idx = i;
            ShuttleType type = cachedAvailable.get(i);
            boolean selected = !deselectedTransports.contains(idx);
            String marker = selected ? "[x]" : "[ ]";
            int cycles = playerCyclesByIndex.getOrDefault(idx, 0);
            StringBuilder rowLabel = new StringBuilder();
            rowLabel.append(marker).append(" 1× ").append(shuttleDisplayName(type));
            if (selected && cycles > 1) {
                rowLabel.append(" (").append(cycles).append(" sorties)");
            } else if (!selected) {
                rowLabel.append(" — held back");
            }
            Color rowColor = selected ? VALUE_COLOR : LABEL_COLOR;
            ButtonWidget toggle = new ButtonWidget(valueX, y - BTN_H + 6f,
                    layout.infoZone.w - (valueX - layout.infoZone.x) - INNER_PAD,
                    BTN_H,
                    () -> {
                        if (deselectedTransports.contains(idx)) deselectedTransports.remove(idx);
                        else deselectedTransports.add(idx);
                        rebuild();
                    });
            widgets.add(toggle);
            widgets.add(new LabelWidget(Fonts.ORBITRON_20, rowLabel.toString(),
                    valueX + 6f, y, rowColor));
            y -= ROW_GAP;
        }

        // Employer line — non-interactive, just informational.
        if (m.employerShuttles > 0) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    m.employerShuttles + "× employer Aeroshuttle",
                    valueX + 6f, y, VALUE_COLOR));
            y -= ROW_GAP;
        }

        // Gate status line — only render when something's off (no transports at all).
        boolean transportOk = isTransportSufficient(m, effectivePlayerShuttles());
        if (!transportOk) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    "Need at least 1 transport (your fleet or employer)",
                    valueX + 6f, y, BLOCKED_COLOR));
            y -= ROW_GAP;
        }

        // Stash flavor paragraph extent for renderFlavor — drawStringWrapped
        // doesn't fit the single-line LabelWidget model so it's drawn inline
        // during render. measureWrappedHeight wasn't called here (renderFlavor
        // recomputes) since flavor only feeds the squad section in step 6.
        flavorY = y - SECTION_GAP;
        flavorW = layout.infoZone.w - 2 * INNER_PAD;
    }

    private void buildSquadSection(Mission m) {
        float x = layout.infoZone.x + INNER_PAD;
        float w = layout.infoZone.w - 2 * INNER_PAD;

        // Position below flavor — pre-measure with the same wrap width so
        // the section moves down by however many lines the prose takes.
        float flavorHeight = m.flavor != null
                ? Fonts.ORBITRON_20.measureWrappedHeight(m.flavor, flavorW)
                : 0f;
        float sectionTop = flavorY - flavorHeight - SECTION_GAP;

        widgets.add(new LabelWidget(Fonts.ORBITRON_20_BOLD,
                Strings.get("briefingSquadLead"),
                x, sectionTop, HEADER_COLOR));

        // Captain rows fill from below the header down to where the buttons begin.
        float listTop = sectionTop - 28f;
        float buttonsTop = layout.infoZone.y + INNER_PAD + BTN_H + SECTION_GAP;

        MarineRosterScript script = MarineRosterScript.getInstance();
        java.util.List<MarineCaptain> captains = script != null
                ? script.roster().active()
                : java.util.Collections.<MarineCaptain>emptyList();

        if (captains.isEmpty()) {
            widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                    Strings.get("briefingNoCaptains"),
                    x, listTop, LABEL_COLOR));
            return;
        }

        float rowY = listTop - SQUAD_ROW_H;
        for (MarineCaptain c : captains) {
            if (rowY < buttonsTop) break; // out of room, overflow handled in a polish pass
            widgets.add(new CaptainRowWidget(
                    c, x, rowY, w, SQUAD_ROW_H,
                    ctx::getSelectedCaptainId,
                    ctx::setSelectedCaptainId));
            rowY -= SQUAD_ROW_H + SQUAD_ROW_GAP;
        }
    }

    private void buildButtons() {
        float availableW = layout.infoZone.w - 2 * INNER_PAD;
        float btnW = (availableW - BTN_GAP) / 2f;
        float btnY = layout.infoZone.y + INNER_PAD;
        float acceptX = layout.infoZone.x + INNER_PAD;
        float backX   = acceptX + btnW + BTN_GAP;

        // Accept gating — when transport is short, the button is non-functional
        // and the label flips to a red "Insufficient Transport". The button shape
        // still renders (no visual disable state on ButtonWidget yet) so the
        // player has a clear "what would have been Accept" target — they read
        // the label and the red Transport row to know why it's blocked.
        Mission m = ctx.getSelectedMission();
        boolean canAccept = m == null || isTransportSufficient(m, effectivePlayerShuttles());

        ButtonWidget accept = new ButtonWidget(acceptX, btnY, btnW, BTN_H,
                canAccept ? this::onAccept : null);
        widgets.add(accept);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20,
                Strings.get(canAccept ? "briefingAccept" : "briefingAcceptBlocked"),
                acceptX + INNER_PAD, btnY + BTN_H - 6f,
                canAccept ? ACCEPT_COLOR : BLOCKED_COLOR));

        ButtonWidget back = new ButtonWidget(backX, btnY, btnW, BTN_H, this::onBack);
        widgets.add(back);
        widgets.add(new LabelWidget(Fonts.ORBITRON_20, Strings.get("actionBack"),
                backX + INNER_PAD, btnY + BTN_H - 6f, HEADER_COLOR));
    }

    /**
     * Combined marine-side roster: mission employer's support plus whatever the
     * player has fitted in their own carrier bays. Used by both the briefing
     * display and {@link #onAccept} so the two never drift.
     */
    private FlybyRoster effectiveAlliedRoster(Mission m) {
        return FlybyRoster.combine(m.clientFighterSupport, PlayerFleetWings.fromPlayerFleet());
    }

    /**
     * Compact one-line summary of the wings on a side. Example output:
     * {@code "2× Broadsword, 1× Talon"} or "None" when empty. Counts collapse
     * multiple wings of the same profile so a 3-sortie + 2-sortie Broadsword
     * pair reads as "5× Broadsword" rather than two separate entries.
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

    /**
     * Mission has a transport — the floor is "at least one source delivering
     * marines." Employer contributes drops up to their cover; selected player
     * transports cycle to fill any remaining gap. A single selected Valkyrie
     * covers a 3-drop mission entirely via cycling; an employer-covered 3/3
     * lets the player commit zero transports of their own.
     *
     * <p>If both the employer and the player's selection are empty, the mission
     * is blocked — no marines would land.
     */
    private static boolean isTransportSufficient(Mission m, List<ShuttleType> selectedShuttles) {
        return m.employerShuttles >= 1 || !selectedShuttles.isEmpty();
    }

    /**
     * Transport summary that communicates both the sprites and the pacing.
     * Examples:
     * <pre>
     *   "1× Valkyrie (3 sorties)"                 - cycling
     *   "1× Valkyrie + 1× Buffalo + 1× employer"  - parallel, 3 drops total
     *   "1× Valkyrie (2 sorties) + 1× Buffalo"    - mixed
     *   "Insufficient — need 1 transport"         - blocked, employer didn't cover
     * </pre>
     */
    private static String summarizeTransport(Mission m, List<ShuttleType> playerShuttles) {
        if (!isTransportSufficient(m, playerShuttles)) {
            int gap = m.requiredDrops - m.employerShuttles;
            return "Insufficient — need 1 transport (or employer cover for " + gap + " drops)";
        }
        List<ShuttleAssignment> manifest = buildShuttleManifest(m, playerShuttles);
        // Collapse identical (type, cycles) pairs so two Valkyries each doing 1
        // sortie read as "2× Valkyrie" and a single Valkyrie cycling twice reads
        // as "1× Valkyrie (2 sorties)". These are different gameplay realities
        // — same total drops, different pacing — and the briefing has to make
        // that distinction.
        java.util.LinkedHashMap<java.util.Map.Entry<ShuttleType, Integer>, Integer> playerGroups =
                new java.util.LinkedHashMap<>();
        int employerCount = 0;
        int leadingAero = 0;
        for (ShuttleAssignment a : manifest) {
            if (leadingAero < m.employerShuttles
                    && a.type == ShuttleType.AEROSHUTTLE && a.cycles == 1) {
                employerCount++;
                leadingAero++;
            } else {
                java.util.Map.Entry<ShuttleType, Integer> key =
                        new java.util.AbstractMap.SimpleEntry<>(a.type, a.cycles);
                playerGroups.merge(key, 1, Integer::sum);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<java.util.Map.Entry<ShuttleType, Integer>, Integer> g
                : playerGroups.entrySet()) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(g.getValue()).append("× ").append(shuttleDisplayName(g.getKey().getKey()));
            if (g.getKey().getValue() > 1) {
                sb.append(" (").append(g.getKey().getValue()).append(" sorties)");
            }
        }
        if (employerCount > 0) {
            if (sb.length() > 0) sb.append(" + ");
            sb.append(employerCount).append("× employer");
        }
        return sb.toString();
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
     * actually assigned it. Deselected and unused transports are absent from
     * the map (caller treats missing as 0).
     */
    private java.util.Map<Integer, Integer> computePlayerCyclesByIndex(
            Mission m, List<ShuttleAssignment> manifest) {
        java.util.Map<Integer, Integer> out = new java.util.HashMap<>();
        // Selected indices in cachedAvailable order — same iteration order
        // buildShuttleManifest uses to pick its player entries.
        List<Integer> selectedIndices = new java.util.ArrayList<>();
        for (int i = 0; i < cachedAvailable.size(); i++) {
            if (!deselectedTransports.contains(i)) selectedIndices.add(i);
        }
        // The manifest's leading entries are employer Aeroshuttles; the rest
        // are player entries in selected order.
        for (int k = 0; k < selectedIndices.size()
                && (m.employerShuttles + k) < manifest.size(); k++) {
            ShuttleAssignment a = manifest.get(m.employerShuttles + k);
            out.put(selectedIndices.get(k), a.cycles);
        }
        return out;
    }

    /**
     * Builds the full {@link ShuttleAssignment} list — what ships fly and how
     * many sorties each performs. Employer slots come first as single-cycle
     * Aeroshuttles (parallel drops); the player's selected transports cover
     * the remaining drops via cycling.
     *
     * <p>Distribution: with {@code N} player transports needing to cover
     * {@code D} drops, the first {@code (D mod N)} transports get
     * {@code ceil(D/N)} cycles and the rest get {@code floor(D/N)}. The
     * player's best transports (priority-sorted) get the extra cycle, so a
     * Valkyrie works harder than a Mudskipper when the math is uneven.
     *
     * <p>When the player has selected zero transports and the employer doesn't
     * cover all the drops, the manifest only covers the employer's portion —
     * battle runs short-handed. The briefing gate blocks before that's an
     * issue, but the function itself doesn't assume gate enforcement.
     */
    private static java.util.List<ShuttleAssignment> buildShuttleManifest(
            Mission m, java.util.List<ShuttleType> playerShuttles) {
        java.util.List<ShuttleAssignment> out = new java.util.ArrayList<>();
        for (int i = 0; i < m.employerShuttles; i++) {
            out.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
        }
        int playerDrops = Math.max(0, m.requiredDrops - m.employerShuttles);
        if (playerDrops == 0) return out;
        int transportsUsed = Math.min(playerDrops, playerShuttles.size());
        if (transportsUsed == 0) {
            // Gated normally, but if somehow we get here pad with employer-style
            // aeroshuttles so the battle still functions.
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

    private void onAccept() {
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        MarineCaptain c = ctx.getSelectedCaptain();
        String captainStr = c != null ? c.id() + " (" + c.name() + ")" : "none";
        LOG.info("MarineOps: accept mission id=" + m.id + " name='" + m.name
                + "' type=" + m.type + " captain=" + captainStr);

        // Resolve the shuttle manifest. Player's best transports get picked
        // first up to the requirement; if employer is covering the gap, fewer
        // player ships get drawn from. Either way the final list is exactly
        // requiredShuttles long, and each entry is the ShuttleType that'll
        // spawn in battle — so the player sees the same hulls in the brief and
        // on the LZ.
        // Use the selected subset, not the full available list — players who
        // deselect a transport in the briefing shouldn't see it fly anyway.
        List<ShuttleAssignment> manifest = buildShuttleManifest(m, effectivePlayerShuttles());

        // Heavy-armaments availability on the target world drives whether
        // the defender side fields a HEAVY_MECH. The commodity description ties
        // mechs/tanks/hovercraft to that good — so a planet that produces it
        // (Heavy Industry, Orbital Works) or stockpiles it for use (Ground
        // Defenses, Heavy Batteries) is exactly where a mech walks the line.
        boolean enemyHasHeavyArmor = planetHasHeavyArmaments(m.targetPlanetName);

        // Mission-type-specific setup. Each type wires its own objectives and
        // loadouts; falls back to ASSAULT for types not yet built out.
        BattleSimulation sim;
        long seed = System.currentTimeMillis();
        switch (m.type) {
            case SABOTAGE:
                sim = BattleSetup.createSabotage(seed, manifest, enemyHasHeavyArmor);
                break;
            case ASSAULT:
            case RAID:
            case EXTRACTION:
            default:
                sim = BattleSetup.createPlaceholder(seed, manifest, enemyHasHeavyArmor);
        }
        // Fold employer support + the player's own fitted bays + enemy support
        // into a single roster the overlay drives spawns from. Re-queries the
        // player fleet here (instead of trusting the briefing-display result)
        // because the player can refit between viewing and accepting.
        sim.setFlybyRoster(FlybyRoster.combine(effectiveAlliedRoster(m), m.enemyFighterSupport));
        ctx.setBattleSimulation(sim);
        ctx.goTo(ScreenId.BATTLE);
    }

    private void onBack() {
        ctx.goTo(ScreenId.MISSION_SELECT);
    }

    /**
     * True when the target planet's market hosts at least one industry that
     * produces or demands heavy armaments. Producers: Heavy Industry, Orbital
     * Works. Consumers: Ground Defenses, Heavy Batteries. The flag drives the
     * defender's mech slot in {@link BattleSetup} — lore-correct because a
     * planet without any of those industries has no organic source of heavy
     * armaments and shouldn't be fielding mechs.
     *
     * <p>Returns false when the mission has no target planet name (story /
     * non-industry ops) or no market is found by that name. Iterates the
     * economy via the same lookup {@code MissionResolver.applyIndustryDisruption}
     * uses, since {@link Mission#targetPlanetName} is the canonical key both
     * sides agree on.
     */
    private static boolean planetHasHeavyArmaments(String targetPlanetName) {
        if (targetPlanetName == null) return false;
        for (com.fs.starfarer.api.campaign.econ.MarketAPI market
                : Global.getSector().getEconomy().getMarketsCopy()) {
            if (market == null || market.getPrimaryEntity() == null) continue;
            if (!targetPlanetName.equals(market.getPrimaryEntity().getName())) continue;
            return market.hasIndustry(com.fs.starfarer.api.impl.campaign.ids.Industries.HEAVYINDUSTRY)
                || market.hasIndustry(com.fs.starfarer.api.impl.campaign.ids.Industries.ORBITALWORKS)
                || market.hasIndustry(com.fs.starfarer.api.impl.campaign.ids.Industries.GROUNDDEFENSES)
                || market.hasIndustry(com.fs.starfarer.api.impl.campaign.ids.Industries.HEAVYBATTERIES);
        }
        return false;
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

        renderMapZone(alphaMult);
        drawFrame(layout.mapZone,  alphaMult);
        drawFrame(layout.infoZone, alphaMult);
        renderReticle(alphaMult);
        renderFlavor(alphaMult);
        widgets.render(alphaMult);
    }

    private void renderFlavor(float alphaMult) {
        if (ctx == null) return;
        Mission m = ctx.getSelectedMission();
        if (m == null || m.flavor == null || m.flavor.isEmpty()) return;
        float x = layout.infoZone.x + INNER_PAD;
        Fonts.ORBITRON_20.drawStringWrapped(m.flavor, x, flavorY, flavorW, FLAVOR_COLOR, alphaMult);
    }

    private void renderMapZone(float alphaMult) {
        if (ctx == null || ctx.planetTexture == null) return;
        Mission m = ctx.getSelectedMission();
        if (m == null) return;
        try {
            SpriteAPI sprite = Global.getSettings().getSprite(ctx.planetTexture);
            if (sprite == null) return;

            // setTex* methods take texture-fraction coords (POT-aware) — multiply
            // our normalized image coords by getTextureWidth/Height to scale into
            // the right range. The bare 0.25f examples in the vanilla API work
            // only because those sprites' images fully fill their POT textures.
            float texW = sprite.getTextureWidth();
            float texH = sprite.getTextureHeight();

            float[] crop = cropForMission(m);
            sprite.setTexX(crop[0] * texW);
            sprite.setTexY(crop[1] * texH);
            sprite.setTexWidth(CROP_FRAC * texW);
            sprite.setTexHeight(CROP_FRAC * texH);
            sprite.setSize(layout.mapZone.w, layout.mapZone.h);
            sprite.setAlphaMult(alphaMult);
            sprite.setNormalBlend();
            sprite.render(layout.mapZone.x, layout.mapZone.y);
        } catch (Exception e) {
            LOG.error("BriefingScreen: cropped terrain render failed", e);
        }
    }

    private void renderReticle(float alphaMult) {
        if (ctx == null) return;
        Mission m = ctx.getSelectedMission();
        if (m == null) return;

        float[] crop = cropForMission(m);
        float cropTexU = crop[0];
        float cropTexV = crop[1];
        float missionTexU = m.normalizedX;
        float missionTexV = m.normalizedY;

        // Mission's position within the visible crop (0..1, V from bottom of crop)
        float fracU = (missionTexU - cropTexU) / CROP_FRAC;
        float fracV = (missionTexV - cropTexV) / CROP_FRAC;

        float screenX = layout.mapZone.x + fracU * layout.mapZone.w;
        float screenY = layout.mapZone.y + fracV * layout.mapZone.h;

        drawCircleLine(screenX, screenY, RETICLE_OUTER, alphaMult);
        drawCircleLine(screenX, screenY, RETICLE_INNER, alphaMult);
        drawCrosshair(screenX, screenY, RETICLE_OUTER + 4f, alphaMult);
    }

    /**
     * @return {@code [cropTexU, cropTexV]} — top-left of the crop window in
     *         normalized image coords (V from top), clamped so the crop stays
     *         inside the image even when the mission sits near an edge.
     */
    /**
     * @return {@code [cropTexU, cropTexV]} — bottom-left of the crop window in
     *         normalized image coords (V from bottom, matching Starsector's GL
     *         load convention), clamped so the crop stays inside the image even
     *         when the mission sits near an edge.
     */
    private static float[] cropForMission(Mission m) {
        float halfCrop = CROP_FRAC / 2f;
        float missionTexU = m.normalizedX;
        float missionTexV = m.normalizedY;
        float cropTexU = clamp(missionTexU - halfCrop, 0f, 1f - CROP_FRAC);
        float cropTexV = clamp(missionTexV - halfCrop, 0f, 1f - CROP_FRAC);
        return new float[] { cropTexU, cropTexV };
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
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

    private static void drawCircleLine(float cx, float cy, float radius, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(
                RETICLE_COLOR.getRed()   / 255f,
                RETICLE_COLOR.getGreen() / 255f,
                RETICLE_COLOR.getBlue()  / 255f,
                0.9f * alphaMult);
        glLineWidth(1.5f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < RETICLE_SEGS; i++) {
            float a = (float)(2 * Math.PI * i / RETICLE_SEGS);
            glVertex2f(cx + (float)Math.cos(a) * radius, cy + (float)Math.sin(a) * radius);
        }
        glEnd();
    }

    private static void drawCrosshair(float cx, float cy, float arm, float alphaMult) {
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(
                RETICLE_COLOR.getRed()   / 255f,
                RETICLE_COLOR.getGreen() / 255f,
                RETICLE_COLOR.getBlue()  / 255f,
                0.9f * alphaMult);
        glLineWidth(1.5f);
        // Four arms with a small gap in the middle so the inner circle stays clean.
        float gap = arm * 0.4f;
        glBegin(GL_LINES);
        glVertex2f(cx - arm, cy); glVertex2f(cx - gap, cy);
        glVertex2f(cx + gap, cy); glVertex2f(cx + arm, cy);
        glVertex2f(cx, cy - arm); glVertex2f(cx, cy - gap);
        glVertex2f(cx, cy + gap); glVertex2f(cx, cy + arm);
        glEnd();
    }
}
