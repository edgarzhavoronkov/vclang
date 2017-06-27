package com.jetbrains.jetpad.vclang.typechecking.patternmatching;

import com.jetbrains.jetpad.vclang.core.context.Utils;
import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.EmptyDependentLink;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.elimtree.*;
import com.jetbrains.jetpad.vclang.core.expr.ConCallExpression;
import com.jetbrains.jetpad.vclang.core.expr.DataCallExpression;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.core.expr.ReferenceExpression;
import com.jetbrains.jetpad.vclang.core.expr.visitor.GetTypeVisitor;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.LevelSubstitution;
import com.jetbrains.jetpad.vclang.core.subst.SubstVisitor;
import com.jetbrains.jetpad.vclang.error.Error;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.typechecking.error.local.LocalTypeCheckingError;
import com.jetbrains.jetpad.vclang.typechecking.error.local.MissingClausesError;
import com.jetbrains.jetpad.vclang.typechecking.visitor.CheckTypeVisitor;
import com.jetbrains.jetpad.vclang.util.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class ElimTypechecking {
  private final CheckTypeVisitor myVisitor;
  private Set<Abstract.Clause> myUnusedClauses;
  private final boolean myAllowInterval;
  private Expression myExpectedType;
  private boolean myOK;
  private Stack<CoverageChecking.ClauseElem> myContext;

  private static final int MISSING_CLAUSES_LIST_SIZE = 10;
  private List<List<CoverageChecking.ClauseElem>> myMissingClauses;

  public ElimTypechecking(CheckTypeVisitor visitor, Expression expectedType, boolean allowInterval) {
    myVisitor = visitor;
    myExpectedType = expectedType;
    myAllowInterval = allowInterval;
  }

  public ElimTree typecheckElim(Abstract.ElimFunctionBody body, DependentLink patternTypes) {
    List<DependentLink> elimParams = null;
    if (!body.getExpressions().isEmpty()) {
      int expectedNumberOfPatterns = body.getExpressions().size();
      for (Abstract.Clause clause : body.getClauses()) {
        if (clause.getPatterns().size() != expectedNumberOfPatterns) {
          myVisitor.getErrorReporter().report(new LocalTypeCheckingError("Expected " + expectedNumberOfPatterns + " patterns, but got " + clause.getPatterns().size(), clause));
          return null;
        }
      }

      DependentLink link = patternTypes;
      elimParams = new ArrayList<>(body.getExpressions().size());
      for (Abstract.Expression expr : body.getExpressions()) {
        if (expr instanceof Abstract.ReferenceExpression) {
          DependentLink elimParam = (DependentLink) myVisitor.getContext().remove(((Abstract.ReferenceExpression) expr).getReferent());
          while (elimParam != link) {
            if (!link.hasNext()) {
              myVisitor.getErrorReporter().report(new LocalTypeCheckingError("Variable elimination must be in the order of variable introduction", expr));
              return null;
            }
            link = link.getNext();
          }
          elimParams.add(elimParam);
        } else {
          myVisitor.getErrorReporter().report(new LocalTypeCheckingError("\\elim can be applied only to a local variable", expr));
          return null;
        }
      }
    } else {
      myVisitor.getContext().clear();
    }

    List<ClauseData> clauses = new ArrayList<>(body.getClauses().size());
    PatternTypechecking patternTypechecking = new PatternTypechecking(myVisitor.getErrorReporter(), myAllowInterval);
    myOK = true;
    for (Abstract.Clause clause : body.getClauses()) {
      Map<Abstract.ReferableSourceNode, Binding> originalContext = new HashMap<>(myVisitor.getContext());
      Pair<List<Pattern>, CheckTypeVisitor.Result> result = patternTypechecking.typecheckClause(clause, patternTypes, elimParams, myExpectedType, myVisitor, true);
      myVisitor.setContext(originalContext);

      if (result == null) {
        myOK = false;
      } else {
        clauses.add(new ClauseData(result.proj1, result.proj2 == null ? null : result.proj2.expression, new ExprSubstitution(), clause));
      }
    }
    if (!myOK) {
      return null;
    }

    if (clauses.isEmpty()) {
      myVisitor.getErrorReporter().report(new LocalTypeCheckingError("Expected a clause list", body));
      return null;
    }

    myUnusedClauses = new HashSet<>(body.getClauses());
    myContext = new Stack<>();
    ElimTree elimTree = clausesToElimTree(clauses);

    if (myMissingClauses != null && !myMissingClauses.isEmpty()) {
      final List<DependentLink> finalElimParams = elimParams;
      myVisitor.getErrorReporter().report(new MissingClausesError(myMissingClauses.stream().map(missingClause -> CoverageChecking.unflattenMissingClause(missingClause, patternTypes, finalElimParams)).collect(Collectors.toList()), body));
    }
    if (!myOK) {
      return null;
    }
    for (Abstract.Clause clause : myUnusedClauses) {
      myVisitor.getErrorReporter().report(new LocalTypeCheckingError(Error.Level.WARNING, "This clause is redundant", clause));
    }
    return elimTree;
  }

  private class ClauseData {
    List<Pattern> patterns;
    Expression expression;
    ExprSubstitution substitution;
    Abstract.Clause clause;

    ClauseData(List<Pattern> patterns, Expression expression, ExprSubstitution substitution, Abstract.Clause clause) {
      this.patterns = patterns;
      this.expression = expression;
      this.substitution = substitution;
      this.clause = clause;
    }
  }

  private ElimTree clausesToElimTree(List<ClauseData> clauseDataList) {
    try (Utils.ContextSaver ignored = new Utils.ContextSaver(myContext)) {
      int index = 0;
      loop:
      for (; index < clauseDataList.get(0).patterns.size(); index++) {
        for (ClauseData clauseData : clauseDataList) {
          if (!(clauseData.patterns.get(index) instanceof BindingPattern)) {
            break loop;
          }
        }
      }

      // If all patterns are variables
      if (index == clauseDataList.get(0).patterns.size()) {
        ClauseData clauseData = clauseDataList.get(0);
        myUnusedClauses.remove(clauseData.clause);
        return new LeafElimTree(clauseData.patterns.isEmpty() ? EmptyDependentLink.getInstance() : ((BindingPattern) clauseData.patterns.get(0)).getBinding(), clauseData.expression.subst(clauseData.substitution));
      }

      // Make new list of variables
      DependentLink vars = index == 0 ? EmptyDependentLink.getInstance() : ((BindingPattern) clauseDataList.get(0).patterns.get(0)).getBinding().subst(clauseDataList.get(0).substitution, LevelSubstitution.EMPTY, index);
      for (DependentLink link = vars; link.hasNext(); link = link.getNext()) {
        myContext.push(new CoverageChecking.PatternClauseElem(new BindingPattern(link)));
      }

      // Update substitution for each clause
      int j = 0;
      for (DependentLink link = vars; link.hasNext(); link = link.getNext(), j++) {
        Expression newRef = new ReferenceExpression(link);
        clauseDataList.get(0).substitution.remove(link);
        for (int i = 1; i < clauseDataList.size(); i++) {
          clauseDataList.get(i).substitution.add(((BindingPattern) clauseDataList.get(i).patterns.get(j)).getBinding(), newRef);
        }
      }

      ClauseData conClauseData = null;
      for (ClauseData clauseData : clauseDataList) {
        Pattern pattern = clauseData.patterns.get(index);
        if (pattern instanceof EmptyPattern) {
          myUnusedClauses.remove(clauseData.clause);
          return new BranchElimTree(vars, Collections.emptyMap());
        }
        if (conClauseData == null && pattern instanceof ConstructorPattern) {
          conClauseData = clauseData;
        }
      }

      assert conClauseData != null;
      ConstructorPattern someConPattern = (ConstructorPattern) conClauseData.patterns.get(index);
      List<ConCallExpression> conCalls = null;
      List<Constructor> constructors;
      if (someConPattern.getConstructor().getDataType().hasIndexedConstructors()) {
        DataCallExpression dataCall = new GetTypeVisitor().visitConCall(new SubstVisitor(conClauseData.substitution, LevelSubstitution.EMPTY).visitConCall(someConPattern.toExpression(), null), null);
        conCalls = dataCall.getMatchedConstructors();
        if (conCalls == null) {
          myVisitor.getErrorReporter().report(new LocalTypeCheckingError("Elimination is not possible here, cannot determine the set of eligible constructors", conClauseData.clause));
          return null;
        }
        constructors = conCalls.stream().map(ConCallExpression::getDefinition).collect(Collectors.toList());
      } else {
        constructors = someConPattern.getConstructor().getDataType().getConstructors();
      }

      boolean hasVars = false;
      Map<Constructor, List<ClauseData>> constructorMap = new LinkedHashMap<>();
      for (ClauseData clauseData : clauseDataList) {
        if (clauseData.patterns.get(index) instanceof BindingPattern) {
          hasVars = true;
          for (Constructor constructor : constructors) {
            constructorMap.computeIfAbsent(constructor, k -> new ArrayList<>()).add(clauseData);
          }
        } else {
          constructorMap.computeIfAbsent(((ConstructorPattern) clauseData.patterns.get(index)).getConstructor(), k -> new ArrayList<>()).add(clauseData);
        }
      }

      if (!hasVars && constructors.size() > constructorMap.size()) {
        for (Constructor constructor : constructors) {
          if (!constructorMap.containsKey(constructor)) {
            if (constructor == Prelude.PROP_TRUNC_PATH_CON) {
              Sort sort = myExpectedType.getType().toSort();
              if (sort != null && sort.isProp()) {
                continue;
              }
            } else if (constructor == Prelude.SET_TRUNC_PATH_CON) {
              Sort sort = myExpectedType.getType().toSort();
              if (sort != null && sort.isSet()) {
                continue;
              }
            }

            myContext.push(new CoverageChecking.ConstructorClauseElem(constructor));
            addMissingClause(new ArrayList<>(myContext));
            myContext.pop();
          }
        }
      }

      Map<BranchElimTree.Pattern, ElimTree> children = new HashMap<>();
      for (Map.Entry<Constructor, List<ClauseData>> entry : constructorMap.entrySet()) {
        List<ClauseData> conClauseDataList = entry.getValue();
        myContext.push(new CoverageChecking.ConstructorClauseElem(entry.getKey()));

        for (int i = 0; i < conClauseDataList.size(); i++) {
          List<Pattern> patterns = new ArrayList<>();
          List<Pattern> oldPatterns = conClauseDataList.get(i).patterns;
          if (oldPatterns.get(index) instanceof ConstructorPattern) {
            patterns.addAll(((ConstructorPattern) oldPatterns.get(index)).getPatterns());
          } else {
            DependentLink conParameters = DependentLink.Helper.subst(entry.getKey().getParameters(), new ExprSubstitution());
            for (DependentLink link = conParameters; link.hasNext(); link = link.getNext()) {
              patterns.add(new BindingPattern(link));
            }

            Expression substExpr;
            List<Expression> arguments = new ArrayList<>(patterns.size());
            for (DependentLink link = conParameters; link.hasNext(); link = link.getNext()) {
              arguments.add(new ReferenceExpression(link));
            }
            if (conCalls != null) {
              ConCallExpression conCall = null;
              for (ConCallExpression conCall1 : conCalls) {
                if (conCall1.getDefinition() == entry.getKey()) {
                  conCall = conCall1;
                  break;
                }
              }
              assert conCall != null;
              substExpr = new ConCallExpression(conCall.getDefinition(), conCall.getSortArgument(), conCall.getDataTypeArguments(), arguments);
            } else {
              substExpr = new ConCallExpression(entry.getKey(), someConPattern.getSortArgument(), someConPattern.getDataTypeArguments(), arguments);
            }
            conClauseDataList.get(i).substitution.add(((BindingPattern) oldPatterns.get(index)).getBinding(), substExpr);
          }
          patterns.addAll(oldPatterns.subList(index + 1, oldPatterns.size()));
          conClauseDataList.set(i, new ClauseData(patterns, conClauseDataList.get(i).expression, conClauseDataList.get(i).substitution, conClauseDataList.get(i).clause));
        }

        ElimTree elimTree = clausesToElimTree(conClauseDataList);
        if (elimTree == null) {
          myOK = false;
        } else {
          children.put(entry.getKey(), elimTree);
        }

        myContext.pop();
      }

      if (hasVars && constructors.size() > constructorMap.size()) {
        List<ClauseData> varClauseDataList = new ArrayList<>();
        for (ClauseData clauseData : clauseDataList) {
          if (clauseData.patterns.get(index) instanceof BindingPattern) {
            varClauseDataList.add(clauseData);
            clauseData.substitution.remove(((BindingPattern) clauseData.patterns.get(index)).getBinding());
          }
        }

        myContext.push(new CoverageChecking.PatternClauseElem(varClauseDataList.get(0).patterns.get(index)));
        ElimTree elimTree = clausesToElimTree(varClauseDataList);
        if (elimTree == null) {
          myOK = false;
        } else {
          children.put(BranchElimTree.Pattern.ANY, elimTree);
        }
        myContext.pop();
      }

      return new BranchElimTree(vars, children);
    }
  }

  private void addMissingClause(List<CoverageChecking.ClauseElem> clause) {
    myOK = false;
    if (myMissingClauses == null) {
      myMissingClauses = new ArrayList<>(MISSING_CLAUSES_LIST_SIZE);
    }
    if (myMissingClauses.size() == MISSING_CLAUSES_LIST_SIZE) {
      myMissingClauses.set(MISSING_CLAUSES_LIST_SIZE - 1, null);
    } else {
      myMissingClauses.add(clause);
    }
  }
}