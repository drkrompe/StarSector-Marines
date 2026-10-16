"""Command bridge and the fore pressure casing it stands on.

The V3 blockout had no discernible bridge: only this plain casing at the
forward end of the dorsal service spine.
"""


def build(kit):
    x,length,z=34,18,15
    kit.box('Fore pressure casing',(x,0,z),(length,11,4),kit.cream,1.5)
    for px in (x-5,x,x+5):
        kit.box('Casing access cover',(px,0,z+2.1),(4.1,8,.5),kit.cream,.25)
