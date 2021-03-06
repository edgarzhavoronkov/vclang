package com.jetbrains.jetpad.vclang.core.pattern;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.expr.ConCallExpression;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;

import java.util.ArrayList;
import java.util.List;

public class ConstructorPattern implements Pattern {
  private final ConCallExpression myConCall;
  private final Patterns myPatterns;

  public ConstructorPattern(ConCallExpression conCall, Patterns patterns) {
    myConCall = conCall;
    myPatterns = patterns;
  }

  public Patterns getPatterns() {
    return myPatterns;
  }

  public Constructor getConstructor() {
    return myConCall.getDefinition();
  }

  public List<Pattern> getArguments() {
    return myPatterns.getPatternList();
  }

  public Sort getSortArgument() {
    return myConCall.getSortArgument();
  }

  public List<Expression> getDataTypeArguments() {
    return myConCall.getDataTypeArguments();
  }

  public ConCallExpression getConCall() {
    return myConCall;
  }

  @Override
  public ConCallExpression toExpression() {
    List<Expression> arguments = new ArrayList<>(myPatterns.getPatternList().size());
    for (Pattern pattern : myPatterns.getPatternList()) {
      Expression argument = pattern.toExpression();
      if (argument == null) {
        return null;
      }
      arguments.add(argument);
    }
    return new ConCallExpression(myConCall.getDefinition(), myConCall.getSortArgument(), myConCall.getDataTypeArguments(), arguments);
  }

  @Override
  public DependentLink getFirstBinding() {
    return myPatterns.getFirstBinding();
  }

  @Override
  public DependentLink getLastBinding() {
    return myPatterns.getLastBinding();
  }

  @Override
  public MatchResult match(Expression expression, List<Expression> result) {
    ConCallExpression conCall = expression.normalize(NormalizeVisitor.Mode.WHNF).checkedCast(ConCallExpression.class);
    if (conCall == null) {
      return MatchResult.MAYBE;
    }
    if (conCall.getDefinition() != myConCall.getDefinition()) {
      return MatchResult.FAIL;
    }
    return myPatterns.match(conCall.getDefCallArguments(), result);
  }

  @Override
  public boolean unify(Pattern other, ExprSubstitution substitution1, ExprSubstitution substitution2) {
    if (other instanceof BindingPattern) {
      substitution2.add(((BindingPattern) other).getBinding(), toExpression());
      return true;
    }

    if (other instanceof ConstructorPattern) {
      ConstructorPattern conPattern = (ConstructorPattern) other;
      return myConCall.getDefinition() == conPattern.myConCall.getDefinition() && myPatterns.unify(conPattern.myPatterns, substitution1, substitution2);
    }

    return false;
  }
}
