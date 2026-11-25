"""Import the approved resonance-device projection from a local Git commit."""

import argparse
import hashlib
import json
import re
import subprocess
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
MODULE = "resonance-device"
DESTINATION = ROOT / "modules" / MODULE
LOCK = ROOT / "modules.lock.json"
JAVA_ROOT = "src/main/java/ke/writ/resonance/"
ASSETS = "src/main/resources/assets/writ/"
RESOURCES = {
    ASSETS + "models/item/resonance_device.json",
    *(ASSETS + f"lang/{language}.json" for language in ("en_us", "fr_fr", "zh_cn")),
}


def sha256(content):
    return hashlib.sha256(content).hexdigest()


def read_snapshot(repo, commit):
    def git(*args):
        return subprocess.check_output(["git", "-C", str(repo), *args])

    if not re.fullmatch("[0-9a-f]{40}", commit) or git("cat-file", "-t", commit).strip() != b"commit":
        raise ValueError("provide a full Git commit ID")
    entries = git("ls-tree", "-rz", commit, "--", JAVA_ROOT, ASSETS).split(b"\0")
    files = {}
    for entry in filter(None, entries):
        metadata, raw_path = entry.split(b"\t", 1)
        path = raw_path.decode("utf-8")
        is_java = path.startswith(JAVA_ROOT) and not path.startswith(JAVA_ROOT + "dev/") and path.endswith(".java")
        if not is_java and path not in RESOURCES:
            continue
        mode, kind, object_id = metadata.split()
        if mode != b"100644" or kind != b"blob" or not re.fullmatch(r"[A-Za-z0-9_./-]+", path):
            raise ValueError(f"unsupported snapshot entry: {path}")
        if any(part in ("", ".", "..") or part.endswith(".") or Path(part).is_reserved()
               for part in path.split("/")):
            raise ValueError(f"unsafe snapshot path: {path}")
        files[path] = git("cat-file", "blob", object_id.decode("ascii"))
    if not RESOURCES.issubset(files) or JAVA_ROOT + "ResonanceModule.java" not in files:
        raise ValueError("the approved module inputs are incomplete")
    if len({path.casefold() for path in files}) != len(files):
        raise ValueError("case-insensitive path collision")
    tree = git("rev-parse", f"{commit}^{{tree}}").decode("ascii").strip()
    return files, tree


def unique_keys(pairs):
    result = dict(pairs)
    if len(result) != len(pairs):
        raise ValueError("duplicate translation key")
    return result


def check_resources(files):
    for path, data in files.items():
        if not path.startswith(ASSETS):
            continue
        host = ROOT / path
        if "/lang/" not in path:
            if host.exists():
                raise ValueError(f"resource conflict: {path}")
            continue
        module_keys = json.loads(data, object_pairs_hook=unique_keys)
        host_keys = json.loads(host.read_bytes(), object_pairs_hook=unique_keys)
        if common := host_keys.keys() & module_keys.keys():
            raise ValueError(f"translation conflict in {path}: {sorted(common)}")


def working_hashes():
    if not DESTINATION.exists():
        return {}
    return {path.relative_to(DESTINATION).as_posix(): sha256(path.read_bytes())
            for path in DESTINATION.rglob("*") if path.is_file()}


def import_snapshot(files, commit, tree):
    hashes = {path: sha256(data) for path, data in sorted(files.items())}
    snapshot = {"commit": commit, "tree": tree, "files": hashes}
    lock = json.loads(LOCK.read_bytes()) if LOCK.exists() else {"schema": 1, "modules": {}}
    if lock.get("schema") != 1:
        raise ValueError("unsupported lock schema")
    previous = lock["modules"].get(MODULE, {})
    if working_hashes() != previous.get("files", {}):
        raise ValueError("the existing snapshot differs from its lock")
    if snapshot == previous:
        print(f"{MODULE} already matches {commit}")
        return
    lock["modules"][MODULE] = snapshot
    DESTINATION.parent.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".resonance-", dir=DESTINATION.parent) as temporary:
        staged = Path(temporary) / "new"
        backup = Path(temporary) / "previous"
        staged_lock = Path(temporary) / "lock.json"
        for path, data in files.items():
            target = staged / path
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        staged_lock.write_bytes((json.dumps(lock, indent=2) + "\n").encode("utf-8"))

        if DESTINATION.exists():
            DESTINATION.rename(backup)
        installed = False
        try:
            staged.rename(DESTINATION)
            installed = True
            staged_lock.replace(LOCK)
        except OSError:
            if installed:
                DESTINATION.rename(staged)
            if backup.exists():
                backup.rename(DESTINATION)
            raise
    print(f"Imported {len(files)} files from {commit}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo", type=Path)
    parser.add_argument("commit")
    args = parser.parse_args()
    files, tree = read_snapshot(args.repo, args.commit)
    check_resources(files)
    import_snapshot(files, args.commit, tree)


if __name__ == "__main__":
    main()
