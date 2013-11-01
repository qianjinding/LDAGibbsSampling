package data;

import data.Docs.DocId;

/**
 * Simple object to hold the results of a prediction
 */
public class Prediction {
  public final DocId docid;
  public final double score;
  public final int actual_failure_count;
  public final String source_file;
  public Prediction(DocId docid, double score, int actual_failure_count, String source_file) {
    this.docid = docid;
    this.score = score;
    this.actual_failure_count = actual_failure_count;
    this.source_file = source_file;
  }

}