# Alpha player-facing localization

Status: IN PROGRESS — opening tutorials and company screen copy migrated
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

Remaining Alpha gate: migrate authored equipment/weapon/armor/mech catalog prose
and enum-derived labels in shared spec sheets, historical saved commendation text,
mission briefing/results and battle guidance, and the remaining story radio/UI.
Translation review and live opening/re-entry acceptance remain required. The
expanded-copy fixture checks layout and interaction, not a shipped translation.
