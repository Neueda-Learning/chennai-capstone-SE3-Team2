// The OpenAPI generator reads a spec's path as a URI, so a space in it -- as
// in Contracts/API Schemas/, the folder name the deployment standard fixes --
// breaks every $ref. Each spec is copied to a scratch path with no space in
// it, and the generator is pointed there. The specs reference no other file,
// so a copy on its own resolves exactly as the original does.
import { copyFileSync, mkdirSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';

export function stageSpecs(generators, root, scratch) {
  const specs = join(scratch, 'specs');
  mkdirSync(specs, { recursive: true });
  for (const [key, generator] of Object.entries(generators)) {
    const staged = join(specs, `${key}-${basename(generator.inputSpec).replace(/\s+/g, '-')}`);
    copyFileSync(resolve(root, generator.inputSpec), staged);
    generator.inputSpec = staged;
  }
}
