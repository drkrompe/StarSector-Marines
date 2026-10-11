# Runtime downsample only. Preserve the complete square frame and colors.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$destination = Join-Path $repository 'mod/graphics/portraits'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
$source = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot 'mara-venn.master.png'))
try {
    if ($source.Width -ne $source.Height) { throw 'Portrait master must be square.' }
    $target = [System.Drawing.Bitmap]::new(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.DrawImage($source, 0, 0, $target.Width, $target.Height)
        $target.Save((Join-Path $destination 'mara-venn.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $target.Dispose() }
} finally { $source.Dispose() }
