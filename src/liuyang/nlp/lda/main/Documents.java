package liuyang.nlp.lda.main;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

/**
 * Class for corpus which consists of M documents
 *
 * @author yangliu
 * @blog http://blog.csdn.net/yangliuy
 * @mail yangliuyx@gmail.com
 */
public class Documents {
  ArrayList<Document> docs;
  Map<String, Integer> termToIndexMap;
  ArrayList<String> indexToTermMap;
  Map<String, Integer> termCountMap;
  public Documents() {
    docs = new ArrayList<Document>();
    termToIndexMap = new HashMap<String, Integer>();
    indexToTermMap = new ArrayList<String>();
    termCountMap = new HashMap<String, Integer>();
  }
  public void readDocs(String docsPath) throws IOException {
    for (File docFile : new File(docsPath).listFiles()) {
      Document doc = new Document(docFile, termToIndexMap, indexToTermMap, termCountMap);
      docs.add(doc);
    }
  }

  public static class Document {
    final int[] docWords;
    public Document(File docName, Map<String, Integer> termToIndexMap, ArrayList<String> indexToTermMap,
        Map<String, Integer> termCountMap) throws IOException {
      // Read file and initialize word index array
      ArrayList<String> words = new ArrayList<String>();
      List<String>docLines = Files.readAllLines(docName.toPath(), Charset.forName("UTF-8"));
      for (String line : docLines) {
        for (String tok : line.split(" ")) {
          words.add(tok);
        }
      }
      // Transfer word to index
      this.docWords = new int[words.size()];
      for (int i = 0; i < words.size(); i++) {
        String word = words.get(i);
        if (!termToIndexMap.containsKey(word)) {
          int newIndex = termToIndexMap.size();
          termToIndexMap.put(word, newIndex);
          indexToTermMap.add(word);
          termCountMap.put(word, new Integer(1));
          docWords[i] = newIndex;
        } else {
          docWords[i] = termToIndexMap.get(word);
          termCountMap.put(word, termCountMap.get(word) + 1);
        }
      }
      words.clear();
    }
  }
}
