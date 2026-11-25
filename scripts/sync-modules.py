"""Import pinned child commits and make one local integration commit."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[1]
MODULES = ROOT / "modules"
LOCK = ROOT / "modules.lock.json"
SYNC_BRANCH_PREFIX = "sync/"
INTEGRATION_BRANCH = "refs/heads/integration-forge-1.20.1"
CORE = "writ-core"
DEVICE = "resonance-device"
LANGUAGES = {f"assets/writ/lang/{locale}.json" for locale in ("en_us", "fr_fr", "zh_cn")}
DEVICE_JAVA = "src/main/java/ke/writ/resonance/"
RESOURCES = "src/main/resources/"
DEVICE_RESOURCES = {RESOURCES + path for path in LANGUAGES | {
    "assets/writ/models/item/resonance_device.json",
    "assets/writ/textures/item/resonance_device.png",
}}
CORE_PREFIXES = ("src/main/", "src/test/", "src/gametest/")
DEVICE_DEV_PREFIXES = (DEVICE_JAVA + "dev/", RESOURCES + "data/writ_resonance_dev/")
DEVICE_STANDALONE_RESOURCES = {RESOURCES + "META-INF/mods.toml", RESOURCES + "pack.mcmeta"}
REQUIRED = {
    CORE: {"src/main/java/ke/writ/Writ.java", RESOURCES + "META-INF/mods.toml"}
    | {RESOURCES + path for path in LANGUAGES},
    DEVICE: {DEVICE_JAVA + "ResonanceModule.java"} | DEVICE_RESOURCES,
}


def git(repo, *args):
    return subprocess.check_output(["git", "-C", str(repo), *args])


def hashes(files):
    return {path: hashlib.sha256(blob).hexdigest() for path, blob in sorted(files.items())}


def accepted(module, path):
    if module == CORE:
        return path.startswith(CORE_PREFIXES) and not path.startswith(DEVICE_JAVA)
    return path in DEVICE_RESOURCES or (
        path.startswith(DEVICE_JAVA) and not path.startswith(DEVICE_JAVA + "dev/") and path.endswith(".java")
    )


def excluded_production_file(module, path):
    return module == DEVICE and (
        path.startswith(DEVICE_DEV_PREFIXES) or path in DEVICE_STANDALONE_RESOURCES
    )


def read_snapshot(module, repo, commit):
    if not re.fullmatch(r"[a-f0-9]{40}", commit) or git(repo, "cat-file", "-t", commit).strip() != b"commit":
        raise ValueError(f"{module}: provide a full Git commit ID")
    objects = git(repo, "ls-tree", "-rz", commit, "--", "src").split(b"\0")
    files = {}
    unclassified = []
    for entry in filter(None, objects):
        metadata, raw_path = entry.split(b"\t", 1)
        path = raw_path.decode("utf-8")
        if not accepted(module, path):
            if path.startswith("src/main/") and not excluded_production_file(module, path):
                unclassified.append(path)
            continue
        mode, kind, object_id = metadata.split()
        if mode != b"100644" or kind != b"blob" or any(
            not re.fullmatch(r"[A-Za-z0-9_.-]+", part)
            or part in (".", "..") or part.endswith(".") or Path(part).is_reserved()
            for part in path.split("/")
        ):
            raise ValueError(f"{module}: unsupported snapshot path {path}")
        files[path] = git(repo, "cat-file", "blob", object_id.decode("ascii"))
    if unclassified:
        paths = "\n  ".join(unclassified)
        raise ValueError(f"{module}: classify these production files before syncing:\n  {paths}")
    if not REQUIRED[module].issubset(files) or len({name.casefold() for name in files}) != len(files):
        raise ValueError(f"{module}: missing required files or ambiguous paths")
    tree = git(repo, "rev-parse", f"{commit}^{{tree}}").decode("ascii").strip()
    return files, {"commit": commit, "tree": tree, "files": hashes(files)}


def checked_existing():
    lock = json.loads(LOCK.read_bytes()) if LOCK.exists() else {"schema": 1, "modules": {}}
    if lock["schema"] != 1 or not lock["modules"].keys() <= REQUIRED.keys():
        raise ValueError("unsupported module lock")
    snapshots = {}
    for name in REQUIRED:
        root = MODULES / name
        files = {path.relative_to(root).as_posix(): path.read_bytes()
                 for path in root.rglob("*") if path.is_file()}
        record = lock["modules"].get(name)
        if hashes(files) != (record["files"] if record else {}):
            raise ValueError(f"{name}: current snapshot does not match its lock")
        if record:
            snapshots[name] = files
    return lock, snapshots


def unique_keys(pairs):
    data = dict(pairs)
    if len(data) != len(pairs):
        raise ValueError("duplicate JSON key")
    return data


def check_resources(snapshots):
    core = snapshots[CORE]
    device = snapshots[DEVICE]
    duplicates = core.keys() & device.keys()
    languages = {RESOURCES + path for path in LANGUAGES}
    if duplicates - languages:
        raise ValueError(f"duplicate module files: {sorted(duplicates - languages)}")
    for path in sorted(languages):
        core_keys = json.loads(core[path], object_pairs_hook=unique_keys)
        device_keys = json.loads(device[path], object_pairs_hook=unique_keys)
        if common := core_keys.keys() & device_keys.keys():
            raise ValueError(f"duplicate translation keys in {path}: {sorted(common)}")


def install(snapshots, lock, changed):
    MODULES.mkdir(exist_ok=True)
    git_dir = Path(git(ROOT, "rev-parse", "--absolute-git-dir").strip().decode("utf-8"))
    with tempfile.TemporaryDirectory(prefix="writ-sync-", dir=git_dir) as directory:
        temp = Path(directory)
        for name in changed:
            for path, blob in snapshots[name].items():
                target = temp / "new" / name / path
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(blob)
        new_lock = temp / LOCK.name
        new_lock.write_bytes((json.dumps(lock, indent=2, sort_keys=True) + "\n").encode("utf-8"))
        previous = {}
        installed = []
        try:
            for name in changed:
                target = MODULES / name
                if target.exists():
                    previous[name] = target.rename(temp / name)
                (temp / "new" / name).rename(target)
                installed.append(name)
            new_lock.replace(LOCK)
        except OSError:
            for name in reversed(installed):
                (MODULES / name).rename(temp / "new" / name)
            for name, backup in previous.items():
                backup.rename(MODULES / name)
            raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--module", nargs=3, action="append", required=True,
                        metavar=("NAME", "REPOSITORY", "COMMIT"))
    args = parser.parse_args()
    branch = git(ROOT, "branch", "--show-current").strip().decode("utf-8")
    if not branch.startswith(SYNC_BRANCH_PREFIX):
        parser.error("run from a sync/* candidate branch, created from the integration branch")
    try:
        git(ROOT, "merge-base", "--is-ancestor", INTEGRATION_BRANCH, "HEAD")
    except subprocess.CalledProcessError as error:
        if error.returncode == 1:
            parser.error("candidate must contain the current local integration-forge-1.20.1 tip")
        raise
    if git(ROOT, "status", "--porcelain"):
        parser.error("integration checkout must be clean")
    names = [name for name, _, _ in args.module]
    if len(names) != len(set(names)) or not set(names) <= REQUIRED.keys():
        parser.error("choose writ-core and/or resonance-device at most once each")

    lock, snapshots = checked_existing()
    changed = {}
    for name, repo, commit in args.module:
        files, record = read_snapshot(name, Path(repo).resolve(), commit)
        if record != lock["modules"].get(name):
            snapshots[name] = files
            lock["modules"][name] = record
            changed[name] = commit
    if snapshots.keys() != REQUIRED.keys():
        parser.error("the first import requires both modules")
    check_resources(snapshots)
    if not changed:
        print("Module snapshots already match the requested commits")
        return

    install(snapshots, lock, changed)
    paths = ["modules.lock.json", *(f"modules/{name}" for name in changed)]
    message = "\n".join(f"{name}: {commit}" for name, commit in sorted(changed.items()))
    try:
        git(ROOT, "add", "--", *paths)
        git(ROOT, "diff", "--cached", "--check")
        result = git(ROOT, "commit", "-m", "chore: 同步集成模块源码", "-m", message)
    except subprocess.CalledProcessError as error:
        if error.output:
            print(error.output.decode("utf-8", "replace").strip())
        parser.exit(1, "Snapshots installed, but Git did not complete the commit. "
                       "Inspect the checkout and follow INTEGRATION.md before retrying.\n")
    print(result.decode("utf-8", "replace").strip())


if __name__ == "__main__":
    main()
