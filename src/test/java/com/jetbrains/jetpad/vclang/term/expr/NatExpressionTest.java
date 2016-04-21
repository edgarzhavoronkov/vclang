package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.expr.visitor.CheckTypeVisitor;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;
import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase.*;

public class NatExpressionTest {
  @Test
  public void testParseLiteral() {
    CheckTypeVisitor.Result res = typeCheckExpr("10", null);
    assertTrue(res.expression instanceof NatExpression);
    assertTrue(((NatExpression)res.expression).isLiteral());
    assertTrue(((NatExpression)res.expression).getSuccs().equals(BigInteger.TEN));
  }

  @Test
  public void testToAbstract() {
    Expression expr = Suc(Suc(Suc(Zero())));
    assertEquals("3", expr.toString());
  }
}
