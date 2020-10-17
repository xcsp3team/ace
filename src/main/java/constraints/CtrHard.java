/**
 * AbsCon - Copyright (c) 2017, CRIL-CNRS - lecoutre@cril.fr
 * 
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the CONTRAT DE LICENCE DE LOGICIEL LIBRE CeCILL which accompanies this
 * distribution, and is available at http://www.cecill.info
 */
package constraints;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.xcsp.common.Types.TypeFramework;

import constraints.hard.ConflictsStructure;
import constraints.hard.CtrExtension;
import constraints.hard.extension.structures.Bits;
import constraints.hard.global.SumSimple.SumSimpleEQ;
import constraints.hard.global.SumWeighted.SumWeightedEQ;
import executables.Resolution;
import interfaces.FilteringSpecific;
import problem.Problem;
import propagation.order1.PropagationForward;
import propagation.structures.revisers.Reviser;
import propagation.structures.revisers.Reviser3;
import propagation.structures.supporters.SupporterHard;
import propagation.structures.supporters.SupporterHardBary;
import propagation.structures.supporters.SupporterHardNary;
import utility.Kit;
import variables.Variable;
import variables.domains.Domain;
import variables.domains.DomainInteger.DomainRange;
import variables.domains.DomainInteger.DomainValues;

public abstract class CtrHard extends Constraint {

	/**
	 * The assistant which manages information about the number of conflicts of the constraint.
	 */
	protected ConflictsStructure conflictsStructure;

	@Override
	public ConflictsStructure conflictsStructure() {
		return conflictsStructure;
	}

	@Override
	public void cloneStructures(boolean onlyConflictsStructure) {
		if (conflictsStructure != null && conflictsStructure.registeredCtrs().size() > 1) {
			conflictsStructure.unregister(this);
			conflictsStructure = new ConflictsStructure(conflictsStructure, this);
		}
	}

	public void updateConflictsStructures(int[] frontier) {
		if (conflictsStructure != null && Stream.of(scp).anyMatch(x -> x.dom.lastRemoved() != frontier[x.num]) && !usePredefinedMaxNumberOfConflicts()) {
			if (conflictsStructure.registeredCtrs().size() > 1)
				cloneStructures(true);
			conflictsStructure.updateCounters(frontier);
		}
	}

	/**
	 * This function must be such that if (an upper bound of) the number of max conflicts is known for one pair (variable, index) then it is known for
	 * any pair
	 */
	public int giveUpperBoundOfMaxNumberOfConflictsFor(Variable x, int a) {
		return Resolution.UNDEFINED; // by default
	}

	/**
	 * we assume that if the number of max conflicts is known for one pair (variable, index) then it is known for any pair
	 */
	public boolean usePredefinedMaxNumberOfConflicts() {
		return giveUpperBoundOfMaxNumberOfConflictsFor(scp[0], scp[0].dom.first()) != Resolution.UNDEFINED;
	}

	/**
	 * Private constructor just used to build the TAG constraint.
	 */
	protected CtrHard() {}

	public CtrHard(Problem pb, Variable[] scp) {
		super(pb, scp);
		buildSupporter();
	}

	@Override
	public void buildSupporter() {
		if (pb.rs.cp.settingPropagation.residues != (supporter != null))
			if (pb.rs.cp.settingPropagation.residues && scp.length > 1 && !(this instanceof FilteringSpecific)
					&& !(pb.rs.cp.settingPropagation.classForRevisions.equals(Reviser3.class.getSimpleName()) && extStructure() instanceof Bits))
				supporter = scp.length == 2 ? new SupporterHardBary(this) : new SupporterHardNary(this);
			else
				supporter = null;
	}

	/**********************************************************************************************
	 * Start of methods
	 *********************************************************************************************/

	/**
	 * Determines if the given tuple is a support of the constraint, i.e., if the given tuple belongs to the relation associated with the constraint.
	 * Be careful: although indexes of values are managed in the core of the solver, at this stage, the given tuple contains values (and not indexes
	 * of values).
	 * 
	 * @return true iff the tuple is a support of the constraint
	 */
	public abstract boolean checkValues(int[] t);

