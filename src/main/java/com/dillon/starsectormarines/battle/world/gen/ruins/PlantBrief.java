package com.dillon.starsectormarines.battle.world.gen.ruins;

import java.util.ArrayList;
import java.util.List;

/**
 * The works plant as its architect briefed it, before it was a ruin ({@code ruins-expedition.md}, The plant is
 * designed intact first): the spaces a sealed works needed beside its works hall, each sized for the crew and
 * machines that worked in it, where it stood relative to the work, and whether a hall frame could reach it.
 *
 * <p>A crew of about 120 in three shifts of 40 ran the plant. Per shift the control gallery held six to eight
 * operators and a shift lead, maintenance eight to ten, the QA lab three or four, logistics five or six, and the
 * habitat housed and fed them all. The sizes here are an extensive site's; a widespread site's plant is about seven
 * tenths as large, a vast one's a little larger.
 *
 * <p>Frame-wide spaces (the stores, the dock, maintenance, line two) are joined by freight ways and published as
 * plant halls a hall frame reaches; the rest (control, the lab, the habitat, plant services) only by service
 * corridors, where infantry can shake off a frame.
 */
final class PlantBrief {

    private PlantBrief() {}

    /** Where a space wants to stand: by the work it served, or by the way it was entered. */
    enum Anchor {
        /** Down the works hall's east wall, overlooking the floor. */
        HALL_EAST,
        /** Beyond the hall's head end, where the frames came off the line for rework. */
        HALL_HEAD,
        /** Beyond the hall's dispatch end, east of the feed. */
        HALL_DISPATCH,
        /** Beyond the hall's dispatch end, beside the spine where the feed rail starts. */
        HALL_FEED,
        /** Inside the first way in: the freight dock. */
        ENTRANCE,
        /** Inside a second way in, away from the line's noise: the personnel lock. */
        QUIET_END,
        /** Beside the plant's power hall. */
        POWER,
        /** On the vault's row, nearest the vault. */
        VAULT_ROW,
        /** The middle of its band. */
        MIDDLE,
        /** Beside the space laid before it. */
        BESIDE_LAST
    }

    /**
     * One space of the brief.
     *
     * @param name    the space's name, for the plan and its places
     * @param stage   the plant stage it is published as when frame-wide, else null
     * @param role    the room it is laid as when it is not a plant hall, else null
     * @param width   its width in cells
     * @param height  its height in cells
     * @param anchor  where it stands
     * @param freight whether a freight way (a hall frame's way) joins it, else a service corridor
     */
    record Space(String name, RuinsDelve.PlantStage stage, RuinsRoom role, int width, int height, Anchor anchor,
                 boolean freight) {}

    /**
     * The spaces interior band {@code band} of {@code bands} holds, the works hall's own band first. A site with one
     * interior band holds everything but line two in it. {@code layers} is the site's layer count: two is
     * widespread, four vast.
     */
    static List<Space> spaces(int band, int bands, int layers) {
        float s = layers <= 2 ? .7f : layers >= 4 ? 1.15f : 1f;
        List<Space> out = new ArrayList<>();
        if (band == 0) {
            out.add(space("dock", RuinsDelve.PlantStage.INTAKE, null, 20, 14, s, Anchor.ENTRANCE, true));
            out.add(space("raw-stock", RuinsDelve.PlantStage.STORAGE, null, 28, 20, s, Anchor.HALL_FEED, true));
            out.add(space("control", null, RuinsRoom.CONTROL, 8, 24, s, Anchor.HALL_EAST, false));
            out.add(space("lab", null, RuinsRoom.LAB, 18, 12, s, Anchor.HALL_DISPATCH, false));
            out.add(space("maintenance", RuinsDelve.PlantStage.MAINTENANCE, null, 24, 18, s, Anchor.HALL_HEAD, true));
            out.add(space("consumables", null, RuinsRoom.STORE, 16, 12, s, Anchor.BESIDE_LAST, false));
            out.add(space("habitat", null, RuinsRoom.CREW, 30, 22, s, Anchor.QUIET_END, false));
            out.add(space("services", null, null, 18, 12, s, Anchor.POWER, false));
        }
        if (band > 0 || bands == 1) {
            if (bands > 1) {
                out.add(space("line-two", RuinsDelve.PlantStage.PRODUCTION, null, 30, 18, s, Anchor.MIDDLE, true));
                out.add(space("line-two-bay", null, RuinsRoom.WORKSHOP, 16, 12, s, Anchor.BESIDE_LAST, false));
            }
            out.add(space("finished-goods", RuinsDelve.PlantStage.STORAGE, null, 24, 18, s, Anchor.VAULT_ROW, true));
        }
        return out;
    }

    private static Space space(String name, RuinsDelve.PlantStage stage, RuinsRoom role, int width, int height,
                               float scale, Anchor anchor, boolean freight) {
        return new Space(name, stage, role, Math.max(6, Math.round(width * scale)),
                Math.max(6, Math.round(height * scale)), anchor, freight);
    }
}
