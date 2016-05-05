package com.jetbrains.jetpad.vclang.typechecking.normalization;

import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.term.Preprelude;
import com.jetbrains.jetpad.vclang.term.context.binding.Binding;
import com.jetbrains.jetpad.vclang.term.context.binding.TypedBinding;
import com.jetbrains.jetpad.vclang.term.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.term.definition.Function;
import com.jetbrains.jetpad.vclang.term.definition.FunctionDefinition;
import com.jetbrains.jetpad.vclang.term.expr.*;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.term.pattern.elimtree.LeafElimTreeNode;

import java.util.*;

import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;

public class EvalNormalizer implements Normalizer {
  @Override
  public Expression normalize(LamExpression fun, List<? extends Expression> arguments, List<? extends EnumSet<AppExpression.Flag>> flags, NormalizeVisitor.Mode mode) {
    int i = 0;
    DependentLink link = fun.getParameters();
    Substitution subst = new Substitution();
    while (link.hasNext() && i < arguments.size()) {
      subst.add(link, arguments.get(i++));
      link = link.getNext();
    }

    Expression result = fun.getBody();
    if (link.hasNext()) {
      result = Lam(link, result);
    }
    result = result.subst(subst);
    if (result != fun.getBody()) {
      result = result.addArguments(arguments.subList(i, arguments.size()), flags.subList(i, flags.size()));
    } else {
      result = Apps(result, arguments.subList(i, arguments.size()), flags.subList(i, flags.size()));
    }
    return result.normalize(mode);
  }

  @Override
  public Expression normalize(Function fun, DependentLink params, List<? extends Expression> paramArgs, List<? extends Expression> arguments, List<? extends Expression> otherArguments, List<? extends EnumSet<AppExpression.Flag>> otherFlags, NormalizeVisitor.Mode mode) {
    assert fun.getNumberOfRequiredArguments() == arguments.size();

    if (fun == Prelude.NAT_MUL && arguments.size() == 2) {
      NatExpression lhs = arguments.get(0).normalize(mode).toNat();
      NatExpression rhs = arguments.get(1).normalize(mode).toNat();
      if (lhs != null && lhs.isLiteral() && rhs != null && rhs.isLiteral()) {
        return Suc(lhs.getSuccs().multiply(rhs.getSuccs()), Zero());
      }
    }
    if (fun == Prelude.NAT_ADD && arguments.size() == 2) {
      NatExpression lhs = arguments.get(0).normalize(mode).toNat();
      if (lhs != null && lhs.isLiteral()) {
        Expression rhs = arguments.get(1);
        return Suc(lhs.getSuccs(), rhs).normalize(mode);
      }
    }
    if (fun == Preprelude.SUC && arguments.size() == 1) {
      Expression result = arguments.get(0).normalize(mode);
      return Suc(1, result).normalize(mode);
    }
    if (fun instanceof FunctionDefinition && Prelude.isCoe((FunctionDefinition) fun)) {
      Expression result = null;

      Binding binding = new TypedBinding("i", DataCall(Preprelude.INTERVAL));
      Expression normExpr = Apps(arguments.get(2), Reference(binding)).normalize(NormalizeVisitor.Mode.NF);
      if (!normExpr.findBinding(binding)) {
        result = arguments.get(3);
      } else {
        FunCallExpression mbIsoFun = normExpr.getFunction().toFunCall();
        List<? extends Expression> mbIsoArgs = normExpr.getArguments();
        if (mbIsoFun != null && Prelude.isIso(mbIsoFun.getDefinition()) && mbIsoArgs.size() == 9) {
          boolean noFreeVar = true;
          for (int i = 0; i < mbIsoArgs.size() - 1; i++) {
            if (mbIsoArgs.get(i).findBinding(binding)) {
              noFreeVar = false;
              break;
            }
          }
          if (noFreeVar) {
            ConCallExpression normedPtCon = arguments.get(4).normalize(NormalizeVisitor.Mode.NF).toConCall();
            if (normedPtCon != null && normedPtCon.getDefinition() == Preprelude.RIGHT) {
              result = Apps(mbIsoArgs.get(4), arguments.get(3));
            }
          }
        }
      }

      if (result != null) {
        return Apps(result, otherArguments, otherFlags).normalize(mode);
      }
    }

    List<Expression> matchedArguments = new ArrayList<>(arguments);
    LeafElimTreeNode leaf = fun.getElimTree().match(matchedArguments);
    if (leaf == null) {
      return null;
    }

    Substitution subst = leaf.matchedToSubst(matchedArguments);
    for (Expression argument : paramArgs) {
      subst.add(params, argument);
      params = params.getNext();
    }
    return Apps(leaf.getExpression().subst(subst), otherArguments, otherFlags).normalize(mode);
  }

  @Override
  public Expression normalize(LetExpression expression) {
    Expression term = expression.getExpression().normalize(NormalizeVisitor.Mode.NF);
    Set<Binding> bindings = new HashSet<>();
    for (LetClause clause : expression.getClauses()) {
      bindings.add(clause);
    }
    return term.findBinding(bindings) ? Let(expression.getClauses(), term) : term;
  }
}
