"""Own local processes and communicate with MCC and the Paper test probe."""

import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import time
from urllib import error, request


class Blocked(RuntimeError):
    """The scenario could not be executed in this environment."""


class Failed(RuntimeError):
    """An action or assertion did not produce the expected behavior."""


def local_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_for(operation, seconds, message, exception=Failed):
    deadline = time.monotonic() + seconds
    while True:
        value = operation()
        if value is not None:
            return value
        if time.monotonic() >= deadline:
            raise exception(message() if callable(message) else message)
        time.sleep(0.25)


class Process:
    def __init__(self, args, cwd, log, env=None):
        self.log = log
        with log.open("ab") as handle:
            self.process = subprocess.Popen(
                [str(arg) for arg in args], cwd=cwd, env=env, stdin=subprocess.PIPE,
                stdout=handle, stderr=subprocess.STDOUT,
            )

    def send(self, line):
        if self.process.poll() is not None:
            raise Blocked(f"Process exited with code {self.process.returncode}. See {self.log}")
        if "\r" in line or "\n" in line:
            raise ValueError("Commands must be a single line")
        self.process.stdin.write(line.encode("utf-8") + b"\n")
        self.process.stdin.flush()

    def stop(self, command=None):
        try:
            if self.process.poll() is None and command is not None:
                try:
                    self.send(command)
                    self.process.wait(timeout=15)
                except (OSError, Blocked, subprocess.TimeoutExpired):
                    pass
            if self.process.poll() is None:
                self.process.terminate()
                try:
                    self.process.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                    self.process.wait()
        finally:
            self.process.stdin.close()

    def check(self):
        if self.process.poll() is not None:
            raise Blocked(f"Process exited with code {self.process.returncode}. See {self.log}")


class Mcp:
    def __init__(self, port, token):
        self.url = f"http://127.0.0.1:{port}/mcp"
        self.token = token
        self.session = None
        self.version = "2025-06-18"
        self.request_id = 0
        # Keep credentials out of a system HTTP proxy.
        self.http = request.build_opener(request.ProxyHandler({}))

    def rpc(self, method, params=None, notify=False, timeout=10):
        self.request_id += 1
        payload = {"jsonrpc": "2.0", "method": method, "params": params or {}}
        if not notify:
            payload["id"] = self.request_id
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "MCP-Protocol-Version": self.version,
        }
        if self.session:
            headers["Mcp-Session-Id"] = self.session
        outgoing = request.Request(self.url, json.dumps(payload).encode("utf-8"), headers)
        with self.http.open(outgoing, timeout=timeout) as response:
            if session := response.headers.get("Mcp-Session-Id"):
                self.session = session
            if notify:
                return None
            if response.headers.get_content_type() == "text/event-stream":
                reply = None
                for line in response:
                    if line.startswith(b"data:"):
                        event = json.loads(line[5:])
                        if event.get("id") == self.request_id:
                            reply = event
                            break
                if reply is None:
                    raise Blocked(f"MCC did not return {method}")
            else:
                reply = json.load(response)
        if "error" in reply:
            raise Blocked(f"MCC {method}: {reply['error']}")
        return reply["result"]

    def initialize(self):
        result = self.rpc("initialize", {
            "protocolVersion": self.version, "capabilities": {},
            "clientInfo": {"name": "mgc-game-test", "version": "1.0"},
        }, timeout=2)
        self.version = result["protocolVersion"]
        self.rpc("notifications/initialized", notify=True)
        self.tools = {}
        params = {}
        while True:
            result = self.rpc("tools/list", params)
            self.tools.update((tool["name"], tool["inputSchema"]) for tool in result["tools"])
            if not result.get("nextCursor"):
                break
            params = {"cursor": result["nextCursor"]}
        return self.call("mcc_session_status", {})

    def call(self, tool, arguments):
        if tool not in self.tools:
            raise Blocked(f"MCC tool unavailable: {tool}")
        result = self.rpc("tools/call", {"name": tool, "arguments": arguments}, timeout=45)
        if result.get("isError"):
            raise Failed(f"{tool}: {result.get('content')}")
        if "structuredContent" in result:
            return result["structuredContent"]
        parts = [part["text"] for part in result.get("content", []) if part.get("type") == "text"]
        if len(parts) == 1:
            try:
                return json.loads(parts[0])
            except json.JSONDecodeError:
                return parts[0]
        return parts


