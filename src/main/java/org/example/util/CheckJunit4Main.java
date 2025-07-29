package org.example.util;

import org.example.converter.JUnit4ToJUnit5Converter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class CheckJunit4Main {

  // shell 脚本路径
  private static final String SCRIPT_PATH =
      "/Users/didi/IdeaProjects/hadoop-junit-tool/shell/CheckJunit4.sh";

  // 存放待检测文件列表的配置文件
  private static final String CHECK_FILE =
      "/Users/didi/IdeaProjects/hadoop-junit-tool/config/checkFIle";

  public static void main(String[] args) throws IOException {

    Set<String> allFiles = new LinkedHashSet<>();

    Path cfg = Path.of(CHECK_FILE);
    try (Stream<String> lines = Files.lines(cfg, StandardCharsets.UTF_8)) {

      lines.map(String::trim)
          .filter(s -> !s.isEmpty())
          .forEach(javaFile -> {
            try {
              List<String> files =
                  ModifyFileCmd.getModifyFile(SCRIPT_PATH, javaFile);
              allFiles.addAll(files);
            } catch (IOException e) {
              System.err.printf(
                  "处理 %s 时出错: %s%n", javaFile, e.getMessage());
            }
          });
    }
    allFiles.forEach(System.out::println);
  }
}