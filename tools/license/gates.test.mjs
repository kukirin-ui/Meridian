#!/usr/bin/env node
// The mesh shell has no paid/full/unlocked backdoor. Licence signing stays two keys by default.
import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = join(import.meta.dirname, "..", "..");
const java = readFileSync(join(root, "app/src/main/java/com/meridian/shell/MainActivity.java"), "utf8");
const html = readFileSync(join(root, "app/src/main/assets/www/index.html"), "utf8");

assert.equal(/equals\(\s*"paid"\s*\)/.test(java), false, "paid is not a key");
assert.equal(/equalsIgnoreCase\(\s*"full"\s*\)/.test(java), false);
assert.equal(/equalsIgnoreCase\(\s*"unlocked"\s*\)/.test(java), false);
assert.equal(/1,000,000|5,000,000|€1 –/.test(html), false, "joke price range must not ship in the UI");

const signer = readFileSync(join(root, "tools/license/sign.mjs"), "utf8");
assert.match(signer, /args\.copies == null \? 1/);
assert.match(signer, /\$\{ref\}-\$\{n\}/);

console.log("gates ok");
