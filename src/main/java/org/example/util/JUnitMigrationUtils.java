package org.example.util;


import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.resolution.types.ResolvedType;

import java.util.Arrays;
import java.util.List;

/**
 * @author zhtttylz
 * @date 2025/1/16 16:49
 * 用于校验assert属于junit4还是junit5
 */
public class JUnitMigrationUtils {

  /**
   * 判断表达式是不是“像是字符串参数”，判断依据如下
   * 1.第一个参数是字符串字面量， 如“xxxx” 格式
   * 2.第一个参数是method.toString()，如err.toString()
   */
  public static boolean isLikelyMessageParameter(Expression expr) {
    // 1) 直接是字符串字面量
    if (expr instanceof StringLiteralExpr) {
      return true;
    }
    // 2) 字符串拼接 (BinaryExpr)，递归检查左右分支
    if (expr instanceof BinaryExpr) {
      BinaryExpr binExpr = (BinaryExpr) expr;
      return isLikelyMessageParameter(binExpr.getLeft())
          || isLikelyMessageParameter(binExpr.getRight());
    }
    // 3) 如果是 MethodCall，比如 xxx.toString()、String.format(...) 等
    if (expr instanceof MethodCallExpr) {
      MethodCallExpr methodCallExpr = (MethodCallExpr) expr;
      // a) 如果方法名就是 toString，通常我们就可以把它认定为返回String
      if ("toString".equals(methodCallExpr.getNameAsString())) {
        return true;
      }
      if (methodCallExpr.getNameAsString().contains("string") ||
          methodCallExpr.getNameAsString().contains("String")) {
        return true;
      }
      // b) 如果方法名是 format，而且 scope 是 String.format(...)，也可以认定为返回String
      //    这里注意：methodCallExpr.getScope() 是 Optional<Expression>
      if ("format".equals(methodCallExpr.getNameAsString())
          && methodCallExpr.getScope().isPresent()
          && methodCallExpr.getScope().get().isNameExpr()) {

        NameExpr scopeExpr = methodCallExpr.getScope().get().asNameExpr();
        if ("String".equals(scopeExpr.getNameAsString())) {
          return true;
        }
      }
    }

    if (expr instanceof NameExpr nameExpr) {
      String name = nameExpr.getNameAsString();
      if ("msg".equals(name)) {
        return true;
      }
      // 将需要匹配的关键字放入列表
      List<String> keywords = Arrays.asList("Message", "message", "Out", "out");
      return keywords.stream().anyMatch(name::contains);
    }

    // 4) 最后，尝试走 symbol solver，看它能不能告诉我们是 String
    try {
      ResolvedType resolvedType = expr.calculateResolvedType();
      if (resolvedType.isReferenceType()) {
        if (resolvedType.asReferenceType().getQualifiedName()
            .equals(String.class.getCanonicalName())) {
          return true;
        }
      }
    } catch (Exception e) {

    }

    // 如果以上都不是，则认为它“不太像” message 参数
    return false;
  }
}