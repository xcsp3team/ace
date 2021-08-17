package constraints;

import static org.xcsp.common.Constants.STAR;
import static org.xcsp.common.Constants.STAR_SYMBOL;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import constraints.ConstraintExtension.ExtensionGeneric.ExtensionV;
import constraints.extension.structures.ExtensionStructure;
import constraints.extension.structures.Table;
import constraints.extension.structures.Tries;
import dashboard.Control.SettingExtension;
import interfaces.FilteringSpecific;
import interfaces.Observers.ObserverOnBacktracks.ObserverOnBacktracksSystematic;
import interfaces.Tags.TagAC;
import interfaces.Tags.TagFilteringCompleteAtEachCall;
import interfaces.Tags.TagNegative;
import interfaces.Tags.TagPositive;
import interfaces.Tags.TagStarred;
import problem.Problem;
import propagation.Supporter.SupporterHard;
import utility.Kit;
import utility.Reflector;
import variables.TupleIterator;
import variables.Variable;
import variables.Variable.VariableInteger;
import variables.Variable.VariableSymbolic;

public abstract class ConstraintExtension extends Constraint implements TagAC, TagFilteringCompleteAtEachCall {

	/**********************************************************************************************
	 ***** Inner classes (Extension1, ExtensionGeneric and ExtensionGlobal)
	 *********************************************************************************************/

	/**
	 * This class is is used for unary extension constraints. Typically, filtering is performed at the root node of the search tree, and the constraint becomes
	 * entailed. BE CAREFUL: this is not a subclass of ConstraintExtension.
	 */
	public static final class Extension1 extends Constraint implements FilteringSpecific, TagAC, TagFilteringCompleteAtEachCall {

		@Override
		public boolean isSatisfiedBy(int[] t) {
			return (Arrays.binarySearch(values, t[0]) >= 0) == positive;
		}

		/**
		 * The set of values authorized (if positive is true) or forbidden (if positive is false) by this unary constraint
		 */
		private final int[] values;

		/**
		 * This field indicates if values are supports (when true) or conflicts (when false)
		 */
		private final boolean positive;

		/**
		 * Builds a unary extension constraint for the specified problem, involving the specified variable, and with semantics defined from the specified values
		 * and Boolean parameter
		 * 
		 * @param pb
		 *            the problem to which the constraint is attached
		 * @param x
		 *            the variable involved in the unary constraint
		 * @param values
		 *            the values defining the semantics of the constraint
		 * @param positive
		 *            if true, values are supports; otherwise values are conflicts
		 */
		public Extension1(Problem pb, Variable x, int[] values, boolean positive) {
			super(pb, new Variable[] { x });
			assert values.length > 0 && Kit.isStrictlyIncreasing(values);
			this.values = values;
			this.positive = positive;
			this.key = signature() + " " + values + " " + positive; // TODO can we use the address of values?
		}

		@Override
		public boolean runPropagator(Variable dummy) {
			// control(problem.solver.depth() == 0, () -> "depth: " + problem.solver.depth()); // cannot be used because after solutions, the entailed set may
			// be reset
			if (positive && scp[0].dom.removeValuesNotIn(values) == false)
				return false;
			if (!positive && scp[0].dom.removeValuesIn(values) == false)
				return false;
			assert scp[0].dom.size() > 0;
			return entailed();
		}
	}

	public abstract static class ExtensionGeneric extends ConstraintExtension {

		public ExtensionGeneric(Problem pb, Variable[] scp) {
			super(pb, scp);
		}

		/**
		 * Involves iterating lists of valid tuples in order to find a support.
		 */
		public static final class ExtensionV extends ExtensionGeneric {

			@Override
			protected ExtensionStructure buildExtensionStructure() {
				if (scp.length == 2)
					return Reflector.buildObject(settings.classBinary, ExtensionStructure.class, this);
				if (scp.length == 3)
					return Reflector.buildObject(settings.classTernary, ExtensionStructure.class, this);
				return new Table(this); // MDD(this);
			}

			public ExtensionV(Problem pb, Variable[] scp) {
				super(pb, scp);
			}
		}

