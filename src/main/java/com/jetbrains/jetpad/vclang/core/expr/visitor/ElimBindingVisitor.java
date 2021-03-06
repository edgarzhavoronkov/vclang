package com.jetbrains.jetpad.vclang.core.expr.visitor;

import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.context.binding.Variable;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.SingleDependentLink;
import com.jetbrains.jetpad.vclang.core.definition.ClassField;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.elimtree.BranchElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.ElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.LeafElimTree;
import com.jetbrains.jetpad.vclang.core.expr.*;
import com.jetbrains.jetpad.vclang.core.expr.type.Type;
import com.jetbrains.jetpad.vclang.core.expr.type.TypeExpression;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.SubstVisitor;

import java.util.*;

public class ElimBindingVisitor extends BaseExpressionVisitor<Void, Expression> {
  private final FindMissingBindingVisitor myVisitor;
  private Variable myFoundVariable = null;

  private ElimBindingVisitor(Set<Binding> bindings) {
    myVisitor = new FindMissingBindingVisitor(bindings);
  }

  public Variable getFoundVariable() {
    return myFoundVariable;
  }

  public static Expression findBindings(Expression expression, Set<Binding> bindings) {
    return new ElimBindingVisitor(bindings).findBindings(expression, true);
  }

  private Expression findBindings(Expression expression, boolean normalize) {
    myFoundVariable = expression.accept(myVisitor, null);
    if (myFoundVariable == null) {
      return expression;
    }
    return (normalize ? expression.normalize(NormalizeVisitor.Mode.WHNF) : expression).accept(this, null);
  }

  @Override
  public AppExpression visitApp(AppExpression expr, Void params) {
    Expression result = findBindings(expr.getFunction(), false);
    if (result == null) {
      return null;
    }
    Expression arg = findBindings(expr.getArgument(), true);
    if (arg == null) {
      return null;
    }
    return new AppExpression(result, arg);
  }

  private List<Expression> visitDefCallArguments(List<? extends Expression> args) {
    List<Expression> result = new ArrayList<>(args.size());
    for (Expression arg : args) {
      Expression newArg = findBindings(arg, true);
      if (newArg == null) {
        return null;
      }
      result.add(newArg);
    }
    return result;
  }

  @Override
  public Expression visitDefCall(DefCallExpression expr, Void params) {
    List<Expression> newArgs = visitDefCallArguments(expr.getDefCallArguments());
    return newArgs == null ? null : expr.getDefinition().getDefCall(expr.getSortArgument(), null, newArgs);
  }

  @Override
  public Expression visitFieldCall(FieldCallExpression expr, Void params) {
    Expression newExpr = findBindings(expr.getExpression(), false);
    return newExpr == null ? null : FieldCallExpression.make(expr.getDefinition(), newExpr);
  }

  @Override
  public ConCallExpression visitConCall(ConCallExpression expr, Void params) {
    List<Expression> newArgs = visitDefCallArguments(expr.getDefCallArguments());
    if (newArgs == null) {
      return null;
    }
    List<Expression> dataTypeArgs = new ArrayList<>(expr.getDataTypeArguments().size());
    for (Expression arg : expr.getDataTypeArguments()) {
      Expression newArg = findBindings(arg, true);
      if (newArg == null) {
        return null;
      }
      dataTypeArgs.add(newArg);
    }
    return new ConCallExpression(expr.getDefinition(), expr.getSortArgument(), dataTypeArgs, newArgs);
  }

  @Override
  public ClassCallExpression visitClassCall(ClassCallExpression expr, Void params) {
    Map<ClassField, Expression> newFieldSet = new HashMap<>();
    for (Map.Entry<ClassField, Expression> entry : expr.getImplementedHere().entrySet()) {
      Expression newImpl = findBindings(entry.getValue(), true);
      if (newImpl == null) {
        return null;
      }
      newFieldSet.put(entry.getKey(), newImpl);
    }
    return new ClassCallExpression(expr.getDefinition(), expr.getSortArgument(), newFieldSet, expr.getSort());
  }

  @Override
  public ReferenceExpression visitReference(ReferenceExpression expr, Void params) {
    if (!myVisitor.getBindings().contains(expr.getBinding())) {
      myFoundVariable = expr.getBinding();
      return null;
    } else {
      return expr;
    }
  }

  @Override
  public Expression visitInferenceReference(InferenceReferenceExpression expr, Void params) {
    return expr.getSubstExpression() != null ? findBindings(expr.getSubstExpression(), true) : expr;
  }

  @Override
  public LamExpression visitLam(LamExpression expr, Void params) {
    ExprSubstitution substitution = new ExprSubstitution();
    SingleDependentLink parameters = DependentLink.Helper.subst(expr.getParameters(), substitution);
    if (!visitDependentLink(parameters)) {
      return null;
    }
    Expression body = findBindings(expr.getBody(), true);
    if (body == null) {
      return null;
    }
    return new LamExpression(expr.getResultSort(), parameters, body.subst(substitution));
  }

  @Override
  public PiExpression visitPi(PiExpression expr, Void params) {
    ExprSubstitution substitution = new ExprSubstitution();
    SingleDependentLink parameters = DependentLink.Helper.subst(expr.getParameters(), substitution);
    if (!visitDependentLink(parameters)) {
      return null;
    }
    Expression codomain = findBindings(expr.getCodomain(), true);
    if (codomain == null) {
      return null;
    }
    return new PiExpression(expr.getResultSort(), parameters, codomain.subst(substitution));
  }

