/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    PMILexiconExpander.java
 *    Copyright (C) 1999-2017 University of Waikato, Hamilton, New Zealand
 *
 */


package weka.filters.supervised.attribute;


import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.ListIterator;

import weka.core.Attribute;
import weka.core.Capabilities;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Option;
import weka.core.OptionMetadata;
import weka.core.TechnicalInformation;
import weka.core.Utils;
import weka.core.Capabilities.Capability;
import weka.core.TechnicalInformation.Type;
import weka.filters.SimpleBatchFilter;



/**
 *  <!-- globalinfo-start --> Calculates word sentiment associations using PMI Semantic Orientation from a corpus of labelled tweets.
 *   
 * <!-- globalinfo-end -->
 * 
 * <!-- technical-bibtex-start -->
 * BibTeX:
 * <pre>
 * &#64inproceedings{turney2002thumbs,
 * title={Thumbs up or thumbs down? Semantic orientation applied to unsupervised classification of reviews},
 * author={Turney, Peter D},
 * booktitle={Proceedings of the 40th Annual Meeting on Association for Computational Linguistics},
 * pages={417--424},
 * year={2002},
 * organization={Association for Computational Linguistics}
 *}
 *
 * </pre>
 <!-- technical-bibtex-end -->
 * 
 * @author Felipe Bravo-Marquez (fbravoma@waikato.ac.nz)
 * @version $Revision: 1 $
 */
public class PMILexiconExpander extends SimpleBatchFilter {



	/** For serialization.    **/
	private static final long serialVersionUID = -836987892698807629L;



	/** the vocabulary and the WordRep */
	protected Object2ObjectMap<String, WordCount> wordInfo; 



	/** the index of the string attribute to be processed */
	protected int textIndex=1; 


	/** True if all tokens should be downcased. */
	protected boolean toLowerCase=true;


	/** True if url, users, and repeated letters are cleaned */
	protected boolean cleanTokens=false;



	/** The positive class value */
	protected String m_PosClassValue="positive";


	/** The negative class value */
	protected String m_NegClassValue="negative";


	/** Minimum frequency of a word to be considered */
	protected int minFreq=10;




	/** A counter for positive tweets */
	private double posCount;

	/** A counter for negative tweets */
	private double negCount;




	/**
	 * @author Felipe Bravo-Marquez (fbravoma@waikato.ac.nz)
	 *  <!-- globalinfo-start --> Internal class for calculating word counts.
	 *   
	 * <!-- globalinfo-end -->
	 */
	class WordCount{
		String word; // the word
		int posCount;
		int negCount;


		/**
		 * Creates a WordCount object from a String
		 * @param word the word
		 */
		public WordCount(String word){
			this.word=word;
			// Laplace Smoothing, we assume that all words occur at least one with each class.
			this.posCount=1;
			this.negCount=1;
		}

	}




	/**
	 * Returns an instance of a TechnicalInformation object, containing
	 * detailed information about the technical background of this class,
	 * e.g., paper reference or book this class is based on.
	 *
	 * @return the technical information about this class
	 */
	public TechnicalInformation getTechnicalInformation() {
		TechnicalInformation result;
		result = new TechnicalInformation(Type.INPROCEEDINGS);
		result.setValue(TechnicalInformation.Field.AUTHOR, "Turney, Peter D");
		result.setValue(TechnicalInformation.Field.TITLE, "Thumbs up or thumbs down? Semantic orientation applied to unsupervised classification of reviews");
		result.setValue(TechnicalInformation.Field.YEAR, "2002");
		result.setValue(TechnicalInformation.Field.BOOKTITLE, "Proceedings of the 40th Annual Meeting on Association for Computational Linguistics.");
		result.setValue(TechnicalInformation.Field.URL, "http://dl.acm.org/citation.cfm?id=1073153");

		return result;
	}

	@Override
	public String globalInfo() {
		return "Creates a word lexicon from a labelled corpus of tweets using PMI Semantic Orientation."				
				+ "\n"+getTechnicalInformation().toString();
	}



