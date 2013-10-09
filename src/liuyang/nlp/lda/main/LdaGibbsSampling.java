package liuyang.nlp.lda.main;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Liu Yang's implementation of Gibbs Sampling of LDA
 *
 * @author yangliu
 * @blog http://blog.csdn.net/yangliuy
 * @mail yangliuyx@gmail.com
 */
public class LdaGibbsSampling {
  public static class ModelParameters {
    float alpha = 0.5f; // usual value is 50 / K
    float beta = 0.1f;// usual value is 0.1
    int topicNum = 100;
    int iteration = 100;
    int saveStep = 10;
    int beginSaveIters = 50;
  }

  public enum parameters {
    alpha, beta, topicNum, iteration, saveStep, beginSaveIters;
  }

  public static void main(String[] args) throws IOException {
    String originalDocsPath = "data/LdaOriginalDocs/";
    String resultPath = "data/LdaResults/";
    ModelParameters ldaparameters = new ModelParameters();
    Documents docSet = new Documents();
    docSet.addDocDir(originalDocsPath);
    System.out.println("wordMap size " + docSet.termToIndexMap.size());
    new File(resultPath).mkdirs();
    Random random = new Random(65536);
    LdaModel model = new LdaModel(ldaparameters, random, docSet);
    System.out.println("Learning and Saving the model ...");
    model.infer(resultPath);
    System.out.println("Done!");
  }
}
