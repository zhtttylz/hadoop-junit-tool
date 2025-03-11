package org.example.converter.processor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.AnnotationExpr;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单独处理 @Test(timeout=xxx)，把它迁移到 JUnit5 的 @Timeout 注解上
 */
public class TestTimeoutProcessor {

  public static void processTestTimeout(CompilationUnit cu, AtomicBoolean needTimeoutImport) {
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
}