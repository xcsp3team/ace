/*
 * This file is part of the constraint solver ACE (AbsCon Essence). 
 *
 * Copyright (c) 2021. All rights reserved.
 * Christophe Lecoutre, CRIL, Univ. Artois and CNRS. 
 * 
 * Licensed under the MIT License.
 * See LICENSE file in the project root for full license information.
 */

package heuristics;

import java.util.Set;
import java.util.stream.Stream;

import dashboard.Control.SettingVarh;
import dashboard.Control.SettingVars;
import interfaces.Observers.ObserverOnRuns;
import problem.Problem;
import propagation.GIC.GIC2;
import solver.Solver;
import utility.Kit;
import utility.Reflector;
import variables.DomainInfinite;
import variables.Variable;

/**
 * This class gives the description of a variable ordering heuristic. <br>
 * A variable ordering heuristic is used by a backtrack search solver to select a variable (to be assigned) at each step of search. <br>
 */
public abstract class HeuristicVariables extends Heuristic {

	public static HeuristicVariables buildFor(Solver solver) {
		Set<Class<?>> classes = solver.head.availableClasses.get(HeuristicVariables.class);
		if (solver.head.control.solving.enableSearch || solver.propagation instanceof GIC2)
			return Reflector.buildObject(solver.head.control.varh.heuristic, classes, solver, solver.head.control.varh.anti);
		return null;
	}

	public static class BestScoredVariable {
		public Variable variable;
		public double score;
		public boolean minimization;
		private boolean discardAux;

		public BestScoredVariable(boolean discardAux) {
			this.discardAux = discardAux;
		}

		public BestScoredVariable() {
			this(false);
		}

		public BestScoredVariable reset(boolean minimization) {
			this.variable = null;
			this.score = minimization ? Double.MAX_VALUE : Double.NEGATIVE_INFINITY;
			this.minimization = minimization;
			return this;
		}

		public boolean update(Variable x, double s) {
			if (discardAux && x.isAuxiliaryVariableIntroducedBySolver()) {
				assert x.id().startsWith(Problem.AUXILIARY_VARIABLE_PREFIX);
				return false;
			}
			if (minimization) {
				if (s < score) {
					variable = x;
					score = s;
					return true;
				}
			} else if (s > score) {
				variable = x;
				score = s;
				return true;
			}
			return false; // not updated
		}
	}

	/** The backtrack search solver that uses this variable ordering heuristic. */
	protected final Solver solver;

	/** The variables that must be assigned in priority. */
	protected Variable[] priorityVars;

	/**
	 * Relevant only if priorityVariables is not an array of size 0. Variables must be assigned in the order strictly given by priorityVariables from 0 to
	 * nbStrictPriorityVariables-1. Variables in priorityVariables recorded between nbStrictPriorityVariables and priorityVariables.length-1 must then be
	 * assigned in priority but in any order given by the heuristic. Beyond priorityVariables.length-1, the heuristic can select any future variable.
	 */
	private int nStrictlyPriorityVars;

	protected SettingVarh settings;

	protected BestScoredVariable bestScoredVariable;

	public final void setPriorityVars(Variable[] priorityVars, int nbStrictPriorityVars) {
		this.priorityVars = priorityVars;
		this.nStrictlyPriorityVars = nbStrictPriorityVars;
	}

	public final void resetPriorityVars() {
		this.priorityVars = new Variable[0];
		this.nStrictlyPriorityVars = 0;
	}

	public HeuristicVariables(Solver solver, boolean antiHeuristic) {
		super(antiHeuristic); // anti ? (this instanceof TagMinimize ? TypeOptimization.MAX : TypeOptimization.MIN ) : (this instanceof TagMinimize ?
								// TypeOptimization.MAX : TypeOptimization.MIN );
		this.solver = solver;
		SettingVars settingVars = solver.head.control.variables;
		if (settingVars.priorityVars.length > 0) {
			this.priorityVars = Stream.of(settingVars.priorityVars).map(o -> solver.problem.findVarWithNumOrId(o)).toArray(Variable[]::new);
			this.nStrictlyPriorityVars = settingVars.nStrictPriorityVars;
		} else {
			this.priorityVars = solver.problem.priorityVars;
			this.nStrictlyPriorityVars = solver.problem.nStrictPriorityVars;
		}
		this.settings = solver.head.control.varh;
		this.bestScoredVariable = new BestScoredVariable(solver.head.control.varh.discardAux);
	}

