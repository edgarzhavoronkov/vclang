package com.jetbrains.jetpad.vclang.typechecking.typeclass.pool;

import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.term.Abstract;

import java.util.ArrayList;
import java.util.List;

public class LocalInstancePool implements ClassViewInstancePool {
  static private class Pair {
    final Expression key;
    final Abstract.ClassView classView;
    final Expression value;

    public Pair(Expression key, Abstract.ClassView classView, Expression value) {
      this.key = key;
      this.classView = classView;
      this.value = value;
    }
  }

  private final List<Pair> myPool = new ArrayList<>();

  private Expression getInstance(Expression classifyingExpression, Abstract.ClassView classView) {
    for (Pair pair : myPool) {
      if (pair.key.equals(classifyingExpression) && pair.classView == classView) {
        return pair.value;
      }
    }
    return null;
  }

  @Override
  public Expression getInstance(Abstract.ReferenceExpression defCall, Expression classifyingExpression, Abstract.ClassView classView) {
    return getInstance(classifyingExpression, classView);
  }

  @Override
  public Expression getInstance(Abstract.ReferenceExpression defCall, int paramIndex, Expression classifyingExpression, Abstract.ClassDefinition classDefinition) {
    for (Pair pair : myPool) {
      if (pair.key.equals(classifyingExpression) && pair.classView.getUnderlyingClassReference().getReferent() == classDefinition) {
        return pair.value;
      }
    }
    return null;
  }

  public Expression addInstance(Expression classifyingExpression, Abstract.ClassView classView, Expression instance) {
    Expression oldInstance = getInstance(classifyingExpression, classView);
    if (oldInstance != null) {
      return oldInstance;
    } else {
      myPool.add(new Pair(classifyingExpression, classView, instance));
      return null;
    }
  }
}
