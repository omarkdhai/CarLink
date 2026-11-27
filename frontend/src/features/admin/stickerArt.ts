import { QRCodeDecoderErrorCorrectionLevel, QRCodeEncoder } from '@zxing/library'
import darkTemplate from '@/assets/carlink-sticker-dark.png'
import lightTemplate from '@/assets/carlink-sticker-light.png'
import type { StickerStyle } from '@/types'

/*
 * Prints a sticker's own QR and link into the CarLink sticker design the buyer
 * chose (assets/carlink-sticker-dark.png or -light.png, both 1417 × 2598, same
 * QR and footer geometry). The template's sample QR and
 * sample link are painted over; everything else (headline, banner, logo,
 * footer) is the design itself. All coordinates are template pixels, measured
 * from the design file.
 */

const W = 1417
const H = 2598
const DARK = '#0f172a'
const BLUE = '#2563eb'

/** Per-design template and footer colours (link's first line, footer fill). */
const THEMES: Record<StickerStyle, { template: string; footer: string; linkHead: string }> = {
  DARK: { template: darkTemplate, footer: BLUE, linkHead: '#dbeafe' },
  LIGHT: { template: lightTemplate, footer: DARK, linkHead: '#60a5fa' },
}

/** Square the QR modules are drawn in (33 modules × 27.5 px in the design). */
const AREA = { x: 255, y: 973, size: 907.5 }
/** White surface around the modules that is repainted (inside the rounded card). */
const CARD_FILL = { x: 220, y: 938, size: 977.5 }
/** CarLink symbol in the middle of the QR, copied as-is from the template. */
const LOGO = { x: 619, y: 1337, size: 180 }
/** Modules are left out within this half-width of the centre (symbol + breathing room). */
const LOGO_CLEAR_HALF = 136
/** Printed link, bottom right of the blue footer. */
const LINK = { x: 879, maxWidth: 515, capHeight: 49, baselines: [2323, 2386, 2448] }
const LINK_FILL = { x: 860, y: 2245, w: 540, h: 230 }

const templates = new Map<StickerStyle, Promise<HTMLImageElement>>()

function loadTemplate(style: StickerStyle): Promise<HTMLImageElement> {
  let pending = templates.get(style)
  if (!pending) {
    pending = new Promise((resolve, reject) => {
      const img = new Image()
      img.onload = () => resolve(img)
      img.onerror = () => {
        templates.delete(style)
        reject(new Error('Sticker template failed to load'))
      }
      img.src = THEMES[style].template
    })
    templates.set(style, pending)
  }
  return pending
}

function roundRect(ctx: CanvasRenderingContext2D, x: number, y: number, size: number, r: number, color: string) {
  ctx.beginPath()
  ctx.roundRect(x, y, size, size, r)
  ctx.fillStyle = color
  ctx.fill()
}

