package com.dillon.starsectormarines.battle.mech;

import com.dillon.starsectormarines.battle.mech.MechFittingLayout.DollDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketDef;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketId;
import com.dillon.starsectormarines.battle.mech.MechFittingLayout.SocketType;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Data authority for chassis fitting dolls, hardpoints, and CAD anchors.
 */
public final class MechFittingDollCatalog {

    private static final Logger LOG = Logger.getLogger(MechFittingDollCatalog.class);

    public static final String CONTENT_PATH = "data/mechs/mech-fitting-dolls.fitting.json";

    private static volatile MechFittingDollCatalog installed;

    private final Map<MechVariant, DollDef> dolls;

    public MechFittingDollCatalog(Map<MechVariant, DollDef> dolls) {
        this.dolls = Map.copyOf(dolls);
    }

    public static MechFittingDollCatalog installed() {
        if (installed == null) {
            synchronized (MechFittingDollCatalog.class) {
                if (installed == null) {
                    installed = loadDefaultFallback();
                }
            }
        }
        return installed;
    }

    public static void install(MechFittingDollCatalog catalog) {
        if (catalog == null) throw new IllegalArgumentException("catalog cannot be null");
        installed = catalog;
    }

    public static void loadBuiltins() {
        try {
            JSONObject json = Global.getSettings().loadJSON(CONTENT_PATH, true);
            MechFittingDollCatalog catalog = parse(json);
            install(catalog);
            LOG.info("Mech fitting doll catalog loaded with " + catalog.dolls.size() + " chassis dolls");
        } catch (Exception e) {
            LOG.warn("Failed to load mech fitting doll catalog from game settings; trying filesystem fallback", e);
            install(loadDefaultFallback());
        }
    }

    public static MechFittingDollCatalog parse(JSONObject root) throws JSONException {
        int schemaVersion = root.optInt("schemaVersion", 1);
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("Unsupported schemaVersion: " + schemaVersion);
        }

        JSONArray dollsArray = root.getJSONArray("dolls");
        EnumMap<MechVariant, DollDef> parsedDolls = new EnumMap<>(MechVariant.class);

        for (int i = 0; i < dollsArray.length(); i++) {
            JSONObject dollObj = dollsArray.getJSONObject(i);
            String variantKey = dollObj.getString("variant").toUpperCase(Locale.ROOT);
            MechVariant variant = MechVariant.valueOf(variantKey);
            float facingDegrees = (float) dollObj.getDouble("facingDegrees");
            if (!Float.isFinite(facingDegrees)) {
                throw new IllegalArgumentException("Doll facing for " + variant + " must be finite");
            }

            JSONArray socketsArray = dollObj.getJSONArray("sockets");
            List<SocketDef> sockets = new ArrayList<>();
            Set<SocketId> seenIds = new HashSet<>();

            for (int s = 0; s < socketsArray.length(); s++) {
                JSONObject sockObj = socketsArray.getJSONObject(s);
                String idStr = sockObj.getString("id").toUpperCase(Locale.ROOT);
                SocketId id = SocketId.valueOf(idStr);
                if (!seenIds.add(id)) {
                    throw new IllegalArgumentException("Duplicate socket " + id + " on chassis " + variant);
                }

                String label = sockObj.optString("label", id.label());
                String typeStr = sockObj.getString("type").toUpperCase(Locale.ROOT);
                SocketType type = SocketType.valueOf(typeStr);

                int gridColumns = sockObj.getInt("gridColumns");
                int gridRows = sockObj.getInt("gridRows");
                if (gridColumns < 1 || gridColumns > MechFittingLayout.MAX_GRID_COLUMNS
                        || gridRows < 1 || gridRows > MechFittingLayout.MAX_GRID_ROWS) {
                    throw new IllegalArgumentException("Invalid grid dimensions " + gridColumns + "x" + gridRows
                            + " for socket " + id + " on " + variant);
                }

                JSONArray anchorArr = sockObj.getJSONArray("anchor");
                float anchorRight = (float) anchorArr.getDouble(0);
                float anchorForward = (float) anchorArr.getDouble(1);
                if (!Float.isFinite(anchorRight) || !Float.isFinite(anchorForward)) {
                    throw new IllegalArgumentException("Non-finite anchor for socket " + id + " on " + variant);
                }

                JSONArray dockArr = sockObj.optJSONArray("dockDirection");
                float dockRight = dockArr != null ? (float) dockArr.getDouble(0) : anchorRight;
                float dockForward = dockArr != null ? (float) dockArr.getDouble(1) : anchorForward;

                JSONArray footprintArr = sockObj.optJSONArray("footprintHull");
                float footprintWidth = footprintArr != null ? (float) footprintArr.getDouble(0) : 1.5f;
                float footprintHeight = footprintArr != null ? (float) footprintArr.getDouble(1) : 0.7f;
                if (footprintWidth <= 0f || footprintHeight <= 0f) {
                    throw new IllegalArgumentException("Footprint must be positive for socket " + id);
                }

                boolean factoryLocked = sockObj.optBoolean("factoryLocked", false);

                sockets.add(new SocketDef(id, type, gridColumns, gridRows,
                        anchorRight, anchorForward, dockRight, dockForward,
                        footprintWidth, footprintHeight, factoryLocked, label));
            }

            if (sockets.isEmpty()) {
                throw new IllegalArgumentException("Chassis " + variant + " must have at least one socket");
            }

            parsedDolls.put(variant, new DollDef(facingDegrees, sockets));
        }

