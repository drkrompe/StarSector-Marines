package com.dillon.starsectormarines.battle.turret;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Immutable bounded stamp geometry for one defense-post layout. */
public final class DefensePostLayoutDef {

    public static final int MAX_EXTENT_CELLS = 15;

    public final String id;
    public final DefensePostKind tier;
    public final String variant;
    public final int anchorX;
    public final int anchorY;
    public final int minOffsetX;
    public final int minOffsetY;
    public final int maxOffsetX;
    public final int maxOffsetY;
    public final List<Cell> cells;
    public final List<TurretPlacement> turrets;

    private final Map<Offset, Cell> cellsByOffset;

    private DefensePostLayoutDef(String id, DefensePostKind tier, String variant,
                                 int anchorX, int anchorY,
                                 int minOffsetX, int minOffsetY,
                                 int maxOffsetX, int maxOffsetY,
                                 List<Cell> cells, List<TurretPlacement> turrets) {
        this.id = id;
        this.tier = tier;
        this.variant = variant;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.minOffsetX = minOffsetX;
        this.minOffsetY = minOffsetY;
        this.maxOffsetX = maxOffsetX;
        this.maxOffsetY = maxOffsetY;
        this.cells = List.copyOf(cells);
        this.turrets = List.copyOf(turrets);
        Map<Offset, Cell> index = new HashMap<>();
        for (Cell cell : cells) index.put(cell.offset, cell);
        this.cellsByOffset = Map.copyOf(index);
    }

    public int halfExtentX() {
        return Math.max(Math.abs(minOffsetX), Math.abs(maxOffsetX));
    }

    public int halfExtentY() {
        return Math.max(Math.abs(minOffsetY), Math.abs(maxOffsetY));
    }

    public Cell cellAt(int offsetX, int offsetY) {
        return cellsByOffset.get(new Offset(offsetX, offsetY));
    }

    public List<Offset> blockedOffsets() {
        return cells.stream().map(cell -> cell.offset).toList();
    }

    public Offset droneHubOffset() {
        for (Cell cell : cells) {
            if (cell.padOccupant == PadOccupant.DRONE_HUB) return cell.offset;
        }
        return null;
    }

    static DefensePostLayoutDef parse(JSONObject json, TurretCatalogRegistry turrets)
            throws JSONException {
        String id = requireText(json, "id");
        DefensePostKind tier = parseTier(requireText(json, "tier"), id);
        String variant = requireText(json, "variant");
        int[] anchor = pair(json.getJSONArray("anchor"), id + ".anchor");
        if (anchor[0] != 0 || anchor[1] != 0) {
            throw new JSONException("Defense-post layout '" + id
                    + "' anchor must be the relative origin [0, 0]");
        }
        JSONObject bounds = json.getJSONObject("bounds");
        int[] min = pair(bounds.getJSONArray("minOffset"), id + ".bounds.minOffset");
        int[] max = pair(bounds.getJSONArray("maxOffset"), id + ".bounds.maxOffset");
        validateBounds(id, min, max);

        JSONArray cellJson = json.getJSONArray("cells");
        if (cellJson.length() == 0) {
            throw new JSONException("Defense-post layout '" + id + "' must contain a cell");
        }
        List<Cell> cells = new ArrayList<>(cellJson.length());
        Map<Offset, Cell> cellsByOffset = new HashMap<>();
        for (int i = 0; i < cellJson.length(); i++) {
            Cell cell = parseCell(cellJson.getJSONObject(i), id, min, max);
            if (cellsByOffset.put(cell.offset, cell) != null) {
                throw new JSONException("Defense-post layout '" + id
                        + "' duplicates cell offset " + cell.offset);
            }
            cells.add(cell);
        }

        JSONArray turretJson = json.getJSONArray("turrets");
        List<TurretPlacement> placements = new ArrayList<>(turretJson.length());
        Set<Offset> turretOffsets = new HashSet<>();
        for (int i = 0; i < turretJson.length(); i++) {
            JSONObject object = turretJson.getJSONObject(i);
            int[] offsetPair = pair(object.getJSONArray("offset"), id + ".turrets[" + i + "].offset");
            Offset offset = new Offset(offsetPair[0], offsetPair[1]);
            if (!turretOffsets.add(offset)) {
                throw new JSONException("Defense-post layout '" + id
                        + "' duplicates turret offset " + offset);
            }
            Cell cell = cellsByOffset.get(offset);
            if (cell == null || cell.kind != CellKind.PAD || cell.padOccupant != PadOccupant.NONE) {
                throw new JSONException("Defense-post layout '" + id
                        + "' turret at " + offset + " must occupy an ordinary pad cell");
            }
            String structureId = requireText(object, "structure");
            StructureDef structure = turrets.getStructure(structureId);
            if (structure == null) {
                throw new JSONException("Defense-post layout '" + id
                        + "' references unknown turret structure '" + structureId + "'");
            }
            if (structure.footprintCellsX != 1 || structure.footprintCellsY != 1) {
                throw new JSONException("Defense-post layout '" + id
                        + "' requires one-cell turret structure '" + structureId + "'");
            }
            placements.add(new TurretPlacement(offset, structureId, structure));
        }
        validateOccupants(id, tier, cells, placements);
        return new DefensePostLayoutDef(id, tier, variant, anchor[0], anchor[1],
                min[0], min[1], max[0], max[1], cells, placements);
    }

