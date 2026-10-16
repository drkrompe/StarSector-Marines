param([string]$OutputDirectory)

# Whole 3:2 story frames. 1200x800 covers the ~1050px art column at 1080p/100%.
# Quality 85 bounds disk cost; no alpha, crops, upscaling or shipped PNG masters.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$sourceDirectory = Join-Path $PSScriptRoot 'story-stills'
$destination = if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    Join-Path $repository 'mod/graphics/illustrations/company'
} else { $OutputDirectory }
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object MimeType -eq 'image/jpeg'
foreach ($source in (Get-ChildItem -LiteralPath $sourceDirectory -Filter '*.master.png' | Sort-Object Name)) {
    $image = [System.Drawing.Image]::FromFile($source.FullName)
    $packed = [System.Drawing.Bitmap]::new(1200, 800)
    $graphics = [System.Drawing.Graphics]::FromImage($packed)
    $parameters = [System.Drawing.Imaging.EncoderParameters]::new(1)
    $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
    try {
        if ($image.Width * 2 -ne $image.Height * 3 -or $image.Width -lt 1200) {
            throw "Expected a 3:2 master at least 1200px wide: $($source.Name)"
        }
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
        $graphics.DrawImage($image, [System.Drawing.Rectangle]::new(0, 0, 1200, 800),
            0, 0, $image.Width, $image.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
        $parameters.Param[0] = [System.Drawing.Imaging.EncoderParameter]::new([System.Drawing.Imaging.Encoder]::Quality, [long]85)
        $output = Join-Path $destination ($source.Name.Replace('.master.png', '.jpg'))
        $packed.Save($output, $codec, $parameters)
        $file = Get-Item -LiteralPath $output
        if ($file.Length -gt 400KB) { throw "Story frame exceeds 400 KiB budget: $output" }
        Write-Output ("{0}: 1200x800, {1:N0} KiB" -f $file.Name, ($file.Length / 1KB))
    } finally {
        $attributes.Dispose(); $parameters.Dispose(); $graphics.Dispose(); $packed.Dispose(); $image.Dispose()
    }
}