class Bot:
    def __init__(self, name, executable, cache, directory, host, port, account):
        self.name = name
        self.executable = executable
        self.log = directory / f"mcc-{name}.log"
        self.cwd = directory / f"mcc-{name}"
        self.cwd.mkdir()
        self.host = host
        self.port = port
        self.account = account
        self.process = None
        self.cache = cache

    def start(self):
        if self.process is not None:
            raise Failed(f"{self.name} is already connected")
        port = local_port()
        token = secrets.token_urlsafe(32)
        self.client = Mcp(port, token)
        login = json.dumps(self.account["login"])
        password = '"-"' if self.account["mode"] == "offline" else '""'
        config = f"""[Main.General]
Account = {{ Login = {login}, Password = {password} }}
Server = {{ Host = {json.dumps(self.host)}, Port = {self.port} }}
[Main.Advanced]
EnableSentry = false
TerrainAndMovements = true
InventoryHandling = true
EntityHandling = true
SessionCache = "disk"
ProfileKeyCache = "memory"
ResolveSrvRecords = "no"
ExitOnFailure = true
[ChatBot.McpServer]
Enabled = true
[ChatBot.McpServer.Transport]
BindHost = "127.0.0.1"
Port = {port}
RequireAuthToken = true
AuthTokenEnvVar = "MCC_MCP_AUTH_TOKEN"
"""
        (self.cwd / "MinecraftClient.ini").write_text(config, encoding="utf-8")
        env = os.environ.copy()
        env["MCC_MCP_AUTH_TOKEN"] = token
        env["DOTNET_BUNDLE_EXTRACT_BASE_DIR"] = str(self.cache / "dotnet")
        # MCC owns one process and configuration per connected player.
        self.process = Process([self.executable, "BasicIO-NoColor"], self.cwd, self.log, env)

        def ready():
            self.process.check()
            try:
                status = self.client.initialize()
            except (error.URLError, TimeoutError):
                return None
            if not isinstance(status, dict) or not status.get("success", status.get("Success", False)):
                return None
            return status

        timeout = 180 if self.account["mode"] == "microsoft" else 35
        if self.account["mode"] == "microsoft":
            print(f"{self.name}: complete Microsoft device sign-in if requested. Private MCC log: {self.log}", flush=True)
        wait_for(ready, timeout, f"{self.name} did not connect. See {self.log}", Blocked)

    def call(self, tool, args):
        if self.process is None:
            raise Failed(f"{self.name} is disconnected")
        self.process.check()
        return self.client.call(tool, args)

    def stop(self):
        if self.process is not None:
            self.process.stop("/quit")
            self.process = None


class Paper:
    def __init__(self, tools, directory, level):
        self.directory = directory
        self.port = local_port()
        self.level = level
        self.log = directory.parent / "paper.log"
        self.tools = tools
        self.process = None
        self.offset = 0
        self.events = []

    def start(self, timeout):
        if mojang := self.tools.get("mojang"):
            bootstrap = self.directory / "cache"
            bootstrap.mkdir()
            shutil.copy2(mojang, bootstrap / mojang.name)
        properties = {
            "server-ip": "127.0.0.1", "server-port": self.port, "online-mode": "false",
            "enforce-secure-profile": "false", "enable-rcon": "false", "enable-query": "false",
            "level-name": self.level, "level-type": r"minecraft\:flat", "generate-structures": "false",
            "spawn-protection": 0, "difficulty": "peaceful", "view-distance": 4,
            "simulation-distance": 4, "max-players": 32, "sync-chunk-writes": "true",
        }
        (self.directory / "server.properties").write_text(
            "".join(f"{key}={value}\n" for key, value in properties.items()), encoding="ascii"
        )
        (self.directory / "eula.txt").write_text("eula=true\n", encoding="ascii")
        self.process = Process(
            [self.tools["java"], "-Xms512M", "-Xmx1536M", "-Dfile.encoding=UTF-8",
             "-Dterminal.jline=false", "-Dterminal.ansi=false", "-jar", self.tools["paper"], "--nogui"],
            self.directory, self.log,
        )

        def ready():
            self.process.check()
            if re.search(r'Done \([\d.]+s\)!', self.log.read_text(encoding="utf-8", errors="replace")):
                return True
            return None

        wait_for(ready, timeout, f"Paper startup timed out. See {self.log}", Blocked)

    def read_events(self):
        with self.log.open("rb") as stream:
            stream.seek(self.offset)
            for line in stream:
                if not line.endswith(b"\n"):
                    break
                self.offset += len(line)
                marker = b"[MGCTestProbe] MGC_TEST "
                if marker in line:
                    text = line.split(marker, 1)[1].decode("utf-8", "replace").rstrip()
                    self.events.append(json.JSONDecoder().raw_decode(text)[0])
        return self.events

    def command(self, line):
        self.process.send(line)

    def snapshot(self, arguments):
        fields = [str(part) for part in arguments]
        if any(not re.fullmatch(r"[A-Za-z0-9_.-]+", part) for part in fields):
            raise ValueError("Snapshot arguments must be identifiers or integer coordinates")
        correlation = secrets.token_hex(8)
        self.command(f"mgctest snapshot {correlation} {' '.join(fields)}")
        return wait_for(
            lambda: next((row for row in self.read_events() if row.get("request") == correlation), None),
            5, "No snapshot response from Paper probe", Blocked,
        )

    def stop(self):
        if self.process is not None:
            self.process.stop("stop")
