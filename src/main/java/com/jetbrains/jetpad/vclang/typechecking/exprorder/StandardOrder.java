package com.jetbrains.jetpad.vclang.typechecking.exprorder;

import com.jetbrains.jetpad.vclang.term.Preprelude;
import com.jetbrains.jetpad.vclang.term.definition.TypeUniverse;
import com.jetbrains.jetpad.vclang.term.expr.*;
import com.jetbrains.jetpad.vclang.term.expr.visitor.CompareVisitor;
import com.jetbrains.jetpad.vclang.term.expr.visitor.NormalizeVisitor;
import com.jetbrains.jetpad.vclang.typechecking.implicitargs.equations.Equations;

public class StandardOrder implements ExpressionOrder {

  @Override
  public boolean compare(Expression expr1, Expression expr2, CompareVisitor visitor, Equations.CMP expectedCMP) {
    if(CNatOrder.compareCNat(expr1, expr2, visitor, expectedCMP) || LvlOrder.compareLvl(expr1, expr2, visitor, expectedCMP) ||
            LevelOrder.compareLevel(expr1, expr2, visitor, expectedCMP) || NatOrder.compareNat(expr1, expr2, visitor, expectedCMP)) return true;
    UniverseExpression uni1 = expr1.toUniverse();
    UniverseExpression uni2 = expr2.toUniverse();
    return uni1 != null && uni2 != null && uni1.getUniverse().compare(uni2.getUniverse(), visitor, expectedCMP);
  }

  @Override
  public Expression max(Expression expr1, Expression expr2) {
    Expression type1 = expr1.getType().normalize(NormalizeVisitor.Mode.NF);
    Expression type2 = expr2.getType().normalize(NormalizeVisitor.Mode.NF);
    DataCallExpression dataCall1 = type1.toDataCall();
    DataCallExpression dataCall2 = type2.toDataCall();

    if (dataCall1 != null) {
      if (dataCall2 == null) {
        return null;
      }

      if (dataCall1.getDefinition() == Preprelude.CNAT && dataCall2.getDefinition() == Preprelude.CNAT) {
        return CNatOrder.maxCNat(expr1, expr2);
      }

      if (dataCall1.getDefinition() == Preprelude.LVL && dataCall2.getDefinition() == Preprelude.LVL) {
        return LvlOrder.maxLvl(expr1, expr2);
      }
    }

    ClassCallExpression classCall1 = type1.toClassCall();
    ClassCallExpression classCall2 = type2.toClassCall();

    if (classCall1 != null) {
      if (classCall2 == null) {
        return null;
      }
      if (classCall1.getDefinition() == Preprelude.LEVEL && classCall2.getDefinition() == Preprelude.LEVEL) {
        return LevelOrder.maxLevel(expr1, expr2);
      }
    }

    UniverseExpression uni1 = type1.toUniverse();
    UniverseExpression uni2 = type2.toUniverse();

    if (uni1 == null || uni2 == null || !(uni1.getUniverse() instanceof TypeUniverse) || !(uni2.getUniverse() instanceof TypeUniverse)) {
      return null;
    }

    TypeUniverse.TypeLevel level1 = ((TypeUniverse) uni1.getUniverse()).getLevel();
    TypeUniverse.TypeLevel level2 = ((TypeUniverse) uni2.getUniverse()).getLevel();

    if (level1 == null || level2 == null) {
      return null;
    }

    return ExpressionFactory.Universe(level1.max(level2).getValue());
  }
}