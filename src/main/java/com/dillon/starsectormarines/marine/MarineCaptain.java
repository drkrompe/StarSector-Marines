package com.dillon.starsectormarines.marine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One named captain on the player's marine roster. Lives across saves via Starsector's
 * xstream serialization (the enclosing {@link MarineRosterScript} is registered with the
 * sector, which pulls this in transitively).
 *
 * <p>All fields are xstream-friendly: strings, primitives, enums, ArrayList of the above.
 * No JOML or LWJGL types — they belong in render code, not persisted state.
 */
public class MarineCaptain implements Serializable {

    private final String id;
    private String name;
    private String portraitSprite;
    private Rank rank;
    private int xp;
    private Status status;
    /** When status == INJURED, the in-game day (Sector clock) the captain returns to ACTIVE. */
    private float injuredUntilDay;
    private final List<Trait> traits = new ArrayList<>();
    /** Free-form log of notable deeds — flavor text for the bridge view. */
    private final List<String> commendations = new ArrayList<>();
    private final float createdAtDay;

    public MarineCaptain(String name, String portraitSprite, Rank rank, float currentDay) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.portraitSprite = portraitSprite;
        this.rank = rank;
        this.xp = 0;
        this.status = Status.ACTIVE;
        this.injuredUntilDay = 0f;
        this.createdAtDay = currentDay;
    }

    public String id() { return id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public String portraitSprite() { return portraitSprite; }
    public void setPortraitSprite(String portraitSprite) { this.portraitSprite = portraitSprite; }

    public Rank rank() { return rank; }
    public void setRank(Rank rank) { this.rank = rank; }
    public int xp() { return xp; }
    public void addXp(int amount) { this.xp += amount; }

    public Status status() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public float injuredUntilDay() { return injuredUntilDay; }
    public void setInjuredUntilDay(float day) { this.injuredUntilDay = day; }

    public List<Trait> traits() { return traits; }
    public List<String> commendations() { return commendations; }
    public float createdAtDay() { return createdAtDay; }
}
