package com.dillon.starsectormarines.marine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * The shoulder pod's authored delivery and the numbers that decide when it is spent: a
 * {@link SpecialActivation#SHOULDER_POD} item's activation block.
 *
 * <p>The salvo shape (how many missiles launch together, their spread, range, damage and
 * penetration) belongs entirely to the item's referenced weapon. Restating any of it here would
 * create a second place those numbers could disagree, which {@code moddable-weapons-nouns.md}'s
 * "one weapon behavior has one authoritative authored value" law forbids. Salvo stock is the
 * item's own {@code startingAmmo}, the same as any other carried special.
 *
 * <p><b>When.</b> {@link SpecialAiPolicy#SIGHTED_STANDOFF_CONTACT} arrives when the carrier holds a
 * clear, reachable line on a hostile no closer than {@link #minimumStandoffCells}. That inner
 * bound judges whether the target warrants a salvo: a payload delivered into something already at
 * arm's length wastes a use the rifle would have covered anyway, and lands its splash on the
 * carrier's own side of the fight. The outer bound is the weapon's own range.
 * {@link #squadSalvoIntervalSeconds} spaces successful salvos from cooperating carriers in one
 * squad; it does not replace any carrier's own ammunition or cooldown.
 *
 * <p><b>Where.</b> {@link #mount} is the physical shoulder hardpoint in cells. Absent, the salvo
 * leaves from the carrier's centre; the carrier artwork never moves the source.
 */
public record MissilePodSpec(MissilePodMountSpec mount, float minimumStandoffCells,
                             float squadSalvoIntervalSeconds) implements Serializable {

    public MissilePodSpec {
        if (!Float.isFinite(minimumStandoffCells) || minimumStandoffCells <= 0f
                || !Float.isFinite(squadSalvoIntervalSeconds) || squadSalvoIntervalSeconds <= 0f) {
            throw new IllegalArgumentException("A pod's standoff and squad spacing must be finite and positive");
        }
    }

    static MissilePodSpec parse(JSONObject activation, String equipmentId) throws JSONException {
        if (!activation.has("minimumStandoffCells")) {
            throw new JSONException("Shoulder pod '" + equipmentId + "' must author"
                    + " activation.minimumStandoffCells: how close is too close to spend a delivered"
                    + " payload is a judgement about the item, not a constant.");
        }
        if (!activation.has("squadSalvoIntervalSeconds")) {
            throw new JSONException("Shoulder pod '" + equipmentId + "' must author"
                    + " activation.squadSalvoIntervalSeconds: the spacing between squad salvos.");
        }
        float standoff = (float) activation.getDouble("minimumStandoffCells");
        float spacing = (float) activation.getDouble("squadSalvoIntervalSeconds");
        if (!Float.isFinite(standoff) || standoff <= 0f || !Float.isFinite(spacing) || spacing <= 0f) {
            throw new JSONException("Shoulder pod '" + equipmentId
                    + "' minimumStandoffCells and squadSalvoIntervalSeconds must be finite and positive");
        }
        MissilePodMountSpec mount = null;
        JSONObject mountJson = activation.has("mount") ? activation.getJSONObject("mount") : null;
        if (mountJson != null) {
            try {
                mount = new MissilePodMountSpec((float) mountJson.getDouble("rightCells"),
                        (float) mountJson.getDouble("forwardCells"),
                        (float) mountJson.getDouble("muzzleForwardCells"));
            } catch (IllegalArgumentException invalid) {
                throw new JSONException("Shoulder pod '" + equipmentId + "' has invalid mount: "
                        + invalid.getMessage());
            }
        }
        return new MissilePodSpec(mount, standoff, spacing);
    }
}
