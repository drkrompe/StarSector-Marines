package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** A persistent six-marine campaign fireteam. Tactical battle squads remain ephemeral. */
public final class MarineSquad implements Serializable {

    public static final int CAPACITY = 6;

    private String id;
    private String name;
    private List<String> memberIds = new ArrayList<>();
    private boolean reserve;

    public MarineSquad(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public MarineSquad(String id, String name) {
        this(id, name, false);
    }

    public MarineSquad(String id, String name, boolean reserve) {
        this.id = id;
        this.name = name;
        this.reserve = reserve;
    }

    public String id() { return id; }
    public String name() { return name; }
    public boolean reserve() { return reserve; }
    public List<String> memberIds() { return Collections.unmodifiableList(memberIds); }

    boolean add(String soldierId) {
        if (soldierId == null || memberIds.contains(soldierId)) return false;
        memberIds.add(soldierId);
        return true;
    }

    boolean remove(String soldierId) { return memberIds.remove(soldierId); }

    void setName(String value) {
        if (value != null && !value.trim().isEmpty()) name = value.trim();
    }

    private Object readResolve() {
        if (id == null) id = UUID.randomUUID().toString();
        if (name == null) name = "Fireteam";
        if (memberIds == null) memberIds = new ArrayList<>();
        return this;
    }
}
