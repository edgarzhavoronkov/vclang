package com.jetbrains.jetpad.vclang.term.expr;

import com.google.common.base.Objects;
import com.jetbrains.jetpad.vclang.frontend.text.Position;
import com.jetbrains.jetpad.vclang.naming.reference.Referable;
import com.jetbrains.jetpad.vclang.term.Concrete;
import com.jetbrains.jetpad.vclang.term.ConcreteExpressionVisitor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ConcreteCompareVisitor implements ConcreteExpressionVisitor<Position, Concrete.Expression<Position>, Boolean> {
  private final Map<Referable, Referable> mySubstitution = new HashMap<>();

  @Override
  public Boolean visitApp(Concrete.AppExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.AppExpression && expr1.getFunction().accept(this, ((Concrete.AppExpression<Position>) expr2).getFunction()) && expr1.getArgument().getExpression().accept(this, ((Concrete.AppExpression<Position>) expr2).getArgument().getExpression());
  }

  @Override
  public Boolean visitReference(Concrete.ReferenceExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    if (!(expr2 instanceof Concrete.ReferenceExpression)) return false;
    Concrete.ReferenceExpression defCallExpr2 = (Concrete.ReferenceExpression) expr2;
    Referable ref1 = mySubstitution.get(expr1.getReferent());
    if (ref1 == null) {
      ref1 = expr1.getReferent();
    }
    return ref1 == null ? defCallExpr2.getReferent() == null : ref1.equals(defCallExpr2.getReferent());
  }

  @Override
  public Boolean visitInferenceReference(Concrete.InferenceReferenceExpression<Position> expr, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.InferenceReferenceExpression && expr.getVariable() == ((Concrete.InferenceReferenceExpression) expr2).getVariable();
  }

  @Override
  public Boolean visitModuleCall(Concrete.ModuleCallExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.ModuleCallExpression && expr1.getPath().equals(((Concrete.ModuleCallExpression) expr2).getPath());
  }

  private boolean compareArg(Concrete.Parameter<Position> arg1, Concrete.Parameter<Position> arg2) {
    if (arg1.getExplicit() != arg2.getExplicit()) {
      return false;
    }
    if (arg1 instanceof Concrete.TelescopeParameter && arg2 instanceof Concrete.TelescopeParameter) {
      List<? extends Referable> list1 = ((Concrete.TelescopeParameter<Position>) arg1).getReferableList();
      List<? extends Referable> list2 = ((Concrete.TelescopeParameter<Position>) arg2).getReferableList();
      if (list1.size() != list2.size()) {
        return false;
      }
      for (int i = 0; i < list1.size(); i++) {
        mySubstitution.put(list1.get(i), list2.get(i));
      }
      return ((Concrete.TelescopeParameter<Position>) arg1).getType().accept(this, ((Concrete.TelescopeParameter<Position>) arg2).getType());
    }
    if (arg1 instanceof Concrete.TypeParameter && arg2 instanceof Concrete.TypeParameter) {
      return ((Concrete.TypeParameter<Position>) arg1).getType().accept(this, ((Concrete.TypeParameter<Position>) arg2).getType());
    }
    if (arg1 instanceof Concrete.NameParameter && arg2 instanceof Concrete.NameParameter) {
      mySubstitution.put((Concrete.NameParameter) arg1, (Concrete.NameParameter) arg2);
      return true;
    }
    return false;
  }

  private boolean compareArgs(List<? extends Concrete.Parameter<Position>> args1, List<? extends Concrete.Parameter<Position>> args2) {
    if (args1.size() != args2.size()) return false;
    for (int i = 0; i < args1.size(); i++) {
      if (!compareArg(args1.get(i), args2.get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitLam(Concrete.LamExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.LamExpression && compareArgs(expr1.getParameters(), ((Concrete.LamExpression<Position>) expr2).getParameters()) && expr1.getBody().accept(this, ((Concrete.LamExpression<Position>) expr2).getBody());
  }

  @Override
  public Boolean visitPi(Concrete.PiExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.PiExpression && compareArgs(expr1.getParameters(), ((Concrete.PiExpression<Position>) expr2).getParameters()) && expr1.getCodomain().accept(this, ((Concrete.PiExpression<Position>) expr2).getCodomain());
  }

  @Override
  public Boolean visitUniverse(Concrete.UniverseExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    if (!(expr2 instanceof Concrete.UniverseExpression)) {
      return false;
    }
    Concrete.UniverseExpression<Position> uni2 = (Concrete.UniverseExpression<Position>) expr2;
    return compareLevel(expr1.getPLevel(), uni2.getPLevel()) && compareLevel(expr1.getHLevel(), uni2.getHLevel());
  }

  public boolean compareLevel(Concrete.LevelExpression level1, Concrete.LevelExpression level2) {
    if (level1 == null) {
      return level2 == null || level2 instanceof Concrete.PLevelExpression || level2 instanceof Concrete.HLevelExpression;
    }
    if (level1 instanceof Concrete.PLevelExpression) {
      return level2 instanceof Concrete.PLevelExpression || level2 == null;
    }
    if (level1 instanceof Concrete.HLevelExpression) {
      return level2 instanceof Concrete.HLevelExpression || level2 == null;
    }
    if (level1 instanceof Concrete.InfLevelExpression) {
      return level2 instanceof Concrete.InfLevelExpression;
    }
    if (level1 instanceof Concrete.NumberLevelExpression) {
      return level2 instanceof Concrete.NumberLevelExpression && ((Concrete.NumberLevelExpression) level1).getNumber() == ((Concrete.NumberLevelExpression) level2).getNumber();
    }
    if (level1 instanceof Concrete.SucLevelExpression) {
      return level2 instanceof Concrete.SucLevelExpression && compareLevel(((Concrete.SucLevelExpression) level1).getExpression(), ((Concrete.SucLevelExpression) level2).getExpression());
    }
    if (level1 instanceof Concrete.MaxLevelExpression) {
      if (!(level2 instanceof Concrete.MaxLevelExpression)) {
        return false;
      }
      Concrete.MaxLevelExpression max1 = (Concrete.MaxLevelExpression) level1;
      Concrete.MaxLevelExpression max2 = (Concrete.MaxLevelExpression) level2;
      return compareLevel(max1.getLeft(), max2.getLeft()) && compareLevel(max1.getRight(), max2.getRight());
    }
    throw new IllegalStateException();
  }

  @Override
  public Boolean visitInferHole(Concrete.InferHoleExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.InferHoleExpression;
  }

  @Override
  public Boolean visitGoal(Concrete.GoalExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.GoalExpression;
  }

  @Override
  public Boolean visitTuple(Concrete.TupleExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    if (!(expr2 instanceof Concrete.TupleExpression)) return false;
    Concrete.TupleExpression<Position> tupleExpr2 = (Concrete.TupleExpression<Position>) expr2;
    if (expr1.getFields().size() != tupleExpr2.getFields().size()) return false;
    for (int i = 0; i < expr1.getFields().size(); i++) {
      if (expr1.getFields().get(i).accept(this, tupleExpr2.getFields().get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitSigma(Concrete.SigmaExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.SigmaExpression && compareArgs(expr1.getParameters(), ((Concrete.SigmaExpression<Position>) expr2).getParameters());
  }

  @Override
  public Boolean visitBinOp(Concrete.BinOpExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    if (expr2 instanceof Concrete.BinOpSequenceExpression && ((Concrete.BinOpSequenceExpression<Position>) expr2).getSequence().isEmpty()) {
      return visitBinOp(expr1, ((Concrete.BinOpSequenceExpression<Position>) expr2).getLeft());
    }
    if (!(expr2 instanceof Concrete.BinOpExpression)) return false;
    Concrete.BinOpExpression<Position> binOpExpr2 = (Concrete.BinOpExpression<Position>) expr2;
    return expr1.getLeft().accept(this, binOpExpr2.getLeft()) && (expr1.getRight() == null && binOpExpr2.getRight() == null || expr1.getRight() != null && binOpExpr2.getRight() != null && expr1.getRight().accept(this, binOpExpr2.getRight())) && expr1.getReferent() != null && expr1.getReferent().equals(((Concrete.BinOpExpression<Position>) expr2).getReferent());
  }

  @Override
  public Boolean visitBinOpSequence(Concrete.BinOpSequenceExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    if (expr1.getSequence().isEmpty()) {
      return expr1.getLeft().accept(this, expr2);
    }
    if (!(expr2 instanceof Concrete.BinOpSequenceExpression)) return false;
    Concrete.BinOpSequenceExpression<Position> binOpExpr2 = (Concrete.BinOpSequenceExpression<Position>) expr2;
    if (!expr1.getLeft().accept(this, binOpExpr2.getLeft())) return false;
    if (expr1.getSequence().size() != binOpExpr2.getSequence().size()) return false;
    for (int i = 0; i < expr1.getSequence().size(); i++) {
      Concrete.Expression<Position> arg1 = (Concrete.Expression<Position>) expr1.getSequence().get(i).argument;
      Concrete.Expression<Position> arg2 = (Concrete.Expression<Position>) ((Concrete.BinOpSequenceExpression<Position>) expr2).getSequence().get(i).argument;
      if (!(expr1.getSequence().get(i).binOp == binOpExpr2.getSequence().get(i).binOp && (arg1 == null && arg2 == null || arg1 != null && arg2 != null && arg1.accept(this, arg2)))) {
        return false;
      }
    }
    return true;
  }

  private boolean comparePattern(Concrete.Pattern pattern1, Concrete.Pattern pattern2) {
    if (pattern1 instanceof Concrete.NamePattern) {
      return pattern2 instanceof Concrete.NamePattern && Objects.equal(pattern1, pattern2);
    }
    if (pattern1 instanceof Concrete.ConstructorPattern) {
      return pattern2 instanceof Concrete.ConstructorPattern && ((Concrete.ConstructorPattern) pattern1).getConstructorName().equals(((Concrete.ConstructorPattern) pattern2).getConstructorName());
    }
    return pattern1 instanceof Concrete.EmptyPattern && pattern2 instanceof Concrete.EmptyPattern;
  }

  private boolean compareClause(Concrete.FunctionClause<Position> clause1, Concrete.FunctionClause<Position> clause2) {
    if (!((clause1.getExpression() == null ? clause2.getExpression() == null : clause1.getExpression().accept(this, clause2.getExpression())) && clause1.getPatterns().size() == clause2.getPatterns().size())) return false;
    for (int i = 0; i < clause1.getPatterns().size(); i++) {
      if (!comparePattern(clause1.getPatterns().get(i), clause2.getPatterns().get(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean compareElimCase(Concrete.CaseExpression<Position> expr1, Concrete.CaseExpression<Position> expr2) {
    if (!(expr1.getExpressions().size() == expr2.getExpressions().size() && expr1.getClauses().size() == expr2.getClauses().size())) return false;
    for (int i = 0; i < expr1.getExpressions().size(); i++) {
      if (!expr1.getExpressions().get(i).accept(this, expr2.getExpressions().get(i))) {
        return false;
      }
    }
    for (int i = 0; i < expr1.getClauses().size(); i++) {
      if (!compareClause(expr1.getClauses().get(i), expr2.getClauses().get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitCase(Concrete.CaseExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.CaseExpression && compareElimCase(expr1, (Concrete.CaseExpression<Position>) expr2);
  }

  @Override
  public Boolean visitProj(Concrete.ProjExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.ProjExpression && expr1.getField() == ((Concrete.ProjExpression<Position>) expr2).getField() && expr1.getExpression().accept(this, ((Concrete.ProjExpression<Position>) expr2).getExpression());
  }

  private boolean compareImplementStatement(Concrete.ClassFieldImpl<Position> implStat1, Concrete.ClassFieldImpl<Position> implStat2) {
    return implStat1.getImplementation().accept(this, implStat2.getImplementation()) && implStat1.getImplementedFieldName().equals(implStat2.getImplementedFieldName());
  }

  @Override
  public Boolean visitClassExt(Concrete.ClassExtExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    if (!(expr2 instanceof Concrete.ClassExtExpression)) return false;
    Concrete.ClassExtExpression<Position> classExtExpr2 = (Concrete.ClassExtExpression<Position>) expr2;
    if (!(expr1.getBaseClassExpression().accept(this, classExtExpr2.getBaseClassExpression()) && expr1.getStatements().size() == classExtExpr2.getStatements().size())) return false;
    for (Iterator<? extends Concrete.ClassFieldImpl<Position>> it1 = expr1.getStatements().iterator(), it2 = classExtExpr2.getStatements().iterator(); it1.hasNext(); ) {
      if (!compareImplementStatement(it1.next(), it2.next())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitNew(Concrete.NewExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    return expr2 instanceof Concrete.NewExpression && expr1.getExpression().accept(this, ((Concrete.NewExpression<Position>) expr2).getExpression());
  }

  private boolean compareLetClause(Concrete.LetClause<Position> clause1, Concrete.LetClause<Position> clause2) {
    return compareArgs(clause1.getParameters(), clause2.getParameters()) && clause1.getTerm().accept(this, clause2.getTerm()) && (clause1.getResultType() == null && clause2.getResultType() == null || clause1.getResultType() != null && clause2.getResultType() != null && clause1.getResultType().accept(this, clause2.getResultType()));
  }

  @Override
  public Boolean visitLet(Concrete.LetExpression<Position> expr1, Concrete.Expression<Position> expr2) {
    if (!(expr2 instanceof Concrete.LetExpression)) return false;
    Concrete.LetExpression<Position> letExpr2 = (Concrete.LetExpression<Position>) expr2;
    if (expr1.getClauses().size() != letExpr2.getClauses().size()) {
      return false;
    }
    for (int i = 0; i < expr1.getClauses().size(); i++) {
      if (!compareLetClause(expr1.getClauses().get(i), letExpr2.getClauses().get(i))) {
        return false;
      }
      mySubstitution.put(expr1.getClauses().get(i), letExpr2.getClauses().get(i));
    }
    return expr1.getExpression().accept(this, letExpr2.getExpression());
  }

  @Override
  public Boolean visitNumericLiteral(Concrete.NumericLiteral expr1, Concrete.Expression expr2) {
    return expr2 instanceof Concrete.NumericLiteral && expr1.getNumber() == ((Concrete.NumericLiteral) expr2).getNumber();
  }
}