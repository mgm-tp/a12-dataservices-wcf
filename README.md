<picture>
  <source media="(prefers-color-scheme: dark)" srcset="https://www.mgm-tp.com/global-content/cd/logos/a12/app-icons/dark/A12-Dark.svg" />
  <img src="https://www.mgm-tp.com/global-content/cd/logos/a12/app-icons/light/A12-Light.svg" height="200" alt="A12 logo" />
</picture>

# WCF – Workspace Conversion Framework

WCF (Workspace Conversion Framework) is a lightweight, extensible Spring Boot based pipeline that transforms a "workspace" (JSON model files plus auxiliary artifacts) into structured seed data via ordered converter steps.

Refer to https://geta12.com/#/docs to get started with A12 development

---

## License

Parts of the A12 platform are made available under a **dual license**.  
Please check the [LICENSE](./LICENSE) file for details.

---

## Getting Started

### How to Use It

#### Import & Install

Add the WCF API dependency to compile against the SPI (converters, domain types):

**Gradle:**
```gradle
dependencies {
    implementation 'com.mgmtp.a12.dataservices.wcf:dataservices-wcf-api:<version>'
}
```

**Maven:**
```xml
<dependency>
    <groupId>com.mgmtp.a12.dataservices.wcf</groupId>
    <artifactId>dataservices-wcf-api</artifactId>
    <version>${wcf.version}</version>
</dependency>
```

To embed the full pipeline (supplier + conversion service + consumer), also add `dataservices-wcf-core`.  
To use the CLI directly, download the `dataservices-wcf-cli` fat JAR from the artifact repository.

---

### How to Build and Run

#### Prerequisites

- JDK 21+
- Gradle Wrapper (bundled, no installation required)

#### How to Build

```bash
./gradlew assemble
```

#### How to Test

```bash
./gradlew check
```

#### How to Run

```bash
java -jar dataservices-wcf-cli/build/libs/dataservices-wcf-cli-<version>.jar \
  <WORKSPACE_DIR> <SEED_DATA_DIR> \
  -c path/to/converters.jar
```

