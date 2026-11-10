package com.dillon.starsectormarines.battle.world.gen.ruins;

/**
 * What a themed plant's rooms hold of its process ({@code ruins-expedition.md}, Every room serves the process): the
 * board its control room watched the line on, the instrument its lab tested samples with, the unit its workshop was
 * reworking, the stock its stores held, and what spilled when it died. Each piece names its own art and a stand-in
 * from the sets that have shipped, as a works hall's pieces do.
 *
 * @param board      the control room's mimic board of the line, 3x1 against the hall-side wall
 * @param instrument the lab's test instrument, 2x2
 * @param spare      the unit on the workshop's rework stand, 3x3: the hall's product or machine, half stripped
 * @param stock      the stores' stock, 2x1 on a shelf
 * @param spill      the surface that spread when the plant died (frost, growth, powder), or null for none
 */
record RoomTheme(Piece board, Piece instrument, Piece spare, Piece stock, String spill) {

    /** One themed piece: its art and the stand-in laid until it ships. */
    record Piece(String id, String standIn) {}

    private static final String SWITCHBOARD = "doodad.industrial-switchboard";
    private static final String WORKSTATION = "doodad.civic-workstation-bank";
    private static final String DRUMS = "doodad.industrial-drum-cluster";
    private static final String CRATES = "doodad.industrial-crate-stack";
    private static final String PALLETS = "doodad.industrial-pallet-stack";
    private static final String REEL = "doodad.industrial-cable-reel";

    /** The theme a plant's rooms wear, by the works hall it made its product in. */
    static RoomTheme of(WorksHall works) {
        return switch (works == null ? WorksHall.NONE : works) {
            case COLD -> theme("cold-loop", "qubit-dewar", "doodad.medical-monitor-stack",
                    "doodad.ruins-cryo-unit-half", "cryogen-dewars", DRUMS, "ruins-ground.rime");
            case CULTURE -> theme("culture", "incubator", "doodad.ruins-decanter", "doodad.ruins-vat-unit-half",
                    "nutrient-drums", DRUMS, "ruins-ground.biofilm");
            case PRINT -> theme("print-queue", "doodad.ruins-metrology-bed", null, "doodad.ruins-drone-frame-half",
                    "feedstock-canisters", DRUMS, "floors.sand");
            case CORE -> theme("lattice", "core-probe", WORKSTATION, "doodad.ruins-core-cabinet-half",
                    "rack-module-crates", CRATES, "floors.sand");
            case FIELD -> theme("field", "coil-test-rig", "doodad.ruins-test-pylon", "doodad.ruins-field-housing-half",
                    "coolant-drums", DRUMS, "volcanic.ash");
            case RING -> theme("beam", "magnet-bench", "doodad.ruins-metrology-bed", "doodad.ruins-magnet-segment-half",
                    "cable-drums", REEL, "ruins-ground.rime");
            case SHAFT -> theme("bore", "core-sample-rack", "colony-furnishings.shelf", "doodad.ruins-bore-head-half",
                    "drill-rods", PALLETS, "floors.dirt");
            case CRACKER -> theme("reactor", "assay-bench", WORKSTATION, "doodad.ruins-catalyst-bed-half",
                    "reagent-drums", DRUMS, "ruins-ground.wet-floor");
            case DRAW -> theme("draw", "doodad.ruins-tensile-bed", null, "doodad.ruins-spool-drum-half",
                    "spool-cores", REEL, null);
            case STAGE -> theme("director", "doodad.ruins-emitter-bench", null, "doodad.ruins-projector-stack-half",
                    "emitter-cases", CRATES, "floors.sand");
            case HIGH_BAY -> theme("dispatch", "tally-bench", WORKSTATION, "sorter-module", "doodad.ruins-reject-skid",
                    "tote-stacks", PALLETS, "floors.sand");
            case FILLING -> theme("range", "charge-test-cell", "doodad.ruins-charge-hopper",
                    "doodad.ruins-filling-press", "charge-cases", CRATES, "floors.sand");
            case FORGE -> theme("pour", "assay-furnace", "doodad.ruins-mould-master", "doodad.ruins-forge-core-half",
                    "ingot-stacks", PALLETS, "volcanic.ash");
            case NONE -> new RoomTheme(new Piece("doodad.ruins-mimic-board", SWITCHBOARD),
                    new Piece(WORKSTATION, null), new Piece("doodad.industrial-machine-tool", null),
                    new Piece(CRATES, null), null);
        };
    }

    /** A theme whose spare is its own art already shipped. */
    private static RoomTheme theme(String board, String instrument, String instrumentStandIn, String spare,
                                   String stock, String stockStandIn, String spill) {
        return theme(board, instrument, instrumentStandIn, spare, null, stock, stockStandIn, spill);
    }

    private static RoomTheme theme(String board, String instrument, String instrumentStandIn, String spare,
                                   String spareStandIn, String stock, String stockStandIn, String spill) {
        return new RoomTheme(new Piece("doodad.ruins-" + board + "-board", SWITCHBOARD),
                new Piece(id(instrument), instrumentStandIn), new Piece(id(spare), spareStandIn),
                new Piece(id(stock), stockStandIn), spill);
    }

    private static String id(String name) { return name.startsWith("doodad.") ? name : "doodad.ruins-" + name; }
}
