package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.term.expr.visitor.ExpressionVisitor;

import java.math.BigInteger;
import java.util.List;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;

public class NatExpression extends Expression {
  private final BigInteger mySuccs;
  private final Expression myExpression;

  public NatExpression(BigInteger mySuccs, Expression myExpression) {
    assert mySuccs.compareTo(BigInteger.ONE) >= 0;
    this.mySuccs = mySuccs;
    this.myExpression = myExpression;
  }

  public Expression unsucc() {
    return unsucc(BigInteger.ONE);
  }

  public Expression unsucc(BigInteger unsuccs) {
    assert unsuccs.compareTo(mySuccs) <= 0;
    if (mySuccs.equals(unsuccs)) {
      return myExpression;
    } else {
      return Suc(mySuccs.subtract(unsuccs), myExpression);
    }
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

  @Override
  public AppExpression toApp() {
    return mySuccs.compareTo(BigInteger.ONE) >= 0 ? Suc(unsucc()).toApp() : null;
  }

  @Override
  public List<? extends Expression> getArguments() {
    AppExpression app = toApp();
    return app == null ? super.getArguments() : app.getArguments();
  }

  @Override
  public Expression getFunction() {
    AppExpression app = toApp();
    return app == null ? super.getFunction() : app.getFunction();
  }

  public boolean isLiteral() {
    return myExpression.equals(Zero());
  }
}
