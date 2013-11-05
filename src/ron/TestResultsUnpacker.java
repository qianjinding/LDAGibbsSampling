package ron;

import java.util.Map;
import java.util.Set;
import data.PackUtils;

public class TestResultsUnpacker {
  //  ID      CREATE_DATE     TEST_DETAIL_ID  RUN_ID  TEST_STATUS
  //  9126993559      31-OCT-13       3233896 35011705        1
  public static void main(String[]args)throws Exception {
    // run_id -> test_status -> test_detail_id
    Map<Integer, Map<Byte, Set<Integer>>> m = PackUtils.readPackedJson("test_results.json");
    System.out.println(m.size());
  }
}
