package org.example.converter.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Format assert method invocations after argument migration.
 * Ensures no leading comma and the method line is not split after '('.
 */
public class AssertMethodCallFormatter {

  private static final List<String> METHODS = List.of(
      "assertEquals",
      "assertNotEquals",
      "assertSame",
      "assertNotSame",
      "assertArrayEquals"
      //"assertTrue",
      //"assertFalse",
      //"assertNull",
      //"assertNotNull"
  );

  public static String format(String source) {
    String[] rawLines = source.split("\\r?\\n", -1);
    List<String> normalized = new ArrayList<>();

    // First pass - handle line merging and leading commas
    for (int i = 0; i < rawLines.length; i++) {
      String line = rawLines[i];
      String trimmed = line.trim();

      for (String m : METHODS) {
        String prefix = m + "(";
        if (trimmed.equals(prefix)) {
          if (i + 1 < rawLines.length) {
            String next = rawLines[++i];
            line = line + next.stripLeading();
          }
          trimmed = line.trim();
          break;
        }
      }

      if (trimmed.startsWith(",")) {
        if (!normalized.isEmpty()) {
          String prev = normalized.remove(normalized.size() - 1);
          prev = prev.replaceAll("\\s*$", "") + ",";
          normalized.add(prev);
        }
        line = line.replaceFirst("^\\s*,\\s*", "");
      }

      normalized.add(line);
    }

    List<String> result = new ArrayList<>();

    for (int i = 0; i < normalized.size(); i++) {
      String line = normalized.get(i);
      String trimmed = line.trim();

      String methodName = extractMethodName(trimmed);
      if (methodName != null) {
        FormatResult fr = formatInvocation(normalized, i, methodName);
        result.addAll(fr.lines);
        i = fr.nextIndex;
        continue;
      }

      result.add(line);
    }

    return String.join("\n", result);
  }

  private static class FormatResult {
    List<String> lines;
    int nextIndex;

    FormatResult(List<String> lines, int nextIndex) {
      this.lines = lines;
      this.nextIndex = nextIndex;
    }
  }

  private static String extractMethodName(String trimmed) {
    for (String m : METHODS) {
      if (trimmed.startsWith(m + "(")) {
        return m;
      }
      String withQual = "Assertions." + m + "(";
      if (trimmed.startsWith(withQual)) {
        return "Assertions." + m;
      }
    }
    return null;
  }

  private static FormatResult formatInvocation(List<String> lines, int start, String name) {
    String indent = lines.get(start).replaceFirst("\\S.*$", "");
    StringBuilder sb = new StringBuilder();
    int i = start;
    int paren = 0;
    boolean started = false;
    for (; i < lines.size(); i++) {
      String ln = lines.get(i).trim();
      for (char c : ln.toCharArray()) {
        if (c == '(') {
          paren++;
          started = true;
        } else if (c == ')') {
          paren--;
        }
      }
      sb.append(ln);
      if (started && paren == 0 && ln.endsWith(";")) {
        break;
      }
    }

    String invocation = sb.toString();
    int open = invocation.indexOf('(');
    int close = invocation.lastIndexOf(')');
    if (open < 0 || close < open) {
      return new FormatResult(Collections.singletonList(lines.get(start)), start);
    }
    String argsPart = invocation.substring(open + 1, close).trim();
    List<String> args = splitArgs(argsPart);

    String oneLine = indent + name + "(" + String.join(", ", args) + ");";
    if (oneLine.length() <= 100) {
      return new FormatResult(Collections.singletonList(oneLine), i);
    }

    List<String> formatted = breakLongInvocation(indent, name, args);
    return new FormatResult(formatted, i);
  }

  private static List<String> breakLongInvocation(String indent, String name, List<String> args) {
    List<String> result = new ArrayList<>();
    String contIndent = indent + "    ";

    if (args.isEmpty()) {
      result.add(indent + name + "();");
      return result;
    }

    // Build lines by moving arguments to new lines once the length limit is exceeded
    String line = indent + name + "(" + args.get(0);
    for (int i = 1; i < args.size(); i++) {
      String arg = args.get(i);
      String candidate = line + ", " + arg;
      if (candidate.length() > 100) {
        result.add(line + ",");
        line = contIndent + arg;
      } else {
        line = candidate;
      }
    }
    line += ");";
    result.add(line);

    // Further split long lines at '+' if needed
    List<String> wrapped = new ArrayList<>();
    for (int idx = 0; idx < result.size(); idx++) {
      String baseIndent = (idx == 0) ? indent : contIndent;
      wrapped.addAll(wrapAtPlus(result.get(idx), baseIndent, contIndent));
    }

    return wrapped;
  }

  private static List<String> wrapAtPlus(String line, String baseIndent, String contIndent) {
    List<String> res = new ArrayList<>();
    String current = line.replaceFirst("\\s+$", "");
    String indent = baseIndent;
    while (current.length() > 100) {
      int idx = current.lastIndexOf('+', 100);
      if (idx <= indent.length()) {
        idx = current.indexOf('+', 100);
      }
      if (idx == -1 || idx <= indent.length()) {
        break;
      }
      String part = current.substring(0, idx + 1).replaceFirst("\\s+$", "");
      res.add(part);
      String rest = current.substring(idx + 1).replaceFirst("^\\s+", "");
      current = contIndent + rest;
      indent = contIndent;
    }
    res.add(current);
    return res;
  }

  private static List<String> splitArgs(String argsString) {
    if (argsString.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> args = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0;
    boolean inSingle = false;
    boolean inDouble = false;
    boolean escape = false;
    for (int i = 0; i < argsString.length(); i++) {
      char c = argsString.charAt(i);
      if (escape) {
        current.append(c);
        escape = false;
        continue;
      }
      if (c == '\\') {
        current.append(c);
        escape = true;
        continue;
      }
      if (inSingle) {
        current.append(c);
        if (c == '\'') {
          inSingle = false;
        }
        continue;
      }
      if (inDouble) {
        current.append(c);
        if (c == '"') {
          inDouble = false;
        }
        continue;
      }
      if (c == '\'') {
        inSingle = true;
        current.append(c);
        continue;
      }
      if (c == '"') {
        inDouble = true;
        current.append(c);
        continue;
      }
      if (c == '(' || c == '[' || c == '{' || c == '<') {
        depth++;
        current.append(c);
        continue;
      }
      if (c == ')' || c == ']' || c == '}' || c == '>') {
        depth--;
        current.append(c);
        continue;
      }
      if (c == ',' && depth == 0) {
        args.add(current.toString().trim());
        current.setLength(0);
        continue;
      }
      current.append(c);
    }
    args.add(current.toString().trim());
    return args;
  }
}
