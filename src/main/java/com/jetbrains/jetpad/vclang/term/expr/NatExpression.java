package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.expr.visitor.ExpressionVisitor;

import java.math.BigInteger;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;

public class NatExpression extends Expression {
  private final BigInteger mySuccs;
  private final Expression myExpression;

  public NatExpression(BigInteger mySuccs, Expression myExpression) {
    this.mySuccs = mySuccs;
    this.myExpression = myExpression;
  }

  public BigInteger getSuccs() {
    return mySuccs;
  }

  public Expression getExpression() {
    return myExpression;
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitNat(this, params);
  }

  @Override
  public Expression getType() {
    return Nat();
  }

  @Override
  public NatExpression toNat() {
    return this;
  }

  public boolean isLiteral() {
    return myExpression.equals(Zero());
  }
}
