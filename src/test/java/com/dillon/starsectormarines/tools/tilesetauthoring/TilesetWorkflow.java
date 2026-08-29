package com.dillon.starsectormarines.tools.tilesetauthoring;

/**
 * What somebody opened the Tilesets page to do.
 *
 * <p>The page's commands are all real and all needed, and presented together
 * they are seventeen controls in the order they happened to be written. Nothing
 * in that says which come first, which belong to each other, or which apply to
 * the sheet currently open — so the page reads as a palette for someone who
 * already knows the procedure, and as nothing at all for someone who does not.
 *
 * <p>These are the three ways in. They are not modes of a document and they do
 * not change what the tools can do: the same editor, the same commands, ordered
 * and narrowed to the job at hand. Picking one is how the page learns which
 * order to put its steps in.
 */
public enum TilesetWorkflow {

    /**
     * Start from a need rather than from a file: a wall is wanted, or a floor.
     * The sheet it comes from is the answer, not the question.
     */
    SURFACE("What can be a wall?",
            "Start from what the game needs — a wall, a floor, a road — and see "
                    + "every block that could fill it, whichever sheet it is on.",
            "Surfaces"),

    /**
     * Start from art: a plate arrived, or one already annotated needs changing.
     * Cut it, say what its pieces are, group its blocks, export it.
     */
    SHEET("New art arrived",
            "Start from a sheet: cut it, say what its pieces are, group its "
                    + "blocks, and export the tileset the game loads.",
            "Sheets"),

    /**
     * Look without changing anything. Kept separate because the difference
     * that matters is that nothing here writes.
     */
    LOOK("Just look at it",
            "Open a sheet and look: the tileset as the game loads it, and a "
                    + "generated map drawn with it. Nothing here writes.",
            "Sheets");

    private final String title;
    private final String blurb;
    private final String tab;

    TilesetWorkflow(String title, String blurb, String tab) {
        this.title = title;
        this.blurb = blurb;
        this.tab = tab;
    }

    /** How the choice reads on the chooser, in the operator's words. */
    public String title() {
        return title;
    }

    /** One sentence saying what this way in is for. */
    public String blurb() {
        return blurb;
    }

    /** Which of the two ways into the workspace this opens on. */
    public String tab() {
        return tab;
    }

    /** Whether this workflow may write anything at all. */
    public boolean writes() {
        return this != LOOK;
    }
}
