package com.jetbrains.jetpad.vclang.core.context.binding.inference;

import com.jetbrains.jetpad.vclang.core.context.binding.Binding;
import com.jetbrains.jetpad.vclang.core.definition.ClassField;
import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.typechecking.error.local.ArgInferenceError;
import com.jetbrains.jetpad.vclang.typechecking.error.local.LocalTypeCheckingError;
import com.jetbrains.jetpad.vclang.typechecking.typeclass.pool.ClassViewInstancePool;

import java.util.Set;

public class TypeClassInferenceVariable extends InferenceVariable {
  private final Abstract.ReferenceExpression myDefCall;
  private final int myParamIndex;
  private final Abstract.ClassView myClassView;
  private final ClassField myClassifyingField;

  public TypeClassInferenceVariable(String name, Expression type, Abstract.ReferenceExpression defCall, int paramIndex, Abstract.ClassView classView, ClassField classifyingField, Set<Binding> bounds) {
    super(name, type, defCall, bounds);
    assert classifyingField != null;
    myDefCall = defCall;
    myParamIndex = paramIndex;
    myClassView = classView;
    myClassifyingField = classifyingField;
  }

  public ClassField getClassifyingField() {
    return myClassifyingField;
  }

  public Abstract.ClassView getClassView() {
    return myClassView;
  }

  @Override
  public LocalTypeCheckingError getErrorInfer(Expression... candidates) {
    return new ArgInferenceError(ArgInferenceError.typeClass(), getSourceNode(), candidates);
  }

  @Override
  public LocalTypeCheckingError getErrorMismatch(Expression expectedType, Expression actualType, Expression candidate) {
    return new ArgInferenceError(ArgInferenceError.typeClass(), expectedType, actualType, getSourceNode(), candidate);
  }

  public Expression getInstance(ClassViewInstancePool pool, Expression classifyingExpression) {
    if (myClassView != null) {
      return pool.getInstance(myDefCall, classifyingExpression, myClassView);
    } else {
      return pool.getInstance(myDefCall, myParamIndex, classifyingExpression, myClassifyingField.getThisClass().getAbstractDefinition());
    }
  }
}
