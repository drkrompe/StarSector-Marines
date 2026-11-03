package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ui.Fonts;
import com.dillon.starsectormarines.ui.retained.style.StyleSheet;
import com.dillon.starsectormarines.ui.retained.style.UiTheme;

import java.awt.Color;
import java.util.Map;

/** Reusable final theme layers for Marine Ops retained documents. */
final class MarineOpsThemes {

    static final String SHEET_NAME = "marine-ops-theme";

    private MarineOpsThemes() {
    }

    static UiTheme standard() {
        return theme("""
                :root {
                    font-family: body;
                    color: #c6d8e7;
                    background-color: #03090d;
                }
                .heading { font-family: heading; }
                .title { font-family: title; }
                /* The base game's screen: near-black with a faint blue grid behind everything,
                   and panels that are dark boxes on it with a thin blue line round them. A header
                   stands forward in a brighter line with cut corners; nothing has relief. */
                .panel {
                    background-color: #050f14a6;
                    border-color: #335060;
                    border-style: solid;
                    background-image: none;
                }
                .workbench-root, .fleet-armory-root, .company-hq-root,
                .mission-select-root, .mission-briefing-root, .squad-deployment-root,
                .stationing-root, .mission-results-root, .mission-loot-root,
                .mech-lab-root, .boat-deck-root, .store-root, .transfer-root,
                .ship-view-root, .barracks-root {
                    background-color: #03090d;
                    border-color: #335060;
                    border-style: solid;
                    grid-lines: 32px #46c8ff1c;
                }
                /* Spoken lines over a battle hold a firm backing, whatever ground is under them. */
                #company-subtitle, #story-subtitle { background-color: #050f14f0; }
                .surface-dark {
                    background-color: #02070af2;
                    background-image: none;
                    border-color: #335060;
                    border-style: solid;
                }
                .tone-edge { color: #aadeff; }
                .heading.tone-edge, .title.tone-edge { color: #cbf5ff; }
                .tone-muted { color: #9caab6; }
                .tone-accent { color: #ffd700; }
                .tone-good { color: #9bff00; }
                .tone-danger { color: #ff6400; }
                /* The base game's action button: a flat teal plate with no border, cut at two
                   opposite corners, with its colours from the game's settings (buttonBgDark over
                   the dark screen, buttonText). A plate keeps its border's width, transparent, so
                   no layout moves, and a state is a change of fill, not a change of relief. */
                button {
                    color: #aadeff;
                    background-color: #17424f;
                    border-color: transparent;
                    border-style: solid;
                    background-image: none;
                    corner-cut: 0px 7px 0px 7px;
                    transition: background-color 140ms ease-out, border-color 140ms ease-out,
                                opacity 100ms linear;
                }
                button.selected { background-color: #43798b; color: #dff4ff; }
                button.power-armed { background-color: #6a4a12; color: #ffd200; }
                button:hover { background-color: #24637a; }
                button:active { background-color: #0e2d37; }
                button:focus-visible { border-color: #aadeff; }
                button:disabled { background-color: #364c52; color: #84909a; opacity: 1; }
                .doctrine-card-active:disabled, .member-card:disabled { opacity: 1; }
                .member-card.member-empty:disabled { opacity: 0.28; }
                .good-surface { background-color: #132b22; border-color: #78d494; }
                .warning-surface { background-color: #332b1c; border-color: #ffd464; color: #ffd464; }
                .danger-surface { background-color: #3d2a26; border-color: #e98b83; }
                .edge-surface { background-color: #253b38; border-color: #77c4f6; }
                .formation-select { background-color: transparent; background-image: none; }
                .spec-card { background-color: #09131d; background-image: none; border-style: solid; border-color: #53c6e8; color: #c7d8e8; }
                .spec-card.spec-sheet-armor { border-color: #dfb84d; }
                .spec-card:hover { background-color: #0e1a26; }
                .spec-card.selected { background-color: #16242f; border-color: #f2cf68; border-style: solid; }
                /* In a list of cards, every card but the chosen one rests with its kind's edge dimmed. */
                .spec-card.spec-sheet-weapon.resting { border-color: #2a5566; }
                .spec-card.spec-sheet-armor.resting { border-color: #6b5a2a; }
                /* A card's issue control is its check box alone, with no button drawn around it. */
                .doctrine-issue-button, .doctrine-issue-button:hover, .doctrine-issue-button:active,
                .doctrine-issue-button:disabled { background-color: transparent; background-image: none; border-color: transparent; }
                .formation-select:hover { background-color: #ffffff08; }
                .formation.stationed .formation-select:disabled { opacity: 0.72; }
                #marine-ops-page-nav { background-color: transparent; background-image: none; }
                #marine-ops-page-context { color: #aadeff; background-color: transparent; }
                #marine-ops-page-routes .page-nav-current { background-color: #43798b; color: #dff4ff; }
                #marine-ops-page-routes .page-nav-current:hover { background-color: #4e8a9e; }
                .console-header {
                    background-color: #071720e6;
                    border-color: #82d6ff;
                    border-style: solid;
                    background-image: none;
                    corner-cut: 0px 10px 0px 10px;
                }
                .console-header-lock { width: 44px; height: 44px; pointer-events: none; }
                .console-header-vent { width: 100px; height: 44px; pointer-events: none; }
                .console-section, .mission-briefing-root .section-heading-row {
                    background-color: #050f14d9;
                    border-color: #335060;
                    border-style: solid;
                    background-image: none;
                }
                .console-readout {
                    background-color: #02070af2;
                    border-color: #2c6a7e;
                    border-style: solid;
                    background-image: none;
                }
                .console-well, .mission-detail-image-wrap, .commitment-section {
                    background-color: #02070af2;
                    border-color: #335060;
                    border-style: solid;
                    background-image: none;
                }
                .mission-glyph { background-color: #02070a; border-color: #2c6a7e; border-style: solid; }
                .debug-drawer-toggle { color: #edc273; border-color: #b99b63; }
                .debug-control-value { color: #bbcbda; }
                .mission-card.selected, .client-card.selected, .tier-step.selected {
                    background-color: #1f4552; border-color: #aadeff; border-style: solid; background-image: none;
                }
                /* Debug launcher cards: flat readout plates with a sunk badge, lit gold when chosen. */
                .debug-card {
                    background-color: #0c1b22; border-color: #375a71; border-style: solid;
                    background-image: none;
                }
                .debug-card:hover { background-color: #10252f; border-color: #5d98bf; }
                .debug-card:active { background-color: #081218; border-style: solid; }
                .debug-card.selected { background-color: #332e1e; border-color: #edc273; border-style: solid; color: #ffe1a0; }
                .debug-card-badge { background-color: #0f1714; border-color: #406984; border-style: solid; }
                .debug-card.selected .debug-card-badge { background-color: #1d180b; border-color: #b99b63; }
                .console-command-rail { background-color: transparent; border-color: transparent; background-image: none; }
                .console-command-signal, .console-toggle-lamp, .console-tier-lamp {
                    background-color: #0a171d; border-color: #45728f; border-style: solid;
                    background-image: none;
                }
                .console-command-rail .good-surface .console-command-signal { background-color: #3a83a7; }
                .console-command-rail .briefing-blocked .console-command-signal { background-color: #e98b83; }
                .console-command-rail .good-surface { color: #c2d3e3; }
                .console-command-rail .briefing-blocked { color: #e98b83; }
                .mission-briefing-root .choice-row, .mission-briefing-root .tier-step, .debug-air-row button {
                    background-color: #112732; border-color: #4d7e9e; border-style: solid;
                    background-image: none;
                }
                .mission-briefing-root .choice-row.selected, .mission-briefing-root .tier-step.selected,
                .debug-air-row button.selected {
                    background-color: #0c1b22; border-color: #5f9dc5; border-style: solid;
                    background-image: none; color: #becedd;
                }
                .debug-drawer-toggle.expanded { background-color: #0c1b22; background-image: none; border-style: solid; }
                .mission-briefing-root .choice-row:hover, .mission-briefing-root .tier-step:hover,
                .debug-air-row button:hover { background-color: #173442; }
                .mission-briefing-root .choice-row:active, .mission-briefing-root .tier-step:active,
                .debug-air-row button:active { background-color: #0a161c; border-style: solid; }
                .mission-briefing-root .choice-row:focus-visible, .mission-briefing-root .tier-step:focus-visible,
                .debug-air-row button:focus-visible { border-color: #ffe1a0; border-style: solid; }
                .tier-step.selected .console-tier-lamp {
                    background-color: #edc273; border-color: #edc273;
                }

                .mission-briefing-root .spec-sheet {
                    background-color: #081318; border-color: #b9a16b; color: #cee1f1;
                }
                .mission-briefing-root .spec-sheet-title { color: #edc273; }
                .mission-briefing-root .spec-sheet-subtitle, .mission-briefing-root .spec-sheet-stat-label { color: #98a5b1; }

                /* The base game's two other button shapes. A tab is cut at both top corners and
                   lights when chosen, as the market's Sell and Buy do; a filter is a hollow outline
                   that fills gold when chosen, as the intel screen's are. Any other command key is
                   the plain action button above. */
                .console-toggle, .fire-team-tab, .equipment-picker-tab, .team-tab {
                    corner-cut: 7px 7px 0px 0px;
                }
                .supply-filter, .catalog-filter-pill {
                    corner-cut: 0px; background-color: #00000080; color: #e8f4f8; border-color: #2c6a7e;
                }
                .supply-filter:hover, .catalog-filter-pill:hover { background-color: #17424f; }
                .supply-filter:active, .catalog-filter-pill:active { background-color: #0e2d37; }
                .supply-filter.selected, .catalog-filter-pill.selected {
                    background-color: #6e5400; border-color: #c9a227; color: #ffd200;
                }
                button.good-surface { background-color: #1e5a3a; color: #a3ef80; border-color: transparent; }
                button.good-surface:hover { background-color: #2a7a4e; }
                button.warning-surface { background-color: #5a4610; color: #ffd200; border-color: transparent; }
                button.warning-surface:hover { background-color: #755c15; }
                button.danger-surface { background-color: #4d2a26; color: #f0a1a1; border-color: transparent; }
                button.danger-surface:hover { background-color: #663732; }
                /* Cards are buttons to the pointer, not to the eye: they keep their own square frame. */
                .spec-card, .doctrine-card-active, .member-card, .formation-select, .mission-card,
                .client-card, .debug-card, .debug-kit-card, .stationing-card, .deck-station, .deck-marker,
                .specialty-node, .kit-specialty-node, .systems-row, .systems-core-row, .choice-row, .tier-step,
                .doctrine-issue-button, .slot-select-button, .command-card,
                .deployment-squad-card, .marine-viewer-card, .loot-card, .transfer-row,
                .captain-card, .stationing-squad-card, .wire-report, .wire-line { corner-cut: 0px; }
                .mission-card, .client-card, .command-card, .deployment-squad-card, .marine-viewer-card,
                .loot-card, .transfer-row, .captain-card, .stationing-squad-card, .wire-report, .wire-line {
                    background-color: #131d21; border-color: #2c6a7e;
                }
                .mission-card:hover, .client-card:hover, .command-card:hover, .deployment-squad-card:hover,
                .marine-viewer-card:hover, .loot-card:hover, .transfer-row:hover, .captain-card:hover,
                .stationing-squad-card:hover, .wire-report:hover, .wire-line:hover {
                    background-color: #1a2e36; border-color: #2c6a7e;
                }
                .command-card.selected, .deployment-squad-card.selected, .marine-viewer-card.selected,
                .loot-card.selected, .transfer-row.selected, .captain-card.selected,
                .stationing-squad-card.selected, .wire-report.selected {
                    background-color: #1f4552; border-color: #aadeff;
                }
                /* A command key's label sits in the middle of the key; set here so no screen has to. */
                .console-action { text-align: center; font-family: body; }
                .console-action.danger-surface { color: #f0a1a1; background-color: #4d2a26; border-color: transparent; }
                .console-action.good-surface { color: #dff4ff; background-color: #1f6b7f; border-color: transparent; }
                .console-toggle-lamp { width: 6px; height: 6px; border-width: 1px; pointer-events: none; }
                .console-toggle.selected .console-toggle-lamp,
                .page-nav-current .console-toggle-lamp { background-color: #edc273; border-color: #edc273; }
                .spec-sheet { background-color: #081318; border-color: #b9a16b; color: #cee1f1; }
                .spec-sheet-title { color: #edc273; }
                .spec-sheet-subtitle, .spec-sheet-stat-label { color: #98a5b1; }
                .console-key-legend {
                    width: 0px; flex-grow: 1; height: auto; padding: 0px;
                    font-family: body; text-align: center; overflow: hidden; pointer-events: none;
                }
                /* The console keys' lamps and grips: a base-game button is its plate and its words. */
                .console-key-grip, .console-command-signal, button .console-toggle-lamp { display: none; }
                /* Licence keys: the tabs and actions that sit on and around the spec-sheet cards,
                   in the cards' own navy and edge colours rather than the console's. After the
                   console block so a surface that also carries a console class takes these. */
                .spec-tab, .spec-action {
                    background-image: none; border-style: solid; border-width: 1px;
                    font-family: heading; text-align: center;
                }
                .spec-tab { background-color: #09131d; border-color: #1f3346; color: #8995a0; }
                .spec-tab:hover { background-color: #0e1a26; border-color: #2a4258; color: #c7d8e8; }
                .spec-tab:active { background-color: #060d14; border-style: solid; }
                .spec-tab.selected { background-color: #16242f; border-color: #53c6e8; color: #eaf3ff; border-style: solid; }
                /* A choice the player commits to (First Refit's A / B) is picked in gold, as its card is. */
                .spec-tab.spec-choice.selected { background-color: #1a1608; border-color: #f2cf68; color: #f2cf68; }
                .spec-action { background-color: #1a1608; border-color: #dfb84d; color: #f2cf68; }
                .spec-action:hover { background-color: #2a2410; border-color: #f2cf68; }
                .spec-action:active { background-color: #100d04; border-style: solid; }
                .spec-action.issued { background-color: #0d1a12; border-color: #4f9c6b; color: #a9b8c5; }
                .spec-tab:focus-visible, .spec-action:focus-visible { border-color: #ffe1a0; border-style: solid; }
                /* Battle glass: the battle HUD draws on the field itself, with no panel behind it, so
                   the fight shows through, and it speaks in the base game's combat neon: bright for
                   what matters, orange for danger, amber for caution. A document's root opts in with
                   hud-glass. Text keeps a shadow so it reads on any ground; a control is a hairline on
                   a faint smoke that lights when hovered and fills when chosen. Last in the sheet, so
                   it wins over the console button rules above. */
                .hud-glass { color: #9bff00; text-shadow: #000000d9; background-color: transparent; background-image: none; }
                .hud-glass .tone-edge { color: #9bff00; }
                .hud-glass .heading.tone-edge, .hud-glass .title.tone-edge { color: #9bff00; }
                .hud-glass .tone-muted { color: #68b210; }
                .hud-glass .tone-accent { color: #cdff6e; }
                .hud-glass .tone-good { color: #9bff00; }
                .hud-glass .tone-danger { color: #ff6400; }
                .hud-glass button {
                    corner-cut: 0px;
                    color: #9bff00; background-color: #0a0e1040; background-image: none;
                    border-style: solid; border-color: #68b21033;
                }
                .hud-glass button:hover { background-color: #cdff6e17; border-color: #68b210aa; }
                .hud-glass button:active { background-color: #00000066; border-style: solid; }
                .hud-glass button:focus-visible { border-color: #ffc800; }
                .hud-glass .console-action { font-family: heading; }
                .hud-glass button:disabled { background-color: #0a0e1040; color: #9bff00; opacity: 0.38; }
                .hud-glass .member-card:disabled { opacity: 1; }
                .hud-glass .member-card.member-empty:disabled { opacity: 0.28; }
                .hud-glass button.good-surface { background-color: #132b22; border-color: #9bff00; color: #cdff6e; }
                .hud-glass button.warning-surface { background-color: #332b1c; border-color: #cdff6e; }
                .hud-glass button.selected, .hud-glass button.power-armed,
                .hud-glass .doctrine-card-active, .hud-glass .pick-selected, .hud-glass .ship-selected {
                    background-color: #cdff6e24; border-color: #cdff6e99; color: #cdff6e; border-style: solid;
                }
                .hud-glass .attachment-state-attached { background-color: #9bff0017; border-color: #68b21099; }
                .hud-glass .doctrine-card-deployed { border-color: #9bff0066; }
                .hud-glass .member-leader { border-color: #cdff6e80; }
                .hud-glass .member-critical { border-color: #ff640099; }
                .hud-glass .member-top { border-color: #cdff6e99; }
                .hud-glass .member-empty { background-color: transparent; }
                .hud-glass .pick-refused, .hud-glass .power-disabled { background-color: transparent; border-color: #68b2101a; }
                .hud-glass .danger-surface { background-color: #ff640024; border-color: #ff640099; color: #ff6400; }
                .hud-glass .danger-surface:hover { background-color: #ff64003d; border-color: #ff6400; }

                """);
    }