See [CLI Usage](#cli-usage) for full options and [Converter Modules — Best Practices](#converter-modules--best-practices) for packaging converter jars.

#### How to Access It

WCF is a command-line tool with no web UI. Invoke the CLI jar directly as shown above.  
Output is written to the seed data directory specified as the second argument.

---

### Documentation

- Full technical documentation is available at [GetA12.com](https://geta12.com).
- The website also provides access to the **A12 Discourse Community Forum**.

---

## Modules

| Module | Description |
| ------ | ----------- |
| dataservices-wcf-api  | Public SPI: domain interfaces, `WorkspaceConverter`, `WorkspaceFactory`, `@WcfConverter`. |
| dataservices-wcf-core | Default Spring components (supplier, conversion service, consumer) + internal POJOs. |
| dataservices-wcf-cli  | Command line interface (fat JAR) that loads external converter modules, each in an isolated class loader + child Spring context. |

## Core Concepts

### Workspace
Logical aggregation:
- Models (parsed JSON + header metadata)
- Files (binary or pass-through)
- Input directory reference (for read-only access by converters)

### Tuples
- `ModelTuple`: `Header` + JSON content string
- `FileTuple`: target output path + optional byte[] content (null ⇒ copy original)

### Converters
Implement `WorkspaceConverter` and are discovered via `@WcfConverter(order=..., description=...)`.

Responsibilities may include:
- Model migration / normalization
- Enrichment
- Synthetic file generation
- Validation / pruning

### Pipeline
1. `DefaultWorkspaceSupplier` scans input recursively:
   * Parses `*.json` with `HeaderParser`
   * Falls back to file tuple on parse failure
2. Ordered converters mutate or replace the `Workspace`
3. `DefaultWorkspaceConsumer` writes:
   * Models → `data/models/<modelId>.json`
   * Files → declared output paths (under seed data root)

## Autoconfiguration

`dataservices-wcf-core` provides:
- `DefaultWorkspaceSupplier`
- `DefaultWorkspaceConsumer`
- `DefaultWorkspaceConversionService`

All annotated with `@Component` (ensure package `com.mgmtp.a12` is scanned).

Injection:
```java
@Autowired
WorkspaceConversionService service;
```

## Library / Embedded Usage

When WCF is consumed as a library (converters already on the launch classpath as ordinary
dependencies), use the supported programmatic entry point instead of hand-rolling a
`@SpringBootApplication`:

```java
import com.mgmtp.a12.dataservices.wcf.WcfRunner;

WcfRunner.run(inputWorkspaceDir, outputSeedDataDir);
```

`WcfRunner` owns the Spring bootstrap: it boots a non-web context, component-scans
`com.mgmtp.a12` so that `@WcfConverter` converters on the classpath are discovered, applies
the WCF auto-configuration for the default beans, runs the pipeline, and closes the context.
Relative paths are resolved against the current working directory and the output directory is
created if missing.

This is the recommended path for embedding WCF (e.g. running it in a forked JVM from a build
plugin). No external converters JAR (`-c`) is involved — converters are discovered the normal
way via component scan.

## Writing a Converter

```java
@WcfConverter(order = 50, description = "Add generated README")
public class AddReadme implements WorkspaceConverter {
  private final WorkspaceFactory f = WorkspaceFactory.getInstance();
  @Override
  public Workspace convert(Workspace ws) {
    ws.getFiles().put(
      "synthetic:readme",
      f.createFileTuple("generated/README.txt", "Hello Workspace".getBytes())
    );
    return ws;
  }
}
```

Package into a **flat fat-jar** and pass with `-c`. See [Converter Modules — Best Practices](#converter-modules--best-practices) for how to build and configure the module jar correctly.

## Converter Modules — Best Practices

A converter module is a self-contained jar passed to the CLI via `-c`. The CLI loads each module in its **own class loader** and its **own child Spring context**, so a module may bundle dependencies the CLI does not have (e.g. Groovy) without polluting the host. Follow the rules below or you will hit class-loading errors.

### 1. Package as a flat fat-jar (not a Spring Boot jar)

Bundle the module and its private dependencies into a single jar whose classes and dependency classes live **at the jar root** — i.e. an *uber/shaded* jar produced by the **Gradle Shadow** or **Maven Shade** plugin.

- ✅ Gradle Shadow (`shadowJar`), Maven Shade, or a plain jar.
- ❌ **Spring Boot `bootJar` is rejected.** Its nested `BOOT-INF/lib/*.jar` layout cannot be read by a `URLClassLoader`; the CLI fails fast with a clear message telling you to repackage as a flat fat-jar.

### 2. Do NOT bundle what the CLI already provides

The CLI loads its module class loaders **parent-first**: any class the CLI ships is loaded from the CLI, and its version wins. A module may *add* libraries the CLI lacks, but it **cannot override** a library the CLI already ships. Bundling host libraries at a different version does not give you that version — it only risks `LinkageError` / `NoSuchMethodError` at runtime.

Declare everything the host provides as `compileOnly` (compile against it, do not ship it). Declare only genuinely private dependencies as `implementation` (these get shaded into the fat-jar).

Host-provided (use `compileOnly`, never bundle):

- `dataservices-wcf-api` — the WCF SPI (`WorkspaceConverter`, `@WcfConverter`, `WorkspaceFactory`, domain types)
- Spring (Spring Framework + Spring Boot) — a single Spring runtime is shared
- Jackson (`tools.jackson.*` / `com.fasterxml.jackson.*`) — leaked through the A12 model API
- A12 `base-model-api` (`com.mgmtp.a12.model.*`)
- SLF4J / logging

Private (use `implementation`, bundled): anything the CLI does **not** ship, e.g. Groovy, XSD/XML tooling, your own helper libraries.

> Build against the **same versions** the target CLI ships (Spring Boot, Spring, Jackson, A12 base). Inspect them with `:dataservices-wcf-cli:dependencies --configuration runtimeClasspath`. Mismatched majors are the usual cause of runtime `LinkageError`.

### 3. Example `build.gradle` (Gradle Shadow)

```gradle
plugins {
  id 'java'
  id 'com.gradleup.shadow' version '<latest>'
}

dependencies {
  // Host-provided — compile against, do NOT bundle:
  compileOnly 'com.mgmtp.a12.dataservices.wcf:dataservices-wcf-api:<version>'
  compileOnly 'org.springframework.boot:spring-boot-starter:<host-version>'
  compileOnly 'com.mgmtp.a12.base:base-model-api:<host-version>'
  // (Jackson, slf4j come transitively / are host-provided too)

  // Private — bundled into the shaded jar:
  implementation 'org.apache.groovy:groovy:<version>'
}

// shadowJar produces the flat fat-jar to pass with -c
```

### 4. Converter contract (what the module context scans)

The CLI component-scans **only the packages that contain your `@WcfConverter` classes**, with the default stereotype filters but **excluding `@Configuration`**. Consequences:

- ✅ A converter is a **type-level** `@WcfConverter` class implementing `WorkspaceConverter`.
- ✅ The converter's own `@Component` / `@Service` / `@Repository` collaborators are picked up and injected — **as long as they live in (or under) the converter's package**.
- ✅ Shared beans (e.g. `WorkspaceFactory`) are injectable from the host context.
- ❌ `@Configuration` classes, `@Bean` factory methods, method-level `@WcfConverter`, `@SpringBootApplication`, `@EnableAsync`/`@EnableScheduling` and other `@Enable*` are **ignored**. The module is a plugin contributing converters, not a standalone application — do not rely on application bootstrap inside it. Build collaborators as plain stereotype beans or construct them yourself.

### 5. Loading bundled resources

Resolve classpath resources with the converter's **own** class loader, not assumptions about the thread context class loader:

```java
ClassLoader cl = getClass().getClassLoader();
try (InputStream in = cl.getResourceAsStream("model/MyConfig.json")) { ... }
// or, relative to the class:  getClass().getResourceAsStream("/model/MyConfig.json")
```

Make sure the resource is actually packaged in the fat-jar (check the shaded jar contents). The CLI also aligns the thread context class loader with the module during `convert()`, so TCCL-based lookups in third-party libraries work too — but `getClass().getClassLoader()` is the portable choice.

### 6. Multiple modules

`-c` is repeatable: `... -c a.jar -c b.jar`. Each jar is isolated in its own loader/context. Converters from all modules are merged and executed in **global `@WcfConverter(order = ...)` order**.

### 7. Keep converters thread-safe & stateless

See [Thread Safety Guidelines](#thread-safety-guidelines). Avoid mutable static state; a converter may be reused.

## CLI Usage

Build CLI:
```bash
./gradlew :dataservices-wcf-cli:bootJar
```

Run:
```bash
java -jar dataservices-wcf-cli/build/libs/dataservices-wcf-cli-<version>.jar \
  <WORKSPACE_DIR> <SEED_DATA_DIR> \
  -c path/to/converters.jar
```

Notes:
- Relative paths resolved against current working directory (CWD unchanged by launcher).
- Each converter jar is loaded in an **isolated** class loader + child Spring context (not appended to the host classpath). See [Converter Modules — Best Practices](#converter-modules--best-practices).
- `-c` is repeatable to load several modules: `-c a.jar -c b.jar`.
- `-c` is **optional**. Omit it when converters are already on the launch classpath (library mode); the CLI delegates to `WcfRunner` and skips the isolated-loader path entirely.

## Test Converters Bundle

Create fat JAR with test converters + external deps (excluding api/core):
```bash
./gradlew :dataservices-wcf-core:testConvertersJar
```

Use:
```bash
java -jar dataservices-wcf-cli/build/libs/dataservices-wcf-cli-<version>.jar \
  samples/workspace out/seed \
  -c dataservices-wcf-core/build/libs/dataservices-wcf-core-<version>-converters.jar
```

## Output Layout (Example)

```
out/seed/
  data/
    models/
      modelA.json
      modelB.json
  generated/
    README.txt
  assets/
    copy.bin
```

## WorkspaceFactory

Thread-safe lazy singleton (`WorkspaceFactory.getInstance()`). Creates plain POJOs—no heavy state.

## Error Handling

| Case | Behavior |
|------|----------|
| Missing input dir | CLI parameter error |
| Converter returns null | Fails fast |
| Duplicate output path | `OutputConflictException` |
| Header parse failure | Stored as file tuple (copy-through) |

## Thread Safety Guidelines

- Converters should be stateless or internally synchronized
- Avoid mutable static fields
- Use thread-safe caches if needed

## Logging

Use descriptive `description` in `@WcfConverter` for clearer pipeline traces.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `No qualifying bean WorkspaceConversionService` | Package not scanned | Adjust `@SpringBootApplication` base package |
| Converter not executed | Missing on classpath / annotation | Verify `-c` path & `@WcfConverter` |
| Overwrite conflict | Duplicate output path | Rename / pre-check existence |
| `LinkageError` / `NoSuchMethodError` / loader-constraint violation | Module bundles a different version of a host library (Spring, Jackson, A12 model, WCF SPI) | Mark host libraries `compileOnly`; rebuild the module against the versions the CLI ships. The CLI now reports this with a clear version-conflict message. |
| `@EnableAsync annotation metadata was not injected` (or other `@Enable*`/auto-config errors) | Module is a full Spring Boot app jar; its `@Configuration`/`@EnableAsync` tried to bootstrap inside the module context | Already handled — `@Configuration` is excluded from the module scan. If seen, you are on an **old CLI build**; upgrade the CLI. |
| `Spring Boot fat-jars are not supported ...` | Module packaged as a Spring Boot `bootJar` | Repackage as a flat fat-jar (Gradle Shadow / Maven Shade). |
| Bundled resource `... cannot be found` | Resource not packaged, or loaded via wrong class loader | Ensure the resource is in the fat-jar; load via `getClass().getClassLoader().getResourceAsStream(...)`. |

## Extensibility Ideas

- Conditional execution
- Parallel (DAG) pipeline
- Incremental updates
- Metrics / tracing hooks

---

**The mgm A12 Team**

[mgm technology partners GmbH](https://www.mgm-tp.com) | [Imprint](https://www.mgm-tp.com/imprint.html)
