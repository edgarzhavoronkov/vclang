package com.jetbrains.jetpad.vclang.module;

import com.jetbrains.jetpad.vclang.term.definition.ClassDefinition;

import java.io.IOException;

public class DummySource implements Source {
  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public long lastModified() {
    return 0;
  }

  @Override
  public boolean load(ClassDefinition classDefinition) throws IOException {
    return false;
  }
}