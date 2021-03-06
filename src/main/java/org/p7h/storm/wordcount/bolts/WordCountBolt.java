package org.p7h.storm.wordcount.bolts;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Counts the words and displays to the console and also logs to the file.
 *
 * @author - Prashanth Babu
 */
public final class WordCountBolt extends BaseRichBolt {
	private static final Logger LOGGER = LoggerFactory.getLogger(WordCountBolt.class);
	private static final long serialVersionUID = -306073100275129760L;
	/**
	 * Interval between logging the output.
	 */
	private final long logIntervalInSeconds;
	/**
	 * Log only the words which crosses this threshold value.
	 */
	private final long minWordCountThreshold;

	private long runCounter;
	private Stopwatch stopwatch = null;
	private Multiset<String> wordsTrackerMultiset;
	private Multimap<Integer, String> frequencyOfWords;


	public WordCountBolt(final long logIntervalInSeconds, final long minWordCountThreshold) {
		this.logIntervalInSeconds = logIntervalInSeconds;
		this.minWordCountThreshold = minWordCountThreshold;
	}

	@Override
	public final void prepare(final Map map, final TopologyContext topologyContext,
	                          final OutputCollector collector) {
		this.wordsTrackerMultiset = HashMultiset.create();
		//Doing this circus so that the output is in a proper descending order of the count of words.
		this.frequencyOfWords = Multimaps.newListMultimap(
			new TreeMap<Integer, Collection<String>>(Ordering.natural().reverse()), new Supplier<List<String>>() {
				public List<String> get() {
					return Lists.newArrayList();
				}
			}
		);
		this.stopwatch = Stopwatch.createStarted();
	}

	@Override
	public final void declareOutputFields(final OutputFieldsDeclarer outputFieldsDeclarer) {
	}

	@Override
	@SuppressWarnings("unchecked")
	public final void execute(final Tuple input) {
		final List<String> words = (List<String>) input.getValueByField("words");
		//Multiset simplifies the logic of adding a key to the Map and incrementing the value next time, etc redundant steps.
		this.wordsTrackerMultiset.addAll(words);

		if (logIntervalInSeconds <= stopwatch.elapsed(TimeUnit.SECONDS)) {
			logWordCount();
			this.stopwatch.reset();
			this.stopwatch.start();
		}
	}

	private final void logWordCount() {
		//We would like to get the count of words and its corresponding words.
		//Group words based on the count into a Multimap.
		for (String type : Multisets.copyHighestCountFirst(wordsTrackerMultiset).elementSet()) {
			this.frequencyOfWords.put(this.wordsTrackerMultiset.count(type), type);
		}
		final StringBuilder dumpWordsToLog = new StringBuilder();

		List<String> words;
		String keyString;
		int maxLength = 0;
		int keyStringLength = 0;
		for (final int key : this.frequencyOfWords.keySet()) {
			//Log only the words which have come at least minWordCountThreshold times.
			if (this.minWordCountThreshold < key) {
				keyString = String.valueOf(key);
				keyStringLength = keyString.length();
				maxLength = (maxLength == 0 ? keyStringLength : maxLength);
				words = (List<String>) this.frequencyOfWords.get(key);
				Collections.sort(words);
				dumpWordsToLog
						.append("\t")
						.append(Strings.padStart(keyString, (maxLength - keyStringLength), ' '))
						.append(" ==> ")
						.append(words)
						.append("\n");
			}
		}
		this.runCounter++;
		LOGGER.info("At {}, total # of words received in run#{}: {} ", new Date(), runCounter,
				           wordsTrackerMultiset.size());
		LOGGER.info("\n{}", dumpWordsToLog.toString());

		// Empty frequency and wordTracker Maps for further iterations.
		this.wordsTrackerMultiset.clear();
		this.frequencyOfWords.clear();
	}
}