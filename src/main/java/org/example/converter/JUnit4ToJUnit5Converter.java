package org.example.converter;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithAnnotations;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.example.util.JUnitMigrationUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author zhtttylz
 * @date 2025/1/09 20:03
 */
public class JUnit4ToJUnit5Converter {

  public static String fileName = "/Users/didi/IdeaProjects/hadoop/hadoop-hdfs-project/hadoop-hdfs-rbf/src/test/java/org/apache/hadoop/hdfs/server/federation/router/TestRouterAdminCLI.java";


  // 需要交换两个参数
  final Set<String> swapTwoArgsMethods;

  // 需要交换三个参数
  final Set<String> shiftThreeArgsMethods;

  public JUnit4ToJUnit5Converter() {

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

  public void converter(Path path) throws IOException {
    String source = Files.readString(path);

    CompilationUnit cu = StaticJavaParser.parse(source);

    // 启用词法级保留打印，保留原始代码格式
    LexicalPreservingPrinter.setup(cu);

    // =============== 1. assert参数替换 ===============
    cu.accept(new VoidVisitorAdapter<Void>() {
      @Override
      public void visit(MethodCallExpr mce, Void arg) {
        super.visit(mce, arg);
        String methodName = mce.getNameAsString();

        // 处理2个参数的方法
        if (swapTwoArgsMethods.contains(methodName) && mce.getArguments().size() == 2) {
          var arg0 = mce.getArgument(0);
          var arg1 = mce.getArgument(1);

          // 判断如果已经是junit 5风格了，不进行替换
          // junit4 第一个参数是message，第二个参数是condition
          // 如果第一个参数是string，第二个参数不string，则认为是 JUnit4，进行替换
          if (JUnitMigrationUtils.isLikelyMessageParameter(arg0) &&
              !JUnitMigrationUtils.isLikelyMessageParameter(arg1)) {
            // 执行交换
            mce.setArgument(0, arg1);
            mce.setArgument(1, arg0);
          }
        }

        // 处理3个参数的方法
        if (shiftThreeArgsMethods.contains(methodName) && mce.getArguments().size() == 3) {
          var arg0 = mce.getArgument(0);
          var arg1 = mce.getArgument(1);
          var arg2 = mce.getArgument(2);

          // 判断是否是junit4风格 3个参数判断逻辑如下
          // 如果第一个参数是 String，第二第三不是 String，则进行移位
          if (JUnitMigrationUtils.isLikelyMessageParameter(arg0)
              && !JUnitMigrationUtils.isLikelyMessageParameter(arg1)
              && !JUnitMigrationUtils.isLikelyMessageParameter(arg2)) {
            // 参数位置如下交换 (0, 1, 2) -> (1, 2, 0)
            mce.setArgument(0, arg1);
            mce.setArgument(1, arg2);
            mce.setArgument(2, arg0);
          }
        }
      }
    }, null);

    // =============== 1. 注解处理，目前仅有 @Test(timeout=xxx) ===============
    AtomicBoolean needTimeoutImport = new AtomicBoolean(false);
    cu.accept(new ModifierVisitor<Void>() {
      @Override
      public Visitable visit(NormalAnnotationExpr nae, Void arg) {
        if ("Test".equals(nae.getNameAsString())) {
          // 查找 timeout=xxx
          nae.getPairs().stream()
              .filter(p -> "timeout".equals(p.getNameAsString()))
              .findFirst()
              .ifPresent(timeoutPair -> {
                Expression timeoutValue = timeoutPair.getValue();
                if (timeoutValue.isIntegerLiteralExpr()) {
                  // 1) 计算秒数
                  int timeoutMs = timeoutValue.asIntegerLiteralExpr().asInt();
                  int timeoutSec = timeoutMs / 1000;

                  // 2) 父节点肯定是声明方法
                  nae.getParentNode().ifPresent(parent -> {
                    if (parent instanceof MethodDeclaration) {
                      MethodDeclaration method = (MethodDeclaration) parent;
                      NodeList<AnnotationExpr> annos = method.getAnnotations();

                      // 3) 找到当前注解在 annos 里的位置
                      int idx = annos.indexOf(nae);

                      // 4) 构造新的 @Timeout
                      NormalAnnotationExpr timeoutAnno = new NormalAnnotationExpr();
                      timeoutAnno.setName("Timeout");
                      timeoutAnno.addPair("value", new IntegerLiteralExpr(timeoutSec));

                      // 5) 移除注解属性 timeout
                      nae.getPairs().remove(timeoutPair);

                      // 6) 如果已经没有别的属性，就把当前注解改成 @Test (Marker)
                      if (nae.getPairs().isEmpty()) {
                        MarkerAnnotationExpr onlyTest = new MarkerAnnotationExpr("Test");
                        // 替换列表里的这个注解
                        annos.set(idx, onlyTest);

                        // 在下一个位置插入 @Timeout
                        annos.add(idx + 1, timeoutAnno);
                      } else {
                        // 如果还有别的属性(例如 @Test(expected=xx, timeout=xx))，
                        // 我们只移除 timeout，对 @Test 不改
                        // 然后把 @Timeout 插在后面
                        annos.add(idx + 1, timeoutAnno);
                      }
                      needTimeoutImport.set(true);
                    }
                  });
                }
              });
        }

        // 最后再调用 super，让 JavaParser 继续往下访问
        return super.visit(nae, arg);
      }
    }, null);

    // =============== 3. 在 Test 后面紧挨着插入 Timeout注解 ===============
    if (needTimeoutImport.get()) {
      // 获取全部 import 列表
      NodeList<ImportDeclaration> importList = cu.getImports();
      // 找到刚的 Test import
      Optional<ImportDeclaration> testImportOpt = importList.stream()
          .filter(id -> "org.junit.jupiter.api.Test".equals(id.getNameAsString()))
          .findFirst();

      if (testImportOpt.isPresent()) {
        ImportDeclaration testImport = testImportOpt.get();

        // 检查是否已经有 Timeout import
        boolean hasTimeout = importList.stream()
            .anyMatch(id -> "org.junit.jupiter.api.Timeout".equals(id.getNameAsString()));

        // 若没有则插在 Test 的后面
        if (!hasTimeout) {
          ImportDeclaration timeoutImport =
              new ImportDeclaration("org.junit.jupiter.api.Timeout", false, false);
          int testIndex = importList.indexOf(testImport);
          importList.add(testIndex + 1, timeoutImport);
        }
      }
    }
    Files.writeString(path, LexicalPreservingPrinter.print(cu));
  }


  public static void main(String[] args) throws IOException {
    JUnit4ToJUnit5Converter converter = new JUnit4ToJUnit5Converter();
    converter.converter(Path.of(fileName));
  }
}
