package com.jetbrains.jetpad.vclang.typechecking;

import com.jetbrains.jetpad.vclang.core.context.binding.LevelVariable;
import com.jetbrains.jetpad.vclang.core.context.param.SingleDependentLink;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.core.sort.Level;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.typechecking.visitor.CheckTypeVisitor;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.jetpad.vclang.ExpressionFactory.*;
import static com.jetbrains.jetpad.vclang.core.expr.ExpressionFactory.Interval;
import static org.junit.Assert.assertEquals;

public class PathsTest extends TypeCheckingTestCase {
  @Test
  public void idpTest() {
    typeCheckDef("\\function idp {A : \\Type0} (a : A) : a = a => path (\\lam _ => a)");
  }

  @Test
  public void idpUntyped() {
    CheckTypeVisitor.Result idp = typeCheckExpr("\\lam {A : \\Type0} (a : A) => path (\\lam _ => a)", null);
    SingleDependentLink A = singleParam(false, Collections.singletonList("A"), Universe(new Level(0), new Level(LevelVariable.HVAR)));
    SingleDependentLink a = singleParam("a", Ref(A));
    SingleDependentLink C = singleParam(null, Interval());
    List<Expression> pathArgs = new ArrayList<>();
    pathArgs.add(Lam(C, Ref(A)));
    pathArgs.add(Ref(a));
    pathArgs.add(Ref(a));
    Expression pathCall = ConCall(Prelude.PATH_CON, Sort.TypeOfLevel(0), pathArgs, Lam(C, Ref(a)));
    assertEquals(Lam(A, Lam(a, pathCall)).normalize(NormalizeVisitor.Mode.NF), idp.expression);
    assertEquals(Pi(A, Pi(a, FunCall(Prelude.PATH_INFIX, Sort.TypeOfLevel(0), Ref(A), Ref(a), Ref(a)))).normalize(NormalizeVisitor.Mode.NF), idp.type.normalize(NormalizeVisitor.Mode.NF));
  }

  @Test
  public void squeezeTest() {
    typeCheckClass(
        "\\function squeeze1 (i j : I) => coe (\\lam x => left = x) (path (\\lam _ => left)) j @ i\n" +
        "\\function squeeze (i j : I) => coe (\\lam i => Path (\\lam j => left = squeeze1 i j) (path (\\lam _ => left)) (path (\\lam j => squeeze1 i j))) (path (\\lam _ => path (\\lam _ => left))) right @ i @ j"
    );
  }

  @Test
  public void concatTest() {
    typeCheckClass(
        "\\function transport {A : \\Type} (B : A -> \\Type) {a a' : A} (p : a = a') (b : B a) => coe (\\lam i => B (p @ i)) b right\n" +
        "\\function concat {A : I -> \\Type} {a : A left} {a' a'' : A right} (p : Path A a a') (q : a' = a'') => transport (Path A a) q p\n" +
        "\\function *> {A : \\Type} {a a' a'' : A} (p : a = a') (q : a' = a'') => concat p q");
  }

  @Test
  public void inv0Test() {
    typeCheckClass(
        "\\function idp {A : \\Type} {a : A} => path (\\lam _ => a)\n" +
        "\\function transport {A : \\Type} (B : A -> \\Type) {a a' : A} (p : a = a') (b : B a) => coe (\\lam i => B (p @ i)) b right\n" +
        "\\function inv {A : \\Type} {a a' : A} (p : a = a') => transport (\\lam a'' => a'' = a) p idp\n" +
        "\\function squeeze1 (i j : I) : I => coe (\\lam x => left = x) (path (\\lam _ => left)) j @ i\n" +
        "\\function squeeze (i j : I) => coe (\\lam i => Path (\\lam j => left = squeeze1 i j) (path (\\lam _ => left)) (path (\\lam j => squeeze1 i j))) (path (\\lam _ => path (\\lam _ => left))) right @ i @ j\n" +
        "\\function psqueeze {A : \\Type} {a a' : A} (p : a = a') (i : I) : a = p @ i => path (\\lam j => p @ squeeze i j)\n" +
        "\\function Jl {A : \\Type} {a : A} (B : \\Pi (a' : A) -> a = a' -> \\Type) (b : B a idp) {a' : A} (p : a = a') : B a' p\n" +
        "  => coe (\\lam i => B (p @ i) (psqueeze p i)) b right\n" +
        "\\function inv-inv {A : \\Type} {a a' : A} (p : a = a') : inv (inv p) = p => Jl (\\lam _ p => inv (inv p) = p) idp p\n" +
        "\\function path-sym {A : \\Type} (a a' : A) : (a = a') = (a' = a) => path (iso inv inv inv-inv inv-inv)");
  }

  @Test
  public void invTest() {
    typeCheckClass(
        "\\function idp {A : \\Type} {a : A} => path (\\lam _ => a)\n" +
        "\\function transport {A : \\Type} (B : A -> \\Type) {a a' : A} (p : a = a') (b : B a) => coe (\\lam i => B (p @ i)) b right\n" +
        "\\function inv {A : \\Type} {a a' : A} (p : a = a') => transport (\\lam a'' => a'' = a) p idp\n" +
        "\\function squeeze1 (i j : I) : I => coe (\\lam x => left = x) (path (\\lam _ => left)) j @ i\n" +
        "\\function squeeze (i j : I) => coe (\\lam i => Path (\\lam j => left = squeeze1 i j) (path (\\lam _ => left)) (path (\\lam j => squeeze1 i j))) (path (\\lam _ => path (\\lam _ => left))) right @ i @ j\n" +
        "\\function psqueeze {A : \\Type} {a a' : A} (p : a = a') (i : I) : a = p @ i => path (\\lam j => p @ squeeze i j)\n" +
        "\\function Jl {A : \\Type} {a : A} (B : \\Pi (a' : A) -> a = a' -> \\Type) (b : B a idp) {a' : A} (p : a = a') : B a' p\n" +
        "  => coe (\\lam i => B (p @ i) (psqueeze p i)) b right\n" +
        "\\function inv-inv {A : \\Type} {a a' : A} (p : a = a') : inv (inv p) = p => Jl (\\lam _ p => inv (inv p) = p) idp p\n" +
        "\\function path-sym {A : \\Type} (a a' : A) : (a = a') = (a' = a) => path (iso inv inv inv-inv inv-inv)");
  }

  @Test
  public void idpTypeTest() {
    typeCheckClass(
      "\\function idp {A : \\Type} {a : A} : a = a => path (\\lam _ => a)\n" +
      "\\function f : 3 = 3 => idp");
  }
}
