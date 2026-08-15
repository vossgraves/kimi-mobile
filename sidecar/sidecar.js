#!/usr/bin/env node
/**
 * Kimi Mobile sidecar.
 *
 * Android sandboxes apps: no spawning processes, no npx, no MCP stdio servers.
 * Termux/Debian has none of those limits. So the capabilities that can't live
 * in the APK live here instead, and the app reaches them over localhost —
 * exactly how it already talks to the Kimi proxy.
 *
 *   node sidecar.js            # listens on 127.0.0.1:8777
 *   PORT=9000 node sidecar.js
 *
 * Endpoints
 *   GET  /health                      capability probe
 *   GET  /skills                      installed skills (SKILL.md files)
 *   POST /skills/install  {source}    npx skills add / git clone
 *   POST /skills/read     {id}        full SKILL.md text for the prompt
 *   GET  /mcp                         configured MCP servers
 *   POST /mcp/add         {name,cmd}  register a stdio server
 *   POST /mcp/tools       {name}      list that server's tools
 *   POST /mcp/call        {name,tool,args}
 *   POST /shell           {cmd}       run a command (allowlisted)
 *   POST /fetch           {url}       fetch a page as text
 */

const http = require('http');
const { spawn, execFile } = require('child_process');
const fs = require('fs');
const path = require('path');
const os = require('os');

const PORT = Number(process.env.PORT || 8777);
const HOME = os.homedir();

// Where agents conventionally keep skills — same paths opencode reads.
const SKILL_DIRS = [
  path.join(HOME, '.config/opencode/skills'),
  path.join(HOME, '.claude/skills'),
  path.join(HOME, '.agents/skills'),
  path.join(process.cwd(), '.opencode/skills'),
];

const CONFIG_PATH = path.join(HOME, '.kimi-mobile-sidecar.json');

function loadConfig() {
  try {
    return JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
  } catch {
    return { mcpServers: {} };
  }
}

function saveConfig(cfg) {
  fs.writeFileSync(CONFIG_PATH, JSON.stringify(cfg, null, 2));
}

// ---- Skills -----------------------------------------------------------------

/** Parses the YAML front matter every SKILL.md carries. */
function parseSkill(file) {
  const raw = fs.readFileSync(file, 'utf8');
  const fm = raw.match(/^---\n([\s\S]*?)\n---/);
  const meta = {};
  if (fm) {
    for (const line of fm[1].split('\n')) {
      const m = line.match(/^(\w[\w-]*):\s*(.*)$/);
      if (m) meta[m[1]] = m[2].replace(/^["']|["']$/g, '');
    }
  }
  return {
    id: meta.name || path.basename(path.dirname(file)),
    name: meta.name || path.basename(path.dirname(file)),
    description: meta.description || '',
    path: file,
    // The body is what actually steers the model.
    body: raw.replace(/^---\n[\s\S]*?\n---\n?/, ''),
  };
}

function listSkills() {
  const found = [];
  for (const dir of SKILL_DIRS) {
    if (!fs.existsSync(dir)) continue;
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (!entry.isDirectory()) continue;
      const file = path.join(dir, entry.name, 'SKILL.md');
      if (fs.existsSync(file)) {
        try {
          const s = parseSkill(file);
          if (!found.some((x) => x.id === s.id)) {
            found.push({ id: s.id, name: s.name, description: s.description, path: s.path });
          }
        } catch {
          /* skip unreadable skill */
        }
      }
    }
  }
  return found;
}

function run(cmd, args, opts = {}) {
  return new Promise((resolve) => {
    execFile(cmd, args, { timeout: opts.timeout || 180000, maxBuffer: 8 * 1024 * 1024 },
      (err, stdout, stderr) => {
        resolve({ ok: !err, stdout: String(stdout || ''), stderr: String(stderr || err?.message || '') });
      });
  });
}

async function installSkill(source) {
  const target = SKILL_DIRS[0];
  fs.mkdirSync(target, { recursive: true });
  // Prefer the official installer; fall back to a clone for bare repos.
  const viaNpx = await run('npx', ['-y', 'skills', 'add', source], { timeout: 300000 });
  if (viaNpx.ok) return viaNpx;
  const name = source.split('/').pop().replace(/\.git$/, '');
  return run('git', ['clone', '--depth', '1', source, path.join(target, name)], { timeout: 300000 });
}

// ---- MCP (stdio JSON-RPC) ----------------------------------------------------

/** Talks to an MCP server over stdio for one request/response exchange. */
function mcpRequest(command, args, payloads) {
  return new Promise((resolve) => {
    const child = spawn(command, args, { stdio: ['pipe', 'pipe', 'pipe'] });
    let buf = '';
    const results = [];
    const timer = setTimeout(() => {
      child.kill();
      resolve({ ok: false, error: 'MCP server timed out', results });
    }, 45000);

    child.stdout.on('data', (d) => {
      buf += d.toString();
      let idx;
      while ((idx = buf.indexOf('\n')) >= 0) {
        const line = buf.slice(0, idx).trim();
        buf = buf.slice(idx + 1);
        if (!line) continue;
        try {
          results.push(JSON.parse(line));
        } catch {
          /* server chatter, ignore */
        }
        if (results.length >= payloads.length) {
          clearTimeout(timer);
          child.kill();
          resolve({ ok: true, results });
          return;
        }
      }
    });
    child.on('error', (e) => {
      clearTimeout(timer);
      resolve({ ok: false, error: e.message, results });
    });

    for (const p of payloads) child.stdin.write(JSON.stringify(p) + '\n');
  });
}

const INIT = {
  jsonrpc: '2.0',
  id: 1,
  method: 'initialize',
  params: {
    protocolVersion: '2025-06-18',
    capabilities: {},
    clientInfo: { name: 'kimi-mobile-sidecar', version: '1.0' },
  },
};

// ---- Shell (allowlisted) -----------------------------------------------------

// Read-only-ish commands the agent may use. Anything destructive stays out;
// the phone is not a scratch VM.
const ALLOWED = new Set([
  'ls', 'cat', 'head', 'tail', 'grep', 'rg', 'find', 'wc', 'file', 'stat',
  'git', 'node', 'python3', 'pip', 'npm', 'npx', 'curl', 'jq', 'echo', 'pwd',
  'date', 'uname', 'df', 'du', 'which', 'tree', 'sed', 'awk', 'sort', 'uniq',
]);

async function shell(cmdline) {
  const bin = cmdline.trim().split(/\s+/)[0];
  if (!ALLOWED.has(bin)) {
    return { ok: false, stderr: `"${bin}" is not allowed. Permitted: ${[...ALLOWED].join(', ')}` };
  }
  return run('bash', ['-lc', cmdline], { timeout: 120000 });
}

// ---- HTTP --------------------------------------------------------------------

function send(res, code, body) {
  const data = JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': 'application/json',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
  });
  res.end(data);
}

