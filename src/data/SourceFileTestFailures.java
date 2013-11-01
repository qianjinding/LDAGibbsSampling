package data;

import io.LineReader;
import java.util.*;

/**
 * Historically, what were the test failures caused by changelists containing this source file.
 * With repetition.
 */
public class SourceFileTestFailures extends LineReader {
  /**
   * map from source file name to failure history
   */
  private final Map<String, List<String>> map = new HashMap<>();
  private final List<String> source_files = new ArrayList<>();
  /**
   * <pre>
   * docs.txt
   * longfilename<TAB>testid<TAB>testid
   * </pre>
   */
  @Override public void add(String line) {
    String[] ar = line.split("\t");
    String filename = ar[0];
    map.put(filename, new ArrayList<String>(Arrays.asList(ar).subList(1, ar.length)));
    source_files.add(filename);
  }
  public String getSourceFile(int docid) {
    return source_files.get(docid);
  }
  public List<String> getFailures(String source_file) {
    return map.get(source_file);
  }
  public int size() {
    return source_files.size();
  }
}