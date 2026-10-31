# The Cordon — allied forces by density

Status: ACTIVE — the table is built (`CordonAlliedTable`, read by
`CordonSetup`); the urban backbone is the owner's pick `cordon-urban-allies`
(the mech lance, with a mortar team in each redoubt); air is the military's
wing, called through each redoubt's forward observer; the rows marked *not built* below are open.

Written: 2026-10-23
Updated: 2026-10-25 — air: the military's wing through a forward observer in each redoubt.

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
| Beside each redoubt | a car or technical on each flank, out past the side wall | a technical on the street through or past the redoubt, and later ones on the street nearest each flank post (built, the Line only: on the Road, hulls beside the main street block the convoys); a rifle team in the house fronts past each flank (built) | **a Hound pair in the street mouths and a walking rifle team, a mortar team in the yard** (built; the owner's pick `cordon-urban-allies`, the mech lance, with mortars added) |
| Opening armour | two cars, two technicals | four technicals | none |
| Base | a fenced yard on the main road; the shed builds | the shed or the town's goods yard, picked from the seed; the shed's berths or the yard's two open-air gantries build | the shed, a warehouse depot or a car-park depot, picked from the seed; the shed's berths or the depot's two open-air gantries build the Hounds |
| Base produces | carrier, car, carrier, technical, round again | technical, carrier, Hound, round again | Hounds, one at a time at the shed's pace; no carriers |
| Opening carrier | one on the apron | one on the apron | none |
| Columns | walk in to the muster yard; carriers lift them up | walk in to the muster yard; carriers lift them up | walk the whole way up, two teams each, to the redoubt that needs them most |
| Carriers forward | yes: lift, then stay on the flanks of a calling redoubt as support | yes, roadbound: set down outside the rear gate only, and wait there | no |
| Ammunition | a truck up the supply road when a redoubt runs low (L2 empties one), escorted by a surplus hull that it keeps to | the same; where the road was not laid, or is cut off, through the town's streets | the same, through the city's streets to the rear gate (built), escorted on foot by a surplus Hound or a street mouth's rifle team (built); a redoubt the streets do not reach is sent none |
| Reserve, asked by need | an armour pair of technicals for an uncovered sector | the same | a Hound pair walking up |
| Air and guns | the allied air wing off its air base in the rear, called through a forward observer in each redoubt and tasking its own sorties (built) | the same | the same; the yard's mortar team as well |

The pace, counts and lines are starting values to balance on `cordon-line`
evidence at each density, not settled numbers. The table is one value per
density (`CordonAlliedTable`): the opening armour, whether the base opens with
a carrier, the shed's line and its pace, whether carriers go forward and where they set
down, the mechs and rifle team in each redoubt's mouths, and the mortar team in its
yard, and the base's reserve the allied line command asks for by need (`cordon-allied-command.md`, The producer).
Nothing fields an
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

Air comes to the line from the military's air wing, off its own air base in the rear
beside the logistics base (`cordon-nouns.md`, Air base): each
redoubt's garrison carries a forward observer, and the wing strikes the masses
those observers see (`cordon-nouns.md`, Forward observer), and the air base sends
its own sorties at what the side can fairly know. Whether the base
also calls a gunship sortie or brings a gun battery is the heart's answer
(`heart-allied-forces.md`), not built for the Cordon.

## Open

- The urban backbone is picked (`cordon-urban-allies`, the mech lance; the
  owner added mortar teams in the redoubts, "likely source of friendly fire").
  Each urban redoubt's yard holds a mortar team whose standing fire mission is
  the ground past its street mouths; its tube never fires a round with one of
  the side's own under it and picks the target clear of them. Seen in
  `cordon-allies-look` (opt-in `createSnapshots` suite);
  `-Dsnapshot.cordonAllies.options=town,urban` renders the built table at a
  redoubt instead.
- Town: riflemen in the house fronts (`CordonHouseFronts`): a team past each
  flank wall, within sixteen cells and eight rows of the front, at the windows
  that look out on the front, or where the houses have none there (most
  town houses), behind the low cover in front of them, never a solid wall; all
  four within six cells of each other, or that flank has none. They hold their
  posts. A sector's first technical holds the
  street through or past its redoubt rather than a flank (a sector of one hull
  gives its flank up for it); a second takes a flank.
