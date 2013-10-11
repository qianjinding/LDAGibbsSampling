package ron;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

// changelists.txt
//  ID      SEEN_DATE       AFFECTED_FILES
//  123456 31-AUG-13       //app/main/core/foo/bar.png
//  23456 31-AUG-13       "//app/main/core/baz.java
//  //app/main/core/bif.xml
//  //app/main/core/gib.gif"
public final class CrapParser {
  private static final Logger logger = Logger.getLogger(CrapParser.class.getName());
  private static final char sep = '\t';
  final String[] cols;
  final List<String[]> rows;
  public CrapParser(String pathname) throws IOException {
    int state = 0;
    String[] cols = null;
    List<String[]> rows = new ArrayList<>();
    try (ProgressTracker pt = new ProgressTracker(logger, "parsing", -1, "rows", "bytes");
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


  public static void main(String[]args)throws Exception {
    String pathname = "/Users/ry23/Dropbox/cmu-sfdc/data/changelists.txt";
    CrapParser changelists = new CrapParser(pathname);
    System.out.println(Arrays.toString(changelists.cols));
    System.out.println(Arrays.toString(changelists.rows.get(0)));
    System.out.println(Arrays.toString(changelists.rows.get(1)));
    System.out.println(Arrays.toString(changelists.rows.get(2)));
    System.out.println(Arrays.toString(changelists.rows.get(3)));
  }
}