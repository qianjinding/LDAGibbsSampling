package cmu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.*;
import java.util.Map.Entry;

import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.PrintInputAndTarget;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.StringList2FeatureSequence;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

public class BrokenByImporter {
  
  Pipe inputPipe;
  Map<String, Collection<String>> files;
  Map<String, Collection<String>> failures;

  public BrokenByImporter(Map<String, Collection<String>> changelist_to_files, Map<String, Collection<String>> failures_to_changelists) 
  {
    //ArrayList<Pipe> pipeList = new ArrayList<Pipe>();
   // pipeList.add(new StringList2FeatureSequence());
   // pipeList.add(new PrintInputAndTarget());

    inputPipe = new StringList2FeatureSequence();
    //inputPipe = new SerialPipes(pipeList);
    files = changelist_to_files;
    failures = failures_to_changelists;
  }
 
  public InstanceList loadInstances()
  { 
    BrokenByIterator it = new BrokenByIterator(files, failures);
    InstanceList instances = new InstanceList(inputPipe);
    instances.addThruPipe(it);
    
    return instances;
  }
  
  private class BrokenByIterator implements Iterator<Instance> {
   Entry<String, Collection<String>> currentEntry;
   Map<String, Collection<String>> changelist_to_files;
   Iterator<Entry<String, Collection<String>>> failures_iterator;
    // ProgressTracker p;
    public BrokenByIterator(Map<String, Collection<String>> changelist_to_files, Map<String, Collection<String>> failures_to_changelists) 
    {
      failures_iterator = failures_to_changelists.entrySet().iterator();
      this.changelist_to_files = changelist_to_files;
    }
    
    public Instance next() {
      String target = null;
      currentEntry = failures_iterator.next();

     List<String> changelists = new ArrayList<String>(currentEntry.getValue());
      
      // add all the files from the changelists to the data field
      List<String> data = new ArrayList<String>();
      for (String cl : changelists) {
        Collection<String> files = changelist_to_files.get(cl);
        if (files != null) {
          data.addAll(files);
        }
      }
      
      target = currentEntry.getKey();
     // System.out.println(target);
      return new Instance(data, target, null, null);
    }
    public boolean hasNext() {
      return failures_iterator.hasNext();
    }
    public void remove() {
      throw new IllegalStateException("This Iterator<Instance> does not support remove().");
    }
  }

  
  
}
