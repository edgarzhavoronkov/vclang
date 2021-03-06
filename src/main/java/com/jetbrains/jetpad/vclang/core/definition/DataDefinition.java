package com.jetbrains.jetpad.vclang.core.definition;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.expr.DataCallExpression;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.expr.UniverseExpression;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.term.Abstract;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DataDefinition extends Definition {
  private List<Constructor> myConstructors;
  private DependentLink myParameters;
  private Sort mySort;
  private boolean myMatchesOnInterval;
  private boolean myIsTruncated;
  private Set<Integer> myCovariantParameters;

  public DataDefinition(Abstract.DataDefinition abstractDef) {
    super(abstractDef, TypeCheckingStatus.HEADER_HAS_ERRORS);
    myConstructors = new ArrayList<>();
  }

  @Override
  public Abstract.DataDefinition getAbstractDefinition() {
    return (Abstract.DataDefinition) super.getAbstractDefinition();
  }

  public Sort getSort() {
    return mySort;
  }

  public void setSort(Sort sort) {
    mySort = sort;
  }

  public boolean isCovariant(int index) {
    return myCovariantParameters != null && myCovariantParameters.contains(index);
  }

  public void setCovariant(int index) {
    if (myCovariantParameters == null) {
      myCovariantParameters = new HashSet<>();
    }
    myCovariantParameters.add(index);
  }

  public boolean isTruncated() {
    return myIsTruncated;
  }

  public void setIsTruncated(boolean value) {
    myIsTruncated = value;
  }

  @Override
  public DependentLink getParameters() {
    assert status().headerIsOK();
    return myParameters;
  }

  public void setParameters(DependentLink parameters) {
    myParameters = parameters;
  }

  public List<Constructor> getConstructors() {
    return myConstructors;
  }

  public Constructor getConstructor(Abstract.Constructor abstractCon) {
    for (Constructor constructor : myConstructors) {
      if (constructor.getAbstractDefinition().equals(abstractCon)) {
        return constructor;
      }
    }
    return null;
  }

  public Constructor getConstructor(String name) {
    for (Constructor constructor : myConstructors) {
      if (constructor.getName().equals(name)) {
        return constructor;
      }
    }
    return null;
  }

  public void addConstructor(Constructor constructor) {
    myConstructors.add(constructor);
  }

  public boolean matchesOnInterval() { return myMatchesOnInterval; }

  public void setMatchesOnInterval() { myMatchesOnInterval = true; }

  public boolean hasIndexedConstructors() {
    for (Constructor constructor : myConstructors) {
      if (constructor.getPatterns() != null) {
        return true;
      }
    }
    return false;
  }

  @Override
  public Expression getTypeWithParams(List<? super DependentLink> params, Sort sortArgument) {
    if (!status().headerIsOK()) {
      return null;
    }

    ExprSubstitution subst = new ExprSubstitution();
    LevelSubstitution polySubst = sortArgument.toLevelSubstitution();
    params.addAll(DependentLink.Helper.toList(DependentLink.Helper.subst(myParameters, subst, polySubst)));
    return new UniverseExpression(mySort.subst(polySubst));
  }

  @Override
  public DataCallExpression getDefCall(Sort sortArgument, Expression thisExpr, List<Expression> arguments) {
    if (thisExpr == null) {
      return new DataCallExpression(this, sortArgument, arguments);
    } else {
      List<Expression> args = new ArrayList<>(arguments.size() + 1);
      args.add(thisExpr);
      args.addAll(arguments);
      return new DataCallExpression(this, sortArgument, args);
    }
  }
}