    static UiTheme highContrast() {
        return theme("""
                :root {
                    font-family: body;
                    color: #ffffff;
                    background-color: #000000;
                }
                .heading { font-family: heading; }
                .title { font-family: title; }
                .panel { background-color: #071019; border-color: #d2f3ff; }
                .workbench-root, .fleet-armory-root, .company-hq-root,
                .mission-select-root, .mission-briefing-root, .squad-deployment-root,
                .stationing-root, .mission-results-root, .mission-loot-root { border-color: #63e8ff; }
                .surface-dark { background-color: #000000; }
                .tone-edge { color: #63e8ff; }
                .tone-muted { color: #c4d1df; }
                .tone-accent { color: #ffe45c; }
                .tone-good { color: #78ff9d; }
                .tone-danger { color: #ff9d91; }
                button {
                    color: #ffffff;
                    background-color: #102b42;
                    border-color: #d2f3ff;
                    transition: background-color 140ms ease-out, border-color 140ms ease-out,
                                opacity 100ms linear;
                }
                button.selected { background-color: #225b75; border-color: #63e8ff; }
                button.power-armed { background-color: #3a3018; border-color: #ffe45c; color: #ffe45c; }
                button:hover { background-color: #15577b; }
                button:active { background-color: #2185b4; }
                button:focus-visible { border-color: #ffe45c; }
                button:disabled { opacity: 0.32; }
                .doctrine-card-active:disabled, .member-card:disabled { opacity: 1; }
                .member-card.member-empty:disabled { opacity: 0.28; }
                .good-surface { background-color: #06361b; border-color: #78ff9d; }
                .warning-surface { background-color: #3a3018; border-color: #ffe45c; color: #ffe45c; }
                .danger-surface { background-color: #501c16; border-color: #ff9d91; }
                .edge-surface { background-color: #06374b; border-color: #63e8ff; }
                .console-header, .console-section, .mission-briefing-root .section-heading-row {
                    background-color: #071019; border-color: #d2f3ff;
                }
                .console-header-lock { width: 44px; height: 44px; pointer-events: none; }
                .console-header-vent { width: 100px; height: 44px; pointer-events: none; }
                .console-readout, .console-well, .mission-detail-image-wrap, .commitment-section {
                    background-color: #000000; border-color: #d2f3ff;
                }
                .mission-glyph { background-color: #071019; border-color: #d2f3ff; }
                .debug-drawer-toggle, .debug-control-value { color: #ffe45c; }
                .console-command-rail { background-color: #000000; border-color: #d2f3ff; }
                .console-command-rail button { background-color: #102b42; border-color: #d2f3ff; }
                .console-command-rail button:hover { background-color: #15577b; }
                .console-command-rail button:active { background-color: #2185b4; }
                .console-command-rail button:focus-visible { border-color: #ffe45c; }
                .console-command-signal, .console-toggle-lamp, .console-tier-lamp {
                    background-color: #000000; border-color: #d2f3ff;
                }
                .console-command-rail .good-surface .console-command-signal { background-color: #78ff9d; }
                .console-command-rail .briefing-blocked .console-command-signal { background-color: #ff9d91; }
                .tier-step.selected .console-tier-lamp {
                    background-color: #ffe45c; border-color: #ffe45c;
                }

                .console-action, .slot-strip-action, .fabricate-action,
                #marine-ops-page-routes button,
                .console-toggle, .fire-team-tab, .equipment-picker-tab,
                .supply-filter, .catalog-filter-pill,
                .team-tab { background-color: #102b42; border-color: #d2f3ff; color: #ffffff; }
                .console-action { text-align: center; }
                .console-action:hover, .slot-strip-action:hover, .fabricate-action:hover,
                #marine-ops-page-routes button:hover,
                .console-toggle:hover, .fire-team-tab:hover, .equipment-picker-tab:hover,
                .supply-filter:hover, .catalog-filter-pill:hover,
                .team-tab:hover { background-color: #15577b; }
                .console-toggle.selected, .fire-team-tab.selected, .equipment-picker-tab.selected,
                .supply-filter.selected, .catalog-filter-pill.selected,
                .team-tab.selected { background-color: #225b75; border-color: #ffe45c; }
                .console-action.danger-surface { color: #ff9d91; border-color: #ff9d91; }
                .console-action.good-surface { color: #78ff9d; border-color: #78ff9d; }
                .console-action.warning-surface { color: #ffe45c; border-color: #ffe45c; }
                .console-toggle-lamp { width: 6px; height: 6px; border-width: 1px; pointer-events: none; }
                .page-nav-current .console-toggle-lamp { background-color: #ffe45c; }
                .console-key-legend {
                    width: 0px; flex-grow: 1; height: auto; padding: 0px;
                    font-family: heading; text-align: center; overflow: hidden; pointer-events: none;
                }
                .console-key-grip { width: 14px; height: 24px; pointer-events: none; }
                .console-command-signal { width: 12px; height: 8px; border-width: 1px; pointer-events: none; }
                /* Licence keys: the tabs and actions that sit on and around the spec-sheet cards,
                   in the cards' own navy and edge colours rather than the console's. After the
                   console block so a surface that also carries a console class takes these. */
                .spec-tab, .spec-action {
                    background-image: none; border-style: solid; border-width: 1px;
                    font-family: heading; text-align: center;
                }
                .spec-tab { background-color: #09131d; border-color: #1f3346; color: #8b9aaf; }
                .spec-tab:hover { background-color: #0e1a26; border-color: #2a4258; color: #d5deea; }
                .spec-tab:active { background-color: #060d14; border-style: solid; }
                .spec-tab.selected { background-color: #16242f; border-color: #53c6e8; color: #eaf3ff; border-style: solid; }
                /* A choice the player commits to (First Refit's A / B) is picked in gold, as its card is. */
                .spec-tab.spec-choice.selected { background-color: #1a1608; border-color: #f2cf68; color: #f2cf68; }
                .spec-action { background-color: #1a1608; border-color: #dfb84d; color: #f2cf68; }
                .spec-action:hover { background-color: #2a2410; border-color: #f2cf68; }
                .spec-action:active { background-color: #100d04; border-style: solid; }
                .spec-action.issued { background-color: #0d1a12; border-color: #4f9c6b; color: #8fd6a6; }
                .spec-tab:focus-visible, .spec-action:focus-visible { border-color: #ffe1a0; border-style: solid; }

                """);
    }

