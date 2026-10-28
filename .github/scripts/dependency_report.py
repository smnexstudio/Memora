"""Quarterly dependency report: outdated Gradle dependencies and known vulnerabilities.

Reads the versions Memora declares (build.gradle.kts, app/build.gradle.kts, the Gradle wrapper)
and the versions it actually resolves (app/gradle.lockfile). It builds nothing. It asks:

  - Google Maven, Maven Central and the Gradle Plugin Portal for the newest stable version of
    each declared dependency and plugin, and services.gradle.org for the newest Gradle;
  - OSV (osv.dev) whether any locked version has a published vulnerability.

Writes a Markdown report to the file named in REPORT_PATH and sets the step output
found=true when there is anything to act on. Standard library only.
"""

import json
import os
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
REPOS = [
    "https://dl.google.com/dl/android/maven2",
    "https://repo1.maven.org/maven2",
    "https://plugins.gradle.org/m2",
]
UNSTABLE = re.compile(r"(alpha|beta|rc|dev|eap|preview|snapshot|-m\d|\.m\d|-pre)", re.IGNORECASE)


def get(url, data=None):
    req = urllib.request.Request(url, data=data, headers={"User-Agent": "memora-dependency-report", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


def version_key(v):
    return [int(p) if p.isdigit() else -1 for p in re.split(r"[.\-]", v)]


def latest_stable(group, artifact):
    """Newest stable version from the first repository that has the artifact, or None."""
    path = group.replace(".", "/") + "/" + artifact + "/maven-metadata.xml"
    for repo in REPOS:
        try:
            meta = ET.fromstring(get(f"{repo}/{path}"))
        except Exception:
            continue
        versions = [v.text for v in meta.iter("version") if v.text and not UNSTABLE.search(v.text)]
        if versions:
            return max(versions, key=version_key)
    return None


def declared():
    """(group, artifact, version, where) for every declared library and plugin."""
    out = []
    for path in ("app/build.gradle.kts",):
        for g, a, v in re.findall(r'"([\w.\-]+):([\w.\-]+):([\w.\-]+)"', read(path)):
            out.append((g, a, v, path))
    for pid, v in re.findall(r'id\("([\w.\-]+)"\) version "([\w.\-]+)"', read("build.gradle.kts")):
        # A plugin id resolves through its marker artifact: <id>:<id>.gradle.plugin
        out.append((pid, pid + ".gradle.plugin", v, "build.gradle.kts (plugin)"))
    return out


def outdated():
    rows = []
    for g, a, v, where in declared():
        newest = latest_stable(g, a)
        if newest and version_key(newest) > version_key(v):
            name = g if a.endswith(".gradle.plugin") else f"{g}:{a}"
            rows.append((name, v, newest, where))
    # The Gradle wrapper itself.
    wrapper = re.search(r"gradle-([\d.]+)-bin\.zip", read("gradle/wrapper/gradle-wrapper.properties"))
    try:
        current = json.loads(get("https://services.gradle.org/versions/current"))["version"]
        if wrapper and version_key(current) > version_key(wrapper.group(1)):
            rows.append(("Gradle (wrapper)", wrapper.group(1), current, "gradle/wrapper/gradle-wrapper.properties"))
    except Exception as e:
        print(f"Couldn't check the Gradle version: {e}", file=sys.stderr)
    return rows


def locked():
    """{group:artifact:version: in_app} from the lockfile; in_app when it ships in the release APK."""
    out = {}
    for line in read("app/gradle.lockfile").splitlines():
        if not line or line.startswith("#") or "=" not in line or line.startswith("empty="):
            continue
        coord, confs = line.split("=", 1)
        if coord.count(":") == 2:
            out[coord] = "releaseRuntimeClasspath" in confs.split(",")
    return out


def vulnerable():
    coords = locked()
    names = list(coords)
    queries = [{"package": {"name": c.rsplit(":", 1)[0], "ecosystem": "Maven"}, "version": c.rsplit(":", 1)[1]} for c in names]
    found = []
    for i in range(0, len(queries), 500):
        batch = json.loads(get("https://api.osv.dev/v1/querybatch", json.dumps({"queries": queries[i:i + 500]}).encode()))
        for coord, result in zip(names[i:i + 500], batch.get("results", [])):
            for v in result.get("vulns", []):
                try:
                    detail = json.loads(get(f"https://api.osv.dev/v1/vulns/{v['id']}"))
                except Exception:
                    detail = {}
                severity = (detail.get("database_specific") or {}).get("severity", "")
                summary = detail.get("summary") or detail.get("details", "")[:120]
                found.append((coord, coords[coord], v["id"], severity, summary))
    return found


def main():
    old = outdated()
    vulns = vulnerable()
    lines = []
    if vulns:
        lines += ["## Known vulnerabilities", "",
                  "| Library | In the app? | Advisory | Severity | Summary |", "|---|---|---|---|---|"]
        for coord, in_app, vid, sev, summary in sorted(vulns, key=lambda r: (not r[1], r[0])):
            lines.append(f"| `{coord}` | {'**yes**' if in_app else 'build/test only'} | [{vid}](https://osv.dev/vulnerability/{vid}) | {sev or '–'} | {summary.replace('|', '/')} |")
        lines.append("")
    if old:
        lines += ["## Newer stable versions", "", "| Dependency | Now | Newest | Declared in |", "|---|---|---|---|"]
        for name, now, newest, where in old:
            lines.append(f"| `{name}` | {now} | {newest} | `{where}` |")
        lines.append("")
    if lines:
        lines += ["---",
                  "After updating, regenerate the lockfile (the command is in the comment at the end of "
                  "`app/build.gradle.kts`) and let CI build and test the change.",
                  "", "_Opened by the quarterly dependency report (.github/workflows/dependency-report.yml)._"]
    report = "\n".join(lines)
    with open(os.environ.get("REPORT_PATH", "report.md"), "w", encoding="utf-8") as f:
        f.write(report)
    print(report or "Everything is up to date and no known vulnerabilities.")
    if "GITHUB_OUTPUT" in os.environ:
        with open(os.environ["GITHUB_OUTPUT"], "a") as f:
            f.write(f"found={'true' if lines else 'false'}\n")
            f.write(f"vulnerable={'true' if vulns else 'false'}\n")


if __name__ == "__main__":
    main()
