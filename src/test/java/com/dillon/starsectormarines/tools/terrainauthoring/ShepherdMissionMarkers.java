package com.dillon.starsectormarines.tools.terrainauthoring;

import com.dillon.starsectormarines.battle.setup.ColdStartSequence;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin.Place;
import com.dillon.starsectormarines.battle.world.gen.ShepherdBasin.Visit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Review markers over a whole-map basin overview: where each visit's objectives sit and where
 * its forces start or arrive. Positions come from the same constants the mission setup reads,
 * so a marker cannot describe a spawn the battle no longer uses. Review aid only; nothing here
 * reaches the game.
 */
final class ShepherdMissionMarkers {
    private enum Kind {
        OBJECTIVE(new Color(255, 214, 64)), ENEMY(new Color(240, 64, 52)), ENEMY_ENTRY(new Color(255, 120, 40)),
        COMPANY(new Color(80, 220, 255)), ALLY(new Color(120, 235, 120));
        final Color color;
        Kind(Color color) { this.color = color; }
    }
    /** Where a label sits relative to its mark, for marks that would otherwise collide. */
    private enum LabelAt { DEFAULT, ABOVE, BELOW, LEFT, RIGHT }
    private record Marker(Kind kind, float x, float y, String label, LabelAt at) {
        Marker(Kind kind, float x, float y, String label) { this(kind, x, y, label, LabelAt.DEFAULT); }
    }

    private final float cellPx, originX, originY;
    private final int height;

    private ShepherdMissionMarkers(int imageWidth, int imageHeight) {
        cellPx = Math.min(imageWidth / (float) ShepherdBasin.WIDTH, imageHeight / (float) ShepherdBasin.HEIGHT);
        originX = imageWidth / 2f - ShepherdBasin.WIDTH / 2f * cellPx;
        originY = imageHeight / 2f + ShepherdBasin.HEIGHT / 2f * cellPx;
        height = imageHeight;
    }

