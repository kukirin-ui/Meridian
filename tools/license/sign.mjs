#!/usr/bin/env node
// Issues a Meridian licence key.
//
//   node sign.mjs --ref ORDER-1234 [--key keys/private.pem] [--sku pro|premium] [--copies 2]
//
// --copies defaults to 1. With 2 or more, each line is a different key and a different ref
// (ORDER-1, ORDER-2, …). One €15 sale uses --copies 2: one key for the buyer, one for a friend.
//
// Output format (what the customer pastes into the app):
//   MERIDIAN-<base64url payload>.<base64url signature>
// payload = {"sku":"pro"|"premium","iat":<unix seconds>,"ref":"ORDER-1234"}
// signature = SHA256withECDSA over the raw payload bytes, DER encoded
// (matches java.security.Signature "SHA256withECDSA" in MainActivity.activate).
import { createSign, createPrivateKey } from "node:crypto";
import { readFileSync } from "node:fs";
import { join } from "node:path";

const args = Object.fromEntries(
  process.argv.slice(2).reduce((acc, a, i, arr) => {
    if (a.startsWith("--")) acc.push([a.slice(2), arr[i + 1] && !arr[i + 1].startsWith("--") ? arr[i + 1] : "true"]);
    return acc;
  }, [])
);

const ref = args.ref;
if (!ref) {
  console.error("Usage: node sign.mjs --ref <order id> [--key keys/private.pem] [--sku pro|premium] [--copies 2]");
  process.exit(1);
}
const keyPath = args.key || join(import.meta.dirname, "keys", "private.pem");
const sku = args.sku || "pro";
if (sku !== "pro" && sku !== "premium") {
  console.error("sku must be pro or premium");
  process.exit(1);
}

const copies = args.copies == null ? 1 : Number(args.copies);
if (!Number.isInteger(copies) || copies < 1 || copies > 20) {
  console.error("copies must be a whole number from 1 to 20");
  process.exit(1);
}

const key = createPrivateKey(readFileSync(keyPath));
const b64url = (b) => b.toString("base64url");
const iat = Math.floor(Date.now() / 1000);
for (let n = 1; n <= copies; n++) {
  const copyRef = copies === 1 ? ref : `${ref}-${n}`;
  const payload = Buffer.from(JSON.stringify({ sku, iat, ref: copyRef }), "utf8");
  const signer = createSign("SHA256");
  signer.update(payload);
  const sig = signer.sign({ key, dsaEncoding: "der" });
  console.log(`MERIDIAN-${b64url(payload)}.${b64url(sig)}`);
}
