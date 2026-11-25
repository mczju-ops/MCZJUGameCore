#!/usr/bin/env python3
"""Run a repository's Minecraft gameplay scenarios with local MCC clients."""

import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit
import zipfile

sys.dont_write_bytecode = True

from runtime import Blocked, Bot, Failed, Paper, wait_for
from toolchain import SetupError, cache_dir, local_eula, resolve_tools

ASSETS = Path(__file__).resolve().parent.parent / "assets"
IDENTIFIER = re.compile(r"[A-Za-z0-9_.-]+")
PLAYER_NAME = re.compile(r"[A-Za-z0-9_]{1,16}")
VARIABLE = re.compile(r"\$\{([A-Za-z0-9_]+)\}")
PROBE_STEPS = {"snapshot", "event", "mark"}
LOCAL_STEPS = PROBE_STEPS | {"console", "paper_log"}


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def substitute(value, variables):
    if isinstance(value, str):
        def replace(match):
            key = match.group(1)
            if key not in variables:
                raise ValueError(f"Unknown scene variable: {key}")
            return str(variables[key])
        return VARIABLE.sub(replace, value)
    if isinstance(value, list):
        return [substitute(item, variables) for item in value]
    if isinstance(value, dict):
        return {key: substitute(item, variables) for key, item in value.items()}
    return value


def matches(value, expected):
    if isinstance(expected, dict):
        return isinstance(value, dict) and all(key in value and matches(value[key], item)
                                               for key, item in expected.items())
    return value == expected and type(value) is type(expected)


def tool_data(value):
    if isinstance(value, dict):
        normalized = {key[0].lower() + key[1:] if key else key: item for key, item in value.items()}
        if normalized.get("ok") is False or normalized.get("success") is False:
            raise Failed(f"MCC action failed: {value}")
        return normalized.get("data", value)
    return value


def timeout(step):
    value = float(step.get("timeout", 10))
    if not 0 < value <= 120:
        raise ValueError("Step timeout must be between 0 and 120 seconds")
    return value


def json_text(value):
    return json.dumps(value, ensure_ascii=False)


def private_dir(parent, prefix):
    parent.mkdir(parents=True, exist_ok=True)
    return Path(tempfile.mkdtemp(prefix=prefix, dir=parent))


def fixture_path(base, path):
    candidate = (base / path).resolve()
    if not candidate.is_relative_to(base):
        raise ValueError(f"Fixture path is outside its scenario directory: {path}")
    return candidate


def artifact(path, role):
    if not path.is_file() or not zipfile.is_zipfile(path):
        raise Blocked(f"Missing or invalid {role} JAR: {path}")
    return path


