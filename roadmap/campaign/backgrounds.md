# Player background — the starting-state seed

> Design discussion, not a spec. Continues from [`themes.md`](themes.md).
> Captures the player-identity design; final background list comes from
> playtest.

The player character has identity at game start. The **name** is
user-entered (typed); the **background** is a *mechanical choice* from a
curated set — never a free-form customization surface.

## Why background, not customization

The player should have stakes and starting context without a "type your
motto / pick your banner / choose faction colors" customization layer.
See [[feedback-world-reactive-over-expressive]] — expressive
customization is deprioritized in favor of systems that respond to what
the player *does*. A background is a starting-state seed: it gives
variety, replayability, and starting narrative without violating that
principle. The player's identity is *what they did*, not *how they
decorated*.

## What a background is

Each background is a tuple of mechanical starting state:

- Starting credits (positive or negative — debt starts are valid; see
  [[feedback-hard-failure-preference]] and [`economy.md`](economy.md)'s
  bankruptcy/hard-fail stance).
- Starting captain(s) — a specific named NPC, sometimes with shared
  history with the player character.
- Faction rep modifiers (+ with one, − with another).
- MRB rep modifier.
- A starting trait or skill on the player character.
- Optional: starting equipment / cargo / a one-time intel asset.

## Sketches (not committed)

- **Former Hegemony Marines officer (dishonorable discharge)** — low
  Hegemony rep, one veteran captain who served with you, infantry-doctrine
  trait.
- **Ex-Tri-Tachyon corporate security** — TT contact, +MRB rep, starting
  credits + corporate debt.
- **Inheritor of a failing merc outfit** — one veteran captain, inherited
  tooling, inherited debt, generational rep with one patron. (This is the
  BattleTech lineage — see [`themes.md`](themes.md)'s condottieri framing.)
- **Sectarian apostate** — antagonistic Pather rep, a sympathetic captain
  hiding from the same past.
- **Indie pirate going legitimate** — MRB starting penalty (rumored past),
  bonus credits, smuggling trait.

## Critical constraint

Background drives *mechanical and narrative seed state*; it does **not**
enable cosmetic customization (banner, motto, faction colors). Resist the
temptation to grow a background picker into a character creator.

## Implementation surfaces

- Game-start dialog: name field + background picker (5–7 cards).
- Apply to `MarineRoster` (starting-captain injection) and `CampaignState`
  (rep modifiers, MRB starting value).
- Background id persisted (likely a `byte` on `CampaignState` for the run's
  lifetime) so it can drive flavor text in patron briefings that reference
  player history.
- **History-aware loop (future):** late-game patron flavor can reference
  the background — "we hear you served with the Hegemony marines yourself"
  — closing the loop on history-aware briefings (see
  [`narrative/overview.md`](narrative/overview.md) § procedural fatigue,
  point 4).

## Related

- [`themes.md`](themes.md) — the merc-company framing the background seeds.
- [`economy.md`](economy.md) — debt-start backgrounds feed the hard-fail
  survival window.
- [`narrative/overview.md`](narrative/overview.md) — history-aware
  briefings the background id feeds.
