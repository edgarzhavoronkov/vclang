package com.jetbrains.jetpad.vclang.core.expr;

import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.expr.visitor.ExpressionVisitor;
import com.jetbrains.jetpad.vclang.core.sort.Sort;

import java.util.List;

public class ConCallExpression extends DefCallExpression {
  private final Sort mySortArgument;
  private final List<Expression> myDataTypeArguments;
  private final List<Expression> myArguments;

  public ConCallExpression(Constructor definition, Sort sortArgument, List<Expression> dataTypeArguments, List<Expression> arguments) {
    super(definition);
    assert dataTypeArguments != null;
    mySortArgument = sortArgument;
    myDataTypeArguments = dataTypeArguments;
    myArguments = arguments;
  }

  public List<Expression> getDataTypeArguments() {
    return myDataTypeArguments;
  }

  @Override
  public Sort getSortArgument() {
    return mySortArgument;
  }

  @Override
  public List<? extends Expression> getDefCallArguments() {
    return myArguments;
  }

  @Override
  public void setDefCallArgument(int index, Expression argument) {
    myArguments.set(index, argument);
  }

  @Override
  public Constructor getDefinition() {
    return (Constructor) super.getDefinition();
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitConCall(this, params);
  }

  public DataCallExpression getDataTypeExpression() {
    return getDefinition().getDataTypeExpression(mySortArgument, myDataTypeArguments);
  }

  @Override
  public boolean isWHNF() {
    //noinspection SimplifiableConditionalExpression
    return getDefinition().getBody() != null ? getDefinition().getBody().isWHNF(myArguments) : true;
  }

  @Override
  public Expression getStuckExpression() {
    return getDefinition().getBody() != null ? getDefinition().getBody().getStuckExpression(myArguments, this) : null;
  }
}