def run_maven(tools, project, log, arguments, options):
    maven = tools["maven"]
    env = os.environ.copy()
    env["JAVA_HOME"] = str(tools["java"].parent.parent)
    env["PATH"] = str(tools["java"].parent) + os.pathsep + env.get("PATH", "")
    command = [str(maven), "-B", *options, "-f", str(project / "pom.xml"), *arguments]
    if os.name == "nt" and maven.suffix.lower() == ".cmd":
        # Use PowerShell's argument passing for native paths, including paths with spaces.
        quote = lambda value: "'" + value.replace("'", "''") + "'"
        script = "& " + " ".join(map(quote, command)) + "; exit $LASTEXITCODE"
        command = ["powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script]
    with log.open("ab") as stream:
        try:
            subprocess.run(command, cwd=project, env=env, stdout=stream, stderr=subprocess.STDOUT,
                           check=True, timeout=600)
        except (subprocess.SubprocessError, OSError) as exc:
            raise Blocked(f"Maven build failed. See {log}") from exc


def plugin_jar(project, output, previous):
    if output:
        return artifact(project / output, "target plugin")
    candidates = []
    fresh = []
    for jar in (project / "target").glob("*.jar"):
        if jar.name.startswith("original-") or jar.name.endswith(("-sources.jar", "-javadoc.jar", "-tests.jar")):
            continue
        stamp = (jar.stat().st_mtime_ns, jar.stat().st_size)
        if zipfile.is_zipfile(jar):
            with zipfile.ZipFile(jar) as bundle:
                if {"plugin.yml", "paper-plugin.yml"} & set(bundle.namelist()):
                    candidates.append(jar)
                    if previous.get(jar) != stamp:
                        fresh.append(jar)
    candidates = fresh or candidates
    if not candidates:
        raise Blocked("No packaged plugin JAR found; set --artifact to the build output")
    if len(candidates) != 1:
        raise Blocked("Multiple newly packaged plugin JARs; set --artifact to the deployable output")
    return candidates[0]


def build_artifacts(args, tools, directory, need_probe):
    log = directory / "build.log"
    if args.plugin_jar:
        target = artifact(args.plugin_jar, "target plugin")
    else:
        previous = {jar: (jar.stat().st_mtime_ns, jar.stat().st_size)
                    for jar in (args.project / "target").glob("*.jar")}
        run_maven(tools, args.project, log, ["package"], args.maven_arg)
        target = plugin_jar(args.project, args.artifact, previous)
    if args.mgc_jar:
        core = artifact(args.mgc_jar, "MGC")
    else:
        classpath = directory / "mgc-classpath.txt"
        goal = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath"
        run_maven(tools, args.project, log,
                  [f"-Dmdep.outputFile={classpath}", "-DincludeArtifactIds=MCZJUGameCore", goal], args.maven_arg)
        entries = [Path(item) for item in classpath.read_text(encoding="utf-8").strip().split(os.pathsep)]
        entries = [item for item in entries if item.name.startswith("MCZJUGameCore-") and item.suffix == ".jar"]
        if len(entries) != 1:
            raise Blocked("Could not resolve one MGC dependency; supply the matching JAR with --mgc-jar")
        core = artifact(entries[0], "MGC")
    jars = [target, core, *(artifact(item, "dependency") for item in args.dependency)]
    if need_probe:
        if args.probe_jar:
            probe = artifact(args.probe_jar, "probe")
        else:
            build_dir = directory / "probe-build"
            shutil.copytree(ASSETS / "probe", build_dir)
            run_maven(tools, build_dir, log, [f"-Dmgc.core.jar={core}", "package"], args.maven_arg)
            probe = artifact(build_dir / "target" / "mgc-test-probe-1.0.0.jar", "probe")
        jars.append(probe)
    return jars


class Scenario:
    def __init__(self, document, path, args, tools, directory, jars):
        if document["schema"] != 1:
            raise ValueError("Only scenario schema 1 is supported")
        self.case = document
        self.path = path
        self.args = args
        self.tools = tools
        self.directory = directory
        self.jars = jars
        self.bots = {}
        self.marks = {"start": 0}
        self.evidence = []
        self.steps = []
        self.paper = None
        self.variables = {"game": document["game"], "room": document["room"]}
        if any(not IDENTIFIER.fullmatch(name) or name in {".", ".."} for name in self.variables.values()):
            raise ValueError("Game and room names must be file-safe identifiers")
        players = document["players"]
        if not 1 <= len(players) <= 32 or len(players) != len(set(players)):
            raise ValueError("Specify between 1 and 32 distinct players")
        if any(not PLAYER_NAME.fullmatch(name) for name in players):
            raise ValueError("Invalid Minecraft player name")

    def local_server(self):
        folder = self.directory / "server"
        plugins = folder / "plugins"
        plugins.mkdir(parents=True)
        files = self.case.get("files", {})
        for dest, source in files.items():
            target = fixture_path(folder, substitute(dest, self.variables))
            relative = target.relative_to(folder)
            if not relative.parts or relative.parts[0] != "plugins":
                raise ValueError("Plugin fixtures must be below plugins/")
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(fixture_path(self.path.parent, source), target)
        world = self.case.get("world")
        if world:
            shutil.copytree(fixture_path(self.path.parent, world), folder / "world")
        for jar in self.jars:
            target = plugins / jar.name
            if target.exists():
                raise ValueError(f"Duplicate plugin output: {jar.name}")
            shutil.copy2(jar, target)
        self.paper = Paper(self.tools, folder, "world")
        self.record("start-paper", lambda: self.paper.start(self.args.start_timeout))
        if self.case.get("probe") and not any(row["type"] == "ready" for row in self.paper.read_events()):
            raise Blocked(f"Paper test probe did not load. See {self.paper.log}")
        return "127.0.0.1", self.paper.port

    def existing_server(self):
        if not self.case.get("existingReady") or not self.case.get("existingSteps"):
            raise Blocked("An existing server requires existingReady checks and existingSteps in the case")
        address = urlsplit("//" + self.args.server)
        if not address.hostname or address.path or address.username or address.query or address.fragment:
            raise ValueError("Use --server host:port")
        return address.hostname, address.port or 25565

    def connect(self, host, port):
        accounts = load_json(self.args.accounts) if self.args.accounts else {}
        for name in self.case["players"]:
            account = accounts.get(name) if self.args.server else {"mode": "offline", "login": name}
            if not account or account.get("mode") not in {"offline", "microsoft"} or not account.get("login"):
                raise Blocked(f"Configure a test account for {name} in --accounts")
            if account["mode"] == "offline" and not PLAYER_NAME.fullmatch(account["login"]):
                raise ValueError(f"Offline account for {name} needs a valid Minecraft username")
            self.bots[name] = Bot(name, self.tools["mcc"], self.args.cache, self.directory, host, port, account)
        for bot in self.bots.values():
            self.record(f"connect-{bot.name}", bot.start)
        if self.paper is not None and self.case.get("probe"):
            for name in self.bots:
                wait_for(lambda: self.find_event({"type": "profile", "player": name}),
                         15, f"MGC did not load the profile of {name}", Blocked)

    def preflight_tools(self, steps):
        required = set()
        for step in steps:
            if step["op"] in {"mcc", "expect_mcc"}:
                required.add(step["tool"])
            elif step["op"] in {"chat", "bind_turn"}:
                required.add("mcc_chat_history")
        for name, bot in self.bots.items():
            missing = required.difference(bot.client.tools)
            if missing:
                raise Blocked(f"{name} lacks MCC tools: {', '.join(sorted(missing))}")

    def find_event(self, expected, after=0):
        return next((row for row in self.paper.read_events()
                     if row["seq"] > after and matches(row, expected)), None)

    def bot(self, step):
        return self.bots[step["player"]]

    def chat(self, bot, text):
        data = tool_data(bot.call("mcc_chat_history", {"maxCount": 100}))
        if isinstance(data, dict):
            entries = data.get("entries", data.get("Entries", []))
            return next((entry for entry in entries if text in entry.get("text", entry.get("Text", ""))), None)
        raise Blocked(f"Unexpected MCC chat history response: {data}")

    def bind_turn(self, step):
        period = timeout(step)
        players = step["players"]
        if len(players) != 2 or players[0] == players[1]:
            raise ValueError("bind_turn requires two distinct players")

        def who():
            for name in players:
                if line := self.chat(self.bots[name], step["contains"]):
                    self.variables[step.get("first", "first")] = name
                    other = next(candidate for candidate in players if candidate != name)
                    self.variables[step.get("second", "second")] = other
                    return {"first": name, "second": other, "chat": line}
            return None

        return wait_for(who, period, f"No player received {step['contains']!r}")

    def execute(self, step):
        operation = step["op"]
        if operation == "console":
            self.paper.command(step["command"])
            return {"command": step["command"]}
        if operation == "mcc":
            result = self.bot(step).call(step["tool"], step.get("args", {}))
            return result if step.get("allowActionFailure") else tool_data(result)
        if operation == "expect_mcc":
            def observe():
                result = tool_data(self.bot(step).call(step["tool"], step.get("args", {})))
                return result if matches(result, step["expect"]) else None
            return wait_for(observe, timeout(step), f"MCC result did not match {step['expect']}")
        if operation == "chat":
            return wait_for(lambda: self.chat(self.bot(step), step["contains"]), timeout(step),
                            f"{step['player']} did not receive {step['contains']!r}")
        if operation == "paper_log":
            def find_line():
                with self.paper.log.open(encoding="utf-8", errors="replace") as stream:
                    return next((line.rstrip() for line in stream if step["contains"] in line), None)
            return wait_for(find_line, timeout(step), f"Paper log did not contain {step['contains']!r}")
        if operation == "disconnect":
            self.bot(step).stop()
            return {"connected": False}
        if operation == "reconnect":
            self.bot(step).start()
            return {"connected": True}
        if operation == "bind_turn":
            return self.bind_turn(step)
        if operation == "mark":
            events = self.paper.read_events()
            self.marks[step["name"]] = events[-1]["seq"] if events else 0
            return {"seq": self.marks[step["name"]]}
        if operation == "snapshot":
            last = None

            def snapshot():
                nonlocal last
                last = self.paper.snapshot(step["args"])
                if "error" in last:
                    raise Blocked(f"Probe snapshot: {last['error']}")
                return last if matches(last, step["expect"]) else None

            return wait_for(snapshot, timeout(step),
                            lambda: f"Snapshot expected {step['expect']}, last observation was {last}")
        if operation == "event":
            after = self.marks[step.get("after", "start")]
            expected = step["expect"]
            count = step.get("count", 1)
            if type(count) is not int or count < 0:
                raise ValueError("event count must be a non-negative integer")

            def collect():
                rows = [row for row in self.paper.read_events()
                        if row["seq"] > after and matches(row, expected)]
                if len(rows) > count:
                    raise Failed(f"Expected {count} event(s) matching {expected}, found {len(rows)}")
                return rows if len(rows) == count else None
            return wait_for(collect, timeout(step), f"Expected {count} event(s) matching {expected}")
        raise ValueError(f"Unknown scenario operation: {operation}")

    def record(self, label, operation):
        try:
            result = operation()
        except (Failed, Blocked, OSError, ValueError, KeyError) as exc:
            self.steps.append({"id": label, "status": "error", "message": str(exc)})
            raise
        self.steps.append({"id": label, "status": "passed", "evidence": result})

    def run_steps(self, steps):
        for raw in steps:
            label = raw.get("id", raw.get("name", raw.get("op", "unknown")))
            self.record(label, lambda: self.execute(substitute(raw, self.variables)))

    def run(self):
        try:
            if self.args.server:
                host, port = self.existing_server()
                setup = self.case["existingReady"]
                steps = self.case["existingSteps"]
                if any(step["op"] in LOCAL_STEPS for step in setup + steps):
                    raise Blocked("Existing server mode supports player-side observations only")
                prepare = []
            else:
                steps = self.case["steps"]
                if any(step["op"] in PROBE_STEPS for step in steps) and not self.case.get("probe"):
                    raise ValueError("Probe observations require probe: true")
                host, port = self.local_server()
                prepare = self.case.get("prepare", [])
                setup = []
            self.run_steps([{"op": "console", "command": command} for command in prepare])
            self.connect(host, port)
            self.preflight_tools(setup + steps)
            try:
                self.run_steps(setup)
            except Failed as exc:
                raise Blocked(f"Existing server preflight failed: {exc}") from exc
            self.run_steps(steps)
        finally:
            for bot in self.bots.values():
                try:
                    bot.stop()
                except OSError as exc:
                    self.evidence.append(f"Cleanup for {bot.name}: {exc}")
            if self.paper is not None:
                try:
                    self.paper.stop()
                    if self.paper.log.is_file():
                        rows = self.paper.read_events()
                        if rows:
                            (self.directory / "probe.jsonl").write_text(
                                "".join(json_text(row) + "\n" for row in rows), encoding="utf-8"
                            )
                except OSError as exc:
                    self.evidence.append(f"Paper cleanup: {exc}")


def candidate_scenarios(args):
    if args.scenario:
        return args.scenario
    path = args.project / "mc-tests"
    locations = sorted(path.glob("*.json"))
    if args.case:
        locations = [item for item in locations if item.stem in args.case]
        if set(args.case) != {item.stem for item in locations}:
            raise Blocked("Requested case missing in mc-tests/")
    if not locations:
        raise Blocked("No cases in mc-tests/; pass --scenario for an explicit or temporary case")
    return locations


def execute_run(args):
    sources = candidate_scenarios(args)
    output = private_dir(args.output or args.cache / "runs", "run-")
    print(f"Run artifacts: {output}", flush=True)
    results = []
    tools = None
    jars = None
    cases = []
    for source in sources:
        try:
            document = load_json(source)
            if not isinstance(document, dict):
                raise ValueError("A scenario must be a JSON object")
            cases.append((source, document))
        except (OSError, ValueError) as exc:
            results.append({"case": str(source), "status": "blocked", "message": str(exc)})
    build_error = None
    if cases:
        try:
            if not args.server and not local_eula(args.cache, args.accept_eula):
                raise Blocked("Minecraft EULA confirmation is missing. Pass --accept-eula only after explicit acceptance.")
            need_probe = not args.server and any(case.get("probe") for _, case in cases)
            maven_needed = not args.server and (not args.plugin_jar or not args.mgc_jar
                                               or need_probe and not args.probe_jar)
            tools = resolve_tools(args.cache, args.mcc, args.paper, args.server, maven_needed)
            jars = [] if args.server else build_artifacts(args, tools, output, need_probe)
        except (Blocked, SetupError, OSError, subprocess.SubprocessError, ValueError, KeyError) as exc:
            build_error = str(exc)
    for index, (source, document) in enumerate(cases, start=1):
        directory = output / f"case-{index:02d}"
        directory.mkdir()
        status = "passed"
        message = ""
        scenario = None
        try:
            if build_error:
                raise Blocked(build_error)
            selected_jars = [jar for jar in jars if document.get("probe") or
                             jar.name != (args.probe_jar.name if args.probe_jar else "mgc-test-probe-1.0.0.jar")]
            scenario = Scenario(document, source, args, tools, directory, selected_jars)
            scenario.run()
        except Failed as exc:
            status, message = "failed", str(exc)
        except (Blocked, OSError, ValueError, KeyError) as exc:
            status, message = "blocked", str(exc)
        result = {"case": str(source), "status": status, "message": message,
                  "directory": str(directory), "steps": scenario.steps if scenario else [],
                  "cleanup": scenario.evidence if scenario else []}
        if status == "passed" and result["cleanup"]:
            result.update(status="blocked", message="The case ran, but process cleanup reported an error.")
        results.append(result)
        print(f"{source.stem}: {result['status']}" + (f" ({result['message']})" if result["message"] else ""),
              flush=True)
    report = output / "report.json"
    report_data = {"mode": "existing" if args.server else "local",
                   "artifacts": [str(jar) for jar in jars or []], "cases": results}
    report.write_text(json.dumps(report_data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Report: {report}", flush=True)
    if any(result["status"] == "failed" for result in results):
        return 1
    return 2 if any(result["status"] == "blocked" for result in results) else 0


def path_arg(value):
    return Path(value).expanduser().resolve()


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    interface = parser.add_subparsers(required=True, dest="action")
    for name in ("setup", "run"):
        command = interface.add_parser(name)
        command.add_argument("--cache", type=path_arg, default=cache_dir())
        command.add_argument("--mcc", type=path_arg, help="Use an existing MCC executable")
        command.add_argument("--paper", type=path_arg, help="Use an existing Paper JAR")
        command.add_argument("--accept-eula", action="store_true",
                             help="Record the user's acceptance of https://aka.ms/MinecraftEULA")
    run = interface.choices["run"]
    run.add_argument("--project", type=path_arg, default=Path.cwd())
    run.add_argument("--scenario", type=path_arg, action="append")
    run.add_argument("--case", action="append", help="Select a case by filename stem in mc-tests/")
    run.add_argument("--output", type=path_arg)
    run.add_argument("--artifact", type=Path, help="Explicit packaged artifact path, relative to --project")
    run.add_argument("--plugin-jar", type=path_arg, help="Skip the project build and use this JAR")
    run.add_argument("--mgc-jar", type=path_arg)
    run.add_argument("--probe-jar", type=path_arg)
    run.add_argument("--dependency", type=path_arg, action="append", default=[])
    run.add_argument("--maven-arg", action="append", default=[])
    run.add_argument("--server", help="Explicit existing server host:port")
    run.add_argument("--accounts", type=path_arg)
    run.add_argument("--start-timeout", type=int, default=180)
    return parser.parse_args()


def main():
    args = parse_args()
    try:
        if args.action == "run":
            return execute_run(args)
        tools = resolve_tools(args.cache, args.mcc, args.paper)
        accepted = local_eula(args.cache, args.accept_eula)
        print(json.dumps({"python": sys.executable, "platform": sys.platform,
                          "windowsStatus": "pending testing", "eulaAccepted": accepted,
                          **{name: str(path) for name, path in tools.items()}}, indent=2))
    except (Blocked, SetupError, OSError, subprocess.SubprocessError, ValueError, KeyError) as exc:
        print(f"Blocked: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
