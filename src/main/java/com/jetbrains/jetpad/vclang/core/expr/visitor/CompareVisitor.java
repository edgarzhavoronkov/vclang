package com.jetbrains.jetpad.vclang.core.expr.visitor;

import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.context.binding.inference.InferenceVariable;
import com.jetbrains.jetpad.vclang.core.context.param.DependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.SingleDependentLink;
import com.jetbrains.jetpad.vclang.core.context.param.TypedSingleDependentLink;
import com.jetbrains.jetpad.vclang.core.definition.ClassDefinition;
import com.jetbrains.jetpad.vclang.core.definition.ClassField;
import com.jetbrains.jetpad.vclang.core.definition.Constructor;
import com.jetbrains.jetpad.vclang.core.elimtree.BranchElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.ElimTree;
import com.jetbrains.jetpad.vclang.core.elimtree.LeafElimTree;
import com.jetbrains.jetpad.vclang.core.expr.*;
import com.jetbrains.jetpad.vclang.core.sort.Sort;
import com.jetbrains.jetpad.vclang.core.subst.ExprSubstitution;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Prelude;
import com.jetbrains.jetpad.vclang.typechecking.error.local.GoalError;
import com.jetbrains.jetpad.vclang.typechecking.implicitargs.equations.DummyEquations;
import com.jetbrains.jetpad.vclang.typechecking.implicitargs.equations.Equations;

import java.util.*;

import static com.jetbrains.jetpad.vclang.core.expr.ExpressionFactory.FieldCall;

public class CompareVisitor extends BaseExpressionVisitor<Expression, Boolean> {
  private final Map<Binding, Binding> mySubstitution;
  private Equations myEquations;
  private final Abstract.SourceNode mySourceNode;
  private Equations.CMP myCMP;

  private CompareVisitor(Equations equations, Equations.CMP cmp, Abstract.SourceNode sourceNode) {
    mySubstitution = new HashMap<>();
    myEquations = equations;
    mySourceNode = sourceNode;
    myCMP = cmp;
  }

  public static boolean compare(Equations equations, Equations.CMP cmp, Expression expr1, Expression expr2, Abstract.SourceNode sourceNode) {
    return new CompareVisitor(equations, cmp, sourceNode).compare(expr1, expr2);
  }

  public static boolean compare(Equations equations, ElimTree tree1, ElimTree tree2, Abstract.SourceNode sourceNode) {
    return new CompareVisitor(equations, Equations.CMP.EQ, sourceNode).compare(tree1, tree2);
  }

  private Boolean compare(ElimTree elimTree1, ElimTree elimTree2) {
    if (elimTree1 == elimTree2) {
      return true;
    }
    if (!compareParameters(DependentLink.Helper.toList(elimTree1.getParameters()), DependentLink.Helper.toList(elimTree2.getParameters()))) {
      return false;
    }

    boolean ok = false;
    if (elimTree1 instanceof LeafElimTree && elimTree2 instanceof LeafElimTree) {
      ok = compare(((LeafElimTree) elimTree1).getExpression(), ((LeafElimTree) elimTree2).getExpression());
    } else
    if (elimTree1 instanceof BranchElimTree && elimTree2 instanceof BranchElimTree) {
      BranchElimTree branchElimTree1 = (BranchElimTree) elimTree1;
      BranchElimTree branchElimTree2 = (BranchElimTree) elimTree2;
      if (branchElimTree1.getChildren().size() == branchElimTree2.getChildren().size()) {
        ok = true;
        for (Map.Entry<Constructor, ElimTree> entry : branchElimTree1.getChildren()) {
          ElimTree elimTree = branchElimTree2.getChild(entry.getKey());
          if (elimTree == null) {
            ok = false;
            break;
          }
          ok = compare(entry.getValue(), elimTree);
          if (!ok) {
            break;
          }
        }
      }
    }

    for (DependentLink link = elimTree2.getParameters(); link.hasNext(); link = link.getNext()) {
      mySubstitution.remove(link);
    }
    return ok;
  }

