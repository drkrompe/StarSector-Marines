package com.dillon.starsectormarines.battle.ui.panel;

/**
 * A weapon's load drawn as a row of icons under its HUD row: rounds, missiles, belt blocks or charge
 * cells. Icons run from the first to be spent, and a group ending sets the next icon apart.
 */
public interface AmmoStrip {

    /** The widest strip a weapon row holds, in document pixels. */
    int MAX_WIDTH = 320;
    /** The space between two groups. */
    int GROUP_GAP = 5;

    int icons();

    /** The icon family, lower case, naming the drawing: {@code rifle}, {@code missile}, {@code cell}. */
    String iconKind();

    /** The icon's state, lower case, naming the drawing's variant: {@code live}, {@code spent}. */
    String iconState(int index);

    /** The drawing of one icon, as the battle HUD and every preview of it show it. */
    default String iconPath(int index) {
        return "graphics/ui/direct-control/round-" + iconKind() + "-" + iconState(index) + ".svg";
    }

    /** Whether a group ends after this icon. */
    boolean iconGroupEnd(int index);

    int iconWidth();

    /** The space between two icons of one group. */
    int iconGap();

    /** The strip's whole width as laid out. */
    default int width() {
        int count = icons();
        if (count == 0) return 0;
        int groups = 1;
        for (int i = 0; i < count - 1; i++) if (iconGroupEnd(i)) groups++;
        return count * iconWidth() + (count - groups) * iconGap() + (groups - 1) * GROUP_GAP;
    }

    default boolean fits() { return width() <= MAX_WIDTH; }
}
