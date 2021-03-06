package com.jetbrains.jetpad.vclang.term;

import com.jetbrains.jetpad.vclang.core.context.binding.LevelVariable;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.EmptyDependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.SingleDependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.TypedSingleDependentLink;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.definition.DataDefinition;
import com.jetbrains.jetpad.vclang.core.definition.Definition;
import com.jetbrains.jetpad.vclang.core.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.core.elimtree.BranchElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.ElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.IntervalElim;
import com.jetbrains.jetpad.vclang.core.elimtree.LeafElimTree;
import com.jetbrains.jetpad.vclang.core.expr.*;
import com.jetbrains.jetpad.vclang.core.sort.Level;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckedReporter;
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState;
import com.jetbrains.jetpad.vclang.util.Pair;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.jetbrains.jetpad.vclang.core.expr.ExpressionFactory.parameter;

public class Prelude {
  public static DataDefinition INTERVAL;
  public static Constructor LEFT, RIGHT;

  public static DataDefinition NAT;
  public static Constructor ZERO, SUC;

  public static FunctionDefinition COERCE;

  public static DataDefinition PATH;
  public static FunctionDefinition PATH_INFIX;
  public static Constructor PATH_CON;

  public static FunctionDefinition AT;
  public static FunctionDefinition ISO;

  public static DataDefinition PROP_TRUNC;
  public static DataDefinition SET_TRUNC;

  public static Constructor PROP_TRUNC_PATH_CON;
  public static Constructor SET_TRUNC_PATH_CON;

  private Prelude() {
  }

  public static void update(Abstract.Definition abstractDef, Definition definition) {
    if (abstractDef.getName().equals("Nat")) {
      NAT = (DataDefinition) definition;
      ZERO = NAT.getConstructor("zero");
      SUC = NAT.getConstructor("suc");
    } else
    if (abstractDef.getName().equals("I")) {
      INTERVAL = (DataDefinition) definition;
      INTERVAL.setSort(Sort.PROP);
      INTERVAL.setMatchesOnInterval();
      LEFT = INTERVAL.getConstructor("left");
      RIGHT = INTERVAL.getConstructor("right");
    } else
    if (abstractDef.getName().equals("Path")) {
      PATH = (DataDefinition) definition;
      PATH.setSort(new Sort(new Level(LevelVariable.PVAR), new Level(LevelVariable.HVAR, -1)));
      PATH_CON = PATH.getConstructor("path");
    } else
    if (abstractDef.getName().equals("=")) {
      PATH_INFIX = (FunctionDefinition) definition;
    } else
    if (abstractDef.getName().equals("@")) {
      AT = (FunctionDefinition) definition;
      DependentLink atParams = AT.getParameters().subst(new ExprSubstitution(), LevelSubstitution.EMPTY, 3);
      SingleDependentLink intervalParam = new TypedSingleDependentLink(true, "i", ExpressionFactory.Interval());
      DependentLink pathParam = parameter("f", new PiExpression(Sort.STD, intervalParam, new AppExpression(new ReferenceExpression(atParams), new ReferenceExpression(intervalParam))));
      pathParam.setNext(parameter("i", ExpressionFactory.Interval()));
      Map<Constructor, ElimTree> children = Collections.singletonMap(PATH_CON, new LeafElimTree(pathParam, new AppExpression(new ReferenceExpression(pathParam), new ReferenceExpression(pathParam.getNext()))));
      ElimTree otherwise = new BranchElimTree(atParams, children);
      AT.setBody(new IntervalElim(AT.getParameters(), Collections.singletonList(new Pair<>(new ReferenceExpression(AT.getParameters().getNext()), new ReferenceExpression(AT.getParameters().getNext().getNext()))), otherwise));
      AT.setStatus(Definition.TypeCheckingStatus.NO_ERRORS);
    } else
    if (abstractDef.getName().equals("coe")) {
      COERCE = (FunctionDefinition) definition;
      DependentLink coeParams = COERCE.getParameters().subst(new ExprSubstitution(), LevelSubstitution.EMPTY, 2);
      COERCE.setBody(new BranchElimTree(coeParams, Collections.singletonMap(LEFT, new LeafElimTree(EmptyDependentLink.getInstance(), new ReferenceExpression(coeParams.getNext())))));
      COERCE.setStatus(Definition.TypeCheckingStatus.NO_ERRORS);
    } else
    if (abstractDef.getName().equals("iso")) {
      ISO = (FunctionDefinition) definition;
      DependentLink isoParams = ISO.getParameters().subst(new ExprSubstitution(), LevelSubstitution.EMPTY, 6);
      Map<Constructor, ElimTree> children = new HashMap<>();
      children.put(LEFT, new LeafElimTree(EmptyDependentLink.getInstance(), new ReferenceExpression(isoParams)));
      children.put(RIGHT, new LeafElimTree(EmptyDependentLink.getInstance(), new ReferenceExpression(isoParams.getNext())));
      ISO.setBody(new BranchElimTree(isoParams, children));
      ISO.setResultType(new UniverseExpression(new Sort(new Level(LevelVariable.PVAR), new Level(LevelVariable.HVAR))));
      ISO.setStatus(Definition.TypeCheckingStatus.NO_ERRORS);
    } else
    if (abstractDef.getName().equals("TrP")) {
      PROP_TRUNC = (DataDefinition) definition;
      PROP_TRUNC.setSort(Sort.PROP);
      PROP_TRUNC_PATH_CON = PROP_TRUNC.getConstructor("truncP");
    } else
    if (abstractDef.getName().equals("TrS")) {
      SET_TRUNC = (DataDefinition) definition;
      SET_TRUNC.setSort(Sort.SetOfLevel(new Level(LevelVariable.PVAR)));
      SET_TRUNC_PATH_CON = SET_TRUNC.getConstructor("truncS");
    }
  }

  public static class UpdatePreludeReporter implements TypecheckedReporter {
    private final TypecheckerState state;

    public UpdatePreludeReporter(TypecheckerState state) {
      this.state = state;
    }

    @Override
    public void typecheckingSucceeded(Abstract.Definition definition) {
      update(definition, state.getTypechecked(definition));
    }

    @Override
    public void typecheckingFailed(Abstract.Definition definition) {
      update(definition, state.getTypechecked(definition));
    }
  }

}
