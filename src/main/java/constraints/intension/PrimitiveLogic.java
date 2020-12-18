/**
 * AbsCon - Copyright (c) 2017, CRIL-CNRS - lecoutre@cril.fr
 * 
 * All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the CONTRAT DE LICENCE DE LOGICIEL LIBRE CeCILL which accompanies this
 * distribution, and is available at http://www.cecill.info
 */
package constraints.intension;

import java.util.stream.Stream;

import org.xcsp.common.Types.TypeLogicalOperator;

import constraints.Constraint;
import interfaces.Tags.TagFilteringCompleteAtEachCall;
import interfaces.Tags.TagGACGuaranteed;
import interfaces.Tags.TagUnsymmetric;
import problem.Problem;
import utility.Kit;
import variables.Domain;
import variables.Variable;

public abstract class PrimitiveLogic extends Primitive implements TagGACGuaranteed, TagUnsymmetric {

	Variable x;
	Variable[] list;

	Domain dx;

	public PrimitiveLogic(Problem pb, Variable x, Variable[] list) {
		super(pb, pb.api.vars(x, list));
		this.x = x;
		this.dx = x.dom;
		this.list = list;
		control(list.length > 1 && !Kit.isPresent(x, list) && Variable.areAllInitiallyBoolean(scp), "Variables must be o1");
	}

	// ************************************************************************
	// ***** Classes for x = (y <op> z) (CtrPrimitiveTernaryLog)
	// ************************************************************************

	public static abstract class PrimitiveLogicEq extends PrimitiveLogic {

		public static Constraint buildFrom(Problem pb, Variable x, TypeLogicalOperator op, Variable[] list) {
			switch (op) {
			case OR:
				return list.length == 2 ? new LogEqOr2(pb, x, list) : new LogEqOr(pb, x, list);
			case AND:
				return list.length == 2 ? new LogEqAnd2(pb, x, list) : null;
			default:
				return null; // throw new AssertionError();
			}
		}

		public PrimitiveLogicEq(Problem pb, Variable x, Variable[] list) {
			super(pb, x, list);
		}

		public static final class LogEqAnd2 extends PrimitiveLogicEq implements TagFilteringCompleteAtEachCall {

			Variable y, z;
			Domain dy, dz;

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] == 0 ? t[1] == 0 || t[2] == 0 : t[1] == 1 && t[2] == 1;
			}

			public LogEqAnd2(Problem pb, Variable x, Variable[] list) {
				super(pb, x, list);
				control(list.length == 2);
				this.y = list[0];
				this.z = list[1];
				this.dy = y.dom;
				this.dz = z.dom;
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				if (dx.first() == 1) // x = 1 => y = 1 and z = 1
					return dy.removeIfPresent(0) && dz.removeIfPresent(0) && entailed();
				if (!dy.present(1) || !dz.present(1)) // y != 1 or z != 1 => x != 1
					return dx.removeIfPresent(1) && entailed();
				// 0 is present in dx, and 1 in dy and 1 in dz; if present, (x,1) is supported
				if (dy.size() == 1 && dz.size() == 1) // y = 1 and z = 1 => x = 1
					return dx.remove(0) && entailed();
				// (x,0) is supported
				if (dx.size() == 2)
					return true;
				// only 0 for x
				if (dy.size() == 2 && dz.size() == 2)
					return true;
				if (dy.size() == 2)
					return dy.remove(1) && entailed();
				else // dz.size() == 2
					return dz.remove(1) && entailed();
			}
		}

		public static final class LogEqOr2 extends PrimitiveLogicEq implements TagFilteringCompleteAtEachCall {

			Variable y, z;
			Domain dy, dz;

			@Override
			public final boolean checkValues(int[] t) {
				return t[0] == 0 ? t[1] == 0 && t[2] == 0 : t[1] == 1 || t[2] == 1;
			}

			public LogEqOr2(Problem pb, Variable x, Variable[] list) {
				super(pb, x, list);
				control(list.length == 2);
				this.y = list[0];
				this.z = list[1];
				this.dy = y.dom;
				this.dz = z.dom;
			}

			@Override
			public boolean runPropagator(Variable dummy) {
				if (dx.last() == 0) // x = 0 => y = 0 and z = 0
					return dy.removeIfPresent(1) && dz.removeIfPresent(1) && entailed();
				if (!dy.present(0) || !dz.present(0)) // y != 0 or z != 0 => x != 0
					return dx.removeIfPresent(0) && entailed();
				// 1 is present in dx, and 0 in dy and 0 in dz; if present, (x,0) is supported
				if (dy.size() == 1 && dz.size() == 1) // y = 0 and z = 0 => x = 0
					return dx.remove(1) && entailed();
				// (x,1) is supported
				if (dx.size() == 2)
					return true;
				// only 1 for x
				if (dy.size() == 2 && dz.size() == 2)
					return true;
				if (dy.size() == 2)
					return dy.remove(0) && entailed();
				else // dz.size() == 2
					return dz.remove(0) && entailed();
			}
		}

		public static final class LogEqOr extends PrimitiveLogicEq {

			@Override
			public final boolean checkValues(int[] t) {
				if (t[0] == 0) { // if x = 0
					for (int v : t)
						if (v == 1)
							return false;
					return true;
				}
				// x == 1
				for (int i = 1; i < t.length; i++)
					if (t[i] == 1)
						return true;
				return false;
			}

			private Variable sentinel1, sentinel2; // for variables in the list supporting (x,1)

			private Variable findSentinel(Variable other) {
				for (Variable y : list)
					if (y != other && y.dom.last() == 1)
						return y;
				return null;
			}

			public LogEqOr(Problem pb, Variable x, Variable[] list) {
				super(pb, x, list);
				this.sentinel1 = list[0];
				this.sentinel2 = list[1];
			}

			@Override
			public boolean runPropagator(Variable evt) {
				if (evt != null && evt != x && evt.dom.first() == 1)
					return dx.removeIfPresent(0) && entailed(); // for some j, y_j = 1 => x = 1
				assert Stream.of(list).allMatch(y -> y.dom.first() == 0) : "0 should be present in the domain of every variable of the list";
				if (dx.last() == 0) { // x = 0 => y_j = 0 for any j
					for (Variable y : list)
						y.dom.removeIfPresent(1); // no possible inconsistency since 0 is also present
					return entailed();
				}
				// it remains to check that (x,1) is supported (as well as any (y_j,1) equivalently)
				if (dx.first() == 1) { // we need two valid sentinels
					if (sentinel1.dom.last() == 0) {
						Variable y = findSentinel(sentinel2);
						if (y == null)
							return sentinel2.dom.remove(0) && entailed();
						else
							sentinel1 = y;
					}
					if (sentinel2.dom.last() == 0) {
						Variable y = findSentinel(sentinel1);
						if (y == null)
							return sentinel1.dom.remove(0) && entailed();
						else
							sentinel2 = y;
					}
					return true;
				}
				// we just need one sentinel
				if (sentinel1.dom.last() == 1 || sentinel2.dom.last() == 1)
					return true;
				for (Variable y : list)
					if (y.dom.last() == 1) {
						sentinel1 = y;
						return true;
					}
				return dx.remove(1) && entailed();
			}
		}

	}
}