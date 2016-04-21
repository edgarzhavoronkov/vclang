package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.Prelude;
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

}
