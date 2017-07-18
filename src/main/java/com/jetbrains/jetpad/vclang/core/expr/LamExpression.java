package com.jetbrains.jetpad.vclang.core.expr;

import com.jetbrains.jetpad.vclang.core.context.param.SingleDependentLink;
import com.jetbrains.jetpad.vclang.core.expr.visitor.ExpressionVisitor;
import com.jetbrains.jetpad.vclang.core.sort.Sort;

public class LamExpression extends Expression {
  private final Sort myResultSort;
  private final SingleDependentLink myLink;
  private Expression myBody;

  public LamExpression(Sort resultSort, SingleDependentLink link, Expression body) {
    myResultSort = resultSort;
    myLink = link;
    myBody = body;
  }

  public Sort getResultSort() {
    return myResultSort;
  }

  public SingleDependentLink getParameters() {
    return myLink;
  }

  public Expression getBody() {
    return myBody;
  }

  public void setBody(Expression body) {
    myBody = body;
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitLam(this, params);
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
