package org.example.converter.processor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;

import java.util.Optional;

/**
 * 如果需要 @Timeout，则插入相关 import
 */
public class TimeoutImportAdder {

  public static void addTimeoutImportIfNeeded(CompilationUnit cu, boolean needTimeoutImport) {
    if (!needTimeoutImport) return;

    NodeList<ImportDeclaration> importList = cu.getImports();
    // 查找是否已经有 Timeout
    boolean hasTimeout = importList.stream()
        .anyMatch(id -> "org.junit.jupiter.api.Timeout".equals(id.getNameAsString()));
    if (!hasTimeout) {
      // 找一下是否有 @Test import，想插在它后面
      Optional<ImportDeclaration> testImportOpt = importList.stream()
          .filter(id -> "org.junit.jupiter.api.Test".equals(id.getNameAsString()))
          .findFirst();
      if (testImportOpt.isPresent()) {
        int testIndex = importList.indexOf(testImportOpt.get());
        importList.add(testIndex + 1,
            new ImportDeclaration("org.junit.jupiter.api.Timeout", false, false));
      } else {
        // 如果没找到 @Test，就直接加在末尾
        importList.add(new ImportDeclaration("org.junit.jupiter.api.Timeout", false, false));
      }
    }
  }
}