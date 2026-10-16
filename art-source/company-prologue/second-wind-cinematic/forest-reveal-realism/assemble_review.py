"""Encode retained 96-frame forest proof and inspect media facts."""
import hashlib
import json
from pathlib import Path
import subprocess
from PIL import Image, ImageDraw
HERE=Path(__file__).resolve().parent
BIN=Path('C:/Users/Dillon/AppData/Local/Microsoft/WinGet/Packages/Gyan.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe/ffmpeg-8.1.1-full_build/bin')
FRAMES=HERE/'build/frames'
REVIEW=HERE/'review'
REVIEW.mkdir(exist_ok=True)
frames=[FRAMES/f'frame_{i:04d}.png' for i in range(96)]
assert all(p.is_file() for p in frames)
assert all(Image.open(p).size==(1920,1080) for p in frames)
movie=REVIEW/'jangala-forest-reveal-realism.mp4'
subprocess.run([str(BIN/'ffmpeg.exe'),'-hide_banner','-loglevel','error','-y','-framerate','24','-i',str(FRAMES/'frame_%04d.png'),'-frames:v','96','-c:v','libx264','-crf','18','-preset','slow','-pix_fmt','yuv420p','-movflags','+faststart','-an',str(movie)],check=True)
for name,i in [('start',0),('poster',48),('end',95)]:
    Image.open(frames[i]).convert('RGB').save(REVIEW/f'{name}.jpg',quality=95,subsampling=0)
sheet=Image.new('RGB',(1920,1080),(22,22,22))
draw=ImageDraw.Draw(sheet)
for tile,i in enumerate((0,48,95)):
    thumb=Image.open(frames[i]).convert('RGB').resize((960,540),Image.Resampling.LANCZOS)
    x,y=(tile%2)*960,(tile//2)*540
    sheet.paste(thumb,(x,y))
    draw.text((x+8,y+8),f'Output frame {i} / source {120+i*.5}',fill='white')
reference=Image.open(HERE.parent/'realism-lookdev/window-realism-v1.png').convert('RGB').resize((960,540),Image.Resampling.LANCZOS)
sheet.paste(reference,(960,540))
draw.text((968,548),'Approved realism still reference (resampled)',fill='white')
sheet.save(REVIEW/'contact-sheet.jpg',quality=93)
probe=json.loads(subprocess.check_output([str(BIN/'ffprobe.exe'),'-v','error','-count_frames','-show_streams','-show_format','-of','json',str(movie)],text=True,encoding='utf-8'))
video=next(s for s in probe['streams'] if s['codec_type']=='video')
assert video['width']==1920 and video['height']==1080
assert video['avg_frame_rate']=='24/1' and int(video['nb_read_frames'])==96
assert abs(float(probe['format']['duration'])-4)<.001
assert all(s['codec_type']!='audio' for s in probe['streams'])
hashes=[hashlib.sha256(p.read_bytes()).hexdigest() for p in frames]
pixel_hashes=[hashlib.sha256(Image.open(p).convert('RGB').tobytes()).hexdigest() for p in frames]
assert len(set(pixel_hashes))==96, 'Duplicate rendered pixel frames'
subprocess.run([str(BIN/'ffmpeg.exe'),'-hide_banner','-loglevel','error','-xerror','-i',str(movie),'-f','null','-'],check=True)
assets={}
for p in sorted((HERE/'assets').glob('*.png')):
    im=Image.open(p)
    alpha=im.getchannel('A') if im.mode=='RGBA' else None
    assets[p.name]={'dimensions':im.size,'mode':im.mode,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'alpha_extrema':alpha.getextrema() if alpha else None}
report={'movie_bytes':movie.stat().st_size,'movie_sha256':hashlib.sha256(movie.read_bytes()).hexdigest(),
    'scene_sha256':hashlib.sha256((HERE/'forest-reveal-realism.blend').read_bytes()).hexdigest(),
    'full_ffmpeg_xerror_decode_passed':True,'video':video,'duration_s':float(probe['format']['duration']),
    'all96_source_images_unique':len(set(hashes))==96,'source_frame_sha256':hashes,'assets':assets,
    'all96_pixel_frames_unique':True,'pixel_frame_sha256':pixel_hashes,
    'visual_review':'1080p endpoints and midpoint inspected before full encode; contact sheet retained. Playback review remains with final reviewer.'}
(REVIEW/'media-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k not in ('source_frame_sha256','pixel_frame_sha256','video')},indent=2))
