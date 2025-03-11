package org.example.converter.processor;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单独处理 @Test(expected=xxx)，转换为 JUnit5 的 assertThrows(...)
 */
public class TestExpectedProcessor {

  public static void processTestExpected(CompilationUnit cu, AtomicBoolean needAssertionsImport) {
    cu.accept(new ModifierVisitor<Void>() {
      @Override
      public Visitable visit(NormalAnnotationExpr nae, Void arg) {
        if ("Test".equals(nae.getNameAsString())) {
          nae.getPairs().stream()
              .filter(p -> "expected".equals(p.getNameAsString()))
              .findFirst()
              .ifPresent(expectedPair -> {
                Expression expectedValue = expectedPair.getValue();

                nae.getParentNode().ifPresent(parent -> {
                  if (parent instanceof MethodDeclaration) {
                    MethodDeclaration method = (MethodDeclaration) parent;

                    if (expectedValue.isClassExpr()) {
                      String exceptionType = expectedValue.asClassExpr().getType().asString();

                      // 把原方法体包裹到 Assertions.assertThrows(...) 中
                      method.getBody().ifPresent(oldBody -> {
                        // 备份所有语句
                        BlockStmt oldStmts = new BlockStmt();
                        for (Statement st : oldBody.getStatements()) {
                          oldStmts.addStatement(st.clone());
                        }
                        oldBody.getStatements().clear();

                        // 构造 Assertions.assertThrows(...)
                        MethodCallExpr assertThrowsCall = new MethodCallExpr(
                            new NameExpr("Assertions"), // 也可改为静态引用
                            "assertThrows"
                        );
                        // 第一个参数
                        assertThrowsCall.addArgument(
                            new ClassExpr(StaticJavaParser.parseType(exceptionType))
                        );
                        // 第二个参数: () -> { 原先方法体的所有语句 }
                        LambdaExpr lambda = new LambdaExpr();
                        lambda.setEnclosingParameters(true);
                        BlockStmt lambdaBody = new BlockStmt();
                        oldStmts.getStatements().forEach(lambdaBody::addStatement);
                        lambda.setBody(lambdaBody);
                        assertThrowsCall.addArgument(lambda);

                        oldBody.addStatement(new ExpressionStmt(assertThrowsCall));

                        needAssertionsImport.set(true);
                      });
                    }

                    // 移除 @Test(expected=xxx) 中的 expected 属性
                    nae.getPairs().remove(expectedPair);
                    // 若移除后空了，就变成纯 Marker @Test
                    if (nae.getPairs().isEmpty()) {
                      NodeList<AnnotationExpr> annos = method.getAnnotations();
                      int idx = annos.indexOf(nae);
                      MarkerAnnotationExpr markerTest = new MarkerAnnotationExpr("Test");
                      annos.set(idx, markerTest);
                    }
                  }
                });
              });
        }
        return super.visit(nae, arg);
      }
    }, null);
  }
}