  private LetClause getLetClause(Expression expr) {
    while (expr.isInstance(AppExpression.class)) {
      expr = expr.cast(AppExpression.class).getFunction();
    }
    if (expr.isInstance(ReferenceExpression.class)) {
      Binding binding = expr.cast(ReferenceExpression.class).getBinding();
      return binding instanceof LetClause ? (LetClause) binding : null;
    }
    return null;
  }

  private Boolean compare(Expression expr1, Expression expr2) {
    Equations.CMP origCMP = myCMP;

    LetClause letClause1 = getLetClause(expr1);
    if (letClause1 != null && letClause1 == getLetClause(expr2) ||
           expr1.isInstance(FunCallExpression.class) && expr2.isInstance(FunCallExpression.class) && expr1.cast(FunCallExpression.class).getDefinition() == expr2.cast(FunCallExpression.class).getDefinition()) {
      myCMP = Equations.CMP.EQ;
      Equations equations = myEquations;
      myEquations = DummyEquations.getInstance();

      boolean ok;
      if (letClause1 == null) {
        ok = visitDefCall(expr1.cast(FunCallExpression.class), expr2.cast(FunCallExpression.class));
      } else {
        List<Expression> args1 = new ArrayList<>();
        while (expr1.isInstance(AppExpression.class)) {
          args1.add(expr1.cast(AppExpression.class).getArgument());
          expr1 = expr1.cast(AppExpression.class).getFunction();
        }
        List<Expression> args2 = new ArrayList<>();
        while (expr2.isInstance(AppExpression.class)) {
          args2.add(expr2.cast(AppExpression.class).getArgument());
          expr2 = expr2.cast(AppExpression.class).getFunction();
        }
        if (args1.size() == args2.size()) {
          ok = true;
          for (int i = 0; i < args1.size(); i++) {
            if (compare(args1.get(i), args2.get(i))) {
              ok = false;
              break;
            }
          }
        } else {
          ok = false;
        }
      }

      myEquations = equations;
      myCMP = origCMP;
      if (ok) {
        return true;
      }
    }

    expr1 = expr1.normalize(NormalizeVisitor.Mode.WHNF);
    expr2 = expr2.normalize(NormalizeVisitor.Mode.WHNF);

    Expression stuck1 = expr1.getStuckExpression();
    Expression stuck2 = expr2.getStuckExpression();
    if (stuck1 != null && stuck1.isError() && (stuck2 == null || !stuck2.isInstance(InferenceReferenceExpression.class)) ||
      stuck2 != null && stuck2.isError() && (stuck1 == null || !stuck1.isInstance(InferenceReferenceExpression.class))) {
      return true;
    }

    if (expr1.isInstance(InferenceReferenceExpression.class)) {
      InferenceVariable variable = expr1.cast(InferenceReferenceExpression.class).getVariable();
      return myEquations.add(expr1, expr2, myCMP, variable.getSourceNode(), variable);
    }
    if (expr2.isInstance(InferenceReferenceExpression.class)) {
      InferenceVariable variable = expr2.cast(InferenceReferenceExpression.class).getVariable();
      return myEquations.add(expr1, expr2.subst(getSubstitution()), myCMP, variable.getSourceNode(), variable);
    }

    if (!expr1.isInstance(UniverseExpression.class) && !expr1.isInstance(PiExpression.class) && !expr1.isInstance(ClassCallExpression.class) && !expr1.isInstance(DataCallExpression.class) && !expr1.isInstance(AppExpression.class) && !expr1.isInstance(SigmaExpression.class)) {
      myCMP = Equations.CMP.EQ;
    }

    boolean ok;
    if (expr2.isInstance(ConCallExpression.class) && expr2.cast(ConCallExpression.class).getDefinition() == Prelude.PATH_CON) {
      ok = visitDefCall(expr2.cast(ConCallExpression.class), expr1, false);
    } else
    if (expr2.isInstance(LamExpression.class)) {
      ok = visitLam(expr2.cast(LamExpression.class), expr1, false);
    } else
    if (expr2.isInstance(TupleExpression.class)) {
      ok = visitTuple(expr2.cast(TupleExpression.class), expr1, false);
    } else {
      Expression type1 = expr1.getType();
      if (type1 != null && type1.isInstance(ClassCallExpression.class) && type1.cast(ClassCallExpression.class).isUnit()) {
        ok = compareUnit(type1.cast(ClassCallExpression.class), expr2, true);
      } else {
        Expression type2 = expr2.getType();
        if (type2 != null && type2.isInstance(ClassCallExpression.class) && type2.cast(ClassCallExpression.class).isUnit()) {
          ok = compareUnit(type2.cast(ClassCallExpression.class), expr1, false);
        } else {
          ok = expr1.accept(this, expr2);
        }
      }
    }
    if (ok) {
      return true;
    }

    InferenceVariable variable;
    if (stuck1 != null && stuck1.isInstance(InferenceReferenceExpression.class)) {
      variable = stuck1.cast(InferenceReferenceExpression.class).getVariable();
    } else
    if (stuck2 != null && stuck2.isInstance(InferenceReferenceExpression.class)) {
      variable = stuck2.cast(InferenceReferenceExpression.class).getVariable();
    } else {
      return false;
    }

    return myEquations.add(expr1, expr2.subst(getSubstitution()), origCMP, variable.getSourceNode(), variable);
  }

