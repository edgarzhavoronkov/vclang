package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.naming.NamespaceMember;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.term.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.term.expr.visitor.CheckTypeVisitor;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Collections;

import static org.junit.Assert.*;
import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase.*;

public class NatExpressionTest {
  @Test
  public void testParseLiteral() {
    CheckTypeVisitor.Result res = typeCheckExpr("10", null);
    NatExpression e = res.expression.toNat();
    assertNotNull(e);
    assertTrue(e.isLiteral());
    assertEquals(BigInteger.TEN, e.getSuccs());
  }

  @Test
  public void testToAbstract() {
    Expression expr = Suc(Suc(Suc(Zero())));
    assertEquals("3", expr.toString());
  }

  @Test
  public void testNormalizeSucSuc() {
    Expression e = Suc(Suc(Zero()));
    NatExpression e2 = e.normalize(NormalizeVisitor.Mode.NF).toNat();
    assertNotNull(e2);
    assertTrue(e2.isLiteral());
    assertEquals(BigInteger.valueOf(2), e2.getSuccs());
  }

  @Test
  public void testOldNewEquals() {
    Expression e1 = Suc(Zero());
    Expression e2 = Suc(1, Zero());
    assertEquals(e1, e2);
  }

  @Test
  public void testPositiveNatExprIsApp() {
    Expression e = Suc(3, Zero());
    Expression app = e.toApp();
    assertNotNull(app);
    assertEquals(app.getFunction(), ConCall(Prelude.SUC));
    assertEquals(app.getArguments(), Collections.singletonList(Suc(2, Zero())));
  }

  @Test
  public void testBigNat() {
    Expression e = typeCheckExpr("1000000000", Nat()).expression;
    NatExpression nat = e.toNat();
    assertNotNull(nat);
    assertTrue(nat.isLiteral());
    assertEquals(BigInteger.valueOf(1000000000), nat.getSuccs());
  }

  @Test
  public void testNormalizeSquash() {
    Expression e = Suc(Suc(10, Suc(Suc(Suc(5, Suc(Zero()))))));
    NatExpression nat = e.normalize(NormalizeVisitor.Mode.NF).toNat();
    assertNotNull(nat);
    assertTrue(nat.isLiteral());
    assertEquals(BigInteger.valueOf(19), nat.getSuccs());
  }

  @Test
  public void testNonLiteral() {
    Expression e = typeCheckExpr("\\lam n => suc (suc (suc n))", null).expression;
    NatExpression nat = e.normalize(NormalizeVisitor.Mode.NF).toLam().getBody().toNat();
    assertNotNull(nat);
    assertFalse(nat.isLiteral());
    assertEquals(BigInteger.valueOf(3), nat.getSuccs());
  }

  @Test
  public void testAddSmall() {
    Expression e = typeCheckExpr("3 + 5", Nat()).expression;
    NatExpression nat = e.normalize(NormalizeVisitor.Mode.NF).toNat();
    assertNotNull(nat);
    assertTrue(nat.isLiteral());
    assertEquals(BigInteger.valueOf(8), nat.getSuccs());
  }

  @Test
  public void testAddLarge() {
    Expression e = typeCheckExpr("100000000 + 1", Nat()).expression;
    NatExpression nat = e.normalize(NormalizeVisitor.Mode.NF).toNat();
    assertNotNull(nat);
    assertTrue(nat.isLiteral());
    assertEquals(BigInteger.valueOf(100000001), nat.getSuccs());
  }

  @Test
  public void testMulSmall() {
    Expression e = typeCheckExpr("3 * 5", Nat()).expression;
    NatExpression nat = e.normalize(NormalizeVisitor.Mode.NF).toNat();
    assertNotNull(nat);
    assertTrue(nat.isLiteral());
    assertEquals(BigInteger.valueOf(15), nat.getSuccs());
  }

  @Test
  public void testMulLarge() {
    Expression e = typeCheckExpr("1000000000 * 1000000000", Nat()).expression;
    NatExpression nat = e.normalize(NormalizeVisitor.Mode.NF).toNat();
    assertNotNull(nat);
    assertTrue(nat.isLiteral());
    assertEquals(new BigInteger("1000000000000000000"), nat.getSuccs());
  }

  @Test
  public void testFactorialSmall() {
    typeCheckClass("" +
            "\\function\n" +
            "idp {A : \\Type0} {a : A} => path (\\lam _ => a)\n" +
            "\\function\n" +
            "fac (x : Nat) : Nat\n" +
            "    <= \\elim x | zero => suc zero | suc x' => suc x' * fac x'\n" +
            "\\function\n" +
            "test-fac : fac 4 = 24 => idp\n");
  }

  @Test
  public void testFactorialLarge() {
    typeCheckClass("" +
            "\\function\n" +
            "idp {A : \\Type0} {a : A} => path (\\lam _ => a)\n" +
            "\\function\n" +
            "fac (x : Nat) : Nat\n" +
            "    <= \\elim x | zero => suc zero | suc x' => suc x' * fac x'\n" +
            "\\function\n" +
            "test-fac : fac 12 = 479001600 => idp\n");
  }

  @Test
  public void testFactorialVeryLarge() {
    NamespaceMember member = typeCheckClass("" +
            "\\function\n" +
            "idp {A : \\Type0} {a : A} => path (\\lam _ => a)\n" +
            "\\static \\function\n" +
            "fac (x : Nat) : Nat\n" +
            "    <= \\elim x | zero => suc zero | suc x' => suc x' * fac x'\n");
    FunctionDefinition fac = (FunctionDefinition) member.namespace.getMember("fac").definition;
    Expression big = Apps(FunCall(fac), Suc(100, Zero()));
    NatExpression norm = big.normalize(NormalizeVisitor.Mode.NF).toNat();
    assertTrue(norm.isLiteral());
  }

  @Test
  public void testFin() {
    typeCheckClass("" +
            "\\data Fin (n : Nat)\n" +
            "    | Fin (suc n) => fzero\n" +
            "    | Fin (suc n) => fsuc (Fin n)\n" +
            "\\function\n" +
            "fin11 : Fin 1 => fzero\n"
    );
  }
}
