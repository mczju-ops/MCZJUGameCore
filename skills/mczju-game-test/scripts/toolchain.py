"""Install and resolve the local Minecraft test toolchain."""

import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import tarfile
import tempfile
import time
import urllib.request
import zipfile

MCC_TAG = "20260929-519"
PAPER_VERSION = "26.2"
PAPER_BUILD = 121
MAVEN_VERSION = "3.9.9"
AGENT = "MCZJUGameCore-test/1.0 (https://github.com/mczju-ops/MCZJUGameCore)"
MCC_DIGESTS = {
    "linux-x64": "a60a5a63813c9e5b4f0f86e275a5e7162d7ff091f66e2281e013af444477b9cb",
    "linux-arm64": "7222e77ff07da62ae533577e274a8d50e85ba7f54fe9f0d0d5b8729fd0a2cb07",
    "osx-x64": "744e44c48d9a709baa95f3f2d78731552ef6828e16c2bdbb40b241e054a713d2",
    "osx-arm64": "7d8fceaa46821a8ef6dbd097689306a1b0ffdb3c8fcf68cb2dfc222ac2dd27c4",
    "win-x64": "4af60e918fef2a034365715ac3b184dec5ef3851c05db3b62073c1ae5b81b868",
    "win-arm64": "6f29c732858039d321024624ae452c0d9db6411bf3e0be43b1d6db1c4afad42d",
}


class SetupError(RuntimeError):
    """A required local prerequisite could not be provisioned."""


def system():
    os_name = {"Linux": "linux", "Darwin": "osx", "Windows": "win"}.get(platform.system())
    arch = {"x86_64": "x64", "amd64": "x64", "arm64": "arm64", "aarch64": "arm64"}.get(
        platform.machine().lower()
    )
    if not os_name or not arch:
        raise SetupError(f"Unsupported platform: {platform.system()} {platform.machine()}")
    return os_name, arch


def cache_dir():
    if override := os.environ.get("MGC_TEST_CACHE"):
        return Path(override).expanduser().resolve()
    if os.name == "nt":
        return Path(os.environ["LOCALAPPDATA"]) / "mgc-game-test"
    if platform.system() == "Darwin":
        return Path.home() / "Library" / "Caches" / "mgc-game-test"
    return Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "mgc-game-test"


def fetch(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": AGENT}), timeout=45)


def download(url, dest, checksum, algorithm="sha256"):
    """Download or reuse an artifact after verifying its upstream checksum."""
    if not re.fullmatch(r"[0-9a-fA-F]+", checksum) or len(checksum) != hashlib.new(algorithm).digest_size * 2:
        raise SetupError(f"Invalid checksum for {dest.name}")
    checksum = checksum.lower()

    def digest(path):
        with path.open("rb") as handle:
            return hashlib.file_digest(handle, algorithm).hexdigest()

    if dest.exists() and digest(dest) == checksum:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    curl = shutil.which("curl.exe" if os.name == "nt" else "curl")
    for attempt in range(3):
        with tempfile.NamedTemporaryFile(dir=dest.parent, delete=False) as temp:
            temporary = Path(temp.name)
            try:
                if attempt != 1 and curl:
                    temp.close()
                    subprocess.run(
                        [curl, "--fail", "--location", "--silent", "--show-error", "--max-time", "180",
                         "--user-agent", AGENT, "--output", str(temporary), url],
                        check=True, timeout=190,
                    )
                else:
                    with fetch(url) as response:
                        shutil.copyfileobj(response, temp)
                    temp.close()
                if digest(temporary) != checksum:
                    raise SetupError(f"Checksum mismatch for {dest.name} ({temporary.stat().st_size} bytes received)")
                temporary.replace(dest)
                return dest
            except (OSError, SetupError, subprocess.SubprocessError) as exc:
                if attempt == 2:
                    raise SetupError(f"Could not install {dest.name}: {exc}") from exc
            finally:
                temp.close()
                temporary.unlink(missing_ok=True)
        time.sleep(attempt + 1)


def metadata(url):
    with fetch(url) as response:
        return json.load(response)


def unpack(archive, destination, relative_executable):
    executable = destination / relative_executable
    if executable.is_file():
        return executable
    destination.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=destination.parent) as temp:
        root = Path(temp)
        if archive.name.endswith(".zip"):
            with zipfile.ZipFile(archive) as bundle:
                bundle.extractall(root)
        else:
            with tarfile.open(archive) as bundle:
                bundle.extractall(root, filter="data")
        extracted = next((item for item in root.iterdir() if (item / relative_executable).is_file()), None)
        if extracted is None:
            raise SetupError(f"Executable {relative_executable} missing in {archive.name}")
        if destination.exists():
            raise SetupError(f"Incomplete tool installation at {destination}")
        extracted.replace(destination)
    return executable


