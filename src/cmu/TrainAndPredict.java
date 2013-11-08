package cmu;


import cc.mallet.pipe.Pipe;
import cc.mallet.topics.*;
import cc.mallet.util.*;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.InstanceList.CrossValidationIterator;

import java.io.*;

import java.util.*;
import java.util.Map.Entry;

import io.TsvParser;
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
    
    TsvParser cls;
    try {
      cls = new TsvParser(changelist_file);
      
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
        // pick a random changelist to make predictions on... more than one file, more than one failure
    String cListID = null;
    boolean stop = false;
    while (!stop) {
      Set<String> changelist_ids = changelist_to_failures.keySet();
       cListID = (String) changelist_ids.toArray()[new Randoms().nextInt(changelist_ids.size())];
      stop = (changelist_id_to_files.get(cListID).size() > 1)
          &&(changelist_to_failures.get(cListID).size() > 1);
    }
     return cListID;
  }
  
  static double cosineSimilarity(double [] v1, double [] v2)
  {
    // assumes lengths are the same
    // divide the dot product by the norms (eqv. to lengths) of the vectors
    double similarity  = 0.0;
    double v1SqSum = 0.0;
    double v2SqSum = 0.0;
    // first dot product - these values might get tiny enough for logs
    for (int i=0; i<v1.length; i++) {
      similarity += v1[i]*v2[i];
      v1SqSum += v1[i]*v1[i];
      v2SqSum += v2[i]*v2[i];
    }
    similarity = similarity/(Math.sqrt(v1SqSum)*Math.sqrt(v2SqSum));
    
    return similarity;
  }
  
  // super naive - do not use
  static double diffSimilarity(double [] v1, double [] v2)
  {
    // assumes lengths are the same
    // divide the dot product by the norms (eqv. to lengths) of the vectors
    double similarity  = 0.0;
    double diffSqSum = 0.0;
    // first dot product - these values might get tiny enough for logs
    for (int i=0; i<v1.length; i++) {
      double diff =v1[i] - v2[i];
      diffSqSum += diff*diff;
    }
    similarity = diffSqSum/v1.length;
    
    return 1.0 - similarity;
  }
  
  public List<Prediction> scoresForChangelistOnTests(String changelist_id, Set<String>test_ids)
  {
    // treating words as files, then comparing the topic distribution of a changelist to the topic distribution of the doc for a given test_id
    // the map is a sequence of failed test ids and the changelist id for the run where they actually failed
    List<Prediction> p = new ArrayList<Prediction>();
    
    // make an instance containing the files from this changelist
    Set<String> files = changelist_id_to_files.get(changelist_id);
    Alphabet al = currentModel.getAlphabet();
    FeatureSequence fs = new FeatureSequence(al);
    for (String f:files) {
      int id = al.lookupIndex(f,false);
      fs.add(id);
    }
    
    Set<String> actualFailures = changelist_to_failures.get(changelist_id);
    
    Instance changelistInstance = new Instance(fs, changelist_id, null, null);    
    List<TopicAssignment> testTopics = currentModel.data;
    
    for (TopicAssignment t:testTopics) {
      String test_id = (String)t.instance.getTarget();
      if (test_ids.contains(test_id)) {
          double testTopicDist[] = currentModel.getTopicProbabilities(t.topicSequence);
          double changelistTopicDist[] = currentInferencer.getSampledDistribution(changelistInstance, 100, 10, 10);
//          System.out.println(Arrays.toString(testTopicDist));
//          System.out.println(Arrays.toString(changelistTopicDist));
          int failure_count = actualFailures.contains(test_id) ? 1 : 0;
           p.add(new Prediction(Integer.valueOf(changelist_id), cosineSimilarity(testTopicDist, changelistTopicDist), failure_count, test_id));
      }
    }
      //  System.out.println();
   return p;
  }
 

  public double evaluatePredictions(String changelist_id1, String changelist_id2, Set<String> test_ids) {
    
    double correctPredictions = 0.0;
    int total = 0;
    
    List<Prediction> scores1 = scoresForChangelistOnTests(changelist_id1, test_ids);
    List<Prediction> scores2 = scoresForChangelistOnTests(changelist_id2, test_ids);
    
    for (Prediction p1:scores1) { 
      for (Prediction p2:scores2) {
          if (p1.description.equals(p2.description)) {
            if (p1.actual_failure_count != p2.actual_failure_count) {
              total++;
                if ((p1.score > p2.score) == (p1.actual_failure_count > p2.actual_failure_count))
                  correctPredictions+=1;
            }
            break; // should only be one of each test id in each of these lists
          }
      }
    }
    
    return correctPredictions/total;
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

  
  public void testCurrentModel() throws IOException {

    String randomCL1 = randomChangelistID();
    String randomCL2 = randomChangelistID();
    while (randomCL1.equals(randomCL2))
      randomCL2 = randomChangelistID();
    
    // take random failures from both runs
    Set<String> failures = changelist_to_failures.get(randomCL1);
    failures.addAll(changelist_to_failures.get(randomCL2));
    
    double acc = evaluatePredictions(randomCL1, randomCL2, failures);
    
    System.out.printf("Changelists %s, %s: %.1f %% correct.\n", randomCL1, randomCL2, acc*100);
  }
  
  public static void main (String[] args) throws Exception {
 
    TrainAndPredict t = new TrainAndPredict("/Users/abannis/CourseWork/18697/project/data/changelists.txt", "/Users/abannis/CourseWork/18697/project/data/amb_changelist_to_failures.txt");
    InstanceImporter importer = new InstanceImporter();
    InstanceList topic_instances = importer.readFile("/Users/abannis/CourseWork/18697/project/data/inverse_docs.txt");
   
    // load data from file if available, else recreate model, inferencer and pipe
    File modelFile = null;
    if (args.length > 0) {
      modelFile = new File(args[0]);
      try {
        t.load(modelFile);
      } catch (IOException e) {
      }
    }
    
    if (t.currentModel == null) {
     t.trainNewModel(topic_instances);
    }
    
    if (modelFile != null) {
      t.save(modelFile);
    }
    
    t.currentModel.printDocumentTopics(new File("/Users/abannis/temp/doc_topics.txt"));
    t.currentModel.printTypeTopicCounts(new File("/Users/abannis/temp/word_counts.txt"));
    
    System.out.println("Model likelihood: " + t.currentModel.modelLogLikelihood());
    
    
      t.testCurrentModel();
  }
}
