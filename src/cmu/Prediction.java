package cmu;

//copied from ron.MalletPredictionMain
public class Prediction {
  public final int docid;
  public final double score;
  public final int actual_failure_count;
  public final String source_file;
  public Prediction(int docid, double score, int actual_failure_count, String source_file) {
    this.docid = docid;
    this.score = score;
    this.actual_failure_count = actual_failure_count;
    this.source_file = source_file;
  }
  
 
}