package cmu;

import io.Tsv;
import java.io.*;
import java.util.*;
import com.google.common.collect.Multimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
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

  double changelistSimilarity(String cl_id1, String cl_id2)
  {    
    
    Collection<String> cl1 = changelist_id_to_files.get(cl_id1);
    Collection<String> cl2 = changelist_id_to_files.get(cl_id2);
    
    Alphabet alphabet = new Alphabet();
    for (String s:cl1) {
      alphabet.lookupIndex(s, true);
    }
    for (String s:cl2) {
      alphabet.lookupIndex(s, true);
    }
    
    double [] v1 = new double[alphabet.size()];
    double [] v2 = new double[alphabet.size()];
    
    for (int i=0; i<alphabet.size(); i++) {
        String s = (String)alphabet.lookupObject(i);
        if (s != null) {
          if (cl1.contains(s))
            v1[i] = 1;
          if (cl2.contains(s))
            v2[i] = 1;
        }
        
    }
    
   return cosineSimilarity(v1, v2);
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
    System.out.printf("%s: %d%% unknown\n", changelist_id, unknown * 100 / files.size());
    Collection<String> actualFailures = changelist_to_failures.get(changelist_id);
    Instance changelistInstance = new Instance(fs, changelist_id, null, null);
    List<TopicAssignment> testTopics = currentModel.data;
    Set<String> handled_ids = new HashSet<String>();
    for (TopicAssignment t : testTopics) {
      String test_id = (String) t.instance.getTarget();
      if (test_ids.contains(test_id)) {
        double testTopicDist[] = currentModel.getTopicProbabilities(t.topicSequence);
        double changelistTopicDist[] = currentInferencer.getSampledDistribution(changelistInstance, 100, 10,
            10);
handled_ids.add(test_id);
        p.add(new Prediction(Integer.valueOf(changelist_id), cosineSimilarity(testTopicDist,
            changelistTopicDist), actualFailures.contains(test_id), test_id));
      }
    }
 
    return p;
  }
  
  // precision, recall, tnr, accuracy, fraction ambiguous
  public List<List<Double>> labelsAndScores(String changelist_id1, String changelist_id2, Set<String> test_ids) {
    List<Double> scores = new ArrayList<Double>();
    List<Double> labels = new ArrayList<Double>();
    List<Prediction> scores1 = scoresForChangelistOnTests(changelist_id1, test_ids);
    List<Prediction> scores2 = scoresForChangelistOnTests(changelist_id2, test_ids);
    double similarity = changelistSimilarity(changelist_id1, changelist_id2);
    System.out.printf("Similarity: %f\n", similarity);
    for (Prediction p1 : scores1) {
      for (Prediction p2 : scores2) {
        if (p1.test_id.equals(p2.test_id)) {
          if ((p1.score == p2.score) || (p1.actually_failed == p2.actually_failed)) {
            // boo hoo
          } else {
            if (p1.actually_failed && !p2.actually_failed)
              labels.add(1.0);
            else
              labels.add(0.0);
            
            scores.add(p1.score-p2.score);
          }
          break; // should only be one of each test id in each of these lists
        }
      }
    }
    List<List<Double>> ret = new ArrayList<List<Double>>();
    ret.add(scores);
    ret.add(labels);
   return ret;
  }
  
  // precision, recall, tnr, accuracy, fraction ambiguous
  public double[] evaluatePredictions(String changelist_id1, String changelist_id2, Set<String> test_ids) {
    double falsePositives = 0;
    double falseNegatives = 0;
    double truePositives  = 0;
    double trueNegatives  = 0; 
    double ambiguous = 0;
    List<Prediction> scores1 = scoresForChangelistOnTests(changelist_id1, test_ids);
    List<Prediction> scores2 = scoresForChangelistOnTests(changelist_id2, test_ids);
    double similarity = changelistSimilarity(changelist_id1, changelist_id2);
    System.out.printf("Similarity: %f\n", similarity);
    for (Prediction p1 : scores1) {
      for (Prediction p2 : scores2) {
        if (p1.test_id.equals(p2.test_id)) {
            if ((p1.score > p2.score) && (p1.actually_failed && !p2.actually_failed))
                truePositives++;
            else if ((p1.score > p2.score) && (!p1.actually_failed && p2.actually_failed))
                falsePositives++;
            else if ((p1.score < p2.score) && (!p1.actually_failed && p2.actually_failed))
                trueNegatives++;
            else if ((p1.score < p2.score) && (p1.actually_failed && !p2.actually_failed))
                falseNegatives++;
            else
                ambiguous++;
          break; // should only be one of each test id in each of these lists
        }
      }
    }
//    return new double[] {
//        truePositives/(truePositives + falsePositives),
//        truePositives/(truePositives + falseNegatives),
//        trueNegatives/(trueNegatives + falsePositives),
//        (truePositives + trueNegatives)/(truePositives + trueNegatives + falseNegatives + falsePositives),
//        (ambiguous)/(truePositives + trueNegatives + falseNegatives + falsePositives + ambiguous)
//    };
    return new double[] {
        truePositives,
        trueNegatives,
        falsePositives,
        falseNegatives,
        (ambiguous)/(truePositives + trueNegatives + falseNegatives + falsePositives + ambiguous)
    };
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
    model.setTopicDisplay(0, 0);
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
  
  private List<List<String>> splitChangelists(Collection<String> clists)
  {
    List<String> clist_list = new ArrayList<String>(clists);
    List<List<String>> splitClists = new ArrayList<List<String>>();
    
    List<Integer> indices = new ArrayList<Integer>();
    for (int i=0; i<clists.size(); i++)
      indices.add(i);
    Collections.shuffle(indices);
        
    // could put this into a loop later for n-way splits
    
    List<Integer> half = indices.subList(0, clists.size()/2);
    List<String> clist = new ArrayList<String>();
    for (Integer i : half) {
      clist.add(clist_list.get(i));
    }
    
    splitClists.add(clist);
    
    half = indices.subList(clists.size()/2, clists.size());
    clist = new ArrayList<String>();
    for (Integer i : half) {
      clist.add(clist_list.get(i));
    }
    splitClists.add(clist);
    
    return splitClists;
 }
  
  public void testCurrentModel(String test_broken_by, String test_fixed_by) throws IOException {

    Multimap <String, String> test_cl_to_failures = importBrokenBy(test_broken_by);
    
    if (test_fixed_by != null) {
      Multimap <String, String> test_cl_to_fixed = importBrokenBy(test_fixed_by);
      changelist_to_failures.putAll(test_cl_to_fixed);
    }
    
    changelist_to_failures.putAll(test_cl_to_failures);


    List<List<String>> splitClists = splitChangelists(test_cl_to_failures.keySet());
    List<String> list1 = splitClists.get(0);
    List<String> list2 = splitClists.get(1);

    int n_changelists = test_cl_to_failures.keySet().size();

    System.out.println();
    int iterations = n_changelists*n_changelists;
    if (iterations > 50000)
      iterations = 50000;
    Randoms r = new Randoms();
    
    
    for (int i=0; i<iterations; i++) {
      
      int j = r.nextInt(list1.size());
      int k = r.nextInt(list2.size());
      String randomCL1 = list1.get(j);
      String randomCL2 = list2.get(k);
  

        // take failures from both clists
        Set<String> failures = new HashSet<String>();

        failures.addAll(test_cl_to_failures.get(randomCL1));
        failures.addAll(test_cl_to_failures.get(randomCL2));

        
        List<List<Double>> forMatlab = labelsAndScores(randomCL1, randomCL2, failures);
        
       // double [] stats = evaluatePredictions(randomCL1, randomCL2, failures);
        Double[] labels = forMatlab.get(1).toArray(new Double[0]);
        Double[] scores = forMatlab.get(0).toArray(new Double[0]);
        
        System.out.println(Arrays.toString(labels));
        System.out.println(Arrays.toString(scores));
        System.out.println();
      }
  }

  public static void main(String[] args) throws Exception {
    TrainAndPredict t = new TrainAndPredict();
    // load data from file if available, else recreate model, inferencer and
    // pipe

    // should really do these file names with options or something

    String training_file = "/Users/abannis/CourseWork/18697/project/data/train/brokenby.txt";
    String fix_file = "/Users/abannis/CourseWork/18697/project/data/train/fixedby.txt";
    String changelists = "/Users/abannis/CourseWork/18697/project/data/changelists.txt";

    String test_broken = "/Users/abannis/CourseWork/18697/project/data/test/brokenby.txt";
    String test_fixed = "/Users/abannis/CourseWork/18697/project/data/test/fixedby.txt";
    File modelFile = null;

    t.importChangelists(changelists);

     fix_file = null;
     test_fixed = null;
    
    Multimap<String, String> training_cl_to_fails = t.importBrokenBy(training_file); 
    if (fix_file != null) {
      Multimap<String, String> cl_to_fixes = t.importBrokenBy(fix_file); 
      t.changelist_to_failures.putAll(cl_to_fixes); 
    }
    t.changelist_to_failures.putAll(training_cl_to_fails);



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

      // invert the map of changelists to failures for our training instances
     // Multimap<String, String> failures_to_clists = HashMultimap.create(); 
     // Multimaps.invertFrom(training_cl_to_fails, failures_to_clists);
   //   BrokenByImporter imp = new BrokenByImporter(t.changelist_id_to_files.asMap() , failures_to_clists.asMap());
    //  instances = imp.loadInstances();
      t.trainNewModel(instances);
    }

    if (modelFile != null) {
      t.save(modelFile);
    }

    System.out.println("Model likelihood: " + t.currentModel.modelLogLikelihood());
    t.testCurrentModel(test_broken, test_fixed);
  }
}
