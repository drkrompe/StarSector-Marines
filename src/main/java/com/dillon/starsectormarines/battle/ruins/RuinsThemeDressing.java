package com.dillon.starsectormarines.battle.ruins;

import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDressing;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultPrize;
import com.dillon.starsectormarines.campaign.ruins.RuinsVaultTheme;

import java.util.List;

/**
 * What a ruins site's interior wears for its vault prize ({@code ruins-expedition.md}, The vault and its prize): each
 * theme's props, drawn from the game's existing furniture, so the deeper the company goes the more the rooms say
 * what the vault holds. The vault room carries the theme's heart; the chambers its working floor.
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
    private static final String COLD_STORE = "doodad.galley-cold-store";
    private static final String CONFERENCE = "doodad.civic-conference-table";

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
                    List.of(SERVER_RACK, WORKSTATIONS, CONSOLE));
            case PRISTINE_NANOFORGE, CORRUPTED_NANOFORGE, DRONE_REPLICATOR ->
                    set(List.of(MACHINE_TOOL, ENGINE_BOARD), List.of(MACHINE_TOOL, CABLE_REEL, PALLETS, ENGINE_BOARD));
            case SYNCHROTRON -> set(List.of(SWITCHBOARD, GENERATOR), List.of(SWITCHBOARD, CABLE_REEL, CONSOLE));
            case FUSION_LAMP, PLASMA_DYNAMO, HYPERSHUNT_TAP ->
                    set(List.of(REACTOR, PUMPS), List.of(PIPES, PUMPS, TANK, GENERATOR));
            case MANTLE_BORE -> set(List.of(ENGINE_BOARD, CABLE_REEL), List.of(CABLE_REEL, DRUMS, ENGINE_BOARD));
            case CATALYTIC_CORE, FULLERENE_SPOOL -> set(List.of(TANK, PUMPS), List.of(TANK, PIPES, PUMPS));
            case SOIL_NANITES, BIOFACTORY_EMBRYO -> set(List.of(TANK, COLD_STORE), List.of(TANK, COLD_STORE, PIPES));
            case CRYOARITHMETIC_ENGINE -> set(List.of(SERVER_RACK, COLD_STORE, SWITCHBOARD),
                    List.of(COLD_STORE, PIPES, SERVER_RACK));
            case DEALMAKER_HOLOSUITE -> set(List.of(CONFERENCE, WORKSTATIONS), List.of(CONFERENCE, WORKSTATIONS, CONSOLE));
        };
    }

    private static RuinsDressing set(List<String> vault, List<String> chambers) {
        return new RuinsDressing(vault, chambers);
    }
}
