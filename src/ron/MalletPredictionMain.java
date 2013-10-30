package ron;

import io.LineReader;
import java.util.*;
import java.util.Map.Entry;
import data.*;

public class MalletPredictionMain {
  public static void main(String[]args)throws Exception {
    // ID SEEN_DATE AFFECTED_FILES
    String changelists_tsv = "/Users/ry23/Dropbox/cmu-sfdc/data/changelists.txt";
    String mallet_output = "/Users/ry23/Downloads/malletorig/mallet-2.0.7/ron.output";
    String changelist_to_failures_filename = "changelist_to_failures_doc.txt";
    String topic_keys_filename = "/Users/ry23/Downloads/malletorig/mallet-2.0.7/ron.topickeys";
    String docs_txt = "/Users/ry23/Downloads/malletorig/mallet-2.0.7/docs.txt";

    ChangelistSourceFiles changelist_to_file_mapping = ChangelistSourceFiles.readChangelistToFileMapping(changelists_tsv);
    ChangelistTestFailures changelist_to_failures = LineReader.handle(new ChangelistTestFailures(), changelist_to_failures_filename);
    Topics topics = LineReader.handle(new Topics(), topic_keys_filename);
    SourceFileTestFailures source_file_to_failure_history = LineReader.handle(new SourceFileTestFailures(), docs_txt);
    Docs docs = LineReader.handle(new Docs(), mallet_output);

    {
      // evaluate performance for a single test
      List<Topic> relevant_topics = new ArrayList<>();
      String test_id = "15578983";
      for (Topic t : topics.topics()) {
        if (t.terms.contains(test_id)) {
          relevant_topics.add(t);
        }
      }

      List<Prediction> predictions = new ArrayList<>();

      List<Entry<String, Set<String>>> list = new ArrayList<>(changelist_to_failures.entries());
      // for every pair of changelists
      for (int i=0; i<list.size(); i++) {
        Entry<String, Set<String>> e = list.get(i);
        String changelist_id = e.getKey();
        Set<String> failures = e.getValue();
        for (int j=i+1; i<list.size(); i++) {

        }
      }

      for (Doc d : docs.docs()) {
        double score = 0;
        for (Topic t : relevant_topics) {
          for (int i=0; i<d.topicids.length; i++) {
            if (d.topicids[i] == t.topicid) {
              score += t.weight * d.topicweights[i];
            }
          }
        }
        String source_file = source_file_to_failure_history.getSourceFile(d.docid);
        String changelist_id = changelist_to_file_mapping.getChangelistId(source_file);
        Set<String> all_source_files = changelist_to_file_mapping.getSourceFiles(changelist_id);

        List<String> actual_failures = source_file_to_failure_history.getFailures(source_file);
        int count = 0;
        for (String s : actual_failures) {
          if (test_id.equals(s)) {
            count++;
          }
        }
        predictions.add(new Prediction(d.docid, score, count, source_file));
      }

      int total = 0;
      int numcorrect = 0;
      Random r = new Random();
      while (total < 100000) {
        int a = r.nextInt(predictions.size());
        int b = r.nextInt(predictions.size());
        Prediction p1 = predictions.get(a);
        Prediction p2 = predictions.get(b);
        if ((p1.actual_failure_count == 0) == (p2.actual_failure_count == 0)) {
          continue;
        }
        total++;
        boolean correct = p1.score > p2.score == p1.actual_failure_count > p2.actual_failure_count;
        System.out.println(correct+" " + p1.score+" " + p1.actual_failure_count+" " + p2.score +" " + p2.actual_failure_count);
        if (correct) numcorrect++;
      }
      System.out.println(numcorrect +" correct, out of " + total);
    }

  }
}
