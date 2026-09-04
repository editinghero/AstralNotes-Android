import fs from 'fs';
import path from 'path';
import sharp from 'sharp';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const THEMES = [
  {
    id: 'peach',
    name: 'Peach',
    primary: '#f0788a',
    accent: '#fde8cf',
    bgDark: '#1e1517'
  },
  {
    id: 'mauve',
    name: 'Mauve',
    primary: '#cba6f7',
    accent: '#f5b78f',
    bgDark: '#181524'
  },
  {
    id: 'teal',
    name: 'Teal',
    primary: '#7ee0c8',
    accent: '#f2a3b3',
    bgDark: '#141c1b'
  },
  {
    id: 'sky',
    name: 'Sky',
    primary: '#9dc4ff',
    accent: '#c4b0f5',
    bgDark: '#131724'
  }
];

const PATH_D = "M18 2l4 4M2 22l1.276-4.68c.083-.305.125-.458.189-.6.057-.127.126-.247.208-.359.092-.126.204-.238.428-.462L14.434 5.566c.198-.198.297-.297.411-.334.1-.033.209-.033.309 0 .114.037.213.136.411.334l2.869 2.869c.198.198.297-.297.334.411.033.1.033.209 0 .309-.037.114-.136.213-.334.411L8.1 19.899c-.224.224-.336.336-.462.428-.112.082-.232.151-.359.208-.142.064-.295.106-.6.189L2 22z";

function generateSvg({ size, rx = 0, bgDark, primary, isMaskable = false, isRaw = false }) {
  if (isRaw) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="${primary}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
  <g transform="translate(1.2, 1.2) scale(0.9)">
    <path d="${PATH_D}"/>
  </g>
</svg>`;
  }

  // Calculate scaling and offsets for centered icon
  // For maskable, safe zone is inner 65%
  const scaleRatio = isMaskable ? 0.55 : 0.68;
  const iconPixelSize = size * scaleRatio;
  const scale = iconPixelSize / 24;
  const offset = (size - iconPixelSize) / 2;

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">
  <rect width="${size}" height="${size}" ${rx > 0 ? `rx="${rx}"` : ''} fill="${bgDark}"/>
  <g transform="translate(${offset.toFixed(2)}, ${offset.toFixed(2)}) scale(${scale.toFixed(4)})">
    <g transform="translate(1.2, 1.2) scale(0.9)">
      <path d="${PATH_D}" fill="none" stroke="${primary}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
    </g>
  </g>
</svg>`;
}

