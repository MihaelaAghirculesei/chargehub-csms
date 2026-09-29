# 0004. Gradle multi-module layout

- Status: Accepted
- Date: 2026-09-29

## Context

The repository produces three things with different lives:

- a **protocol library** (OCPP messages, codec, schema validation) with no framework in it;
- the **CSMS application**, a Spring Boot service;
- a **charge point simulator**, a separate program that uses the protocol library and also
  serves as a test fixture.

The simulator must use the same protocol code as the application, but must not depend on
the application itself. All three also need the same build settings: Java version, compiler
flags, formatting and static analysis.

## Considered options

For the structure:

1. **One Gradle module.** Simplest build. But the simulator would carry the whole
   application with it, and nothing would stop the protocol code from using Spring.
2. **Several modules in one repository.** Each artifact is a module. Dependencies between
   them are explicit, and the build checks them.
3. **One repository per artifact.** Clean separation, but every protocol change needs a
   release and a version bump in two other repositories. Too much ceremony for one developer.

For the shared build settings:

1. **`subprojects {}` in the root build.** Short, but the root reaches into every module and
   configures it from outside. A module's build file no longer tells the whole story, and
   Gradle discourages it because it blocks features like isolated projects.
2. **`buildSrc`.** Works, but any change in it invalidates the cache of the whole build.
3. **Convention plugins in an included build (`build-logic`).** Each module applies the
   plugin it needs, visibly, in its own build file.

## Decision

We use a multi-module build in one repository (`ocpp-protocol`, `csms-app`,
`charge-point-simulator`) and share settings through a `chargehub.java-conventions` plugin
in `build-logic`.

Other rules of the build:

- Versions live in one version catalog, `gradle/libs.versions.toml`. Libraries managed by
  the Spring Boot BOM have no version there.
- Repositories are declared once, in `settings.gradle.kts`; modules cannot add their own.
- `ocpp-protocol` does not import the Spring Boot BOM, so it stays free of Spring and can
  keep its own test stack.
- The Java 25 toolchain is set in the convention plugin, so the build does not depend on the
  JDK installed on the machine.

## Consequences

- Good: the simulator and the application share protocol code without depending on each
  other.
- Good: every module's build file shows which conventions it uses; there is no hidden
  configuration from the root.
- Bad: more build files than a single module, and Gradle knowledge is needed to change them.
- Bad: because `ocpp-protocol` does not use the Spring Boot BOM, versions it shares with the
  application (Jackson) must be kept aligned by hand. A build check will fail when they
  drift apart.
