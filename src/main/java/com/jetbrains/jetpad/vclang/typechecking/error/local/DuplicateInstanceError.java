package com.jetbrains.jetpad.vclang.typechecking.error.local;

import com.jetbrains.jetpad.vclang.core.expr.Expression;
import com.jetbrains.jetpad.vclang.error.doc.Doc;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.SourceInfoProvider;

import static com.jetbrains.jetpad.vclang.error.doc.DocFactory.*;

public class DuplicateInstanceError extends LocalTypeCheckingError {
  public final Expression oldInstance;
  public final Expression newInstance;

  public DuplicateInstanceError(Expression oldInstance, Expression newInstance, Abstract.SourceNode cause) {
    super(Level.WARNING, "Duplicate instance", cause);
    this.oldInstance = oldInstance;
    this.newInstance = newInstance;
  }

  @Override
  public Doc getBodyDoc(SourceInfoProvider src) {
    return vList(
      hang(text("Old instance:"), termDoc(oldInstance)),
      hang(text("New instance:"), termDoc(newInstance)));
  }
}
