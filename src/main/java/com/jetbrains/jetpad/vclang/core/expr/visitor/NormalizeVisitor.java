package com.jetbrains.jetpad.vclang.core.expr.visitor;

import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.context.binding.TypedBinding;
import com.jetbrains.jetpad.vclang.core.context.binding.inference.TypeClassInferenceVariable;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.SingleDependentLink;
import com.jetbrains.jetpad.vclang.core.definition.ClassField;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.definition.Function;
import com.jetbrains.jetpad.vclang.core.elimtree.*;
import com.jetbrains.jetpad.vclang.core.expr.*;
import com.jetbrains.jetpad.vclang.core.expr.type.Type;
import com.jetbrains.jetpad.vclang.core.expr.type.TypeExpression;
import com.jetbrains.jetpad.vclang.core.internal.FieldSet;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.SubstVisitor;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.util.ComputationInterruptedException;

import java.util.*;

public class NormalizeVisitor extends BaseExpressionVisitor<NormalizeVisitor.Mode, Expression>  {
  public enum Mode { WHNF, NF, RNF }

  private NormalizeVisitor() { }

  public static Expression normalize(Expression expression, Mode mode) {
    return mode == Mode.WHNF && expression.isWHNF() ? expression : expression.accept(new SubstVisitor(new ExprSubstitution(), LevelSubstitution.EMPTY), null).accept(new NormalizeVisitor(), mode);
  }

  public static ClassCallExpression normalize(ClassCallExpression expression, Mode mode) {
    return mode == Mode.WHNF ? expression : new NormalizeVisitor().visitClassCall(new SubstVisitor(new ExprSubstitution(), LevelSubstitution.EMPTY).visitClassCall(expression, null), mode);
  }

  public static DataCallExpression normalize(DataCallExpression expression, Mode mode) {
    return mode == Mode.WHNF ? expression : new NormalizeVisitor().visitDataCall(new SubstVisitor(new ExprSubstitution(), LevelSubstitution.EMPTY).visitDataCall(expression, null), mode);
  }

  public static PiExpression normalize(PiExpression expression, Mode mode) {
    return mode == Mode.WHNF ? expression : new NormalizeVisitor().visitPi(new SubstVisitor(new ExprSubstitution(), LevelSubstitution.EMPTY).visitPi(expression, null), mode);
  }

  public static SigmaExpression normalize(SigmaExpression expression, Mode mode) {
    return mode == Mode.WHNF ? expression : new NormalizeVisitor().visitSigma(new SubstVisitor(new ExprSubstitution(), LevelSubstitution.EMPTY).visitSigma(expression, null), mode);
  }

  @Override
  public Expression visitApp(AppExpression appExpr, Mode mode) {
    Expression function;
    AppExpression appExpr1 = appExpr;
    List<Expression> args = new ArrayList<>();
    while (true) {
      args.add(appExpr1.getArgument());
      function = appExpr1.getFunction().accept(this, Mode.WHNF);
      appExpr1.setFunction(function);
      if (!function.isInstance(AppExpression.class)) {
        break;
      }
      appExpr1 = function.cast(AppExpression.class);
    }
    Collections.reverse(args);

    if (function.isInstance(LamExpression.class)) {
      return normalizeLam(function.cast(LamExpression.class), args).accept(this, mode);
    }

    appExpr1.setFunction(mode == Mode.NF ? function.accept(this, Mode.NF) : function);
    if (mode == Mode.RNF) {
      return appExpr;
    }

    if (mode == Mode.NF) {
      appExpr1 = appExpr;
      while (appExpr1 != null) {
        appExpr1.setArgument(appExpr1.getArgument().accept(this, Mode.NF));
        appExpr1 = appExpr1.getFunction().checkedCast(AppExpression.class);
      }
    }

    return appExpr;
  }

