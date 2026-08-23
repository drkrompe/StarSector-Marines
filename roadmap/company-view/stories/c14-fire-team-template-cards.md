# C14 — Fire-team template cards

> The Fleet Armory currently makes the player repeat routine equipment work
> marine by marine, while its bulk preset paints a whole twelve-marine squad
> with one weapon and one armour pattern. Equip the organization instead:
> design a four-billet card once, then assign it wherever the company owns a
> complete kit.

**Status:** IN PROGRESS — Slices 1–2 shipped; Slice 3 next
**Written:** 2026-08-23
**Updated:** 2026-08-23 — Player-authored card design now ships; availability and fast swap are next.

Read `company-view-nouns.md` first; it owns the standing organization and
equipment-authority model this story extends.

## Decision

The **fire team is the Fleet Armory's routine equipment unit**. The squad
remains the unit selected for deployment and commanded in battle; the four
marines remain named people with careers and casualties; between them, a
fire-team template card is the reusable equipment establishment the player
builds and assigns.

This is a player-facing organizational level, not merely an implementation
partition. A squad reads as three named team slots, each carrying one assigned
card and four equipment billets.

## Vocabulary

- A **template card** is a reusable design. It is not a physical item and is
  never consumed by assignment.
- A **billet** is one of the card's four equipment positions. Cards describe
  billets, not named marines; current team members materialize those positions.
- A **card assignment** binds a template to one fire team and issues its
  occupied, ready billets from fleet stock.
- A **squad arrangement** is a saved set of three card assignments. It is a
  quick-refit convenience, not a fourth kind of equipment.
- **Conformance** compares the card's establishment with the equipment its
  current members actually hold. Vacancies, wounds and unrecovered equipment
  can leave an assigned team out of conformance without deleting its card.

## Design laws

1. **Cards are reusable; equipment is finite.** The same card may be assigned
   to several teams only when the armory can supply one complete physical kit
   per assignment.
2. **Design is not gated by stock.** The player may author and save a card they
   cannot currently field. Stock gates assignment, not imagination.
3. **Assignment is atomic and net of returns.** A refit counts the selected
   team's current issue as returned before evaluating the new card. Failure
   changes neither equipment nor assignment, and reports the missing category.
4. **Special equipment belongs to cards, not personal overrides.** A
   Masterwork DMR becomes the scarce centre of a marksman card. Owning one lets
   the company field one such team; the player does not maintain a parallel
   per-marine exception system.
5. **Cards describe billets, not people.** Leadership and future specialist
   matching decide who occupies each billet. Replacements inherit the
   establishment instead of requiring twelve paper-doll visits.
6. **Editing never silently refits fielded teams.** Saving a changed card
   creates a pending revision or a new card. Applying that revision is an
   explicit inventory transaction with its total company delta visible.
7. **The assigned card survives degradation.** A vacancy, WIA marine or gear
   loss produces an incomplete/out-of-conformance card; it does not erase the
   team's intended organization.

## Intended Fleet Armory flow

The left formation rail is company → squad → fire team. Selecting a team shows
its four billets and current members. The card library sits beside it and
reports, for every design, how many additional complete teams current free
stock can supply.

Dropping or clicking a card previews the net return/issue transaction. A valid
drop assigns immediately. An invalid drop leaves the team untouched and names
the shortfall. Three-card squad arrangements provide the same operation across
Alpha, Bravo and Charlie in one transaction.

Individual marines remain inspectable for aptitude, career, wounds and the kit
their billet gives them. The paper doll stops being an equipment authoring
surface once the designer replaces it.

## Slices

1. ~~**Built-in cards and one-team assignment.**~~ **Shipped 2026-08-23.** Introduce a four-billet card
   model and armory-owned starter library; persist the assigned card id on each
   squad team; atomically materialize a selected card onto a complete RTD team,
   members, including primaries, grades, armour and secondaries; expose the
   three teams and starter cards on the existing LOADOUTS surface. This is the
   compatibility slice: per-marine kit remains the battle-facing materialized
   state.
2. ~~**Loadout designer.**~~ **Shipped 2026-08-23.** Create, clone, rename and edit
   player-authored cards. Saving is legal without stock. Validate exactly four
   complete billets and preserve stable card ids across save/load. Built-ins
   clone into custom cards; billet changes save as a new revision so assigned
   teams retain their prior intent and issue.
3. **Availability and fast swap.** Preview returned/required/free gear, show
   `fielded` and `ready to issue` counts, and support card-to-team drag/drop or
   an equivalent one-action assignment. A swap between two teams is evaluated
   as one net transaction.
4. **Squad arrangements.** Save and apply a three-card squad composition
   atomically. A failure in any team leaves all three assignments unchanged.
5. **Conformance and replacement flow.** Show vacancies, WIA-held gear and
   missing recovered equipment per billet; reconcile returning/replacement
   marines against the assigned card without reopening individual loadouts.
6. **Retire routine per-marine editing.** Keep the marine inspector and roster
   legibility owned by progression, but remove individual equipment mutation
   once every catalog item is expressible in the designer.

## Slice 1 acceptance

- A built-in card can mix four different billet issues, including a secondary.
- Assignment touches only the selected four-marine team; the other eight squad
  members are unchanged.
- The same card can be reused across teams only as far as finite stock permits.
- Existing gear on the target team is counted as returned during the atomic
  check.
- Locked recipes, insufficient primaries, armour or secondaries, stationing,
  and an invalid/incomplete team all fail without partial mutation.
- A successful assignment persists the card id on the selected team and the
  Fleet Armory names that card beside Alpha, Bravo or Charlie.
- Existing deployment freezes still read `MarineSoldier` equipment exactly as
  before; this slice changes no battle-tier contract.

## Slice 2 acceptance

- The Fleet Armory exposes a distinct Card Designer with a paged library and
  exactly four editable billet rows.
- Each billet authors its role label, primary, grade, armour and optional
  secondary from the full catalog, regardless of unlock or stock state.
- New and cloned cards receive stable persisted custom ids; built-in cards
  cannot be renamed or deleted.
- Renaming a custom card changes metadata only. Saving changed billet issue
  creates a new card id and leaves every assigned team on its prior revision.
- A custom card referenced by any squad team cannot be deleted.
- The LOADOUTS card picker pages through libraries larger than the four starter
  cards.

## Boundaries

- C14 changes equipment authorship, not deployment selection or player battle
  orders. Squads remain the command target.
- `company-view-nouns.md` owns how assigned teams maneuver in battle.
  It may later consume card-derived tactical tags, but C14 does not make gear a
  hidden AI-order channel.
- `progression-nouns.md` owns catalog breadth, unlocks, grades and individual
  career legibility. C14 gives those systems an organizational surface.
- Recovery and permanent loss of issued gear need an explicit economy rule
  before Slice 5 claims that equipment disappears with a casualty.
