# Role and access

Status: ACTIVE — direction for how equipment is organised and how a squad is
put together. The role vocabulary is closed, the matrix is filled, and both are
under test. Doctrine still fuses role with access; that is the remaining work.

Written: 2026-08-29
Updated: 2026-08-29 — role became closed validated data (`ArmorRole`), the
matrix was filled with six patterns, and `ArmorCatalogShapeTest` asserts both
role span and the capability ladder.

Read `progression-nouns.md` for the standing rules equipment must obey,
`integral-system-slate.md` for which traditions build a suit capability and
what it should be, and `equipment-lore-catalog.md` for each pattern's
provenance. This doc sits underneath all three: it is about what the catalog's
*shape* should be, which those docs assume and none of them owns.

**`powered-assault-armor-roles.md` owns the role vocabulary**, and this doc does
not introduce a second one. That story already establishes the closed role set
and the principle this doc rests on — *"Role is deliberately not quality. Future
masterwork recon armor can remain light"* — and lists a closed role vocabulary
among its outstanding scope. What follows is the other half of the same idea: the
story says role must stop being inferred from tier, and this doc says the catalog
currently makes that impossible to demonstrate, because no role spans two tiers.

## The problem, stated as evidence

Every armour pattern in the catalog, by the role its own copy claims and the
tier it is sold at:

| Role | I | II | III | IV |
| --- | --- | --- | --- | --- |
| LINE | field-fatigues | — | combat, aegis, palatine, furnace-line, line | — |
| SECURITY | cordon-shell | militia | — | — |
| RAIDER | lashplate-harness | outlaw | reaver | — |
| SCOUT | — | scout | — | — |
| ASSAULT | — | — | — | heavy, specter, bulwark, reliquary, lions-mantle, foundry-breaker |

Eighteen patterns on a diagonal. Six of them share one cell. Two roles exist at
exactly one tier each. (This table is the state that motivated the doc; it is
kept as written because the argument is about the shape, and the shape is what
changed.)

**Role is not a category here. It is a label stuck on a rung of a ladder.**
Buying up the ladder does not upgrade a marine, it changes what that marine
*is* — a company that can afford tier IV stops having scouts, because no
tier-IV scout exists to have.

The visible consequence is that a fully equipped company fields nothing but
assault suits, every assault suit carries a breaching screen by
`integral-system-slate.md`'s role rule, and so every marine in the force has
the same capability. That reads as "tier grants the ability", which was never
the intent and is not what the code does. The cause is one missing column, not
a rule that needs changing.

## Two axes, currently fused

**Role** — what a marine is *for*. Recon, holding a line, getting through a
door, carrying the heavy thing. It is set by how the unit is organised, and it
barely moves as a company gets richer: a squad that needed a scout last year
still needs one.

**Access** — what the company can *afford*, and what its patrons and markets
will sell it. It moves constantly, and when it moves it should raise the whole
squad rather than reshape it.

A poor company fields a scout, a support gunner, and riflemen in patched
kit. A rich company fields *the same squad* in Domain-grade kit. Nobody stops
having a scout because they got rich, and nobody acquires a breaching
speciality merely by getting rich either.

Today these are one axis. `SquadArmorDoctrine` names twelve concrete pattern
ids, so a doctrine cannot say "a scout, four breachers, seven riflemen" without
also fixing exactly how good each one's kit is. That single fact is what forces
`DebugCompany` to select doctrines by exact tier match — a workaround for an
axis that should not have been fused in the first place, and one that
disappears on its own once they are separated.

**Role does not yet exist as a concept.** `unitClass` on a catalog entry is
free text, validated only for non-emptiness, read only by the Armory to print a
word on a card. Making role real is part of this work rather than a use of
something already there.

## Squad composition

A squad is twelve marines in three fire teams of four. The composition is the
doctrine's business and the kit quality is the company's, so:

- **A doctrine names a role mix**, per billet, the way it already names patterns
  per billet. "One scout, one support, four assault, six line" is a doctrine.
  So is "twelve assault" — a deliberately lopsided breach unit, which becomes a
  real doctrinal choice with a real cost rather than the only thing the top of
  the ladder can mean.
- **Access resolves each slot to a pattern.** The same doctrine at tier II and
  at tier IV is the same unit, better equipped.
