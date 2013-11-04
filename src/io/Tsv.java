package io;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parses tab separated text files, understanding quoted fields somewhat.
 * Assumes first line is a header.
 * <pre>
 * changelists.txt
 * ID      SEEN_DATE       AFFECTED_FILES
 * 123456 31-AUG-13       //app/main/core/foo/bar.png
 * 23456 31-AUG-13       "//app/main/core/baz.java
 * //app/main/core/bif.xml
 * //app/main/core/gib.gif"
 * </pre>
 */
public final class Tsv {
  private static final Logger logger = Logger.getLogger(Tsv.class.getName());
  private static final char sep = '\t';
  private final String[] cols;
  private final List<String[]> rows;
  public Tsv(String pathname) throws IOException {
    int state = 0;
    String[] cols = null;
    List<String[]> rows = new ArrayList<>();
    try (ProgressTracker pt = new ProgressTracker(logger, "parsing " + pathname, -1, "rows", "bytes");
        BufferedReader r = Files.newBufferedReader(new File(pathname).toPath(), Charset.forName("UTF-8"))) {
      String line;
      while (null != (line = r.readLine())) {
        pt.advise(1, line.length());
        switch (state) {
        case 0:
          assert cols == null;
          // header
          state = 1;
          cols = split(line, sep);
          break;
        case 1:
          assert cols != null;
          String[] ar = split(line, sep);
          if (ar.length != cols.length) throw new IllegalStateException(Arrays.toString(cols)+" " + line);
          if (ar[ar.length-1].startsWith("\"")) {
            ar[ar.length-1] = ar[ar.length-1].substring(1) + '\n';
            state = 2;
          }
          rows.add(ar);
          break;
        case 2:
          assert cols != null;
          if (line.indexOf(sep) != -1) throw new IllegalStateException(line);
          if (line.endsWith("\"")) {
            if (line.indexOf('"') != line.lastIndexOf('"')) throw new IllegalStateException(line);
            state = 1;
            line = line.substring(0, line.length() - 1);
          } else {
            line += '\n';
          }
          rows.get(rows.size() - 1)[cols.length - 1] += line;
          break;
        default: throw new AssertionError();
        }
      }
    }
    this.cols = cols;
    this.rows = rows;
  }


  private String[] split(String line, char sep) {
    int x = 0;
    for (int i=0; i<line.length(); i++) {
      if (line.charAt(i) == sep) {
        x++;
      }
    }
    String[] ret = new String[x + 1];
    x = 0;
    int idx = 0;
    for (int i=0; i<line.length(); i++) {
      if (line.charAt(i) == sep) {
        ret[idx] = line.substring(x, i);
        x = i + 1;
        idx++;
      }
    }
    ret[idx] = line.substring(x);
    assert idx == ret.length - 1;
    return ret;
  }


  public int size() {
    return rows.size();
  }

  public List<String[]> rows() {
    return rows;
  }

  public String[] cols() {
    return cols;
  }

  public String[] getRow(int i) {
    return rows.get(i);
  }


  /**
   * Assumes the first column is ID and that the tsv files have the same columns.
   * If an ID is found to be the same in both files, the rest of the columns are
   * expected to hold the same values.
   */
  public void union(Tsv tsv, ConflictResolver resolver) {
    Map<String, Integer> ids = new HashMap<>();
    if (!"ID".equals(cols[0])) {
      throw new RuntimeException("I need an ID: " + Arrays.toString(cols));
    }
    if (!Arrays.equals(cols, tsv.cols())) {
      throw new RuntimeException(Arrays.toString(cols)+ " " + Arrays.toString(tsv.cols()));
    }
    for (int i=0; i<rows.size(); i++) {
      Integer prev = ids.put(rows.get(i)[0], i);
      if (prev != null) throw new RuntimeException("nonunique id: " + prev+" " + i);
    }
    for (String[] row : tsv.rows()) {
      Integer idx = ids.get(row[0]);
      if (idx == null) {
        ids.put(row[0], -1);
        rows.add(row);
      } else if (idx == -1) {
        throw new RuntimeException("incoming tsv has duplicate id's: " + row[0]);
      } else if (!Arrays.equals(row, rows.get(idx))) {
        if (resolver != null) {
          String[] resolved = resolver.resolve(cols, row, rows.get(idx));
          if (!resolved[0].equals(row[0])) {
            throw new RuntimeException("why was the id changed: " + Arrays.toString(resolved)+" vs " + Arrays.toString(row));
          }
          rows.set(idx, resolved);
        } else {
          throw new RuntimeException(Arrays.toString(row)+" " + Arrays.toString(rows.get(idx)));
        }
      }
    }
  }


  public void save(String outfilename) throws IOException {
    try (BufferedWriter out = new BufferedWriter(new FileWriter(outfilename))) {
      out.write(cols[0]);
      for (int i=1; i<cols.length; i++) {
        out.write('\t');
        out.write(cols[i]);
      }
      out.write('\n');

      for (int i=0; i<rows.size(); i++) {
        writeVal(out, rows.get(i)[0]);
        for (int j=1; j<cols.length; j++) {
          out.write('\t');
          out.write(rows.get(i)[j]);
        }
        out.write('\n');
      }
    }
  }


  private void writeVal(BufferedWriter out, String string) throws IOException {
    if (string == null) return;
    if (string.indexOf('\t') != -1) throw new RuntimeException("too fancy");
    if (string.indexOf(' ') != -1) throw new RuntimeException("too fancy");
    if (string.indexOf('\t') != -1) throw new RuntimeException("too fancy");
    if (string.indexOf('"') != -1) throw new RuntimeException("too fancy");
    if (string.indexOf('\n') != -1) {
      out.write('"');
      out.write(string);
      out.write('"');
    } else {
      out.write(string);
    }
  }

  public static interface ConflictResolver {
    public String[] resolve(String[] cols, String[] row1, String[] row2);
  }
}