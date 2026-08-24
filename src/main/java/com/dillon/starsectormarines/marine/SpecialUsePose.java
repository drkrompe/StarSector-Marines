package com.dillon.starsectormarines.marine;

import org.json.JSONException;

/** Presentation profile used while a marine activates special equipment. */
public enum SpecialUsePose {
    SHOULDER_LAUNCHER("shoulder-launcher"),
    BRACED_RIFLE("braced-rifle"),
    THROW("throw"),
    PLANT("plant");

    public final String key;

    SpecialUsePose(String key) {
        this.key = key;
    }

    public static SpecialUsePose fromKey(String key, String equipmentId)
            throws JSONException {
        for (SpecialUsePose pose : values()) {
            if (pose.key.equalsIgnoreCase(key)) return pose;
        }
        throw new JSONException("Special equipment '" + equipmentId
                + "' has unknown use pose '" + key + "'");
    }
}
