# Alpha player-facing localization

Status: IN PROGRESS — opening tutorials, company copy and shared equipment catalogs migrated
Written: 2026-10-12
Updated: 2026-10-13

Read `company-view-nouns.md`. `Strings` and the marineOps namespace in
strings.json are the translation authority; translation mods override keys.

- [ ] Inventory player-facing literals in campaign start, company rooms,
  dossiers, tutorials, briefing, results, resource bills and battle guidance.
- [ ] Move prose and labels out of Java and MLX into stable catalog keys;
  format whole translated sentences rather than concatenating English fragments.
- [x] Keep internal IDs, save values and authored mechanical identifiers stable.
- [x] Update headless fixtures to load the same catalog, with missing-key and
  placeholder checks and a translated/expanded-copy layout pass.
- [x] Validate apostrophes, plural/quantity phrasing and variable names/costs.
- [ ] No untranslated player-facing text in the selected Alpha campaign route.

New Castle campaign lessons must satisfy this now. Existing broader UI and
story narration require the remaining sweep before Alpha; do not present the
tutorial slice as full-project localization compliance.

Opening slice implemented:

- Cerberus prologue, yard conversation and conversion feedback; company founding
  intel; ship choice and preparation; Hermes commissioning; Mira induction;
  Barracks guidance, dossiers and company management controls; Armory, Boat Deck,
  Mech Lab, Engineering and restoration feedback now use the catalog.
- The management recording script keeps its stable line IDs and synthesis text;
  on-screen titles and dialogue resolve titleKey/textKey at display time.
- Whole variable messages, quantity forms and recovery clocks can be translated
  and reordered. Built-in rank, specialty and boat fitting labels resolve lazily.
  Player names, custom equipment names, saved roles and mechanical IDs are preserved.
- MLX has a read-only i18n scope shared by nested components. Headless tests and
  the snapshot CLI load the shipped catalog. `opening-localization` exercises
  production opening controls in English and expanded copy at 1920x1080 / 100%.

Shared catalog slice implemented:

- Weapon, armor, special-equipment, integral-system and mech catalog names and
  prose use translation references; grade, role, tradition, fitting socket and
  fabrication labels resolve at display time. Derived template/recipe names
  follow their equipment without caching a language at startup.
- Shared sheets translate stat labels, ability explanations, units and quantities.
  Stable stat identities preserve comparisons and icons even when translated
  labels coincide. Equipment ids, numerical behavior and literal addon copy remain intact.
- `equipment-localization` exercises first-refit choices, all twelve billet
  controls and shared weapon, suit-system, mech-component and chassis hover sheets
  in English and expanded copy at 1920x1080 / 100%.

Contract-board slice implemented:

- MissionSelectScreen owns catalog-backed empty states, broker roles and its own
  story status copy, dossier labels, commitment/payment summaries, quantity forms,
  tier/risk/presence labels and navigation markup. Names and saved identities stay
  unchanged. Variable messages are formatted as complete translated patterns.
- Domain-owned mission names, legacy story flavor, route status, broker introductions
  and branch consequences still require the broader story sweep; extracting the
  screen does not claim those domains are localized.

Remaining Alpha gate: built-in/saved squad-definition names and role copy,
historical saved commendation text,
mission briefing/results and battle guidance, and the remaining story radio/UI.
Expanded-copy layout still needs room for compact rarity/firepower badges,
long claim buttons and Mech Lab header/socket labels. Shared hover sheets wrap;
the surrounding compact controls need a separate layout pass.
Translation review and live opening/re-entry acceptance remain required. The
expanded-copy fixture checks layout and interaction, not a shipped translation.
