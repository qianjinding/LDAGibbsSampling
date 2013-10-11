package ron;

import java.io.File;
import java.util.*;

public class TestsLda {
  public static void main(String[]args)throws Exception {
    // ID SEEN_DATE AFFECTED_FILES
    CrapParser cls = new CrapParser("/Users/ry23/Dropbox/cmu-sfdc/data/changelists.txt");

    // ID CREATE_DATE STATUS CHANGELIST TYPE BUILD_FAILED
    // 1234 18-SEP-13 SKIP|FINISHED 54321 PARTIAL|FULL n|y
    CrapParser runs = new CrapParser("/Users/ry23/Dropbox/cmu-sfdc/data/runs.txt");

    // ID CREATE_DATE TEST_DETAIL_ID RUN_ID
    // 80001 14-SEP-13 12345 1234
    // 80002 14-SEP-13 12341 1234
    CrapParser tfs = new CrapParser("/Users/ry23/Dropbox/cmu-sfdc/data/test_failures.txt");

    ModelParameters ldaparameters = new ModelParameters();
    Documents docSet = new Documents();
    for (int i=0; i<cls.rows.size(); i++) {
      String[] filenames = cls.rows.get(i)[2].split("\n");
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
