package utility.operations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.xcsp.common.Utilities;

import utility.Kit;

public class Miner {

	public final CombinatorOfTwoInts combinator;

	protected int[][] selectedItems;

	protected double minSup; // minimum support for a frequent itemset in percentage, e.g. 0.8

	public int[][] getSelectedItems() {
		return selectedItems;
	}

	public Miner(int[] domainSizes, double minSup) {
		this.minSup = minSup;
		this.combinator = new CombinatorOfTwoInts(domainSizes.length - 1, Kit.computeMaxOf(domainSizes));
		Kit.control(minSup > 0 && minSup < 1);
	}

	public static class MinerApriori extends Miner {

		private int minimalItemSize = 1;

		private boolean isMatching(int[] item, int[] tuple) {
			for (int v : item)
				if (tuple[combinator.leftValueIn(v)] != combinator.rightValueIn(v))
					return false;
			return true;
		}

		private int[][] selectFrequentItemsFrom(int[][] currentItems, int[][] tuples) {
			List<int[]> frequentItems = new ArrayList<>(); // the frequent candidates for the current itemset
			for (int[] item : currentItems) {
				int nOccurrences = 0;
				for (int[] tuple : tuples)
					if (isMatching(item, tuple))
						nOccurrences++;
				if ((nOccurrences / (double) (tuples.length)) >= minSup)
					frequentItems.add(item);
			}
			return Kit.intArray2D(frequentItems);
		}

		private boolean isVariableInvolvedIn(int variablePosition, int[] item) {
			for (int v : item)
				if (combinator.leftValueIn(v) == variablePosition)
					return true;
			return false;
		}

		private int[][] createNewItemsFrom(int[][] items) {
			// by construction, all existing currItems have the same size
			int newItemSize = items[0].length + 1;
			List<int[]> newItems = new ArrayList<>();
			// compare each pair of items of size n-1
			for (int i = 0; i < items.length; i++)
				for (int j = i + 1; j < items.length; j++)
					for (int v : items[j]) {
						if (!isVariableInvolvedIn(combinator.leftValueIn(v), items[i])) {
							int[] newItem = new int[newItemSize];
							System.arraycopy(items[i], 0, newItem, 0, newItem.length - 1);
							newItem[newItem.length - 1] = v; // we put the missing value at the end
							newItems.add(newItem);
						}
					}
			return Kit.intArray2D(newItems);
		}

		private int[][] selectFrequentItems(int[] domainSizes, int[][] tuples) {
			List<int[]> selectedItems = new ArrayList<>();
			// building Items of Size1
			int[][] currentItems = new int[(int) Kit.sum(domainSizes)][1];
			for (int i = 0, cnt = 0; i < domainSizes.length; i++)
				for (int j = 0; j < domainSizes[i]; j++)
					currentItems[cnt++][0] = combinator.combinedIntValueFor(i, j);
			// select and iterate building new items (of size increased by 1)
			while (currentItems.length >= 1 && currentItems[0].length < domainSizes.length) {
				currentItems = selectFrequentItemsFrom(currentItems, tuples);
				if (currentItems.length > 0) {
					if (currentItems[0].length >= minimalItemSize)
						for (int[] item : currentItems)
							selectedItems.add(item);
					currentItems = createNewItemsFrom(currentItems);
				}
			}
			return Kit.intArray2D(selectedItems);
		}

		public MinerApriori(int[] domainSizes, int[][] tuples, double minSup) {
			super(domainSizes, minSup);
			this.selectedItems = selectFrequentItems(domainSizes, tuples);
		}
	}

	public static class MinerFPTree extends Miner {

		/**********************************************************************************************
		 * Intern classes
		 *********************************************************************************************/

		private class Node {
			private int value;

			private int frequencyCounter = 1; // (a.k.a. support)

			private List<Node> childs = new ArrayList<>();

			private Node(int value) {
				this.value = value;
			}

			private Node getChildWithValue(int value) {
				for (Node child : childs)
					if (child.value == value)
						return child;
				return null;
			}
		}

		private class Tree {
			private Node root;

