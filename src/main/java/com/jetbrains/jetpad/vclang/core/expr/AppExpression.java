package com.jetbrains.jetpad.vclang.core.expr;

import com.jetbrains.jetpad.vclang.core.expr.visitor.ExpressionVisitor;

public class AppExpression extends Expression {
  private Expression myFunction;
  private Expression myArgument;

  public AppExpression(Expression function, Expression argument) {
    myFunction = function;
    myArgument = argument;
  }

  public Expression getFunction() {
    return myFunction;
  }

  public Expression getArgument() {
    return myArgument;
  }

  public void setFunction(Expression function) {
    myFunction = function;
  }

  public void setArgument(Expression argument) {
    myArgument = argument;
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitApp(this, params);
  }

  @Override
  public boolean isWHNF() {
    return myFunction.isWHNF() && !myFunction.isInstance(LamExpression.class);
  }

  @Override
  public Expression getStuckExpression() {
    return myFunction.getStuckExpression();
  }
}
