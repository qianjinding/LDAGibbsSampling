package data;

import io.LineReader;
import java.util.*;

/**
 * Historically, what were the test failures caused by changelists containing this source file.
 * With repetition.
 */
public class SourceFileTestFailures extends LineReader {
  final Map<String, List<String>> source_file_to_failure_history = new HashMap<>();
  private final List<String> source_files = new ArrayList<>();
  /**
   * <pre>
   * docs.txt
   * longfilename<TAB>testid<TAB>testid
   * </pre>
   */
  @Override public void add(String line) {
    String[] ar = line.split("\t");
    source_file_to_failure_history.put(ar[0], new ArrayList<String>(Arrays.asList(ar).subList(1, ar.length)));
    source_files.add(ar[0]);
  }
  public String getSourceFile(int docid) {
    return source_files.get(docid);
  }
  public List<String> getFailures(String source_file) {
    return source_file_to_failure_history.get(source_file);
  }
}