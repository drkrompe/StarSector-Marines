# Mechanical full-canvas downsample; two equal slots, source alpha retained.
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$source = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'master.png'))
$target = [System.Drawing.Bitmap]::new(1024,384)
$g = [System.Drawing.Graphics]::FromImage($target)
$g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($source,0,0,1024,384)
$target.Save((Join-Path $repository 'mod/graphics/battle/vehicles/militia-technical.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $target.Dispose(); $source.Dispose()
