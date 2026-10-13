# Ridgewatch missile battery

Two independent square sprites: a stationary octagonal base and a rotating
twin missile rack, both centered on the same pivot. The mount's forward direction
is up; the weapon alternates between the two pod muzzles.

`produce.py guide` builds the retained geometry guide. `prompt.txt` is the exact
reskin prompt sent to the built-in ImageGen tool in edit mode, referencing
`guide.png` with transparent background enabled. `source.png` is the raw result.

`python art-source/defences/ridge-missile-turret/produce.py export
art-source/defences/ridge-missile-turret/source.png` crops the fixed source frames
and resizes them to the two 256-pixel runtime assets under
`mod/graphics/structures/ridge-aa-{base,mount}.png`. It preserves square frame
centers and transparency; no alpha trimming or geometry inference is used.