  private Boolean compareUnit(ClassCallExpression type1, Expression expr2, boolean correctOrder) {
    for (Map.Entry<ClassField, Expression> entry : type1.getImplementedHere().entrySet()) {
      if (correctOrder ? !compare(entry.getValue(), FieldCall(entry.getKey(), expr2)) : !compare(FieldCall(entry.getKey(), expr2), entry.getValue())) {
        return false;
      }
    }

    if (expr2.isInstance(NewExpression.class) && expr2.cast(NewExpression.class).getType().getDefinition().isSubClassOf(type1.getDefinition())) {
      return true;
    }

    for (Map.Entry<ClassField, ClassDefinition.Implementation> entry : type1.getDefinition().getImplemented()) {
      if (correctOrder ? !compare(entry.getValue().term, FieldCall(entry.getKey(), expr2)) : !compare(FieldCall(entry.getKey(), expr2), entry.getValue().term)) {
        return false;
      }
    }

    return true;
  }

  private boolean checkIsInferVar(Expression fun, Expression expr1, Expression expr2) {
    InferenceReferenceExpression ref = fun.checkedCast(InferenceReferenceExpression.class);
    InferenceVariable binding = ref != null && ref.getSubstExpression() == null ? ref.getVariable() : null;
    return binding != null && myEquations.add(expr1, expr2.subst(getSubstitution()), myCMP, binding.getSourceNode(), binding);
  }

  private ExprSubstitution getSubstitution() {
    ExprSubstitution substitution = new ExprSubstitution();
    for (Map.Entry<Binding, Binding> entry : mySubstitution.entrySet()) {
      substitution.add(entry.getKey(), new ReferenceExpression(entry.getValue()));
    }
    return substitution;
  }

  @Override
  public Boolean visitApp(AppExpression expr1, Expression expr2) {
    List<Expression> args1 = new ArrayList<>();
    Expression fun1 = expr1;
    while (fun1.isInstance(AppExpression.class)) {
      args1.add(fun1.cast(AppExpression.class).getArgument());
      fun1 = fun1.cast(AppExpression.class).getFunction();
    }
    if (checkIsInferVar(fun1, expr1, expr2)) {
      return true;
    }

    List<Expression> args2 = new ArrayList<>();
    Expression fun2 = expr2;
    while (fun2.isInstance(AppExpression.class)) {
      args2.add(fun2.cast(AppExpression.class).getArgument());
      fun2 = fun2.cast(AppExpression.class).getFunction();
    }
    if (checkIsInferVar(fun2, expr1, expr2)) {
      return true;
    }

    if (args1.size() != args2.size()) {
      return false;
    }

    if (!compare(fun1, fun2)) {
      return false;
    }
    Collections.reverse(args1);
    Collections.reverse(args2);

    myCMP = Equations.CMP.EQ;
    for (int i = 0; i < args1.size(); i++) {
      if (!compare(args1.get(i), args2.get(i))) {
        return false;
      }
    }

    return true;
  }

