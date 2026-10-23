# The Cordon — allied forces by density

Status: ACTIVE — the table is built (`CordonAlliedTable`, read by
`CordonSetup`); the urban backbone is built on the recommended option of the
owner's open pick `cordon-urban-allies`; air and guns wait on the shared
`heart-allied-tool` pick; the rows marked *not built* below are open.

Written: 2026-10-23
Updated: 2026-10-23 — the table built for every density; what is still open is marked.

Step 3 of the owner's plan for the Cordon. `cordon-nouns.md` is the model
(Allied armour, Logistics base, Allied column, Density); `allied-tools.md` is
the shared catalogue, base and density rule this table picks from, the same
one the heart uses (`heart-allied-forces.md`).

## What the allies hold

The line is four redoubts, each with a **sector**: the redoubt and the ground
either side of it to the middle of the next gap. The allies hold the ordinary
pressure in every sector by themselves and sag in the tides
(`cordon-nouns.md`, *The allies hold, the company decides*). What stands in a
sector beside the garrison is what changes with density.

## The table

| | Rural | Town | Urban |
| --- | --- | --- | --- |
| Beside each redoubt | a car or technical on each flank, out past the side wall | technicals on the flanks (built); *not built:* a technical on the street through the redoubt, riflemen in the house fronts | **a Hound pair in the street mouths and a walking rifle team** (built; the recommended option of `cordon-urban-allies`) |
| Opening armour | two cars, two technicals | four technicals | none |
| Base | a fenced yard on the main road; the shed builds | the base the map lays; the shed builds (*not built:* the town's goods yard) | the base the map lays; the shed's berths build the Hounds (*not built:* a depot in a warehouse block with a mech gantry) |
| Base produces | carrier, car, carrier, technical, round again | technical, carrier, Hound, round again | Hounds, one at a time at the shed's pace; no carriers |
| Opening carrier | one on the apron | one on the apron | none |
| Columns | walk in to the muster yard; carriers lift them up | walk in to the muster yard; carriers lift them up | walk the whole way up, two teams each, to the redoubt that needs them most |
| Carriers forward | yes: lift, then stay on the flanks of a calling redoubt as support | yes, roadbound: set down outside the rear gate only, and wait there | no |
| Ammunition (L2) | truck up the supply road | truck through the town's streets | truck along the cleared street to the rear gate |
| Air and guns | deferred to `heart-allied-tool` | deferred | deferred; matters most here |

The pace, counts and lines are starting values to balance on `cordon-line`
evidence at each density, not settled numbers. The table is one value per
density (`CordonAlliedTable`): the opening armour, whether the base opens with
a carrier, the shed's line and its pace, whether carriers go forward and where they set
down, and the mechs and rifle team in each redoubt's mouths. Nothing fields an
ally the table does not list, except a beat through the same producer (L2's
truck).

How the street mouths, the rear gate and a mech's sector are found is the
standing model: `cordon-nouns.md`, Allied armour (Allied mechs) and Logistics
base.

## The company's role by density

- **Rural:** the reserve. The armour covers the open ground in front, and
  carriers fill the redoubts; the company is called to a lane that breaks and
  to the rear when the base is hit.
- **Town:** the hinge. Armour cannot leave the streets, so the gaps between
  the village's blocks are the company's to hold in a tide.
- **Urban:** the hammer behind the mechs. A mech pair holds a street mouth
  but cannot cover every alley; the company is the force that clears a block
  the swarm has got into, and the one that walks forward to a redoubt that
  lost its mechs. With no carriers the company's own lift matters more.

## Air and guns

Whether the base calls a gunship sortie or brings a mortar or gun battery is
the heart's open question (`heart-allied-tool`); the Cordon takes the same
answer through the same producer (`allied-tools.md`), behind its own air gate
(the swarm's fliers over the line). Not built for the Cordon until that pick
is in.

## Open

- The owner's pick for the urban backbone (`cordon-urban-allies`): mech lance
  (recommended, built), infantry and mortars, or armour held to the boulevard.
  Seen in `cordon-allies-look` (opt-in `createSnapshots` suite);
  `-Dsnapshot.cordonAllies.options=town,urban` renders the built table at a
  redoubt instead.
- Town: a technical on the street through the redoubt and riflemen in the
  house fronts; the town's technicals still take the flank posts.
- The base's own ground by density: a town's goods yard, a city's depot and
  mech gantry. The shed's berths build Hounds meanwhile.