	/** Returns the score of the specified variable. This is the method to override when defining a new heuristic. */
	public abstract double scoreOf(Variable x);

	/** Returns the score of the specified variable, while considering the optimization coeff (i.e., minimization or maximization). */
	public final double scoreOptimizedOf(Variable x) {
		if (x.dom instanceof DomainInfinite)
			return multiplier == -1 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY; // because such variables must be computed (and not
																							// assigned)
		return scoreOf(x) * multiplier;
	}

	/** Returns the preferred variable among those that are priority. */
	private Variable bestPriorityVar() {
		int nPast = solver.futVars.nPast();
		if (nPast < priorityVars.length) {
			if (nPast < nStrictlyPriorityVars)
				return priorityVars[nPast];
			if (settings.lc > 0) {
				Variable x = solver.lastConflict.priorityVariable();
				if (x != null && Kit.isPresent(x, priorityVars))
					return x;
			}
			Variable bestVar = null;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (int i = nStrictlyPriorityVars; i < priorityVars.length; i++) {
				if (priorityVars[i].assigned())
					continue;
				double score = scoreOptimizedOf(priorityVars[i]);
				if (score > bestScore) {
					bestVar = priorityVars[i];
					bestScore = score;
				}
			}
			return bestVar;
		}
		return null;
	}

	/** Returns the preferred variable among those that are not priority. Called only if no variable has been selected in priority. */
	protected abstract Variable bestUnpriorityVar();

	/** Returns the preferred variable (next variable to be assigned) of the problem. */
	public final Variable bestVar() {
		Variable x = bestPriorityVar();
		return x != null ? x : bestUnpriorityVar();
	}

	/**
	 * Returns true if the data structures of the heuristic must be reset (according to the current run)
	 * 
	 * @return true if the data structures of the heuristic must be reset
	 */
	protected boolean runReset() {
		int numRun = solver.restarter.numRun;
		return 0 < numRun && numRun % solver.head.control.restarts.varhResetPeriod == 0;
	}

	/*************************************************************************
	 * Special heuristic
	 *************************************************************************/

	public static final class Memory extends HeuristicVariables implements ObserverOnRuns {

		@Override
		public void beforeRun() {
			nOrdered = 0;
		}

		private int[] order;
		private int nOrdered;
		private int posLastConflict = -1;

		public Memory(Solver solver, boolean antiHeuristic) {
			super(solver, antiHeuristic);
			this.order = new int[solver.problem.variables.length];
			Kit.control(!solver.head.control.varh.discardAux);
		}

		@Override
		protected final Variable bestUnpriorityVar() {
			int pos = -1;
			for (int i = 0; i < nOrdered; i++)
				if (!solver.problem.variables[order[i]].assigned()) {
					pos = i;
					break;
				}
			if (pos != -1) {
				if (posLastConflict > pos) {
					Kit.control(posLastConflict < nOrdered);
					int vid = order[pos];
					order[pos] = order[posLastConflict];
					order[posLastConflict] = vid;
				}
				posLastConflict = pos;
			} else {
				bestScoredVariable.reset(false);
				solver.futVars.execute(x -> bestScoredVariable.update(x, scoreOptimizedOf(x)));
				pos = posLastConflict = nOrdered;
				order[nOrdered++] = bestScoredVariable.variable.num;
			}
			return solver.problem.variables[order[pos]];
		}

		@Override
		public double scoreOf(Variable x) {
			return x.ddeg() / x.dom.size(); // TODO x.wdeg() is currently no more possible car weighetd degrees are in another heuristic class
		}
	}

}