- **Tradition chooses among the patterns access allows.** A Hegemony company and
  a Tri-Tachyon one resolve the same role at the same tier to different suits,
  which is where `equipment-lore-catalog.md`'s provenance rules keep applying
  unchanged.

The three bands that motivated this doc then fall out of the model instead of
being special-cased:

1. **Early.** A role mix at tiers I–II. Only the scout billet's pattern carries
   a capability, so exactly one marine in the squad has one.
2. **Established.** Tier III. Scout, line and support patterns each carry
   theirs, so capability is varied within the squad, by role.
3. **Specialist.** A lopsided doctrine at tier IV — every marine a breacher —
   chosen because that is what the unit is for, and paid for in what it gives
   up.

None of those is a rule. They are what a role mix plus an access level produces.

## Capability follows role, and ladders with tier

`integral-system-slate.md` already establishes the first half: a capability
attaches to what a suit is *for*, so all six assault patterns breach and no
other pattern does. That rule is correct and this doc does not amend it.

What it could not demonstrate is the second half, because no effect exists at
more than one tier: the sweep only at II, the pod only at III, the screen only
at IV. So "the claim is the role, not the tier" has been true in intent and
unobservable in practice.

Once a role spans tiers, the same capability exists at more than one strength,
and the ladder between them becomes the thing that expresses access:

- A tier-II Janus sweep and a tier-IV one are the same capability. The better
  suit reads further, holds it longer, or recovers faster — a real progression a
  player can feel by upgrading rather than by re-roling.
- **Grade stays description, not arithmetic.** `progression-nouns.md` is
  deliberate that a Masterwork system is finely made rather than automatically
  strongest, and the Specter proves it: masterwork build, nearly the smallest
  pool, sold on recycle speed. Tier is the strength axis; grade remains the
  character axis; a suit may be a masterwork example of a cheap tier.
- Because a capability then exists at two tiers, the ladder is *checkable*. A
  tier-IV system weaker than its tier-II sibling in every axis is a bug a test
  can refuse, which is not possible while each effect is a singleton.

## The role set

`powered-assault-armor-roles.md` already names three: **light infiltration /
recon**, **standard line combat**, and **heavy mechanized battlesuit**, with
`ARMORLESS` as an unpowered kit rather than a fourth. Those are the roles, and
this doc adopts them rather than proposing others. In the table above they read
as SCOUT, LINE and ASSAULT.

Two of the five labels in that table are not roles at all and its closed
vocabulary would already retire them:

- **SECURITY** reads as *garrison and low-intensity policing*, but every pattern
  wearing the label is also cheap. It is a price band with a name — light-role
  kit at the bottom of the ladder.
- **RAIDER** reads as *provenance*: pirate and Pather manufacture. An outlaw
  rifleman is a line marine in outlaw-made armour, and the tradition already
  carries that flavour without needing a role slot.

A fourth is adopted: **support** — the marine carrying the heavy or indirect
weapon, and whatever else the squad needs carried for it.
`integral-system-slate.md` records that the micro-missile pod's home was "a
deliberate choice among three plausible homes" before settling on a *line* suit,
which is what having nowhere to put it looks like. The Aegis is that role's first
member, and the pod stops being a line suit's oddity.

The vocabulary is therefore `ArmorRole`: UNPOWERED, RECON, LINE, ASSAULT,
SUPPORT. It is closed and validated at parse time, which is what makes the
catalog's shape countable at all — the free text it replaced could describe a
price band or a manufacturer and nothing would object.

## Which cells to fill

Not every cell. A role should span the range its users actually operate at, and
an empty cell is a statement that nobody builds that thing.

The gaps that mattered are filled. Six patterns, no new art — every one reuses
an appearance family and a tier icon that already shipped:

| Pattern | Role | Tier | Tradition | Carries |
| --- | --- | --- | --- | --- |
| Kestrel riot shell | assault | II | Corporate security surplus | A real shield and no shove worth the name |
| Packframe support harness | support | II | Independent crews | One rocket on a welded rail |
| Pathfinder recon suit | recon | III | Hegemony | A survey array that reads further than a Janus and announces itself doing it |
| Redoubt siege plate | line | IV | Hegemony | Nothing — it holds a breach somebody else made |
| Outrider deep-recon suit | recon | IV | Persean League | The deepest return anyone fields, built to be shared |
| Arbalest support battlesuit | support | IV | Hegemony | Four rounds and a doctrine for spending them |