		public static final class ExtensionVA extends ExtensionGeneric implements TagPositive {

			@Override
			protected ExtensionStructure buildExtensionStructure() {
				assert settings.variant == 0 || settings.variant == 1 || settings.variant == 11;
				return settings.variant == 0 ? new Table(this).withSubtables() : new Tries(this, settings.variant == 11);
			}

			public ExtensionVA(Problem pb, Variable[] scp) {
				super(pb, scp);
			}

			private final boolean seekSupportVA(int x, int a, int[] tuple, boolean another) {
				if (!another)
					tupleIterator.firstValidTupleWith(x, a, tuple);
				else if (tupleIterator.nextValidTupleCautiously() == -1)
					return false;
				while (true) {
					int[] t = extStructure.nextSupport(x, a, tuple);
					if (t == tuple)
						break;
					if (t == null)
						return false;
					Kit.copy(t, tuple);
					if (isValid(tuple))
						break;
					if (tupleIterator.nextValidTupleCautiously() == -1)
						return false;
				}
				return true;
			}

			@Override
			public final boolean seekFirstSupportWith(int x, int a, int[] buffer) {
				buffer[x] = a;
				return seekSupportVA(x, a, buffer, false);
			}
		}
	}

	public abstract static class ExtensionGlobal extends ConstraintExtension implements FilteringSpecific, ObserverOnBacktracksSystematic {

		public ExtensionGlobal(Problem pb, Variable[] scp) {
			super(pb, scp);
		}
	}

	/**********************************************************************************************
	 ***** Static Methods
	 *********************************************************************************************/

	private static ConstraintExtension build(Problem pb, Variable[] scp, boolean positive, boolean presentStar) {
		SettingExtension settings = pb.head.control.extension;
		Kit.control(scp.length > 1);
		Set<Class<?>> classes = pb.head.handlerClasses.get(ConstraintExtension.class);
		if (presentStar) {
			Kit.control(positive);
			String name = settings.positive.toString();
			ConstraintExtension c = (ConstraintExtension) Reflector.buildObject(name.equals("V") || name.equals("VA") ? "Extension" + name : name, classes, pb,
					scp);
			Kit.control(c instanceof TagStarred); // currently, STR2, STR2S, CT, CT2 and MDDSHORT
			return c;
		}
		if (scp.length == 2 && settings.validForBinary)
			return new ExtensionV(pb, scp); // return new CtrExtensionSTR2(pb, scp);
		String name = (positive ? settings.positive : settings.negative).toString();
		return (ConstraintExtension) Reflector.buildObject(name.equals("V") || name.equals("VA") ? "Extension" + name : name, classes, pb, scp);
	}

	private static int[][] reverseTuples(Variable[] scp, int[][] tuples) {
		Kit.control(Variable.areDomainsFull(scp));
		assert Kit.isLexIncreasing(tuples);
		int cnt = 0;
		TupleIterator tupleIterator = new TupleIterator(Variable.buildDomainsArrayFor(scp));
		int[] idxs = tupleIterator.firstValidTuple(), vals = new int[idxs.length];
		List<int[]> list = new ArrayList<>();
		do {
			for (int i = vals.length - 1; i >= 0; i--)
				vals[i] = scp[i].dom.toVal(idxs[i]);
			if (cnt < tuples.length && Arrays.equals(vals, tuples[cnt]))
				cnt++;
			else
				list.add(vals.clone());
		} while (tupleIterator.nextValidTuple() != -1);
		return Kit.intArray2D(list);
	}

	private static boolean isStarPresent(Object tuples) {
		return tuples instanceof int[][] ? Kit.isPresent(STAR, (int[][]) tuples) : Kit.isPresent(STAR_SYMBOL, (String[][]) tuples);
	}

