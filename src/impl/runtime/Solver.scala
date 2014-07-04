package impl.runtime

import Unification._
import impl.logic.Symbol.{PredicateSymbol => PSym, VariableSymbol => VSym}
import impl.logic._
import util.collection.mutable

/**
 * A semi-naive solver.
 */
class Solver(program: Program) {

  /**
   * The tables for n-ary relations.
   */
  val relation1 = mutable.MultiMap1.empty[PSym, Value]
  val relation2 = mutable.MultiMap1.empty[PSym, (Value, Value)]
  val relation3 = mutable.MultiMap1.empty[PSym, (Value, Value, Value)]
  val relation4 = mutable.MultiMap1.empty[PSym, (Value, Value, Value, Value)]
  val relation5 = mutable.MultiMap1.empty[PSym, (Value, Value, Value, Value, Value)]

  /**
   * Maps for n-ary lattices.
   */
  // TODO: Store as tuples?
  val map1 = mutable.Map1.empty[PSym, Value]
  val map2 = mutable.Map1.empty[PSym, (Value, Value)]
  val map3 = mutable.Map1.empty[PSym, (Value, Value, Value)]
  val map4 = mutable.Map1.empty[PSym, (Value, Value, Value, Value)]
  val map5 = mutable.Map1.empty[PSym, (Value, Value, Value, Value, Value)]

  /**
   * A map of dependencies between predicate symbols and horn clauses.
   *
   * If a horn clause `h` contains a predicate `p` then the map contains the element `p -> h`.
   */
  val dependencies = mutable.MultiMap1.empty[PSym, HornClause]

  /**
   * A work list of pending horn clauses (and their associated environments).
   */
  val queue = scala.collection.mutable.Queue.empty[(HornClause, Map[VSym, Value])]

