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

public class StartCoverterByFileList {

  static String scriptPath = "/Users/didi/IdeaProjects/hadoop-junit-tool/shell/JUnit4ToJUnit5.sh";

  // 存放待检测文件列表的配置文件
  private static final String COVERTER_FILE =
      "/Users/didi/IdeaProjects/hadoop-junit-tool/config/coverFiles";

  static String repoRoot = "/Users/didi/IdeaProjects/hadoop";

  public static void main(String[] args) throws IOException {

    Set<String> allFiles = new LinkedHashSet<>();

    Path cfg = Path.of(COVERTER_FILE);
    try (Stream<String> lines = Files.lines(cfg, StandardCharsets.UTF_8)) {

      lines.map(String::trim)
          .filter(s -> !s.isEmpty())
          .forEach(javaFile -> {
            try {
              List<String> files =
                  ModifyFileCmd.getModifyFile(scriptPath, javaFile);
              allFiles.addAll(files);
            } catch (IOException e) {
              System.err.printf(
                  "处理 %s 时出错: %s%n", javaFile, e.getMessage());
            }
          });
    }
    JUnit4ToJUnit5Converter converter = new JUnit4ToJUnit5Converter(Path.of(repoRoot));
    for (String file : allFiles) {
      System.out.println(file);
      converter.converter(Path.of(file));
    }
  }
}