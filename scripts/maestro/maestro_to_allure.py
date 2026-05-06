#!/usr/bin/env python3
import argparse
import hashlib
import json
import mimetypes
import re
import shutil
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path


def safe_name(name: str) -> str:
    return " ".join(name.split())


def result_uuid(name: str) -> str:
    return str(uuid.UUID(hashlib.md5(name.encode("utf-8")).hexdigest()))


def map_case_status(case: ET.Element) -> str:
    status_attr = (case.attrib.get("status") or "").upper()
    if case.find("failure") is not None or case.find("error") is not None:
        return "failed"
    if status_attr in {"ERROR", "FAILED"}:
        return "failed"
    if case.find("skipped") is not None or status_attr == "SKIPPED":
        return "skipped"
    return "passed"


def map_step_status(raw: str) -> str:
    raw = (raw or "").upper()
    if raw in {"COMPLETED", "PASSED", "SUCCESS"}:
        return "passed"
    if raw in {"FAILED", "ERROR"}:
        return "failed"
    if raw in {"SKIPPED"}:
        return "skipped"
    return "broken"


def describe_command(command: dict) -> str:
    if not command:
        return "step"
    name, payload = next(iter(command.items()))
    payload = payload or {}

    if name == "tapOnElement":
        sel = (payload.get("selector") or {})
        text = sel.get("textRegex") or sel.get("idRegex") or sel.get("optional")
        return f"Tap: {text}" if text else "Tap"
    if name == "inputTextCommand":
        txt = payload.get("text", "")
        return f"Input text: {txt}" if txt else "Input text"
    if name == "assertConditionCommand":
        cond = payload.get("condition") or {}
        if "visible" in cond:
            text = (cond["visible"] or {}).get("textRegex")
            return f"Assert visible: {text}" if text else "Assert visible"
        return "Assert condition"
    if name == "launchAppCommand":
        return "Launch app"
    if name == "runFlowCommand":
        src = payload.get("sourceDescription")
        return f"Run flow: {src}" if src else "Run flow"

    return name


def load_commands(debug_dir: Path, test_name: str) -> list:
    exact = debug_dir / f"commands-({test_name}).json"
    candidates = []
    if exact.exists():
        candidates.append(exact)
    if not candidates:
        candidates.extend(sorted(debug_dir.glob("commands-(*.json")))
        candidates = [p for p in candidates if f"({test_name})" in p.name]
    if not candidates:
        return []
    try:
        data = json.loads(candidates[0].read_text())
        return data if isinstance(data, list) else []
    except Exception:
        return []


