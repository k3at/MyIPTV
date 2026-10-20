# ---------------------------------------------------------------------------
# MyIPTV brand art generator
#
# One source of truth for every piece of artwork:
#
#   play-store/logo-source.png                 the supplied logo (trimmed master)
#   app/src/main/res/mipmap-*/ic_launcher.png  legacy launcher icons (API <= 25)
#   app/src/main/res/mipmap-anydpi-v26/        adaptive icon (API 26+)
#   app/src/main/res/drawable-nodpi/           adaptive layers + in-app logo
#   app/src/main/res/drawable-xhdpi/           TV banner 320x180
#   play-store/icon-512.png                    Play store icon
#   play-store/feature-graphic-1024x500*.png   Play store feature graphic
#   play-store/tv-banner-320x180.png           Android TV listing banner
#
# Swap logo-source.png for a new file and re-run this script to re-derive
# everything. Nothing here is hand-edited.
# ---------------------------------------------------------------------------
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$root = "C:\Users\k3ats\Documents\Default Project\MyIPTV"
$res  = Join-Path $root "app\src\main\res"
$out  = Join-Path $root "play-store"
New-Item -ItemType Directory -Force -Path $out | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $res "drawable-nodpi") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $res "mipmap-anydpi-v26") | Out-Null

$dot = [char]0x00B7   # middle dot, avoids encoding issues in PS 5.1
$SS  = 4              # supersample factor: draw big, then downscale once

# ---------- palette: dark navy derived from the logo's own blues ----------
$navyA = [System.Drawing.Color]::FromArgb(255,   6,  10,  51)   # #060A33
$navyB = [System.Drawing.Color]::FromArgb(255,  19,  31, 110)   # #131F6E
$veil  = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(42, 0, 0, 0))
$white = [System.Drawing.Brushes]::White
$soft  = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(225, 255, 255, 255))

function New-RoundRectPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-Gradient([System.Drawing.Graphics]$g, [float]$size) {
    $brush = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.PointF]::new(0, 0), [System.Drawing.PointF]::new($size, $size), $navyA, $navyB)
    return $brush
}