  private Expression normalizeLam(LamExpression fun, List<? extends Expression> arguments) {
    int i = 0;
    SingleDependentLink link = fun.getParameters();
    ExprSubstitution subst = new ExprSubstitution();
    while (link.hasNext() && i < arguments.size()) {
      subst.add(link, arguments.get(i++));
      link = link.getNext();
    }

    Expression result = fun.getBody();
    if (link.hasNext()) {
      result = new LamExpression(fun.getResultSort(), link, result);
    }
    result = result.subst(subst);
    for (; i < arguments.size(); i++) {
      result = new AppExpression(result, arguments.get(i));
    }
    return result;
  }

  private void applyDefCall(DefCallExpression expr, Mode mode) {
    if (mode == Mode.NF) {
      for (int i = 0; i < expr.getDefCallArguments().size(); i++) {
        expr.setDefCallArgument(i, expr.getDefCallArguments().get(i).accept(this, mode));
      }
    }
  }

  private Expression visitDefCall(DefCallExpression expr, LevelSubstitution levelSubstitution, Mode mode) {
    if (expr.getDefinition() == Prelude.COERCE) {
      Expression result = null;

      Binding binding = new TypedBinding("i", ExpressionFactory.Interval());
      Expression normExpr = new AppExpression(expr.getDefCallArguments().get(0), new ReferenceExpression(binding)).accept(this, NormalizeVisitor.Mode.NF);
      if (!normExpr.findBinding(binding)) {
        result = expr.getDefCallArguments().get(1);
      } else {
        FunCallExpression funCall = normExpr.checkedCast(FunCallExpression.class);
        if (funCall != null && funCall.getDefinition() == Prelude.ISO) {
          List<? extends Expression> isoArgs = funCall.getDefCallArguments();
          boolean noFreeVar = true;
          for (int i = 0; i < isoArgs.size() - 1; i++) {
            if (isoArgs.get(i).findBinding(binding)) {
              noFreeVar = false;
              break;
            }
          }
          if (noFreeVar) {
            Expression normedPt = expr.getDefCallArguments().get(2).accept(this, NormalizeVisitor.Mode.WHNF);
            expr.setDefCallArgument(2, normedPt);
            if (normedPt.isInstance(ConCallExpression.class) && normedPt.cast(ConCallExpression.class).getDefinition() == Prelude.RIGHT) {
              result = new AppExpression(isoArgs.get(2), expr.getDefCallArguments().get(1));
            }
          }
        }
      }

      if (result != null) {
        return result.accept(this, mode);
      }
    }

    ElimTree elimTree;
    Body body = ((Function) expr.getDefinition()).getBody();
    if (body instanceof IntervalElim) {
      IntervalElim elim = (IntervalElim) body;
      int i0 = expr.getDefCallArguments().size() - elim.getCases().size();
      for (int i = i0; i < expr.getDefCallArguments().size(); i++) {
        Expression arg = expr.getDefCallArguments().get(i).accept(this, Mode.WHNF);
        expr.setDefCallArgument(i, arg);
        ConCallExpression conCall = arg.checkedCast(ConCallExpression.class);
        if (conCall != null) {
          ExprSubstitution substitution = getDataTypeArgumentsSubstitution(expr);
          DependentLink link = elim.getParameters();
          for (int j = 0; j < expr.getDefCallArguments().size(); j++) {
            if (j != i) {
              substitution.add(link, expr.getDefCallArguments().get(j));
            }
            link = link.getNext();
          }

          Expression result;
          if (conCall.getDefinition() == Prelude.LEFT) {
            result = elim.getCases().get(i - i0).proj1;
          } else if (conCall.getDefinition() == Prelude.RIGHT) {
            result = elim.getCases().get(i - i0).proj2;
          } else {
            throw new IllegalStateException();
          }

          if (result == null) {
            applyDefCall(expr, mode);
            return expr;
          } else {
            return result.accept(new SubstVisitor(substitution, LevelSubstitution.EMPTY), null).accept(this, mode);
          }
        }
      }
      elimTree = elim.getOtherwise();
    } else {
      elimTree = (ElimTree) body;
    }

    if (elimTree == null) {
      applyDefCall(expr, mode);
      return expr;
    }

    // TODO[cmpNorm]: Why it works slower this way?
    // for (int i = 0; i < expr.getDefCallArguments().size(); i++) {
    //   expr.setDefCallArgument(i, expr.getDefCallArguments().get(i).accept(this, Mode.WHNF));
    // }
    Expression result = eval(elimTree, expr.getDefCallArguments(), getDataTypeArgumentsSubstitution(expr), levelSubstitution);

    if (Thread.interrupted()) {
      throw new ComputationInterruptedException();
    }

    if (result == null) {
      applyDefCall(expr, mode);
      return expr;
    } else {
      return result.accept(this, mode);
    }
  }

