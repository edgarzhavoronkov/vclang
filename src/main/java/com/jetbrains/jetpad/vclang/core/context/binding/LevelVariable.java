package com.jetbrains.jetpad.vclang.core.context.binding;

public interface LevelVariable extends Variable {
  enum LvlType { PLVL, HLVL }
  LvlType getType();

  default LevelVariable getStd() {
    return getType() == LvlType.PLVL ? PVAR : HVAR;
  }

  LevelVariable PVAR = new LevelVariable() {
    @Override
    public LvlType getType() {
      return LvlType.PLVL;
    }

    @Override
    public String toString() {
      return "\\lp";
    }
  };

  LevelVariable HVAR = new LevelVariable() {
    @Override
    public LvlType getType() {
      return LvlType.HLVL;
    }

    @Override
    public String toString() {
      return "\\lh";
    }
  };
}