	@Override
	public Capabilities getCapabilities() {

		Capabilities result = new Capabilities(this);
		result.disableAll();



		// attributes
		result.enableAllAttributes();
		result.enable(Capability.MISSING_VALUES);

		// class
		result.enableAllClasses();
		result.enable(Capability.MISSING_CLASS_VALUES);

		result.setMinimumNumberInstances(0);

		return result;
	}




	/* (non-Javadoc)
	 * @see weka.filters.Filter#listOptions()
	 */
	@Override
	public Enumeration<Option> listOptions() {
		return Option.listOptionsForClass(this.getClass()).elements();
	}


	/* (non-Javadoc)
	 * @see weka.filters.Filter#getOptions()
	 */
	@Override
	public String[] getOptions() {		
		return Option.getOptions(this, this.getClass());
	}


	/**
	 * Parses the options for this object.
	 * 
	 * <!-- options-start --> 
	 * <pre> 
	 *-I &lt;col&gt;
	 *  Index of string attribute (default: 1)
	 * </pre>
	 * <pre>
	 *-U 
	 *	 Lowercase content	(default: false)
	 * </pre>
	 * <pre>
	 *-O 
	 *	 Clean tokens (replace goood by good, standarise URLs and @users) 	(default: false)
	 *</pre> 
	 *  
	 * <!-- options-end -->
	 * 
	 * @param options
	 *            the options to use
	 * @throws Exception
	 *             if setting of options fails
	 */
	@Override
	public void setOptions(String[] options) throws Exception {
		Option.setOptions(options, this, this.getClass());
	}




	/* To allow determineOutputFormat to access to entire dataset
	 * (non-Javadoc)
	 * @see weka.filters.SimpleBatchFilter#allowAccessToFullInputFormat()
	 */
	public boolean allowAccessToFullInputFormat() {
		return true;
	}




	/**
	 * Calculates the counts of words occurring with the class values
	 * @param inputFormat the input tweets
	 */
	protected void calculateWordCounts(Instances inputFormat){

		if (!this.isFirstBatchDone()){

			this.posCount=1.0;
			this.negCount=1.0;


			this.wordInfo = new Object2ObjectOpenHashMap<String, WordCount>();

			Attribute attrCont = inputFormat.attribute(this.textIndex-1);

			Attribute attClassInp=inputFormat.attribute(inputFormat.classIndex());

			for (ListIterator<Instance> it = inputFormat.listIterator(); it
					.hasNext();) {
				Instance inst = it.next();
				String content = inst.stringValue(attrCont);



				String classValue=attClassInp.value((int)inst.value(attClassInp));


				if(classValue.equals(this.m_PosClassValue))
					posCount++;

				else if(classValue.equals(this.m_NegClassValue))
					negCount++;


				// tokenises the content 
				List<String> tokens=affective.core.Utils.tokenize(content,this.toLowerCase,this.cleanTokens);


				// counts word frequencies for each distinct word
				for(String word:tokens){
					if(this.wordInfo.containsKey(word)){
						WordCount wc=this.wordInfo.get(word);
						if(classValue.equals(this.m_PosClassValue))
							wc.posCount++;
						else if(classValue.equals(this.m_NegClassValue))
							wc.negCount++;


					}
					else{
						WordCount wc=new WordCount(word);
						if(classValue.equals("positive"))
							wc.posCount++;
						else if(classValue.equals("negative"))
							wc.negCount++;

						
						this.wordInfo.put(word,wc);
					}
				}
			}

		}


	}






	/* (non-Javadoc)
	 * @see weka.filters.SimpleFilter#determineOutputFormat(weka.core.Instances)
	 */
	@Override
	protected Instances determineOutputFormat(Instances inputFormat) {


		ArrayList<Attribute> att = new ArrayList<Attribute>();

		att.add(new Attribute("WORD_NAME", (ArrayList<String>) null));

		att.add(new Attribute("PMI-SO"));


		Instances result = new Instances(inputFormat.relationName(), att, 0);

		return result;
	}


