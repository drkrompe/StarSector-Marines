"""Export accepted Specter plate and casing classification without repainting geometry.

Only source.png supplies shipped pixels. material-id.png classifies paintable
casing; geometry.json supplies retained cut regions in guide coordinates.
"""
import argparse
import json
import hashlib
from pathlib import Path
from PIL import Image, ImageChops

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[2]
OUTPUT = REPOSITORY / "mod/graphics/battle/marine-modular-topdown/variants/armor/specter-heavy"
PALETTES = {
    "slate": ("#26333c", "#657f91", "#b5c4cb"),
    "olive": ("#282f24", "#697553", "#b9bda1"),
    "oxide": ("#362526", "#905b4e", "#cfac94"),
    "sand": ("#38332b", "#9a8b69", "#d2c5a6"),
}

def linear(v):
    v /= 255
    return v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4

def encoded(v):
    v = max(0, min(1, v))
    return round(255 * (12.92*v if v <= .0031308 else 1.055*v**(1/2.4)-.055))

def lab(rgb):
    r,g,b = map(linear,rgb)
    l = (.4122214708*r+.5363325363*g+.0514459929*b)**(1/3)
    m = (.2119034982*r+.6806995451*g+.1073969566*b)**(1/3)
    s = (.0883024619*r+.2817188376*g+.6299787005*b)**(1/3)
    return (.2104542553*l+.793617785*m-.0040720468*s,
            1.9779984951*l-2.428592205*m+.4505937099*s,
            .0259040371*l+.7827717662*m-.808675766*s)

def rgb(color):
    ll,a,b = color
    l=(ll+.3963377774*a+.2158037573*b)**3
    m=(ll-.1055613458*a-.0638541728*b)**3
    s=(ll-.0894841775*a-1.291485548*b)**3
    return tuple(map(encoded,(4.0767416621*l-3.3077115913*m+.2309699292*s,
                             -1.2684380046*l+2.6097574011*m-.3413193965*s,
                             -.0041960863*l-.7034186147*m+1.707614701*s)))

def material_mask(material):
    mask=Image.new("L",material.size)
    mask.putdata([round(255*max(0,min(1,(min(r-g,b-g)-18)/72))
                       *max(0,min(1,(min(r,b)-38)/92)))
                  for r,g,b in material.convert("RGB").getdata()])
    return mask

def recolor(base,mask,palette):
    pixels=list(base.getdata()); weights=list(mask.getdata())
    values=sorted(lab(p[:3])[0] for p,w in zip(pixels,weights) if p[3]>=128 and w>=128)
    if not values:
        raise ValueError("Casing mask has no opaque paintable pixels")
    lo,mid,hi=(values[round(p*(len(values)-1))] for p in (.08,.5,.92))
    ramp=[lab(tuple(bytes.fromhex(c[1:]))) for c in palette]
    result=[]
    for pixel,weight in zip(pixels,weights):
        if weight==0 or pixel[3]==0:
            result.append(pixel); continue
        luminance=lab(pixel[:3])[0]
        a,b,t=(ramp[0],ramp[1],(luminance-lo)/max(.0001,mid-lo)) if luminance<=mid else (
            ramp[1],ramp[2],(luminance-mid)/max(.0001,hi-mid))
        t=max(0,min(1,t)); target=rgb(tuple(x+(y-x)*t for x,y in zip(a,b)))
        blend=weight/255
        result.append(tuple(round(x+(y-x)*blend) for x,y in zip(pixel[:3],target))+(pixel[3],))
    output=Image.new("RGBA",base.size); output.putdata(result)
    if output.getchannel("A").tobytes()!=base.getchannel("A").tobytes():
        raise ValueError("Palette export changed authoritative source alpha")
    if any(before!=after for before,after,weight in zip(pixels,result,weights) if weight==0):
        raise ValueError("Palette export changed protected source pixels")
    return output

