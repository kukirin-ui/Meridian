#!/usr/bin/env node
// Static checks for the Meridian overhaul: symmetry, duplicates, and graph updates.
import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = join(import.meta.dirname, "..", "..");
const html = readFileSync(join(root, "app/src/main/assets/www/index.html"), "utf8");

function sliceFn(name) {
  const start = html.indexOf("function " + name);
  assert.ok(start >= 0, "missing " + name);
  const brace = html.indexOf("{", start);
  let depth = 0;
  for (let i = brace; i < html.length; i++) {
    if (html[i] === "{") depth++;
    else if (html[i] === "}") {
      depth--;
      if (depth === 0) return html.slice(start, i + 1);
    }
  }
  throw new Error("unclosed " + name);
}

const system = sliceFn("systemPage()");
assert.equal(/EGL|Vulkan|Vendor/.test(system), false, "graphics rows must not render on System");

const home = sliceFn("homePage()");
assert.equal(/LeadBAT|data-k="Battery"|Connected to Termux/.test(home), false);
assert.match(home, /speedometers/);
assert.match(home, /grid-template-columns:repeat\(' \+ gauges\.length/);
assert.match(home, /id="sparkfill"/);
assert.match(home, /id="sparkline"/);

const paint = sliceFn("paintGraph()");
assert.equal(/innerHTML/.test(paint), false, "graph paint must update path attributes");
assert.match(paint, /setAttribute\("d"/);

assert.match(html, /function uniqueParts/);
assert.match(html, /commandPh:"Query the terminal or enter a command\.\.\."/);
assert.match(html, /animation: pulse-ring 2s cubic-bezier\(0\.4, 0, 0\.6, 1\) infinite/);
assert.match(html, /\.spark \{ width: calc\(100% \+ 28px\); height: 64px; margin: 8px -14px 0;/);
assert.match(html, /\.speedometers \{ display: grid; grid-template-columns: minmax\(0, 1fr\) minmax\(0, 1fr\)/);
assert.equal(/What is a star/i.test(html), false);
assert.equal(/packCard\("Linux PC"/.test(html), false);

const security = sliceFn("securityPage()");
assert.match(security, /does not scan other machines/);
assert.match(security, /does not capture packets/);

const pay = sliceFn("payPage()");
assert.equal((pay.match(/id="pay"/g) || []).length, 1);
assert.equal(/premiumCheckout/.test(pay), false);
assert.match(pay, /roleFree/);
assert.match(pay, /rolePremium/);

assert.equal((html.match(/data-go="settings"/g) || []).length, 1);
assert.equal(/const about = \(view === "tools"/.test(html), false, "Termux menu must not inject a second About row");
assert.match(html, /NAV_TABS = \["home", "hardware", "system", "battery", "security", "build", "plan", "apis", "tools"\]/);
assert.match(html, /MERIDIAN_MARK/);
assert.equal(/const PRO = \{[^}]*plan:/.test(html), false, "Plan stays on the free tier");
assert.match(html, /function apiSecret/);
assert.match(html, /savePlatforms/);
assert.equal(/localStorage\.setItem\("m-key"/.test(html), false);

const plate = html.match(/\.plate\.freq \{[^}]*padding:\s*14px 14px 0/);
assert.ok(plate, "frequency plate horizontal padding is 14px, cancelled by the spark margin");

console.log("ui audit ok");
