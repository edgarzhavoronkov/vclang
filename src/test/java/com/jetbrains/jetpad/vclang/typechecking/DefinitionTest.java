package com.jetbrains.jetpad.vclang.typechecking;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.definition.Definition;
import com.jetbrains.jetpad.vclang.core.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jetpad.vclang.ExpressionFactory.Pi;
import static com.jetbrains.jetpad.vclang.ExpressionFactory.fromPiParameters;
import static com.jetbrains.jetpad.vclang.core.expr.ExpressionFactory.Nat;
import static org.junit.Assert.*;

public class DefinitionTest extends TypeCheckingTestCase {
  @Test
  public void function() {
    FunctionDefinition typedDef = (FunctionDefinition) typeCheckDef("\\function f : Nat => 0");
    assertNotNull(typedDef);
    assertTrue(typedDef.status() == Definition.TypeCheckingStatus.NO_ERRORS);
  }

  @Test
  public void functionUntyped() {
    FunctionDefinition typedDef = (FunctionDefinition) typeCheckDef("\\function f => 0");
    assertNotNull(typedDef);
    assertTrue(typedDef.status() == Definition.TypeCheckingStatus.NO_ERRORS);
    assertEquals(Nat(), typedDef.getTypeWithParams(new ArrayList<>(), Sort.SET0));
  }

  @Test
  public void functionWithArgs() {
    FunctionDefinition typedDef = (FunctionDefinition) typeCheckDef("\\function f (x : Nat) (y : Nat -> Nat) => y");
    assertNotNull(typedDef);
    assertTrue(typedDef.status() == Definition.TypeCheckingStatus.NO_ERRORS);
    List<DependentLink> params = new ArrayList<>();
    Expression type = typedDef.getTypeWithParams(params, Sort.SET0);
    assertEquals(Pi(Nat(), Pi(Pi(Nat(), Nat()), Pi(Nat(), Nat()))), fromPiParameters(type, params));
  }

  @Test
  public void errorInParameters() {
    typeCheckClass(
        "\\data E (n : Nat) | e\n" +
        "\\data D (n : Nat -> Nat) (E n) | d\n" +
        "\\function test => D", 2);
  }

  @Test
  public void errorInParametersCon() {
    typeCheckClass(
        "\\data E (n : Nat) | e\n" +
        "\\data D (n : Nat -> Nat) (E n) | d\n" +
        "\\function test => d", 2);
  }

  @Test
  public void patternVector() {
    typeCheckDef("\\data Vec (A : \\Type0) (n : Nat) => \\elim n | zero => Nil | suc m => Cons A (Vec A m)");
  }

  @Test
  public void patternDepParams() {
    typeCheckClass(
        "\\data D (n : Nat) (n = n) => \\elim n | zero => d\n" +
        "\\data C {n : Nat} {p : n = n} (D n p) => \\elim n | zero => c (p = p)");
  }

  @Test
  public void patternDepParams2() {
    typeCheckClass(
        "\\data D (n : Nat) (n = n) => \\elim n | zero => d\n" +
        "\\data C {n : Nat} {p : n = n} (D n p) | c (p = p)");
  }

  @Test
  public void patternNested() {
    typeCheckDef("\\data C Nat \\with | suc (suc n) => c2 (n = n)");
  }

  @Test
  public void patternDataLE() {
    typeCheckDef("\\data LE Nat Nat \\with | zero, m => LE-zero | suc n, suc m => LE-suc (LE n m)");
  }

  @Test
  public void patternImplicitError() {
    typeCheckDef("\\data D Nat \\with | {A} => d", 1);
  }

  @Test
  public void patternConstructorCall() {
    typeCheckClass(
        "\\data D {Nat} \\with | {zero} => d\n" +
        "\\function test => d");
  }

  @Test
  public void patternAbstract() {
    typeCheckClass(
        "\\data Wheel | wheel\n" +
        "\\data VehicleType | bikeType | carType\n" +
        "\\data Vehicle VehicleType \\with\n" +
        "  | carType => car Wheel Wheel Wheel Wheel" +
        "  | bikeType => bike Wheel Wheel");
  }

  @Test
  public void patternLift() {
    typeCheckClass(
        "\\data D Nat \\with | zero => d\n" +
        "\\data C (m n : Nat) (d : D m) => \\elim m, n | zero, zero => c");
  }

  @Test
  public void patternLift2() {
    typeCheckClass(
        "\\data D Nat \\with | zero => d\n" +
        "\\data C (m n : Nat) (D m) => \\elim n | zero => c");
  }

  @Test
  public void patternMultipleSubst() {
    typeCheckClass(
        "\\data D (n m : Nat) | d (n = n) (m = m)\n" +
        "\\data C | c (n m : Nat) (D n m)\n" +
        "\\data E C \\with | c zero (suc zero) (d _ _) => e\n" +
        "\\function test => (E (c 0 1 (d (path (\\lam _ => 0)) (path (\\lam _ => 1))))).e");
  }

  @Test
  public void patternConstructorDefCall() {
    typeCheckClass(
        "\\data D (n m : Nat) => \\elim n, m | suc n, suc m => d (n = n) (m = m)\n" +
        "\\function test => d (path (\\lam _ => 1)) (path (\\lam _ => 0))");
  }

  @Test
  public void patternConstructorDefCallError() {
    typeCheckClass(
        "\\data D Nat \\with | zero => d\n" +
        "\\function test (n : Nat) : D n => d", 1);
  }

  @Test
  public void patternSubstTest() {
    typeCheckClass(
        "\\data E Nat \\with | zero => e\n" +
        "\\data D (n : Nat) (E n) => \\elim n | zero => d\n" +
        "\\function test => d");
  }

  @Test
  public void patternExpandArgsTest() {
    typeCheckClass(
        "\\data D (n : Nat) | d (n = n)\n" +
        "\\data C (D 1) \\with | d p => c\n" +
        "\\function test : C (d (path (\\lam _ => 1))) => c");
  }

  @Test
  public void patternNormalizeTest() {
    typeCheckClass(
        "\\data E (x : 0 = 0) | e\n" +
        "\\data C (n m : Nat) => \\elim n, m | suc n, suc (suc n) => c (n = n)\n" +
        "\\data D ((\\lam (x : \\Type0) => x) (C 1 2)) \\with | c p => x (E p)\n" +
        "\\function test => x (E (path (\\lam _ => 0))).e");
  }

  @Test
  public void patternNormalizeTest1() {
    typeCheckClass(
        "\\data E (x : 0 = 0) | e\n" +
        "\\data C Nat Nat \\with | suc n, suc (suc n) => c (n = n)\n" +
        "\\data D ((\\lam (x : \\Type0) => x) (C 1 1)) \\with | c p => x (E p)", 1);
  }

  @Test
  public void patternTypeCheck() {
    typeCheckClass(
        "\\function f (x : Nat -> Nat) => x 0\n" +
        "\\data Test (A : \\Set0) \\with\n" +
        "  | suc n => foo (f n)", 1);
  }
}
