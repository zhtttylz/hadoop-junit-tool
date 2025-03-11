package org.example;

import org.example.converter.JUnit4ToJUnit5Converter;
import org.example.util.ModifyFileCmd;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class StartCoverterMain {

  static String scriptPath = "/Users/didi/IdeaProjects/hadoop-junit-tool/shell/JUnit4ToJUnit5.sh";

  static String converterDir = "/Users/didi/IdeaProjects/hadoop/hadoop-hdfs-project/hadoop-hdfs-rbf/src/test/java/org/apache/hadoop/hdfs/server/federation/router/TestRouterAdminCLI.java";

  public static void main(String[] args) throws IOException {
    List<String> files = ModifyFileCmd.getModifyFile(scriptPath, converterDir);
    JUnit4ToJUnit5Converter converter = new JUnit4ToJUnit5Converter();

    for (String file : files) {
      System.out.println(file);
      converter.converter(Path.of(file));
    }
  }
}