def resolve_java(cache, os_name, arch):
    extension = ".exe" if os_name == "win" else ""
    relative = ("Contents/Home/bin/" if os_name == "osx" else "bin/") + "java" + extension
    java_home = os.environ.get("JAVA_HOME")
    candidates = [Path(java_home) / "bin" / ("java" + extension)] if java_home else []
    if java := shutil.which("java"):
        candidates.append(Path(java))
    candidates.append(cache / f"jdk-25-{os_name}-{arch}" / relative)
    for candidate in candidates:
        if candidate.is_file():
            version = subprocess.run([str(candidate), "-version"], capture_output=True, text=True, timeout=15)
            javac = candidate.with_name("javac" + extension)
            if version.returncode == 0 and javac.is_file() and re.search(r'version "25(?:[.+"-])', version.stderr):
                return candidate.resolve()
    adoptium_os = {"win": "windows", "osx": "mac", "linux": "linux"}[os_name]
    assets = metadata(f"https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture={arch}&image_type=jdk&os={adoptium_os}&vendor=eclipse")
    if not assets:
        raise SetupError(f"No JDK 25 distribution for {os_name}-{arch}")
    package = assets[0]["binary"]["package"]
    archive = download(package["link"], cache / "downloads" / package["name"], package["checksum"])
    return unpack(archive, cache / f"jdk-25-{os_name}-{arch}", relative)


def resolve_maven(cache, os_name):
    executable = "mvn.cmd" if os_name == "win" else "mvn"
    if maven := shutil.which(executable):
        return Path(maven).absolute()
    destination = cache / f"maven-{MAVEN_VERSION}"
    relative = f"bin/{executable}"
    if (destination / relative).is_file():
        return destination / relative
    extension = "zip" if os_name == "win" else "tar.gz"
    name = f"apache-maven-{MAVEN_VERSION}-bin.{extension}"
    url = f"https://archive.apache.org/dist/maven/maven-3/{MAVEN_VERSION}/binaries/{name}"
    with fetch(url + ".sha512") as response:
        checksum = response.read().decode("ascii").split()[0]
    archive = download(url, cache / "downloads" / name, checksum, "sha512")
    return unpack(archive, destination, relative)


def resolve_tools(cache, mcc_override=None, paper_override=None, server=None, maven_needed=True):
    """Provision the network client, local server and build tools needed for this mode."""
    os_name, arch = system()
    platform_id = f"{os_name}-{arch}"
    mcc_name = f"MinecraftClient-{MCC_TAG}-{platform_id}" + (".exe" if os_name == "win" else "")
    mcc = mcc_override
    if mcc is None:
        url = f"https://github.com/MCCTeam/Minecraft-Console-Client/releases/download/{MCC_TAG}/{mcc_name}"
        mcc = download(url, cache / "tools" / mcc_name, MCC_DIGESTS[platform_id])
        if os_name != "win":
            mcc.chmod(0o755)
    if server:
        return {"mcc": mcc}
    java = resolve_java(cache, os_name, arch)
    tools = {"mcc": mcc, "java": java}
    if maven_needed:
        tools["maven"] = resolve_maven(cache, os_name)
    if paper_override:
        tools["paper"] = paper_override
    else:
        digest = "0de30efb024bc8b83c9c7d507d11802897ad8056b6110ec09fe1a91d126ccb54"
        name = f"paper-{PAPER_VERSION}-{PAPER_BUILD}.jar"
        url = f"https://fill-data.papermc.io/v1/objects/{digest}/{name}"
        tools["paper"] = download(url, cache / "tools" / name, digest)
    with zipfile.ZipFile(tools["paper"]) as bundle:
        if "META-INF/download-context" in bundle.namelist():
            checksum, url, name = bundle.read("META-INF/download-context").decode("utf-8").strip().split("\t")
            if not url.startswith(("https://piston-data.mojang.com/", "https://launcher.mojang.com/")) or Path(name).name != name:
                raise SetupError("Unexpected Mojang bootstrap metadata in Paper JAR")
            tools["mojang"] = download(url, cache / "mojang" / checksum / name, checksum)
    return tools


def local_eula(cache, accept=False):
    receipt = cache / "minecraft-eula.accepted"
    if accept:
        receipt.parent.mkdir(parents=True, exist_ok=True)
        receipt.write_text("https://aka.ms/MinecraftEULA\n", encoding="utf-8")
    return receipt.is_file()
