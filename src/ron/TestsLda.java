package ron;

import java.io.File;
import java.util.*;

public class TestsLda {
  public static void main(String[]args)throws Exception {
    String pathname = "/Users/ry23/Dropbox/cmu-sfdc/data/changelists.txt";
    CrapParser changelists = new CrapParser(pathname);

    ModelParameters ldaparameters = new ModelParameters();
    Documents docSet = new Documents();
    for (int i=0; i<changelists.rows.size(); i++) {
      String[] filenames = changelists.rows.get(i)[2].split("\n");
      List<String> words = Arrays.asList(filenames);
      docSet.addDocWords(words);
    }

    Random random = new Random(65536);
    LdaModel model = new LdaModel(ldaparameters, random, docSet);
    String resultPath = "data/changelists/";
    new File(resultPath).mkdirs();
    model.infer(resultPath);
  }
}
