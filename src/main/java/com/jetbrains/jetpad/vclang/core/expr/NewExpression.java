package com.jetbrains.jetpad.vclang.core.expr;

import com.jetbrains.jetpad.vclang.core.expr.visitor.ExpressionVisitor;

public class NewExpression extends Expression {
  private ClassCallExpression myExpression;

  public NewExpression(ClassCallExpression expression) {
    myExpression = expression;
  }

  public ClassCallExpression getExpression() {
    return myExpression;
  }

  public void setExpression(ClassCallExpression expression) {
    myExpression = expression;
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitNew(this, params);
  }

  @Override
  public boolean isWHNF() {
    return true;
  }

  @Override
  public Expression getStuckExpression() {
    return null;
  }
}
