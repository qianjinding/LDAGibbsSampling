package ron;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.logging.Logger;

/**
 * Liu Yang's implementation of Gibbs Sampling of LDA
 *
 * @author yangliu
 * @blog http://blog.csdn.net/yangliuy
 * @mail yangliuyx@gmail.com
 */
public class LdaGibbsSampling {
  private static final Logger logger = Logger.getLogger(LdaGibbsSampling.class.getName());
  public static void main(String[] args) throws IOException {
    String originalDocsPath = "data/LdaOriginalDocs/";
    String resultPath = "data/LdaResults/";
    ModelParameters ldaparameters = new ModelParameters();
    Documents docSet = new Documents();
    docSet.addDocDir(originalDocsPath);
    logger.info("wordMap size " + docSet.termToIndexMap.size());
    new File(resultPath).mkdirs();
    Random random = new Random(65536);
    LdaModel model = new LdaModel(ldaparameters, random, docSet);
    logger.info("Learning and Saving the model ...");
    model.infer(resultPath);
    logger.info("Done!");
  }
}
