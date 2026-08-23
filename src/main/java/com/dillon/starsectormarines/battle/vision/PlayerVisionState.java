package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.EnumSet;
import java.util.Set;

/**
 * Set of factions whose units' line-of-sight contributes to the
 * player-visible fog-of-war reveal. Today it's just
 * {@link Faction#MARINE} — the only player faction. Ally-faction missions may
 * add their faction during setup, before their units spawn; the visibility pass
 * then unions sightings across every contributor. Runtime membership changes
 * require a separate lifecycle handoff so already-live units and footprints are
 * enrolled or removed together.
 *
 * <p>AI factions stay out of this — they run their own perception layer.
 * This is purely the rendering side of fog-of-war.
 */
public final class PlayerVisionState {

    private final EnumSet<Faction> contributors;

    public PlayerVisionState() {
        this.contributors = EnumSet.of(Faction.MARINE);
    }

    public Set<Faction> contributors() {
        return contributors;
    }

    public boolean isContributor(Faction faction) {
        return contributors.contains(faction);
    }

    public void addContributor(Faction faction) {
        contributors.add(faction);
    }

    public void removeContributor(Faction faction) {
        contributors.remove(faction);
    }
}
