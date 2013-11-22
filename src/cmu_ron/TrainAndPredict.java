package cmu_ron;

import io.LineReader;
import java.io.*;
import java.util.*;
import cc.mallet.pipe.Pipe;
import cc.mallet.topics.*;
import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import com.google.common.collect.Sets;
import data.*;

public class TrainAndPredict {
  ParallelTopicModel currentModel;
  TopicInferencer currentInferencer;
  Pipe instancePipe;

  private class Predictor {
    private final ChangelistTestFailures changelist_to_failures;
    private final ChangelistSourceFiles changelist_to_source_files;
    public Predictor(ChangelistTestFailures changelist_to_failures,
        ChangelistSourceFiles changelist_to_source_files) {
      this.changelist_to_failures = changelist_to_failures;
      this.changelist_to_source_files = changelist_to_source_files;
    }

    private String randomChangelistID(){
      // pick a random changelist to make predictions on... more than one file, more than one failure
      Set<String> changelist_ids = changelist_to_failures.changelistIds();
      int len = changelist_ids.size();
      if (len == 0) throw new RuntimeException();

      do {
        String cListID = (String) changelist_ids.toArray()[new Randoms().nextInt(len)];
        int length = changelist_to_source_files.getSourceFiles(cListID).size();
        int failures = changelist_to_failures.getFailures(cListID).size();
        if ((length > 1) &&(failures > 1)) return cListID;
      } while (true);
    }

    public void testCurrentModel() {
      do {
        String randomCL1 = randomChangelistID();
        String randomCL2;
        while ((randomCL2 = randomChangelistID()).equals(randomCL1));

        // take failures from both runs
        Set<String> cl1failures = changelist_to_failures.getFailures(randomCL1);
        Set<String> cl2failures = changelist_to_failures.getFailures(randomCL2);
        Set<String> failures = Sets.difference(Sets.union(cl1failures, cl2failures), Sets.intersection(cl1failures, cl2failures));

        // if the changelists have identical resulting test failures then let's not bother evaluating
        if (failures.isEmpty()) continue;

        int[] predictions = evaluatePredictions(randomCL1, randomCL2, failures);

        int total = predictions[0] + predictions[1] + predictions[2] + predictions[3];
        if (total > 0) {
          System.out.printf("Changelists %s, %s: tp cl1: %d, fp cl1: %d, tp cl2: %d, fp cl2: %d; accuracy: %.2f%%\n", randomCL1, randomCL2,
              predictions[0], predictions[1], predictions[2], predictions[3],
              (predictions[0] + predictions[2]) * 100. / total);
        }
      } while (false);
    }
    /**
     * @return int array {cl1 correct, cl1 incorrect, cl2 correct, cl2 incorrect}
     */
    public int[] evaluatePredictions(String changelist_id1, String changelist_id2, Set<String> test_ids) {
      int[] predictions = {0,0,0,0};

      for (String test_id : test_ids) {
        Prediction p1 = predict(changelist_id1, test_id);
        Prediction p2 = predict(changelist_id2, test_id);
        if (p1.actually_failed == p2.actually_failed) throw new AssertionError();
        if (p1.score == p2.score) {
          // logger.warn("scores were the same {}; skipping.", p1.score);
          continue;
        }
        if (p1.score > p2.score) {
          if (p1.actually_failed) {
            predictions[0]++;
          } else {
            assert p2.actually_failed;
            predictions[1]++;
          }
        } else {
          if (p2.actually_failed) {
            predictions[2]++;
          } else {
            assert p1.actually_failed;
            predictions[3]++;
          }
        }
      }

      return predictions;
    }

