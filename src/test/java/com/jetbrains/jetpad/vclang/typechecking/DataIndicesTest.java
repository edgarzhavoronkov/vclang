package com.jetbrains.jetpad.vclang.typechecking;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.SingleDependentLink;
import com.jetbrains.jetpad.vclang.core.definition.DataDefinition;
import com.jetbrains.jetpad.vclang.core.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.core.elimtree.LeafElimTree;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jetpad.vclang.ExpressionFactory.*;
import static com.jetbrains.jetpad.vclang.core.expr.ExpressionFactory.*;
import static org.junit.Assert.assertEquals;

public class DataIndicesTest extends TypeCheckingTestCase {
  @Test
  public void vectorTest() {
    typeCheckClass(
      "\\data Vector (n : Nat) (A : \\Set0) => \\elim n\n" +
      "  | zero  => vnil\n" +
      "  | suc n => \\infixr 5 :^ A (Vector n A)\n" +
      "\n" +
      "\\function \\infixl 6\n" +
      "+ (x y : Nat) : Nat => \\elim x\n" +
      "  | zero => y\n" +
      "  | suc x' => suc (x' + y)\n" +
      "\n" +
      "\\function\n" +
      "+^ {n m : Nat} {A : \\Set0} (xs : Vector n A) (ys : Vector m A) : Vector (n + m) A => \\elim n, xs\n" +
      "  | zero, vnil => ys\n" +
      "  | suc n', `:^ x xs' => x :^ xs' +^ ys\n" +
      "\n" +
      "\\function\n" +
      "vnil-vconcat {n : Nat} {A : \\Set0} (xs : Vector n A) : vnil +^ xs = xs => path (\\lam _ => xs)");
  }

  @Test
  public void vectorTest2() {
    typeCheckClass(
      "\\data Vector (n : Nat) (A : \\Set0) => \\elim n\n" +
      "  | zero  => vnil\n" +
      "  | suc n => \\infixr 5 :^ A (Vector n A)\n" +
      "\\function id {n : Nat} (A : \\Set0) (v : Vector n A) => v\n" +
      "\\function test => id Nat vnil");
  }

  @Test
  public void constructorTypeTest() {
    TypeCheckClassResult result = typeCheckClass(
        "\\data NatVec Nat \\with\n" +
        "  | zero  => nil\n" +
        "  | suc n => cons Nat (NatVec n)");
    DataDefinition data = (DataDefinition) result.getDefinition("NatVec");
    assertEquals(DataCall(data, Sort.SET0, Zero()), data.getConstructor("nil").getTypeWithParams(new ArrayList<>(), Sort.SET0));
    SingleDependentLink param = singleParams(false, vars("n"), Nat());
    List<DependentLink> consParams = new ArrayList<>();
    Expression consType = data.getConstructor("cons").getTypeWithParams(consParams, Sort.SET0);
    assertEquals(Pi(param, Pi(Nat(), Pi(DataCall(data, Sort.SET0, Ref(param)), DataCall(data, Sort.SET0, Suc(Ref(param)))))), fromPiParameters(consType, consParams));
  }

  @Test
  public void toAbstractTest() {
    TypeCheckClassResult result = typeCheckClass(
        "\\data Fin Nat \\with\n" +
        "  | suc n => fzero\n" +
        "  | suc n => fsuc (Fin n)\n" +
        "\\function f (n : Nat) (x : Fin n) => fsuc (fsuc x)");
    assertEquals("(Fin (suc (suc n))).fsuc ((Fin (suc n)).fsuc x)", ((LeafElimTree) ((FunctionDefinition) result.getDefinition("f")).getBody()).getExpression().normalize(NormalizeVisitor.Mode.NF).toString());
  }
}
