package com.jetbrains.jetpad.vclang.term.expr;

import com.jetbrains.jetpad.vclang.naming.NamespaceMember;
import com.jetbrains.jetpad.vclang.term.Preprelude;
import com.jetbrains.jetpad.vclang.term.definition.DataDefinition;
import org.junit.Test;

import static org.junit.Assert.*;
import static com.jetbrains.jetpad.vclang.term.expr.ExpressionFactory.*;
import static com.jetbrains.jetpad.vclang.typechecking.TypeCheckingTestCase.*;

public class IntExpressionTest {
  @Test
  public void test1() {
    NamespaceMember member = typeCheckClass("" +
            "\\static \\data Int2 \n" +
            "  | pos2 Nat\n" +
            "  | neg2 Nat\n" +
            "\\with\n" +
            "  | neg2 zero => pos2 zero\n"
    );
    DataDefinition data = (DataDefinition) member.namespace.getMember("Int2").definition;
    DataDefinition data2 = Preprelude.INT;
    System.err.println(data);

  }
}
