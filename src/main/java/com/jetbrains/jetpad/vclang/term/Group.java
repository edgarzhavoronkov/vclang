package com.jetbrains.jetpad.vclang.term;

import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable;

import javax.annotation.Nonnull;
import java.util.Collection;

public interface Group {
  @Nonnull GlobalReferable getReferable();

  @Nonnull Collection<? extends Group> getSubgroups();
  @Nonnull Collection<? extends NamespaceCommand> getNamespaceCommands();

  @Nonnull Collection<? extends GlobalReferable> getConstructors();

  @Nonnull Collection<? extends Group> getDynamicSubgroups();
  @Nonnull Collection<? extends GlobalReferable> getFields();
}