package cmu;

import io.Tsv;
import java.io.*;
import java.util.*;
import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;
import cc.mallet.pipe.Pipe;
import cc.mallet.topics.*;
import cc.mallet.types.*;
import cc.mallet.util.Randoms;
import data.Prediction;

public class TrainAndPredict {
  ParallelTopicModel currentModel;
  TopicInferencer currentInferencer;
  final Multimap<String, String> changelist_id_to_files;
  final Multimap<String, String> changelist_to_failures;
  Pipe instancePipe;
  public TrainAndPredict() {
    changelist_id_to_files = HashMultimap.create();
    changelist_to_failures = HashMultimap.create();
  }
  public String randomChangelistIDFrom( Multimap <String, String> source) {
    // pick a random changelist to make predictions on... more than one file,
    // more than one failure
    String cListID = null;
    boolean stop = false;
    Set<String> changelist_ids = source.keySet();
    while (!stop) {
      cListID = (String) changelist_ids.toArray()[new Randoms().nextInt(changelist_ids.size())];
      int length = changelist_id_to_files.get(cListID).size();
      int failures = source.get(cListID).size();
      stop = (length > 1) && (failures > 1);
    }
    return cListID;
  }
  static double cosineSimilarity(double[] v1, double[] v2) {
    // assumes lengths are the same
    // divide the dot product by the norms (eqv. to lengths) of the vectors
    double similarity = 0.0;
    double v1SqSum = 0.0;
    double v2SqSum = 0.0;
    // first dot product - these values might get tiny enough for logs
    for (int i = 0; i < v1.length; i++) {
      similarity += v1[i] * v2[i];
      v1SqSum += v1[i] * v1[i];
      v2SqSum += v2[i] * v2[i];
    }
    similarity = similarity / (Math.sqrt(v1SqSum) * Math.sqrt(v2SqSum));
    return similarity;
  }

  public List<Prediction> scoresForChangelistOnTests(String changelist_id, Set<String> test_ids) {
    // treating words as files, then comparing the topic distribution of a
    // changelist to the topic distribution of the doc for a given test_id
    // the map is a sequence of failed test ids and the changelist id for the
    // run where they actually failed
    List<Prediction> p = new ArrayList<Prediction>();
    // make an instance containing the files from this changelist
    int unknown = 0;
    Collection<String> files = changelist_id_to_files.get(changelist_id);
    Alphabet al = currentModel.getAlphabet();
    FeatureSequence fs = new FeatureSequence(al);
    for (String f : files) {
      int id = al.lookupIndex(f, false);
      if (id > 0) {
        fs.add(id);
      } else {
        unknown++;
        // uncomment below to use unknown files
        fs.add(f);
      }
    }
    System.out.printf("%d%% of changelist %s is unknown to model\n", unknown * 100 / files.size(), changelist_id);
    Collection<String> actualFailures = changelist_to_failures.get(changelist_id);
    Instance changelistInstance = new Instance(fs, changelist_id, null, null);
    List<TopicAssignment> testTopics = currentModel.data;
    for (TopicAssignment t : testTopics) {
      String test_id = (String) t.instance.getTarget();
      if (test_ids.contains(test_id)) {
        double testTopicDist[] = currentModel.getTopicProbabilities(t.topicSequence);
        double changelistTopicDist[] = currentInferencer.getSampledDistribution(changelistInstance, 100, 10,
            10);

        p.add(new Prediction(Integer.valueOf(changelist_id), cosineSimilarity(testTopicDist,
            changelistTopicDist), actualFailures.contains(test_id), test_id));
      }
    }
    // System.out.println();
    return p;
  }
  public double evaluatePredictions(String changelist_id1, String changelist_id2, Set<String> test_ids) {
    double correctPredictions = 0.0;
    int total = 0;
    List<Prediction> scores1 = scoresForChangelistOnTests(changelist_id1, test_ids);
    List<Prediction> scores2 = scoresForChangelistOnTests(changelist_id2, test_ids);
    for (Prediction p1 : scores1) {
      for (Prediction p2 : scores2) {
        if (p1.test_id.equals(p2.test_id)) {
          if (p1.actually_failed != p2.actually_failed) {
            total++;
            if ((p1.score > p2.score) == (p1.actually_failed && !p2.actually_failed))
              correctPredictions += 1;
          }
          break; // should only be one of each test id in each of these lists
        }
      }
    }
    return correctPredictions / total;
  }

  // just in case
  void importChangelists(String changelist_file) throws IOException
  {
    Tsv cls = new Tsv(changelist_file);
    // ID SEEN_DATE AFFECTED_FILES
    for (String[] changelist : cls.rows()) {
      for (String file : changelist[2].split("\n")) {
        changelist_id_to_files.put(changelist[0], file);
      }
    }
  }