  private Expression eval(ElimTree elimTree, List<? extends Expression> arguments, ExprSubstitution substitution, LevelSubstitution levelSubstitution) {
    Stack<Expression> stack = new Stack<>();
    for (int i = arguments.size() - 1; i >= 0; i--) {
      stack.push(arguments.get(i));
    }

    while (true) {
      for (DependentLink link = elimTree.getParameters(); link.hasNext(); link = link.getNext()) {
        substitution.add(link, stack.pop());
      }
      if (elimTree instanceof LeafElimTree) {
        return ((LeafElimTree) elimTree).getExpression().accept(new SubstVisitor(substitution, levelSubstitution), null);
      }

      ConCallExpression conCall = stack.peek().accept(this, Mode.WHNF).checkedCast(ConCallExpression.class);
      // TODO[cmpNorm]: Why it works slower this way?
      // ConCallExpression conCall = stack.peek().checkedCast(ConCallExpression.class);
      elimTree = ((BranchElimTree) elimTree).getChild(conCall == null ? null : conCall.getDefinition());
      if (elimTree == null) {
        return null;
      }

      if (conCall != null) {
        stack.pop();
        for (int i = conCall.getDefCallArguments().size() - 1; i >= 0; i--) {
          // TODO[cmpNorm]: Why it works slower this way?
          // Expression arg = conCall.getDefCallArguments().get(i).accept(this, Mode.WHNF);
          // conCall.setDefCallArgument(i, arg);
          // stack.push(arg);
          stack.push(conCall.getDefCallArguments().get(i));
        }
      }
    }
  }

  // This function mutates arguments
  public static boolean doesEvaluate(ElimTree elimTree, List<? extends Expression> arguments) {
    Stack<Expression> stack = new Stack<>();
    for (int i = arguments.size() - 1; i >= 0; i--) {
      stack.push(arguments.get(i));
    }

    NormalizeVisitor visitor = new NormalizeVisitor();
    while (true) {
      for (DependentLink link = elimTree.getParameters(); link.hasNext(); link = link.getNext()) {
        if (stack.isEmpty()) {
          return true;
        }
        stack.pop();
      }
      if (elimTree instanceof LeafElimTree || stack.isEmpty()) {
        return true;
      }

      Expression argument = stack.peek().accept(visitor, Mode.WHNF);
      ConCallExpression conCall = argument.checkedCast(ConCallExpression.class);
      elimTree = ((BranchElimTree) elimTree).getChild(conCall == null ? null : conCall.getDefinition());
      if (elimTree == null) {
        return false;
      }

      if (conCall != null) {
        stack.pop();
        for (int i = conCall.getDefCallArguments().size() - 1; i >= 0; i--) {
          stack.push(conCall.getDefCallArguments().get(i));
        }
      }
    }
  }

  // This function mutates arguments
  public static Expression eval(ElimTree elimTree, List<? extends Expression> arguments) {
    NormalizeVisitor visitor = new NormalizeVisitor();
    // TODO[cmpNorm]: Why it works slower this way?
    // return visitor.eval(elimTree, arguments.stream().map(arg -> arg.accept(visitor, Mode.WHNF)).collect(Collectors.toList()), new ExprSubstitution(), LevelSubstitution.EMPTY);
    return visitor.eval(elimTree, arguments, new ExprSubstitution(), LevelSubstitution.EMPTY);
  }

