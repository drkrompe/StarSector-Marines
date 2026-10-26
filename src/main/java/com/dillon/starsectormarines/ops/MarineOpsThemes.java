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
                    color: #e4e7df;
                    background-color: #111516;
                }
                .heading { font-family: heading; }
                .title { font-family: title; }
                .panel {
                    background-color: #292f30;
                    border-color: #727b79;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff0c 0%, #ffffff00 18%, #00000024 100%);
                }
                .workbench-root, .fleet-armory-root, .company-hq-root,
                .mission-select-root, .mission-briefing-root, .squad-deployment-root,
                .stationing-root, .mission-results-root, .mission-loot-root,
                .mech-lab-root, .boat-deck-root {
                    border-color: #727b79;
                    border-style: outset;
                }
                .surface-dark {
                    background-color: #101919;
                    background-image: none;
                    border-style: inset;
                }
                .tone-edge { color: #a7ccc4; }
                .heading.tone-edge, .title.tone-edge { color: #d3d0c5; }
                .tone-muted { color: #a0ada8; }
                .tone-accent { color: #edc273; }
                .tone-good { color: #78d494; }
                .tone-danger { color: #e98b83; }
                button {
                    color: #e4e7df;
                    background-color: #3c4444;
                    border-color: #8d9690;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff1b 0%, #ffffff06 46%, #00000010 48%, #00000030 100%);
                    transition: background-color 140ms ease-out, border-color 140ms ease-out,
                                opacity 100ms linear;
                }
                button.selected { background-color: #494431; border-color: #edc273; border-style: inset; }
                button.power-armed { background-color: #4a3c20; border-color: #edc273; color: #edc273; border-style: inset; }
                button:hover { background-color: #505b58; }
                button:active { background-color: #222c2a; border-style: inset; }
                button:focus-visible { border-color: #ffe1a0; border-style: solid; }
                button:disabled { opacity: 0.38; }
                .doctrine-card-active:disabled, .member-card:disabled { opacity: 1; }
                .member-card.member-empty:disabled { opacity: 0.28; }
                .good-surface { background-color: #132b22; border-color: #78d494; }
                .warning-surface { background-color: #332b1c; border-color: #ffd464; color: #ffd464; }
                .danger-surface { background-color: #3d2a26; border-color: #e98b83; }
                .edge-surface { background-color: #253b38; border-color: #a7ccc4; }
                .formation-select { background-color: transparent; background-image: none; }
                .spec-card { background-color: #09131d; background-image: none; border-style: solid; border-color: #53c6e8; color: #d5deea; }
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
                #marine-ops-page-nav {
                    background-color: #343634;
                    border-color: #82857c;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff10 0%, #ffffff00 18%, #00000026 100%);
                }
                #marine-ops-page-context {
                    background-color: #111b18;
                    color: #b8c9ad;
                    border-color: #727d73;
                    border-style: inset;
                }
                #marine-ops-page-routes .page-nav-current {
                    background-color: #393323;
                    border-color: #edc273;
                    color: #ffda8b;
                    border-style: inset;
                    background-image: linear-gradient(to bottom, #00000050 0%, #00000000 100%);
                }
                #marine-ops-page-routes .page-nav-current:hover { background-color: #50452e; }
                .console-header {
                    background-color: #343634;
                    border-color: #82857c;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff10 0%, #ffffff00 18%, #00000026 100%);
                }
                .console-header-lock { width: 44px; height: 44px; pointer-events: none; }
                .console-header-vent { width: 100px; height: 44px; pointer-events: none; }
                .console-section, .mission-briefing-root .section-heading-row {
                    background-color: #303430;
                    border-color: #6f736c;
                    border-style: outset;
                }
                .console-readout {
                    background-color: #202b27;
                    border-color: #56675c;
                    border-style: inset;
                    background-image: none;
                }
                .console-well, .mission-detail-image-wrap, .commitment-section {
                    background-color: #101a16;
                    border-color: #69786b;
                    border-style: inset;
                    background-image: none;
                }
                .mission-glyph { background-color: #121d18; border-color: #65766a; border-style: inset; }
                .debug-drawer-toggle { color: #edc273; border-color: #b99b63; }
                .debug-control-value { color: #d6d5a3; }
                .mission-card.selected, .client-card.selected, .tier-step.selected {
                    background-color: #494431;
                    border-color: #edc273;
                    border-style: inset;
                    color: #ffe1a0;
                }
                .mission-card:hover, .client-card:hover { background-color: #505b58; }
                .mission-card:active, .client-card:active { background-color: #222c2a; }
                .console-command-rail {
                    background-color: #101713; border-color: #697368; border-style: inset;
                    background-image: none;
                }
                .console-command-rail button, .debug-drawer-toggle, .mission-briefing-root .debug-control-row button {
                    color: #dfe3d4; background-color: #3c453e; border-color: #919887;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff15 0%, #ffffff00 12%, #00000000 80%, #00000044 82%, #00000044 100%);
                }
                .console-command-rail button:hover, .debug-drawer-toggle:hover, .mission-briefing-root .debug-control-row button:hover { background-color: #525d4e; }
                .console-command-rail button:active, .debug-drawer-toggle:active, .mission-briefing-root .debug-control-row button:active {
                    background-color: #222b24; border-style: inset;
                }
                .console-command-rail button:focus-visible, .debug-drawer-toggle:focus-visible, .mission-briefing-root .debug-control-row button:focus-visible {
                    border-color: #ffe1a0; border-style: solid;
                }
                .console-command-signal, .console-toggle-lamp, .console-tier-lamp {
                    background-color: #1a231a; border-color: #6b7562; border-style: inset;
                    background-image: none;
                }
                .console-command-rail .good-surface .console-command-signal { background-color: #a6c788; }
                .console-command-rail .briefing-blocked .console-command-signal { background-color: #e98b83; }
                .console-command-rail .good-surface { color: #e8d9a7; }
                .console-command-rail .briefing-blocked { color: #e98b83; }
                .mission-briefing-root .choice-row, .mission-briefing-root .tier-step, .debug-air-row button {
                    background-color: #303a32; border-color: #76816e; border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff0c 0%, #ffffff00 12%, #00000020 100%);
                }
                .mission-briefing-root .choice-row.selected, .mission-briefing-root .tier-step.selected,
                .debug-air-row button.selected {
                    background-color: #20291f; border-color: #a89b70; border-style: inset;
                    background-image: none; color: #e9d29b;
                }
                .debug-drawer-toggle.expanded { background-color: #20291f; background-image: none; border-style: inset; }
                .mission-briefing-root .choice-row:hover, .mission-briefing-root .tier-step:hover,
                .debug-air-row button:hover { background-color: #424d3c; }
                .mission-briefing-root .choice-row:active, .mission-briefing-root .tier-step:active,
                .debug-air-row button:active { background-color: #182219; border-style: inset; }
                .mission-briefing-root .choice-row:focus-visible, .mission-briefing-root .tier-step:focus-visible,
                .debug-air-row button:focus-visible { border-color: #ffe1a0; border-style: solid; }
                .tier-step.selected .console-tier-lamp {
                    background-color: #edc273; border-color: #edc273;
                }

                .mission-briefing-root .spec-sheet {
                    background-color: #141e19; border-color: #b9a16b; color: #e4e7df;
                }
                .mission-briefing-root .spec-sheet-title { color: #edc273; }
                .mission-briefing-root .spec-sheet-subtitle, .mission-briefing-root .spec-sheet-stat-label { color: #a0ada8; }

                /* Shared command keys and latching selectors; dimensions belong to each surface. */
                .console-action, .slot-strip-action, .fabricate-action,
                #marine-ops-page-routes button,
                .console-toggle, .fire-team-tab, .equipment-picker-tab,
                .supply-filter, .catalog-filter-pill,
                .team-tab {
                    color: #dfe3d4; background-color: #3c453e; border-color: #919887;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff15 0%, #ffffff00 12%, #00000000 80%, #00000044 82%, #00000044 100%);
                }
                .console-action:hover, .slot-strip-action:hover, .fabricate-action:hover,
                #marine-ops-page-routes button:hover,
                .console-toggle:hover, .fire-team-tab:hover, .equipment-picker-tab:hover,
                .supply-filter:hover, .catalog-filter-pill:hover,
                .team-tab:hover { background-color: #525d4e; }
                .console-action:active, .slot-strip-action:active, .fabricate-action:active,
                #marine-ops-page-routes button:active,
                .console-toggle:active, .fire-team-tab:active, .equipment-picker-tab:active,
                .supply-filter:active, .catalog-filter-pill:active,
                .team-tab:active { background-color: #222b24; border-style: inset; }
                .console-action:focus-visible, .slot-strip-action:focus-visible, .fabricate-action:focus-visible,
                #marine-ops-page-routes button:focus-visible,
                .console-toggle:focus-visible, .fire-team-tab:focus-visible, .equipment-picker-tab:focus-visible,
                .supply-filter:focus-visible, .catalog-filter-pill:focus-visible,
                .team-tab:focus-visible { border-color: #ffe1a0; border-style: solid; }
                .console-toggle.selected, .fire-team-tab.selected, .equipment-picker-tab.selected,
                .supply-filter.selected, .catalog-filter-pill.selected,
                .team-tab.selected {
                    background-color: #20291f; border-color: #a89b70; color: #e9d29b;
                    border-style: inset; background-image: none;
                }
                /* A command key's label sits in the middle of the key; set here so no screen has to. */
                .console-action { text-align: center; }
                .console-action.danger-surface { color: #e98b83; border-color: #a4756c; }
                .console-action.good-surface { color: #e8d9a7; }
                .console-toggle-lamp { width: 6px; height: 6px; border-width: 1px; pointer-events: none; }
                .console-toggle.selected .console-toggle-lamp,
                .page-nav-current .console-toggle-lamp { background-color: #edc273; border-color: #edc273; }
                .spec-sheet { background-color: #141e19; border-color: #b9a16b; color: #e4e7df; }
                .spec-sheet-title { color: #edc273; }
                .spec-sheet-subtitle, .spec-sheet-stat-label { color: #a0ada8; }
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
                /* Battle glass: the battle HUD draws on the field itself, with no panel behind it, so
                   the fight shows through. A document's root opts in with hud-glass. Text keeps a
                   shadow so it reads on any ground; a control is a hairline on a faint smoke that
                   lights when hovered and fills gold when chosen, as a direct-control weapon row
                   does. Last in the sheet, so it wins over the console button rules above. */
                .hud-glass { text-shadow: #000000d9; background-color: transparent; background-image: none; }
                .hud-glass button {
                    color: #e4e9e4; background-color: #0a0e1040; background-image: none;
                    border-style: solid; border-color: #c3cec933;
                }
                .hud-glass button:hover { background-color: #ffffff17; border-color: #c3cec9aa; }
                .hud-glass button:active { background-color: #00000066; border-style: solid; }
                .hud-glass button:focus-visible { border-color: #ffe1a0; }
                .hud-glass button.selected, .hud-glass button.power-armed,
                .hud-glass .doctrine-card-active, .hud-glass .pick-selected, .hud-glass .ship-selected {
                    background-color: #d6c27a24; border-color: #d6c27a99; color: #ffd464; border-style: solid;
                }
                .hud-glass .attachment-state-attached { background-color: #8ee3ff17; border-color: #a7ccc499; }
                .hud-glass .doctrine-card-deployed { border-color: #78d49466; }
                .hud-glass .member-leader { border-color: #d6c27a80; }
                .hud-glass .member-critical { border-color: #e98b8399; }
                .hud-glass .member-top { border-color: #dfc58899; }
                .hud-glass .member-empty { background-color: transparent; }
                .hud-glass .pick-refused, .hud-glass .power-disabled { background-color: transparent; border-color: #c3cec91a; }
                .hud-glass .danger-surface { background-color: #e98b8324; border-color: #e98b8399; color: #f0a1a1; }
                .hud-glass .danger-surface:hover { background-color: #e98b833d; border-color: #ef8f8f; }

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
        return new CanvasPalette(new Color(0x3C, 0x44, 0x44),
                new Color(0x49, 0x44, 0x31), new Color(0x3D, 0x2A, 0x26),
                new Color(0x13, 0x2B, 0x22), new Color(0xA7, 0xCC, 0xC4),
                new Color(0x72, 0x7B, 0x79), new Color(0x78, 0xD4, 0x94),
                new Color(0xED, 0xC2, 0x73), new Color(0xE4, 0xE7, 0xDF),
                new Color(0xA0, 0xAD, 0xA8));
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
