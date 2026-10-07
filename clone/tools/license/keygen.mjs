#!/usr/bin/env node
// Generates the P-256 signing keypair for Meridian Pro licence keys.
//
//   node keygen.mjs [outDir]
//
// Writes <outDir>/private.pem (keep secret, never commit) and prints the
// base64 SPKI public key to paste into MainActivity.LICENSE_PUBKEY.
import { generateKeyPairSync } from "node:crypto";
import { mkdirSync, writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";

const outDir = process.argv[2] || join(import.meta.dirname, "keys");
mkdirSync(outDir, { recursive: true });
const privPath = join(outDir, "private.pem");
if (existsSync(privPath)) {
  console.error(`Refusing to overwrite ${privPath}. Delete it first if you really want a new keypair.`);
  process.exit(1);
}

const { publicKey, privateKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
writeFileSync(privPath, privateKey.export({ type: "pkcs8", format: "pem" }), { mode: 0o600 });
const spki = publicKey.export({ type: "spki", format: "der" }).toString("base64");
writeFileSync(join(outDir, "public.b64"), spki + "\n");

console.log(`Private key: ${privPath}  (back it up offline — losing it means re-issuing every key)`);
console.log("");
console.log("Paste this into clone/app/src/main/java/com/meridian/shell/MainActivity.java:");
console.log("");
console.log(`    private static final String LICENSE_PUBKEY = "${spki}";`);
