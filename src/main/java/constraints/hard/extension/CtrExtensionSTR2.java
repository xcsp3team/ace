/**
 * AbsCon - Copyright (c) 2017, CRIL-CNRS - lecoutre@cril.fr
 * 
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the CONTRAT DE LICENCE DE LOGICIEL LIBRE CeCILL which accompanies this
 * distribution, and is available at http://www.cecill.info
 */
package constraints.hard.extension;

import static org.xcsp.common.Constants.STAR;

import java.util.stream.Stream;

import problem.Problem;
import utility.Kit;
import variables.Variable;

// why not using a counter 'time' and replace boolean[][] ac by int[][] ac (we just do time++ instead of Arrays.fill(ac[x],false)
public class CtrExtensionSTR2 extends CtrExtensionSTROptimized {

	public CtrExtensionSTR2(Problem pb, Variable... scp) {
		super(pb, scp);
	}

	@Override
	protected void initSpecificStructures() {
		buildBasicCollectingStructures();
		buildBasicOptimizationSets();
	}

	protected boolean isValidTuple(int[] tuple) {
		for (int i = sValSize - 1; i >= 0; i--) {
			int x = sVal[i];
			if (tuple[x] != STAR && !doms[x].isPresent(tuple[x]))
				return false;
		}
		return true;
	}

	@SuppressWarnings("unused")
	private void removeInitialSequenceOfInvalidTuples() {
		int cnt = 0;
		for (int i = set.limit; i >= 0 && !isValidTuple(tuples[set.dense[i]]); i--)
			cnt++;
		if (cnt > 0)
			set.moveLimitAtLevel(cnt, pb.solver.depth());
	}

	// private boolean earlyBreak = false;

	@Override
	public boolean runPropagator(Variable dummy) {
		// System.out.println("tuu1 " + this + " " + futvars + " " + decremental);
		// pb.stuff.updateStatsForSTR(set);
		int depth = pb.solver.depth();
		// if (entailedDepth >= depth) return true;
		beforeFiltering();
		// removeInitialSequenceOfInvalidTuples();
		for (int i = set.limit; i >= 0; i--) {
			int[] tuple = tuples[set.dense[i]];
			// System.out.println("tuu " + Kit.join(tuple));
			if (isValidTuple(tuple)) {
				for (int j = sSupSize - 1; j >= 0; j--) {
					int x = sSup[j];
					int a = tuple[x];
					if (a == STAR) {
						cnts[x] = 0;
						sSup[j] = sSup[--sSupSize];
					} else if (!ac[x][a]) {
						ac[x][a] = true;
						if (--cnts[x] == 0)
							sSup[j] = sSup[--sSupSize];
					}
					// if (earlyBreak && sSupSize == 0) {
					// // System.out.println("gain " + i);
					// i = 0;
					// }
				}
			} else
				set.removeAtPosition(i, depth);
		}
		assert controlValidTuples();
		// if (Variable.computeNbValidTuplesFor(scope) == set.size()) { entailedDepth = depth; } // and for short tables ? ??
		return updateDomains();
	}

	private boolean controlValidTuples() {
		int[] dense = set.dense;
		for (int i = set.limit; i >= 0; i--) {
			int[] tuple = tuples[dense[i]];
			for (int j = tuple.length - 1; j >= 0; j--) {
				if (tuple[j] != STAR && !doms[j].isPresent(tuple[j])) {
					System.out.println(this + " at " + pb.solver.depth() + "\n" + Kit.join(tuple));
					Stream.of(scp).forEach(x -> x.display(true));
					return false;
				}
			}
		}
		return true;
	}

}
