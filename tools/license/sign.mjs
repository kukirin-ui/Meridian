#!/usr/bin/env node
// Issues a Meridian Pro licence key.
//
//   node sign.mjs --ref ORDER-1234 [--euros 10] [--key keys/private.pem] [--sku pro]
//
// Output format (what the customer pastes into the app):
//   MERIDIAN-<base64url payload>.<base64url signature>
// payload = {"sku":"pro","iat":<unix seconds>,"ref":"ORDER-1234","euros":10}
// euros is optional. Any whole euro from 1 to 1000000 is the same unlock.
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
  console.error("Usage: node sign.mjs --ref <order id> [--euros 1..1000000] [--key keys/private.pem] [--sku pro]");
  process.exit(1);
}
const keyPath = args.key || join(import.meta.dirname, "keys", "private.pem");
const sku = args.sku || "pro";
const body = { sku, iat: Math.floor(Date.now() / 1000), ref };
if (args.euros) {
  const euros = Number(args.euros);
  if (!Number.isInteger(euros) || euros < 1 || euros > 1000000) {
    console.error("euros must be a whole number from 1 to 1000000");
    process.exit(1);
  }
  body.euros = euros;
}

const payload = Buffer.from(JSON.stringify(body), "utf8");
const signer = createSign("SHA256");
signer.update(payload);
const sig = signer.sign({ key: createPrivateKey(readFileSync(keyPath)), dsaEncoding: "der" });

const b64url = (b) => b.toString("base64url");
console.log(`MERIDIAN-${b64url(payload)}.${b64url(sig)}`);
