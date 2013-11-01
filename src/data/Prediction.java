package data;


/**
 * Simple object to hold the results of a prediction
 */
public class Prediction {
  public final int id;
  public final double score;
  public final int actual_failure_count;
  public final String description;
  public Prediction(int id, double score, int actual_failure_count, String description) {
    this.id = id;
    this.score = score;
    this.actual_failure_count = actual_failure_count;
    this.description = description;
  }
}