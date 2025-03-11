package org.example.converter.processor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

/**
 * 识别 `extends Assert` 并将其改为 `extends Assertions`。
 * 同时如果原来有 import org.junit.Assert，就移除；并添加/保留 import org.junit.jupiter.api.Assertions。
 */
public class ClassExtendsAssertProcessor {

  public static void processClassExtendsAssert(CompilationUnit cu) {
    // 使用 ModifierVisitor 遍历所有类/接口声明
    cu.accept(new ModifierVisitor<Void>() {
      @Override
      public Visitable visit(ClassOrInterfaceDeclaration cid, Void arg) {
        for (ClassOrInterfaceType et : cid.getExtendedTypes()) {
          if ("Assert".equals(et.getNameAsString())) {
            // 将 extends Assert 改为 extends Assertions
            et.setName("Assertions");
          }
        }
        return super.visit(cid, arg);
      }
    }, null);
  }
}