package org.example;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.nio.file.Files;
import java.nio.file.Path;

public class AssertTransformerTest {

  public static String fileName = "/Users/didi/IdeaProjects/hadoop/hadoop-hdfs-project/hadoop-hdfs-rbf/src/test/java/org/apache/hadoop/hdfs/server/federation/router/TestRouterAllResolver.java";

  public static void main(String[] args) throws Exception {
    Path path = Path.of(fileName);
    String source = Files.readString(path);

    CompilationUnit cu = StaticJavaParser.parse(source);

    LexicalPreservingPrinter.setup(cu);

    cu.accept(new VoidVisitorAdapter<Void>() {
      @Override
      public void visit(MethodCallExpr mce, Void arg) {
        super.visit(mce, arg);
        // 如果调用了assertTrue方法
        if ("assertTrue".equals(mce.getNameAsString())) {
          // 参数为两个
          if (mce.getArguments().size() == 2) {
            // 将两个参数互换
            var arg0 = mce.getArgument(0);
            var arg1 = mce.getArgument(1);
            mce.setArgument(0, arg1);
            mce.setArgument(1, arg0);
          }
        }
      }
    }, null);

    Files.writeString(path, LexicalPreservingPrinter.print(cu));
  }
}