  @Override
  public UniverseExpression visitUniverse(UniverseExpression expr, Void params) {
    return expr;
  }

  @Override
  public ErrorExpression visitError(ErrorExpression expr, Void params) {
    if (expr.getExpression() == null) {
      return expr;
    }
    Expression errorExpr = findBindings(expr.getExpression(), true);
    if (errorExpr == null) {
      myFoundVariable = null;
      return new ErrorExpression(null, expr.getError());
    } else {
      return new ErrorExpression(errorExpr, expr.getError());
    }
  }

  @Override
  public TupleExpression visitTuple(TupleExpression expr, Void params) {
    List<Expression> newFields = new ArrayList<>(expr.getFields().size());
    for (Expression field : expr.getFields()) {
      Expression newField = findBindings(field, true);
      if (newField == null) {
        return null;
      }
      newFields.add(newField);
    }

    SigmaExpression sigmaExpr = (SigmaExpression) findBindings(expr.getSigmaType(), false);
    return sigmaExpr == null ? null : new TupleExpression(newFields, sigmaExpr);
  }

  @Override
  public SigmaExpression visitSigma(SigmaExpression expr, Void params) {
    DependentLink parameters = DependentLink.Helper.subst(expr.getParameters(), new ExprSubstitution());
    return visitDependentLink(parameters) ? new SigmaExpression(expr.getSort(), parameters) : null;
  }

  @Override
  public Expression visitProj(ProjExpression expr, Void params) {
    Expression newExpr = findBindings(expr.getExpression(), false);
    return newExpr == null ? null : ProjExpression.make(newExpr, expr.getField());
  }

  private boolean visitDependentLink(DependentLink link) {
    for (; link.hasNext(); link = link.getNext()) {
      link = link.getNextTyped(null);
      Expression type = findBindings(link.getTypeExpr(), true);
      if (type == null) {
        return false;
      }
      link.setType(type instanceof Type ? (Type) type : new TypeExpression(type, link.getType().getSortOfType()));
    }
    return true;
  }

  @Override
  public NewExpression visitNew(NewExpression expr, Void params) {
    ClassCallExpression newExpr = visitClassCall(expr.getExpression(), null);
    return newExpr == null ? null : new NewExpression(newExpr);
  }

  @Override
  public LetExpression visitLet(LetExpression letExpression, Void params) {
    throw new IllegalStateException();
    /*
    List<LetClause> newClauses = new ArrayList<>(letExpression.getClauses().size());
    for (LetClause clause : letExpression.getClauses()) {
      Expression newClauseExpr = findBindings(clause.getExpression(), true);
      if (newClauseExpr == null) {
        return null;
      }
      newClauses.add(new LetClause(clause.getName(), newClauseExpr));
    }

    Expression newExpr = findBindings(letExpression.getExpression(), true);
    return newExpr == null ? null : new LetExpression(newClauses, newExpr);
    */
  }

  @Override
  public CaseExpression visitCase(CaseExpression expr, Void params) {
    List<Expression> newArgs = new ArrayList<>(expr.getArguments().size());
    for (Expression argument : expr.getArguments()) {
      Expression newArg = findBindings(argument, true);
      if (newArg == null) {
        return null;
      }
    }

    Expression newType = findBindings(expr.getResultType(), true);
    if (newType == null) {
      return null;
    }

    ExprSubstitution substitution = new ExprSubstitution();
    DependentLink parameters = DependentLink.Helper.subst(expr.getParameters(), substitution);
    if (!visitDependentLink(parameters)) {
      return null;
    }

    ElimTree newElimTree = findBindingInElimTree(expr.getElimTree());
    return newElimTree == null ? null : new CaseExpression(parameters, newType.subst(substitution), newElimTree, newArgs);
  }

  private ElimTree findBindingInElimTree(ElimTree elimTree) {
    ExprSubstitution substitution = new ExprSubstitution();
    DependentLink parameters = DependentLink.Helper.subst(elimTree.getParameters(), substitution);
    if (!visitDependentLink(parameters)) {
      return null;
    }

    if (elimTree instanceof LeafElimTree) {
      Expression newExpr = findBindings(((LeafElimTree) elimTree).getExpression(), true);
      return newExpr == null ? null : new LeafElimTree(parameters, newExpr.subst(substitution));
    } else {
      Map<Constructor, ElimTree> newChildren = new HashMap<>();
      SubstVisitor visitor = new SubstVisitor(substitution, LevelSubstitution.EMPTY);
      for (Map.Entry<Constructor, ElimTree> entry : ((BranchElimTree) elimTree).getChildren()) {
        ElimTree newElimTree = findBindingInElimTree(entry.getValue());
        if (newElimTree == null) {
          return null;
        }
        newChildren.put(entry.getKey(), visitor.substElimTree(newElimTree));
      }

      return new BranchElimTree(parameters, newChildren);
    }
  }

  @Override
  public OfTypeExpression visitOfType(OfTypeExpression expr, Void params) {
    Expression newExpr = findBindings(expr.getExpression(), true);
    if (newExpr == null) {
      return null;
    }
    Expression newType = findBindings(expr.getTypeOf(), true);
    return newType == null ? null : new OfTypeExpression(newExpr, newType);
  }
}