	/**
	 * Determines if the given tuple is a support of the constraint, i.e., if the given tuple belongs to the relation associated with the constraint.
	 * Be careful: the given tuple must contains indexes of values.
	 * 
	 * @param target
	 *            a given tuple of indexes (of values)
	 * @return true iff the tuple of values corresponding to the given tuple of indexes is a support of the constraint
	 */
	public boolean checkIndexes(int[] t) {
		pb.stuff.nCcks++;
		return indexesMatchValues ? checkValues(t) : checkValues(toVals(t));
	}

	/** All variables of the scope must be fixed. */
	public boolean checkCurrentInstantiation() {
		return checkIndexes(buildCurrentInstantiationTuple());
	}

	/**********************************************************************************************
	 * Supports and conflicts
	 *********************************************************************************************/

	/**
	 * Seeks a support for the constraint when considering the current state of the domains and the tuple currently managed by the tuple manager
	 * (initial value of the current tuple included in search). A lexicographic order is used.
	 */
	private final boolean seekSupport() {
		return tupleManager.findValidTupleSuchThat(t -> checkIndexes(t));
	}

	public final boolean seekFirstSupport() {
		tupleManager.firstValidTuple();
		return seekSupport();
	}

	public final boolean seekFirstSupport(int[] buffer) {
		tupleManager.firstValidTuple(buffer);
		return seekSupport();
	}

	public final boolean seekFirstSupportWith(int x, int a) {
		tupleManager.firstValidTupleWith(x, a);
		return seekSupport();
	}

	public boolean seekFirstSupportWith(int x, int a, int[] buffer) {
		tupleManager.firstValidTupleWith(x, a, buffer);
		return seekSupport();
	}

	public final boolean seekFirstSupportWith(int x, int a, int y, int b) {
		tupleManager.firstValidTupleWith(x, a, y, b);
		return seekSupport();
	}

	public final boolean seekFirstSupportWith(int x, int a, int y, int b, int[] buffer) {
		tupleManager.firstValidTupleWith(x, a, y, b, buffer);
		return seekSupport();
	}

	// The next support is searched for from tupleManager.currTuple(), excluded, which is not necessarily valid (as it may have been
	// deleted). If some values have been fixed, they remain fixed
	public final boolean seekNextSupport() {
		return tupleManager.nextValidTupleCautiously() != -1 && seekSupport();
	}

	// public boolean seekNextSupportSafe() {
	// assert isValid(tupleManager.currTuple());
	// return tupleManager.nextValidTuple() != -1 && seekSupport();
	// }

	private final boolean seekConflict() {
		return tupleManager.findValidTupleSuchThat(t -> !checkIndexes(t));
		// assert tupleManager.currTuple() == inoutTuple;
		// // assert checkValidityOf(inoutTuple); // A REMETREet A ENLEVR POUR ?????
		// while (true)
		// if (!checkIndexes(inoutTuple))
		// return true;
		// else if (tupleManager.nextValidTuple() == -1)
		// return false;
	}

	public final boolean seekFirstConflict() {
		tupleManager.firstValidTuple();
		return seekConflict();
	}

	public final boolean seekFirstConflictWith(int x, int a) {
		tupleManager.firstValidTupleWith(x, a);
		return seekConflict();
	}

	public long nSupports() {
		tupleManager.firstValidTuple();
		return tupleManager.countValidTuplesSuchThat(t -> checkIndexes(t));
	}

	public long nConflicts() {
		tupleManager.firstValidTuple();
		return tupleManager.countValidTuplesSuchThat(t -> !checkIndexes(t));
	}

	public long nSupportsFor(int x, int a) {
		tupleManager.firstValidTupleWith(x, a);
		return tupleManager.countValidTuplesSuchThat(t -> checkIndexes(t));
	}

	public long nConflictsFor(int x, int a) {
		tupleManager.firstValidTupleWith(x, a);
		return tupleManager.countValidTuplesSuchThat(t -> !checkIndexes(t));
	}

	/**********************************************************************************************
	 * Methods related to filtering
	 *********************************************************************************************/

	@Override
	public long costOfIdxs(int[] idxs) {
		return checkIndexes(idxs) ? 0 : cost;
	}

	@Override
	public long minCostOfTuplesWith(int x, int a) {
		return seekFirstSupportWith(x, a) ? 0 : cost; // problem.getSolver().solutionManager.getBestCostFound();
	}

