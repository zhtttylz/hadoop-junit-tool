package org.example.converter.processor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

/**
 * 处理 org.assertj.core.api.Assertions 和 org.junit.jupiter.api.Assertions 重名问题
 */
public class RedundantAssertionsImportProcessor {

  public static void processRedundantAssertionsImport(CompilationUnit cu) {
    NodeList<ImportDeclaration> importList = cu.getImports();
    boolean hasAssertJAssertions = false;
    boolean hasJUnitAssertions = false;
    boolean hasStaticAssertThat = false;

    // 检查是否有 org.assertj.core.api.Assertions 和 org.junit.jupiter.api.Assertions 导入
    for (ImportDeclaration id : importList) {
      String importName = id.getNameAsString();
      if ("org.assertj.core.api.Assertions".equals(importName)) {
        hasAssertJAssertions = true;
      } else if ("org.junit.jupiter.api.Assertions".equals(importName)) {
        hasJUnitAssertions = true;
      } else if (id.isStatic() &&
          "org.assertj.core.api.Assertions.assertThat".equals(importName)) {
        hasStaticAssertThat = true;
      }
    }

    // 如果同时存在两个导入，删除 JUnit 的 Assertions 导入
    if (hasAssertJAssertions && hasJUnitAssertions) {
      importList.removeIf(id -> "org.assertj.core.api.Assertions".equals(id.getNameAsString()));

      // 替换代码中的 Assertions.assertThat 为 assertThat
      cu.accept(new ModifierVisitor<Void>() {
        @Override
        public Visitable visit(MethodCallExpr mce, Void arg) {
          if ("assertThat".equals(mce.getNameAsString()) &&
              mce.getScope().isPresent() && mce.getScope().get() instanceof NameExpr) {
            NameExpr scope = mce.getScope().get().asNameExpr();
            if ("Assertions".equals(scope.getNameAsString())) {
              mce.removeScope();
            }
          }
          return super.visit(mce, arg);
        }
      }, null);

      // 如果不存在添加静态导入语句，则新增导入
      if (!hasStaticAssertThat) {
        importList.add(new ImportDeclaration("org.assertj.core.api.Assertions.assertThat",
            true, false));
      }
    }
  }
}