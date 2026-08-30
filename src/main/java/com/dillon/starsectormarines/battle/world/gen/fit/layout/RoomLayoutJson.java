package com.dillon.starsectormarines.battle.world.gen.fit.layout;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.Gantry;
import com.dillon.starsectormarines.battle.world.gen.fit.Hookup;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomFit;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading and writing a {@link RoomLayout} as JSON.
 *
 * <p>Kept clear of {@code Global} on purpose. The workbench writes these files
 * and tests read them without a game process, so the parser takes a
 * {@code JSONObject} and the caller decides where it came from — the same seam
 * {@code TileRegistry.ingestSheet} uses.
 *
 * <p>Every failure is loud and names what was wrong. The alternative is already
 * known in this package: a layout that half-parses furnishes a room half way and
 * reads as a fill defect for as long as it takes somebody to go looking.
 */
public final class RoomLayoutJson {

    private RoomLayoutJson() {}

    /** The mask character for floor, matching {@link RoomShape#of}. */
    private static final char FLOOR = '#';

    public static RoomLayout parse(JSONObject root) {
        RoomPurpose purpose = enumValue(RoomPurpose.class, root.optString("purpose", null), "purpose");
        RoomFit fit = enumValue(RoomFit.class, root.optString("fit", "STANDARD"), "fit");
        RoomShape shape = parseShape(root.optJSONArray("shape"));
        List<LayoutOp> ops = parseOps(root.optJSONArray("ops"));
        List<Hookup> hookups = parseHookups(root.optJSONArray("hookups"));
        return new RoomLayout(purpose, fit, shape, ops, hookups, root.optBoolean("handed", false));
    }

    private static RoomShape parseShape(JSONArray rows) {
        if (rows == null || rows.length() == 0) {
            throw new IllegalArgumentException(
                    "room layout: 'shape' is required and names the footprint");
        }
        String[] lines = new String[rows.length()];
        for (int i = 0; i < rows.length(); i++) {
            lines[i] = rows.optString(i, "");
        }
        return RoomShape.of(lines);
    }

    private static List<LayoutOp> parseOps(JSONArray array) {
        List<LayoutOp> ops = new ArrayList<>();
        if (array == null) return ops;
        for (int i = 0; i < array.length(); i++) {
            JSONObject op = array.optJSONObject(i);
            if (op == null) {
                throw new IllegalArgumentException("room layout: step " + i + " is not an object");
            }
            ops.add(parseOp(op, i));
        }
        return ops;
    }

    private static LayoutOp parseOp(JSONObject op, int index) {
        String kind = op.optString("op", "");
        int x = op.optInt("x");
        int y = op.optInt("y");
        switch (kind) {
            case "lane":
                return new LayoutOp.Lane(x, y, span(op, "spanX"), span(op, "spanY"));
            case "closed":
                return new LayoutOp.Closed(x, y, span(op, "spanX"), span(op, "spanY"));
            case "ground":
                return new LayoutOp.Ground(x, y, span(op, "spanX"), span(op, "spanY"),
                        enumValue(GroundKind.class, op.optString("kind", null), "ground kind"));
            case "pave":
                return new LayoutOp.Paving(x, y, requireId(op, index));
            case "fixture":
                return new LayoutOp.Fixture(x, y, requireId(op, index),
                        op.has("affordance")
                                ? enumValue(Affordance.class, op.optString("affordance"), "affordance")
                                : null);
            case "task":
                return new LayoutOp.Task(x, y,
                        enumValue(Affordance.class, op.optString("affordance", null), "affordance"),
                        op.optInt("fixtureX"), op.optInt("fixtureY"));
            case "berthTask":
                return new LayoutOp.BerthTask(x, y, op.optInt("berth"),
                        op.optInt("fixtureX"), op.optInt("fixtureY"));
            case "berth":
                return new LayoutOp.Berth(x, y, span(op, "spanX"), span(op, "spanY"),
                        enumValue(Gantry.Facing.class, op.optString("facing", null), "berth facing"));
            default:
                throw new IllegalArgumentException(
                        "room layout: step " + index + " has unknown op '" + kind + "'");
        }
    }

    private static String requireId(JSONObject op, int index) {
        String id = op.optString("id", "");
        if (id.isEmpty()) {
            throw new IllegalArgumentException(
                    "room layout: step " + index + " needs a doodad 'id'");
        }
        return id;
    }

    private static int span(JSONObject op, String key) {
        return Math.max(1, op.optInt(key, 1));
    }

