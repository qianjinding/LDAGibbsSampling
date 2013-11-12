package data;

import java.util.Map;
import java.util.Set;
import data.PackUtils;

public class TestResultsPackerMain {
  //  ID      CREATE_DATE     TEST_DETAIL_ID  RUN_ID  TEST_STATUS
  //  9126993559      31-OCT-13       3233896 35011705        1
  public static void main(String[]args)throws Exception {
    // run_id -> test_status -> test_detail_id
    Map<Integer, Map<Byte, Set<Integer>>> m = PackUtils.readRawTestRuns("/Users/ry23/Sites/cmu/test_results.txt.gz");
    PackUtils.writePackedJson("/Users/ry23/Dropbox/cmu-sfdc/ron_mallet/20131112/test_results.json", m);
  }
}