  /**
   * The fixpoint computation.
   */
  def solve(): Unit = {
    // Find dependencies between predicates and horn clauses.
    // A horn clause `h` depends on predicate `p` iff `p` occurs in the body of `h`.
    for (h <- program.clauses; p <- h.body) {
      dependencies.put(p.name, h)
    }

    // Satisfy all facts. Satisfying a fact adds violated horn clauses (and environments) to the work list.
    for (h <- program.facts) {
      satisfy(h.head, interpretationOf(h.head, program.interpretation), Map.empty[VSym, Value])
    }

    // Iteratively try to satisfy pending horn clauses.
    // Satisfying a horn clause may cause additional items to be added to the work list.
    while (queue.nonEmpty) {
      val (h, env) = queue.dequeue()
      satisfy(h, program.interpretation, env)
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // Evaluation                                                              //
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a list of models for the given horn clause `h` with interpretations `inv` under the given environment `env`.
   */
  def evaluate(h: HornClause, inv: Map[PSym, Interpretation], env: Map[VSym, Value]): List[Map[VSym, Value]] = {
    // Evaluate relational predicates before functional predicates.
    val relationals = h.body filter (p => isRelational(p, inv))
    val functionals = h.body -- relationals
    val predicates = relationals.toList ::: functionals.toList

    // Fold each predicate over the intial environment.
    val init = List(env)
    (init /: predicates) {
      case (envs, p) => evaluate(p, interpretationOf(p, inv), envs)
    }
  }

  /**
   * Returns a list of environments for the given predicate `p` with interpretation `i` under *each* of the given environments `envs`.
   */
  def evaluate(p: Predicate, i: Interpretation, envs: List[Map[VSym, Value]]): List[Map[VSym, Value]] = {
    envs flatMap (evaluate(p, i, _))
  }

  /**
   * Returns a list of environments for the given predicate `p` with interpretation `i` under the given environment `env0`.
   */
  def evaluate(p: Predicate, i: Interpretation, env0: Map[VSym, Value]): List[Map[VSym, Value]] = i match {
    case Interpretation.Relation.In1 =>
      val List(t1) = p.terms
      relation1.get(p.name).toList.flatMap {
        case v1 => unify(t1, v1, env0)
      }

    case Interpretation.Relation.In2 =>
      val List(t1, t2) = p.terms
      relation2.get(p.name).toList.flatMap {
        case (v1, v2) => unify(t1, t2, v1, v2, env0)
      }

    case Interpretation.Relation.In3 =>
      val List(t1, t2, t3) = p.terms
      relation3.get(p.name).toList.flatMap {
        case (v1, v2, v3) => unify(t1, t2, t3, v1, v2, v3, env0)
      }

    case Interpretation.Relation.In4 =>
      val List(t1, t2, t3, t4) = p.terms
      relation4.get(p.name).toList.flatMap {
        case (v1, v2, v3, v4) => unify(t1, t2, t3, t4, v1, v2, v3, v4, env0)
      }

    case Interpretation.Relation.In5 =>
      val List(t1, t2, t3, t4, t5) = p.terms
      relation5.get(p.name).toList.flatMap {
        case (v1, v2, v3, v4, v5) => unify(t1, t2, t3, t4, t5, v1, v2, v3, v4, v5, env0)
      }

    case Interpretation.Map.Leq1(lattice) =>
      throw new RuntimeException("Disallow LeqX in bodies? Not monotone....????")

    case _ => throw new Error.NonRelationalPredicate(p.name)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Satisfaction                                                            //
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Satisfies the given horn clause `h` by finding all valid models of the given environment `env`.
   *
   * Adds all facts which satisfies the given horn clause `h`.
   */
  def satisfy(h: HornClause, inv: Map[PSym, Interpretation], env: Map[VSym, Value]): Unit = {
    val models = evaluate(h, inv, env)
    for (model <- models) {
      satisfy(h.head, interpretationOf(h.head, inv), model)
    }
  }

  /**
   * Satisfies the given predicate `p` under the given interpretation `i` and environment `env`.
   */
  def satisfy(p: Predicate, i: Interpretation, env: Map[VSym, Value]): Unit = i match {
    case Interpretation.Relation.In1 =>
      val List(t1) = p.terms
      val v1 = t1.toValue(env)
      val newFact = relation1.put(p.name, v1)
      if (newFact)
        propagate(p, IndexedSeq(v1))

    case Interpretation.Relation.In2 =>
      val List(t1, t2) = p.terms
      val (v1, v2) = (t1.toValue(env), t2.toValue(env))
      val newFact = relation2.put(p.name, (v1, v2))
      if (newFact)
        propagate(p, IndexedSeq(v1, v2))

    case Interpretation.Relation.In3 =>
      val List(t1, t2, t3) = p.terms
      val (v1, v2, v3) = (t1.toValue(env), t2.toValue(env), t3.toValue(env))
      val newFact = relation3.put(p.name, (v1, v2, v3))
      if (newFact)
        propagate(p, IndexedSeq(v1, v2, v3))

    case Interpretation.Relation.In4 =>
      val List(t1, t2, t3, t4) = p.terms
      val (v1, v2, v3, v4) = (t1.toValue(env), t2.toValue(env), t3.toValue(env), t4.toValue(env))
      val newFact = relation4.put(p.name, (v1, v2, v3, v4))
      if (newFact)
        propagate(p, IndexedSeq(v1, v2, v3, v4))

    case Interpretation.Relation.In5 =>
      val List(t1, t2, t3, t4, t5) = p.terms
      val (v1, v2, v3, v4, v5) = (t1.toValue(env), t2.toValue(env), t3.toValue(env), t4.toValue(env), t5.toValue(env))
      val newFact = relation5.put(p.name, (v1, v2, v3, v4, v5))
      if (newFact)
        propagate(p, IndexedSeq(v1, v2, v3, v4, v5))

    case Interpretation.Map.Leq1(lattice) =>
      val List(t1) = p.terms
      val newValue = t1.toValue(env)
      val oldValue = map1.get(p.name).getOrElse(lattice.bot)
      val joinValue = join(newValue, oldValue, lattice.join)
      val newFact: Boolean = !leq(joinValue, oldValue, lattice.leq)
      if (newFact) {
        relation1.put(p.name, joinValue)
        propagate(p, IndexedSeq(joinValue))
      }


    case _ => throw new Error.NonRelationalPredicate(p.name)
  }


  /**
   * Enqueues all depedencies of the given predicate with the given environment.
   */
  def propagate(p: Predicate, values: IndexedSeq[Value]): Unit = {
    for (h <- dependencies.get(p.name)) {
      bind(h, p, values) match {
        case None => // nop
        case Some(m) => queue.enqueue((h, m))
      }
    }
  }

  /**
   * Optionally returns a new environment where all free variables, for the given predicate `p`,
   * have been mapped to the value in the given environment `env`.
   *
   * That is, if the horn clause is A(x, y, z) :- B(x, y), C(z), the predicate is B
   * and the environment is [0 -> a, 1 -> b] then the return environment is [x -> a, y -> b].
   *
   * Returns `None` if no satisfying assignment exists.
   */
  def bind(h: HornClause, p: Predicate, env: IndexedSeq[Value]): Option[Map[VSym, Value]] = {
    var m = Map.empty[VSym, Value]
    for (p2 <- h.body; if p.name == p2.name) {
      for ((t, i) <- p2.terms.zipWithIndex) {
        unify(t, env(i), m) match {
          case None => return None
          case Some(m2) => m = m2
        }
      }
    }
    Some(m)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Top-down satisfiability                                                 //
  /////////////////////////////////////////////////////////////////////////////
  // TODO: This part is in development --------------------

  // TODO: Naming
  /**
   * Returns `true` iff the given predicate `p` is satisfiable.
   *
   * All terms in `p` must be values under the given environment `env`.
   */
  def satisfiable(p: Predicate, env: Map[VSym, Value]): Boolean = {
    // Find all horn clauses which define the predicate.
    val clauses: Set[HornClause] = ???

    // Compute the arguments of the predicate.
    val values = (p.terms map (_.toValue(env))).toIndexedSeq

    // The predicate is satisfiable iff atleast one of its horn clauses is satisfiable.
    //clauses exists (h => satisfiable(h, values))

    ???
  }

  //  bind(h, h.head, vs) match {
  //    case None => false // The predicate is unsatisfiable.
  //    case Some(env) =>
  //      // The predicate is satisfiable iff
  //      // (1) it has no body (i.e. it is a fact), or
  //      // (2) it body is satisfiable
  //      h.isFact || (h.body forall (p => satisfiable(p, env)))


  /**
   * TODO: DOC
   */
  def topdown(h: HornClause, env: Map[VSym, Value]): List[Map[VSym, Value]] = {
    ???
  }


  /**
   * Returns `true` iff `v1` is less or equal to `v2`.
   */
  // TODO: Generalize
  def leq(v1: Value, v2: Value, leq: Set[HornClause]): Boolean = {
    val env0 = Map.empty[VSym, Value]

    val envs = leq.toList.flatMap {
      case h =>
        val List(t1, t2) = h.head.terms
        val env = unify(t1, t2, v1, v2, env0)
        // TODO: Proceed into body...
        ???
    }

    envs.nonEmpty
  }

  /**
   * Returns the join of `v1` and `v2`.
   */
  // TODO: Generalize
  def join(v1: Value, v2: Value, join: Set[HornClause]): Value = {

    ???
  }

  /////////////////////////////////////////////////////////////////////////////
  // Utilities                                                               //
  /////////////////////////////////////////////////////////////////////////////

  /**
   * Returns the interpretation of the given predicate `p`.
   */
  def interpretationOf(p: Predicate, inv: Map[PSym, Interpretation]): Interpretation = inv.get(p.name) match {
    case None => throw new Error.InterpretationNotFound(p.name)
    case Some(i) => i
  }

  /**
   * Returns `true` iff the given predicate `p` is relational under the given interpretations `inv`.
   */
  private def isRelational(p: Predicate, inv: Map[PSym, Interpretation]): Boolean = interpretationOf(p, inv).isRelational
}
