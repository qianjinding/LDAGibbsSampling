package data;

import io.ProgressTracker;
import java.io.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

public class PackUtils {

  public static interface ToString {
    public String fromSet(Set<Integer> value);
  }

  static final class Packer implements ToString {
    @Override public String fromSet(Set<Integer> value) {
      StringBuilder sb = new StringBuilder();
      boolean first = true;
      Integer runstart = null;
      Integer runfinish = null;
      int lastrunfinish = 0;
      for (int i : value) {
        if (runstart == null) {
          runstart = i;
          runfinish = i;
        } else {
          assert runfinish != null;
          if (i - 1 == runfinish) {
            // extend the run
            runfinish = i;
          } else {
            // we are more than runfinish+1
            first = PackUtils.emit(sb, first, lastrunfinish, runstart, runfinish);
            lastrunfinish = runfinish;
            runstart = i;
            runfinish = i;
          }
        }
      }
      if (runstart == null) {
        assert runfinish == null;
      } else {
        assert runfinish != null;
        PackUtils.emit(sb, first, lastrunfinish, runstart, runfinish);
      }
      return sb.toString();
    }
  }

  public static Map<Integer, Map<Byte, Set<Integer>>> readRawTestRuns(String infile) throws IOException,
  FileNotFoundException {
    Map<Integer, Map<Byte, Set<Integer>>> m = new TreeMap<>();
    try (ProgressTracker pt = new ProgressTracker(null, "read", -1, "rows", "bytes");
        BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(infile))))) {
      String line = r.readLine();
      pt.advise(1, line.length());
      while (null != (line = r.readLine())) {
        String[] ar = line.split("\t");
        int run_id = Integer.parseInt(ar[3]);
        int test_detail_id = Integer.parseInt(ar[2]);
        byte test_status = Byte.parseByte(ar[4]);
        Map<Byte, Set<Integer>> run = m.get(run_id);
        if (run == null) {
          m.put(run_id, run = new TreeMap<>());
        }
        Set<Integer> tests = run.get(test_status);
        if (tests == null) {
          run.put(test_status, tests = new TreeSet<>());
        }
        tests.add(test_detail_id);
        pt.advise(1, line.length() + 1);
      }
    }
    return m;
  }

  public static void writePackedJson(String outfile, Map<Integer, Map<Byte, Set<Integer>>> m) throws IOException {
    writeJson(outfile, m, new Packer());
  }
  static void writeJson(String outfile, Map<Integer, Map<Byte, Set<Integer>>> m, PackUtils.ToString toString) throws IOException {
    try (ProgressTracker pt = new ProgressTracker(null, "write", m.size(), "runs");
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(outfile.endsWith(".gz") ? new GZIPOutputStream(new FileOutputStream(outfile)) : new FileOutputStream(outfile)))) {
      w.write("[\n");
      boolean firstrun = true;
      for (Entry<Integer, Map<Byte, Set<Integer>>> run : m.entrySet()) {
        if (firstrun) {
          firstrun = false;
        } else {
          w.write(',');
        }
        pt.advise(1);
        w.write("{\n");
        w.write("  \"RUN_ID\" : " + run.getKey() + ",\n");
        w.write("  \"TEST_RESULTS\" : [\n");
        boolean firststatus = true;
        for (Entry<Byte, Set<Integer>> e : run.getValue().entrySet()) {
          if (firststatus) {
            firststatus = false;
          } else {
            w.write(',');
          }
          w.write("    {\n");
          w.write("      \"TEST_STATUS\" : " + e.getKey() + ",\n");
          w.write("      \"TESTS\" : \"" + toString.fromSet(e.getValue()) + "\",\n");
          w.write("      \"NUMTESTS\" : " + e.getValue().size() + ",\n");
          w.write("      \"HASH\" : \"" + PackUtils.hash(e.getValue()) + "\"\n");
          w.write("    }\n");
        }
        w.write("  ]\n");
        w.write("}\n");
      }
      w.write("]\n");
    }
  }

  static String hash(Set<Integer> value) {
    Hasher h = Hashing.murmur3_128().newHasher();
    for (int v : value) {
      h.putInt(v);
    }
    return h.hash().toString();
  }

  private static Set<Integer> unpack(String tests) {
    Set<Integer> set = new TreeSet<>();
    int base = 0;
    for (String s : tests.split(",")) {
      int plus = s.indexOf('+');
      int equals = s.indexOf('=');
      assert equals == 0 || equals == -1;
      assert plus > 0 || plus == -1;

      int startdelta, enddelta;
      if (equals != -1 && plus == -1) {
        // "=1234" means just 1234
        startdelta = Integer.parseInt(s.substring(1));
        enddelta = 0;
      } else if (equals != -1 && plus != -1) {
        // "=1234+2" means 1234,1235,1236
        // "=1234+" means 1234,1235
        startdelta = Integer.parseInt(s.substring(1, plus));
        enddelta = plus == s.length() - 1 ? 1 : Integer.parseInt(s.substring(plus + 1));
      } else if (equals == -1 && plus != -1) {
        // "3+3" means 1237,1238,1239 (if base was 1234)
        // "3+" means just 1237,1238
        startdelta = Integer.parseInt(s.substring(0, plus));
        enddelta = plus == s.length() - 1 ? 1 : Integer.parseInt(s.substring(plus + 1));
      } else if (equals == -1 && plus == -1) {
        // "5" means just 1239 (if base was 1234)
        startdelta = Integer.parseInt(s);
        enddelta = 0;
      } else {
        throw new AssertionError();
      }
      int start = base + startdelta;
      int endpoint = start + enddelta;
      for (int x = start; x <= endpoint; x++) {
        set.add(x);
      }
      base = endpoint;
    }
    return set;
  }

  static boolean emit(StringBuilder sb, boolean first, int lastrunfinish, int runstart, int runfinish) {
    if (first) {
      first = false;
    } else {
      sb.append(",");
    }
    if (lastrunfinish == 0) {
      sb.append('=').append(runstart);
    } else {
      sb.append(runstart - lastrunfinish);
    }
    if (runfinish != runstart) {
      sb.append("+");
    }
    if (runfinish > runstart + 1) {
      sb.append(runfinish-runstart);
    }
    return first;
  }

  public static Map<Integer, Map<Byte, Set<Integer>>> readPackedJson(String infile) throws IOException,
  JsonParseException, JsonMappingException, FileNotFoundException {
    try (ProgressTracker pt = new ProgressTracker(null, "readpacked", -1, "runs", "tests");
        @SuppressWarnings("resource")
        BufferedInputStream in = new BufferedInputStream(infile.endsWith(".gz") ? new GZIPInputStream(new FileInputStream(infile)) : new FileInputStream(infile))) {
      //      {
      //        "RUN_ID" : 33794975,
      //        "TEST_RESULTS" : [
      //          {
      //            "TEST_STATUS" : 1,
      //            "TESTS" : "3225813+31,+29+4

      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readValue(in, JsonNode.class);
      Map<Integer, Map<Byte, Set<Integer>>> map = new TreeMap<>();
      for (int i=0; i<root.size(); i++) {
        Map<Byte, Set<Integer>> m3 = new TreeMap<>();
        JsonNode run = root.get(i);
        int run_id = run.get("RUN_ID").asInt();
        map.put(run_id, m3);
        JsonNode test_results = run.get("TEST_RESULTS");
        for (int j=0; j<test_results.size(); j++) {
          JsonNode test_result = test_results.get(j);
          byte test_status = (byte) test_result.get("TEST_STATUS").asInt();
          String tests = test_result.get("TESTS").asText();
          int numtests = test_result.get("NUMTESTS").asInt();
          String expected_hash = test_result.get("HASH").asText();
          Set<Integer> unpacked = unpack(tests);
          if (unpacked.size() != numtests) throw new RuntimeException("Wrong number of tests: " + numtests+" vs " + unpacked.size());
          String new_hash = hash(unpacked);
          if (!new_hash.equals(expected_hash)) throw new RuntimeException("Wrong hash: " + new_hash+" vs " + expected_hash);
          pt.advise(0, unpacked.size());
          m3.put(test_status, unpacked);
        }
        pt.advise(1, 0);
      }
      return map;
    }
  }

}
