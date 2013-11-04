package cmu;


import cc.mallet.pipe.Pipe;
import cc.mallet.topics.*;
import cc.mallet.util.*;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.InstanceList.CrossValidationIterator;

import java.io.*;

import java.util.*;
import java.util.Map.Entry;

import io.Tsv;
import cmu.InstanceImporter.TrainerType;
import data.Prediction;


public class TrainAndPredict {

  ParallelTopicModel currentModel;
  TopicInferencer currentInferencer;
  
   final Map<String, Set<String>> changelist_id_to_files;
   final Map<String, String> file_to_changelist_id;
   final Map<String, Set<String>> changelist_to_failures;
   
   Pipe instancePipe;
  
  public TrainAndPredict(String changelist_file, String changelist_failure_file)
  {
    
    Tsv cls;
    try {
      cls = new Tsv(changelist_file);
      
      // ID SEEN_DATE AFFECTED_FILES
      changelist_id_to_files = new HashMap<>();
       file_to_changelist_id = new HashMap<>();
      for (String[] changelist: cls.rows()) {
        for (String file : changelist[2].split("\n")) {
          Set<String> files = changelist_id_to_files.get(changelist[0]);
          if (files == null) {
            changelist_id_to_files.put(changelist[0], files = new HashSet<>());
          }
          files.add(file);
          file_to_changelist_id.put(file, changelist[0]);
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot load changelists", e);
    }
    
    try {
     
      BufferedReader clfReader = new BufferedReader(new FileReader(changelist_failure_file));
      
      changelist_to_failures = new HashMap<>();
      String line;
      while ((line = clfReader.readLine()) != null) {
        String[] ar = line.split("\t");
        Set<String> set = new HashSet<>();
        Set<String> prev = changelist_to_failures.put(ar[0], set);
        if (prev != null) throw new RuntimeException(line);
        for (int i=1; i<ar.length; i++) {
          boolean added = set.add(ar[i]);
          if (!added) throw new RuntimeException(line);
        }
      }
      clfReader.close();
    } catch( IOException e) {
      throw new RuntimeException("Could not read changelist to failure file", e);
    }
 }
  
 
  public String randomChangelistID(){
        // pick a random changelist (!) to make predictions on... more than one file
    int size = 0;
    String cListID = null;
    while (size <= 1) {
      Set<String> changelist_ids = changelist_id_to_files.keySet();
       cListID = (String) changelist_ids.toArray()[new Randoms().nextInt(changelist_ids.size())];
      size = changelist_id_to_files.get(cListID).size();
    }
     return cListID;
  }
  
  // predict on all files
  public List<Prediction> predictFailures(InstanceList testingDocs, String test_id) throws IOException
  {

    return predictFailures(testingDocs, test_id, (List<String>)null);
  }
  
  // use a changelist ID
  public List<Prediction> predictFailures(InstanceList testingDocs, String test_id, String changelistID)
  {
    Set<String> cList = changelist_id_to_files.get(changelistID);
    return predictFailures(testingDocs, test_id, cList);
  }
  
  public List<Prediction> predictFailures(InstanceList testingDocs, String test_id, Collection<String> changelist)
  {
    // modified from ron.MalletPredictionMain
    List<Prediction> predictions = new ArrayList<Prediction>();

    
    int docId = 0;
    
    // weirdly enough, using only the top x words gives better results than using all probabilities
    // could be due to overfitting
    
    Object[][] topWordsArray = currentModel.getTopWords(100);
    
    // convert to an ArrayList so I don't have to search out values myself
    List<List<Object>> topWords = new ArrayList<List<Object>>();
    for (Object [] words : topWordsArray) {
      topWords.add(new ArrayList<Object>(Arrays.asList(words)));
    }
    
    for (Instance d : testingDocs) {
      docId++;

      String source_file = (String)d.getTarget();
      
      // restrict to source files in changelist, if it exists
      if (changelist != null && !changelist.contains(source_file))
        continue;

      // corresponds to 'ron.output'
      // uses default values from cc.mallet.topics.tui.InferTopics for arguments
      double docTopicWeights[] = currentInferencer.getSampledDistribution(d, 100, 10, 10);
      
        double score = 0;

        for (int i=0; i<currentModel.numTopics; i++) {
          List<Object> a = topWords.get(i);

          // weight(topic in doc)*weight(topic)
          if (a.contains(test_id)) {
            score += docTopicWeights[i] * currentModel.alpha[i];
          }
        }
    
        //find actual failures for this source file
        int count = 0;
        FeatureSequence f = (FeatureSequence)d.getData();
        for (int featureID : f.getFeatures()) {
          String test = (String)f.getAlphabet().lookupObject(featureID);
            if (test.equals(test_id))
              count++;
        } 
        
        predictions.add(new Prediction(docId, score, count, source_file));
    }
   return predictions;
  }
  


public Map<String, String> predictBlame(Collection<String> test_ids, Collection<String> changelist)
{
  // test id: file
  Map<String, String> blame = new HashMap<>();
 
  
  // build the string of all test ids together
  StringBuilder sb = new StringBuilder();
  Iterator<String> i = test_ids.iterator();
  while(i.hasNext()) {
    sb.append(i.next());
    if (i.hasNext())
      sb.append("\t");
  }
  
  String testIdString = new String(sb);
  
  // we're going to make new instances: each file in the change list to all test ids
  InstanceList predictInstances = new InstanceList(instancePipe);
  for (String file : changelist) {
    predictInstances.addThruPipe(new Instance(testIdString,file, null, null));
  }
  
  for (String test_id : test_ids) {
    double highScore = -1;
    List<Prediction> predictions = predictFailures(predictInstances, test_id, changelist);
    for (Prediction p : predictions) {
        if (p.score > highScore) {
          highScore = p.score;
          blame.put(test_id, p.description);
        }
    }
  }
  
  return blame;
}
  
  // returns {precision, recall, accuracy, true negative rate}
  // where precision is the ratio of  true failures to predicted failures
  // recall is the ratio of true failures to actual failures 
  // accuracy is the ratio of correct predictions (including predicted not to fail) to all predictions
  // true negative rate is the ratio of true negatives (not predicted to fail) to predicted negatives
  public  double[] evaluatePredictionAccuracy(List<Prediction> predictions) {
    int total = 0;
    Random r = new Random();
    int falsePositiveCount = 0; // score was lower but actual_failures was higher
    int falseNegativeCount = 0; // score was higher but actual_failures was lower
    int truePositiveCount = 0; // score was higher and actual_failures is higher or equal
    int trueNegativeCount = 0; // score was lower and actual_failures is lower or equal

    int comparisons = predictions.size()*3/2;

    while (total < comparisons) {
      int a = r.nextInt(predictions.size());
      int b = r.nextInt(predictions.size());
      Prediction pred1 = predictions.get(a);
      Prediction pred2 = predictions.get(b);

      total++;

      if (pred1.score >= pred2.score) {
        if (pred1.actual_failure_count >= pred2.actual_failure_count)
          truePositiveCount++;
        else
          falsePositiveCount++;
      }

      if (pred1.score < pred2.score) {
        if (pred1.actual_failure_count <= pred2.actual_failure_count)
          trueNegativeCount++;
        else
          falseNegativeCount++;
      }


    }

    double precision = (double)truePositiveCount/(truePositiveCount + falsePositiveCount);
    double recall = (double)truePositiveCount/(truePositiveCount + falseNegativeCount);
    double accuracy = (double)(truePositiveCount + trueNegativeCount)/(truePositiveCount + trueNegativeCount + falseNegativeCount + falsePositiveCount);
    double tnr = (double)(trueNegativeCount)/(trueNegativeCount + falsePositiveCount);
    
        
    return new double[]{precision, recall, accuracy, tnr};
  }
  
  public void trainNewModel(InstanceList training ) throws IOException {
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
 //     files_to_failures.addAll(newDocs);
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
    ObjectInputStream ois ;
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

  // do K-fold validation - divide dataset in K parts: train on 1 part and test on the remaining K-1
  // ... then do it K-1 more times
  public void testModel(int nFolds, String test_id, InstanceList instancesToSplit) throws IOException {
    double stats[][] = new double[nFolds][] ;

    CrossValidationIterator folds = instancesToSplit.crossValidationIterator(nFolds);
    for (int i = 0; i<nFolds; i++) {
      InstanceList partition[] = folds.nextSplit();
      trainNewModel(partition[0]);
      double s[] = evaluatePredictionAccuracy(predictFailures(partition[1], test_id));
      stats[i] = s;
    }
    System.out.println("Results: ");
    System.out.println("Precision\t\tRecall\t\tAccuracy\t\tTNR");
    for (double s[] : stats) {
      for (double d : s) 
        System.out.printf("%.4f\t\t", d);
      System.out.println();
    }
  }

  public static void main (String[] args) throws Exception {
 
    TrainAndPredict t = new TrainAndPredict("/Users/abannis/CourseWork/18697/project/data/changelists.txt", "/Users/abannis/CourseWork/18697/project/data/changelist_to_failures_doc.txt");
    InstanceImporter importer = new InstanceImporter();
    InstanceList topic_instances = importer.readFile("/Users/abannis/CourseWork/18697/project/data/docs.txt");
   
    // load data from file if available, else recreate model, inferencer and pipe
//    File modelFile = null;
//    if (args.length > 0) {
//      modelFile = new File(args[0]);
//      try {
//        t.load(modelFile);
//      } catch (IOException e) {
//      }
//    }
//    
//    if (t.currentModel == null) {
//     t.trainNewModel(topic_instances);
//    }
//    
//    if (modelFile != null) {
//      t.save(modelFile);
//    }
    
//    String changelistID = t.randomChangelistID();
//    Set<String> files = t.changelist_id_to_files.get(changelistID);
//    Set<String> failures = t.changelist_to_failures.get(changelistID);
//    
//    Map<String, String> causes=  t.predictBlame(failures, files);
//    System.out.printf("Assigning blame for changelist %s: %d files changed and %d failures\n", changelistID, files.size(), failures.size());
//    for (Entry<String, String> e : causes.entrySet()) {
//      System.out.println(e.getKey()+"\t"+e.getValue());
//    }
    
      t.testModel(5, "14842196", topic_instances);
  }
}
