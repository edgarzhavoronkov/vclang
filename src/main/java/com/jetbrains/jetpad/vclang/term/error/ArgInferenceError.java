package com.jetbrains.jetpad.vclang.term.error;

import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.PrettyPrintable;
import com.jetbrains.jetpad.vclang.term.definition.Definition;

import java.util.List;

public class ArgInferenceError extends TypeCheckingError {
  private final PrettyPrintable myWhere;

  public ArgInferenceError(Definition parent, String message, Abstract.PrettyPrintableSourceNode expression, List<String> names, PrettyPrintable where) {
    super(parent, message, expression, names);
    myWhere = where;
  }

  public static String functionArg(int index) {
    return "Cannot infer " + index + suffix(index) + " argument to function";
  }

  public static String typeOfFunctionArg(int index) {
    return "Cannot infer type of " + index + suffix(index) + " argument of function";
  }

  public static String lambdaArg(int index) {
    return "Cannot infer type of the " + index + suffix(index) + " argument of lambda";
  }

  public static String parameter(int index) {
    return "Cannot infer " + index + suffix(index) + " parameter to constructor";
  }

  public static String expression() {
    return "Cannot infer an expression";
  }

  public static String type() {
    return "Cannot infer type of expression";
  }

  public static String suffix(int n) {
    switch (n) {
      case 1: return "st";
      case 2: return "nd";
      case 3: return "rd";
      default: return "th";
    }
  }

  @Override
  public String toString() {
    String msg = printPosition() + getMessage();
    if (getExpression() == null) {
      return msg;
    } else {
      if (myWhere != null) {
        msg += " " + prettyPrint(myWhere);
      }
      return msg;
    }
  }

  public static class StringPrettyPrintable implements PrettyPrintable {
    private final String myString;

    public StringPrettyPrintable(String string) {
      myString = string;
    }

    @Override
    public void prettyPrint(StringBuilder builder, List<String> names, byte prec) {
      builder.append(myString);
    }
  }
}
