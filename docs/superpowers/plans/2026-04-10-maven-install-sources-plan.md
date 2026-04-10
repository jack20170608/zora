# Maven Install Sources Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure the project so that running `mvn install` automatically builds and installs the source JAR along with the compiled JAR to the local Maven repository.

**Architecture:** Add the `maven-source-plugin` configuration globally in the root `pom.xml` so that all modules inherit the configuration automatically. The plugin binds to the `verify` phase which runs before `install`, ensuring sources are built when `install` is executed.

**Tech Stack:** Maven, maven-source-plugin 3.3.1

---

### Task 1: Add maven-source-plugin configuration to root pom.xml

**Files:**
- Modify: `pom.xml:50-72`

- [ ] **Step 1: Add plugin configuration**

Add the following `maven-source-plugin` configuration after `maven-compiler-plugin` in the root `pom.xml`:

```xml
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-source-plugin</artifactId>
                <version>3.3.1</version>
                <executions>
                    <execution>
                        <phase>verify</phase>
                        <goals>
                            <goal>jar-no-fork</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
```

- [ ] **Step 2: Verify the change is correct**

Check that the XML is well-formed and the plugin is added in the correct location inside `<plugins>` section.

- [ ] **Step 3: Commit the change**

```bash
git add pom.xml
git commit -m "feat: add maven-source-plugin to install sources during mvn install"
```

### Task 2: Test the configuration

**Files:**
- No file changes, just test the build

- [ ] **Step 1: Run a dry build to verify the configuration**

Run: `mvn clean install -DskipTests`

Expected: Build completes successfully, and for each jar module, a `*-sources.jar` file is generated in the `target/` directory and installed to the local Maven repository.

