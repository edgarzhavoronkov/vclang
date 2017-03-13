package com.jetbrains.jetpad.vclang.core.expr;

import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.core.expr.visitor.ExpressionVisitor;
import com.jetbrains.jetpad.vclang.core.pattern.elimtree.BranchElimTreeNode;
import com.jetbrains.jetpad.vclang.core.sort.LevelArguments;

import java.util.List;

public class FunCallExpression extends DefCallExpression {
  private final LevelArguments myLevelArguments;
  private final List<Expression> myArguments;

  public FunCallExpression(FunctionDefinition definition, LevelArguments levelArguments, List<Expression> arguments) {
    super(definition);
    assert definition.status().headerIsOK();
    myLevelArguments = levelArguments;
    myArguments = arguments;
  }

  @Override
  public LevelArguments getLevelArguments() {
    return myLevelArguments;
  }

  @Override
  public List<? extends Expression> getDefCallArguments() {
    return myArguments;
  }

  @Override
  public FunctionDefinition getDefinition() {
    return (FunctionDefinition) super.getDefinition();
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitFunCall(this, params);
  }

  @Override
  public FunCallExpression toFunCall() {
    return this;
  }

  @Override
  public Expression addArgument(Expression argument) {
    if (myArguments.size() < DependentLink.Helper.size(getDefinition().getParameters())) {
      myArguments.add(argument);
      return this;
    } else {
      return super.addArgument(argument);
    }
  }

  @Override
  public Expression getStuckExpression() {
    if (!(getDefinition().getElimTree() instanceof BranchElimTreeNode)) {
      return null;
    }
    Binding binding = ((BranchElimTreeNode) getDefinition().getElimTree()).getReference();
    int i = 0;
    for (DependentLink param = getDefinition().getParameters(); param.hasNext(); param = param.getNext()) {
      if (param == binding) {
        return myArguments.get(i).getStuckExpression();
      }
      i++;
    }
    return null;
  }
}
