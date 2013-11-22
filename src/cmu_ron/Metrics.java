package cmu_ron;

public class Metrics {

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
    double similarity  = 0.0;
    double diffSqSum = 0.0;
  
    for (int i=0; i<v1.length; i++) {
      double diff =v1[i] - v2[i];
      diffSqSum += diff*diff;
    }
    similarity = diffSqSum/v1.length;
  
    return 1.0 - similarity;
  }}
