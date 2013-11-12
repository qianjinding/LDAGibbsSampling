package data;


/**
 * Simple object to hold the results of a prediction
 */
public class Prediction {
  public final int id;
  public final double score;
  public final boolean actually_failed;
  public final String test_id;
  public Prediction(int id, double score, boolean actually_failed, String test_id) {
    this.id = id;
    this.score = score;
    this.actually_failed = actually_failed;
    this.test_id = test_id;
  }
  @Override public String toString() {
    return "Prediction [id=" + id + ", score=" + score + ", actually_failed=" + actually_failed + ", test_id="
        + test_id + "]";
  }
}