
Spec-driven Workshop
====================

This is a workshop repo designed to help engineers learn about spec-driven
development (and lightweight formal methods) through a series of exercises.

For more details, see [the main guide](/GettingStartedWithSpec-DrivenDev.md) and [reference](/reference.md).  See the `main-more` branch for... more.

### Minimal requirements

The base tooling depends on:

 * GNU Make 4.x or better (Make 3.x will also work on most platforms)
 * GNU Bash 3.x or better
 * Java 21 or better
 * Maven 3.6 or better (this project should be compatible with Maven 4)

### Getting started

This project uses Make to manage build/project commands.

**Attention!**
You must run these commands first before using the project.  Please have a good internet connection before continuing.
 * `make tooling`
 * `make check`

#### General tooling info

 * The project uses Maven for managing dependences and workflow plugins
 * Code formatting with [Spotless](https://github.com/diffplug/spotless); Currently using the Palantir-modified Google Style
 * Unit tests via JUnit5, property-based tests via [jqwik](https://jqwik.net/docs/current/user-guide.html), concurrency tests via [Fray](https://github.com/cmu-pasta/fray/tree/main) -- all integrated through JUnit5
 * Static analysis via Google Errorprone, Spotbugs, and find-security-bugs
 * Extended checking and verification with [OpenJML](https://www.openjml.org/tutorial/)

As you adapt the spec-driven techniques to other languages and runtimes, you should have similar tooling coverage.
For example, in Rust you might use Cargo, rustfmt, proptest/quickcheck/bolero for property-based tests,
Shuttle for concurrency tests, clippy and cargo-audit for additional static analysis, and Kani/Prusti/Creusot for verication.
Kani integrates directly with Bolero (so that might make the tooling a bit easier) and presumably the test suite
would be run with Miri.

### Important Make targets

 * `make tooling`   - downloads all required tools for this project
 * `make clean`     - deletes all build/test artifacts
 * `make format`    - runs the code formatter
 * `make check`     - runs `format` and then compiles all code and tests, runs all static analyzers, runs the test suite
 * `make check-jml` - runs `check` and then runs full JML code verification
 * `make doc`       - produces API / Javadoc documentation
 * `make jshell`    - launches an interactive JShell (full Classpath access)
 * `make repl`      - launches a Clojure REPL (full Classpath access)
 * `make uberjar`   - create an executable Uberjar of the project
 * `make run`       - run/execute the Uberjar artifact

### Using Orchard (macOS / Docker)

Orchard is a Docker-based sandbox for macOS users. It builds an Ubuntu container with Java 21, Maven, OpenJML, and Fray pre-installed — including native aarch64 (Apple Silicon) support.

```
./orchard.sh              # Enter the orchard interactively
./orchard.sh make check   # Run a specific command inside the orchard
```

The first run builds the Docker image (this takes a few minutes). Subsequent runs reuse the cached image. The container mounts only the project directory at `/workspace` — no home directory credentials or `/var` are exposed.

**Important:** `git push` / `git pull` from inside the orchard is discouraged — prefer doing them *outside*, on your host machine. Exporting `GH_TOKEN` (see below) wires git's credential helper inside the container too, so whether push/pull actually works there depends on how the PAT itself is scoped — scope it without "Contents: write" so GitHub rejects push regardless.

#### Optional: GitHub access inside the orchard

Export these on the host before running `./orchard.sh` — they're forwarded into the container via a secure temp env-file (never as a `-e` flag, which would leak the raw value into this shell's process listing):

| Variable | Token type | Used for |
|---|---|---|
| `GH_TOKEN` | Fine-grained PAT (repo read + PR read/write) | `gh` CLI, git push/pull (via `gh auth setup-git`). Read directly from the environment by `gh` — never written to disk. |
| `GITHUB_TOKEN` | Classic PAT (`read:packages` scope) | Installs the `@adzerk`-scoped `libjs-cli` package from GitHub Packages. Used once at container start, then discarded for the rest of the session. |

Neither variable is required — omit them and the orchard behaves as before (fully isolated, no GitHub access).

git push/pull via `GH_TOKEN` is discouraged even when the token is present — scope the fine-grained PAT without "Contents: write" so push is rejected by GitHub itself, rather than relying on the orchard to prevent it.

### Using Bubblewrap / `bwrap` (Linux)

Bubblewrap is a lightweight, unprivileged tool for constructing sandboxes.
This repo contains a Bash script, `bw-opencode`, that launches opencode within a Bubblewrap sandbox.

You are not required to use either sandbox utility, but they serve as helpful examples.

### Other sandbox options

Other alternatives include:
 * [VirtualBox VM](https://www.virtualbox.org/) / [Vagrant](https://developer.hashicorp.com/vagrant)
 * [firejail](https://github.com/netblue30/firejail)
 * a microVM
 * [boxlite](https://boxlite.ai/)
 * [OpenSandbox](https://github.com/alibaba/OpenSandbox/tree/main/examples/claude-code)

### Additional tooling and agent-based resources

There are a number of additional commands, skills, and utilities in this repo to improve the agent-based workflow.
Copy the desired commands and skills into your `.opencode` or `.claude` directory.  Adjusting naming as needed.

#### Commands
`commands` - contains common commands you can use directly or adapt for you project
- `/optimize-code @some/path/to/code` - Perform an optimization review of the code
- `/enhance-code @some/path/to/code` - Review the code for quality and spec alignment
- `/spec-evidence @some/spec.md` - Update the spec.md file so Scenarios link to source and tests. You can also list src and test dirs to focus the command if needed
- `/sample-prompt Some prompt text` - Perform verbalized sampling of the given prompt

#### Skills
`skills` - contains skills for advanced modeling techniques
- For Alloy, the `alloy-more` skill should be preferred
- For OpenJML, if the specs are simple don't use a skill.  If the specs are challenging, try `openjml-more`
- For TLA+, if the spec is simple don't use a skill.  If the specs are challenging, try `tlaplus-more`

#### Scripts
The `scripts` directory contains additional tools/scripts to assist in agent-based development
- `evidence.sh` is a doctest tool that turns "living docs" (`spec.md`) into verification artifacts. Validation + Verification in a single source.

### License

<sup>
Licensed under either of <a href="LICENSE-APACHE">Apache License, Version
2.0</a> or <a href="LICENSE-MIT">MIT license</a> at your option.
</sup>

<br>

<sub>
Unless you explicitly state otherwise, any contribution intentionally submitted
for inclusion in this crate by you, as defined in the Apache-2.0 license, shall
be dual licensed as above, without any additional terms or conditions.
</sub>
