package org.example.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhtttylz
 * @date 2025/1/16 16:49
 * 执行shell的工具类
 */
public class ModifyFileCmd {

  public static List<String> getModifyFile(String scriptPath, String targetDir)
      throws IOException {
    List<String> files = new ArrayList<>();
    ProcessBuilder pb = new ProcessBuilder(scriptPath, targetDir);
    try {

      Process process = pb.start();

      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream()))
      ) {
        String line;
        while ((line = reader.readLine()) != null) {
          System.out.println("Shell output: " + line);
          files.add(line);
        }
      }

      try (BufferedReader errReader = new BufferedReader(
          new InputStreamReader(process.getErrorStream()))
      ) {
        String line;
        while ((line = errReader.readLine()) != null) {
          System.err.println("Shell error: " + line);
        }
      }

      int exitCode = process.waitFor();
      System.out.println("Shell script exited with code: " + exitCode);

    } catch (Exception e) {
      e.printStackTrace();
    }
    return files;
  }
}
