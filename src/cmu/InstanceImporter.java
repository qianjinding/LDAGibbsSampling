package cmu;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.regex.Pattern;

import cc.mallet.pipe.*;
import cc.mallet.types.*;

public class InstanceImporter {
  // I did some experimenting with clustering to find how many topics we need
		static public enum TrainerType {
			CLASSIFIER,
			TOPIC_MODEL,
			CLUSTER,
		}
	//EnumMap<TrainerType, Pipe> pipes;
	
	public InstanceImporter() {

		// Regular expression for what constitutes a token.
		//  This pattern includes Unicode letters, Unicode numbers, 
		//   and the underscore character. Alternatives:
		//    "\\S+"   (anything not whitespace)
		
	}

	public Pipe buildPipe(TrainerType forTrainer) {
		Pattern tokenPattern = Pattern.compile("\\S+");

		ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

		// Read data from File objects
		pipeList.add(new Input2CharSequence("UTF-8"));

		// Tokenize raw strings
		pipeList.add(new CharSequence2TokenSequence(tokenPattern));

		// Normalize all tokens to all lowercase
		pipeList.add(new TokenSequenceLowercase());

		// Rather than storing tokens as strings, convert 
		//  them to integers by looking them up in an alphabet.
		pipeList.add(new TokenSequence2FeatureSequence());

		// Topic models use feature sequence, but classifiers use labels and feature vectors
		if (forTrainer == TrainerType.CLASSIFIER || forTrainer == TrainerType.CLUSTER) {
			
			// Do the same thing for the "target" field: 
			//  convert a class label string to a Label object,
			//  which has an index in a Label alphabet.
			pipeList.add(new Target2Label());

			// Now convert the sequence of features to a sparse vector,
			//  mapping feature IDs to counts.
			pipeList.add(new FeatureSequence2FeatureVector());
		}
		// Print out the features and the label
	//	pipeList.add(new PrintInputAndTarget());

		return new SerialPipes(pipeList);
	}
	public InstanceList readFile(Reader r, TrainerType forTrainer) throws FileNotFoundException {
		SFDCIterator iterator = new SFDCIterator(r, 
				"\t",
				2, 1, 0);

		//InstanceList instances = new InstanceList(pipes.get(forTrainer));
		InstanceList instances = new InstanceList(buildPipe(forTrainer));
		instances.addThruPipe(iterator);

		return instances;
	}
	
	public InstanceList readFile(String filename) throws FileNotFoundException {
		return readFile(new FileReader(filename), TrainerType.TOPIC_MODEL);
	}
	
}
