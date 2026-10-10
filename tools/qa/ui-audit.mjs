#!/usr/bin/env node
// The phone UI is the mesh terminal packaged from the Ultimate sandbox.
import { readFileSync } from "node:fs";
import { join } from "node:path";
import assert from "node:assert/strict";

const root = join(import.meta.dirname, "..", "..");
const html = readFileSync(join(root, "app/src/main/assets/www/index.html"), "utf8");
const java = readFileSync(join(root, "app/src/main/java/com/meridian/shell/MainActivity.java"), "utf8");
const manifest = readFileSync(join(root, "app/src/main/AndroidManifest.xml"), "utf8");

assert.match(html, /window\.meridian/);
assert.match(html, /onNativeSpeechResult/);
assert.match(html, /onHardwareSignature/);
assert.match(html, /requestBiometricSignature/);
assert.match(html, /config\.json/);
assert.match(html, /The microphone listens only while this screen is open/);
assert.equal(/setOnTouchListener/.test(java), false, "taps must not install a touch listener");
assert.equal(/request\.grant\(request\.getResources\(\)\)/.test(java), false, "the page must not receive every WebView permission");
assert.match(java, /RESOURCE_AUDIO_CAPTURE/);
assert.match(java, /onPause/);
assert.match(java, /stopListening/);
assert.match(java, /listenGen/);
assert.match(manifest, /RECORD_AUDIO/);
assert.match(manifest, /android:hardwareAccelerated="true"/);
assert.match(manifest, /android:windowSoftInputMode="adjustResize"/);
assert.equal(/WRITE_EXTERNAL_STORAGE/.test(manifest), false, "the mesh screen does not read storage");

console.log("ui audit ok");
