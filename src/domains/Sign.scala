package domains

import impl.logic._
import impl.runtime.{Hint, Representation}
import syntax.Symbols._

object Sign {

  val Elements = Type.Variant(IndexedSeq(
    Type.Constructor0("Sign.Top"),
    Type.Constructor0("Sign.Neg"), Type.Constructor0("Sign.Zer"), Type.Constructor0("Sign.Pos"),
    Type.Constructor0("Sign.Bot")
  ))

  val Top = Term.Constructor0("Sign.Top")
  val Neg = Term.Constructor0("Sign.Neg")
  val Zero = Term.Constructor0("Sign.Zer")
  val Pos = Term.Constructor0("Sign.Pos")
  val Bot = Term.Constructor0("Sign.Bot")

  val LeqSymbol = Symbol.PredicateSymbol("Sign.Leq")
  val JoinSymbol = Symbol.PredicateSymbol("Sign.Join")
  val SumSymbol = Symbol.PredicateSymbol("Sign.Sum")

  val Leq = List(
    HornClause(Predicate(LeqSymbol, List(Bot, Term.Variable("_")))),
    HornClause(Predicate(LeqSymbol, List(Neg, Neg))),
    HornClause(Predicate(LeqSymbol, List(Zero, Zero))),
    HornClause(Predicate(LeqSymbol, List(Pos, Pos))),
    HornClause(Predicate(LeqSymbol, List(Term.Variable("_"), Top)))
  )

  val Join = List(
    HornClause(Predicate(JoinSymbol, List(Bot, Term.Variable("x"), Term.Variable("x")))),
    HornClause(Predicate(JoinSymbol, List(Term.Variable("x"), Bot, Term.Variable("x")))),

    HornClause(Predicate(JoinSymbol, List(Neg, Neg, Neg))),
    HornClause(Predicate(JoinSymbol, List(Neg, Zero, Top))),
    HornClause(Predicate(JoinSymbol, List(Neg, Pos, Top))),

    HornClause(Predicate(JoinSymbol, List(Zero, Neg, Top))),
    HornClause(Predicate(JoinSymbol, List(Zero, Zero, Zero))),
    HornClause(Predicate(JoinSymbol, List(Zero, Pos, Top))),

    HornClause(Predicate(JoinSymbol, List(Pos, Neg, Top))),
    HornClause(Predicate(JoinSymbol, List(Pos, Zero, Top))),
    HornClause(Predicate(JoinSymbol, List(Pos, Pos, Pos))),

    HornClause(Predicate(JoinSymbol, List(Term.Variable("_"), Top, Top))),
    HornClause(Predicate(JoinSymbol, List(Top, Term.Variable("_"), Top)))
  )

  val Sum = List(
    HornClause(Predicate(SumSymbol, List(Bot, Term.Variable("_"), Bot))),
    HornClause(Predicate(SumSymbol, List(Term.Variable("_"), Bot, Bot))),

    HornClause(Predicate(SumSymbol, List(Neg, Neg, Neg))),
    HornClause(Predicate(SumSymbol, List(Neg, Zero, Neg))),
    HornClause(Predicate(SumSymbol, List(Neg, Pos, Top))),

    HornClause(Predicate(SumSymbol, List(Zero, Neg, Neg))),
    HornClause(Predicate(SumSymbol, List(Zero, Zero, Zero))),
    HornClause(Predicate(SumSymbol, List(Zero, Pos, Pos))),

    HornClause(Predicate(SumSymbol, List(Pos, Neg, Top))),
    HornClause(Predicate(SumSymbol, List(Pos, Zero, Pos))),
    HornClause(Predicate(SumSymbol, List(Pos, Pos, Pos))),

    HornClause(Predicate(SumSymbol, List(Top, Term.Variable("_"), Top))),
    HornClause(Predicate(SumSymbol, List(Term.Variable("_"), Top, Top)))
  )

  val Interpretations = Map(
    LeqSymbol -> Interpretation.Leq,
    JoinSymbol -> Interpretation.Join
  )

  val Hints = Map(
    SumSymbol -> Hint(Representation.Code)
  )

  val lattice = Lattice(Elements, Bot.toValue, LeqSymbol, JoinSymbol, Leq ::: Join ::: Sum, Interpretations)

}