    static CanvasPalette canvasPalette(boolean highContrast) {
        if (highContrast) {
            return new CanvasPalette(new Color(0x10, 0x2B, 0x42),
                    new Color(0x22, 0x5B, 0x75), new Color(0x58, 0x1E, 0x18),
                    new Color(0x06, 0x36, 0x1B), new Color(0x63, 0xE8, 0xFF),
                    new Color(0xD2, 0xF3, 0xFF), new Color(0x78, 0xFF, 0x9D),
                    new Color(0xFF, 0xE4, 0x5C), Color.WHITE,
                    new Color(0xC4, 0xD1, 0xDF));
        }
        return new CanvasPalette(new Color(0x17, 0x42, 0x4F),
                new Color(0x43, 0x79, 0x8B), new Color(0x4D, 0x2A, 0x26),
                new Color(0x1E, 0x5A, 0x3A), new Color(0xAA, 0xDE, 0xFF),
                new Color(0x33, 0x50, 0x60), new Color(0x9B, 0xFF, 0x00),
                new Color(0xFF, 0xD7, 0x00), new Color(0xDC, 0xDC, 0xDC),
                new Color(0x9F, 0xB2, 0xBA));
    }

    private static UiTheme theme(String css) {
        return new UiTheme(StyleSheet.parse(SHEET_NAME, css),
                Map.of("body", Fonts.INSIGNIA_15_AA,
                        "prose", Fonts.INSIGNIA_LARGE,
                        "heading", Fonts.ORBITRON_12_BOLD,
                        "compact", Fonts.ORBITRON_10,
                        "title", Fonts.ORBITRON_16,
                        "display", Fonts.ORBITRON_24_BOLD));
    }

    record CanvasPalette(Color button, Color selected, Color danger, Color valid,
                         Color edge, Color border, Color good, Color accent,
                         Color text, Color muted) {
    }
}