Each was written to be a different suit rather than a scaled copy, per the
standing rule below. The Kestrel is the clearest case: it is the only breacher
whose whole value is the screen and whose assist is negligible, which is what a
riot rig actually is and is nothing like the six crossing suits above it.

Still deliberately empty: a tier-I version of everything.
`integral-system-slate.md`'s "do not give a faction a system so it has one"
applies to cells too — a cell exists because somebody would build that thing,
not to make the table rectangular.

Deliberately *not* filled: a tier-I version of everything. `integral-system-slate.md`'s
"do not give a faction a system so it has one" applies to cells too — a cell
exists because somebody would build that thing, not to make the table
rectangular.

## What this does not change

- **The one-family-one-role rule.** Assault patterns breach; nothing else does.
  Filling the matrix does not license spreading effects around.
- **Provenance discipline.** `equipment-lore-catalog.md` still decides whether a
  tradition has anything to say. A new cell that would be a faction-coloured
  clone of an existing pattern should stay empty.
- **Suits sharing an effect must be different suits.** Two tiers of the same
  capability must differ by more than a scaled number, or they are the palette
  swap the catalog exists to prevent.
- **The simulation.** Nothing here needs a new mechanic. The capability model,
  the durability model, and the activation policy are all unaffected; this is
  about which suits exist and who wears them.

## Consequences

- `SquadArmorDoctrine` changes shape: a role mix per billet rather than a
  pattern per billet, resolved against an access level.
- `DebugCompany`'s `bestArmorTier(doctrine) == plan.maxArmorTier()` exact match
  is deleted rather than relaxed. It exists only because doctrines carry tier;
  once they carry roles, a company applies its access to whatever doctrine it
  is running.
- `DebugCompanyStage`'s ladder is separately non-monotonic — Veteran Company is
  HARDENED, Reinforced drops back to SEASONED, Full Strength returns to
  HARDENED — so a company currently loses its battlesuits by growing. That is a
  standalone defect and should be fixed on its own rather than folded into this
  work.
- `unitClass` became `ArmorRole`, validated at parse time — the closed vocabulary
  `powered-assault-armor-roles.md` had outstanding. Two of the five words it
  replaced were retired rather than translated: SECURITY was a price band and
  RAIDER was provenance, so their patterns are line suits that happen to be cheap
  or pirate-made.
- `integral-system-slate.md` gains the tier ladder for each capability it
  already lists; its role rule is unchanged.

## Why this was not visible earlier

Worth recording, because the failure was one of evidence rather than judgement.
`powered-assault-armor-roles.md` has said "role is not quality" since it was
written, and the catalog has contradicted it the whole time. Nothing caught that,
because every check that could have was written against one pattern at a time —
a pattern's role is well-formed, a pattern's stats are in band — and the defect
only exists in the shape of the whole table. It surfaced when a player-facing
question ("why do I never see this ability?") was chased far enough to notice
that a fully equipped company can only buy one role.

A rule about a catalog's shape needs a check that reads the whole catalog.
`ArmorCatalogShapeTest` is that check. It pins what holds today — every role has
a pattern, every role is reachable through the player's own supply, and no
capability is carried by two roles — and *reports* the role/tier matrix rather
than asserting it, because the invariant this doc actually wants does not hold
yet and a failing test is not a plan.

That report is now an assertion. `everyRoleIsAvailableAtMoreThanOneTier` is the
invariant this doc exists for, and a second — `aCapabilityGetsStrongerWithTheTierOfTheSuitCarryingIt`
— pins the ladder: the best example of an effect at each populated tier is at
least as strong as the best at the tier below, measured on that effect's own
headline axis. It is compared between populated tiers and on the *best* example
rather than every one, because a capability may skip a tier and because suits
within a tier are deliberate side-grades — the Foundry-breaker and the Reliquary
are both tier IV and their pools differ sevenfold.

Neither test could have been written before the matrix was filled. The second
one especially: a ladder needs two rungs.
