package cmu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.StringList2FeatureSequence;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

public class BrokenByImporter {
  
  Pipe inputPipe;

  public BrokenByImporter() 
  {
   // ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
   // pipeList.add(new StringList2FeatureSequence());

    inputPipe = new StringList2FeatureSequence();
  }
 
  public InstanceList readFile(Reader r, Map<String, Collection<String>> changelist_to_files)
  { 
    BrokenByIterator it = new BrokenByIterator(r, changelist_to_files);
    InstanceList instances = new InstanceList(inputPipe);
    instances.addThruPipe(it);
    
    return instances;
  }
  
  private class BrokenByIterator implements Iterator<Instance> {
    BufferedReader reader = null;
   String currentLine;
   boolean hasNextUsed;
   Map<String, Collection<String>> changelist_to_files;
    // ProgressTracker p;
    public BrokenByIterator(Reader reader, Map<String, Collection<String>> changelist_to_files) 
    {
      this.reader = new BufferedReader(reader);
      try {
        this.reader.readLine(); //skip first line
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      
      this.changelist_to_files = changelist_to_files;
    }
    
    public Instance next() {
      String target = null;
      if (!hasNextUsed) {
        try {
          currentLine = reader.readLine();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        hasNextUsed = false;
      }
      String parts[] = currentLine.split("\\s");
      String changelists[] = parts[1].split("\\|");
      
      // add all the files from the changelists to the data field
      List<String> data = new ArrayList<String>();
      for (String cl : changelists) {
        Collection<String> files = changelist_to_files.get(cl);
        if (files != null) {
          data.addAll(files);
        }
      }
      
      target = parts[0]; // the test
      
      return new Instance(data, target, null, null);
    }
    public boolean hasNext() {
      hasNextUsed = true;
      try {
        currentLine = reader.readLine();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return (currentLine != null);
    }
    public void remove() {
      throw new IllegalStateException("This Iterator<Instance> does not support remove().");
    }
  }

  
  
}
