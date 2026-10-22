# Barracks bed damage

The ground berthing fitting uses four directional two-cell residential beds.
These states belong to those exact ids; the one-cell military bunk is a separate family.

Built-in ImageGen reskinned `templates/berthing-bed-damage-guide.png` using
`templates/berthing-bed-reference.png` as the intact material reference.
The retained Python guide producer renders both inputs. The exact prompt is
`berthing-bed-damage.prompt.txt`; accepted RGBA output is `berthing-bed-damage.raw.png`.

Columns are head W, E, S, N; rows are damaged and destroyed. ImageGen returned
1774 by 887 pixels and shifted the vertical columns slightly. The authoring
document records measured full placement frames, including transparent margins,
rather than trimming to opaque bounds. The intact/damaged/debris comparisons
were reviewed through `createSnapshots -Psnapshot=berthing-bed-damage` at 32 and
64 pixels per cell. Quarter-turn comparisons use actual combat damage.

Export with `tools/authoring.cmd tileset_export -` and
`{"name":"berthing-bed-damage"}` on stdin. Shipped output is
`mod/graphics/doodads/berthing-bed-damage.png` plus its catalog. No secondary
producer patches the packed atlas, and this sheet has no normal/height companions.
