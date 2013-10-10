package ron;

/**
 * Class for Lda model
 *
 * @author yangliu
 * @blog http://blog.csdn.net/yangliuy
 * @mail yangliuyx@gmail.com
 */
import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Logger;

public class LdaModel {
  private static final Logger logger = Logger.getLogger(LdaModel.class.getName());
  private final Random random;
  /**
   * word index array
   */
  private final int[][] doc;
  /**
   * vocabulary size
   */
  private final int V;
  /**
   * topic number
   */
  private final int K;
  /**
   * document number
   */
  private final int M;
  /**
   * topic label array
   */
  private final int[][] z;
  /**
   * doc-topic dirichlet prior parameter
   */
  private final float alpha;
  /**
   * topic-word dirichlet prior parameter
   */
  private final float beta;
  /**
   * given document m, count times of topic k. M*K
   */
  private final int[][] nmk;
  /**
   * given topic k, count times of term t. K*V
   */
  private final int[][] nkt;
  /**
   * Sum for each row in nmk
   */
  private final int[] nmkSum;
  /**
   * Sum for each row in nkt
   */
  private final int[] nktSum;
  /**
   * Parameters for topic-word distribution K*V
   */
  private final double[][] phi;
  /**
   * Parameters for doc-topic distribution M*K
   */
  private final double[][] theta;
  /**
   * Times of iterations
   */
  private final int iterations;
  /**
   * The number of iterations between two saving
   */
  private final int saveStep;
  /**
   * Begin save model at this iteration
   */
  private final int beginSaveIters;
  private final Documents docSet;
  public LdaModel(ModelParameters modelparam, Random random, Documents docSet) {
    this.docSet = docSet;
    alpha = modelparam.alpha;
    beta = modelparam.beta;
    iterations = modelparam.iteration;
    K = modelparam.topicNum;
    saveStep = modelparam.saveStep;
    beginSaveIters = modelparam.beginSaveIters;
    this.random = random;

    M = docSet.docs.size();
    V = docSet.termToIndexMap.size();
    nmk = new int[M][K];
    nkt = new int[K][V];
    nmkSum = new int[M];
    nktSum = new int[K];
    phi = new double[K][V];
    theta = new double[M][K];
    // initialize documents index array
    doc = new int[M][];
    for (int m = 0; m < M; m++) {
      // Notice the limit of memory
      int N = docSet.docs.get(m).docWords.length;
      doc[m] = new int[N];
      for (int n = 0; n < N; n++) {
        doc[m][n] = docSet.docs.get(m).docWords[n];
      }
    }
    // initialize topic lable z for each word
    z = new int[M][];
    for (int m = 0; m < M; m++) {
      int N = docSet.docs.get(m).docWords.length;
      z[m] = new int[N];
      for (int n = 0; n < N; n++) {
        int initTopic = random.nextInt(K);// From 0 to K - 1
        z[m][n] = initTopic;
        // number of words in doc m assigned to topic initTopic add 1
        nmk[m][initTopic]++;
        // number of terms doc[m][n] assigned to topic initTopic add 1
        nkt[initTopic][doc[m][n]]++;
        // total number of words assigned to topic initTopic add 1
        nktSum[initTopic]++;
      }
      // total number of words in document m is N
      nmkSum[m] = N;
    }
  }
  public void infer(String resPath) throws IOException {
    for (int i = 0; i < iterations; i++) {
      logger.info("Iteration " + i);
      if ((i >= beginSaveIters) && (((i - beginSaveIters) % saveStep) == 0)) {
        // Saving the model
        logger.info("Saving model at iteration " + i + " ... ");
        // Firstly update parameters
        updateEstimatedParameters();
        // Secondly print model variables
        saveIteratedModel(i, resPath);
      }
      // Use Gibbs Sampling to update z[][]
      for (int m = 0; m < M; m++) {
        int N = docSet.docs.get(m).docWords.length;
        for (int n = 0; n < N; n++) {
          // Sample from p(z_i|z_-i, w)
          int newTopic = sampleTopicZ(m, n);
          z[m][n] = newTopic;
        }
      }
    }
    saveIteratedModel(iterations, resPath);
  }
  private void updateEstimatedParameters() {
    for (int k = 0; k < K; k++) {
      for (int t = 0; t < V; t++) {
        phi[k][t] = (nkt[k][t] + beta) / (nktSum[k] + V * beta);
      }
    }
    for (int m = 0; m < M; m++) {
      for (int k = 0; k < K; k++) {
        theta[m][k] = (nmk[m][k] + alpha) / (nmkSum[m] + K * alpha);
      }
    }
  }
  private int sampleTopicZ(int m, int n) {
    // Sample from p(z_i|z_-i, w) using Gibbs upde rule
    // Remove topic label for w_{m,n}
    int oldTopic = z[m][n];
    nmk[m][oldTopic]--;
    nkt[oldTopic][doc[m][n]]--;
    nmkSum[m]--;
    nktSum[oldTopic]--;
    // Compute p(z_i = k|z_-i, w)
    double[] p = new double[K];
    for (int k = 0; k < K; k++) {
      p[k] = (nkt[k][doc[m][n]] + beta) / (nktSum[k] + V * beta) * (nmk[m][k] + alpha)
          / (nmkSum[m] + K * alpha);
    }
    // Sample a new topic label for w_{m, n} like roulette
    // Compute cumulated probability for p
    for (int k = 1; k < K; k++) {
      p[k] += p[k - 1];
    }
    double u = random.nextDouble() * p[K - 1]; // p[] is unnormalised
    int newTopic;
    for (newTopic = 0; newTopic < K; newTopic++) {
      if (u < p[newTopic]) {
        break;
      }
    }
    // Add new topic label for w_{m, n}
    nmk[m][newTopic]++;
    nkt[newTopic][doc[m][n]]++;
    nmkSum[m]++;
    nktSum[newTopic]++;
    return newTopic;
  }
  private void saveIteratedModel(int iters, String resPath) throws IOException {
    // lda.params lda.phi lda.theta lda.tassign lda.twords
    // lda.params
    String modelName = "lda_" + iters;
    ArrayList<String> lines = new ArrayList<String>();
    lines.add("alpha = " + alpha);
    lines.add("beta = " + beta);
    lines.add("topicNum = " + K);
    lines.add("docNum = " + M);
    lines.add("termNum = " + V);
    lines.add("iterations = " + iterations);
    lines.add("saveStep = " + saveStep);
    lines.add("beginSaveIters = " + beginSaveIters);
    Files.write(new File(new File(resPath), modelName + ".params").toPath(), lines, Charset.forName("UTF-8"));
    // lda.phi K*V
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(new File(resPath), modelName + ".phi")))) {
      for (int i = 0; i < K; i++) {
        for (int j = 0; j < V; j++) {
          writer.write(phi[i][j] + "\t");
        }
        writer.write("\n");
      }
    }
    // lda.theta M*K
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(new File(resPath), modelName + ".theta")))) {
      for (int i = 0; i < M; i++) {
        for (int j = 0; j < K; j++) {
          writer.write(theta[i][j] + "\t");
        }
        writer.write("\n");
      }
    }
    // lda.tassign
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(new File(resPath), modelName + ".tassign")))) {
      for (int m = 0; m < M; m++) {
        for (int n = 0; n < doc[m].length; n++) {
          writer.write(doc[m][n] + ":" + z[m][n] + "\t");
        }
        writer.write("\n");
      }
    }
    // lda.twords phi[][] K*V
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(new File(resPath), modelName + ".twords")))) {
      int topNum = V; // Find ALL the words in each topic
      for (int i = 0; i < K; i++) {
        List<Integer> tWordsIndexArray = new ArrayList<>();
        for (int j = 0; j < V; j++) {
          tWordsIndexArray.add(j);
        }
        Collections.sort(tWordsIndexArray, new LdaModel.TwordsComparable(phi[i]));
        writer.write("topic " + i + "\t:\t");
        for (int t = 0; t < topNum; t++) {
          writer.write(docSet.indexToTermMap.get(tWordsIndexArray.get(t)) + " "
              + phi[i][tWordsIndexArray.get(t)] + "\t");
        }
        writer.write("\n");
      }
    }
  }

  public class TwordsComparable implements Comparator<Integer> {
    private final double[] sortProb; // Store probability of each word in topic k
    public TwordsComparable(double[] sortProb) {
      this.sortProb = sortProb;
    }
    @Override public int compare(Integer o1, Integer o2) {
      // Sort topic word index according to the probability of each word in
      // topic k
      if (sortProb[o1] > sortProb[o2]) return -1;
      else if (sortProb[o1] < sortProb[o2]) return 1;
      else return 0;
    }
  }
}
