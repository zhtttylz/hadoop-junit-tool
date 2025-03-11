package org.example.converter.processor;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.example.util.JUnitMigrationUtils;

import java.util.Set;

/**
 * 处理断言方法参数迁移 (JUnit4 -> JUnit5):
 * - 若方法是 assertTrue("msg", condition)，则改为 assertTrue(condition, "msg") 等
 */
public class AssertArgumentsProcessor {

  public static void processAssertArguments(CompilationUnit cu,
                                            Set<String> swapTwoArgsMethods,
                                            Set<String> shiftThreeArgsMethods) {
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
}