  private Boolean comparePathEta(ConCallExpression conCall1, Expression expr2, boolean correctOrder) {
    SingleDependentLink param = new TypedSingleDependentLink(true, "i", ExpressionFactory.Interval());
    List<Expression> args = new ArrayList<>(5);
    for (Expression arg : conCall1.getDataTypeArguments()) {
      args.add(correctOrder ? arg : arg.subst(getSubstitution()));
    }
    args.add(expr2);
    args.add(new ReferenceExpression(param));
    expr2 = new LamExpression(conCall1.getSortArgument(), param, new FunCallExpression(Prelude.AT, conCall1.getSortArgument(), args));
    return correctOrder ? compare(conCall1.getDefCallArguments().get(0), expr2) : compare(expr2, conCall1.getDefCallArguments().get(0));
  }

  private Boolean visitDefCall(DefCallExpression expr1, Expression expr2, boolean correctOrder) {
    if (expr1.getDefinition() == Prelude.PATH_CON && !expr2.isInstance(ConCallExpression.class)) {
      return comparePathEta((ConCallExpression) expr1, expr2, correctOrder);
    }

    DefCallExpression defCall2 = expr2.checkedCast(DefCallExpression.class);
    if (defCall2 == null || expr1.getDefinition() != defCall2.getDefinition()) {
      return false;
    }
    for (int i = 0; i < expr1.getDefCallArguments().size(); i++) {
      if (correctOrder ? !compare(expr1.getDefCallArguments().get(i), defCall2.getDefCallArguments().get(i)) : !compare(defCall2.getDefCallArguments().get(i), expr1.getDefCallArguments().get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitDefCall(DefCallExpression expr1, Expression expr2) {
    return visitDefCall(expr1, expr2, true);
  }

  @Override
  public Boolean visitDataCall(DataCallExpression dataCall1, Expression expr2) {
    DataCallExpression dataCall2 = expr2.checkedCast(DataCallExpression.class);
    if (dataCall2 == null || dataCall1.getDefinition() != dataCall2.getDefinition()) {
      return false;
    }

    Equations.CMP origCMP = myCMP;
    for (int i = 0; i < dataCall1.getDefCallArguments().size(); i++) {
      myCMP = dataCall1.getDefinition().isCovariant(i) ? origCMP : Equations.CMP.EQ;
      if (!compare(dataCall1.getDefCallArguments().get(i), dataCall2.getDefCallArguments().get(i))) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitFieldCall(FieldCallExpression fieldCall1, Expression expr2) {
    FieldCallExpression fieldCall2 = expr2.checkedCast(FieldCallExpression.class);
    if (fieldCall2 == null || fieldCall1.getDefinition() != fieldCall2.getDefinition()) {
      return false;
    }

    InferenceVariable variable = null;
    InferenceReferenceExpression ref1 = fieldCall1.getExpression().checkedCast(InferenceReferenceExpression.class);
    if (ref1 != null && ref1.getSubstExpression() == null) {
      variable = ref1.getVariable();
    } else {
      InferenceReferenceExpression ref2 = fieldCall2.getExpression().checkedCast(InferenceReferenceExpression.class);
      if (ref2 != null && ref2.getSubstExpression() == null) {
        variable = ref2.getVariable();
      }
    }
    if (variable != null) {
      return myEquations.add(fieldCall1, fieldCall2.subst(getSubstitution()), Equations.CMP.EQ, variable.getSourceNode(), variable);
    }

    return compare(fieldCall1.getExpression(), fieldCall2.getExpression());
  }

  private boolean checkSubclassImpl(ClassCallExpression classCall1, ClassCallExpression classCall2) {
    Equations.CMP origCMP = myCMP;
    myCMP = Equations.CMP.EQ;
    for (Map.Entry<ClassField, Expression> entry : classCall2.getImplementedHere().entrySet()) {
      Expression impl1 = classCall1.getImplementationHere(entry.getKey());
      if (impl1 == null) {
        myCMP = origCMP;
        return false;
      }
      if (!compare(impl1, entry.getValue())) {
        myCMP = origCMP;
        return false;
      }
    }
    myCMP = origCMP;
    return true;
  }

  @Override
  public Boolean visitClassCall(ClassCallExpression expr1, Expression expr2) {
    if (!expr2.isInstance(ClassCallExpression.class)) {
      return false;
    }
    ClassCallExpression classCall2 = expr2.cast(ClassCallExpression.class);

    boolean subclass2of1Test = myCMP.equals(Equations.CMP.LE) || classCall2.getDefinition().isSubClassOf(expr1.getDefinition());
    boolean subclass1of2Test = myCMP.equals(Equations.CMP.GE) || expr1.getDefinition().isSubClassOf(classCall2.getDefinition());
    if (!(subclass1of2Test && subclass2of1Test)) {
      return false;
    }

    boolean implAllOf1Test = myCMP.equals(Equations.CMP.LE) || checkSubclassImpl(classCall2, expr1);
    boolean implAllOf2Test = myCMP.equals(Equations.CMP.GE) || checkSubclassImpl(expr1, classCall2);
    return implAllOf1Test && implAllOf2Test;
  }

  @Override
  public Boolean visitReference(ReferenceExpression expr1, Expression expr2) {
    ReferenceExpression ref2 = expr2.checkedCast(ReferenceExpression.class);
    if (ref2 == null) {
      return false;
    }

    Binding binding2 = ref2.getBinding();
    Binding subst2 = mySubstitution.get(binding2);
    if (subst2 != null) {
      binding2 = subst2;
    }
    return binding2 == expr1.getBinding();
  }

  @Override
  public Boolean visitInferenceReference(InferenceReferenceExpression expr1, Expression expr2) {
    return false;
  }

  private Boolean visitLam(LamExpression expr1, Expression expr2, boolean correctOrder) {
    List<DependentLink> params1 = new ArrayList<>();
    List<DependentLink> params2 = new ArrayList<>();
    Expression body1 = expr1.getLamParameters(params1);
    Expression body2 = expr2.getLamParameters(params2);

    for (int i = 0; i < params1.size() && i < params2.size(); i++) {
      mySubstitution.put(correctOrder ? params2.get(i) : params1.get(i), correctOrder ? params1.get(i) : params2.get(i));
    }

    if (params1.size() < params2.size()) {
      for (int i = params1.size(); i < params2.size(); i++) {
        body1 = new AppExpression(body1, new ReferenceExpression(params2.get(i)));
      }
    }
    if (params2.size() < params1.size()) {
      for (int i = params2.size(); i < params1.size(); i++) {
        body2 = new AppExpression(body2, new ReferenceExpression(params1.get(i)));
      }
    }

    Boolean result = compare(correctOrder ? body1 : body2, correctOrder ? body2 : body1);
    for (int i = 0; i < params1.size() && i < params2.size(); i++) {
      mySubstitution.remove(correctOrder ? params2.get(i) : params1.get(i), correctOrder ? params1.get(i) : params2.get(i));
    }
    return result;
  }

  @Override
  public Boolean visitLam(LamExpression expr1, Expression expr2) {
    return visitLam(expr1, expr2, true);
  }

  @Override
  public Boolean visitPi(PiExpression expr1, Expression expr2) {
    if (!expr2.isInstance(PiExpression.class)) {
      return false;
    }
    PiExpression piExpr2 = expr2.cast(PiExpression.class);

    Equations.CMP origCMP = myCMP;
    myCMP = Equations.CMP.EQ;
    if (!compare(expr1.getParameters().getTypeExpr(), piExpr2.getParameters().getTypeExpr())) {
      return false;
    }

    SingleDependentLink link1 = expr1.getParameters(), link2 = piExpr2.getParameters();
    for (; link1.hasNext() && link2.hasNext(); link1 = link1.getNext(), link2 = link2.getNext()) {
      mySubstitution.put(link2, link1);
    }

    myCMP = origCMP;
    if (!compare(link1.hasNext() ? new PiExpression(expr1.getResultSort(), link1, expr1.getCodomain()) : expr1.getCodomain(), link2.hasNext() ? new PiExpression(piExpr2.getResultSort(), link2, piExpr2.getCodomain()) : piExpr2.getCodomain())) {
      return false;
    }

    for (DependentLink link = piExpr2.getParameters(); link != link2; link = link.getNext()) {
      mySubstitution.remove(link);
    }
    mySubstitution.remove(link2);
    return true;
  }

  private boolean compareParameters(List<DependentLink> params1, List<DependentLink> params2) {
    if (params1.size() != params2.size()) {
      return false;
    }

    Equations.CMP origCMP = myCMP;
    for (int i = 0; i < params1.size() && i < params2.size(); ++i) {
      if (!compare(params1.get(i).getTypeExpr(), params2.get(i).getTypeExpr())) {
        return false;
      }
      mySubstitution.put(params2.get(i), params1.get(i));
      myCMP = origCMP;
    }

    return true;
  }


  @Override
  public Boolean visitUniverse(UniverseExpression expr1, Expression expr2) {
    return expr2.isInstance(UniverseExpression.class) && Sort.compare(expr1.getSort(), expr2.cast(UniverseExpression.class).getSort(), myCMP, myEquations, mySourceNode);
  }

  @Override
  public Boolean visitError(ErrorExpression expr1, Expression expr2) {
    return expr1.getError() instanceof GoalError && expr2.isInstance(ErrorExpression.class) && expr1.getError().equals(expr2.cast(ErrorExpression.class).getError());
  }

  @Override
  public Boolean visitTuple(TupleExpression expr1, Expression expr2) {
    return visitTuple(expr1, expr2, true);
  }

  private Boolean visitTuple(TupleExpression expr1, Expression expr2, boolean correctOrder) {
    Boolean ok;
    if (expr2.isInstance(TupleExpression.class)) {
      TupleExpression tuple2 = expr2.cast(TupleExpression.class);
      if (expr1.getFields().size() != tuple2.getFields().size()) {
        ok = false;
      } else {
        ok = true;
        for (int i = 0; i < expr1.getFields().size(); i++) {
          if (correctOrder ? !compare(expr1.getFields().get(i), tuple2.getFields().get(i)) : !compare(tuple2.getFields().get(i), expr1.getFields().get(i))) {
            ok = false;
            break;
          }
        }
      }
    } else {
      ok = compareTupleEta(expr1, expr2, correctOrder);
    }

    return ok;
  }

  private Boolean compareTupleEta(TupleExpression tuple1, Expression expr2, boolean correctOrder) {
    int i = 0;
    for (Expression field : tuple1.getFields()) {
      if (correctOrder ? !compare(field, ProjExpression.make(expr2, i++)) : !compare(ProjExpression.make(expr2, i++), field)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Boolean visitSigma(SigmaExpression expr1, Expression expr2) {
    SigmaExpression sigma2 = expr2.checkedCast(SigmaExpression.class);
    if (sigma2 == null) {
      return false;
    }
    if (!compareParameters(DependentLink.Helper.toList(expr1.getParameters()), DependentLink.Helper.toList(sigma2.getParameters()))) {
      return false;
    }
    for (DependentLink link = sigma2.getParameters(); link.hasNext(); link = link.getNext()) {
      mySubstitution.remove(link);
    }
    return true;
  }

  @Override
  public Boolean visitProj(ProjExpression expr1, Expression expr2) {
    if (!expr2.isInstance(ProjExpression.class)) {
      return false;
    }
    ProjExpression proj2 = expr2.cast(ProjExpression.class);
    return expr1.getField() == proj2.getField() && compare(expr1.getExpression(), proj2.getExpression());
  }

  @Override
  public Boolean visitNew(NewExpression expr1, Expression expr2) {
    return false;
  }

  @Override
  public Boolean visitLet(LetExpression letExpr1, Expression expr2) {
    throw new IllegalStateException();
  }

  @Override
  public Boolean visitCase(CaseExpression case1, Expression expr2) {
    if (!expr2.isInstance(CaseExpression.class)) {
      return false;
    }
    CaseExpression case2 = expr2.cast(CaseExpression.class);

    if (case1.getArguments().size() != case2.getArguments().size()) {
      return false;
    }

    for (int i = 0; i < case1.getArguments().size(); i++) {
      if (!compare(case1.getArguments().get(i), case2.getArguments().get(i))) {
        return false;
      }
    }

    return compare(case1.getElimTree(), case2.getElimTree());
  }

  @Override
  public Boolean visitOfType(OfTypeExpression expr, Expression params) {
    return expr.getExpression().accept(this, params);
  }
}
