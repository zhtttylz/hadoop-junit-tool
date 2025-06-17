package org.example.converter.processor.RuleAnnotate;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RuleTimeoutProcessorTest {

    @Test
    public void testTimeoutRuleConvertedToClassAnnotation() {
        String source = String.join(System.lineSeparator(),
            "import org.junit.Test;",
            "import org.junit.rules.Timeout;",
            "public class SampleTest {",
            "  public Timeout globalTimeout = new Timeout(300000);",
            "  @Test",
            "  public void testA() {",
            "  }",
            "}");

        CompilationUnit cu = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(cu);

        RuleTimeoutProcessor.processTimeoutRule(cu);

        // 类上应添加 @Timeout 注解
        assertTrue(cu.getClassByName("SampleTest").get()
            .getAnnotationByName("Timeout").isPresent());

        // 原始字段应该被移除
        assertFalse(cu.toString().contains("globalTimeout"));
    }
}
