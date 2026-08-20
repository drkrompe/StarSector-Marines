package com.dillon.starsectormarines.battle.ui.highlight;

import com.dillon.starsectormarines.DebugOnly;
import com.dillon.starsectormarines.battle.command.influence.CommanderInfluenceSnapshot;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Converts immutable commander fields into coarse debug rectangles. */
@DebugOnly
public final class CommanderInfluenceOverlayPublisher {

    private static final Color MARINE_FRIENDLY = new Color(0x40, 0xC8, 0xFF);
    private static final Color MARINE_HOSTILE = new Color(0xFF, 0x48, 0x70);
    private static final Color DEFENDER_FRIENDLY = new Color(0xFF, 0xB0, 0x40);
    private static final Color DEFENDER_HOSTILE = new Color(0xB0, 0x58, 0xFF);

    private CommanderInfluenceOverlayPublisher() {}

    public static void publish(BattleSimulation sim, HighlightOverlay overlay,
                               boolean marineFriendly, boolean marineHostile,
                               boolean defenderFriendly, boolean defenderHostile) {
        publishChannel(sim, overlay, Faction.MARINE, true, marineFriendly,
                HighlightOverlay.SRC_MARINE_FRIENDLY_INFLUENCE, MARINE_FRIENDLY);
        publishChannel(sim, overlay, Faction.MARINE, false, marineHostile,
                HighlightOverlay.SRC_MARINE_HOSTILE_INFLUENCE, MARINE_HOSTILE);
        publishChannel(sim, overlay, Faction.DEFENDER, true, defenderFriendly,
                HighlightOverlay.SRC_DEFENDER_FRIENDLY_INFLUENCE, DEFENDER_FRIENDLY);
        publishChannel(sim, overlay, Faction.DEFENDER, false, defenderHostile,
                HighlightOverlay.SRC_DEFENDER_HOSTILE_INFLUENCE, DEFENDER_HOSTILE);
    }

    private static void publishChannel(BattleSimulation sim, HighlightOverlay overlay,
                                       Faction faction, boolean friendly, boolean enabled,
                                       String sourceId, Color base) {
        if (!enabled || sim == null) {
            overlay.clear(sourceId);
            return;
        }
        CommanderInfluenceSnapshot snapshot = sim.getCommanderInfluence(faction);
        if (snapshot == null) {
            overlay.clear(sourceId);
            return;
        }
        float maximum = friendly ? snapshot.maxFriendly() : snapshot.maxHostile();
        if (maximum <= 0f) {
            overlay.clear(sourceId);
            return;
        }
        List<CellHighlight> cells = new ArrayList<>();
        for (int blockY = 0; blockY < snapshot.height(); blockY++) {
            for (int blockX = 0; blockX < snapshot.width(); blockX++) {
                float value = friendly
                        ? snapshot.friendlyAt(blockX, blockY)
                        : snapshot.hostileAt(blockX, blockY);
                if (value <= 0f) continue;
                float normalized = Math.min(1f, value / maximum);
                int alpha = Math.round(32f + normalized * 176f);
                Color color = new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
                cells.add(new CellHighlight(snapshot.blockWorldX(blockX),
                        snapshot.blockWorldY(blockY), snapshot.blockWorldWidth(blockX),
                        snapshot.blockWorldHeight(blockY), color));
            }
        }
        overlay.put(sourceId, cells);
    }
}