    Prediction predict(String changelist_id, String test_id) {
      // treating words as files, then comparing the topic distribution of a changelist to the topic distribution of the doc for a given test_id
      // the map is a sequence of failed test ids and the changelist id for the run where they actually failed
      List<Prediction> p = new ArrayList<Prediction>();

      // make an instance containing the files from this changelist
      int unknown = 0;
      Set<String> files = changelist_to_source_files.getSourceFiles(changelist_id);
      Alphabet al = currentModel.getAlphabet();
      FeatureSequence fs = new FeatureSequence(al);
      for (String f:files) {
        int id = al.lookupIndex(f,false);
        if (id > 0) {
          fs.add(id);
        } else {
          // System.out.printf("Unknown file %s in changelist %s\n", f, changelist_id);
          unknown++;
          // uncomment below to use unknown files
          fs.add(f);
        }
      }
      if (unknown > 0) {
        System.out.printf("%d%% of changelist %s is unknown\n", unknown*100/files.size(), changelist_id);
      }

      Collection<String> actualFailures = changelist_to_failures.getFailures(changelist_id);

      Instance changelistInstance = new Instance(fs, changelist_id, null, null);
      List<TopicAssignment> testTopics = currentModel.data;

      for (TopicAssignment t:testTopics) {
        String target_test_id = (String)t.instance.getTarget();
        if (target_test_id.equals(test_id)) {
          double testTopicDist[] = currentModel.getTopicProbabilities(t.topicSequence);
          double changelistTopicDist[] = currentInferencer.getSampledDistribution(changelistInstance, 100, 10, 10);
          //          System.out.println(Arrays.toString(testTopicDist));
          //          System.out.println(Arrays.toString(changelistTopicDist));
          p.add(new Prediction(Integer.valueOf(changelist_id), Metrics.cosineSimilarity(testTopicDist, changelistTopicDist), actualFailures.contains(test_id), test_id));
        }
      }
      //  System.out.println();
      if (p.size() != 1) throw new RuntimeException(""+p);
      return p.get(0);
    }
  }

  public void trainNewModel(InstanceList training ) throws IOException {
    ParallelTopicModel model = new ParallelTopicModel(500, 100, 1);

    model.addInstances(training);
    model.setOptimizeInterval(20);
    model.setNumThreads(4);
    model.setNumIterations(10000);
    model.estimate();

    currentModel = model;
    currentInferencer = model.getInferencer();
    instancePipe = training.getPipe();
  }

  public void updateModel(InstanceList newDocs) throws IOException {
    currentModel.addInstances(newDocs);
    currentModel.estimate();
    currentInferencer = currentModel.getInferencer();
  }

  public void save(File f) throws IOException {
    // we need the model, the inferencer, and the pipe used to make instances
    ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f));
    try {
      oos.writeObject(currentInferencer);
      oos.writeObject(currentModel);
      oos.writeObject(instancePipe);
    } finally {
      oos.close();
    }
  }

  public void load(File f) throws ClassNotFoundException, IOException {
    ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f));
    try {
      currentInferencer = (TopicInferencer) ois.readObject();
      currentModel = (ParallelTopicModel) ois.readObject();
      instancePipe = (Pipe) ois.readObject();
    } finally {
      ois.close();
    }
  }

  public static void main (String[] args) throws Exception {
    String traindir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/train/";
    String testdir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/test/";
    String modeldir = "/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/model/";
    train(traindir, modeldir);
    predict(testdir, modeldir);
  }


  public static void train(String datadir, String modeldir) throws Exception {
    TrainAndPredict t = new TrainAndPredict();

    // load data from file if available, else recreate model, inferencer and pipe
    String modelFileName = modeldir + "ronmodel.txt";
    File modelFile = new File(modelFileName);
    if (modelFile.exists()) {
      t.load(modelFile);
    }

    boolean didTraining = t.currentModel == null;
    if (t.currentModel == null) {
      InstanceImporter importer = new InstanceImporter();
      InstanceList topic_instances = importer.readFile(datadir+"inverse_docs.txt.gz");
      t.trainNewModel(topic_instances);
    }

    if (didTraining) {
      t.save(modelFile);
      t.currentModel.printDocumentTopics(new File(modeldir+"doc_topics.txt"));
      t.currentModel.printTopWords(new File(modeldir+"topic_words.txt"), 10, false);
    }

    System.out.println("Model likelihood: " + t.currentModel.modelLogLikelihood());
  }

  public static void predict(String datadir, String modeldir) throws Exception {
    String changelist_file = datadir+"changelists.txt";
    String changelist_failure_file =datadir+"changelist_to_failures_doc.txt";
    ChangelistSourceFiles changelist_to_source_files = ChangelistSourceFiles.readChangelistToFileMapping(changelist_file);
    ChangelistTestFailures changelist_to_failures = LineReader.handle(false, new ChangelistTestFailures(), changelist_failure_file);

    TrainAndPredict t = new TrainAndPredict();
    String modelFileName = modeldir + "ronmodel.txt";
    File modelFile = new File(modelFileName);
    t.load(modelFile);

    Predictor p = t.new Predictor(changelist_to_failures, changelist_to_source_files);
    while (true) {
      p.testCurrentModel();
    }
  }
}
