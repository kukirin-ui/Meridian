#!/usr/bin/env node
// Asserts the paid/full/unlocked backdoor is gone and the window logo is one SVG.
import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = join(import.meta.dirname, "..", "..");
const java = readFileSync(join(root, "app/src/main/java/com/meridian/shell/MainActivity.java"), "utf8");
const html = readFileSync(join(root, "app/src/main/assets/www/index.html"), "utf8");

function method(name) {
  const start = java.indexOf(name);
  assert.ok(start >= 0, "missing " + name);
  const brace = java.indexOf("{", start);
  let depth = 0;
  for (let i = brace; i < java.length; i++) {
    if (java[i] === "{") depth++;
    else if (java[i] === "}") {
      depth--;
      if (depth === 0) return java.slice(start, i + 1);
    }
  }
  throw new Error("unclosed " + name);
}

const activate = method("public String activate(String key)");
assert.equal(/equals\(\s*"paid"\s*\)/.test(activate), false, "activate still treats paid as success");
assert.equal(/equalsIgnoreCase\(\s*"full"\s*\)/.test(activate), false);
assert.equal(/equalsIgnoreCase\(\s*"unlocked"\s*\)/.test(activate), false);
assert.match(activate, /"pro"\.equals\(sku\)/);
assert.match(activate, /"premium"\.equals\(sku\)/);
assert.match(activate, /markPaid\(sku\)/);

const adopt = method("private void adoptLegacyUnlock()");
assert.match(adopt, /text\.equals\("paid"\)/);
assert.match(adopt, /continue/);
assert.equal(/markPaid\(/.test(adopt), false, "adopt must not mint a licence; activate() verifies");
assert.match(adopt, /new Deck\(\)\.activate\(text\)/);
assert.match(adopt, /if \(local\.isFile\(\) && local\.length\(\) > 0\) return/);

const unlocked = method("public String unlocked()");
assert.equal(/delete\(/.test(unlocked), false, "unlocked must not delete full.key");

function shapeRejects(key) {
  let k = String(key || "").trim().replace(/\r|\n| /g, "");
  if (k.startsWith("MERIDIAN-")) k = k.slice(9);
  return k.indexOf(".") <= 0;
}
for (const word of ["paid", "full", "unlocked", "PAID", "Full", "Unlocked"]) {
  assert.equal(shapeRejects(word), true, word + " must not look like a key");
}

const logos = html.match(/const LOGO_SVG = /g) || [];
assert.equal(logos.length, 1, "LOGO_SVG must be defined once");
const uses = html.match(/LOGO_SVG/g) || [];
assert.ok(uses.length >= 5, "LOGO_SVG should be reused on every chrome");
assert.equal(/class="land"|globe-map|globeHtml/.test(html), false, "planet globe is still in the UI");
assert.match(html, /class="termorb"/);
assert.match(html, /const PREMIUM_ON = false/);
assert.match(html, /pricePro:"€15 once"/);
assert.match(html, /Buy Pro · €15/);
assert.match(html, /two Pro keys: one for you/);
assert.match(html, /id="dlcount"/);
assert.match(html, /m-downloads/);
const downloads = method("private String apkDownloadTotal()");
assert.match(downloads, /https:\/\/api\.github\.com\/repos\/kukirin-ui\/Meridian\/releases/);
assert.match(downloads, /User-Agent", "Meridian"/);
assert.equal(/Build\.MODEL|LICENSE|Authorization|phone/.test(downloads), false);
const signer = readFileSync(join(root, "tools/license/sign.mjs"), "utf8");
assert.match(signer, /args\.copies == null \? 1/);
assert.match(signer, /\$\{ref\}-\$\{n\}/);
assert.equal(/1,000,000|5,000,000|€1 –/.test(html), false, "joke price range must not ship in the UI");
const payFn = html.slice(html.indexOf("function payPage()"), html.indexOf("window.onPaid"));
assert.equal(/paypremium|premiumCheckout|tierPremium/.test(payFn), false, "pay page must not sell Premium");
assert.equal((html.match(/data-go="settings"/g) || []).length, 1, "settings entry must be the header gear only");
assert.equal((html.match(/data-pref="lang"/g) || []).length, 0);
assert.match(html, /seg\("lang", LANGS\)/);

const licensed = html.slice(html.indexOf("function payPage()"), html.indexOf("window.onPaid"));
const thanks = licensed.slice(0, licensed.indexOf("let html"));
assert.match(thanks, /thisPhoneIsPro/);
assert.match(thanks, /thankYouShort/);
assert.equal(/lickey|pastePaid|activate/.test(thanks), false, "a Pro phone must not see the licence form");

console.log("gates ok");
