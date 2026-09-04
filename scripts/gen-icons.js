const { createCanvas } = require('canvas');
const fs = require('fs');
const path = require('path');

const SVG_PATH = 'M18 2l4 4M2 22l1.276-4.68c.083-.305.125-.458.189-.6.057-.127.126-.247.208-.359.092-.126.204-.238.428-.462L14.434 5.566c.198-.198.297-.297.411-.334.1-.033.209-.033.309 0 .114.037.213.136.411.334l2.869 2.869c.198.198.297-.297.334.411.033.1.033.209 0 .309-.037.114-.136.213-.334.411L8.1 19.899c-.224.224-.336.336-.462.428-.112.082-.232.151-.359.208-.142.064-.295.106-.6.189L2 22z';

function generateIcon(size, color, padding, outputPath) {
  const canvas = createCanvas(size, size);
  const ctx = canvas.getContext('2d');

  ctx.fillStyle = '#1e1517';
  ctx.fillRect(0, 0, size, size);

  const scale = (size - padding * 2) / 24;
  ctx.translate(padding, padding);
  ctx.scale(scale, scale);
  ctx.translate(1.2, 1.2);
  ctx.scale(0.9, 0.9);

  ctx.strokeStyle = color;
  ctx.lineWidth = 2.2;
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  ctx.fillStyle = 'none';

  const p = new Path2D(SVG_PATH);
  ctx.stroke(p);

  const buffer = canvas.toBuffer('image/png');
  fs.writeFileSync(outputPath, buffer);
  console.log(`Generated: ${outputPath}`);
}

const outDir = path.join(__dirname, '..', 'web', 'public', 'icons');

generateIcon(192, '#f0788a', 16, path.join(outDir, 'icon-192.png'));
generateIcon(512, '#f0788a', 40, path.join(outDir, 'icon-512.png'));
generateIcon(192, '#f0788a', 32, path.join(outDir, 'icon-maskable-192.png'));
generateIcon(512, '#f0788a', 80, path.join(outDir, 'icon-maskable-512.png'));
