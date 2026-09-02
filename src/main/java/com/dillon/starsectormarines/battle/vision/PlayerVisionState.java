package com.dillon.starsectormarines.battle.vision;

import com.dillon.starsectormarines.battle.unit.Faction;

import java.util.EnumSet;
import java.util.Set;

/**
 * Set of factions whose units' line-of-sight contributes to the
 * player-visible fog-of-war reveal. The default is
 * {@link Faction#MARINE} and {@link Faction#ALLY}: the player's own company and
 * whoever is fighting beside it, whose reports the player is entitled to. The
 * visibility pass unions sightings across every contributor and shows a
 * contributor's own bodies unconditionally.
 *
 * <p><b>Allies are in by default rather than by mission setup.</b> Fog-of-war
 * law 2 makes contributor membership an explicit decision, and this is where
 * that decision is taken. There are a dozen paths that stand a battle up, an
 * allied force can arrive down any of them, and a path that forgot to enrol its
 * ally would produce no error at all — only a militia the player cannot see
 * fighting on their side. Deciding it once, here, is the only version of that
 * decision that cannot be half-applied. {@link #addContributor} and
 * {@link #removeContributor} remain for a mission that wants otherwise; runtime
 * membership changes require a separate lifecycle handoff so already-live units
 * and footprints are enrolled or removed together.
 *
 * <p>AI factions stay out of this — they run their own perception layer.
 * This is purely the rendering side of fog-of-war.
 */
public final class PlayerVisionState {

    private final EnumSet<Faction> contributors;

    public PlayerVisionState() {
        this.contributors = EnumSet.of(Faction.MARINE, Faction.ALLY);
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
