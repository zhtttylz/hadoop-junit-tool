package org.example.converter.processor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

/**
 * 如果需要 Assertions（因 assertThrows）则插入相关 import
 */
public class AssertionsImportAdder {

  public static void addAssertionsImportIfNeeded(CompilationUnit cu, boolean needAssertionsImport) {
    if (!needAssertionsImport) return;

    NodeList<ImportDeclaration> importList = cu.getImports();
    boolean hasAssertions = importList.stream()
        .anyMatch(id -> "org.junit.jupiter.api.Assertions".equals(id.getNameAsString()));
    if (!hasAssertions) {
      importList.add(new ImportDeclaration("org.junit.jupiter.api.Assertions", false, false));
    }
  }
}