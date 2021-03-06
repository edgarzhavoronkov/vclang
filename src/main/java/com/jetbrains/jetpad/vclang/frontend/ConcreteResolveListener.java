package com.jetbrains.jetpad.vclang.frontend;

import com.jetbrains.jetpad.vclang.frontend.resolving.OpenCommand;
import com.jetbrains.jetpad.vclang.frontend.resolving.ResolveListener;
import com.jetbrains.jetpad.vclang.term.Abstract;

import java.util.Collections;

public class ConcreteResolveListener implements ResolveListener {

  public ConcreteResolveListener() {
  }

  @Override
  public void nameResolved(Abstract.ReferenceExpression referenceExpression, Abstract.ReferableSourceNode referable) {
    ((Concrete.ReferenceExpression) referenceExpression).setResolvedReferent(referable);
  }

  @Override
  public void moduleResolved(Abstract.ModuleCallExpression moduleCallExpression, Abstract.Definition module) {
    ((Concrete.ModuleCallExpression) moduleCallExpression).setModule(module);
  }

  @Override
  public void openCmdResolved(OpenCommand openCmd, Abstract.Definition definition) {
    ((Concrete.NamespaceCommandStatement) openCmd).setResolvedClass(definition);
  }

  @Override
  public void implementResolved(Abstract.Implementation implementDef, Abstract.ClassField definition) {
    ((Concrete.Implementation) implementDef).setImplemented(definition);
  }

  @Override
  public void implementResolved(Abstract.ClassFieldImpl implementStmt, Abstract.ClassField definition) {
    ((Concrete.ClassFieldImpl) implementStmt).setImplementedField(definition);
  }

  @Override
  public void classViewResolved(Abstract.ClassView classView, Abstract.ClassField classifyingField) {
    ((Concrete.ClassView) classView).setClassifyingField(classifyingField);
  }

  @Override
  public void classViewFieldResolved(Abstract.ClassViewField classViewField, Abstract.ClassField definition) {
    ((Concrete.ClassViewField) classViewField).setUnderlyingField(definition);
  }

  @Override
  public void classViewInstanceResolved(Abstract.ClassViewInstance instance, Abstract.Definition classifyingDefinition) {
    ((Concrete.ClassViewInstance) instance).setClassifyingDefinition(classifyingDefinition);
  }

  @Override
  public Abstract.BinOpExpression makeBinOp(Abstract.BinOpSequenceExpression binOpExpr, Abstract.Expression left, Abstract.ReferableSourceNode binOp, Abstract.ReferenceExpression var, Abstract.Expression right) {
    return ((Concrete.BinOpSequenceExpression) binOpExpr).makeBinOp(left, binOp, var, right);
  }

  @Override
  public Abstract.Expression makeError(Abstract.BinOpSequenceExpression binOpExpr, Abstract.SourceNode node) {
    return ((Concrete.BinOpSequenceExpression) binOpExpr).makeError(node);
  }

  @Override
  public void replaceBinOp(Abstract.BinOpSequenceExpression binOpExpr, Abstract.Expression expression) {
    ((Concrete.BinOpSequenceExpression) binOpExpr).replace(expression);
  }

  @Override
  public void replaceWithConstructor(Abstract.PatternContainer container, int index, Abstract.Constructor constructor) {
    Concrete.PatternContainer concreteContainer = (Concrete.PatternContainer) container;
    //noinspection ConstantConditions
    Concrete.Pattern old = concreteContainer.getPatterns().get(index);
    Concrete.Pattern newPattern = new Concrete.ConstructorPattern(old.getPosition(), constructor, Collections.emptyList());
    newPattern.setExplicit(old.isExplicit());
    concreteContainer.getPatterns().set(index, newPattern);
  }

  @Override
  public void patternResolved(Abstract.ConstructorPattern pattern, Abstract.Constructor definition) {
    ((Concrete.ConstructorPattern) pattern).setConstructor(definition);
  }
}
