package cmu;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.util.Iterator;

import cc.mallet.types.Instance;

public class SFDCIterator implements Iterator<Instance> {
  /// Imports data in the format of 'docs.txt'
  /// Splits on \t, can extract label (mostly for classifiers), name and data
  
	BufferedReader reader = null;
	int index = -1;
	String currentLine = null;
	boolean hasNextUsed = false;
	String separator;
	int uriGroup, targetGroup, dataGroup;
	int splitParts = 1;

	public SFDCIterator (Reader reader, String separator,  int dataGroup, int targetGroup, int uriGroup) {
		this.reader = new BufferedReader (reader);
		this.index = 0;
		
		if (dataGroup <= 0)
            throw new IllegalStateException ("You must extract a data field.");
		
		if (uriGroup > 0)
			splitParts++;
		if (targetGroup > 0)
			splitParts++;
		
		this.separator = separator;
		this.targetGroup = targetGroup;
		this.dataGroup = dataGroup;
		this.uriGroup = uriGroup;
	}

	public Instance next () {

		String uriStr = null;
		String data = "";
		String target = null;

		if (!hasNextUsed) {
			try {
				currentLine = reader.readLine();
			}
			catch (IOException e) {
				throw new RuntimeException (e);
			}
		}
		else {
			hasNextUsed = false;
		}
		
		String parts[] = currentLine.split(separator, splitParts);
		if (uriGroup > 0 &&parts.length >= uriGroup)
			uriStr = parts[uriGroup-1];
		if (targetGroup > 0 && parts.length >= targetGroup)
			target = parts[targetGroup-1];
		if (dataGroup > 0 && parts.length >= dataGroup)
			data = parts[dataGroup-1];

		URI uri = null;
		if (uriStr == null) {
			uriStr = "example:" + index++;
		}
		try { uri = new URI (uriStr); }
		catch (Exception e) { throw new RuntimeException (e); }

		return new Instance(data, target, uri, null);

	}

	public boolean hasNext ()      
	{       
		hasNextUsed = true;
		try {
			currentLine = reader.readLine();
		}
		catch (IOException e) {
			throw new RuntimeException (e);
		}
		return (currentLine != null);   
	}

	public void remove () {
		throw new IllegalStateException ("This Iterator<Instance> does not support remove().");
	}

}