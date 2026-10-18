$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$root = "C:\Users\k3ats\Documents\Default Project\MyIPTV"
$res  = Join-Path $root "app\src\main\res"
$out  = Join-Path $root "play-store"
New-Item -ItemType Directory -Force -Path $out | Out-Null

$dot = [char]0x00B7   # middle dot, avoids encoding issues in PS 5.1

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

function Draw-Glyph([System.Drawing.Graphics]$g, [float]$cx, [float]$cy, [float]$scale) {
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $ringW = [Math]::Max(2.0, 0.05 * $scale)
    $ringRect = [System.Drawing.RectangleF]::new($cx - 0.32 * $scale, $cy - 0.32 * $scale, 0.64 * $scale, 0.64 * $scale)
    $pen = [System.Drawing.Pen]::new([System.Drawing.Color]::White, $ringW)
    $pen.Alignment = [System.Drawing.Drawing2D.PenAlignment]::Center
    $g.DrawEllipse($pen, $ringRect)

    $p1 = [System.Drawing.PointF]::new($cx - 0.10 * $scale, $cy - 0.17 * $scale)
    $p2 = [System.Drawing.PointF]::new($cx - 0.10 * $scale, $cy + 0.17 * $scale)
    $p3 = [System.Drawing.PointF]::new($cx + 0.19 * $scale, $cy)
    $g.FillPolygon([System.Drawing.Brushes]::White, [System.Drawing.PointF[]]@($p1, $p2, $p3))
    $pen.Dispose()
}

# ---------- 1. App icon 512 x 512 ----------
$s = 512
$bmp = [System.Drawing.Bitmap]::new($s, $s, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$path = New-RoundRectPath 0 0 $s $s (0.22 * $s)
$c1 = [System.Drawing.Color]::FromArgb(255, 124, 77, 255)
$c2 = [System.Drawing.Color]::FromArgb(255, 69, 39, 196)
$grad = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    [System.Drawing.Point]::new(0, 0), [System.Drawing.Point]::new($s, $s), $c1, $c2)
$g.FillPath($grad, $path)
Draw-Glyph $g ($s / 2.0) ($s / 2.0) ($s * 1.0)
$g.Dispose()
$bmp.Save((Join-Path $out "icon-512.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# ---------- 2. Feature graphic 1024 x 500 ----------
$w = 1024; $h = 500
$bmp = [System.Drawing.Bitmap]::new($w, $h, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

$c1 = [System.Drawing.Color]::FromArgb(255, 124, 77, 255)
$c2 = [System.Drawing.Color]::FromArgb(255, 48, 24, 128)
$grad = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    [System.Drawing.Point]::new(0, 0), [System.Drawing.Point]::new($w, $h), $c1, $c2)
$g.FillRectangle($grad, 0, 0, $w, $h)

# subtle darker panel on the right for text legibility
$veil = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(38, 0, 0, 0))
$g.FillRectangle($veil, 300, 0, $w - 300, $h)

Draw-Glyph $g 165 250 250

$fontBig = [System.Drawing.Font]::new("Segoe UI", 104, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$fontSub = [System.Drawing.Font]::new("Segoe UI", 36, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)

$fmt = [System.Drawing.StringFormat]::new()
$fmt.Alignment = [System.Drawing.StringAlignment]::Near
$fmt.LineAlignment = [System.Drawing.StringAlignment]::Center

$white = [System.Drawing.Brushes]::White
$soft = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(225, 255, 255, 255))

$g.DrawString("MyIPTV", $fontBig, $white, [System.Drawing.RectangleF]::new(330, 160, 660, 120), $fmt)
$g.DrawString("Live TV $dot Movies $dot Series", $fontSub, $soft, [System.Drawing.RectangleF]::new(336, 285, 660, 60), $fmt)

$g.Dispose()
$bmp.Save((Join-Path $out "feature-graphic-1024x500.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# ---------- 2b. Feature graphic, safe-zone variant (content pulled inward) ----------
$w = 1024; $h = 500
$bmp = [System.Drawing.Bitmap]::new($w, $h, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$grad = [System.Drawing.Drawing2D.LinearGradientBrush]::new(
    [System.Drawing.Point]::new(0, 0), [System.Drawing.Point]::new($w, $h), $c1, $c2)
$g.FillRectangle($grad, 0, 0, $w, $h)
$g.FillRectangle($veil, 300, 0, $w - 300, $h)
Draw-Glyph $g 200 250 210
$fontBig2 = [System.Drawing.Font]::new("Segoe UI", 88, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$fontSub2 = [System.Drawing.Font]::new("Segoe UI", 32, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$g.DrawString("MyIPTV", $fontBig2, $white, [System.Drawing.RectangleF]::new(340, 175, 560, 100), $fmt)
$g.DrawString("Live TV $dot Movies $dot Series", $fontSub2, $soft, [System.Drawing.RectangleF]::new(344, 278, 560, 50), $fmt)
$g.Dispose()
$bmp.Save((Join-Path $out "feature-graphic-1024x500-safezone.png"), [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()

# ---------- 3. TV banner 320 x 180 (Play / Android TV listing) ----------
Copy-Item (Join-Path $res "drawable-xhdpi\tv_banner.png") (Join-Path $out "tv-banner-320x180.png") -Force

Write-Output "--- generated ---"
Get-ChildItem $out -File | Select-Object Name, Length | Format-Table -AutoSize
