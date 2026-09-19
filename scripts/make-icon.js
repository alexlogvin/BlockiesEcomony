// Generates the mod icon: assets/blockies_economy/icon.png
//
// A generator rather than a checked-in blob with no source, so the icon can be adjusted
// by editing values here instead of by opening an image editor and guessing at the
// original palette. Run it with:  node scripts/make-icon.js
//
// The subject is a cobblestone coin. Cobblestone is this mod's currency symbol -- it is
// what the HUD draws next to a balance -- and a coin says "money" at a glance in a way a
// plain block does not. The two together are the whole mod in one image.
//
// Drawn at 64x64 and scaled up 4x with nearest-neighbour, so the result is honest pixel
// art at 256x256 rather than a blurry upscale. Minecraft's own textures work the same way.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const SIZE = 64;
const SCALE = 4;

// Vanilla cobblestone's palette, light to dark.
const STONE = [
  [0x9a, 0x9a, 0x9a],
  [0x86, 0x86, 0x86],
  [0x7a, 0x7a, 0x7a],
  [0x6b, 0x6b, 0x6b],
  [0x5a, 0x5a, 0x5a],
];
const OUTLINE = [0x2b, 0x2b, 0x2b];
const RIM_LIGHT = [0xc4, 0xc4, 0xc4];
const LETTER = [0xf2, 0xf2, 0xf2];
const LETTER_SHADOW = [0x3a, 0x3a, 0x3a];

// A small deterministic PRNG. The icon must come out identical on every run, or the file
// churns in git for no reason every time anyone regenerates it.
let seed = 0x5eed1234;
function rand() {
  seed ^= seed << 13; seed >>>= 0;
  seed ^= seed >> 17;
  seed ^= seed << 5; seed >>>= 0;
  return seed / 0x100000000;
}

const px = new Uint8Array(SIZE * SIZE * 4);
function set(x, y, [r, g, b], a = 255) {
  if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) return;
  const i = (y * SIZE + x) * 4;
  px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a;
}

const CX = 31.5, CY = 31.5, R = 30;

// Cobblestone blobs: seed points scattered in the disc, each pixel taking the shade of
// its nearest point. That is a Voronoi diagram, which is essentially what cobblestone's
// texture is -- irregular chunks of stone at slightly different tones.
const blobs = [];
for (let i = 0; i < 70; i++) {
  blobs.push({
    x: rand() * SIZE,
    y: rand() * SIZE,
    shade: STONE[Math.floor(rand() * STONE.length)],
  });
}

for (let y = 0; y < SIZE; y++) {
  for (let x = 0; x < SIZE; x++) {
    const dx = x - CX, dy = y - CY;
    const d = Math.sqrt(dx * dx + dy * dy);
    if (d > R) continue;

    if (d > R - 1.6) { set(x, y, OUTLINE); continue; }
    // A lit top-left edge and a dark bottom-right one: the same one-pixel bevel every
    // vanilla item icon uses to stop a flat shape reading as a sticker.
    if (d > R - 4) {
      set(x, y, dx + dy < 0 ? RIM_LIGHT : STONE[4]);
      continue;
    }

    let best = null, bestD = Infinity;
    for (const b of blobs) {
      const bd = (x - b.x) ** 2 + (y - b.y) ** 2;
      if (bd < bestD) { bestD = bd; best = b; }
    }
    set(x, y, best.shade);
  }
}

// The letter B, drawn as a bitmap rather than with a font: at this size every pixel
// matters and a rasterised font would land half-on pixels.
const B = [
  '111110',
  '100011',
  '100011',
  '100011',
  '111110',
  '100011',
  '100011',
  '100011',
  '111110',
];
const GLYPH_SCALE = 4;
const gw = B[0].length * GLYPH_SCALE;
const gh = B.length * GLYPH_SCALE;
const gx = Math.round(CX - gw / 2) + 1;
const gy = Math.round(CY - gh / 2) + 1;

for (let r = 0; r < B.length; r++) {
  for (let c = 0; c < B[r].length; c++) {
    if (B[r][c] !== '1') continue;
    for (let sy = 0; sy < GLYPH_SCALE; sy++) {
      for (let sx = 0; sx < GLYPH_SCALE; sx++) {
        const x = gx + c * GLYPH_SCALE + sx;
        const y = gy + r * GLYPH_SCALE + sy;
        // Drop shadow first, so the letter stays readable over a light stone blob.
        set(x + 2, y + 2, LETTER_SHADOW);
      }
    }
  }
}
for (let r = 0; r < B.length; r++) {
  for (let c = 0; c < B[r].length; c++) {
    if (B[r][c] !== '1') continue;
    for (let sy = 0; sy < GLYPH_SCALE; sy++) {
      for (let sx = 0; sx < GLYPH_SCALE; sx++) {
        set(gx + c * GLYPH_SCALE + sx, gy + r * GLYPH_SCALE + sy, LETTER);
      }
    }
  }
}

// ---- encode -------------------------------------------------------------------------

const OUT = SIZE * SCALE;
const raw = Buffer.alloc(OUT * (OUT * 4 + 1));
let p = 0;
for (let y = 0; y < OUT; y++) {
  raw[p++] = 0; // filter type 0: none
  for (let x = 0; x < OUT; x++) {
    const i = ((y / SCALE | 0) * SIZE + (x / SCALE | 0)) * 4;
    raw[p++] = px[i]; raw[p++] = px[i + 1]; raw[p++] = px[i + 2]; raw[p++] = px[i + 3];
  }
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body) >>> 0);
  return Buffer.concat([len, body, crc]);
}

let crcTable = null;
function crc32(buf) {
  if (!crcTable) {
    crcTable = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      crcTable[n] = c;
    }
  }
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return c ^ -1;
}

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(OUT, 0);
ihdr.writeUInt32BE(OUT, 4);
ihdr[8] = 8;  // bit depth
ihdr[9] = 6;  // colour type: RGBA
const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk('IHDR', ihdr),
  chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
  chunk('IEND', Buffer.alloc(0)),
]);

const out = path.join(__dirname, '..', 'src', 'main', 'resources', 'assets',
    'blockies_economy', 'icon.png');
fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, png);
console.log(`wrote ${out} (${OUT}x${OUT}, ${png.length} bytes)`);