def export(source=HERE/"source.png",material=HERE/"material-id.png"):
    source=Path(source); material=Path(material)
    if not source.exists() or not material.exists():
        raise FileNotFoundError("Specter accepted source.png and material-id.png are required")
    image=Image.open(source).convert("RGBA")
    classification=Image.open(material).convert("RGBA")
    if image.getchannel("A").getextrema()[0]==255:
        raise ValueError("Accepted raw source requires genuine transparency")
    metadata=json.loads((HERE/"source-regions.json").read_text())
    guide_width,guide_height=metadata["canvas"]
    if image.size!=(guide_width,guide_height):
        raise ValueError("Accepted source dimensions changed; measure and update source-regions.json")
    if classification.size!=image.size:
        raise ValueError("Material-ID dimensions changed; remeasure per-part material_bounds")
    field=material_mask(classification)
    OUTPUT.mkdir(parents=True,exist_ok=True)
    (HERE/"masks").mkdir(exist_ok=True)
    report={"source_dimensions":list(image.size),
            "source_sha256":hashlib.sha256(source.read_bytes()).hexdigest(),
            "material_id_sha256":hashlib.sha256(material.read_bytes()).hexdigest(),"parts":{}}
    for name,part in metadata["parts"].items():
        x1,y1,x2,y2=part["region"]
        cut=(round(x1*image.width/guide_width),round(y1*image.height/guide_height),
             round(x2*image.width/guide_width),round(y2*image.height/guide_height))
        cropped=image.crop(cut)
        bbox=cropped.getchannel("A").point(lambda a: 255 if a>=24 else 0).getbbox()
        if bbox is None:
            raise ValueError(f"Missing {name} in retained region {cut}")
        if bbox[0]==0 or bbox[1]==0 or bbox[2]==cropped.width or bbox[3]==cropped.height:
            raise ValueError(f"{name} touches cut edge; measure and update source-regions.json")
        original=cropped.crop(bbox)
        size=tuple(part["target_size"]) if "target_size" in part else (
            part["target_width"],round(original.height*part["target_width"]/original.width))
        base=original.resize(size,Image.Resampling.NEAREST)
        material_bounds=part["material_bounds"]
        source_mask=field.crop(tuple(material_bounds)).resize(original.size,Image.Resampling.LANCZOS)
        # The classification pass moved each component independently. Bounds
        # registration is retained per part; source pixels guard functional details.
        source_weights=[]; protected_cyan=protected_dark=0
        for pixel,weight in zip(original.getdata(),source_mask.getdata()):
            r,g,b,a=pixel
            cyan=g-r>=15 and b-r>=15 and g>=110
            dark=max(r,g,b)<75
            if cyan:
                protected_cyan+=a>=128; weight=0
            elif dark:
                protected_dark+=a>=128; weight=0
            source_weights.append(weight)
        source_mask.putdata(source_weights)
        protected_regions=part.get("protected_regions",[])
        region_protected=0
        for protected in protected_regions:
            left,top,right,bottom=protected["bounds"]
            source_left,source_top=cut[0]+bbox[0],cut[1]+bbox[1]
            local=(left-source_left,top-source_top,right-source_left,bottom-source_top)
            if not (0<=local[0]<local[2]<=original.width and 0<=local[1]<local[3]<=original.height):
                raise ValueError(f"Protected region {protected['id']} lies outside {name}; update source-regions.json")
            region_protected+=sum(a>=128 for a in original.getchannel("A").crop(local).getdata())
            source_mask.paste(0,local)
        source_mask=ImageChops.multiply(source_mask,original.getchannel("A"))
        source_mask.save(HERE/"masks"/f"{name}-source.png")
        mask=source_mask.resize(size,Image.Resampling.NEAREST)
        mask=ImageChops.multiply(mask,base.getchannel("A"))
        mask.save(HERE/"masks"/f"{name}.png")
        coverage=sum(w>=128 and p[3]>=128 for p,w in zip(base.getdata(),mask.getdata()))
        opaque=sum(p[3]>=128 for p in base.getdata())
        if not .05<=coverage/max(1,opaque)<=.90:
            raise ValueError(f"Implausible casing coverage for {name}: {coverage/max(1,opaque):.1%}")
        recolor(base,mask,PALETTES["slate"]).save(OUTPUT/f"{name}.png")
        for palette,colors in PALETTES.items():
            if palette=="slate": continue
            directory=OUTPUT/"paints"/palette; directory.mkdir(parents=True,exist_ok=True)
            painted=recolor(base,mask,colors)
            if any(p!=q for p,q,w in zip(base.getdata(),painted.getdata(),mask.getdata()) if w==0):
                raise ValueError(f"Protected hardware changed in {name}/{palette}")
            painted.save(directory/f"{name}.png")
        report["parts"][name]={"cut":list(cut),"content_bounds_in_cut":list(bbox),
                              "source_content_bounds":[cut[0]+bbox[0],cut[1]+bbox[1],cut[0]+bbox[2],cut[1]+bbox[3]],
                              "material_bounds":material_bounds,
                              "registration_scale":[original.width/(material_bounds[2]-material_bounds[0]),
                                                    original.height/(material_bounds[3]-material_bounds[1])],
                              "registration_translation":[cut[0]+bbox[0]-material_bounds[0]*original.width/(material_bounds[2]-material_bounds[0]),
                                                          cut[1]+bbox[1]-material_bounds[1]*original.height/(material_bounds[3]-material_bounds[1])],
                              "protected_cyan_source_pixels":protected_cyan,
                              "protected_dark_source_pixels":protected_dark,
                              "protected_regions":protected_regions,
                              "protected_region_source_pixels":region_protected,
                              "export_size":list(size),"casing_coverage":coverage/max(1,opaque)}
    report["body_socket_bounds"]=metadata["body_socket_bounds"]
    report["body_socket_center"]=metadata["body_socket_center"]
    (HERE/"export-measurements.json").write_text(json.dumps(report,indent=2)+"\n")
    return report

if __name__=="__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--source",type=Path,default=HERE/"source.png")
    parser.add_argument("--material",type=Path,default=HERE/"material-id.png")
    args=parser.parse_args(); print(json.dumps(export(args.source,args.material),indent=2))
