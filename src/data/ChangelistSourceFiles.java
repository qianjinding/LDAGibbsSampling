package data;

import io.TsvParser;
import java.io.IOException;
import java.util.*;

/**
 * For each changelist, what source files are in it
 */
public class ChangelistSourceFiles {
  final Map<String, Set<String>> changelist_id_to_files = new HashMap<>();
  final Map<String, String> file_to_changelist_id = new HashMap<>();
  public String getChangelistId(String source_file) {
    return file_to_changelist_id.get(source_file);
  }
  public Set<String> getSourceFiles(String changelist_id) {
    return changelist_id_to_files.get(changelist_id);
  }
  public static ChangelistSourceFiles readChangelistToFileMapping(String changelists_tsv) throws IOException {
    TsvParser cls = new TsvParser(changelists_tsv);
    ChangelistSourceFiles changelist_to_file_mapping = new ChangelistSourceFiles();
    for (String[] changelist: cls.rows()) {
      for (String file : changelist[2].split("\n")) {
        Set<String> files = changelist_to_file_mapping.changelist_id_to_files.get(changelist[0]);
        if (files == null) {
          changelist_to_file_mapping.changelist_id_to_files.put(changelist[0], files = new HashSet<>());
        }
        files.add(file);
        changelist_to_file_mapping.file_to_changelist_id.put(file, changelist[0]);
      }
    }
    return changelist_to_file_mapping;
  }
}