    private static Cell parseCell(JSONObject json, String id, int[] min, int[] max)
            throws JSONException {
        int[] pair = pair(json.getJSONArray("offset"), id + ".cell.offset");
        Offset offset = new Offset(pair[0], pair[1]);
        if (offset.x < min[0] || offset.x > max[0]
                || offset.y < min[1] || offset.y > max[1]) {
            throw new JSONException("Defense-post layout '" + id
                    + "' cell " + offset + " lies outside its bounds");
        }
        CellKind kind = CellKind.parse(requireText(json, "kind"), id);
        if (kind == CellKind.BARRIER) {
            BarrierAppearance appearance = BarrierAppearance.parse(
                    requireText(json, "appearance"), id);
            int[] facingPair = pair(json.getJSONArray("facing"), id + ".cell.facing");
            if (Math.abs(facingPair[0]) > 1 || Math.abs(facingPair[1]) > 1
                    || facingPair[0] == 0 && facingPair[1] == 0) {
                throw new JSONException("Defense-post layout '" + id
                        + "' barrier facing must be a non-zero [-1,1] vector");
            }
            return new Cell(offset, kind, appearance,
                    new Offset(facingPair[0], facingPair[1]), PadOccupant.NONE);
        }
        String occupantKey = json.optString("occupant", "none");
        return new Cell(offset, kind, null, null, PadOccupant.parse(occupantKey, id));
    }

    private static void validateBounds(String id, int[] min, int[] max) throws JSONException {
        if (min[0] > 0 || min[1] > 0 || max[0] < 0 || max[1] < 0
                || min[0] > max[0] || min[1] > max[1]) {
            throw new JSONException("Defense-post layout '" + id
                    + "' bounds must contain the [0,0] anchor");
        }
        int width = max[0] - min[0] + 1;
        int height = max[1] - min[1] + 1;
        if (width > MAX_EXTENT_CELLS || height > MAX_EXTENT_CELLS) {
            throw new JSONException("Defense-post layout '" + id + "' exceeds "
                    + MAX_EXTENT_CELLS + " cells on an axis");
        }
    }

    private static void validateOccupants(String id, DefensePostKind tier,
                                          List<Cell> cells,
                                          List<TurretPlacement> turrets) throws JSONException {
        long hubs = cells.stream()
                .filter(cell -> cell.padOccupant == PadOccupant.DRONE_HUB).count();
        if (tier == DefensePostKind.DRONE_HUB) {
            if (hubs != 1 || !turrets.isEmpty()) {
                throw new JSONException("DRONE_HUB layout '" + id
                        + "' requires one drone-hub pad and no turrets");
            }
        } else if (hubs != 0) {
            throw new JSONException("Non-DRONE_HUB layout '" + id
                    + "' may not contain a drone-hub pad");
        } else if (turrets.isEmpty()) {
            throw new JSONException("Defense-post layout '" + id
                    + "' must contain at least one turret");
        }
    }

    private static DefensePostKind parseTier(String key, String id) throws JSONException {
        try {
            return DefensePostKind.valueOf(key.replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JSONException("Defense-post layout '" + id
                    + "' has unknown tier '" + key + "'");
        }
    }

    static String requireText(JSONObject json, String key) throws JSONException {
        String value = json.optString(key, null);
        if (value == null || value.trim().isEmpty() || "null".equals(value.trim())) {
            throw new JSONException("Defense-post layout is missing text field '" + key + "'");
        }
        return value.trim();
    }

    private static int[] pair(JSONArray array, String where) throws JSONException {
        if (array.length() != 2) throw new JSONException(where + " must contain [x, y]");
        return new int[]{array.getInt(0), array.getInt(1)};
    }

    public record Offset(int x, int y) {}

    public record Cell(Offset offset, CellKind kind,
                       BarrierAppearance appearance, Offset facing,
                       PadOccupant padOccupant) {}

    public record TurretPlacement(Offset offset, String structureId,
                                  StructureDef structure) {}

    public enum CellKind {
        BARRIER, PAD;

        static CellKind parse(String key, String id) throws JSONException {
            try {
                return valueOf(key.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new JSONException("Defense-post layout '" + id
                        + "' has unknown cell kind '" + key + "'");
            }
        }
    }

    public enum BarrierAppearance {
        VENT, EMBANKMENT, BOW_OUT;

        static BarrierAppearance parse(String key, String id) throws JSONException {
            try {
                return valueOf(key.replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new JSONException("Defense-post layout '" + id
                        + "' has unknown barrier appearance '" + key + "'");
            }
        }
    }

    public enum PadOccupant {
        NONE, DRONE_HUB;

        static PadOccupant parse(String key, String id) throws JSONException {
            try {
                return valueOf(key.replace('-', '_').toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new JSONException("Defense-post layout '" + id
                        + "' has unknown pad occupant '" + key + "'");
            }
        }
    }
}
