package com.dillon.starsectormarines.render2d.texture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * What the build did to the images it fitted: for each one, the logical size the code addresses
 * it by (its source size, in which every cut, frame and layout is written) and the file that
 * actually ships, which differs when the build re-encoded it (an opaque image shipped as JPG).
 *
 * <p>Written by {@code tools/optimize_pngs.py} into the deployed and released mod as
 * {@link ModTextures#MANIFEST_PATH}; absent in a checkout, where every image is its own source.
 * An image that is not listed was shipped untouched.
 */
public final class TextureFitManifest {

    public static final TextureFitManifest EMPTY = new TextureFitManifest(Collections.emptyMap());

    /**
     * One fitted image: its logical size in source pixels, the file that ships for it, and that
     * file's own pixel size.
     */
    public record Entry(int width, int height, String file, int shippedWidth, int shippedHeight) { }

    private final Map<String, Entry> entries;

    private TextureFitManifest(Map<String, Entry> entries) {
        this.entries = entries;
    }

    /** Reads {@code {"textures": {path: {"w": int, "h": int, "file": path, "sw": int, "sh": int}}}}. */
    public static TextureFitManifest parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject textures = root.optJSONObject("textures");
        if (textures == null) return EMPTY;
        Map<String, Entry> entries = new HashMap<>();
        Iterator<?> keys = textures.keys();
        while (keys.hasNext()) {
            String path = (String) keys.next();
            JSONObject entry = textures.getJSONObject(path);
            int w = entry.getInt("w");
            int h = entry.getInt("h");
            if (w <= 0 || h <= 0) throw new JSONException("non-positive logical size for " + path);
            entries.put(path, new Entry(w, h, entry.optString("file", path),
                    entry.optInt("sw", w), entry.optInt("sh", h)));
        }
        return new TextureFitManifest(entries);
    }

    /** The fitted entry for {@code path}, or null when it shipped untouched. */
    public Entry entry(String path) {
        return path == null ? null : entries.get(path);
    }

    /** The file that ships for {@code path}: itself unless the build re-encoded it. */
    public String file(String path) {
        Entry entry = entry(path);
        return entry == null ? path : entry.file();
    }

    public int size() {
        return entries.size();
    }
}
