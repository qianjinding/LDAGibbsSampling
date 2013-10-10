package ron;

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
  final List<Document> docs;
  final Map<String, Integer> termToIndexMap;
  final List<String> indexToTermMap;
  final Map<String, Integer> termCountMap;
  public Documents() {
    docs = new ArrayList<>();
    termToIndexMap = new HashMap<>();
    indexToTermMap = new ArrayList<>();
    termCountMap = new HashMap<>();
  }
  public void addDocDir(String docsPath) throws IOException {
    for (File docFile : new File(docsPath).listFiles()) {
      addDocFile(docFile);
    }
  }
  public void addDocFile(File docFile) throws IOException {
    docs.add(newDocument(docFile));
  }

  public void addDocWords(List<String> words) {
    docs.add(newDocument(words));
  }

  private Document newDocument(List<String> words) {
    // Transfer word to index
    int[] docWords = new int[words.size()];
    for (int i = 0; i < words.size(); i++) {
      String word = words.get(i);
      Integer index = termToIndexMap.get(word);
      if (index == null) {
        termToIndexMap.put(word, index = termToIndexMap.size());
        indexToTermMap.add(word);
        termCountMap.put(word, new Integer(1));
      } else {
        termCountMap.put(word, termCountMap.get(word) + 1);
      }
      docWords[i] = index;
    }
    return new Document(docWords);
  }

  private Document newDocument(File docFile) throws IOException {
    // Read file and initialize word index array
    List<String> words = new ArrayList<String>();
    List<String>docLines = Files.readAllLines(docFile.toPath(), Charset.forName("UTF-8"));
    for (String line : docLines) {
      for (String tok : line.split(" ")) {
        words.add(tok);
      }
    }
    return newDocument(words);
  }


  public static final class Document {
    final int[] docWords;
    private Document(int[] docWords) {
      this.docWords = docWords;
    }
  }
}
