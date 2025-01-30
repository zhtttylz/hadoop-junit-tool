package org.example.converter;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.example.util.JUnitMigrationUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class JUnit4ToJUnit5ConverterBeta {

  public static String fileName = "/Users/didi/IdeaProjects/hadoop/hadoop-hdfs-project/hadoop-hdfs-rbf/src/test/java/org/apache/hadoop/hdfs/server/federation/router/TestRouterAdminCLI.java";

  // 需要交换两个参数
  final Set<String> swapTwoArgsMethods;

  // 需要交换三个参数
  final Set<String> shiftThreeArgsMethods;

  public JUnit4ToJUnit5ConverterBeta() {
    shiftThreeArgsMethods = Set.of(
        "assertEquals",
        "assertNotEquals",
        "assertSame",
        "assertNotSame",
        "assertArrayEquals"
    );

    swapTwoArgsMethods = Set.of(
        "assertTrue",
        "assertFalse",
        "assertNull",
        "assertNotNull"
    );
  }

  /**
   * 对外主入口
   */
  public void converter(Path path) throws IOException {
    String source = Files.readString(path);
    CompilationUnit cu = StaticJavaParser.parse(source);
    // 启用词法级保留打印，保留原始代码格式
    LexicalPreservingPrinter.setup(cu);

    // 先处理那些 `extends Assert` 的类
    processClassExtendsAssert(cu);

    // 1) 先对断言方法(如 assertEquals) 做参数迁移
    processAssertArguments(cu);

    // 2) 独立处理 @Test(timeout=xxx)
    AtomicBoolean needTimeoutImport = new AtomicBoolean(false);
    processTestTimeout(cu, needTimeoutImport);

    // 3) 独立处理 @Test(expected=xxx)
    AtomicBoolean needAssertionsImport = new AtomicBoolean(false);
    processTestExpected(cu, needAssertionsImport);

    // 4) 根据需要插入 import
    addTimeoutImportIfNeeded(cu, needTimeoutImport.get());
    addAssertionsImportIfNeeded(cu, needAssertionsImport.get());

    // 最终写回文件
    Files.writeString(path, LexicalPreservingPrinter.print(cu));
  }

  /**
   * 新增功能：
   * 识别 `extends Assert` 并将其改为 `extends Assertions`。
   * 同时如果原来有 import org.junit.Assert，就移除；并添加/保留 import org.junit.jupiter.api.Assertions。
   */
  private void processClassExtendsAssert(CompilationUnit cu) {

    // 使用 ModifierVisitor 遍历所有类/接口声明
    cu.accept(new ModifierVisitor<Void>() {
      @Override
      public com.github.javaparser.ast.visitor.Visitable visit(ClassOrInterfaceDeclaration cid, Void arg) {
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

  /**
   * 1) 处理断言方法的参数迁移 (JUnit4 -> JUnit5)
   * - 若方法是 {@code assertTrue("message", condition)}，则改为 {@code assertTrue(condition, "message")} 等
   */
  private void processAssertArguments(CompilationUnit cu) {
    cu.accept(new VoidVisitorAdapter<Void>() {
      @Override
      public void visit(MethodCallExpr mce, Void arg) {
        super.visit(mce, arg);
        String methodName = mce.getNameAsString();

        // 处理2个参数的方法
        if (swapTwoArgsMethods.contains(methodName) && mce.getArguments().size() == 2) {
          var arg0 = mce.getArgument(0);
          var arg1 = mce.getArgument(1);

          // 若第一个参数是字符串，第二个不是，则认为是JUnit4风格，需要交换
          if (JUnitMigrationUtils.isLikelyMessageParameter(arg0)
              && !JUnitMigrationUtils.isLikelyMessageParameter(arg1)) {
            mce.setArgument(0, arg1);
            mce.setArgument(1, arg0);
          }
        }

        // 处理3个参数的方法
        if (shiftThreeArgsMethods.contains(methodName) && mce.getArguments().size() == 3) {
          var arg0 = mce.getArgument(0);
          var arg1 = mce.getArgument(1);
          var arg2 = mce.getArgument(2);

          // 若第一个参数是字符串，其他两个不是，则移位
          if (JUnitMigrationUtils.isLikelyMessageParameter(arg0)
              && !JUnitMigrationUtils.isLikelyMessageParameter(arg1)
              && !JUnitMigrationUtils.isLikelyMessageParameter(arg2)) {
            // (0,1,2) -> (1,2,0)
            mce.setArgument(0, arg1);
            mce.setArgument(1, arg2);
            mce.setArgument(2, arg0);
          }
        }
      }
    }, null);
  }

  /**
   * 2) 单独处理 @Test(timeout=xxx)，把它迁移到 JUnit5 的 @Timeout 注解上
   */
  private void processTestTimeout(CompilationUnit cu, AtomicBoolean needTimeoutImport) {
    cu.accept(new ModifierVisitor<Void>() {
      @Override
      public Visitable visit(NormalAnnotationExpr nae, Void arg) {
        if ("Test".equals(nae.getNameAsString())) {
          nae.getPairs().stream()
              .filter(p -> "timeout".equals(p.getNameAsString()))
              .findFirst()
              .ifPresent(timeoutPair -> {
                Expression timeoutValue = timeoutPair.getValue();
                if (timeoutValue.isIntegerLiteralExpr()) {
                  int timeoutMs = timeoutValue.asIntegerLiteralExpr().asInt();
                  int timeoutSec = timeoutMs / 1000;

                  nae.getParentNode().ifPresent(parent -> {
                    if (parent instanceof MethodDeclaration) {
                      MethodDeclaration method = (MethodDeclaration) parent;
                      NodeList<AnnotationExpr> annos = method.getAnnotations();

                      int idx = annos.indexOf(nae);

                      // 构造新的 @Timeout(xxx)
                      NormalAnnotationExpr timeoutAnno = new NormalAnnotationExpr();
                      timeoutAnno.setName("Timeout");
                      timeoutAnno.addPair("value", new IntegerLiteralExpr(timeoutSec));

                      // 移除 timeout=xxx 这个属性
                      nae.getPairs().remove(timeoutPair);

                      // 如果 @Test(...) 中已无其它属性，就改成 Marker @Test
                      if (nae.getPairs().isEmpty()) {
                        MarkerAnnotationExpr markerTest = new MarkerAnnotationExpr("Test");
                        annos.set(idx, markerTest);
                        annos.add(idx + 1, timeoutAnno);
                      } else {
                        // 否则保留 @Test(...) 中的其它属性
                        // 并在后面插入 @Timeout
                        annos.add(idx + 1, timeoutAnno);
                      }
                      needTimeoutImport.set(true);
                    }
                  });
                }
              });
        }
        return super.visit(nae, arg);
      }
    }, null);
  }

  /**
   * 3) 单独处理 @Test(expected=xxx)，转换为 JUnit5 的 assertThrows(...)
   */
  private void processTestExpected(CompilationUnit cu, AtomicBoolean needAssertionsImport) {
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

  /**
   * 4) 如果需要 @Timeout，则插入相关 import
   */
  private void addTimeoutImportIfNeeded(CompilationUnit cu, boolean needTimeoutImport) {
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
            new ImportDeclaration("org.junit.jupiter.api.Timeout", false,
                false));
      } else {
        // 如果没找到 @Test，就直接加在末尾
        importList.add(new ImportDeclaration("org.junit.jupiter.api.Timeout",
            false, false));
      }
    }
  }

  /**
   * 5) 如果需要 Assertions（因 assertThrows）则插入相关 import
   */
  private void addAssertionsImportIfNeeded(CompilationUnit cu, boolean needAssertionsImport) {
    if (!needAssertionsImport) return;

    NodeList<ImportDeclaration> importList = cu.getImports();
    boolean hasAssertions = importList.stream()
        .anyMatch(id -> "org.junit.jupiter.api.Assertions".equals(id.getNameAsString()));
    if (!hasAssertions) {
      importList.add(new ImportDeclaration("org.junit.jupiter.api.Assertions",
          false, false));
    }
  }

  public static void main(String[] args) throws IOException {
    JUnit4ToJUnit5Converter converter = new JUnit4ToJUnit5Converter();
    converter.converter(Path.of(fileName));
  }
}