        return new MechFittingDollCatalog(parsedDolls);
    }

    private static MechFittingDollCatalog loadDefaultFallback() {
        Map<MechVariant, DollDef> dolls = new EnumMap<>(MechVariant.class);

        // Hound (Light mech: 4 sockets)
        dolls.put(MechVariant.HOUND, new DollDef(180f, List.of(
                new SocketDef(SocketId.CORE, SocketType.CORE, 2, 2,
                        0.0f, 0.0f, 0.0f, -1.45f, 1.36f, 0.68f, true, "ENGINE CORE"),
                new SocketDef(SocketId.ARMS, SocketType.BALLISTIC, 2, 2,
                        0.0f, 0.24f, 0.0f, 1.45f, 1.36f, 0.68f, false, "NOSE MOUNT"),
                new SocketDef(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 2, 2,
                        0.0f, -0.16f, -1.34f, -0.46f, 1.36f, 0.68f, false, "SHOULDER"),
                new SocketDef(SocketId.AMMO_RESERVE, SocketType.AMMO, 2, 1,
                        0.0f, -0.26f, -1.34f, 0.68f, 1.36f, 0.38f, true, "AMMO RESERVE")
        )));

        // Sirocco (6 sockets)
        dolls.put(MechVariant.SIROCCO, new DollDef(180f, List.of(
                new SocketDef(SocketId.CORE, SocketType.CORE, 2, 2,
                        0.0f, 0.0f, 0.0f, -1.45f, 1.36f, 0.68f, true, "ENGINE CORE"),
                new SocketDef(SocketId.ARMS, SocketType.BALLISTIC, 3, 2,
                        0.0f, 0.26f, 0.0f, 1.45f, 1.70f, 0.68f, false, "NOSE CANNON"),
                new SocketDef(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 3, 2,
                        -0.36f, -0.15f, -1.34f, -0.46f, 1.36f, 0.68f, false, "L. SHOULDER"),
                new SocketDef(SocketId.RIGHT_SHOULDER, SocketType.OMNI, 3, 2,
                        0.36f, -0.15f, 1.34f, -0.46f, 1.36f, 0.68f, false, "R. SHOULDER"),
                new SocketDef(SocketId.AMMO_RESERVE, SocketType.AMMO, 2, 2,
                        0.0f, -0.26f, -1.34f, 0.68f, 1.36f, 0.68f, true, "AMMO RESERVE"),
                new SocketDef(SocketId.MINI_FAB, SocketType.UTILITY, 1, 1,
                        0.0f, -0.36f, 1.34f, 0.68f, 0.80f, 0.44f, false, "MINI-FAB")
        )));

        // Bulwark (6 sockets)
        dolls.put(MechVariant.BULWARK, new DollDef(180f, List.of(
                new SocketDef(SocketId.CORE, SocketType.CORE, 2, 2,
                        0.0f, 0.03f, 0.0f, -1.50f, 1.70f, 0.68f, true, "ENGINE CORE"),
                new SocketDef(SocketId.ARMS, SocketType.OMNI, 3, 2,
                        0.0f, 0.22f, 0.0f, 1.50f, 1.70f, 0.68f, false, "ARM ASSEMBLY"),
                new SocketDef(SocketId.LEFT_SHOULDER, SocketType.MISSILE, 3, 2,
                        -0.36f, -0.12f, -1.38f, -0.48f, 1.40f, 0.68f, false, "L. SHOULDER"),
                new SocketDef(SocketId.RIGHT_SHOULDER, SocketType.OMNI, 3, 2,
                        0.36f, -0.12f, 1.38f, -0.48f, 1.40f, 0.68f, false, "R. SHOULDER"),
                new SocketDef(SocketId.AMMO_RESERVE, SocketType.AMMO, 3, 1,
                        0.0f, -0.26f, -1.38f, 0.72f, 1.40f, 0.38f, true, "AMMO RESERVE"),
                new SocketDef(SocketId.MINI_FAB, SocketType.UTILITY, 1, 1,
                        0.0f, -0.36f, 1.38f, 0.72f, 0.80f, 0.44f, false, "MINI-FAB")
        )));

        return new MechFittingDollCatalog(dolls);
    }

    public DollDef doll(MechVariant variant) {
        return dolls.get(variant);
    }

    public boolean hasDoll(MechVariant variant) {
        return dolls.containsKey(variant);
    }

    public Map<MechVariant, DollDef> dolls() {
        return dolls;
    }
}
