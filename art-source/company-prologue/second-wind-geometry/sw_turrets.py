"""The four weapon mounts: three small, one medium, at vanilla Cerberus XY."""

# Vanilla warhound.ship weapon slot XY positions; size is a placeholder radius.
MOUNTS = [(-11,20,2.6),(-11,-20,2.6),(41,11,2.6),(36,-16,3.8)]


def build(kit):
    for i,(x,y,size) in enumerate(MOUNTS,1):
        hit=kit.surface(x,y)
        if hit is None:
            raise RuntimeError(f'Turret {i} has no supporting hull surface')
        z=hit[0].z+.4
        kit.cylinder(f'Turret {i} shallow ring',(x,y,z),size+1,.8,kit.steel)
        kit.box(f'Turret {i} raised armored housing',(x,y,z+1.6),(size*2,size*1.7,2.6),kit.steel,.7)
        kit.cylinder(f'Turret {i} forward barrel',(x+size+2,y,z+1.6),.38 if size<3 else .6,5,kit.steel,'X',16)
