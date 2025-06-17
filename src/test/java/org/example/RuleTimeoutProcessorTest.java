package org.example;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.example.converter.processor.RuleAnnotate.RuleTimeoutProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RuleTimeoutProcessorTest {

    @Test
    void addsTimeoutAnnotationOnClass() {
        String source = """
                import org.junit.Rule;
                import org.junit.rules.Timeout;
                import org.junit.Test;

                public class SampleTest {
                    @Rule
                    public Timeout globalTimeout = new Timeout(300000);

                    @Test
                    public void demo() {}
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        RuleTimeoutProcessor.processTimeoutRule(cu);

        ClassOrInterfaceDeclaration clazz = cu.getClassByName("SampleTest").orElseThrow();
        var timeoutAnno = clazz.getAnnotationByName("Timeout");
        assertTrue(timeoutAnno.isPresent(), "Timeout annotation should be added to class");
        assertEquals("300", ((SingleMemberAnnotationExpr) timeoutAnno.get()).getMemberValue().toString());

        assertFalse(cu.toString().contains("globalTimeout"));
    }
}
