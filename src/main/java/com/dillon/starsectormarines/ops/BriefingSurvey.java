package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.world.gen.ThunderRunCompound;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.ConquestObjective;
import com.dillon.starsectormarines.battle.command.objective.ExtractionPayloadObjective;
import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.command.objective.RaidObjective;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.precinct.LaneRoute;
import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.CanvasContext;
import com.dillon.starsectormarines.ui.retained.CanvasProducer;
import com.dillon.starsectormarines.ui.retained.UiDocument;
import com.dillon.starsectormarines.ui.retained.markup.MarkupInstance;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Immutable intelligence projection of an unstarted production scenario, never a second map generator. */
record BriefingSurvey(int width, int height, List<Terrain> terrain, List<Area> areas, List<Marker> markers,
                      List<LaneRoute> lanes, List<String> objectives) implements CanvasProducer {
    record Terrain(int x, int y, int width, int height, boolean roof) { }
    record Area(int x, int y, int width, int height, boolean primary) { }
    record Marker(String label, int x, int y, boolean landing) { }
    private static final Color GROUND = new Color(37, 56, 53);
    private static final Color ROOF = new Color(70, 82, 76);
    private static final Color BACKGROUND = new Color(13, 24, 25);
    private static final Color GRID = new Color(51, 72, 68);
    private static final Color OBJECTIVE = new Color(229, 180, 89);
    private static final Color LANDING = new Color(127, 207, 195);

    BriefingSurvey {
        terrain = List.copyOf(terrain); areas = List.copyOf(areas); markers = List.copyOf(markers);
        lanes = List.copyOf(lanes); objectives = List.copyOf(objectives);
    }

    static BriefingSurvey capture(BattleSimulation sim) {
        int width = sim.getGrid().getWidth(), height = sim.getGrid().getHeight();
        List<Terrain> terrain = new ArrayList<>();
        // Run-length encode terrain once on the worker. Buildings remain visible in the survey.
        for (int y = 0; y < height; y++) {
            int start = 0, prior = terrainKind(sim, 0, y);
            for (int x = 1; x <= width; x++) {
                int next = x < width ? terrainKind(sim, x, y) : -1;
                if (next != prior) {
                    if (prior > 0) terrain.add(new Terrain(start, y, x - start, 1, prior == 2));
                    start = x;
                    prior = next;
                }
            }
        }
        List<Area> areas = new ArrayList<>();
        List<Marker> markers = new ArrayList<>();
        List<String> objectives = new ArrayList<>();
        for (var pad : sim.getMarineLandingPads()) {
            // Every actual berth is visible; a single landing-area label avoids overlapping labels.
            markers.add(new Marker("", pad.centerX, pad.centerY, true));
        }
        Map<TacticalNode.Kind, Integer> siteCounts = new EnumMap<>(TacticalNode.Kind.class);
        boolean conquest = sim.getObjectives().stream().anyMatch(ConquestObjective.class::isInstance);
        if (conquest && sim.getCompoundService() != null) {
            for (var compound : sim.getCompoundService().getRecords()) {
                TacticalNode node = compound.node;
                if (node.defaultGuard == Faction.MARINE) continue;
                boolean primary = node.kind == TacticalNode.Kind.COMMAND_POST;
                areas.add(new Area(node.left, node.top, node.right - node.left + 1,
                        node.bottom - node.top + 1, primary));
                markers.add(new Marker(primary ? "COMMAND" : "", node.centerX(), node.centerY(), false));
                siteCounts.merge(node.kind, 1, Integer::sum);
            }
        }
        if (!siteCounts.isEmpty()) {
            objectives.add("Secure the command post and strategic sites");
            for (var entry : siteCounts.entrySet()) {
                objectives.add(entry.getValue() + " / " + entry.getKey().name().replace('_', ' ')
                        .toLowerCase(Locale.ROOT));
            }
        }
        if (sim.getThunderRun() != null) {
            int i = 0;
            for (var compound : sim.getCompoundService().getRecords()) {
                var node = compound.node;
                markers.add(new Marker(ThunderRunCompound.SITE_NAMES.get(i++).toUpperCase(Locale.ROOT), node.anchorX, node.anchorY, false));
                areas.add(new Area(node.left, node.top, node.right - node.left + 1, node.bottom - node.top + 1, false));
            }
            markers.add(new Marker("FREIGHT EXIT", ThunderRunCompound.WIDTH - 8, 75, false));
            objectives.add("Dispatch Post > East Crossing > Outbound Gate");
            objectives.add("Escort the freight through the marked exit");
        }
        for (Objective objective : sim.getObjectives()) {
            if (objective.owningFaction() != Faction.MARINE) continue;
            int x = -1, y = -1;
            if (objective instanceof ChargeSiteObjective site) { x = site.cellX(); y = site.cellY(); }
            else if (objective instanceof RaidObjective raid) { x = raid.targetCellX(); y = raid.targetCellY(); }
            else if (objective instanceof ExtractionPayloadObjective payload) {
                x = payload.sourceCellX(); y = payload.sourceCellY();
                if (payload.egressCellX() >= 0 && payload.egressCellY() >= 0
                        && (payload.egressCellX() != x || payload.egressCellY() != y)) {
                    markers.add(new Marker("EXFIL", payload.egressCellX(), payload.egressCellY(), false));
                    objectives.add("EXFIL / extract at the marked pickup");
                }
            }
            if (x >= 0 && y >= 0) {
                String label = "OBJ" + (objectives.size() + 1);
                markers.add(new Marker(label, x, y, false));
                objectives.add(label + " / " + objective.displayName());
            } else if (objectives.isEmpty()) objectives.add(objective.displayName());
        }
        return new BriefingSurvey(width, height, terrain, areas, markers, sim.getLaneRoutes(), objectives);
    }

    private static int terrainKind(BattleSimulation sim, int x, int y) {
        return sim.getTopology().isRoofIntact(x, y) ? 2 : sim.getGrid().isWalkable(x, y) ? 1 : 0;
    }

    static void install(UiDocument document, MarkupInstance markup, Map<String, Object> props) {
        CanvasProducer producer = props.get("surveyMap") instanceof BriefingSurvey map ? map
                : canvas -> {
                    boolean failed = Boolean.TRUE.equals(props.get("surveyFailed"));
                    float w = canvas.metrics().surfaceWidth(), h = canvas.metrics().surfaceHeight();
                    canvas.fillRect(0, 0, w, h, new Color(13, 24, 25));
                    for (int x = 20; x < w; x += 40) canvas.line(x, 20, x, h - 20, GRID, 1);
                    for (int y = 20; y < h; y += 40) canvas.line(20, y, w - 20, y, GRID, 1);
                    canvas.text(Fonts.ORBITRON_16, failed ? "SURVEY UNAVAILABLE" : "ACQUIRING TERRAIN",
                            168, h / 2, failed ? OBJECTIVE : LANDING);
                };
        document.canvases().set(markup.requireElement("mission-survey-canvas"), producer);
    }

    @Override public void draw(CanvasContext canvas) {
        float w = canvas.metrics().surfaceWidth(), h = canvas.metrics().surfaceHeight();
        canvas.fillRect(0, 0, w, h, BACKGROUND);
        float scale = Math.min((w - 40) / width, (h - 44) / height);
        float ox = (w - width * scale) / 2, oy = (h - height * scale) / 2;
        for (Terrain run : terrain) canvas.fillRect(ox + run.x * scale,
                oy + (height - run.y - run.height) * scale,
                run.width * scale, run.height * scale, run.roof ? ROOF : GROUND);
        for (int x = 0; x <= width; x += 40) canvas.line(ox + x * scale, oy, ox + x * scale,
                oy + height * scale, GRID, 0.6f);
        for (int y = 0; y <= height; y += 40) canvas.line(ox, oy + y * scale, ox + width * scale,
                oy + y * scale, GRID, 0.6f);
        for (Area area : areas) {
            canvas.strokeRect(ox + area.x * scale, oy + (height - area.y - area.height) * scale,
                    area.width * scale, area.height * scale,
                    area.primary ? OBJECTIVE : new Color(152, 134, 89), area.primary ? 2 : 1);
        }
        for (LaneRoute lane : lanes) {
            for (int i = 1; i < lane.route().size(); i++) {
                var a = lane.route().get(i - 1); var b = lane.route().get(i);
                canvas.line(ox + a.x() * scale, oy + (height - a.y()) * scale,
                        ox + b.x() * scale, oy + (height - b.y()) * scale, OBJECTIVE, 1.5f);
            }
        }
        float landingX = 0, landingY = 0;
        int landingCount = 0;
        for (Marker marker : markers) {
            float x = ox + marker.x * scale, y = oy + (height - marker.y) * scale;
            Color color = marker.landing ? LANDING : OBJECTIVE;
            float size = marker.landing ? 5 : 3;
            canvas.fillRect(x - size / 2, y - size / 2, size, size, color);
            if (marker.landing) {
                canvas.strokeRect(x - 4, y - 4, 8, 8, color, 1);
                landingX += x; landingY += y; landingCount++;
            }
        }
        // Label only the route and primary objective on dense maps. Every site still has its footprint.
        for (LaneRoute lane : lanes) {
            if (lane.route().isEmpty()) continue;
            var mid = lane.route().get(lane.route().size() / 2);
            label(canvas, "LANE " + (lane.lane() + 1), ox + mid.x() * scale + 8,
                    oy + (height - mid.y()) * scale + 8, OBJECTIVE);
        }
        for (Marker marker : markers) {
            if (!marker.label.isEmpty()) label(canvas, marker.label,
                    ox + marker.x * scale + 9, oy + (height - marker.y) * scale - 23, OBJECTIVE);
        }
        if (landingCount > 0) label(canvas, "DROP ZONES", landingX / landingCount - 115,
                landingY / landingCount - 8, LANDING);
        canvas.strokeRect(ox, oy, width * scale, height * scale, GRID, 1);
    }

    private static void label(CanvasContext canvas, String text, float x, float y, Color color) {
        float width = Fonts.ORBITRON_12_BOLD.measureWidth(text) + 10;
        x = Math.max(4, Math.min(canvas.metrics().surfaceWidth() - width - 4, x));
        y = Math.max(4, Math.min(canvas.metrics().surfaceHeight() - 20, y));
        canvas.fillRect(x, y, width, 19, BACKGROUND);
        canvas.text(Fonts.ORBITRON_12_BOLD, text, x + 5, y + 3, color);
    }
}
