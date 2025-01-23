# JUnit4 to JUnit5 Migration Tool

Welcome to the **JUnit4 to JUnit5 Converter**! 🚀 This tool helps you effortlessly migrate your JUnit4 test cases to JUnit5 with minimal manual intervention.



---

## 🎯 Features

- **Automatic Argument Swapping**: Transforms outdated JUnit4 `assert` methods to JUnit5 format.
- **Annotation Handling**:
  - Converts `@Test(timeout)` to JUnit5's `@Timeout`.
  - Preserves or adapts additional attributes seamlessly.
- **Lexical Preservation**: Retains original code formatting for clean diffs.
- **Easy Import Management**: Automatically adds required imports for JUnit5.
- **Batch Migration Script**: Automates migration of multiple files using a shell script.
- **Command-Line Integration**: Uses `StartConverterMain` as the entry point for orchestrating the migration process.

---

## 🛠️ How It Works

### Overall Workflow

1. **Locate Files to Update**:

   - The `StartConverterMain` class acts as the entry point.
   - It invokes `ModifyFileCmd` to find files containing JUnit4 imports and annotations.

2. **Initial Replacement**:

   - The shell script updates imports and annotations across all matching files.
   - Handles transformations like:
     - Replacing JUnit4 imports with JUnit5 equivalents.
     - Updating annotations such as `@Before` to `@BeforeEach`.

3. **AST Transformations**:

   - The Java code further refines the files using AST (Abstract Syntax Tree) analysis.
   - Handles more nuanced updates like swapping arguments and modifying `@Test(timeout)`.

4. **Batch Process**:

   - Combines file replacement and AST transformations for seamless migration.

---

### 1. Swap Arguments

- Handles methods like `assertTrue`, `assertFalse`, and more.
- Converts parameter order for clarity and compatibility.

**Example**:

```java
// Before
assertTrue("Expected condition to be true", condition);

// After
assertTrue(condition, "Expected condition to be true");
```

### 2. Shift Three Arguments

- Reorganizes `assertEquals`, `assertNotEquals`, etc., with three parameters.

**Example**:

```java
// Before
assertEquals("Message", expected, actual);

// After
assertEquals(expected, actual, "Message");
```

### 3. Handle `@Test(timeout)`

- Converts `timeout` to JUnit5's `@Timeout`.

**Example**:

```java
// Before
@Test(timeout = 1000)
public void testMethod() {
    // ...
}

// After
@Test
@Timeout(1)
public void testMethod() {
    // ...
}
```

### 4. Batch Migration Script

- A shell script for migrating entire directories of JUnit4 test files to JUnit5.

**Example**:

```bash
#!/bin/bash

# If no path is provided, print usage and exit
if [ -z "$1" ]; then
  echo "Usage: $0 <directory>"
  exit 1
fi

TARGET_DIR="$1"

grep -rlE "org.junit.[A-Z]" "$TARGET_DIR" --include '*.java' | while IFS= read -r FILE; do
  echo "$FILE"
  sed -i '' \
    -e 's/org.junit.After;/org.junit.jupiter.api.AfterEach;/' \
    -e 's/@After$/@AfterEach/' \
    -e 's/org.junit.AfterClass;/org.junit.jupiter.api.AfterAll;/' \
    -e 's/@AfterClass$/@AfterAll/' \
    -e 's/org.junit.Before;/org.junit.jupiter.api.BeforeEach;/' \
    -e 's/@Before$/@BeforeEach/' \
    -e 's/org.junit.BeforeClass;/org.junit.jupiter.api.BeforeAll;/' \
    -e 's/@BeforeClass$/@BeforeAll/' \
    -e 's/org.junit.Test/org.junit.jupiter.api.Test/' \
    -e 's/org.junit.Assert/org.junit.jupiter.api.Assertions/' \
    -e 's/junit.framework.TestCase/org.junit.jupiter.api.Assertions/' \
    -e 's/Assert\./Assertions./' \
    "$FILE"
done
```

---

## 🚀 Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/zhtttylz/junit4-to-junit5-converter.git
cd junit4-to-junit5-converter
```

### 2. Build and Run

```bash
mvn clean install
java -jar target/junit4-to-junit5-converter.jar
```

### 3. Configure Your File Path

Edit the `targetDir` variable in the `StartCoverterMain` class:
Edit the `scriptPath` variable in the `StartCoverterMain` class:

```java
public static String fileName = "";
String targetDir = "/hadoop/hadoop-hdfs-project/hadoop-hdfs-rbf/src/test/java";
```

### 4. Execute the Migration

Run the `main` method to transform your test cases:

```bash
java -cp target/junit4-to-junit5-converter.jar org.example.StartCoverterMain

---

## 📊 Diagrammatic Workflow

### **Migration Process Overview**



1. **Locate Files**: The shell script finds files containing JUnit4 references.
2. **Replace Imports and Annotations**: The script applies initial transformations.
3. **AST Transformations**: The Java code refines changes using AST analysis.
4. **Batch Process**: Handles multiple files efficiently.

---

## 📚 Resources

- [JUnit5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [JavaParser Library](https://javaparser.org/)

---

Happy Testing! 🎉