function drawQr(ctx: CanvasRenderingContext2D, img: HTMLImageElement, url: string) {
  // H: 30% recovery, so the modules hidden behind the symbol do not matter.
  const matrix = QRCodeEncoder.encode(url, QRCodeDecoderErrorCorrectionLevel.H).getMatrix()
  const n = matrix.getWidth()
  const m = AREA.size / n
  const cx = AREA.x + AREA.size / 2
  const cy = AREA.y + AREA.size / 2

  ctx.fillStyle = '#ffffff'
  ctx.fillRect(CARD_FILL.x, CARD_FILL.y, CARD_FILL.size, CARD_FILL.size)

  const inFinder = (x: number, y: number) =>
    (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7)
  const inLogo = (x: number, y: number) => {
    const left = AREA.x + x * m
    const top = AREA.y + y * m
    return left < cx + LOGO_CLEAR_HALF && left + m > cx - LOGO_CLEAR_HALF &&
      top < cy + LOGO_CLEAR_HALF && top + m > cy - LOGO_CLEAR_HALF
  }

  ctx.fillStyle = DARK
  for (let y = 0; y < n; y++) {
    for (let x = 0; x < n; x++) {
      if (matrix.get(x, y) !== 1 || inFinder(x, y) || inLogo(x, y)) continue
      // Whole-pixel edges so neighbouring modules join without hairline seams.
      const x0 = Math.floor(AREA.x + x * m)
      const y0 = Math.floor(AREA.y + y * m)
      ctx.fillRect(x0, y0, Math.ceil(AREA.x + (x + 1) * m) - x0, Math.ceil(AREA.y + (y + 1) * m) - y0)
    }
  }

  // Finder "eyes": dark rounded frame, white ring, blue rounded centre.
  for (const [fx, fy] of [[0, 0], [n - 7, 0], [0, n - 7]]) {
    const x = AREA.x + fx * m
    const y = AREA.y + fy * m
    roundRect(ctx, x, y, 7 * m, 1.36 * m, DARK)
    roundRect(ctx, x + m, y + m, 5 * m, 0.4 * m, '#ffffff')
    roundRect(ctx, x + 2 * m, y + 2 * m, 3 * m, 0.55 * m, BLUE)
  }

  ctx.fillStyle = '#ffffff'
  ctx.fillRect(cx - LOGO_CLEAR_HALF, cy - LOGO_CLEAR_HALF, 2 * LOGO_CLEAR_HALF, 2 * LOGO_CLEAR_HALF)
  ctx.drawImage(img, LOGO.x, LOGO.y, LOGO.size, LOGO.size, LOGO.x, LOGO.y, LOGO.size, LOGO.size)
}

/** "carlink.com/c/" + the token over two lines, as printed on the design. */
function linkLines(url: string): string[] {
  const u = new URL(url)
  const token = u.pathname.replace(/^\/c\//, '')
  const half = Math.ceil(token.length / 2)
  return [`${u.host}/c/`, token.slice(0, half), token.slice(half)]
}

function drawLink(ctx: CanvasRenderingContext2D, url: string, theme: (typeof THEMES)[StickerStyle]) {
  ctx.fillStyle = theme.footer
  ctx.fillRect(LINK_FILL.x, LINK_FILL.y, LINK_FILL.w, LINK_FILL.h)

  const lines = linkLines(url)
  const family = '"Bricolage Grotesque", "Plus Jakarta Sans", sans-serif'
  // Size the font so capitals are as tall as in the design, then shrink if a line is too wide.
  ctx.font = `700 100px ${family}`
  const capAt100 = ctx.measureText('Z').actualBoundingBoxAscent || 70
  let size = (100 * LINK.capHeight) / capAt100
  ctx.font = `700 ${size}px ${family}`
  const widest = Math.max(...lines.map((l) => ctx.measureText(l).width))
  if (widest > LINK.maxWidth) size *= LINK.maxWidth / widest
  ctx.font = `700 ${size}px ${family}`
  ctx.textBaseline = 'alphabetic'
  ctx.textAlign = 'left'
  lines.forEach((line, i) => {
    ctx.fillStyle = i === 0 ? theme.linkHead : '#ffffff'
    ctx.fillText(line, LINK.x, LINK.baselines[i])
  })
}

/**
 * The full sticker artwork for one sticker, as a PNG blob at print size
 * (1417 × 2598 px).
 */
export async function renderStickerArt(publicUrl: string, style: StickerStyle = 'DARK'): Promise<Blob> {
  const [img] = await Promise.all([
    loadTemplate(style),
    document.fonts.load('700 70px "Bricolage Grotesque"').catch(() => undefined),
  ])
  const canvas = document.createElement('canvas')
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Canvas unavailable')
  ctx.drawImage(img, 0, 0, W, H)
  drawQr(ctx, img, publicUrl)
  drawLink(ctx, publicUrl, THEMES[style])
  return new Promise((resolve, reject) =>
    canvas.toBlob((b) => (b ? resolve(b) : reject(new Error('PNG export failed'))), 'image/png'),
  )
}
