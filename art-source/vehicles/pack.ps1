# Runtime downsample only. Preserve complete frame, proportions, colors and alpha.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$source = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot 'wayline-hauler.master.png'))
$target = [System.Drawing.Bitmap]::new(512, [int][Math]::Round(512 * $source.Height / $source.Width), [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($target)
try {
    $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.DrawImage($source, 0, 0, $target.Width, $target.Height)
    $target.Save((Join-Path $repository 'mod/graphics/battle/vehicles/wayline-hauler.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally { $graphics.Dispose(); $target.Dispose(); $source.Dispose() }
