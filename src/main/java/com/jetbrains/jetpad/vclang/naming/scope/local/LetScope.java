package com.jetbrains.jetpad.vclang.naming.scope.local;

import com.jetbrains.jetpad.vclang.naming.reference.Referable;
import com.jetbrains.jetpad.vclang.naming.scope.Scope;
import com.jetbrains.jetpad.vclang.term.abs.Abstract;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Predicate;

public class LetScope implements Scope {
  private final Scope myParent;
  private final List<? extends Abstract.LetClause> myClauses;

  public LetScope(Scope parent, List<? extends Abstract.LetClause> clauses) {
    myParent = parent;
    myClauses = clauses;
  }

  @Override
  public Referable find(Predicate<Referable> pred) {
    for (int i = myClauses.size() - 1; i >= 0; i--) {
      Referable ref = myClauses.get(i).getReferable();
      if (pred.test(ref)) {
        return ref;
      }
    }
    return myParent.find(pred);
  }

  @Nonnull
  @Override
  public Scope getGlobalSubscope() {
    return myParent.getGlobalSubscope();
  }
}