import { readdirSync, readFileSync, statSync } from 'node:fs';
import { dirname, extname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const src = resolve(dirname(fileURLToPath(import.meta.url)), '../src');
const errors = [];
const roots = new Set(['app','api','auth','pages','components','lib','styles']);
const allowed = {
  app: ['app','api','auth','pages','components','lib','styles'],
  api: ['api','lib'],
  auth: ['auth','api','lib'],
  pages: ['pages','api','auth','components','lib'],
  'components/ui': ['components/ui','lib'],
  'components/layout': ['components/layout','components/ui','auth','lib'],
  lib: ['lib'],
  main: ['app','styles'],
};
function walk(dir) {
  return readdirSync(dir).flatMap((name) => {
    const path = resolve(dir, name);
    return statSync(path).isDirectory() ? walk(path) : [path];
  });
}
function layer(path) {
  if (path[0] === 'main.tsx') return 'main';
  return path[0] === 'components' ? 'components/' + path[1] : path[0];
}
for (const file of walk(src)) {
  const rel = relative(src, file).split(sep).join('/');
  const parts = rel.split('/');
  const origin = layer(parts);
  if (!roots.has(parts[0]) && rel !== 'main.tsx' && rel !== 'vite-env.d.ts') errors.push(rel + ': unknown layer');
  if (parts[0] === 'components' && !['ui','layout'].includes(parts[1])) errors.push(rel + ': component subgroup forbidden');
  if (['features','entities','controllers','repositories','services','modules'].some((x) => parts.includes(x))) errors.push(rel + ': entity-folder architecture forbidden');
  if (!['.ts','.tsx','.js','.jsx'].includes(extname(file)) || file.endsWith('.d.ts')) continue;
  const code = readFileSync(file, 'utf8');
  if (origin !== 'api' && /\bfetch\s*\(|\baxios\s*\(/.test(code)) errors.push(rel + ': HTTP must stay in api/');
  if (/\bstyle\s*=\s*\{|#[\da-f]{3,8}\b/i.test(code)) errors.push(rel + ': inline style or raw hex');
  const imports = [...code.matchAll(/(?:\bfrom\s*|\bimport\s*\(|\bimport\s*)['"]([^'"]+)['"]/g)];
  for (const [, spec] of imports) {
    if (!spec.startsWith('@/') && !spec.startsWith('.')) continue;
    const target = spec.startsWith('@/') ? resolve(src, spec.slice(2)) : resolve(dirname(file), spec);
    if (!target.startsWith(src + sep)) { errors.push(rel + ': import outside src'); continue; }
    const dest = layer(relative(src, target).split(sep));
    if (!allowed[origin]?.includes(dest) && !(allowed[origin]?.includes('components') && dest.startsWith('components/'))) errors.push(rel + ': forbidden dependency ' + origin + ' -> ' + dest);
  }
}
if (errors.length) {
  errors.forEach((e) => console.error(' - ' + e));
  process.exit(1);
}
console.log('Architecture PASS');
