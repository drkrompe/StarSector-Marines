# Open pit voids: the bench face

ImageGen master for the open pit (`voids.md`): a blasted bench face in an open-cut
ore mine, seen straight on, laid along the pit's faces by `NaturalVoidRenderSystem`.
The master keeps its brief (`*.prompt.txt`) and provenance (`*.png.provenance.json`).

The shipped tile, `mod/graphics/battle/voids/pit-bench-wall.png`, is the master made
seamless and sized to 1024 with the sky port's script:

    python art-source/voids/sky-port/make_seamless.py art-source/voids/open-pit/pit-bench-wall.png mod/graphics/battle/voids/pit-bench-wall.png --size 1024

## The machines

`pit-machines.png` is the ImageGen master for `PitMachines`: six mining machines overhead, fronts east, three
columns by two rows in `PitMachines.Kind` order. It was filled in over `machines-guide.png`, which
`machines-guide.py` draws at 30 px a metre, with the parked vehicles' raw sheet as the material reference.
Its brief is `pit-machines.prompt.txt` and its provenance `pit-machines.png.provenance.json`. It ships
unchanged as `mod/graphics/battle/voids/pit-machines.png`; the renderer cuts the frames by thirds and halves.
