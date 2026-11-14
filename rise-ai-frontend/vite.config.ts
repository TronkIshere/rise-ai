import { fileURLToPath, URL } from 'node:url';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig, type Plugin } from 'vite';
import { readFileSync } from 'node:fs';
import ts from 'typescript';

function readRoutePaths() {
  const routerFile = new URL('./src/app/router.tsx', import.meta.url);
  const source = ts.createSourceFile('router.tsx', readFileSync(routerFile, 'utf8'), ts.ScriptTarget.Latest, true);
  const paths: string[] = [];
  function visit(node: ts.Node) {
    if (ts.isPropertyAssignment(node) && ts.isIdentifier(node.name) && node.name.text === 'path') {
      if (!ts.isStringLiteral(node.initializer)) throw new Error('First paint requires literal router paths');
      if (node.initializer.text !== '*') paths.push(node.initializer.text);
    }
    ts.forEachChild(node, visit);
  }
  visit(source);
  return paths;
}

function wildcardFirstPaint(): Plugin {
  // Use the browser router's static and required-parameter paths before React mounts.
  const routes = readRoutePaths().map(path =>
    path.split('/').map(segment =>
      segment.startsWith(':') ? '[^/]+' : segment.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'),
    ).join('/'),
  );
  const routePattern = JSON.stringify('^(?:' + routes.join('|') + ')/*$');
  return {
    name: 'wildcard-first-paint',
    transformIndexHtml: {
      order: 'pre',
      handler: () => [
        {
          tag: 'style',
          children: 'html[data-rise-wildcard-entry]:has(#root:empty){background:#082d25}',
          injectTo: 'head-prepend',
        },
        {
          tag: 'script',
          children: `(() => {
            let path = location.pathname;
            try {
              path = path.split('/').map(segment => decodeURIComponent(segment).replace(/\\//g, '%2F')).join('/');
            } catch {
              // React Router matches the original pathname if a segment cannot be decoded.
            }
            if (!new RegExp(${routePattern}, 'i').test(path)) {
              document.documentElement.setAttribute('data-rise-wildcard-entry', '');
            }
          })();`,
          injectTo: 'head-prepend',
        },
      ],
    },
  };
}

export default defineConfig({
  plugins: [react(), tailwindcss(), wildcardFirstPaint()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': { target: 'http://127.0.0.1:8080', changeOrigin: true } },
  },
});