def collect_attachments(test_name: str, debug_dir: Path, artifacts_dir: Path) -> list[Path]:
    test_norm = re.sub(r"[^a-z0-9]+", "", test_name.lower())

    def matches(path: Path) -> bool:
        n = path.name
        if f"({test_name})" in n:
            return True
        name_norm = re.sub(r"[^a-z0-9]+", "", n.lower())
        return bool(test_norm) and test_norm in name_norm

    out = []
    for base in (debug_dir, artifacts_dir):
        if not base.exists():
            continue
        for p in sorted(base.rglob("*")):
            if p.is_file() and matches(p):
                out.append(p)
    return out


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--junit", required=True)
    parser.add_argument("--debug-dir", required=True)
    parser.add_argument("--artifacts-dir", required=True)
    parser.add_argument("--out-dir", required=True)
    parser.add_argument("--pipeline", default="")
    args = parser.parse_args()

    junit = Path(args.junit)
    debug_dir = Path(args.debug_dir)
    artifacts_dir = Path(args.artifacts_dir)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # Clean old generated results to avoid stale test bodies.
    for p in out_dir.glob("*"):
        if p.is_file():
            p.unlink()

    if not junit.exists():
        # Still write minimal launch metadata so report header isn't fully unknown.
        (out_dir / "executor.json").write_text(json.dumps({
            "name": "Gradle qaMaestro",
            "type": "local",
            "buildName": "qaMaestro",
            "buildUrl": "./gradlew qaMaestro",
            "reportName": "Maestro E2E",
        }))
        (out_dir / "environment.properties").write_text(
            "firebase.project=demo-moneysurfer\n"
            "firebase.mode=emulator\n"
            "maestro.debugOutput=build/maestro-debug\n"
            "maestro.testOutput=build/maestro-artifacts\n"
            + (f"qa.pipeline={args.pipeline}\n" if args.pipeline else "")
        )
        return 0

    try:
        root = ET.fromstring(junit.read_text())
    except ET.ParseError as exc:
        # Keep going: the final executor.json/environment.properties block below
        # still lets `allure generate` produce a (mostly empty) report.
        print(f"[maestro_to_allure] could not parse {junit}: {exc}", flush=True)
        root = None

    if root is not None:
        suite = root.find("testsuite")
        suite_name = (suite.attrib.get("name") if suite is not None else "Maestro") or "Maestro"

        for case in root.findall(".//testcase"):
            try:
                name = safe_name(case.attrib.get("name") or case.attrib.get("classname") or "Unnamed Maestro Test")
                uid = result_uuid(name)

                status = map_case_status(case)
                status_details = {}
                failure = case.find("failure")
                if failure is not None and failure.text:
                    status_details["message"] = failure.text.strip()
                    status_details["trace"] = failure.text.strip()

                steps = []
                for item in load_commands(debug_dir, name):
                    cmd = item.get("command") or {}
                    meta = item.get("metadata") or {}
                    start = int(meta.get("timestamp") or 0)
                    duration = int(meta.get("duration") or 0)
                    step = {
                        "name": describe_command(cmd),
                        "status": map_step_status(meta.get("status")),
                        "stage": "finished",
                        "start": start,
                        "stop": start + duration,
                        "steps": [],
                        "attachments": [],
                        "parameters": [],
                    }
                    steps.append(step)

                attachments = []
                for src in collect_attachments(name, debug_dir, artifacts_dir):
                    try:
                        mime, _ = mimetypes.guess_type(src.name)
                        ext = src.suffix or ".bin"
                        dst_name = f"{uuid.uuid4().hex}-attachment{ext}"
                        shutil.copy2(src, out_dir / dst_name)
                        attachments.append({
                            "name": src.name,
                            "source": dst_name,
                            "type": mime or "application/octet-stream",
                        })
                    except OSError as exc:
                        # One unreadable screenshot must not poison the whole report.
                        print(f"[maestro_to_allure] skipping attachment {src}: {exc}", flush=True)

                duration_ms = int(float(case.attrib.get("time") or 0) * 1000)
                result = {
                    "uuid": uid,
                    "historyId": hashlib.md5(f"{suite_name}:{name}".encode("utf-8")).hexdigest(),
                    "name": name,
                    "fullName": f"{suite_name}.{name}",
                    "status": status,
                    "statusDetails": status_details,
                    "stage": "finished",
                    "steps": steps,
                    "attachments": attachments,
                    "parameters": ([{"name": "qa.pipeline", "value": args.pipeline}] if args.pipeline else []),
                    "labels": [
                        {"name": "suite", "value": suite_name},
                        {"name": "framework", "value": "maestro"},
                        {"name": "language", "value": "yaml"},
                        {"name": "package", "value": "scripts/maestro"},
                    ],
                    "links": [],
                    "start": 0,
                    "stop": duration_ms,
                }
                (out_dir / f"{uid}-result.json").write_text(json.dumps(result, ensure_ascii=False))
            except Exception as exc:  # noqa: BLE001 — best-effort per-case isolation
                tc_name = case.attrib.get("name") or case.attrib.get("classname") or "?"
                print(f"[maestro_to_allure] skipping case {tc_name!r}: {exc}", flush=True)
                continue

    (out_dir / "executor.json").write_text(json.dumps({
        "name": "Gradle qaMaestro",
        "type": "local",
        "buildName": "qaMaestro",
        "buildUrl": "./gradlew qaMaestro",
        "reportName": "Maestro E2E",
    }))
    (out_dir / "environment.properties").write_text(
        "firebase.project=demo-moneysurfer\n"
        "firebase.mode=emulator\n"
        "maestro.debugOutput=build/maestro-debug\n"
        "maestro.testOutput=build/maestro-artifacts\n"
        + (f"qa.pipeline={args.pipeline}\n" if args.pipeline else "")
    )

    return 0


if __name__ == "__main__":
    raise SystemExit(main())