function readBody(req) {
  return new Promise((resolve) => {
    let b = '';
    req.on('data', (c) => (b += c));
    req.on('end', () => {
      try {
        resolve(b ? JSON.parse(b) : {});
      } catch {
        resolve({});
      }
    });
  });
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, {});
  const url = new URL(req.url, `http://localhost:${PORT}`);
  const body = req.method === 'POST' ? await readBody(req) : {};

  try {
    switch (`${req.method} ${url.pathname}`) {
      case 'GET /health': {
        const node = await run('node', ['-v']);
        const git = await run('git', ['--version']);
        return send(res, 200, {
          ok: true,
          version: '1.0',
          host: os.platform(),
          node: node.stdout.trim(),
          git: git.stdout.trim(),
          skills: listSkills().length,
          mcpServers: Object.keys(loadConfig().mcpServers).length,
        });
      }

      case 'GET /skills':
        return send(res, 200, { skills: listSkills() });

      case 'POST /skills/install': {
        if (!body.source) return send(res, 400, { error: 'source required' });
        const out = await installSkill(body.source);
        return send(res, out.ok ? 200 : 500, { ...out, skills: listSkills() });
      }

      case 'POST /skills/read': {
        const skill = listSkills().find((s) => s.id === body.id);
        if (!skill) return send(res, 404, { error: 'no such skill' });
        return send(res, 200, parseSkill(skill.path));
      }

      case 'GET /mcp':
        return send(res, 200, { servers: loadConfig().mcpServers });

      case 'POST /mcp/add': {
        if (!body.name || !body.command) {
          return send(res, 400, { error: 'name and command required' });
        }
        const cfg = loadConfig();
        cfg.mcpServers[body.name] = { command: body.command, args: body.args || [] };
        saveConfig(cfg);
        return send(res, 200, { servers: cfg.mcpServers });
      }

      case 'POST /mcp/tools': {
        const entry = loadConfig().mcpServers[body.name];
        if (!entry) return send(res, 404, { error: 'unknown server' });
        const out = await mcpRequest(entry.command, entry.args, [
          INIT,
          { jsonrpc: '2.0', id: 2, method: 'tools/list' },
        ]);
        const tools = out.results?.find((r) => r.id === 2)?.result?.tools || [];
        return send(res, out.ok ? 200 : 500, { tools, error: out.error });
      }

      case 'POST /mcp/call': {
        const entry = loadConfig().mcpServers[body.name];
        if (!entry) return send(res, 404, { error: 'unknown server' });
        const out = await mcpRequest(entry.command, entry.args, [
          INIT,
          {
            jsonrpc: '2.0',
            id: 2,
            method: 'tools/call',
            params: { name: body.tool, arguments: body.args || {} },
          },
        ]);
        const result = out.results?.find((r) => r.id === 2)?.result;
        const text = (result?.content || [])
          .map((c) => c.text)
          .filter(Boolean)
          .join('\n');
        return send(res, out.ok ? 200 : 500, { text, raw: result, error: out.error });
      }

      case 'POST /shell': {
        if (!body.cmd) return send(res, 400, { error: 'cmd required' });
        return send(res, 200, await shell(body.cmd));
      }

      case 'POST /fetch': {
        if (!body.url) return send(res, 400, { error: 'url required' });
        const out = await run('curl', ['-sL', '--max-time', '30', body.url]);
        const text = out.stdout
          .replace(/<script[\s\S]*?<\/script>/gi, ' ')
          .replace(/<style[\s\S]*?<\/style>/gi, ' ')
          .replace(/<[^>]+>/g, ' ')
          .replace(/\s+/g, ' ')
          .trim();
        return send(res, 200, { text: text.slice(0, 20000) });
      }

      default:
        return send(res, 404, { error: 'unknown endpoint' });
    }
  } catch (e) {
    return send(res, 500, { error: e.message });
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Kimi Mobile sidecar on http://127.0.0.1:${PORT}`);
  console.log(`skills: ${listSkills().length} · mcp: ${Object.keys(loadConfig().mcpServers).length}`);
});