  // so importing of train and test data are separate
  Multimap <String, String> importBrokenBy(String brokenby_file)
  {
    Multimap <String, String> added = HashMultimap.create();
    try {
      BufferedReader clfReader = new BufferedReader(new FileReader(brokenby_file));
      String line;
      // skip first line
      clfReader.readLine();
      while ((line = clfReader.readLine()) != null) {
        String[] ar = line.split("\\s");
        String test_id = ar[0];
        String [] clists = ar[1].split("\\|");
        for (int i = 0; i < clists.length; i++) {
          added.put(clists[i],  test_id);
        }
      }
      clfReader.close(); // there's no rewinding this file

    } catch (IOException e) {
      throw new RuntimeException("Could not read broken_by.txt", e);
    }
    return added;
  }

  public void trainNewModel(InstanceList training) throws IOException {
    ParallelTopicModel model = new ParallelTopicModel(100, 50, 0.01);
    model.addInstances(training);
    model.setOptimizeInterval(20);
    model.setNumThreads(4);
    model.setNumIterations(1000);
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
  public void save(File f) throws ClassNotFoundException {
    // we need the model, the inferencer, and the pipe used to make instances
    ObjectOutputStream oos;
    try {
      oos = new ObjectOutputStream(new FileOutputStream(f));
      oos.writeObject(currentInferencer);
      oos.writeObject(currentModel);
      oos.writeObject(instancePipe);
      oos.close();
    } catch (IOException e) {
      System.out.println("Could not save model and associated objects");
    }
  }
  public void load(File f) throws ClassNotFoundException, IOException {
    ObjectInputStream ois;
    try {
      ois = new ObjectInputStream(new FileInputStream(f));
      currentInferencer = (TopicInferencer) ois.readObject();
      currentModel = (ParallelTopicModel) ois.readObject();
      instancePipe = (Pipe) ois.readObject();
      ois.close();
    } catch (IOException e) {
      System.out.println("Could not load model and associated objects");
      throw (e);
    }
  }
  public void testCurrentModel(String test_broken_by, String changelists) throws IOException {

    if (changelists != null) {
      importChangelists(changelists);
    }

    Multimap <String, String> test_cl_to_failures = importBrokenBy(test_broken_by);
    changelist_to_failures.putAll(test_cl_to_failures);

    for (int i=0; i<100; i++) {
      String randomCL1 = randomChangelistIDFrom(test_cl_to_failures);
      String randomCL2 = randomChangelistIDFrom(test_cl_to_failures);

      while (randomCL1.equals(randomCL2))
        randomCL2 = randomChangelistIDFrom(test_cl_to_failures);

      // take failures from both runs
      Set<String> failures = new HashSet<String>();

      failures.addAll(test_cl_to_failures.get(randomCL1));
      failures.addAll(test_cl_to_failures.get(randomCL2));


      double acc = evaluatePredictions(randomCL1, randomCL2, failures);
      System.out.printf("Changelists %s, %s: %.1f %% correct.\n", randomCL1, randomCL2, acc * 100);
    }
  }

  public static void main(String[] args) throws Exception {
    TrainAndPredict t = new TrainAndPredict();
    // load data from file if available, else recreate model, inferencer and
    // pipe

    // should really do these file names with options or something

    String training_file = "/Users/abannis/CourseWork/18697/project/data/train/brokenby.txt";
    String training_changelists = "/Users/abannis/CourseWork/18697/project/data/train/changelists.txt";

    String test_file = "/Users/abannis/CourseWork/18697/project/data/test/brokenby.txt";
    String test_changelists = "/Users/abannis/CourseWork/18697/project/data/test/changelists.txt";
    File modelFile = null;

    t.importChangelists(training_changelists);

    t.changelist_to_failures.putAll(t.importBrokenBy(training_file));

    if (args.length > 0) {
      modelFile = new File(args[0]);

      try {
        t.load(modelFile);
      } catch (IOException e) {
        // no problem, we'll make a new one
      }
    }

    if (t.currentModel == null) {

      InstanceList instances = null;

      FileReader clfReader = new FileReader(training_file);
      BrokenByImporter imp = new BrokenByImporter();
      instances = imp.readFile(clfReader, t.changelist_id_to_files.asMap());
      clfReader.close();
      t.trainNewModel(instances);
    }

    if (modelFile != null) {
      t.save(modelFile);
    }

    System.out.println("Model likelihood: " + t.currentModel.modelLogLikelihood());
    t.testCurrentModel(test_file, test_changelists);
  }
}
