package ron;

import io.LineReader;
import io.ProgressTracker;
import java.util.*;
import java.util.Map.Entry;
import data.*;
import data.Docs.Doc;
import data.Topics.Topic;

public class MalletPredictionMain {
  public static void main(String[]args)throws Exception {
    //String basedir = "/Users/ry23/Downloads/malletorig/mallet-2.0.7/";
    String basedir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/";
    String changelists_tsv = basedir+"changelists.txt";
    String mallet_output_filename = basedir+"ron.output";
    String changelist_to_failures_filename = basedir + "changelist_to_failures_doc.txt";
    String topic_keys_filename = basedir+"ron.topickeys";
    String source_file_to_failure_history_txt = basedir+"docs.txt";

    ChangelistSourceFiles changelist_to_file_mapping = ChangelistSourceFiles.readChangelistToFileMapping(changelists_tsv);
    ChangelistTestFailures changelist_to_failures = LineReader.handle(false, new ChangelistTestFailures(), changelist_to_failures_filename);
    Topics topics = LineReader.handle(false, new Topics(), topic_keys_filename);
    List<String> sourcefilenames = new ArrayList<>();
    Map<String, Integer> source_filename_to_docid = new HashMap<>();
    {
      SourceFileTestFailures source_file_to_failure_history = LineReader.handle(false, new SourceFileTestFailures(), source_file_to_failure_history_txt);
      for (int i=0; i< source_file_to_failure_history.size(); i++) {
        String source_file_name = source_file_to_failure_history.getSourceFile(i);
        sourcefilenames.add(source_file_name);
        source_filename_to_docid.put(source_file_name, source_filename_to_docid.size());
      }
    }
    Docs mallet_output = LineReader.handle(true, new Docs(), mallet_output_filename);

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

      List<Entry<String, Set<String>>> changelists = new ArrayList<>(changelist_to_failures.entries());
      // for every pair of changelists
      for (int i=0; i<changelists.size(); i++) {
        Entry<String, Set<String>> e = changelists.get(i);
        String changelist_id = e.getKey();
        Set<String> failures = e.getValue();
        int actual_failure_count;
        if (failures.contains(test_id)) {
          actual_failure_count = 1;
        } else {
          actual_failure_count = 0;
        }
        double score = 0;
        for (String source_file : changelist_to_file_mapping.getSourceFiles(changelist_id)) {
          int docid = source_filename_to_docid.get(source_file);
          Doc doc = mallet_output.getDoc(docid);
          for (Topic t : relevant_topics) {
            for (int j=0; j<doc.topicids.length; j++) {
              if (doc.topicids[j] == t.topicid) {
                score += doc.topicweights[j];
              }
            }
          }
        }
        Prediction prediction = new Prediction(Integer.parseInt(changelist_id), score, actual_failure_count, changelist_id /* maybe list of filenames? */);
        predictions.add(prediction);
      }

      int total = 0;
      int numcorrect = 0;
      try (ProgressTracker pt = new ProgressTracker(null, test_id, (predictions.size() * (predictions.size() - 1))/2, "eligible", "comparison")) {
        for (int a=0; a<predictions.size(); a++) {
          for (int b=a+1; b<predictions.size(); b++) {

            Prediction p1 = predictions.get(a);
            Prediction p2 = predictions.get(b);
            if ((p1.actual_failure_count == 0) == (p2.actual_failure_count == 0)) {
              pt.advise(1, 0);
              continue;
            }
            pt.advise(1, 1);
            total++;
            boolean correct = p1.score > p2.score == p1.actual_failure_count > p2.actual_failure_count;
            // System.out.println(correct+" " + p1.score+" " + p1.actual_failure_count+" " + p2.score +" " + p2.actual_failure_count);
            if (correct) numcorrect++;
          }
        }
        System.out.printf("accuracy: %.2f%%, correct: %d, total: %d\n", numcorrect * 100f / total, numcorrect, total);
      }
    }
  }
}
