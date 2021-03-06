package com.jetbrains.jetpad.vclang.core.subst;

import com.jetbrains.jetpad.vclang.core.context.binding.LevelVariable;
import com.jetbrains.jetpad.vclang.core.context.binding.Variable;
import com.jetbrains.jetpad.vclang.core.sort.Level;
import com.jetbrains.jetpad.vclang.core.sort.Sort;

public class StdLevelSubstitution implements LevelSubstitution {
  private final Level myPLevel;
  private final Level myHLevel;

  public StdLevelSubstitution(Level pLevel, Level hLevel) {
    myPLevel = pLevel;
    myHLevel = hLevel;
  }

  public StdLevelSubstitution(Sort sort) {
    myPLevel = sort.getPLevel();
    myHLevel = sort.getHLevel();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public Level get(Variable variable) {
    return variable == LevelVariable.PVAR ? myPLevel : variable == LevelVariable.HVAR ? myHLevel : null;
  }
}
