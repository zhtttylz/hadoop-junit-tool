package org.example.util;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.jface.text.Document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility that formats only changed lines of a Java file according to
 * Eclipse JDT formatter. Changed lines are detected using {@code git diff}.
 */
public class GitDiffCodeFormatter {

  private static CodeFormatter createFormatter() {
    Map<String, String> opts = DefaultCodeFormatterConstants.getEclipseDefaultSettings();

    opts.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
    opts.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
    opts.put(DefaultCodeFormatterConstants.FORMATTER_INDENTATION_SIZE, "4");
    opts.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");


    opts.put(DefaultCodeFormatterConstants.FORMATTER_LINE_SPLIT, "100");


    opts.put(DefaultCodeFormatterConstants.FORMATTER_CONTINUATION_INDENTATION, "1");

    return ToolFactory.createCodeFormatter(opts);
  }
  /**
   * Format the lines of {@code filePath} that were modified relative to
   * the Git repository at {@code repoPath}. If no changes are detected the
   * file is left untouched.
   */
  public static void formatChangedLines(Path repoPath, Path filePath) throws Exception {
    List<Range> ranges = getChangedLines(repoPath, filePath);
    if (ranges.isEmpty()) {
      return;
    }

    String source = Files.readString(filePath);
    Document doc = new Document(source);

    // 把行号范围转换成 Region[]
    List<IRegion> regions = new ArrayList<>(ranges.size());
    for (Range r : ranges) {
      int startOffset = doc.getLineOffset(r.start - 1);
      int endOffset =
          r.end < doc.getNumberOfLines() ? doc.getLineOffset(r.end) : doc.getLength();
      regions.add(new Region(startOffset, endOffset - startOffset));
    }

    CodeFormatter formatter = createFormatter();
    TextEdit edit =
        formatter.format(
            CodeFormatter.K_COMPILATION_UNIT,
            doc.get(),
            regions.toArray(IRegion[]::new),
            /* initialIndent */ 0,
            System.lineSeparator());

    if (edit != null) {
      edit.apply(doc);
      Files.writeString(filePath, doc.get(), StandardCharsets.UTF_8);
    }
  }


  static class Range {
    final int start;
    final int end;
    Range(int start, int end) {
      this.start = start;
      this.end = end;
    }
  }


  private static List<Range> getChangedLines(Path repoPath, Path filePath)
      throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder("git", "-C", repoPath.toString(),
        "diff", "-U0", "--", filePath.toString());
    pb.redirectErrorStream(true);
    Process proc = pb.start();
    String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    proc.waitFor();
    return parseDiff(output);
  }

  private static List<Range> parseDiff(String diff) {
    List<Range> ranges = new ArrayList<>();
    Pattern p = Pattern.compile("@@ -(?:\\d+)(?:,\\d+)? \\+(\\d+)(?:,(\\d+))? @@");
    for (String line : diff.split("\n")) {
      Matcher m = p.matcher(line);
      if (m.find()) {
        int start = Integer.parseInt(m.group(1));
        int count = m.group(2) == null ? 1 : Integer.parseInt(m.group(2));
        ranges.add(new Range(start, start + count - 1));
      }
    }
    return ranges;
  }
}
