# Gorge river voids: the rock walls

ImageGen master for the gorge river, the first natural void style (`voids.md`): a
sheer wall of sedimentary strata seen straight on, laid along a gorge's faces by
`NaturalVoidRenderSystem`. The master keeps its brief (`*.prompt.txt`) and
provenance (`*.png.provenance.json`).

The shipped tile, `mod/graphics/battle/voids/gorge-wall.png`, is the master made
seamless and sized to 1024 with the sky port's script:

    python art-source/voids/sky-port/make_seamless.py art-source/voids/gorge/gorge-wall.png mod/graphics/battle/voids/gorge-wall.png --size 1024

The half-offset blend doubles a few bedding lines in its blend band; a master
generated to wrap would not need it.