	public boolean findArcSupportFor(int x, int a) {
		if (supporter != null)
			return ((SupporterHard) supporter).findArcSupportFor(x, a);
		if (extStructure() instanceof Bits) {
			long[] t1 = ((Bits) extStructure()).bitSupsFor(x)[a];
			long[] t2 = scp[x == 0 ? 1 : 0].dom.binaryRepresentation();
			for (int i = 0; i < t1.length; i++) {
				pb.stuff.nCcks++;
				if ((t1[i] & t2[i]) != 0)
					return true;
			}
			return false;
		}
		// AC3 below
		return seekFirstSupportWith(x, a);
	}

	private boolean genericFiltering(Variable x) {
		Reviser reviser = ((PropagationForward) pb.solver.propagation).reviser;
		if (x.isAssigned()) {
			for (int i = futvars.limit; i >= 0; i--)
				if (reviser.revise(this, scp[futvars.dense[i]]) == false)
					return false;
		} else {
			boolean revisingEventVarToo = (scp.length == 1); // TODO can we just initialize it to false ?
			for (int i = futvars.limit; i >= 0; i--) {
				Variable y = scp[futvars.dense[i]];
				if (y == x)
					continue;
				if (timestamp < y.timestamp)
					revisingEventVarToo = true;
				if (reviser.revise(this, y) == false)
					return false;
			}
			if (revisingEventVarToo && reviser.revise(this, x) == false)
				return false;
		}
		return true;
	}

	/**
	 * This is the method that is called for filtering. We know that the domain of the specified variable has been recently reduced, but this is not
	 * necessarily the only one in that situation.
	 */
	@Override
	public final boolean filterFrom(Variable x) {
		// System.out.println("fileting " + this + " " + x);
		if (this.hugeDomainVars.length > 0) {

			if (futvars.size() == 0)
				return this.checkCurrentInstantiation();
			if (futvars.size() == 1) {
				if (this instanceof SumSimpleEQ) {
					((SumSimpleEQ) this).deduce();
					return true;
				}
				if (this instanceof SumWeightedEQ) {
					((SumWeightedEQ) this).deduce();
					return true;
				}

				// Variable y = scp[futvars.dense[0]];
				// assert y.dom instanceof DomainHuge;
				// if (this instanceof )

			}

			// for (Variable y : hugeDomainVars)
			// if (!y.isAssigned())
			// return true; // we have to wait

			if (futvars.size() > 0)
				return true;
		}

		/*
		 * The test below remains valid if filtering is performed after every assignment (no two direct assignments can be performed without an
		 * intermediary filtering), nFC0 must also be guaranteed (our hypothesis). variableManager.nbFutureVars() == 1 && evt.isFuture()remains valid
		 * as long as all constraints involving the last assigned variable are considered first (because a variable might have been assigned, and evt
		 * reduced before considering the constraint). Watch the second condition when modifying code.
		 */

		// For CSP, there are first some conditions that allow us to directly return true (because we know then that there is no filtering
		// possibility)
		if (pb.framework == TypeFramework.CSP) {
			if (futvars.size() == 0) {
				if (isGuaranteedGAC()) {
					assert checkCurrentInstantiation() : "Unsatisfied constraint " + this;
					return true;
				} else
					return checkCurrentInstantiation();
			}
			if (futvars.size() == 1 && x.isFuture() && scp.length > 1)
				return true;
		}
		int nBefore = pb.nValuesRemoved;
		boolean consistent = true;
		if (this instanceof FilteringSpecific) {
			if (timestamp > x.timestamp && completeFilteringAtEachCall())
				return true;
			consistent = ((FilteringSpecific) this).runPropagator(x);
		} else {
			if (timestamp > x.timestamp || futvars.size() > genericFilteringThreshold)
				return true;
			consistent = genericFiltering(x);
		}
		if (!consistent || pb.nValuesRemoved != nBefore)
			this.handleEffectiveFilterings();
		if (consistent == false)
			return false;
		timestamp = pb.solver.propagation.incrementTime();
		return true;
	}

