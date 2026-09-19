// Generates the mod's coin artwork, at every size it is needed:
//
//   src/main/resources/assets/blockies_economy/icon.png              256x256  mod list
//   src/main/resources/.../textures/gui/coin.png                     16x16    HUD
//   branding/icon-1024.png                                           1024x1024  store pages
//
// Run with:  node scripts/make-icon.js
//
// A generator rather than three checked-in blobs with no source, so the artwork can be
// adjusted by editing values here instead of opening an image editor and guessing at the
// original palette -- and so the three sizes cannot drift apart.
//
// The subject is a cobblestone coin. Cobblestone is this mod's currency symbol, and a coin
// says "money" at a glance in a way a plain block does not. The two together are the whole
// mod in one image.
//
// Every size is drawn as real pixel art at its own small grid and then scaled up by whole
// numbers with nearest-neighbour, never interpolated. That is how Minecraft's own textures
// work, and it is why the 16x16 HUD icon is drawn at 16x16 rather than shrunk from 256,
// where the rim and the letter would turn to mush.

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

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

// The letter B as a bitmap rather than a font: at these sizes every pixel matters, and a
// rasterised glyph would land half-on pixels. Two cuts, because a 6x9 B has no room to
// breathe inside a 16-pixel coin.
const GLYPH_LARGE = [
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
const GLYPH_SMALL = [
  '1110',
  '1001',
  '1110',
  '1001',
  '1001',
  '1110',
];

/**
 * Draws one coin on a `size` x `size` RGBA grid.
 *
 * The stone texture is a Voronoi diagram: points scattered in the disc, each pixel taking
 * the shade of its nearest point. That is essentially what cobblestone is -- irregular
 * chunks of stone at slightly different tones -- and it stays convincing at any size as
 * long as the number of cells scales with the area.
 */
function drawCoin(size, blobCount, glyph, glyphScale, rimWidth) {
  // A small deterministic PRNG, re-seeded per size. The files must come out identical on
  // every run, or they churn in git every time anyone regenerates them.
  let seed = 0x5eed1234;
  const rand = () => {
    seed ^= seed << 13; seed >>>= 0;
    seed ^= seed >> 17;
    seed ^= seed << 5; seed >>>= 0;
    return seed / 0x100000000;
  };

  const px = new Uint8Array(size * size * 4);
  const set = (x, y, [r, g, b], a = 255) => {
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    const i = (y * size + x) * 4;
    px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = a;
  };

  const c = (size - 1) / 2;
  const radius = size / 2 - 1;

  const blobs = [];
  for (let i = 0; i < blobCount; i++) {
    blobs.push({
      x: rand() * size,
      y: rand() * size,
      shade: STONE[Math.floor(rand() * STONE.length)],
    });
  }

  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const dx = x - c, dy = y - c;
      const d = Math.sqrt(dx * dx + dy * dy);
      if (d > radius) continue;

      if (d > radius - rimWidth * 0.4) { set(x, y, OUTLINE); continue; }
      // A lit top-left edge and a dark bottom-right one: the one-pixel bevel every vanilla
      // item icon uses to stop a flat shape reading as a sticker.
      if (d > radius - rimWidth) {
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

  const gw = glyph[0].length * glyphScale;
  const gh = glyph.length * glyphScale;
  const gx = Math.round(c - gw / 2) + 1;
  const gy = Math.round(c - gh / 2) + 1;
  const shadow = Math.max(1, Math.round(glyphScale / 2));

  const stamp = (dx, dy, colour) => {
    for (let r = 0; r < glyph.length; r++) {
      for (let col = 0; col < glyph[r].length; col++) {
        if (glyph[r][col] !== '1') continue;
        for (let sy = 0; sy < glyphScale; sy++) {
          for (let sx = 0; sx < glyphScale; sx++) {
            set(gx + col * glyphScale + sx + dx, gy + r * glyphScale + sy + dy, colour);
          }
        }
      }
    }
  };

  // Shadow first, so the letter stays readable over a light stone blob.
  stamp(shadow, shadow, LETTER_SHADOW);
  stamp(0, 0, LETTER);

  return px;
}

// ---- PNG encoding --------------------------------------------------------------------

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

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body) >>> 0);
  return Buffer.concat([len, body, crc]);
}

function encodePng(px, size, scale) {
  const out = size * scale;
  const raw = Buffer.alloc(out * (out * 4 + 1));
  let p = 0;
  for (let y = 0; y < out; y++) {
    raw[p++] = 0; // filter type 0: none
    for (let x = 0; x < out; x++) {
      const i = ((y / scale | 0) * size + (x / scale | 0)) * 4;
      raw[p++] = px[i]; raw[p++] = px[i + 1]; raw[p++] = px[i + 2]; raw[p++] = px[i + 3];
    }
  }

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(out, 0);
  ihdr.writeUInt32BE(out, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 6;  // colour type: RGBA
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function write(relative, px, size, scale) {
  const out = path.join(__dirname, '..', relative);
  const png = encodePng(px, size, scale);
  fs.mkdirSync(path.dirname(out), { recursive: true });
  fs.writeFileSync(out, png);
  console.log(`${relative}  ${size * scale}x${size * scale}  ${png.length} bytes`);
}

const ASSETS = 'src/main/resources/assets/blockies_economy';

// 64-pixel master, scaled 4x for the mod list and 16x for store pages. One drawing, so the
// large image cannot end up being a different coin from the small one.
const master = drawCoin(64, 70, GLYPH_LARGE, 4, 4);
write(`${ASSETS}/icon.png`, master, 64, 4);
write('branding/icon-1024.png', master, 64, 16);

// Drawn at its own size, not shrunk from the master: at 16 pixels the rim is one pixel and
// the letter is four, and nothing survives being scaled down into that.
const hud = drawCoin(16, 7, GLYPH_SMALL, 1, 1.4);
write(`${ASSETS}/textures/gui/coin.png`, hud, 16, 1);