	/* (non-Javadoc)
	 * @see weka.filters.SimpleFilter#process(weka.core.Instances)
	 */
	@Override
	protected Instances process(Instances instances) throws Exception {



		Instances result = getOutputFormat();


		this.calculateWordCounts(instances);


		String[] sortedWords=this.wordInfo.keySet().toArray(new String[0]);

		Arrays.sort(sortedWords);

		for(String word:sortedWords){
			WordCount wordCount=this.wordInfo.get(word);

			if(wordCount.posCount+wordCount.negCount>=this.minFreq){

				double posProb=wordCount.posCount/posCount;
				double negProb=wordCount.negCount/negCount;
				double semanticOrientation=Utils.log2(posProb)-Utils.log2(negProb);



				double[] values = new double[result.numAttributes()];

				int wordNameIndex=result.attribute("WORD_NAME").index();
				values[wordNameIndex]=result.attribute(wordNameIndex).addStringValue(word);	

				values[result.numAttributes()-1]=semanticOrientation;


				Instance inst=new DenseInstance(1, values);

				inst.setDataset(result);

				result.add(inst);
			}

		}


		return result;



	}





	@OptionMetadata(displayName = "textIndex",
			description = "The index (starting from 1) of the target string attribute.",
			commandLineParamName = "I", commandLineParamSynopsis = "-I <int>",
			displayOrder = 0)
	/**
	 * Get the position of the target string.
	 * 
	 * @return the index of the target string
	 */	
	public int getTextIndex() {
		return textIndex;
	}


	/**
	 * Set the attribute's index with the string to process.
	 * 
	 * @param textIndex the index value name
	 */
	public void setTextIndex(int textIndex) {
		this.textIndex = textIndex;
	}




	@OptionMetadata(displayName = "lowercase",
			description = "Lowercase the tweet's content.", commandLineParamIsFlag = true,
			commandLineParamName = "U", commandLineParamSynopsis = "-U",
			displayOrder = 1)
	/**
	 * Gets the value of the lowercase flag.
	 * 
	 * @return the value of the flag.
	 */
	public boolean isToLowerCase() {
		return toLowerCase;
	}

	/**
	 * Sets the value of the lowercase flag.
	 * 
	 * @param toLowerCase the value of the flag.
	 * 
	 */
	public void setToLowerCase(boolean toLowerCase) {
		this.toLowerCase = toLowerCase;
	}



	@OptionMetadata(displayName = "cleanTokens",
			description = "Reduce the attribute space by replacing sequences of letters occurring more than two "
					+ "times in a row with two occurrences of them (e.g., huuungry is reduced to huungry, loooove to loove), "
					+ "and replacing user mentions and URLs with generic tokens.", 
					commandLineParamIsFlag = true, commandLineParamName = "O", 
					commandLineParamSynopsis = "-O",
					displayOrder = 2)	
	/**
	 * Gets the value of the cleanTokens option.
	 * 
	 * @return the value of the flag.
	 */
	public boolean isCleanTokens() {
		return cleanTokens;
	}

	/**
	 * Sets the value of the cleanTokens flag.
	 * 
	 * @param cleanTokens the value of the flag.
	 * 
	 */
	public void setCleanTokens(boolean cleanTokens) {
		this.cleanTokens = cleanTokens;
	}


	@OptionMetadata(displayName = "posClassValue",
			description = "The value of the positive class.\t default positive",
			commandLineParamName = "posClassValue", commandLineParamSynopsis = "-posClassValue <String>",
			displayOrder = 3)
	public String getPosClassValue() {
		return m_PosClassValue;
	}
	public void setPosClassValue(String m_PosClassValue) {
		this.m_PosClassValue = m_PosClassValue;
	}

	@OptionMetadata(displayName = "negClassValue",
			description = "The value of the negative class.\t default negative",
			commandLineParamName = "negClassValue", commandLineParamSynopsis = "-negClassValue <String>",
			displayOrder = 4)
	public String getNegClassValue() {
		return m_NegClassValue;
	}
	public void setNegClassValue(String m_NegClassValue) {
		this.m_NegClassValue = m_NegClassValue;
	}



	@OptionMetadata(displayName = "minFreq",
			description = "Minimum frequency of a word to be considered.\t default 10",
			commandLineParamName = "minFreq", commandLineParamSynopsis = "-minFreq <int>",
			displayOrder = 5)
	public int getMinFreq() {
		return minFreq;
	}
	public void setMinFreq(int minFreq) {
		this.minFreq = minFreq;
	}


	public static void main(String[] args) {
		runFilter(new PMILexiconExpander(), args);
	}

}
