package io;

import java.io.*;

public abstract class LineReader {
  public abstract void add(String line);

  public static <T extends LineReader> T handle(boolean skipfirst, T lineReader, String filename) throws IOException {
    try (BufferedReader in = new BufferedReader(new FileReader(filename))) {
      String line;
      if (skipfirst) {
        in.readLine(); // skip first line
      }
      while (null != (line = in.readLine())) {
        lineReader.add(line);
     }
    }
    return lineReader;
  }

  public static String[] split(String line, char delim) {
    int x = 0;
    int idx = -1;
    while (-1 != (idx = line.indexOf(delim, idx + 1))) {
      x++;
    }
    String[] ar = new String[x + 1];
    idx = -1;
    int previdx = -1;
    x = 0;
    while (-1 != (idx = line.indexOf(delim, idx + 1))) {
      ar[x++] = line.substring(previdx + 1, idx);
      previdx = idx;
    }
    ar[x] = line.substring(previdx + 1);
    return ar;
  }
}