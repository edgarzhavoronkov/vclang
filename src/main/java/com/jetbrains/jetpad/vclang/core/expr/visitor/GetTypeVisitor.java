package com.jetbrains.jetpad.vclang.core.expr.visitor;

import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.expr.*;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.StdLevelSubstitution;
import com.jetbrains.jetpad.vclang.error.IncorrectExpressionException;

import java.util.ArrayList;
import java.util.List;

public class GetTypeVisitor extends BaseExpressionVisitor<Void, Expression> {
  public final static GetTypeVisitor INSTANCE = new GetTypeVisitor();

  private GetTypeVisitor() {
  }

  @Override
  public Expression visitApp(AppExpression expr, Void params) {
    Expression result = expr.getFunction().accept(this, null).applyExpression(expr.getArgument());
    if (result == null) {
      throw new IncorrectExpressionException("Expression " + expr.getFunction() + " does not have a pi type, but is applied to " + expr.getArgument());
    }
    return result;
  }

  @Override
  public Expression visitDefCall(DefCallExpression expr, Void params) {
    List<DependentLink> defParams = new ArrayList<>();
    Expression type = expr.getDefinition().getTypeWithParams(defParams, expr.getSortArgument());
    assert expr.getDefCallArguments().size() == defParams.size();
    return type.subst(DependentLink.Helper.toSubstitution(defParams, expr.getDefCallArguments()), expr.getSortArgument().toLevelSubstitution());
  }

  @Override
  public UniverseExpression visitDataCall(DataCallExpression expr, Void params) {
    return new UniverseExpression(expr.getDefinition().getSort().subst(new StdLevelSubstitution(expr.getSortArgument())));
  }

  @Override
  public DataCallExpression visitConCall(ConCallExpression expr, Void params) {
    return expr.getDefinition().getDataTypeExpression(expr.getSortArgument(), expr.getDataTypeArguments());
  }

  @Override
  public Expression visitClassCall(ClassCallExpression expr, Void params) {
    return new UniverseExpression(expr.getSort().subst(expr.getSortArgument().toLevelSubstitution()));
  }

  @Override
  public Expression visitReference(ReferenceExpression expr, Void params) {
    return expr.getBinding().getTypeExpr().copy();
  }

  @Override
  public Expression visitInferenceReference(InferenceReferenceExpression expr, Void params) {
    return expr.getSubstExpression() != null ? expr.getSubstExpression().accept(this, null) : expr.getVariable().getType();
  }

  @Override
  public Expression visitLam(LamExpression expr, Void ignored) {
    return new PiExpression(expr.getResultSort(), expr.getParameters(), expr.getBody().accept(this, null));
  }

  @Override
  public Expression visitPi(PiExpression expr, Void params) {
    return new UniverseExpression(expr.getResultSort());
  }

  @Override
  public Expression visitSigma(SigmaExpression expr, Void params) {
    return new UniverseExpression(expr.getSort());
  }

  @Override
  public Expression visitUniverse(UniverseExpression expr, Void params) {
    return new UniverseExpression(expr.getSort().succ());
  }

  @Override
  public Expression visitError(ErrorExpression expr, Void params) {
    return expr.getExpression() == null ? expr : expr.getExpression();
  }

  @Override
  public Expression visitTuple(TupleExpression expr, Void params) {
    return expr.getSigmaType();
  }

  @Override
  public Expression visitProj(ProjExpression expr, Void ignored) {
    Expression type = expr.getExpression().accept(this, null).normalize(NormalizeVisitor.Mode.WHNF);
    if (type.isInstance(ErrorExpression.class)) {
      return type;
    }

    if (!type.isInstance(SigmaExpression.class)) {
      throw new IncorrectExpressionException("Expression " + expr + " should have a sigma type");
    }
    DependentLink params = type.cast(SigmaExpression.class).getParameters();
    if (expr.getField() == 0) {
      return params.getTypeExpr();
    }

    ExprSubstitution subst = new ExprSubstitution();
    for (int i = 0; i < expr.getField(); i++) {
      subst.add(params, ProjExpression.make(expr.getExpression(), i));
      params = params.getNext();
    }
    return params.getTypeExpr().subst(subst);
  }

  @Override
  public Expression visitNew(NewExpression expr, Void params) {
    return expr.getExpression();
  }

  @Override
  public Expression visitLet(LetExpression expr, Void params) {
    return expr.getExpression().accept(this, null).subst(expr.getClausesSubstitution());
  }

  @Override
  public Expression visitCase(CaseExpression expr, Void params) {
    return expr.getResultType().subst(DependentLink.Helper.toSubstitution(expr.getParameters(), expr.getArguments()));
  }

  @Override
  public Expression visitOfType(OfTypeExpression expr, Void params) {
    return expr.getTypeOf();
  }
}