  private ExprSubstitution getDataTypeArgumentsSubstitution(DefCallExpression expr) {
    ExprSubstitution substitution = new ExprSubstitution();
    if (expr instanceof ConCallExpression) {
      int i = 0;
      List<Expression> args = ((ConCallExpression) expr).getDataTypeArguments();
      for (DependentLink link = ((ConCallExpression) expr).getDefinition().getDataTypeParameters(); link.hasNext(); link = link.getNext()) {
        substitution.add(link, args.get(i++));
      }
    }
    return substitution;
  }

  @Override
  public Expression visitDefCall(DefCallExpression expr, Mode mode) {
    if (!expr.getDefinition().status().bodyIsOK()) {
      applyDefCall(expr, mode);
      return expr;
    }

    if (expr instanceof FieldCallExpression) {
      Expression thisExpr = ((FieldCallExpression) expr).getExpression().accept(this, Mode.WHNF);
      expr.setDefCallArgument(0, thisExpr);
      if (!(thisExpr.isInstance(InferenceReferenceExpression.class) && thisExpr.cast(InferenceReferenceExpression.class).getVariable() instanceof TypeClassInferenceVariable)) {
        ClassCallExpression classCall = normalize(thisExpr.getType(), Mode.WHNF).checkedCast(ClassCallExpression.class);
        if (classCall != null) {
          FieldSet.Implementation impl = classCall.getFieldSet().getImplementation((ClassField) expr.getDefinition());
          if (impl != null) {
            Expression implExpr = impl.thisParam == null ? impl.term.accept(new SubstVisitor(new ExprSubstitution(), LevelSubstitution.EMPTY), null) : impl.substThisParam(thisExpr);
            return implExpr.accept(this, mode);
          }
        }
      }
    }

    if (expr.getDefinition() instanceof Function) {
      return visitDefCall(expr, expr.getSortArgument().toLevelSubstitution(), mode);
    } else {
      applyDefCall(expr, mode);
      return expr;
    }
  }

  @Override
  public ClassCallExpression visitClassCall(ClassCallExpression expr, Mode mode) {
    if (mode != Mode.NF) {
      return expr;
    }

    // TODO[cmpNorm]: Modify fieldSet in-place
    FieldSet fieldSet = FieldSet.applyVisitorToImplemented(expr.getFieldSet(), expr.getDefinition().getFieldSet(), this, mode);
    return new ClassCallExpression(expr.getDefinition(), expr.getSortArgument(), fieldSet);
  }

  @Override
  public DataCallExpression visitDataCall(DataCallExpression expr, Mode mode) {
    applyDefCall(expr, mode);
    return expr;
  }

  @Override
  public Expression visitReference(ReferenceExpression expr, Mode mode) {
    return expr.getBinding() instanceof LetClause ? normalize(((LetClause) expr.getBinding()).getExpression(), mode) : expr;
  }

  @Override
  public Expression visitInferenceReference(InferenceReferenceExpression expr, Mode mode) {
    return expr.getSubstExpression() != null ? expr.getSubstExpression().accept(this, mode) : expr;
  }

  @Override
  public Expression visitLam(LamExpression expr, Mode mode) {
    if (mode != Mode.WHNF) {
      expr.setBody(expr.getBody().accept(this, mode));
    }
    return expr;
  }

  private void normalizeDependentLink(DependentLink link, Mode mode) {
    for (; link.hasNext(); link = link.getNext()) {
      link = link.getNextTyped(null);
      Type type;
      if (link.getType() instanceof Expression) {
        type = (Type) ((Expression) link.getType()).accept(this, mode);
      } else {
        type = new TypeExpression(link.getType().getExpr().accept(this, mode), link.getType().getSortOfType());
      }
      link.setType(type);
    }
  }

  @Override
  public PiExpression visitPi(PiExpression expr, Mode mode) {
    if (mode != Mode.WHNF) {
      normalizeDependentLink(expr.getParameters(), mode);
      expr.setCodomain(expr.getCodomain().accept(this, mode));
    }
    return expr;
  }