    /** A copy of a whole-map overview with this visit's markers, route and a legend drawn over it. */
    static BufferedImage annotate(BufferedImage overview, MapResult map, Visit visit) {
        BufferedImage out = new BufferedImage(overview.getWidth(), overview.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(overview, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var view = new ShepherdMissionMarkers(overview.getWidth(), overview.getHeight());
        List<Marker> markers = markers(map, visit);
        if (visit == Visit.WORKING_PASSAGE) view.route(g);
        if (visit == Visit.COLD_START) {
            view.arrow(g, ShepherdBasin.SOUTH_HAUL_ROAD.x(), ShepherdBasin.SOUTH_HAUL_ROAD.y(), 194, 45);
            view.arrow(g, ShepherdBasin.QUARRY_ROAD.x(), ShepherdBasin.QUARRY_ROAD.y(), 270, 145);
        } else {
            view.arrow(g, ShepherdBasin.SOUTH_HAUL_ROAD.x(), ShepherdBasin.SOUTH_HAUL_ROAD.y(), 194, 45);
            if (visit == Visit.WORKING_PASSAGE)
                view.arrow(g, ShepherdBasin.QUARRY_ROAD.x(), ShepherdBasin.QUARRY_ROAD.y(), 270, 145);
        }
        for (Marker marker : markers) view.draw(g, marker);
        view.legend(g, visit);
        g.dispose();
        return out;
    }

    private static List<Marker> markers(MapResult map, Visit visit) {
        List<Marker> m = new ArrayList<>();
        var receiving = map.pointsOfInterest.get(0);
        var workshop = map.pointsOfInterest.get(1);
        m.add(at(Kind.ALLY, ShepherdBasin.MILITIA, "Vale's militia"));
        switch (visit) {
            case SALVAGE_RIGHTS -> {
                berths(m, map);
                m.add(new Marker(Kind.OBJECTIVE, receiving.interiorAnchorX, receiving.interiorAnchorY, "1 Clear receiving store"));
                m.add(new Marker(Kind.OBJECTIVE, workshop.interiorAnchorX, workshop.interiorAnchorY, "2 Secure workshop"));
                m.add(new Marker(Kind.OBJECTIVE, 127, 101, "3-4 Morrow south access + inspection"));
                m.add(new Marker(Kind.ENEMY, receiving.interiorAnchorX + 3, receiving.interiorAnchorY - 3, "Scrapper watch (4)"));
                m.add(new Marker(Kind.ENEMY, workshop.interiorAnchorX + 3, workshop.interiorAnchorY - 3, "Scrapper watch (4)"));
                m.add(at(Kind.ENEMY, ShepherdBasin.CRADLE_CUTTERS, "Cutting crew (4)"));
                m.add(at(Kind.ENEMY_ENTRY, ShepherdBasin.SOUTH_HAUL_ROAD, "Reserve (8) at cradle phase"));
            }
            case COLD_START -> {
                m.add(new Marker(Kind.COMPANY, 18, 68, "Company on foot", LabelAt.ABOVE));
                m.add(new Marker(Kind.COMPANY, 8, 68, "Cargo carrier", LabelAt.BELOW));
                m.add(new Marker(Kind.OBJECTIVE, ColdStartSequence.WORKSHOP_X, ColdStartSequence.WORKSHOP_Y, "1 Escort cargo to workshop"));
                m.add(new Marker(Kind.OBJECTIVE, ColdStartSequence.PUMP_X, ColdStartSequence.PUMP_Y, "2 Restore pump power"));
                m.add(new Marker(Kind.OBJECTIVE, ColdStartSequence.BUILD_X, ColdStartSequence.BUILD_Y, "3 Build turret", LabelAt.BELOW));
                m.add(new Marker(Kind.OBJECTIVE, ColdStartSequence.BAY_X, ColdStartSequence.BAY_Y, "4 Repair Morrow", LabelAt.LEFT));
                m.add(new Marker(Kind.ENEMY, ShepherdBasin.PUMP.x() + 4, ShepherdBasin.PUMP.y() - 4, "Pump watch (8, unpowered only)", LabelAt.RIGHT));
                m.add(at(Kind.ALLY, ShepherdBasin.SOUTH_PICKET, "Day-hand picket (3)"));
                m.add(at(Kind.ALLY, ShepherdBasin.QUARRY_PICKET, "Day-hand picket (3)"));
                m.add(at(Kind.ENEMY_ENTRY, ShepherdBasin.SOUTH_HAUL_ROAD, "Harlan waves 1, 2, 4 (6 each)"));
                m.add(at(Kind.ENEMY_ENTRY, ShepherdBasin.QUARRY_ROAD, "Harlan wave 3 (6)"));
            }
            case WORKING_PASSAGE -> {
                berths(m, map);
                m.add(new Marker(Kind.COMPANY, ShepherdBasin.HOVER_ROUTE[0][0], ShepherdBasin.HOVER_ROUTE[0][1], "Morrow lifts"));
                m.add(new Marker(Kind.OBJECTIVE, ShepherdBasin.BEND.x(), ShepherdBasin.BEND.y(), "1 Bend: clear + build"));
                m.add(new Marker(Kind.OBJECTIVE, ShepherdBasin.NARROWS.x(), ShepherdBasin.NARROWS.y(), "2 Narrows: clear + build"));
                m.add(new Marker(Kind.OBJECTIVE, ShepherdBasin.LIFT.x(), ShepherdBasin.LIFT.y(), "3 Climb-out: spool + depart"));
                for (Place watch : ShepherdBasin.PASSAGE_WATCHES) m.add(at(Kind.ENEMY, watch, watch.label() + " (4)"));
                m.add(at(Kind.ENEMY_ENTRY, ShepherdBasin.SOUTH_HAUL_ROAD, "Road waves 1, 3, 4 (4/4/6)"));
                m.add(at(Kind.ENEMY_ENTRY, ShepherdBasin.QUARRY_ROAD, "Road wave 2 (6)"));
            }
        }
        return m;
    }

    private static void berths(List<Marker> m, MapResult map) {
        int n = 1;
        for (LandingPad pad : map.landingPads) m.add(new Marker(Kind.COMPANY, pad.centerX, pad.centerY, "LZ " + n++));
    }

    private static Marker at(Kind kind, Place place, String label) { return new Marker(kind, place.x(), place.y(), label); }

    private float px(float x) { return originX + (x + .5f) * cellPx; }
    private float py(float y) { return originY - (y + .5f) * cellPx; }

    private void draw(Graphics2D g, Marker marker) {
        float x = px(marker.x()), y = py(marker.y());
        int r = 11;
        g.setStroke(new BasicStroke(3f));
        switch (marker.kind()) {
            case OBJECTIVE -> {
                g.setColor(Color.BLACK);
                g.fillRect((int) x - r - 2, (int) y - r - 2, 2 * r + 4, 2 * r + 4);
                g.setColor(marker.kind().color);
                g.drawRect((int) x - r, (int) y - r, 2 * r, 2 * r);
                g.fillRect((int) x - 4, (int) y - 4, 8, 8);
            }
            case ENEMY_ENTRY -> {
                Polygon tri = new Polygon(new int[]{(int) x, (int) x - r - 3, (int) x + r + 3},
                        new int[]{(int) y - r - 3, (int) y + r, (int) y + r}, 3);
                g.setColor(Color.BLACK);
                g.setStroke(new BasicStroke(6f));
                g.drawPolygon(tri);
                g.setColor(marker.kind().color);
                g.fillPolygon(tri);
            }
            default -> {
                g.setColor(Color.BLACK);
                g.fillOval((int) x - r - 2, (int) y - r - 2, 2 * r + 4, 2 * r + 4);
                g.setColor(marker.kind().color);
                g.fillOval((int) x - r + 2, (int) y - r + 2, 2 * r - 4, 2 * r - 4);
            }
        }
        // Objectives label above their mark and starts below, so a crew posted on its
        // own objective stays legible.
        LabelAt at = marker.at() != LabelAt.DEFAULT ? marker.at()
                : marker.kind() == Kind.OBJECTIVE ? LabelAt.ABOVE : marker.kind() == Kind.ENEMY ? LabelAt.BELOW : LabelAt.RIGHT;
        float dy = at == LabelAt.ABOVE ? -r - 12 : at == LabelAt.BELOW ? r + 12 : 0;
        if (at == LabelAt.LEFT) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
            label(g, marker.label(), x - r - 10 - g.getFontMetrics().stringWidth(marker.label()), y, marker.kind().color);
        } else label(g, marker.label(), x + r + 6, y + dy, marker.kind().color);
    }

    private void label(Graphics2D g, String text, float x, float y, Color color) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text), top = (int) y - fm.getAscent() / 2 - 3;
        int left = (int) x;
        if (left + w + 8 > 1920) left = (int) x - w - 2 * 17 - 14;
        if (top + fm.getHeight() + 6 > height) top = height - fm.getHeight() - 6;
        g.setColor(new Color(0, 0, 0, 190));
        g.fillRoundRect(left - 4, top, w + 8, fm.getHeight() + 4, 6, 6);
        g.setColor(color);
        g.drawString(text, left, top + fm.getAscent() + 2);
    }

    private void arrow(Graphics2D g, float ax, float ay, float bx, float by) {
        float x0 = px(ax), y0 = py(ay), x1 = px(bx), y1 = py(by);
        double angle = Math.atan2(y1 - y0, x1 - x0);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{12f, 8f}, 0f));
        g.setColor(Kind.ENEMY_ENTRY.color);
        g.drawLine((int) x0, (int) y0, (int) x1, (int) y1);
        Path2D head = new Path2D.Float();
        head.moveTo(x1, y1);
        head.lineTo(x1 - 18 * Math.cos(angle - .45), y1 - 18 * Math.sin(angle - .45));
        head.lineTo(x1 - 18 * Math.cos(angle + .45), y1 - 18 * Math.sin(angle + .45));
        head.closePath();
        g.fill(head);
    }

    private void route(Graphics2D g) {
        List<float[]> points = new ArrayList<>(List.of(ShepherdBasin.HOVER_APPROACH));
        points.add(ShepherdBasin.HOVER_ROUTE[2]);
        points.add(ShepherdBasin.HOVER_ROUTE[3]);
        Path2D path = new Path2D.Float();
        path.moveTo(px(points.get(0)[0]), py(points.get(0)[1]));
        for (int i = 1; i < points.size(); i++) path.lineTo(px(points.get(i)[0]), py(points.get(i)[1]));
        g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(0, 0, 0, 160));
        g.draw(path);
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{16f, 10f}, 0f));
        g.setColor(Kind.COMPANY.color);
        g.draw(path);
    }

    private void legend(Graphics2D g, Visit visit) {
        String title = switch (visit) {
            case SALVAGE_RIGHTS -> "SALVAGE RIGHTS";
            case COLD_START -> "COLD START";
            case WORKING_PASSAGE -> "WORKING PASSAGE";
        };
        String[][] rows = {
                {"Objective (in order)", "OBJECTIVE"}, {"Enemy start", "ENEMY"}, {"Enemy road entry", "ENEMY_ENTRY"},
                {visit == Visit.WORKING_PASSAGE ? "Company / Morrow route" : "Company", "COMPANY"}, {"Allies", "ALLY"}};
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRoundRect(16, 16, 300, 44 + rows.length * 30, 10, 10);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.setColor(Color.WHITE);
        g.drawString(title, 30, 44);
        for (int i = 0; i < rows.length; i++) {
            Kind kind = Kind.valueOf(rows[i][1]);
            int y = 74 + i * 30;
            g.setColor(kind.color);
            if (kind == Kind.OBJECTIVE) g.drawRect(32, y - 9, 18, 18);
            else if (kind == Kind.ENEMY_ENTRY) g.fillPolygon(new int[]{41, 31, 51}, new int[]{y - 10, y + 9, y + 9}, 3);
            else g.fillOval(32, y - 9, 18, 18);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 17));
            g.setColor(Color.WHITE);
            g.drawString(rows[i][0], 62, y + 6);
        }
    }
}
