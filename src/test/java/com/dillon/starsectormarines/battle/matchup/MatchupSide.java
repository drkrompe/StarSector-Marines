package com.dillon.starsectormarines.battle.matchup;

import com.dillon.starsectormarines.battle.infantry.MarineLoadout;
import com.dillon.starsectormarines.marine.LoadoutCardIssue;
import com.dillon.starsectormarines.marine.SquadEquipmentDoctrines;

import java.util.Objects;

/**
 * One side of a matchup: a weapon loadout and a tactic sheet, by definition id ({@code weapons:...},
 * {@code armor:...}) — the two cards a player pairs. Twelve marines, one regular each, so the comparison is of
 * equipment and nothing else.
 */
public record MatchupSide(String weaponsId, String sheetId) {

    /** The issue a fresh company fields, which is never a card: the control a card is measured against. */
    public static final MatchupSide STARTER = new MatchupSide(
            SquadEquipmentDoctrines.FIELD_SECURITY_WEAPONS, SquadEquipmentDoctrines.FIELD_FATIGUES_ARMOR);

    public MatchupSide {
        Objects.requireNonNull(weaponsId, "weaponsId");
        Objects.requireNonNull(sheetId, "sheetId");
    }

    /** {@code weapons:x+armor:y}, as it is written on the command line and in reports. */
    public static MatchupSide parse(String text) {
        String[] parts = text.split("\\+");
        if (parts.length != 2) throw new IllegalArgumentException("Expected weapons:<id>+armor:<id>, got " + text);
        return new MatchupSide(parts[0].trim(), parts[1].trim());
    }

    public String label() {
        return weaponsId + "+" + sheetId;
    }

    public MarineLoadout[] seats() {
        return LoadoutCardIssue.seats(weaponsId, sheetId);
    }
}
