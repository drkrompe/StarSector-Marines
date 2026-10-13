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
                .workbench-root, .fleet-armory-root, .fleet-armory-overview-root, .company-hq-root,
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
                .doctrine-card-active:disabled, .lance-order-card-active:disabled, .member-card:disabled { opacity: 1; }
                .member-card.member-empty:disabled { opacity: 0.28; }
                .good-surface { background-color: #132b22; border-color: #78d494; }
                .warning-surface { background-color: #332b1c; border-color: #ffd464; color: #ffd464; }
                .danger-surface { background-color: #3d2a26; border-color: #e98b83; }
                .edge-surface { background-color: #253b38; border-color: #a7ccc4; }
                .company-card-ready { border-color: #78d494; }
                .company-card-operational { border-color: #ffd464; }
                .company-card-unready { border-color: #e98b83; }
                /* Dossiers are steel enclosures around a dark readout, not giant keys. */
                .squad-card, .organization-target, .company-card {
                    background-color: #2e3736;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff12 0%, #ffffff00 12%, #00000026 100%);
                }
                .squad-card.organizing { border-color: #727b79; }
                .organization-target:hover, .company-card:hover { background-color: #46524d; }
                .organization-target.selected, .company-card.selected {
                    background-color: #494431;
                    border-style: inset;
                }
                .organization-target:active, .company-card:active {
                    background-color: #222c2a;
                    border-style: inset;
                }
                .squad-card.selected {
                    background-color: #3d3b2e;
                    border-color: #edc273;
                    border-style: inset;
                }
                .squad-open, .formation-select { background-color: transparent; background-image: none; }
                .squad-open:hover, .formation-select:hover { background-color: #ffffff08; }
                .formation.stationed .formation-select:disabled { opacity: 0.72; }
                .squad-command-block, .squad-card-status {
                    background-color: #121e1c;
                    border-color: #657771;
                    border-style: inset;
                }
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
                .console-action, .organization-start, .organization-cancel,
                .squad-reinforce, .slot-strip-action, .fabricate-action,
                #compare-armor-patterns, #squad-breadcrumb button, #fireteam-breadcrumb button,
                #comparison-breadcrumb button, #designer-breadcrumb button, #marine-ops-page-routes button,
                .console-toggle, .fire-team-tab, .equipment-picker-tab,
                .loadout-filter, .supply-filter, .catalog-filter-pill,
                .team-tab {
                    color: #dfe3d4; background-color: #3c453e; border-color: #919887;
                    border-style: outset;
                    background-image: linear-gradient(to bottom, #ffffff15 0%, #ffffff00 12%, #00000000 80%, #00000044 82%, #00000044 100%);
                }
                .console-action:hover, .organization-start:hover, .organization-cancel:hover,
                .squad-reinforce:hover, .slot-strip-action:hover, .fabricate-action:hover,
                #compare-armor-patterns:hover, #squad-breadcrumb button:hover, #fireteam-breadcrumb button:hover,
                #comparison-breadcrumb button:hover, #designer-breadcrumb button:hover, #marine-ops-page-routes button:hover,
                .console-toggle:hover, .fire-team-tab:hover, .equipment-picker-tab:hover,
                .loadout-filter:hover, .supply-filter:hover, .catalog-filter-pill:hover,
                .team-tab:hover { background-color: #525d4e; }
                .console-action:active, .organization-start:active, .organization-cancel:active,
                .squad-reinforce:active, .slot-strip-action:active, .fabricate-action:active,
                #compare-armor-patterns:active, #squad-breadcrumb button:active, #fireteam-breadcrumb button:active,
                #comparison-breadcrumb button:active, #designer-breadcrumb button:active, #marine-ops-page-routes button:active,
                .console-toggle:active, .fire-team-tab:active, .equipment-picker-tab:active,
                .loadout-filter:active, .supply-filter:active, .catalog-filter-pill:active,
                .team-tab:active { background-color: #222b24; border-style: inset; }
                .console-action:focus-visible, .organization-start:focus-visible, .organization-cancel:focus-visible,
                .squad-reinforce:focus-visible, .slot-strip-action:focus-visible, .fabricate-action:focus-visible,
                #compare-armor-patterns:focus-visible, #squad-breadcrumb button:focus-visible, #fireteam-breadcrumb button:focus-visible,
                #comparison-breadcrumb button:focus-visible, #designer-breadcrumb button:focus-visible, #marine-ops-page-routes button:focus-visible,
                .console-toggle:focus-visible, .fire-team-tab:focus-visible, .equipment-picker-tab:focus-visible,
                .loadout-filter:focus-visible, .supply-filter:focus-visible, .catalog-filter-pill:focus-visible,
                .team-tab:focus-visible { border-color: #ffe1a0; border-style: solid; }
                .console-toggle.selected, .fire-team-tab.selected, .equipment-picker-tab.selected,
                .loadout-filter.selected, .supply-filter.selected, .catalog-filter-pill.selected,
                .team-tab.selected {
                    background-color: #20291f; border-color: #a89b70; color: #e9d29b;
                    border-style: inset; background-image: none;
                }
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
                .workbench-root, .fleet-armory-root, .fleet-armory-overview-root, .company-hq-root,
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
                .squad-card.organizing { background-color: #071019; border-color: #d2f3ff; }
                .squad-card.selected { background-color: #225b75; border-color: #ffe45c; }
                button.power-armed { background-color: #3a3018; border-color: #ffe45c; color: #ffe45c; }
                button:hover { background-color: #15577b; }
                button:active { background-color: #2185b4; }
                button:focus-visible { border-color: #ffe45c; }
                button:disabled { opacity: 0.32; }
                .doctrine-card-active:disabled, .lance-order-card-active:disabled, .member-card:disabled { opacity: 1; }
                .member-card.member-empty:disabled { opacity: 0.28; }
                .good-surface { background-color: #06361b; border-color: #78ff9d; }
                .warning-surface { background-color: #3a3018; border-color: #ffe45c; color: #ffe45c; }
                .danger-surface { background-color: #501c16; border-color: #ff9d91; }
                .edge-surface { background-color: #06374b; border-color: #63e8ff; }
                .company-card-ready { border-color: #78ff9d; }
                .company-card-operational { border-color: #ffe45c; }
                .company-card-unready { border-color: #ff9d91; }
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

                .console-action, .organization-start, .organization-cancel,
                .squad-reinforce, .slot-strip-action, .fabricate-action,
                #compare-armor-patterns, #squad-breadcrumb button, #fireteam-breadcrumb button,
                #comparison-breadcrumb button, #designer-breadcrumb button, #marine-ops-page-routes button,
                .console-toggle, .fire-team-tab, .equipment-picker-tab,
                .loadout-filter, .supply-filter, .catalog-filter-pill,
                .team-tab { background-color: #102b42; border-color: #d2f3ff; color: #ffffff; }
                .console-action:hover, .organization-start:hover, .organization-cancel:hover,
                .squad-reinforce:hover, .slot-strip-action:hover, .fabricate-action:hover,
                #compare-armor-patterns:hover, #squad-breadcrumb button:hover, #fireteam-breadcrumb button:hover,
                #comparison-breadcrumb button:hover, #designer-breadcrumb button:hover, #marine-ops-page-routes button:hover,
                .console-toggle:hover, .fire-team-tab:hover, .equipment-picker-tab:hover,
                .loadout-filter:hover, .supply-filter:hover, .catalog-filter-pill:hover,
                .team-tab:hover { background-color: #15577b; }
                .console-toggle.selected, .fire-team-tab.selected, .equipment-picker-tab.selected,
                .loadout-filter.selected, .supply-filter.selected, .catalog-filter-pill.selected,
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
                        "title", Fonts.ORBITRON_16));
    }

    record CanvasPalette(Color button, Color selected, Color danger, Color valid,
                         Color edge, Color border, Color good, Color accent,
                         Color text, Color muted) {
    }
}
