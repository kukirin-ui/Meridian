#!/usr/bin/env node
// Local desktop companion for Meridian.
// Readings come from this machine. The HTTP port only serves those readings on
// 127.0.0.1. A shell is available on standard input with --tty, never over HTTP.
import { readFileSync } from "node:fs";
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import os from "node:os";

function readText(path) {
  try { return readFileSync(path, "utf8"); } catch (e) { return ""; }
}

function meminfo() {
  const text = readText("/proc/meminfo");
  function grab(key) {
    const match = text.match(new RegExp("^" + key + ":\\s+(\\d+)", "m"));
    return match ? Number(match[1]) * 1024 : 0;
  }
  const total = grab("MemTotal") || os.totalmem();
  const free = grab("MemAvailable") || grab("MemFree") || os.freemem();
  return { total: total, free: free };
}

function snapshot() {
  const mem = meminfo();
  return {
    host: os.hostname(),
    platform: os.platform(),
    arch: os.arch(),
    cpus: os.cpus().length,
    load: os.loadavg(),
    uptimeSec: Math.round(os.uptime()),
    memTotal: mem.total,
    memFree: mem.free,
    note: "This process reads the local machine. It does not scan other hosts and it does not capture packets."
  };
}

function printSnapshot() {
  const s = snapshot();
  const used = s.memTotal > 0 ? Math.round(100 * (s.memTotal - s.memFree) / s.memTotal) : 0;
  console.log("Meridian desktop");
  console.log(s.host + "  " + s.platform + " " + s.arch + "  " + s.cpus + " cores");
  console.log("Load " + s.load.map(function (n) { return n.toFixed(2); }).join(" ") + "   RAM " + used + "%");
  console.log(s.note);
}

const args = process.argv.slice(2);
if (args.indexOf("--tty") >= 0) {
  const shell = process.platform === "win32" ? (process.env.COMSPEC || "cmd.exe") : (process.env.SHELL || "/bin/bash");
  const child = spawn(shell, [], { stdio: "inherit" });
  child.on("exit", function (code) { process.exit(code == null ? 1 : code); });
} else if (args.indexOf("--serve") >= 0) {
  const port = Number(process.env.MERIDIAN_PORT || 8765);
  const server = createServer(function (req, res) {
    const host = req.socket.localAddress || "";
    if (host !== "127.0.0.1" && host !== "::1") {
      res.writeHead(403, { "content-type": "text/plain" });
      res.end("local only");
      return;
    }
    if (req.url === "/" || req.url === "/metrics") {
      res.writeHead(200, { "content-type": "application/json; charset=utf-8" });
      res.end(JSON.stringify(snapshot()));
      return;
    }
    res.writeHead(404, { "content-type": "text/plain" });
    res.end("not found");
  });
  server.listen(port, "127.0.0.1", function () {
    console.log("Meridian desktop metrics on http://127.0.0.1:" + port + "/metrics");
  });
} else {
  printSnapshot();
}
