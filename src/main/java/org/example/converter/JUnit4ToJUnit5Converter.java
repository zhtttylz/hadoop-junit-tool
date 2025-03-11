package org.example.converter;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import org.example.converter.processor.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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

  /**
   * 对外主入口
   */
  public void converter(Path path) throws IOException {
    String source = Files.readString(path);
    CompilationUnit cu = StaticJavaParser.parse(source);
    // 启用词法级保留打印，保留原始代码格式
    LexicalPreservingPrinter.setup(cu);

    // 先处理那些 `extends Assert` 的类
    ClassExtendsAssertProcessor.processClassExtendsAssert(cu);

    // 1) 先对断言方法(如 assertEquals) 做参数迁移
    AssertArgumentsProcessor.processAssertArguments(cu, swapTwoArgsMethods, shiftThreeArgsMethods);

    // 2) 独立处理 @Test(timeout=xxx)
    AtomicBoolean needTimeoutImport = new AtomicBoolean(false);
    TestTimeoutProcessor.processTestTimeout(cu, needTimeoutImport);

    // 3) 独立处理 @Test(expected=xxx)
    AtomicBoolean needAssertionsImport = new AtomicBoolean(false);
    TestExpectedProcessor.processTestExpected(cu, needAssertionsImport);

    // 4) 根据需要插入 import
    TimeoutImportAdder.addTimeoutImportIfNeeded(cu, needTimeoutImport.get());
    AssertionsImportAdder.addAssertionsImportIfNeeded(cu, needAssertionsImport.get());

    // 5) 处理 "import static org.hamcrest.MatcherAssertions.assertThat"
    //    以及将 assertThat(...) 转为 AssertJ 风格。
    HamcrestToAssertJTransformer.transformHamcrestAssertToAssertJ(cu);

    // 6) 处理 "org.assertj.core.api.Assertions" 和 “org.junit.jupiter.api.Assertions” 重名问题
    RedundantAssertionsImportProcessor.processRedundantAssertionsImport(cu);

    // 7） 处理@Rule注解
    //RuleAnnotateProcessor.processJUnit4Rules(cu);

    // 最终写回文件
    Files.writeString(path, LexicalPreservingPrinter.print(cu));
  }

  public static void main(String[] args) throws IOException {
    JUnit4ToJUnit5Converter converter = new JUnit4ToJUnit5Converter();
    converter.converter(Path.of(fileName));
  }
}