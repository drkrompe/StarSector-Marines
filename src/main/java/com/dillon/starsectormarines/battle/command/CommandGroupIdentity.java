package com.dillon.starsectormarines.battle.command;

import java.util.Objects;
import com.dillon.starsectormarines.marine.OfficerCommandProfile;

/** Immutable command membership, independent of campaign handles. */
public record CommandGroupIdentity(String groupId, String officerId, String label,
                                   boolean namedOfficer, OfficerCommandProfile profile) {
    public CommandGroupIdentity(String groupId, String officerId, String label, boolean namedOfficer) {
        this(groupId, officerId, label, namedOfficer, null);
    }
    public CommandGroupIdentity {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(label, "label");
        if (groupId.isBlank() || label.isBlank() || namedOfficer != (officerId != null)) {
            throw new IllegalArgumentException("Invalid command group identity");
        }
    }

    public static CommandGroupIdentity actingNco(String squadId, String label) {
        return new CommandGroupIdentity("nco:" + squadId, null, label + " / acting NCO", false);
    }
}
