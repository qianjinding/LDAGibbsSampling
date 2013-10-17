package ron;

import java.io.*;
import java.util.*;

public class TestsLdaMain {
  public static void main(String[]args)throws Exception {
    ModelParameters ldaparameters = new ModelParameters();
    Documents docSet = new Documents();
    try (BufferedReader in = new BufferedReader(new FileReader("docs.txt"))) {
      String line;
      while (null != (line = in.readLine())) {
        String[] ar = line.split("\t");
        List<String> words = Arrays.asList(ar).subList(1, ar.length);
        docSet.addDocWords(words);
      }
    }

    Random random = new Random(65536);
    LdaModel model = new LdaModel(ldaparameters, random, docSet);
    String resultPath = "data/tfs/";
    new File(resultPath).mkdirs();
    model.infer(resultPath);
  }
}