	public static Constraint build(Problem pb, Variable[] scp, Object tuples, boolean positive, Boolean starred) {
		Kit.control(scp.length > 1 && Variable.haveSameType(scp));
		Kit.control(Array.getLength(tuples) == 0 || Array.getLength(Array.get(tuples, 0)) == scp.length,
				() -> "Badly formed extensional constraint " + scp.length + " " + Array.getLength(Array.get(tuples, 0)));
		if (starred == null)
			starred = isStarPresent(tuples);
		else
			assert starred == isStarPresent(tuples) : starred + " \n" + Kit.join(tuples);
		int[][] m = scp[0] instanceof VariableSymbolic ? pb.symbolic.replaceSymbols((String[][]) tuples) : (int[][]) tuples;
		if (scp[0] instanceof VariableInteger && !starred && pb.head.control.extension.mustReverse(scp.length, positive)) {
			m = reverseTuples(scp, m);
			positive = !positive;
		}
		ConstraintExtension c = build(pb, scp, positive, starred);
		c.storeTuples(m, positive);
		return c;
	}

	/**********************************************************************************************
	 * End of static section
	 *********************************************************************************************/

	/**
	 * The settings related to extension constraints
	 */
	protected final SettingExtension settings;

	public ExtensionStructure extStructure;

	protected abstract ExtensionStructure buildExtensionStructure();

	@Override
	public ExtensionStructure extStructure() {
		return extStructure;
	}

	/**
	 * In this overriding, we know that we can check directly indexes with the extension structure (by construction). As a result, we cannot check values
	 * anymore (see the other method).
	 */
	@Override
	public final boolean checkIndexes(int[] t) {
		return extStructure.checkIdxs(t);
	}

	@Override
	public final boolean isSatisfiedBy(int[] vals) {
		int[] t = tupleIterator.buffer;
		for (int i = vals.length - 1; i >= 0; i--)
			t[i] = doms[i].toIdx(vals[i]);
		return checkIndexes(t);
	}

	@Override
	public int[] symmetryMatching() {
		return extStructure.computeVariableSymmetryMatching(this);
	}

	public ConstraintExtension(Problem pb, Variable[] scp) {
		super(pb, scp);
		this.settings = pb.head.control.extension;
	}

	@Override
	public void cloneStructures(boolean onlyConflictsStructure) {
		super.cloneStructures(onlyConflictsStructure);
		if (!onlyConflictsStructure && extStructure.registeredCtrs().size() > 1) {
			extStructure.unregister(this);
			extStructure = Reflector.buildObject(extStructure.getClass().getSimpleName(), ExtensionStructure.class, this, extStructure);
			// IF NECESSARY, add another constructor in the class instance of ExtensionStructure
		}
	}

	public final void storeTuples(int[][] tuples, boolean positive) {
		String tableKey = signature() + " " + tuples + " " + positive; // TODO be careful, we assume that the address of tuples can be used. Is that correct?
		this.key = problem.features.collecting.tableKeys.computeIfAbsent(tableKey, k -> signature() + "r" + problem.features.collecting.tableKeys.size());

		control((positive && this instanceof TagPositive) || (!positive && this instanceof TagNegative)
				|| (!(this instanceof TagPositive) && !(this instanceof TagNegative)), positive + " " + this.getClass().getName());
		// System.out.println("Storing tuples for " + this + " " + Kit.join(tuples) + " " + positive);

		if (supporter != null)
			((SupporterHard) supporter).reset();

		Map<String, ExtensionStructure> map = problem.head.structureSharing.mapForExtension;
		extStructure = map.get(key);
		if (extStructure == null) {
			extStructure = buildExtensionStructure(); // note that the constraint is automatically registered
			extStructure.originalTuples = this instanceof ExtensionGeneric || problem.head.control.problem.isSymmetryBreaking() ? tuples : null;
			extStructure.originalPositive = positive;
			extStructure.storeTuples(tuples, positive);
			map.put(key, extStructure);
		} else {
			extStructure.register(this);
			assert indexesMatchValues == extStructure.firstRegisteredCtr().indexesMatchValues;
		}
	}

	boolean controlTuples(int[][] tuples) {
		return Stream.of(tuples).allMatch(t -> IntStream.range(0, t.length).allMatch(i -> t[i] == STAR || scp[i].dom.containsValue(t[i])));
	}
}