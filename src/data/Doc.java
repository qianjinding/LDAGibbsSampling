package data;

/**
 * a document is composed of topics and their weights
 */
public final class Doc {
  public final int docid;
  public final int[] topicids;
  public final double[] topicweights;
  public Doc(String line) {
    String[] ar = line.split(" ");
    docid = Integer.parseInt(ar[0]);
    if (!"null-source".equals(ar[1])) throw new RuntimeException(line);
    int n = ar.length/2 - 1;
    if (n * 2 + 2 != ar.length) throw new RuntimeException(n+" " + line);
    topicids = new int[n];
    topicweights = new double[n];
    for (int x = 0; x < n; x++) {
      topicids[x] = Integer.parseInt(ar[2 + x * 2]);
      topicweights[x] = Double.parseDouble(ar[2 + x * 2 + 1]);
    }
  }
}