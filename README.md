# version-eviction-highest-wins

Wave 1, probe #4 of the sbt versioning coverage plan (`docs/SBT_VERSIONING_PLAN.md`).

## Feature exercised

Coursier's highest-wins eviction rule: when the same artifact is requested at two different versions, Coursier resolves to the highest version and evicts the lower. Mend must report only the winning version; the evicted version MUST NOT appear in the dependency tree.

## Dependencies

| Declared | Artifact (Mend artifactId) | Version | Direct | Notes |
|---|---|---|---|---|
| `"org.typelevel" %% "cats-core" % "2.9.0"` | (evicted) | 2.9.0 | — | Evicted by Coursier highest-wins; MUST NOT appear in tree |
| `"org.typelevel" %% "cats-core" % "2.10.0"` | `cats-core_2.13` | 2.10.0 | yes | WINNER — highest version wins |
| (transitive of cats-core 2.10.0) | `cats-kernel_2.13` | 2.10.0 | no | Pulled by the winning cats-core; version matches winner |

Total: 2 packages. Direct: 1. Transitive: 1.

## Expected dependency tree

- `org.typelevel:cats-core_2.13:2.10.0` (Compile, direct, registry, main) — WINNER
  - `org.typelevel:cats-kernel_2.13:2.10.0` (Compile, transitive, registry, main)

`org.typelevel:cats-core_2.13:2.9.0` is evicted and MUST NOT appear in the tree.

## Mend config

- `.whitesource` pins `sbt: 1.9.8`, `scala: 2.13.12`, `java: 17` via `scanSettings.versioning`. sbt has no dynamic version detection (mend-knowledge `whitesource-config.md` line 148), so the pin is required to keep the probe deterministic across scans. Without this pin, Mend's install-tool may provision a different sbt/Scala/Java combination, which can change the Coursier-resolved tree (different default repositories, different transitive resolution, different artifact suffix at runtime).
- No `whitesource.config` (UA) is needed. Coursier-driven detection from `build.sbt` is sufficient; `runPreStep` is not required for this probe.
- The `sbt` and `scala` values in `.whitesource` match `project/build.properties` (`sbt.version=1.9.8`) and `build.sbt` (`scalaVersion := "2.13.12"`) exactly.

## Contrast with probe #3 (version-dependency-override-force-down)

Probe #3 uses `dependencyOverrides` to force a specific version, overriding what Coursier would naturally resolve. This probe (#4) has **no `dependencyOverrides`** at all — the version selection is performed entirely by Coursier's built-in highest-wins eviction. The distinction matters because:

- Probe #3 tests whether Mend respects an explicit manual override that contradicts natural resolution.
- Probe #4 tests whether Mend correctly applies the Coursier eviction algorithm (highest-wins, not MVS/minimum-version selection as used by Go modules).

A tool that implements MVS-style selection would incorrectly report `2.9.0` as the winner for this probe while correctly handling probe #3. These two probes together distinguish the two failure modes.

## Approach chosen: A (direct conflict via two declarations)

Two explicit `libraryDependencies` entries declare the same artifact (`org.typelevel %% cats-core`) at versions `2.9.0` and `2.10.0`. This is the most direct and deterministic way to trigger eviction in sbt/Coursier:

- **Deterministic**: The two versions are fixed, not range-based. The winning version is always `2.10.0` regardless of new Maven Central releases.
- **Minimal**: No additional direct dependencies are needed. The eviction conflict is self-contained.
- **Verifiable**: cats-core 2.9.0 and 2.10.0 are both published on Maven Central. cats-kernel versions match the corresponding cats-core versions exactly.

Approach B (transitive conflict via two direct deps with overlapping transitives) was considered but rejected for this probe: it requires knowing the exact published transitive graph of two unrelated libraries, which can change across releases and makes the expected tree harder to verify without running `sbt update`.

## Failure modes exercised

1. **Both versions reported (duplicate, eviction not applied)**: Mend reports both `cats-core_2.13:2.9.0` and `cats-core_2.13:2.10.0`. This means Mend read the raw `libraryDependencies` list without simulating Coursier eviction.
2. **Wrong version reported (lower won -- MVS-style instead of highest-wins)**: Mend reports `cats-core_2.13:2.9.0` as the winner. This indicates Mend uses minimum-version selection (Go-module semantics) rather than Coursier's highest-wins rule.
3. **Transitive version mismatch**: `cats-kernel_2.13` is reported at `2.9.0` rather than `2.10.0`. This means the transitive was resolved against the evicted version rather than the winner.
4. **Scala suffix stripped**: `cats-core` reported instead of `cats-core_2.13`.
5. **Transitive missing**: `cats-kernel_2.13` absent from the tree.

## Probe metadata

```json
{
  "probe_name": "sbt-version-eviction-highest-wins-probe",
  "pattern": "version-eviction-highest-wins",
  "wave": 1,
  "probe_number": 4,
  "pm": "sbt",
  "generated": "2026-05-05",
  "target": "local",
  "sbt_version": "1.9.8",
  "scala_version": "2.13.12",
  "java_version": "17"
}
```