  @Override
  public Expression visitUniverse(UniverseExpression expr, Mode mode) {
    return expr;
  }

  @Override
  public Expression visitError(ErrorExpression expr, Mode mode) {
    if ((mode == Mode.NF || mode == Mode.RNF) && expr.getExpression() != null) {
      expr.setExpression(expr.getExpression().accept(this, mode));
    }
    return expr;
  }

  @Override
  public Expression visitTuple(TupleExpression expr, Mode mode) {
    if (mode != Mode.WHNF) {
      for (int i = 0; i < expr.getFields().size(); i++) {
        expr.setField(i, expr.getFields().get(i).accept(this, mode));
      }
    }
    return expr;
  }

  @Override
  public SigmaExpression visitSigma(SigmaExpression expr, Mode mode) {
    if (mode != Mode.WHNF) {
      normalizeDependentLink(expr.getParameters(), mode);
    }
    return expr;
  }

  @Override
  public Expression visitProj(ProjExpression expr, Mode mode) {
    expr.setExpression(expr.getExpression().accept(this, Mode.WHNF));
    TupleExpression exprNorm = expr.getExpression().checkedCast(TupleExpression.class);
    if (exprNorm != null) {
      return exprNorm.getFields().get(expr.getField()).accept(this, mode);
    } else {
      if (mode == Mode.NF) {
        expr.setExpression(expr.getExpression().accept(this, Mode.NF));
      }
      return expr;
    }
  }

  @Override
  public Expression visitNew(NewExpression expr, Mode mode) {
    if (mode != Mode.WHNF) {
      expr.setExpression(visitClassCall(expr.getExpression(), mode));
    }
    return expr;
  }

  @Override
  public Expression visitLet(LetExpression letExpression, Mode mode) {
    ExprSubstitution substitution = new ExprSubstitution();
    for (LetClause clause : letExpression.getClauses()) {
      substitution.add(clause, clause.getExpression());
    }
    return letExpression.getExpression().subst(substitution).accept(this, mode);
  }

  @Override
  public Expression visitCase(CaseExpression expr, Mode mode) {
    // TODO[cmpNorm]: Why it works slower this way?
    // for (int i = 0; i < expr.getArguments().size(); i++) {
    //   expr.setArgument(i, expr.getArguments().get(i).accept(this, Mode.WHNF));
    // }
    Expression result = eval(expr.getElimTree(), expr.getArguments(), new ExprSubstitution(), LevelSubstitution.EMPTY);
    if (result != null) {
      return result.accept(this, mode);
    }
    if (mode != Mode.NF) {
      return expr;
    }

    normalizeDependentLink(expr.getParameters(), mode);
    expr.setResultType(expr.getResultType().accept(this, mode));
    expr.setElimTree(normalizeElimTree(expr.getElimTree()));
    for (int i = 0; i < expr.getArguments().size(); i++) {
      expr.setArgument(i, expr.getArguments().get(i).accept(this, mode));
    }
    return expr;
  }

  private ElimTree normalizeElimTree(ElimTree elimTree) {
    ExprSubstitution substitution = new ExprSubstitution();
    DependentLink vars = DependentLink.Helper.subst(elimTree.getParameters(), substitution);
    if (elimTree instanceof LeafElimTree) {
      return new LeafElimTree(vars, ((LeafElimTree) elimTree).getExpression().subst(substitution).accept(this, Mode.NF));
    } else {
      Map<Constructor, ElimTree> children = new HashMap<>();
      for (Map.Entry<Constructor, ElimTree> entry : ((BranchElimTree) elimTree).getChildren()) {
        children.put(entry.getKey(), normalizeElimTree(entry.getValue()));
      }
      return new BranchElimTree(vars, children);
    }
  }

  @Override
  public Expression visitOfType(OfTypeExpression expr, Mode mode) {
    return mode == Mode.NF ? new OfTypeExpression(expr.getExpression().accept(this, mode), expr.getTypeOf()) : expr.getExpression().accept(this, mode);
  }
}
