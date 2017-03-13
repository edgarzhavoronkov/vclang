package com.jetbrains.jetpad.vclang.core.expr;

import com.jetbrains.jetpad.vclang.core.definition.Definition;
import com.jetbrains.jetpad.vclang.core.sort.LevelArguments;

import java.util.Collections;
import java.util.List;

public abstract class DefCallExpression extends Expression {
  private final Definition myDefinition;

  public DefCallExpression(Definition definition) {
    myDefinition = definition;
  }

  public List<? extends Expression> getDefCallArguments() {
    return Collections.emptyList();
  }

  public abstract LevelArguments getLevelArguments();

  public Definition getDefinition() {
    return myDefinition;
  }

  @Override
  public DefCallExpression toDefCall() {
    return this;
  }
}
