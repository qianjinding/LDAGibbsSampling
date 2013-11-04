package data;

import io.Tsv;
import java.io.IOException;
import java.util.*;

/**
 * For each changelist, what source files are in it
 */
public class ChangelistSourceFiles {
  final Map<String, Set<String>> changelist_id_to_files = new HashMap<>();
  final Map<String, String> file_to_changelist_id = new HashMap<>();
  final List<Changelist> changelists = new ArrayList<>();

  public String getChangelistId(String source_file) {
    return file_to_changelist_id.get(source_file);
  }
  public Set<String> getSourceFiles(String changelist_id) {
    return changelist_id_to_files.get(changelist_id);
  }

  /**
   * <pre>
   * ID SEEN_DATE AFFECTED_FILES
   * </pre>
   */
  public static ChangelistSourceFiles readChangelistToFileMapping(String changelists_tsv) throws IOException {
    Tsv cls = new Tsv(changelists_tsv);
    ChangelistSourceFiles changelist_to_file_mapping = new ChangelistSourceFiles();
    for (String[] changelist: cls.rows()) {
      for (String file : changelist[2].split("\n")) {
        Set<String> files = changelist_to_file_mapping.changelist_id_to_files.get(changelist[0]);
        if (files == null) {
          changelist_to_file_mapping.changelist_id_to_files.put(changelist[0], files = new HashSet<>());
          changelist_to_file_mapping.changelists.add(new Changelist(changelist[0], files));
        }
        files.add(file);
        changelist_to_file_mapping.file_to_changelist_id.put(file, changelist[0]);
      }
    }
    return changelist_to_file_mapping;
  }
  public Iterable<Changelist> changelists() {
    return changelists;
  }

  public static class Changelist {
    public final String changelist_id;
    public final Set<String> files;
    public Changelist(String changelist_id, Set<String> files) {
      this.changelist_id = changelist_id;
      this.files = files;
    }

  }
}