# ---------- load the logo and trim it to its real bounding box ----------
$logoPath = Join-Path $out "logo-source.png"
$src = [System.Drawing.Bitmap]::new($logoPath)
$minX = $src.Width; $maxX = -1; $minY = $src.Height; $maxY = -1
for ($y = 0; $y -lt $src.Height; $y++) {
    for ($x = 0; $x -lt $src.Width; $x++) {
        if ($src.GetPixel($x, $y).A -gt 24) {
            if ($x -lt $minX) { $minX = $x }
            if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }
            if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
if ($maxX -lt 0) { throw "logo-source.png has no visible content." }
$rect = [System.Drawing.Rectangle]::new($minX, $minY, $maxX - $minX + 1, $maxY - $minY + 1)
$logo = $src.Clone($rect, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$src.Dispose()
$logoAspect = [float]$logo.Height / [float]$logo.Width

function Draw-Logo([System.Drawing.Graphics]$g, [float]$cx, [float]$cy, [float]$width) {
    $h = $width * $logoAspect
    $dest = [System.Drawing.RectangleF]::new($cx - $width / 2.0, $cy - $h / 2.0, $width, $h)
    $g.DrawImage($logo, $dest)
}

function New-Bitmap([int]$w, [int]$h) {
    $b = [System.Drawing.Bitmap]::new($w, $h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($b)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.PageUnit = [System.Drawing.GraphicsUnit]::Pixel
    Write-Output -NoEnumerate $b
}

function Downscale([System.Drawing.Bitmap]$big, [int]$sizeW, [int]$sizeH) {
    $small = [System.Drawing.Bitmap]::new($sizeW, $sizeH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($small)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $g.DrawImage($big, [System.Drawing.Rectangle]::new(0, 0, $sizeW, $sizeH))
    $g.Dispose()
    $big.Dispose()
    Write-Output -NoEnumerate $small
}

# ---------- 1. launcher icons, one square composition at every density -----
# Full bleed: the launcher applies its own mask (circle / squircle / square),
# so nothing may sit in the transparent corners.
$made = @()
$mipmaps = @{ "mipmap-mdpi" = 48; "mipmap-hdpi" = 72; "mipmap-xhdpi" = 96; "mipmap-xxhdpi" = 144; "mipmap-xxxhdpi" = 192 }
foreach ($dir in $mipmaps.Keys | Sort-Object { $mipmaps[$_] }) {
    $size = [int]$mipmaps[$dir]
    $big = New-Bitmap ($size * $SS) ($size * $SS)
    $g = [System.Drawing.Graphics]::FromImage($big)
    $grad = New-Gradient $g $big.Width
    $g.FillRectangle($grad, 0, 0, $big.Width, $big.Height)
    Draw-Logo $g ($big.Width / 2.0) ($big.Height / 2.0) (0.62 * $big.Width)
    $grad.Dispose(); $g.Dispose()
    $img = Downscale $big $size $size
    $p = Join-Path $res "$dir\ic_launcher.png"
    $img.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
    $img.Dispose()
    $made += $p
}

# ---------- 2. adaptive icon layers (API 26+) ------------------------------
# 108dp canvas, visible circle 72dp: the logo must fit the inscribed circle,
# so it is drawn at 50% of the layer width.
$layer = 432
$big = New-Bitmap ($layer * $SS) ($layer * $SS)
$g = [System.Drawing.Graphics]::FromImage($big)
$grad = New-Gradient $g $big.Width
$g.FillRectangle($grad, 0, 0, $big.Width, $big.Height)
$grad.Dispose(); $g.Dispose()
$img = Downscale $big $layer $layer
$p = Join-Path $res "drawable-nodpi\ic_launcher_background.png"
$img.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
$img.Dispose()
$made += $p

$big = New-Bitmap ($layer * $SS) ($layer * $SS)   # transparent foreground
$g = [System.Drawing.Graphics]::FromImage($big)
Draw-Logo $g ($big.Width / 2.0) ($big.Height / 2.0) (0.50 * $big.Width)
$g.Dispose()
$img = Downscale $big $layer $layer
$p = Join-Path $res "drawable-nodpi\ic_launcher_foreground.png"
$img.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
$img.Dispose()
$made += $p

$xml = @"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"@
$p = Join-Path $res "mipmap-anydpi-v26\ic_launcher.xml"
[System.IO.File]::WriteAllText($p, $xml, (New-Object System.Text.UTF8Encoding($true)))
$made += $p

# ---------- 3. in-app logo (header + Settings, 28-38dp) -------------------
# Not the launcher icon: on API 26+ that resolves to the adaptive icon, whose
# safe-zone padding would shrink the logo in the app header.
$big = New-Bitmap ($layer * $SS) ($layer * $SS)
$g = [System.Drawing.Graphics]::FromImage($big)
$path = New-RoundRectPath 0 0 $big.Width $big.Height (0.22 * $big.Width)
$grad = New-Gradient $g $big.Width
$g.FillPath($grad, $path)
Draw-Logo $g ($big.Width / 2.0) ($big.Height / 2.0) (0.60 * $big.Width)
$grad.Dispose(); $g.Dispose()
$img = Downscale $big $layer $layer
$p = Join-Path $res "drawable-nodpi\app_logo.png"
$img.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
$img.Dispose()
$made += $p

# ---------- 4. Play store icon 512 x 512 ----------------------------------
$s = 512
$big = New-Bitmap ($s * $SS) ($s * $SS)
$g = [System.Drawing.Graphics]::FromImage($big)
$grad = New-Gradient $g $big.Width
$g.FillRectangle($grad, 0, 0, $big.Width, $big.Height)
Draw-Logo $g ($big.Width / 2.0) ($big.Height / 2.0) (0.60 * $big.Width)
$grad.Dispose(); $g.Dispose()
$img = Downscale $big $s $s
$p = Join-Path $out "icon-512.png"
$img.Save($p, [System.Drawing.Imaging.ImageFormat]::Png)
$img.Dispose()
$made += $p

# ---------- 5. feature graphic 1024 x 500 (+ safe-zone variant) ------------
function New-FeatureGraphic([string]$path, [float]$logoW, [float]$logoX, [float]$logoY, [float]$textX, [float]$titleSize, [float]$subSize) {
    $w = 1024; $h = 500
    $big = New-Bitmap ($w * 2) ($h * 2)
    $g = [System.Drawing.Graphics]::FromImage($big)
    $g.ScaleTransform(2.0, 2.0)
    $grad = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.Point]::new(0, 0), [System.Drawing.Point]::new($w, $h), $navyA, $navyB)
    $g.FillRectangle($grad, 0, 0, $w, $h)
    $g.FillRectangle($veil, [int]($textX - 40), 0, [int]($w - $textX + 40), $h)
    Draw-Logo $g $logoX $logoY $logoW
    $fontBig = [System.Drawing.Font]::new("Segoe UI", $titleSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $fontSub = [System.Drawing.Font]::new("Segoe UI", $subSize, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = [System.Drawing.StringFormat]::new()
    $fmt.Alignment = [System.Drawing.StringAlignment]::Near
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString("MyIPTV", $fontBig, $white, [System.Drawing.RectangleF]::new($textX, 165, 640, 110), $fmt)
    $g.DrawString("Live TV $dot Movies $dot Series", $fontSub, $soft, [System.Drawing.RectangleF]::new($textX + 4, 275, 640, 55), $fmt)
    $grad.Dispose(); $fontBig.Dispose(); $fontSub.Dispose(); $g.Dispose()
    $big2 = Downscale $big $w $h
    $big.Dispose()
    $big2.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $big2.Dispose()
}
New-FeatureGraphic (Join-Path $out "feature-graphic-1024x500.png")         230 190 250 340 104 36
New-FeatureGraphic (Join-Path $out "feature-graphic-1024x500-safezone.png") 190 215 250 350 88 32
$made += (Join-Path $out "feature-graphic-1024x500.png")
$made += (Join-Path $out "feature-graphic-1024x500-safezone.png")

# ---------- 6. TV banner 320 x 180 ----------------------------------------
function New-TvBanner([string]$path) {
    $w = 320; $h = 180
    $big = New-Bitmap ($w * 4) ($h * 4)
    $g = [System.Drawing.Graphics]::FromImage($big)
    $g.ScaleTransform(4.0, 4.0)
    $grad = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
        [System.Drawing.Point]::new(0, 0), [System.Drawing.Point]::new($w, $h), $navyA, $navyB)
    $g.FillRectangle($grad, 0, 0, $w, $h)
    Draw-Logo $g 76 90 122
    $fontBig = [System.Drawing.Font]::new("Segoe UI", 40, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $fontSub = [System.Drawing.Font]::new("Segoe UI", 15, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = [System.Drawing.StringFormat]::new()
    $fmt.Alignment = [System.Drawing.StringAlignment]::Near
    $fmt.LineAlignment = [System.Drawing.StringAlignment]::Center
    $g.DrawString("MyIPTV", $fontBig, $white, [System.Drawing.RectangleF]::new(152, 55, 165, 55), $fmt)
    $g.DrawString("Live TV $dot Movies $dot Series", $fontSub, $soft, [System.Drawing.RectangleF]::new(154, 108, 165, 30), $fmt)
    $grad.Dispose(); $fontBig.Dispose(); $fontSub.Dispose(); $g.Dispose()
    $small = Downscale $big $w $h
    $big.Dispose()
    $small.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $small.Dispose()
}
$banner = Join-Path $res "drawable-xhdpi\tv_banner.png"
New-TvBanner $banner
$made += $banner
Copy-Item $banner (Join-Path $out "tv-banner-320x180.png") -Force
$made += (Join-Path $out "tv-banner-320x180.png")

$logo.Dispose()

# ---------- report ---------------------------------------------------------
Write-Output "--- generated ---"
foreach ($f in $made) {
    $item = Get-Item $f
    $dim = ""
    try { $img = [System.Drawing.Image]::FromFile($f); $dim = "$($img.Width)x$($img.Height)"; $img.Dispose() } catch { }
    "{0,-46} {1,10:N0} B  {2}" -f $item.FullName.Replace("$root\", ""), $item.Length, $dim
}
