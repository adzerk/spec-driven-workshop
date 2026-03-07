
Spec-driven Workshop
====================

This is a workshop repo designed to help engineers learn about spec-driven
development (and lightweight formal methods) through a series of exercises.

For more details, see [the main guide](https://docs.google.com/document/d/1thg_fBgx5VA34uHJsa31dcPdJ6aC8y1c2iB6NBsAzeo).

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

### Using Bubblewrap / `bwrap`

Bubblewrap is a lightweight, unpriviledged tool for constructing sandboxes.
This repo contains a Bash script, `bw-opencode`, that launches opencode within a Bubblewrap sandbox.

You are not required to use this utility, but it might serve as a helpful example.

Other alternatives include:
 * [VirtualBox VM](https://www.virtualbox.org/) / [Vagrant](https://developer.hashicorp.com/vagrant)
 * [firejail](https://github.com/netblue30/firejail)
 * a microVM
 * [boxlite](https://boxlite.ai/)
 * [OpenSandbox](https://github.com/alibaba/OpenSandbox/tree/main/examples/claude-code)

