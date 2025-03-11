package org.example.converter.processor.RuleAnnotate;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class RuleTimeoutProcessor {

  public static void processTimeoutRule(CompilationUnit cu) {
    // 遍历所有字段声明
    cu.findAll(FieldDeclaration.class).forEach(field -> {
      // 判断该字段是否有 @Rule 注解且类型为 Timeout
      boolean hasRuleAnnotation = field.getAnnotations().stream()
          .anyMatch(a -> a.getNameAsString().equals("Rule"));

      boolean isTimeoutType = field.getElementType().asString().equals("Timeout");
      if (hasRuleAnnotation && isTimeoutType) {
        // 获取字段中定义的变量（通常只有一个）
        VariableDeclarator var = field.getVariable(0);
        Optional<Expression> initializerOpt = var.getInitializer();
        if (initializerOpt.isPresent() && initializerOpt.get() instanceof ObjectCreationExpr) {
          ObjectCreationExpr creationExpr = (ObjectCreationExpr) initializerOpt.get();
          // 超时规则一般只有一个参数，例如 new Timeout(30 * 1000)
          if (creationExpr.getArguments().size() == 1) {
            Expression arg = creationExpr.getArgument(0);
            long timeoutMillis = evaluateTimeoutExpression(arg);
            if (timeoutMillis > 0) {
              // 将毫秒转换为秒
              long timeoutSeconds = timeoutMillis / 1000;
              // 移除 JUnit4 的 Timeout rule 字段
              field.remove();

              // 找到包含该字段的类，然后为该类中每个测试方法添加 @Timeout 注解
              Optional<ClassOrInterfaceDeclaration> parentClassOpt =
                  field.findAncestor(ClassOrInterfaceDeclaration.class);
              if (parentClassOpt.isPresent()) {
                ClassOrInterfaceDeclaration parentClass = parentClassOpt.get();
                // 遍历该类中的所有方法
                parentClass.findAll(MethodDeclaration.class).forEach(method -> {
                  // 假定带有 @Test 注解的方法为测试方法
                  boolean isTestMethod = method.getAnnotations().stream()
                      .anyMatch(a -> a.getNameAsString().equals("Test"));
                  if (isTestMethod) {
                    // 如果方法上还没有 @Timeout 注解，则添加
                    boolean hasTimeoutAnnotation = method.getAnnotations().stream()
                        .anyMatch(a -> a.getNameAsString().equals("Timeout"));
                    if (!hasTimeoutAnnotation) {
                      AnnotationExpr timeoutAnnotation = new SingleMemberAnnotationExpr(
                          new Name("Timeout"),
                          new IntegerLiteralExpr(String.valueOf(timeoutSeconds))
                      );
                      method.addAnnotation(timeoutAnnotation);
                    }
                  }
                });
              }

              // 修改导入：移除 JUnit4 的 Timeout 导入，添加 JUnit5 的 Timeout 导入（如果尚未添加）
              cu.getImports().removeIf(imp -> imp.getNameAsString().equals("org.junit.rules.Timeout"));
              // 移除 JUnit4 的 Rule 导入
              cu.getImports().removeIf(imp -> imp.getNameAsString().equals("org.junit.Rule"));
              if (cu.getImports().stream().noneMatch(imp -> imp.getNameAsString().equals("org.junit.jupiter.api.Timeout"))) {
                cu.addImport("org.junit.jupiter.api.Timeout");
              }
            }
          }
        }
      }
    });
  }

  /**
   * 辅助方法：计算表达式的值。
   * 支持整数字面量和简单的乘法运算（例如：30 * 1000）。
   *
   * @param expr 表达式
   * @return 表达式的数值，如果无法计算则返回 0
   */
  private static long evaluateTimeoutExpression(Expression expr) {
    if (expr.isIntegerLiteralExpr()) {
      try {
        // 去除下划线后再解析
        String value = expr.asIntegerLiteralExpr().getValue().replace("_", "");
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        return 0;
      }
    } else if (expr.isBinaryExpr()) {
      BinaryExpr binaryExpr = expr.asBinaryExpr();
      // 这里只支持乘法运算
      if (binaryExpr.getOperator() == BinaryExpr.Operator.MULTIPLY) {
        long left = evaluateTimeoutExpression(binaryExpr.getLeft());
        long right = evaluateTimeoutExpression(binaryExpr.getRight());
        return left * right;
      }
    }
    // 如果不能识别则返回 0
    return 0;
  }

  public static void main(String[] args) throws IOException {
    String testClass = "/Users/didi/IdeaProjects/hadoop/hadoop-hdfs-project/hadoop-hdfs/src/test/java/org/apache/hadoop/hdfs/client/impl/TestBlockReaderFactory.java";
    String source = Files.readString(Path.of(testClass));
    CompilationUnit cu = StaticJavaParser.parse(source);
    // 启用词法级保留打印，保留原始代码格式
    LexicalPreservingPrinter.setup(cu);
    RuleTimeoutProcessor.processTimeoutRule(cu);
    //Files.writeString(Path.of(testClass), LexicalPreservingPrinter.print(cu));
  }
}
