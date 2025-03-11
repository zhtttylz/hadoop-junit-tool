package org.example.converter.processor;

import com.github.javaparser.ast.CompilationUnit;
import org.example.converter.processor.RuleAnnotate.RuleExpectedException;
import org.example.converter.processor.RuleAnnotate.RuleTimeoutProcessor;


import java.util.Optional;

/**
 * 主要包含如下类的处理
 * ExpectedException
 * MethodRule
 * TemporaryFolder
 * TestName
 * TestRule
 * TestWatcher
 * TestWatchman
 * Timeout
 */
public class RuleAnnotateProcessor {

  public static void processJUnit4Rules(CompilationUnit cu) {
    // 1) 处理 @Rule Timeout 的场景
    RuleTimeoutProcessor.processTimeoutRule(cu);

    // 2)处理 @Rule ExpectedException 的场景
    RuleExpectedException.processExpectedExceptionRule(cu);
  }

  private static void processExpectedExceptionRule(CompilationUnit cu) {
  }
}
