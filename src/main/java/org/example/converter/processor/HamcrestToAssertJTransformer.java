package org.example.converter.processor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.util.Map;

/**
 * 识别并替换 `import static org.hamcrest.MatcherAssertions.assertThat;`
 * 为 `import static org.assertj.core.api.Assertions.assertThat;`
 * 同时将类似 `assertThat(obj, is(value))` → `assertThat(obj).isEqualTo(value)`
 */
public class HamcrestToAssertJTransformer {

  public static void transformHamcrestAssertToAssertJ(CompilationUnit cu) {
    // 1) 替换静态导入
    for (ImportDeclaration id : cu.getImports()) {
      if (id.isStatic() &&
          "org.hamcrest.MatcherAssertions.assertThat".equals(id.getNameAsString())) {
        id.setName("org.assertj.core.api.Assertions.assertThat");
      }
    }

    // 2) 删除无用的 hamcrest 相关导入
    cu.getImports().removeIf(id -> id.getNameAsString().startsWith("org.hamcrest"));

    // 3) 将 Hamcrest 的调用转为 AssertJ
    Map<String, String> matcherMap = Map.of(
        "is", "isEqualTo",
        "equalTo", "isEqualTo",
        "not", "isNotEqualTo",
        "containsString", "contains"
        // 如果有更多需要映射，可在此继续添加
    );

    // 用于记录是否代码中确实使用了 assertThat（方法调用名为 assertThat 的地方）
    final boolean[] usedAssertThat = {false};

    cu.accept(new ModifierVisitor<Void>() {
      @Override
      public Visitable visit(MethodCallExpr mce, Void arg) {
        if ("assertThat".equals(mce.getNameAsString())) {
          usedAssertThat[0] = true; // 发现了 assertThat 调用

          NodeList<Expression> args = mce.getArguments();
          // 只处理 assertThat( actual, matcher(...) ) 这种两个参数的情况
          if (args.size() == 2) {
            Expression actualExpr = args.get(0);
            Expression matcherExpr = args.get(1);

            if (matcherExpr.isMethodCallExpr()) {
              MethodCallExpr matcherCall = matcherExpr.asMethodCallExpr();
              String matcherName = matcherCall.getNameAsString();

              if (matcherMap.containsKey(matcherName)) {
                String assertJMethod = matcherMap.get(matcherName);

                if (!matcherCall.getArguments().isEmpty()) {
                  Expression expectedExpr = matcherCall.getArgument(0);

                  // 1) 修改原始 assertThat(...) 调用为单参数：assertThat(actual)
                  mce.setArguments(new NodeList<>(actualExpr));

                  // 2) 拼接链式调用：.isEqualTo(...) / .contains(...) 等
                  MethodCallExpr newChainedCall = new MethodCallExpr(mce, assertJMethod);
                  newChainedCall.addArgument(expectedExpr);

                  return newChainedCall;
                }
              }
            }
          }
        }
        return super.visit(mce, arg);
      }
    }, null);

    // 4) 如果发现了 assertThat 调用，但没有静态导入 org.assertj.core.api.Assertions.assertThat，则添加
    if (usedAssertThat[0]) {
      boolean hasAssertJStaticImport = false;
      for (ImportDeclaration id : cu.getImports()) {
        if (id.isStatic() &&
            "org.assertj.core.api.Assertions.assertThat".equals(id.getNameAsString())) {
          hasAssertJStaticImport = true;
          break;
        }
      }

      if (!hasAssertJStaticImport) {
        ImportDeclaration newStaticImport =
            new ImportDeclaration("org.assertj.core.api.Assertions.assertThat", true, false);

        NodeList<ImportDeclaration> importList = cu.getImports();
        // 找到最后一个静态导入的位置
        int lastStaticIndex = -1;
        for (int i = 0; i < importList.size(); i++) {
          if (importList.get(i).isStatic()) {
            lastStaticIndex = i;
          }
        }
        if (lastStaticIndex != -1) {
          // 在最后一个静态导入之后插入
          importList.add(lastStaticIndex + 1, newStaticImport);
        } else {
          // 如果类里没有任何静态导入，就在所有 import 的末尾添加
          importList.add(newStaticImport);
        }
      }
    }
  }
}