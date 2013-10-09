package liuyang.nlp.lda.main;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;

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
  /**
   * Get parameters from configuring file. If the configuring file has value in
   * it, use the value. Else the default value in program will be used
   */
  private static void getParametersFromFile(ModelParameters ldaparameters, String parameterFile) throws IOException {
    List<String> paramLines = Files.readAllLines(new File(parameterFile).toPath(), Charset.forName("UTF-8"));
    for (String line : paramLines) {
      String[] lineParts = line.split("\t");
      switch (parameters.valueOf(lineParts[0])) {
      case alpha:
        ldaparameters.alpha = Float.valueOf(lineParts[1]);
        break;
      case beta:
        ldaparameters.beta = Float.valueOf(lineParts[1]);
        break;
      case topicNum:
        ldaparameters.topicNum = Integer.valueOf(lineParts[1]);
        break;
      case iteration:
        ldaparameters.iteration = Integer.valueOf(lineParts[1]);
        break;
      case saveStep:
        ldaparameters.saveStep = Integer.valueOf(lineParts[1]);
        break;
      case beginSaveIters:
        ldaparameters.beginSaveIters = Integer.valueOf(lineParts[1]);
        break;
      }
    }
  }

  public enum parameters {
    alpha, beta, topicNum, iteration, saveStep, beginSaveIters;
  }

  public static void main(String[] args) throws IOException {
    String originalDocsPath = "data/LdaOriginalDocs/";
    String resultPath = "data/LdaResults/";
    String parameterFile = "data/LdaParameter/LdaParameters.txt";
    ModelParameters ldaparameters = new ModelParameters();
    getParametersFromFile(ldaparameters, parameterFile);
    Documents docSet = new Documents();
    docSet.readDocs(originalDocsPath);
    System.out.println("wordMap size " + docSet.termToIndexMap.size());
    new File(resultPath).mkdirs();
    LdaModel model = new LdaModel(ldaparameters);
    System.out.println("1 Initialize the model ...");
    model.initializeModel(docSet);
    System.out.println("2 Learning and Saving the model ...");
    model.inferenceModel(docSet, resultPath);
    System.out.println("3 Output the final model ...");
    model.saveIteratedModel(ldaparameters.iteration, docSet, resultPath);
    System.out.println("Done!");
  }
}
