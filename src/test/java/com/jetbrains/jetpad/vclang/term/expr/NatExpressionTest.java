package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.expr.visitor.CheckTypeVisitor;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;
import org.junit.Test;

import java.math.BigInteger;

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
    assertTrue(e.getSuccs().equals(BigInteger.TEN));
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
}
