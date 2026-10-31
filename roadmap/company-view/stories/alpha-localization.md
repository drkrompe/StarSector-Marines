# Alpha player-facing localization

Status: IMPLEMENTED — catalog sweep complete on the Alpha route; translation review and live acceptance open
Written: 2026-10-12
Updated: 2026-10-31 — Simplified Chinese translation mod started (opening dialogue); overlay parity guard

Read `company-view-nouns.md`. `Strings` and the marineOps namespace in
strings.json are the translation authority; translation mods override keys.

- [x] Inventory player-facing literals in campaign start, company rooms,
  dossiers, tutorials, briefing, results, resource bills and battle guidance.
- [x] Move prose and labels out of Java and MLX into stable catalog keys;
  format whole translated sentences rather than concatenating English fragments.
- [x] Keep internal IDs, save values and authored mechanical identifiers stable.
- [x] Update headless fixtures to load the same catalog, with missing-key and
  placeholder checks and a translated/expanded-copy layout pass.
- [x] Validate apostrophes, plural/quantity phrasing and variable names/costs.
- [ ] No untranslated player-facing text in the selected Alpha campaign route
  (needs the live pass below; the catalog side is done).

## Standing rules

- Java reads copy through `Strings.get/format/join/named/resolve`; MLX through
  `{i18n.key}`; data files through `@key` display fields. Variable messages are
  one MessageFormat pattern each, with `choice` for quantities and doubled
  apostrophes in any pattern that takes arguments.
- Built-in text that a save holds stays an English literal in the save and is
  translated when shown, for as long as it still equals the built-in default
  (default squad names, the reserve pool, first-refit plan names, built-in
  billet roles). Player-typed names and copies a player owns are never
  translated. Built-in doctrines, armor plans and fire-team templates are
  reinstalled from code and name `@key` references.
- Commendations are saved as a catalog reference with arguments and formatted
  in the reader's language (`Commendations`); legacy free-text entries display
  exactly as saved.
- Guards (`TranslationSmokeTest`): every key named in Java or MLX exists, every
  argument pattern parses, no argument pattern carries an undoubled apostrophe,
  and player-facing MLX contains no literal copy. Developer markup
  (`ui-workbench.mlx`, `debug-missions.mlx`) and the briefing's debug drawer
  are exempt. Per-surface tests (`OpeningLocalizationTest`,
  `EquipmentLocalizationTest`, `MissionSelectLocalizationTest`,
  `SquadDefinitionLocalizationTest`, `MissionFlowLocalizationTest`,
  `BattleUiLocalizationTest`) check quantity forms and that a translation
  overlay reaches output while names and ids pass through.
- Expanded-copy evidence: `opening-localization`, `equipment-localization` and
  `mission-flow-localization` snapshot suites render English and +50% copy at
  1920x1080 / 100%. They check layout, not a shipped translation.

## Covered surfaces

Opening tutorials and company rooms; shared equipment catalogs and spec sheets;
contract board; squad definitions, billet roles and service records; mission
briefing, pre-battle muster, stationing, results, debrief and salvage; mission
objective names, steps, measures and blockers; battle HUD, order, mech, power,
air and squad overlays, battlefield markers, order cursors and battle comms.
Debug launchers, debug drawers and debug panels are deliberately English.

## Translations

Each language is its own mod under `translations/<locale>/`: `mod_info.json`
depending on `starsector_marines` (so it loads after it), a partial
`data/strings/strings.json` merged over the English `marineOps` keys, and
`data/config/settings.json` setting `localeOverride` (and `cjkMode` for
Chinese, Japanese and Korean). It sits outside `mod/`, so `deployMod`,
`packageRelease` and the jar never carry it. `TranslationOverlayTest` holds
every override to a key the English catalog has, with the same argument
indexes and formats, printf specifiers, `@key` references and doubled
apostrophes. Shared terms live in the language's `glossary.md`; proper names
stay as the story docs spell them.

Simplified Chinese (`zh_CN`, owner's first language) has these slices:

- [x] Opening dialogue: prologue, Nessa's yard, Castle's preparation guide,
  Morgan's induction, the Cerberus start and hand-off, the first-lift lesson
  (`openingCompanyPrologue*`, `openingCompanyYard*`,
  `openingCompanyPreparation*`, `openingOfficerInduction*`,
  `openingCerberusCompany*`, `openingYard*Dialogue/NextStep`, `lessonFirstLift*`).
- [ ] Mission board: `missionSelect*`, contract board, `wayline*` offers.
- [ ] Briefing and muster: `briefingUi*`, `briefingVm*`, `waylineBriefing*`,
  stationing.
- [ ] Results, debrief and salvage: `resultsScreen*`, `debrief*`, loot.
- [ ] Battle UI: `battleDirect*`, `battleComms*`, `battleWs*`, objective
  names and markers, the Relief and Dust Ledger HUDs.
- [ ] Company rooms' own labels: Barracks, Armory, Shuttle Deck, Mech Lab,
  ship transfer and refit (the bulk of the `opening*` keys).

Blocking a live zh_CN pass:

- **Font.** The retained UI draws the vanilla Orbitron and Insignia bitmap
  faces, which have no CJK glyphs; a missing glyph measures 0 px and draws
  nothing. Chinese needs replacement `.fnt` faces at the same paths (what
  Starsector's Chinese packs ship). Until then the layout lint of Chinese copy
  is vacuous, so translated layout is checked only by the +50% expanded copy.
- The planet-menu option "Marine operations" and other `rules.csv` text are
  outside the catalog and stay English.

## Remaining before the gate is green

- Translation review and live opening/re-entry acceptance.
- Expanded-copy layout for compact controls: rarity/firepower badges, long claim
  buttons and Mech Lab header/socket labels. Shared hover sheets wrap.
- Domain display names still read from English fields on some surfaces:
  `CommandPower.displayName`, `OperationTier.displayName` outside the contract
  board and briefing, `ExperienceTier`/`SoldierAptitude` display names outside
  the muster, generated map site, sequence and compound names, and
  `shipHull` names (`Strings.named` falls back to the id spelled out).
- Generated mission data (`Mission.requirements`, `Mission.flavor`) and
  `MissionOutcome.smallWarOverhead` are composed in English when generated and
  saved; they need the commendation treatment (key plus arguments).
- Objective phases other than the opening and raid phases have no catalog
  entries yet and read as the id in words.
- Cleanup: the never-called legacy widget builders in `BriefingScreen`
  (`buildMissionColumn`, `buildDetachmentColumn`, `buildButtons`,
  `buildLiftReadout`) and catalog keys left unused by the squad sweep
  (`openingBilletRole*`, `openingFirstRefitSelected`,
  `openingFleetArmoryNoKit`, `openingFleetArmoryUnclassified`).