	public boolean isIrreflexive() {
		Kit.control(scp.length == 2);
		int[] tuple = tupleManager.localTuple;
		int p = scp[0].dom.size() > scp[1].dom.size() ? 1 : 0, q = p == 0 ? 1 : 0;
		Domain dom = scp[p].dom, domSib = scp[q].dom;
		for (int a = dom.first(); a != -1; a = dom.next(a)) {
			int b = domSib.toIdx(dom.toVal(a));
			if (b < 0)
				continue;
			tuple[p] = a;
			tuple[q] = b;
			if (checkIndexes(tuple))
				return false;
		}
		return true;
	}

	@Override
	public boolean isSubstitutableBy(Variable x, int a, int b) {
		int px = positionOf(x);
		tupleManager.firstValidTupleWith(px, a);
		return !tupleManager.findValidTupleSuchThat(t -> {
			t[px] = a;
			boolean b1 = checkIndexes(t);
			t[px] = b;
			boolean b2 = checkIndexes(t);
			return b1 && !b2;
		});
	}

	@Override
	public boolean controlArcConsistency() {
		if (ignored)
			return true;
		if (Variable.nValidTuplesBoundedAtMaxValueFor(scp) > 1000)
			return true;
		for (int i = 0; i < scp.length; i++)
			for (int a = doms[i].first(); a != -1; a = doms[i].next(a))
				if (!seekFirstSupportWith(i, a)) {
					Kit.log.warning(" " + scp[i] + "=" + a + " not supported by " + this);
					display(true);
					return false;
				}
		return true;
	}

	public void save4Baudouin() {
		if (!(this instanceof CtrExtension))
			return;
		boolean toIdx = true;
		int[][] tuples = null;
		if (this instanceof CtrExtension && ((CtrExtension) this).extStructure().originalTuples != null
				&& ((CtrExtension) this).extStructure().originalPositive) {
			// System.out.println("Direct");
			tuples = ((CtrExtension) this).extStructure().originalTuples;
			if (toIdx)
				for (int i = 0; i < tuples.length; i++) {
					int[] t = tuples[i];
					tuples[i] = IntStream.range(0, scp.length).map(j -> scp[j].dom.toIdx(t[j])).toArray();
				}
		} else {
			List<int[]> list = new ArrayList<>();
			tupleManager.firstValidTuple();
			tupleManager.overValidTuples(t -> {
				if (checkIndexes(t))
					list.add(toIdx ? t.clone() : toVals(t).clone());
			});
			tuples = Kit.intArray2D(list);
		}

		System.out.println(pb.name());
		int first = pb.name().lastIndexOf(File.separator) + 1, last = pb.name().indexOf(".xml");
		String s = (last != -1 ? pb.name().substring(first, last) : pb.name().substring(first)) + "-" + (this.num == 0 ? tuples.length : "tab" + this.num);
		System.out.println("Saving Baudouin Data in filename = " + s);

		try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(s)))) {
			out.println(scp.length + " variables");
			if (toIdx)
				Stream.of(scp).forEach(x -> out.println(x.id() + " " + 0 + ".." + (x.dom.initSize() - 1)));
			else
				Stream.of(scp).forEach(x -> out.println(x.id() + " "
						+ (x.dom instanceof DomainRange ? x.dom.firstValue() + ".." + x.dom.lastValue() : Kit.join(((DomainValues) x.dom).values))));
			// if (this instanceof CtrExtension && ((CtrExtension) this).extStructure().originalTuples != null
			// && ((CtrExtension) this).extStructure().originalPositive) {
			// System.out.println("Direct");
			// int[][] tuples = ((CtrExtension) this).extStructure().originalTuples;
			// out.println(tuples.length + " tuples");
			// for (int[] t : tuples)
			// out.println(Kit.join(toIdx ? IntStream.range(0, scp.length).map(i -> scp[i].dom.toIdx(t[i])).toArray() : t));
			// } else {
			// List<int[]> list = new ArrayList<>();
			// int[] tuple = tupleManager.setFirstValidTuple();
			// while (true) {
			// if (checkIdxs(tuple))
			// list.add(toIdx ? tuple.clone() : toVals(tuple).clone());
			// if (tupleManager.setNextValidTuple() == -1)
			// break;
			// }
			// }
			out.println(tuples.length + " tuples");
			for (int[] t : tuples)
				out.println(Kit.join(t));

		} catch (Exception e) {
			Kit.exit("Pb " + e);
		}
	}
}