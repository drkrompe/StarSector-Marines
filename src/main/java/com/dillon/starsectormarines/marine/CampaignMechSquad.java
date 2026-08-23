package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persistent player-facing mech squad; battle realizes it as one mech lance. */
public final class CampaignMechSquad implements Serializable {

    public static final int CAPACITY = 4;

    private final String id;
    private String displayName;
    private List<CampaignMech> mechs = new ArrayList<>();

    public CampaignMechSquad(String id, String displayName) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Squad id is required");
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Squad name is required");
        }
        this.id = id;
        this.displayName = displayName;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public List<CampaignMech> mechs() { return Collections.unmodifiableList(mechs); }

    public CampaignMech mechById(String mechId) {
        if (mechId == null) return null;
        for (CampaignMech mech : mechs) {
            if (mechId.equals(mech.id())) return mech;
        }
        return null;
    }

    public boolean add(CampaignMech mech) {
        if (mech == null || mechs.size() >= CAPACITY || mechById(mech.id()) != null) {
            return false;
        }
        mechs.add(mech);
        return true;
    }

    private Object readResolve() {
        if (mechs == null) mechs = new ArrayList<>();
        mechs.removeIf(mech -> mech == null);
        if (mechs.size() > CAPACITY) {
            mechs = new ArrayList<>(mechs.subList(0, CAPACITY));
        }
        return this;
    }
}
