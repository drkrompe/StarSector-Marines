package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDressing;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;

import java.util.List;

/**
 * What a ruins site's interior wears for its vault prize ({@code ruins-expedition.md}, The vault and its prize): each
 * theme's props, so the deeper the company goes the more the rooms say what the vault holds. The vault room carries
 * the theme's heart; the chambers its working floor. Most themes borrow the game's existing furniture; a cold or a
 * growing prize has props of its own (the {@code ruins-themes} sheet) and patches on its floors.
 */
public final class RuinsThemeDressing {

    private static final String CRATES = "doodad.industrial-crate-stack";
    private static final String PALLETS = "doodad.industrial-pallet-stack";
    private static final String DRUMS = "doodad.industrial-drum-cluster";
    private static final String RIFLE_RACK = "doodad.armoury-rifle-rack";
    private static final String AMMO_LOCKER = "doodad.armoury-ammo-locker";
    private static final String READY_LOCKER = "doodad.armoury-ready-locker";
    private static final String ISSUE_COUNTER = "doodad.armoury-issue-counter";
    private static final String CLEANING_BENCH = "doodad.armoury-cleaning-bench";
    private static final String SERVER_RACK = "doodad.civic-server-rack";
    private static final String WORKSTATIONS = "doodad.civic-workstation-bank";
    private static final String SWITCHBOARD = "doodad.industrial-switchboard";
    private static final String CONSOLE = "doodad.industrial-control-console";
    private static final String MACHINE_TOOL = "doodad.industrial-machine-tool";
    private static final String PIPES = "doodad.industrial-pipe-bundle";
    private static final String CABLE_REEL = "doodad.industrial-cable-reel";
    private static final String GENERATOR = "doodad.industrial-generator";
    private static final String REACTOR = "doodad.industrial-reactor-housing";
    private static final String PUMPS = "doodad.industrial-pump-set";
    private static final String TANK = "doodad.industrial-fluid-tank";
    private static final String ENGINE_BOARD = "doodad.industrial-engine-control-board";
    private static final String CONFERENCE = "doodad.civic-conference-table";
    private static final String CRYO_RACK = "doodad.ruins-cryo-rack";
    private static final String FROST_HEATSINK = "doodad.ruins-frost-heatsink";
    private static final String CULTURE_VAT = "doodad.ruins-culture-vat";
    private static final String GROW_BED = "doodad.ruins-grow-bed";
    private static final String CRYO_UNIT = "doodad.ruins-cryo-unit";
    private static final String VAT_UNIT = "doodad.ruins-vat-unit";
    private static final String FORGE_CORE = "doodad.ruins-forge-core";
    private static final String DRONE_FRAME = "doodad.ruins-drone-frame";
    private static final String CORE_CABINET = "doodad.ruins-core-cabinet";
    private static final String FIELD_HOUSING = "doodad.ruins-field-housing";
    private static final String MAGNET_SEGMENT = "doodad.ruins-magnet-segment";
    private static final String CATALYST_BED = "doodad.ruins-catalyst-bed";
    private static final String SPOOL_DRUM = "doodad.ruins-spool-drum";
    private static final String BORE_HEAD = "doodad.ruins-bore-head";
    private static final String PROJECTOR_STACK = "doodad.ruins-projector-stack";
    private static final List<String> FROST = List.of("doodad.ruins-frost-patch-a", "doodad.ruins-frost-patch-b");
    private static final List<String> GROWTH = List.of("doodad.ruins-growth-patch-a", "doodad.ruins-growth-patch-b");

    private RuinsThemeDressing() {}

    /** The dressing for a prize; plain ground for none. */
    public static RuinsDressing of(RuinsVaultPrize prize) {
        return prize == null ? RuinsDressing.NONE : of(prize.theme());
    }

    /** The dressing for a theme: the vault room's props, then the chambers'. */
    public static RuinsDressing of(RuinsVaultTheme theme) {
        if (theme == null) return RuinsDressing.NONE;
        return switch (theme) {
            case DEPOT -> set(List.of(CRATES, PALLETS), List.of(CRATES, PALLETS, DRUMS));
            case ARMORY -> set(List.of(AMMO_LOCKER, READY_LOCKER, RIFLE_RACK),
                    List.of(RIFLE_RACK, ISSUE_COUNTER, CLEANING_BENCH, AMMO_LOCKER));
            case SERVER_HALL -> set(List.of(SERVER_RACK, SERVER_RACK, SWITCHBOARD),
                    List.of(SERVER_RACK, WORKSTATIONS, CONSOLE)).withProduct(CORE_CABINET);
            case PRISTINE_NANOFORGE, CORRUPTED_NANOFORGE ->
                    set(List.of(MACHINE_TOOL, ENGINE_BOARD), List.of(MACHINE_TOOL, CABLE_REEL, PALLETS, ENGINE_BOARD))
                            .withProduct(FORGE_CORE).withFoundry();
            case DRONE_REPLICATOR ->
                    set(List.of(MACHINE_TOOL, ENGINE_BOARD), List.of(MACHINE_TOOL, CABLE_REEL, PALLETS, ENGINE_BOARD))
                            .withProduct(DRONE_FRAME);
            case SYNCHROTRON -> set(List.of(SWITCHBOARD, GENERATOR), List.of(SWITCHBOARD, CABLE_REEL, CONSOLE))
                    .withProduct(MAGNET_SEGMENT);
            case FUSION_LAMP, PLASMA_DYNAMO ->
                    set(List.of(REACTOR, PUMPS), List.of(PIPES, PUMPS, TANK, GENERATOR)).withProduct(FIELD_HOUSING);
            case HYPERSHUNT_TAP -> set(List.of(REACTOR, PUMPS), List.of(PIPES, PUMPS, TANK, GENERATOR));
            case MANTLE_BORE -> set(List.of(ENGINE_BOARD, CABLE_REEL), List.of(CABLE_REEL, DRUMS, ENGINE_BOARD))
                    .withProduct(BORE_HEAD);
            case CATALYTIC_CORE -> set(List.of(TANK, PUMPS), List.of(TANK, PIPES, PUMPS)).withProduct(CATALYST_BED);
            case FULLERENE_SPOOL -> set(List.of(TANK, PUMPS), List.of(TANK, PIPES, PUMPS)).withProduct(SPOOL_DRUM);
            case SOIL_NANITES, BIOFACTORY_EMBRYO -> new RuinsDressing(List.of(CULTURE_VAT, GROW_BED),
                    List.of(GROW_BED, CULTURE_VAT, TANK, PIPES), GROWTH).withProduct(VAT_UNIT);
            case CRYOARITHMETIC_ENGINE -> new RuinsDressing(List.of(CRYO_RACK, CRYO_RACK, FROST_HEATSINK),
                    List.of(CRYO_RACK, FROST_HEATSINK, PIPES), FROST).withProduct(CRYO_UNIT);
            case DEALMAKER_HOLOSUITE -> set(List.of(CONFERENCE, WORKSTATIONS), List.of(CONFERENCE, WORKSTATIONS, CONSOLE))
                    .withProduct(PROJECTOR_STACK);
        };
    }

    private static RuinsDressing set(List<String> vault, List<String> chambers) {
        return new RuinsDressing(vault, chambers);
    }
}
