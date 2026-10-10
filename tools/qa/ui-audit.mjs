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
assert.match(html, /animation: latpulse 2\.8s var\(--ease\) infinite/);
assert.equal(/pulse-ring/.test(html), false);
assert.match(html, /\.spark \{ width: calc\(100% \+ 32px\); height: 64px; margin: 8px -16px 0;/);
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

const plate = html.match(/\.plate\.freq \{[^}]*padding:\s*16px 16px 0/);
assert.ok(plate, "frequency plate horizontal padding is 16px, cancelled by the spark margin");
assert.equal(/linear-gradient\(var\(--grid\)/.test(html), false, "square grid background must be gone");
assert.equal(/#D4AF37/.test(html), false, "termux rules follow the accent, not a fixed gold");
assert.equal(/#00FFC4/.test(html), false, "buttons follow the accent, not a fixed green");
assert.equal(/Top 10/.test(html), false);
assert.match(html, /latpulse/);
assert.match(html, /data-density/);
assert.match(html, /id="pagetitle"/);
assert.equal(/packCard\("Install"/.test(html), false, "Build does not show the Install card");
assert.match(html, /packCard\("Workstation"/);
assert.equal(/nth-child\(1\):last-child/.test(html), false, "a single tile stays one cell of the three-column grid");
assert.match(html, /id="signal"/);
assert.match(html, /function paintSignal/);
assert.match(html, /Contact Support/);
assert.match(html, /Python\+Pip/);
assert.match(html, /Docker CLI/);
assert.equal(/id="sharereport"/.test(sliceFn("aboutPage()")), false, "About must not export hardware");
assert.equal(/thisPhoneIsPro|trialLeft|freeVersion/.test(sliceFn("aboutPage()")), false, "About must not show a subscription");
const java = readFileSync(join(root, "app/src/main/java/com/meridian/shell/MainActivity.java"), "utf8");
const manifest = readFileSync(join(root, "app/src/main/AndroidManifest.xml"), "utf8");
assert.match(manifest, /android:hardwareAccelerated="true"/);
assert.match(manifest, /android:windowSoftInputMode="adjustResize"/);
assert.match(java, /FLAG_DEBUGGABLE/);
assert.match(java, /showSoftInput/);
assert.match(java, /hideSoftInputFromWindow/);
assert.equal(/setOnTouchListener/.test(java), false, "taps outside a text field must not focus the WebView");
assert.match(java, /dev\.unlock/);
assert.match(manifest, /android:configChanges="[^"]*orientation\|screenSize/);
assert.match(java, /clipOut/);
assert.match(java, /printf -- %s /);
assert.match(java, /refuseCmd/);
assert.match(java, /signalBars/);
assert.match(java, /BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE/);
assert.match(java, /SYSTEM_UI_FLAG_IMMERSIVE_STICKY/);
assert.match(manifest, /ACCESS_WIFI_STATE/);
assert.match(html, /function clipOut/);
assert.match(html, /\[earlier output trimmed\]/);
assert.match(html, /m-draft/);
assert.match(html, /m-goal/);
assert.match(html, /\.ico em \{[^}]*color: var\(--icon\)/);
assert.match(html, /\.kv::before,\s*\n\s*\.rowline::after[\s\S]*?animation: latpulse 2\.8s var\(--ease\) infinite/);
assert.equal(/\.page\.readings \.kv/.test(html), false, "row lines pulse on every page");
assert.match(html, /@keyframes tileglow/);
assert.match(html, /\.ico \{[^}]*animation: tileglow 2\.8s var\(--ease\) infinite/);
assert.match(html, /\.win \{[^}]*animation: tileglow 2\.8s var\(--ease\) infinite/);
assert.equal(/\.ico::before \{[^}]*latpulse/.test(html), false, "tile pulse must not fade the icon");
assert.equal(/\.win::before/.test(html), false, "window frame must not cover the page");
assert.equal(/termLog\(/.test(sliceFn("planPage()")), false, "Plan stays a form, not a console dump");
assert.equal(/planmark/.test(sliceFn("planPage()")), false, "Plan does not repeat the logo");
assert.equal(/data-more="about"/.test(sliceFn("toolsPage()")), false, "About lives in the gear, not a fourth grid");
assert.match(sliceFn("toolsPage()"), /items<\/div>/);
assert.match(sliceFn("linksPage()"), /items<\/div>/);
assert.equal(/meridian-logo\.png/.test(html), false);
assert.equal(/meridian-mark\.png/.test(html), false);
assert.match(html, /function meridianMark/);

console.log("ui audit ok");