			private Tree() {
				this.root = new Node(-1);
			}

			private void addTransaction(List<Integer> transaction) {
				Node currentNode = root;
				for (int value : transaction) {
					Node child = currentNode.getChildWithValue(value);
					if (child == null) {
						child = new Node(value);
						currentNode.childs.add(child);
					} else
						child.frequencyCounter++;
					currentNode = child;
				}
			}
		}

		/**********************************************************************************************
		 * Fields and Methods
		 *********************************************************************************************/

		private static final int SUPPORT_THRESHOLD = 80;

		private int[] computeFrequencyOfValues(int[] domainSizes, int[][] tuples) {
			int[] frequencies = new int[domainSizes.length * (Kit.computeMaxOf(domainSizes) + 1)];
			for (int[] tuple : tuples)
				for (int i = 0; i < tuple.length; i++)
					frequencies[combinator.combinedIntValueFor(i, tuple[i])]++;
			return frequencies;
		}

		private List<int[]> collectFrequentPatterns(Node node, int[] prefix, List<int[]> patterns) {
			for (Node child : node.childs) {
				if (child.frequencyCounter < SUPPORT_THRESHOLD)
					continue;
				int[] newPrefix = Utilities.collectInt(prefix, child.value);
				int sizeBefore = patterns.size();
				if (child.childs.size() != 0) {
					patterns = collectFrequentPatterns(child, newPrefix, patterns);
					if (patterns.size() == sizeBefore)
						patterns.add(newPrefix); // Kit.prn(Kit.implode(p) + "(" + fils.counter + ")");
				} else
					patterns.add(newPrefix);
			}
			return patterns;
		}

		private List<int[]> normalize(List<int[]> patterns) {
			List<int[]> keepIt = new ArrayList<>();
			for (int i = 0; i < patterns.size(); i++) {
				int[] pattern = patterns.get(i);
				if (pattern.length == 1)
					continue;
				boolean subsumed = false;
				for (int j = i + 1; !subsumed && j < patterns.size(); j++)
					if (Kit.isPrefix(pattern, patterns.get(j)))
						subsumed = true;
				if (!subsumed)
					keepIt.add(pattern);
			}
			return keepIt;
		}

		public MinerFPTree(int[] domainSizes, int[][] tuples, double minSup) {
			super(domainSizes, minSup);
			final int[] valuesFrequencies = computeFrequencyOfValues(domainSizes, tuples);
			Tree tree = new Tree();
			List<Integer> transaction = new ArrayList<>();
			for (int[] tuple : tuples) {
				for (int i = 0; i < tuple.length; i++) {
					int value = combinator.combinedIntValueFor(i, tuple[i]);
					// if (valuesFrequencies[value] >= tuples.length * proba[j] * minSup)
					if (valuesFrequencies[value] >= SUPPORT_THRESHOLD)
						transaction.add(value);
				}
				Collections.sort(transaction, new Comparator<Integer>() {
					@Override
					public int compare(Integer value1, Integer value2) {
						int compare = valuesFrequencies[value2] - valuesFrequencies[value1];
						return compare == 0 ? value2 - value1 : compare;
					}
				});
				tree.addTransaction(transaction);
				transaction.clear();
			}
			// printTree(tree.root, "");
			Comparator<int[]> comparator = new Comparator<int[]>() {
				@Override
				public int compare(int[] item1, int[] item2) {
					return (item1.length == 0 || item2.length == 0) ? 0 : item1.length < item2.length ? -1 : 1;
				}
			};
			List<int[]> frequentPatterns = collectFrequentPatterns(tree.root, new int[0], new ArrayList<int[]>());
			Collections.sort(frequentPatterns, comparator);
			selectedItems = Kit.intArray2D(normalize(frequentPatterns));
			Arrays.sort(selectedItems, comparator);
		}

		@SuppressWarnings("unused")
		private void printTree(Node node, String prefix) {
			if (node.childs.size() != 0)
				for (Node child : node.childs)
					printTree(child, prefix + " " + child.value + "(" + child.frequencyCounter + ")");
			else
				Kit.log.finest(prefix);
		}
	}

}