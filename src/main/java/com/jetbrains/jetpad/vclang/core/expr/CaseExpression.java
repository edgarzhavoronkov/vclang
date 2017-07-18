package com.jetbrains.jetpad.vclang.core.expr;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.elimtree.ElimTree;
import com.jetbrains.jetpad.vclang.core.expr.visitor.ExpressionVisitor;

import java.util.List;

public class CaseExpression extends Expression {
  private final DependentLink myParameters;
  private Expression myResultType;
  private ElimTree myElimTree;
  private final List<Expression> myArguments;

  public CaseExpression(DependentLink parameters, Expression resultType, ElimTree elimTree, List<Expression> arguments) {
    myParameters = parameters;
    myElimTree = elimTree;
    myResultType = resultType;
    myArguments = arguments;
  }

  public DependentLink getParameters() {
    return myParameters;
  }

  public Expression getResultType() {
    return myResultType;
  }

  public void setResultType(Expression type) {
    myResultType = type;
  }

  public ElimTree getElimTree() {
    return myElimTree;
  }

  public void setElimTree(ElimTree elimTree) {
    myElimTree = elimTree;
  }

  public List<? extends Expression> getArguments() {
    return myArguments;
  }

  public void setArgument(int index, Expression argument) {
    myArguments.set(index, argument);
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitCase(this, params);
  }

  @Override
  public boolean isWHNF() {
    return myElimTree.isWHNF(myArguments);
  }

  @Override
  public Expression getStuckExpression() {
    return myElimTree.getStuckExpression(myArguments, this);
  }
}
