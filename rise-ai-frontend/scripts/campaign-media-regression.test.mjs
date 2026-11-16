import assert from 'node:assert/strict';
import test from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';

test('campaign media filters the verified source and renders the corresponding provenance', async () => {
  const server = await createServer({
    configFile: false,
    esbuild: { jsx: 'automatic' },
    optimizeDeps: { noDiscovery: true, include: [] },
    resolve: { alias: { '@': fileURLToPath(new URL('../src', import.meta.url)) } },
    server: { middlewareMode: true, ws: false },
    appType: 'custom',
  });
  const previousWindow = globalThis.window;
  globalThis.window = { location: { origin: 'https://rise.test' } };
  try {
    const { usableCampaignImageUrl } = await server.ssrLoadModule('/src/lib/campaign-display.ts');
    const { CampaignMedia } = await server.ssrLoadModule('/src/components/ui/CampaignMedia.tsx');
    const placeholder = 'https://PLACEHOLD.CO./1200x630/png?text=campaign';
    const genuine = 'https://media.example.org/demo/field-photo.jpg';
    assert.equal(usableCampaignImageUrl(placeholder), undefined);
    assert.equal(usableCampaignImageUrl('https://placehold.co.example.org/photo.jpg'), 'https://placehold.co.example.org/photo.jpg');
    assert.equal(usableCampaignImageUrl(genuine), genuine);
    assert.equal(usableCampaignImageUrl('/uploads/photo.jpg'), 'https://rise.test/uploads/photo.jpg');
    assert.equal(usableCampaignImageUrl('http://media.example.org/photo.jpg'), 'http://media.example.org/photo.jpg');

    const render = (src) => renderToStaticMarkup(createElement(CampaignMedia, {
      src: usableCampaignImageUrl(src),
      alt: 'Campaign image',
      fallback: createElement('span', { role: 'img', 'aria-label': 'Illustration' }),
      renderCaption: (hasImage) => hasImage ? 'ẢNH TỪ HỒ SƠ' : 'MINH HỌA LĨNH VỰC',
    }));
    for (const source of [placeholder, 'data:image/png;base64,AA==', 'sim://media/photo', 'javascript:alert(1)', undefined]) {
      const html = render(source);
      assert.match(html, /aria-label="Illustration".*MINH HỌA LĨNH VỰC/);
      assert.doesNotMatch(html, /<img|ẢNH TỪ HỒ SƠ/);
    }
    assert.match(render(genuine), /<img[^>]+src="https:\/\/media\.example\.org\/demo\/field-photo\.jpg"[^>]+alt="Campaign image".*ẢNH TỪ HỒ SƠ/);
  } finally {
    if (previousWindow === undefined) delete globalThis.window;
    else globalThis.window = previousWindow;
    await server.close();
  }
});
