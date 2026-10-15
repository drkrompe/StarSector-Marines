# Mechanical hull-frame registration and runtime downsample; masters remain unchanged.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$manifest = Get-Content -Raw (Join-Path $PSScriptRoot 'manifest.json') | ConvertFrom-Json
$scale = [int]$manifest.runtimeScale
$totalBytes = 0
foreach ($fighter in $manifest.fighters) {
    $source = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot $fighter.master))
    $width = [int]$fighter.sourceSize[0] * $scale
    $height = [int]$fighter.sourceSize[1] * $scale
    $target = [System.Drawing.Bitmap]::new($width, $height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    try {
        # Fit the meaningful painted hull to the original painted hull bounds.
        # Use retained measurements, not an alpha crop that could follow faint stray pixels.
        # Independent X/Y registration restores the original hull's proportions.
        $master = $fighter.registration.masterBodyBounds
        $native = $fighter.registration.nativeBodyBounds
        $sx = ($native[2] - $native[0]) * $scale / ($master[2] - $master[0])
        $sy = ($native[3] - $native[1]) * $scale / ($master[3] - $master[1])
        $destination = [System.Drawing.RectangleF]::new(
            $native[0] * $scale - $master[0] * $sx,
            $native[1] * $scale - $master[1] * $sy,
            $source.Width * $sx, $source.Height * $sy)
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.DrawImage($source, $destination,
            [System.Drawing.RectangleF]::new(0, 0, $source.Width, $source.Height),
            [System.Drawing.GraphicsUnit]::Pixel)
        $output = Join-Path $repository ('mod/' + $fighter.runtime)
        [System.IO.Directory]::CreateDirectory((Split-Path $output -Parent)) | Out-Null
        $target.Save($output, [System.Drawing.Imaging.ImageFormat]::Png)
        $bytes = (Get-Item -LiteralPath $output).Length
        $totalBytes += $bytes
        Write-Output ('{0}: {1} x {2}, {3:N1} KiB' -f $fighter.id, $width, $height, ($bytes / 1KB))
    } finally {
        $graphics.Dispose()
        $target.Dispose()
        $source.Dispose()
    }
}
if ($totalBytes -gt 1MB) { throw 'Aircraft runtime PNGs exceed the 1 MiB combined budget.' }
Write-Output ('Total: {0:N1} KiB (lossless RGBA PNG)' -f ($totalBytes / 1KB))