    private static List<Hookup> parseHookups(JSONArray array) {
        List<Hookup> hookups = new ArrayList<>();
        if (array == null) return hookups;
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            JSONArray slots = entry == null ? null : entry.optJSONArray("slots");
            if (slots == null || slots.length() == 0) {
                throw new IllegalArgumentException("room layout: hookup " + i + " has no doorway");
            }
            List<Hookup.DoorSlot> parsed = new ArrayList<>();
            for (int s = 0; s < slots.length(); s++) {
                parsed.add(parseSlot(slots.optJSONObject(s), i, s));
            }
            hookups.add(new Hookup(parsed));
        }
        return hookups;
    }

    private static Hookup.DoorSlot parseSlot(JSONObject slot, int hookup, int index) {
        JSONArray cells = slot == null ? null : slot.optJSONArray("cells");
        if (cells == null || cells.length() == 0) {
            throw new IllegalArgumentException("room layout: hookup " + hookup
                    + " doorway " + index + " has no cells");
        }
        List<int[]> parsed = new ArrayList<>();
        for (int i = 0; i < cells.length(); i++) {
            JSONArray cell = cells.optJSONArray(i);
            if (cell == null || cell.length() < 2) {
                throw new IllegalArgumentException("room layout: hookup " + hookup
                        + " doorway " + index + " cell " + i + " is not an [x, y] pair");
            }
            parsed.add(new int[]{ cell.optInt(0), cell.optInt(1) });
        }
        return new Hookup.DoorSlot(parsed);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, String what) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("room layout: '" + what + "' is required");
        }
        for (E value : type.getEnumConstants()) {
            if (value.name().equals(name)) return value;
        }
        throw new IllegalArgumentException("room layout: unknown " + what + " '" + name + "'");
    }

    /**
     * Write a layout back out.
     *
     * <p>Round-trips. The workbench seeds a document from a procedural fitting
     * and the author corrects it, so what is written has to parse back to the
     * same arrangement or the editor is quietly losing work.
     */
    public static JSONObject write(RoomLayout layout) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("purpose", layout.purpose().name());
        root.put("fit", layout.fit().name());
        if (layout.handed()) root.put("handed", true);
        root.put("shape", shapeRows(layout.shape()));

        JSONArray ops = new JSONArray();
        for (LayoutOp op : layout.ops()) {
            ops.put(writeOp(op));
        }
        root.put("ops", ops);

        if (!layout.hookups().isEmpty()) {
            JSONArray hookups = new JSONArray();
            for (Hookup hookup : layout.hookups()) {
                JSONArray slots = new JSONArray();
                for (Hookup.DoorSlot slot : hookup.slots()) {
                    JSONArray cells = new JSONArray();
                    for (int[] cell : slot.cells()) {
                        cells.put(new JSONArray().put(cell[0]).put(cell[1]));
                    }
                    slots.put(new JSONObject().put("cells", cells));
                }
                hookups.put(new JSONObject().put("slots", slots));
            }
            root.put("hookups", hookups);
        }
        return root;
    }

    private static JSONArray shapeRows(RoomShape shape) throws JSONException {
        JSONArray rows = new JSONArray();
        for (int y = 0; y < shape.height(); y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < shape.width(); x++) {
                row.append(shape.contains(x, y) ? FLOOR : '.');
            }
            rows.put(row.toString());
        }
        return rows;
    }

    private static JSONObject writeOp(LayoutOp op) throws JSONException {
        JSONObject json = new JSONObject();
        if (op instanceof LayoutOp.Lane lane) {
            json.put("op", "lane").put("x", lane.x()).put("y", lane.y())
                    .put("spanX", lane.spanX()).put("spanY", lane.spanY());
        } else if (op instanceof LayoutOp.Closed closed) {
            json.put("op", "closed").put("x", closed.x()).put("y", closed.y())
                    .put("spanX", closed.spanX()).put("spanY", closed.spanY());
        } else if (op instanceof LayoutOp.Ground ground) {
            json.put("op", "ground").put("x", ground.x()).put("y", ground.y())
                    .put("spanX", ground.spanX()).put("spanY", ground.spanY())
                    .put("kind", ground.kind().name());
        } else if (op instanceof LayoutOp.Paving paving) {
            json.put("op", "pave").put("x", paving.x()).put("y", paving.y())
                    .put("id", paving.doodadId());
        } else if (op instanceof LayoutOp.Fixture fixture) {
            json.put("op", "fixture").put("x", fixture.x()).put("y", fixture.y())
                    .put("id", fixture.doodadId());
            if (fixture.affordance() != null) json.put("affordance", fixture.affordance().name());
        } else if (op instanceof LayoutOp.Task task) {
            json.put("op", "task").put("x", task.x()).put("y", task.y())
                    .put("affordance", task.affordance().name())
                    .put("fixtureX", task.fixtureX()).put("fixtureY", task.fixtureY());
        } else if (op instanceof LayoutOp.BerthTask task) {
            json.put("op", "berthTask").put("x", task.x()).put("y", task.y())
                    .put("berth", task.berth())
                    .put("fixtureX", task.fixtureX()).put("fixtureY", task.fixtureY());
        } else if (op instanceof LayoutOp.Berth berth) {
            json.put("op", "berth").put("x", berth.x()).put("y", berth.y())
                    .put("spanX", berth.spanX()).put("spanY", berth.spanY())
                    .put("facing", berth.facing().name());
        } else {
            throw new IllegalStateException("room layout: no writer for " + op.getClass());
        }
        return json;
    }
}
