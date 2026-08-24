package com.dillon.starsectormarines.battle.weapon.fx;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Immutable, id-addressed authored effect composition for one weapon. */
public final class WeaponFxDef {

    public final String id;
    private final Map<FxSlot, List<FxLayerDef>> layersBySlot;

    private WeaponFxDef(String id, EnumMap<FxSlot, List<FxLayerDef>> layersBySlot) {
        this.id = id;
        EnumMap<FxSlot, List<FxLayerDef>> copy = new EnumMap<>(FxSlot.class);
        for (Map.Entry<FxSlot, List<FxLayerDef>> entry : layersBySlot.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.layersBySlot = Collections.unmodifiableMap(copy);
    }

    public List<FxLayerDef> layers(FxSlot slot) {
        List<FxLayerDef> layers = layersBySlot.get(slot);
        return layers != null ? layers : List.of();
    }

    public Map<FxSlot, List<FxLayerDef>> slots() {
        return layersBySlot;
    }

    /** Parses the contents of a weapon's {@code fx} object. */
    public static WeaponFxDef parse(String id, JSONObject json) throws JSONException {
        if (id == null || id.isBlank()) throw new JSONException("FX definition id may not be blank");
        if (json == null) throw new JSONException("FX definition '" + id + "' may not be null");

        EnumMap<FxSlot, List<FxLayerDef>> slots = new EnumMap<>(FxSlot.class);
        Iterator<?> keys = json.keys();
        while (keys.hasNext()) {
            String key = String.valueOf(keys.next());
            FxSlot slot = FxSlot.fromKey(key, id);
            if (slots.containsKey(slot)) {
                throw new JSONException("FX definition '" + id + "' declares slot '"
                        + slot.key + "' more than once");
            }
            Object value = json.get(key);
            if (!(value instanceof JSONArray array)) {
                throw new JSONException("FX definition '" + id + "' slot '" + key + "' must be an array");
            }
            if (array.length() == 0) {
                throw new JSONException("FX definition '" + id + "' slot '" + key + "' may not be empty");
            }
            List<FxLayerDef> layers = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                Object layer = array.get(i);
                if (!(layer instanceof JSONObject layerObject)) {
                    throw new JSONException("FX definition '" + id + "' slot '" + key
                            + "' layer " + i + " must be an object");
                }
                layers.add(FxLayerDef.parse(layerObject, id, slot, i));
            }
            slots.put(slot, layers);
        }
        if (slots.isEmpty()) throw new JSONException("FX definition '" + id + "' has no effect slots");
        return new WeaponFxDef(id, slots);
    }
}