async function run() {
  const publicDir = path.resolve(__dirname, '../web/public');
  const iconsBaseDir = path.join(publicDir, 'icons');

  if (!fs.existsSync(iconsBaseDir)) {
    fs.mkdirSync(iconsBaseDir, { recursive: true });
  }

  for (const theme of THEMES) {
    const themeDir = path.join(iconsBaseDir, theme.id);
    if (!fs.existsSync(themeDir)) {
      fs.mkdirSync(themeDir, { recursive: true });
    }

    // 1. Raw SVG icon
    const rawSvg = generateSvg({ size: 64, primary: theme.primary, isRaw: true });
    fs.writeFileSync(path.join(themeDir, 'favicon.svg'), rawSvg);
    fs.writeFileSync(path.join(themeDir, 'icon.svg'), rawSvg);

    // 2. 192x192 SVG
    const svg192 = generateSvg({ size: 192, rx: 32, bgDark: theme.bgDark, primary: theme.primary });
    fs.writeFileSync(path.join(themeDir, 'icon-192.svg'), svg192);

    // 3. 512x512 SVG
    const svg512 = generateSvg({ size: 512, rx: 85, bgDark: theme.bgDark, primary: theme.primary });
    fs.writeFileSync(path.join(themeDir, 'icon-512.svg'), svg512);

    // 4. Maskable 192x192 SVG
    const maskable192 = generateSvg({ size: 192, rx: 0, bgDark: theme.bgDark, primary: theme.primary, isMaskable: true });
    fs.writeFileSync(path.join(themeDir, 'icon-maskable-192.svg'), maskable192);

    // 5. Maskable 512x512 SVG
    const maskable512 = generateSvg({ size: 512, rx: 0, bgDark: theme.bgDark, primary: theme.primary, isMaskable: true });
    fs.writeFileSync(path.join(themeDir, 'icon-maskable-512.svg'), maskable512);

    // 6. Generate PNGs using sharp
    await sharp(Buffer.from(svg192)).resize(192, 192).png().toFile(path.join(themeDir, 'icon-192.png'));
    await sharp(Buffer.from(svg512)).resize(512, 512).png().toFile(path.join(themeDir, 'icon-512.png'));
    await sharp(Buffer.from(maskable192)).resize(192, 192).png().toFile(path.join(themeDir, 'icon-maskable-192.png'));
    await sharp(Buffer.from(maskable512)).resize(512, 512).png().toFile(path.join(themeDir, 'icon-maskable-512.png'));

    // 7. Apple touch icon (180x180) and favicon-32
    const appleSvg = generateSvg({ size: 180, rx: 0, bgDark: theme.bgDark, primary: theme.primary });
    await sharp(Buffer.from(appleSvg)).resize(180, 180).png().toFile(path.join(themeDir, 'apple-touch-icon.png'));
    await sharp(Buffer.from(rawSvg)).resize(32, 32).png().toFile(path.join(themeDir, 'favicon-32.png'));

    console.log(`Generated icons for theme: ${theme.id}`);

    // Create theme-specific manifest.json
    const manifest = {
      name: `Astral Notes (${theme.name})`,
      short_name: "Astral Notes",
      description: "A beautiful, privacy-first notes app with zero-knowledge vault encryption and cross-platform sync.",
      start_url: `/?theme=${theme.id}`,
      display: "standalone",
      background_color: theme.bgDark,
      theme_color: theme.primary,
      orientation: "any",
      categories: ["productivity", "utilities"],
      icons: [
        {
          src: `/icons/${theme.id}/icon.svg`,
          type: "image/svg+xml",
          sizes: "any"
        },
        {
          src: `/icons/${theme.id}/icon-192.png`,
          type: "image/png",
          sizes: "192x192",
          purpose: "any"
        },
        {
          src: `/icons/${theme.id}/icon-512.png`,
          type: "image/png",
          sizes: "512x512",
          purpose: "any"
        },
        {
          src: `/icons/${theme.id}/icon-maskable-192.png`,
          type: "image/png",
          sizes: "192x192",
          purpose: "maskable"
        },
        {
          src: `/icons/${theme.id}/icon-maskable-512.png`,
          type: "image/png",
          sizes: "512x512",
          purpose: "maskable"
        },
        {
          src: `/icons/${theme.id}/icon-192.svg`,
          type: "image/svg+xml",
          sizes: "192x192",
          purpose: "any"
        },
        {
          src: `/icons/${theme.id}/icon-512.svg`,
          type: "image/svg+xml",
          sizes: "512x512",
          purpose: "any"
        }
      ]
    };

    fs.writeFileSync(path.join(publicDir, `manifest-${theme.id}.json`), JSON.stringify(manifest, null, 2));
  }

  // Also write default manifest.json (Peach)
  const defaultManifest = JSON.parse(fs.readFileSync(path.join(publicDir, 'manifest-peach.json'), 'utf-8'));
  defaultManifest.name = "Astral Notes";
  fs.writeFileSync(path.join(publicDir, 'manifest.json'), JSON.stringify(defaultManifest, null, 2));

  // Copy peach icons as root icons fallback
  const peachDir = path.join(iconsBaseDir, 'peach');
  fs.copyFileSync(path.join(peachDir, 'icon-192.png'), path.join(iconsBaseDir, 'icon-192.png'));
  fs.copyFileSync(path.join(peachDir, 'icon-512.png'), path.join(iconsBaseDir, 'icon-512.png'));
  fs.copyFileSync(path.join(peachDir, 'icon-maskable-192.png'), path.join(iconsBaseDir, 'icon-maskable-192.png'));
  fs.copyFileSync(path.join(peachDir, 'icon-maskable-512.png'), path.join(iconsBaseDir, 'icon-maskable-512.png'));

  console.log('All theme icons and manifests successfully generated!');
}

run().catch(console.error);
