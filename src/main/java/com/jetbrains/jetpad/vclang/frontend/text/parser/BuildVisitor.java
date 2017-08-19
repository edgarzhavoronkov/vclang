package com.jetbrains.jetpad.vclang.frontend.text.parser;

import com.jetbrains.jetpad.vclang.error.ErrorReporter;
import com.jetbrains.jetpad.vclang.frontend.text.Position;
import com.jetbrains.jetpad.vclang.module.source.SourceId;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.Concrete;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.*;

import static com.jetbrains.jetpad.vclang.frontend.text.parser.VcgrammarParser.*;

public class BuildVisitor extends VcgrammarBaseVisitor {
  private final SourceId myModule;
  private final ErrorReporter myErrorReporter;

  public BuildVisitor(SourceId module, ErrorReporter errorReporter) {
    myModule = module;
    myErrorReporter = errorReporter;
  }

  private Concrete.LocalVariable<Position> getVar(AtomFieldsAccContext ctx) {
    if (!ctx.fieldAcc().isEmpty() || !(ctx.atom() instanceof AtomLiteralContext)) {
      return null;
    }
    LiteralContext literal = ((AtomLiteralContext) ctx.atom()).literal();
    if (literal instanceof UnknownContext) {
      Position position = tokenPosition(literal.start);
      return new Concrete.LocalVariable<>(position, null);
    }
    if (literal instanceof NameContext && ((NameContext) literal).prefix().PREFIX() != null) {
      Position position = tokenPosition(literal.start);
      return new Concrete.LocalVariable<>(position, ((NameContext) literal).prefix().PREFIX().getText());
    }
    return null;
  }

  private boolean getVars(BinOpArgumentContext expr, List<Concrete.LocalVariable<Position>> result) {
    Concrete.LocalVariable<Position> firstArg = getVar(expr.atomFieldsAcc());
    if (firstArg == null) {
      return false;
    }

    result.add(firstArg);
    for (ArgumentContext argument : expr.argument()) {
      if (!(argument instanceof ArgumentExplicitContext)) {
        return false;
      }

      Concrete.LocalVariable<Position> arg = getVar(((ArgumentExplicitContext) argument).atomFieldsAcc());
      if (arg == null) {
        return false;
      }
      result.add(arg);
    }
    return true;
  }

  private boolean getVars(ExprContext expr, List<Concrete.LocalVariable<Position>> result) {
    if (!(expr instanceof BinOpContext && ((BinOpContext) expr).binOpArg() instanceof BinOpArgumentContext && ((BinOpContext) expr).maybeNew() instanceof NoNewContext && ((BinOpContext) expr).implementStatements() == null && ((BinOpContext) expr).postfix().isEmpty())) {
      return false;
    }

    for (BinOpLeftContext leftCtx : ((BinOpContext) expr).binOpLeft()) {
      if (!(leftCtx.maybeNew() instanceof NoNewContext && leftCtx.binOpArg() instanceof BinOpArgumentContext && leftCtx.implementStatements() == null && leftCtx.postfix().isEmpty() && leftCtx.infix().INFIX() != null)) {
        return false;
      }
      if (!getVars((BinOpArgumentContext) leftCtx.binOpArg(), result)) {
        return false;
      }
      result.add(new Concrete.LocalVariable<>(tokenPosition(leftCtx.infix().start), leftCtx.infix().INFIX().getText()));
    }

    return getVars((BinOpArgumentContext) ((BinOpContext) expr).binOpArg(), result);
  }

  private List<Concrete.LocalVariable<Position>> getVarList(ExprContext expr, List<TerminalNode> infixList) {
    List<Concrete.LocalVariable<Position>> result = new ArrayList<>();
    if (getVars(expr, result)) {
      for (TerminalNode infix : infixList) {
        result.add(new Concrete.LocalVariable<>(tokenPosition(infix.getSymbol()), infix.getText()));
      }
      return result;
    } else {
      myErrorReporter.report(new ParserError(tokenPosition(expr.getStart()), "Expected a list of variables"));
      throw new ParseException();
    }
  }

  private List<Concrete.LocalVariable<Position>> getVars(TypedVarsContext ctx) {
    List<Concrete.LocalVariable<Position>> result = new ArrayList<>(ctx.id().size() + 1);
    result.add(new Concrete.LocalVariable<>(tokenPosition(ctx.start), ctx.INFIX().getText()));
    for (IdContext idCtx : ctx.id()) {
      result.add(new Concrete.LocalVariable<>(tokenPosition(idCtx.start), idCtx.getText()));
    }
    return result;
  }

  public Concrete.Expression<Position> visitExpr(ExprContext expr) {
    //noinspection unchecked
    return (Concrete.Expression<Position>) visit(expr);
  }

  private Concrete.Expression<Position> visitExpr(AtomContext expr) {
    //noinspection unchecked
    return (Concrete.Expression<Position>) visit(expr);
  }

  private Concrete.Expression<Position> visitExpr(LiteralContext expr) {
    //noinspection unchecked
    return (Concrete.Expression) visit(expr);
  }

  private Concrete.UniverseExpression<Position> visitExpr(UniverseAtomContext expr) {
    //noinspection unchecked
    return (Concrete.UniverseExpression<Position>) visit(expr);
  }

  @Override
  public String visitId(IdContext ctx) {
    if (ctx.PREFIX() != null) {
      return ctx.PREFIX().getText();
    }
    if (ctx.INFIX() != null) {
      return ctx.INFIX().getText();
    }
    throw new IllegalStateException();
  }

  @Override
  public String visitPrefix(PrefixContext ctx) {
    if (ctx.PREFIX() != null) {
      return ctx.PREFIX().getText();
    }
    if (ctx.PREFIX_INFIX() != null) {
      String s = ctx.PREFIX_INFIX().getText();
      return s.substring(1, s.length());
    }
    throw new IllegalStateException();
  }

  @Override
  public String visitInfix(InfixContext ctx) {
    if (ctx.INFIX() != null) {
      return ctx.INFIX().getText();
    }
    if (ctx.INFIX_PREFIX() != null) {
      String s = ctx.INFIX_PREFIX().getText();
      return s.substring(1, s.length());
    }
    throw new IllegalStateException();
  }

  @Override
  public String visitPostfix(PostfixContext ctx) {
    String s;
    if (ctx.POSTFIX_INFIX() != null) {
      s = ctx.POSTFIX_INFIX().getText();
    } else if (ctx.POSTFIX_PREFIX() != null) {
      s = ctx.POSTFIX_PREFIX().getText();
    } else {
      throw new IllegalStateException();
    }
    return s.substring(0, s.length() - 1);
  }

  private List<String> getModulePath(String module) {
    String[] modulePath = module.split("::");
    assert modulePath[0].isEmpty();
    return Arrays.asList(modulePath).subList(1, modulePath.length);
  }

  @Override
  public Concrete.ModuleCallExpression visitAtomModuleCall(AtomModuleCallContext ctx) {
    return new Concrete.ModuleCallExpression<>(tokenPosition(ctx.getStart()), getModulePath(ctx.MODULE_PATH().getText()));
  }

  private List<Concrete.Statement<Position>> visitStatementList(List<StatementContext> statementCtxs) {
    List<Concrete.Statement<Position>> statements = new ArrayList<>(statementCtxs.size());
    for (StatementContext statementCtx : statementCtxs) {
      try {
        Object statement = visit(statementCtx);
        if (statement != null) {
          //noinspection unchecked
          statements.add((Concrete.Statement<Position>) statement);
        }
      } catch (ParseException ignored) {

      }
    }
    return statements;
  }

  @Override
  public List<Concrete.Statement<Position>> visitStatements(StatementsContext ctx) {
    return visitStatementList(ctx.statement());
  }

  public Concrete.Definition<Position> visitDefinition(DefinitionContext ctx) {
    //noinspection unchecked
    return (Concrete.Definition<Position>) visit(ctx);
  }

  @Override
  public Concrete.DefineStatement visitStatDef(StatDefContext ctx) {
    Concrete.Definition<Position> definition = visitDefinition(ctx.definition());
    return new Concrete.DefineStatement<>(definition.getData(), definition);
  }

  @Override
  public Concrete.NamespaceCommandStatement visitStatCmd(StatCmdContext ctx) {
    Concrete.NamespaceCommandStatement.Kind kind = (Concrete.NamespaceCommandStatement.Kind) visit(ctx.nsCmd());
    List<String> modulePath = ctx.nsCmdRoot().MODULE_PATH() == null ? null : getModulePath(ctx.nsCmdRoot().MODULE_PATH().getText());
    List<String> path = new ArrayList<>();
    if (ctx.nsCmdRoot().id() != null) {
      path.add(visitId(ctx.nsCmdRoot().id()));
    }
    for (FieldAccContext fieldAccContext : ctx.fieldAcc()) {
      if (fieldAccContext instanceof ClassFieldAccContext) {
        path.add(visitId(((ClassFieldAccContext) fieldAccContext).id()));
      } else {
        myErrorReporter.report(new ParserError(tokenPosition(fieldAccContext.getStart()), "Expected a name"));
      }
    }

    List<String> names;
    if (!ctx.id().isEmpty()) {
      names = new ArrayList<>(ctx.id().size());
      for (IdContext idCtx : ctx.id()) {
        names.add(visitId(idCtx));
      }
    } else {
      names = null;
    }
    return new Concrete.NamespaceCommandStatement<>(tokenPosition(ctx.getStart()), kind, modulePath, path, ctx.hidingOpt() instanceof WithHidingContext, names);
  }

  @Override
  public Concrete.NamespaceCommandStatement.Kind visitOpenCmd(OpenCmdContext ctx) {
    return Concrete.NamespaceCommandStatement.Kind.OPEN;
  }

  @Override
  public Concrete.NamespaceCommandStatement.Kind visitExportCmd(ExportCmdContext ctx) {
    return Concrete.NamespaceCommandStatement.Kind.EXPORT;
  }

  private Abstract.Precedence visitPrecedence(PrecedenceContext ctx) {
    return (Abstract.Precedence) visit(ctx);
  }

  @Override
  public Abstract.Precedence visitNoPrecedence(NoPrecedenceContext ctx) {
    return Abstract.Precedence.DEFAULT;
  }

  @Override
  public Abstract.Precedence visitWithPrecedence(WithPrecedenceContext ctx) {
    int priority = Integer.parseInt(ctx.NUMBER().getText());
    if (priority < 1 || priority > 9) {
      myErrorReporter.report(new ParserError(tokenPosition(ctx.NUMBER().getSymbol()), "Precedence out of range: " + priority));

      if (priority < 1) {
        priority = 1;
      } else {
        priority = 9;
      }
    }

    return new Abstract.Precedence((Abstract.Precedence.Associativity) visit(ctx.associativity()), (byte) priority);
  }

  @Override
  public Abstract.Precedence.Associativity visitNonAssoc(NonAssocContext ctx) {
    return Abstract.Precedence.Associativity.NON_ASSOC;
  }

  @Override
  public Abstract.Precedence.Associativity visitLeftAssoc(LeftAssocContext ctx) {
    return Abstract.Precedence.Associativity.LEFT_ASSOC;
  }

  @Override
  public Abstract.Precedence.Associativity visitRightAssoc(RightAssocContext ctx) {
    return Abstract.Precedence.Associativity.RIGHT_ASSOC;
  }

  private Concrete.Pattern<Position> visitPattern(PatternContext ctx) {
    //noinspection unchecked
    return (Concrete.Pattern<Position>) visit(ctx);
  }

  @Override
  public Concrete.Pattern visitPatternAtom(PatternAtomContext ctx) {
    return (Concrete.Pattern) visit(ctx.atomPattern());
  }

  @Override
  public Concrete.Pattern visitPatternConstructor(PatternConstructorContext ctx) {
    if (ctx.atomPatternOrID().size() == 0) {
      return new Concrete.NamePattern<>(tokenPosition(ctx.start), visitPrefix(ctx.prefix()));
    } else {
      List<Concrete.Pattern<Position>> patterns = new ArrayList<>(ctx.atomPatternOrID().size());
      for (AtomPatternOrIDContext atomCtx : ctx.atomPatternOrID()) {
        patterns.add(visitAtomPattern(atomCtx));
      }
      return new Concrete.ConstructorPattern<>(tokenPosition(ctx.start), visitPrefix(ctx.prefix()), patterns);
    }
  }

  private Concrete.Pattern<Position> visitAtomPattern(AtomPatternOrIDContext ctx) {
    //noinspection unchecked
    return (Concrete.Pattern<Position>) visit(ctx);
  }

  @Override
  public Concrete.Pattern visitPatternExplicit(PatternExplicitContext ctx) {
    return visitPattern(ctx.pattern());
  }

  @Override
  public Concrete.Pattern visitPatternImplicit(PatternImplicitContext ctx) {
    Concrete.Pattern pattern = visitPattern(ctx.pattern());
    if (pattern != null) {
      pattern.setExplicit(false);
    }
    return pattern;
  }

  @Override
  public Concrete.Pattern visitPatternEmpty(PatternEmptyContext ctx) {
    return new Concrete.EmptyPattern<>(tokenPosition(ctx.getStart()));
  }

  @Override
  public Concrete.Pattern visitPatternOrIDAtom(PatternOrIDAtomContext ctx) {
    return (Concrete.Pattern) visit(ctx.atomPattern());
  }

  @Override
  public Concrete.Pattern visitPatternID(PatternIDContext ctx) {
    Position position = tokenPosition(ctx.getStart());
    return new Concrete.NamePattern<>(position, visitPrefix(ctx.prefix()));
  }

  @Override
  public Concrete.Pattern visitPatternAny(PatternAnyContext ctx) {
    Position position = tokenPosition(ctx.getStart());
    return new Concrete.NamePattern<>(position, "_");
  }

  @Override
  public Concrete.ClassField visitClassField(ClassFieldContext ctx) {
    return new Concrete.ClassField<>(tokenPosition(ctx.getStart()), visitId(ctx.id()), visitPrecedence(ctx.precedence()), visitExpr(ctx.expr()));
  }

  @Override
  public Concrete.ClassView visitDefClassView(DefClassViewContext ctx) {
    List<Concrete.ClassViewField<Position>> fields = new ArrayList<>(ctx.classViewField().size());

    Concrete.Expression<Position> expr = visitExpr(ctx.expr());
    if (!(expr instanceof Concrete.ReferenceExpression)) {
      myErrorReporter.report(new ParserError(expr.getData(), "Expected a class"));
      throw new ParseException();
    }

    Concrete.ClassView<Position> classView = new Concrete.ClassView<>(tokenPosition(ctx.getStart()), visitId(ctx.id(0)), (Concrete.ReferenceExpression<Position>) expr, visitId(ctx.id(1)), fields);
    for (ClassViewFieldContext classViewFieldContext : ctx.classViewField()) {
      fields.add(visitClassViewField(classViewFieldContext, classView));
    }

    return classView;
  }

  private Concrete.ClassViewField<Position> visitClassViewField(ClassViewFieldContext ctx, Concrete.ClassView<Position> classView) {
    String underlyingField = visitId(ctx.id(0));
    return new Concrete.ClassViewField<>(tokenPosition(ctx.id(0).start), ctx.id().size() > 1 ? visitId(ctx.id(1)) : underlyingField, ctx.precedence() == null ? Abstract.Precedence.DEFAULT : visitPrecedence(ctx.precedence()), underlyingField, classView);
  }

  @Override
  public Concrete.ClassViewInstance<Position> visitDefInstance(DefInstanceContext ctx) {
    List<Concrete.Parameter<Position>> arguments = visitFunctionArguments(ctx.tele());
    Concrete.Expression<Position> term = visitExpr(ctx.expr());
    if (term instanceof Concrete.NewExpression) {
      Concrete.Expression<Position> type = ((Concrete.NewExpression<Position>) term).getExpression();
      if (type instanceof Concrete.ClassExtExpression) {
        Concrete.ClassExtExpression<Position> classExt = (Concrete.ClassExtExpression<Position>) type;
        if (classExt.getBaseClassExpression() instanceof Concrete.ReferenceExpression) {
          return new Concrete.ClassViewInstance<>(tokenPosition(ctx.getStart()), ctx.defaultInst() instanceof WithDefaultContext, visitId(ctx.id()), Abstract.Precedence.DEFAULT, arguments, (Concrete.ReferenceExpression<Position>) classExt.getBaseClassExpression(), classExt.getStatements());
        }
      }
    }

    myErrorReporter.report(new ParserError(tokenPosition(ctx.expr().getStart()), "Expected a class view extension"));
    throw new ParseException();
  }

  @Override
  public List<Concrete.Statement<Position>> visitWhere(WhereContext ctx) {
    return ctx == null || ctx.statement().isEmpty() ? Collections.emptyList() : visitStatementList(ctx.statement());
  }

  @Override
  public List<Concrete.ReferenceExpression<Position>> visitElim(ElimContext ctx) {
    if (ctx != null && ctx.atomFieldsAcc() != null && !ctx.atomFieldsAcc().isEmpty()) {
      List<Concrete.Expression<Position>> expressions = new ArrayList<>(ctx.atomFieldsAcc().size());
      for (AtomFieldsAccContext exprCtx : ctx.atomFieldsAcc()) {
        expressions.add(visitAtomFieldsAcc(exprCtx));
      }
      return checkElimExpressions(expressions);
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public Concrete.FunctionDefinition visitDefFunction(DefFunctionContext ctx) {
    Concrete.Expression<Position> resultType = ctx.expr() != null ? visitExpr(ctx.expr()) : null;
    Concrete.FunctionBody<Position> body;
    if (ctx.functionBody() instanceof WithElimContext) {
      WithElimContext elimCtx = ((WithElimContext) ctx.functionBody());
      body = new Concrete.ElimFunctionBody<>(tokenPosition(elimCtx.start), visitElim(elimCtx.elim()), visitClauses(elimCtx.clauses()));
    } else {
      body = new Concrete.TermFunctionBody<>(tokenPosition(ctx.start), visitExpr(((WithoutElimContext) ctx.functionBody()).expr()));
    }
    List<Concrete.Statement<Position>> statements = visitWhere(ctx.where());
    Concrete.FunctionDefinition<Position> result = new Concrete.FunctionDefinition<>(tokenPosition(ctx.getStart()), visitId(ctx.id()), visitPrecedence(ctx.precedence()), visitFunctionArguments(ctx.tele()), resultType, body, statements);

    for (Iterator<Concrete.Statement<Position>> iterator = statements.iterator(); iterator.hasNext(); ) {
      Concrete.Statement<Position> statement = iterator.next();
      if (statement instanceof Concrete.DefineStatement) {
        Concrete.Definition<Position> definition = ((Concrete.DefineStatement<Position>) statement).getDefinition();
        if (definition instanceof Concrete.ClassField || definition instanceof Concrete.Implementation) {
          misplacedDefinitionError(definition.getData());
          iterator.remove();
        } else {
          definition.setParent(result);
        }
      }
    }

    return result;
  }

  private List<Concrete.Parameter<Position>> visitFunctionArguments(List<TeleContext> teleCtx) {
    List<Concrete.Parameter<Position>> arguments = new ArrayList<>();
    for (TeleContext tele : teleCtx) {
      List<Concrete.Parameter<Position>> args = visitLamTele(tele);
      if (args != null) {
        if (args.get(0) instanceof Concrete.TelescopeParameter) {
          arguments.add(args.get(0));
        } else {
          myErrorReporter.report(new ParserError(tokenPosition(tele.getStart()), "Expected a typed variable"));
        }
      }
    }
    return arguments;
  }

  @Override
  public Concrete.DataDefinition<Position> visitDefData(DefDataContext ctx) {
    final Concrete.UniverseExpression<Position> universe;
    if (ctx.expr() != null) {
      Concrete.Expression<Position> expr = visitExpr(ctx.expr());
      if (expr instanceof Concrete.UniverseExpression) {
        universe = (Concrete.UniverseExpression<Position>) expr;
      } else {
        myErrorReporter.report(new ParserError(tokenPosition(ctx.expr().getStart()), "Specified type of the data definition is not a universe"));
        universe = null;
      }
    } else {
      universe = null;
    }
    List<Concrete.ReferenceExpression<Position>> eliminatedReferences = ctx.dataBody() instanceof DataClausesContext ? visitElim(((DataClausesContext) ctx.dataBody()).elim()) : null;
    Concrete.DataDefinition<Position> dataDefinition = new Concrete.DataDefinition<>(tokenPosition(ctx.getStart()), visitId(ctx.id()), visitPrecedence(ctx.precedence()), visitTeles(ctx.tele()), eliminatedReferences, ctx.isTruncated() instanceof TruncatedContext, universe, new ArrayList<>());
    visitDataBody(ctx.dataBody(), dataDefinition);
    return dataDefinition;
  }

  private void visitDataBody(DataBodyContext ctx, Concrete.DataDefinition<Position> def) {
    if (ctx instanceof DataClausesContext) {
      for (ConstructorClauseContext clauseCtx : ((DataClausesContext) ctx).constructorClause()) {
        try {
          List<Concrete.Pattern<Position>> patterns = new ArrayList<>(clauseCtx.pattern().size());
          for (PatternContext patternCtx : clauseCtx.pattern()) {
            patterns.add(visitPattern(patternCtx));
          }
          def.getConstructorClauses().add(new Concrete.ConstructorClause<>(tokenPosition(clauseCtx.start), patterns, visitConstructors(clauseCtx.constructor(), def)));
        } catch (ParseException ignored) {

        }
      }
    } else if (ctx instanceof DataConstructorsContext) {
      def.getConstructorClauses().add(new Concrete.ConstructorClause<>(tokenPosition(ctx.start), null, visitConstructors(((DataConstructorsContext) ctx).constructor(), def)));
    }
  }

  private List<Concrete.Constructor<Position>> visitConstructors(List<ConstructorContext> conContexts, Concrete.DataDefinition<Position> def) {
    List<Concrete.Constructor<Position>> result = new ArrayList<>(conContexts.size());
    for (ConstructorContext conCtx : conContexts) {
      try {
        List<Concrete.FunctionClause<Position>> clauses;
        if (conCtx.elim() != null || !conCtx.clause().isEmpty()) {
          clauses = new ArrayList<>(conCtx.clause().size());
          for (ClauseContext clauseCtx : conCtx.clause()) {
            clauses.add(visitClause(clauseCtx));
          }
        } else {
          clauses = Collections.emptyList();
        }

        result.add(new Concrete.Constructor<>(
          tokenPosition(conCtx.start),
          visitId(conCtx.id()),
          visitPrecedence(conCtx.precedence()),
          def,
          visitTeles(conCtx.tele()),
          visitElim(conCtx.elim()),
          clauses));
      } catch (ParseException ignored) {

      }
    }
    return result;
  }

  private void misplacedDefinitionError(Position position) {
    myErrorReporter.report(new ParserError(position, "This definition is not allowed here"));
  }

  private void visitInstanceStatements(List<ClassStatContext> ctx, List<Concrete.ClassField<Position>> fields, List<Concrete.Implementation<Position>> implementations, List<Concrete.Definition<Position>> definitions) {
    for (ClassStatContext statementCtx : ctx) {
      if (statementCtx == null) {
        continue;
      }

      try {
        //noinspection unchecked
        Concrete.SourceNode<Position> sourceNode = (Concrete.SourceNode<Position>) visit(statementCtx);
        if (sourceNode != null) {
          Concrete.Definition<Position> definition;
          if (sourceNode instanceof Concrete.Definition) {
            definition = (Concrete.Definition<Position>) sourceNode;
          } else
          if (sourceNode instanceof Concrete.DefineStatement) {
            definition = ((Concrete.DefineStatement<Position>) sourceNode).getDefinition();
          } else {
            misplacedDefinitionError(sourceNode.getData());
            continue;
          }

          if (definition instanceof Concrete.ClassField) {
            if (fields != null) {
              fields.add((Concrete.ClassField<Position>) definition);
            } else {
              misplacedDefinitionError(definition.getData());
            }
          } else if (definition instanceof Concrete.Implementation) {
            if (implementations != null) {
              implementations.add((Concrete.Implementation<Position>) definition);
            } else {
              misplacedDefinitionError(definition.getData());
            }
          } else if (definition instanceof Concrete.FunctionDefinition || definition instanceof Concrete.DataDefinition || definition instanceof Concrete.ClassDefinition) {
            definitions.add(definition);
          } else {
            misplacedDefinitionError(definition.getData());
          }
        }
      } catch (ParseException ignored) {

      }
    }
  }

  @Override
  public Concrete.Statement visitClassStatement(ClassStatementContext ctx) {
    return (Concrete.Statement) visit(ctx.statement());
  }

  @Override
  public Concrete.ClassDefinition<Position> visitDefClass(DefClassContext ctx) {
    List<Concrete.TypeParameter<Position>> polyParameters = visitTeles(ctx.tele());
    List<Concrete.SuperClass<Position>> superClasses = new ArrayList<>(ctx.atomFieldsAcc().size());
    List<Concrete.ClassField<Position>> fields = new ArrayList<>();
    List<Concrete.Implementation<Position>> implementations = new ArrayList<>();
    List<Concrete.Statement<Position>> globalStatements = visitWhere(ctx.where());
    List<Concrete.Definition<Position>> instanceDefinitions;

    if (ctx.classStat().isEmpty()) {
      instanceDefinitions = Collections.emptyList();
    } else {
      instanceDefinitions = new ArrayList<>(ctx.classStat().size());
      visitInstanceStatements(ctx.classStat(), fields, implementations, instanceDefinitions);
    }

    for (AtomFieldsAccContext exprCtx : ctx.atomFieldsAcc()) {
      superClasses.add(new Concrete.SuperClass<>(tokenPosition(exprCtx.getStart()), visitAtomFieldsAcc(exprCtx)));
    }

    Concrete.ClassDefinition<Position> classDefinition = new Concrete.ClassDefinition<>(tokenPosition(ctx.getStart()), visitId(ctx.id()), polyParameters, superClasses, fields, implementations, globalStatements, instanceDefinitions);
    for (Concrete.ClassField<Position> field : fields) {
      field.setParent(classDefinition);
    }
    for (Concrete.Implementation<Position> implementation : implementations) {
      implementation.setParent(classDefinition);
    }
    for (Concrete.Definition<Position> definition : instanceDefinitions) {
      definition.setParent(classDefinition);
      definition.setNotStatic();
    }
    for (Concrete.Statement<Position> statement : globalStatements) {
      if (statement instanceof Concrete.DefineStatement) {
        ((Concrete.DefineStatement<Position>) statement).getDefinition().setParent(classDefinition);
      }
    }
    return classDefinition;
  }

  @Override
  public Concrete.Implementation<Position> visitClassImplement(ClassImplementContext ctx) {
    return new Concrete.Implementation<>(tokenPosition(ctx.start), visitId(ctx.id()), visitExpr(ctx.expr()));
  }

  @Override
  public Concrete.ReferenceExpression<Position> visitName(NameContext ctx) {
    return new Concrete.ReferenceExpression<>(tokenPosition(ctx.start), null, visitPrefix(ctx.prefix()));
  }

  @Override
  public Concrete.InferHoleExpression<Position> visitUnknown(UnknownContext ctx) {
    return new Concrete.InferHoleExpression<>(tokenPosition(ctx.getStart()));
  }

  @Override
  public Concrete.GoalExpression<Position> visitGoal(GoalContext ctx) {
    return new Concrete.GoalExpression<>(tokenPosition(ctx.start), ctx.id() == null ? null : visitId(ctx.id()), ctx.expr() == null ? null : visitExpr(ctx.expr()));
  }

  @Override
  public Concrete.PiExpression<Position> visitArr(ArrContext ctx) {
    Concrete.Expression<Position> domain = visitExpr(ctx.expr(0));
    Concrete.Expression<Position> codomain = visitExpr(ctx.expr(1));
    List<Concrete.TypeParameter<Position>> arguments = new ArrayList<>(1);
    arguments.add(new Concrete.TypeParameter<>(domain.getData(), true, domain));
    return new Concrete.PiExpression<>(tokenPosition(ctx.getToken(ARROW, 0).getSymbol()), arguments, codomain);
  }

  @Override
  public Concrete.Expression visitTuple(TupleContext ctx) {
    if (ctx.expr().size() == 1) {
      return visitExpr(ctx.expr(0));
    } else {
      List<Concrete.Expression<Position>> fields = new ArrayList<>(ctx.expr().size());
      for (ExprContext exprCtx : ctx.expr()) {
        fields.add(visitExpr(exprCtx));
      }
      return new Concrete.TupleExpression<>(tokenPosition(ctx.getStart()), fields);
    }
  }

  private List<Concrete.Parameter<Position>> visitLamTele(TeleContext tele) {
    List<Concrete.Parameter<Position>> arguments = new ArrayList<>(3);
    if (tele instanceof ExplicitContext || tele instanceof ImplicitContext) {
      boolean explicit = tele instanceof ExplicitContext;
      TypedExprContext typedExpr = explicit ? ((ExplicitContext) tele).typedExpr() : ((ImplicitContext) tele).typedExpr();
      Concrete.Expression<Position> typeExpr;
      List<Concrete.LocalVariable<Position>> vars;
      if (typedExpr instanceof TypedVarsContext) {
        vars = getVars((TypedVarsContext) typedExpr);
        typeExpr = visitExpr(((TypedVarsContext) typedExpr).expr());
      } else
      if (typedExpr instanceof TypedContext) {
        vars = getVarList(((TypedContext) typedExpr).expr(0), ((TypedContext) typedExpr).INFIX());
        typeExpr = visitExpr(((TypedContext) typedExpr).expr(1));
      } else if (typedExpr instanceof NotTypedContext) {
        vars = getVarList(((NotTypedContext) typedExpr).expr(), Collections.emptyList());
        typeExpr = null;
      } else {
        throw new IllegalStateException();
      }
      if (typeExpr == null) {
        for (Concrete.LocalVariable<Position> var : vars) {
          arguments.add(new Concrete.NameParameter<>(var.getData(), explicit, var.getName()));
        }
      } else {
        arguments.add(new Concrete.TelescopeParameter<>(tokenPosition(tele.getStart()), explicit, vars, typeExpr));
      }
    } else {
      boolean ok = tele instanceof TeleLiteralContext;
      if (ok) {
        LiteralContext literalContext = ((TeleLiteralContext) tele).literal();
        if (literalContext instanceof NameContext && ((NameContext) literalContext).prefix().PREFIX() != null) {
          TerminalNode id = ((NameContext) literalContext).prefix().PREFIX();
          arguments.add(new Concrete.NameParameter<>(tokenPosition(id.getSymbol()), true, id.getText()));
        } else if (literalContext instanceof UnknownContext) {
          arguments.add(new Concrete.NameParameter<>(tokenPosition(literalContext.getStart()), true, null));
        } else {
          ok = false;
        }
      }
      if (!ok) {
        myErrorReporter.report(new ParserError(tokenPosition(tele.start), "Unexpected token, expected an identifier"));
        throw new ParseException();
      }
    }
    return arguments;
  }

  private List<Concrete.Parameter<Position>> visitLamTeles(List<TeleContext> tele) {
    List<Concrete.Parameter<Position>> arguments = new ArrayList<>();
    for (TeleContext arg : tele) {
      arguments.addAll(visitLamTele(arg));
    }
    return arguments;
  }

  @Override
  public Concrete.LamExpression<Position> visitLam(LamContext ctx) {
    return new Concrete.LamExpression<>(tokenPosition(ctx.getStart()), visitLamTeles(ctx.tele()), visitExpr(ctx.expr()));
  }

  @Override
  public Concrete.Expression visitBinOpArgument(BinOpArgumentContext ctx) {
    return visitAtoms(visitAtomFieldsAcc(ctx.atomFieldsAcc()), ctx.argument());
  }

  private Concrete.Expression parseImplementations(MaybeNewContext newCtx, ImplementStatementsContext implCtx, Token token, Concrete.Expression<Position> expr) {
    if (implCtx != null) {
      List<Concrete.ClassFieldImpl<Position>> implementStatements = new ArrayList<>(implCtx.implementStatement().size());
      for (ImplementStatementContext implementStatement : implCtx.implementStatement()) {
        implementStatements.add(new Concrete.ClassFieldImpl<>(tokenPosition(implementStatement.id().start), visitId(implementStatement.id()), visitExpr(implementStatement.expr())));
      }
      expr = new Concrete.ClassExtExpression<>(tokenPosition(token), expr, implementStatements);
    }

    if (newCtx instanceof WithNewContext) {
      expr = new Concrete.NewExpression<>(tokenPosition(token), expr);
    }

    return expr;
  }

  private Concrete.LevelExpression<Position> parseTruncatedUniverse(TerminalNode terminal) {
    String universe = terminal.getText();
    if (universe.charAt(1) == 'o') {
      return new Concrete.InfLevelExpression<>(tokenPosition(terminal.getSymbol()));
    }

    return new Concrete.NumberLevelExpression<>(tokenPosition(terminal.getSymbol()), Integer.parseInt(universe.substring(1, universe.indexOf('-'))));
  }

  @Override
  public Concrete.UniverseExpression visitUniverse(UniverseContext ctx) {
    Position position = tokenPosition(ctx.getStart());
    Concrete.LevelExpression<Position> lp;
    Concrete.LevelExpression<Position> lh;

    String text = ctx.UNIVERSE().getText().substring("\\Type".length());
    lp = text.isEmpty() ? null : new Concrete.NumberLevelExpression<>(tokenPosition(ctx.UNIVERSE().getSymbol()), Integer.parseInt(text));

    if (ctx.levelAtom().size() >= 1) {
      if (lp == null) {
        lp = visitLevel(ctx.levelAtom(0));
        lh = null;
      } else {
        lh = visitLevel(ctx.levelAtom(0));
      }

      if (ctx.levelAtom().size() >= 2) {
        if (lh == null) {
          lh = visitLevel(ctx.levelAtom(1));
        } else {
          myErrorReporter.report(new ParserError(tokenPosition(ctx.levelAtom(1).getStart()), "h-level is already specified"));
        }
      }
    } else {
      lh = null;
    }

    return new Concrete.UniverseExpression<>(position, lp, lh);
  }

  @Override
  public Concrete.UniverseExpression<Position> visitTruncatedUniverse(TruncatedUniverseContext ctx) {
    Position position = tokenPosition(ctx.getStart());
    Concrete.LevelExpression<Position> pLevel;

    String text = ctx.TRUNCATED_UNIVERSE().getText();
    text = text.substring(text.indexOf('-') + "-Type".length());
    if (text.isEmpty()) {
      pLevel = ctx.levelAtom() == null ? null : visitLevel(ctx.levelAtom());
    } else {
      pLevel = new Concrete.NumberLevelExpression<>(tokenPosition(ctx.TRUNCATED_UNIVERSE().getSymbol()), Integer.parseInt(text));
      if (ctx.levelAtom() != null) {
        myErrorReporter.report(new ParserError(tokenPosition(ctx.levelAtom().getStart()), "p-level is already specified"));
      }
    }

    return new Concrete.UniverseExpression<>(position, pLevel, parseTruncatedUniverse(ctx.TRUNCATED_UNIVERSE()));
  }

  @Override
  public Concrete.UniverseExpression visitSetUniverse(SetUniverseContext ctx) {
    Position position = tokenPosition(ctx.getStart());
    Concrete.LevelExpression<Position> pLevel;

    String text = ctx.SET().getText().substring("\\Set".length());
    if (text.isEmpty()) {
      pLevel = ctx.levelAtom() == null ? null : visitLevel(ctx.levelAtom());
    } else {
      pLevel = new Concrete.NumberLevelExpression<>(tokenPosition(ctx.SET().getSymbol()), Integer.parseInt(text));
      if (ctx.levelAtom() != null) {
        myErrorReporter.report(new ParserError(tokenPosition(ctx.levelAtom().getStart()), "p-level is already specified"));
      }
    }

    return new Concrete.UniverseExpression<>(position, pLevel, new Concrete.NumberLevelExpression<>(position, 0));
  }

  @Override
  public Concrete.UniverseExpression visitUniTruncatedUniverse(UniTruncatedUniverseContext ctx) {
    String text = ctx.TRUNCATED_UNIVERSE().getText();
    text = text.substring(text.indexOf('-') + "-Type".length());
    Concrete.LevelExpression<Position> pLevel = text.isEmpty() ? null : new Concrete.NumberLevelExpression<>(tokenPosition(ctx.TRUNCATED_UNIVERSE().getSymbol()), Integer.parseInt(text));
    return new Concrete.UniverseExpression<>(tokenPosition(ctx.getStart()), pLevel, parseTruncatedUniverse(ctx.TRUNCATED_UNIVERSE()));
  }

  @Override
  public Concrete.UniverseExpression visitUniUniverse(UniUniverseContext ctx) {
    String text = ctx.UNIVERSE().getText().substring("\\Type".length());
    Concrete.LevelExpression<Position> lp = text.isEmpty() ? null : new Concrete.NumberLevelExpression<>(tokenPosition(ctx.UNIVERSE().getSymbol()), Integer.parseInt(text));
    return new Concrete.UniverseExpression<>(tokenPosition(ctx.getStart()), lp, null);
  }

  @Override
  public Concrete.UniverseExpression visitUniSetUniverse(UniSetUniverseContext ctx) {
    Position position = tokenPosition(ctx.getStart());
    String text = ctx.SET().getText().substring("\\Set".length());
    Concrete.LevelExpression<Position> pLevel = text.isEmpty() ? null : new Concrete.NumberLevelExpression<>(tokenPosition(ctx.SET().getSymbol()), Integer.parseInt(text));
    return new Concrete.UniverseExpression<>(position, pLevel, new Concrete.NumberLevelExpression<>(position, 0));
  }

  @Override
  public Concrete.UniverseExpression visitProp(PropContext ctx) {
    Position pos = tokenPosition(ctx.getStart());
    return new Concrete.UniverseExpression<>(pos, new Concrete.NumberLevelExpression<>(pos, 0), new Concrete.NumberLevelExpression<>(pos, -1));
  }

  private Concrete.LevelExpression<Position> visitLevel(LevelAtomContext ctx) {
    //noinspection unchecked
    return (Concrete.LevelExpression<Position>) visit(ctx);
  }

  @Override
  public Concrete.PLevelExpression visitPLevel(PLevelContext ctx) {
    return new Concrete.PLevelExpression<>(tokenPosition(ctx.getStart()));
  }

  @Override
  public Concrete.HLevelExpression visitHLevel(HLevelContext ctx) {
    return new Concrete.HLevelExpression<>(tokenPosition(ctx.getStart()));
  }

  @Override
  public Concrete.NumberLevelExpression visitNumLevel(NumLevelContext ctx) {
    return new Concrete.NumberLevelExpression<>(tokenPosition(ctx.NUMBER().getSymbol()), Integer.parseInt(ctx.NUMBER().getText()));
  }

  @Override
  public Concrete.LevelExpression visitExprLevel(ExprLevelContext ctx) {
    return (Concrete.LevelExpression) visit(ctx.levelExpr());
  }

  @Override
  public Concrete.LevelExpression visitAtomLevelExpr(AtomLevelExprContext ctx) {
    return visitLevel(ctx.levelAtom());
  }

  @Override
  public Concrete.SucLevelExpression visitSucLevelExpr(SucLevelExprContext ctx) {
    return new Concrete.SucLevelExpression<>(tokenPosition(ctx.getStart()), visitLevel(ctx.levelAtom()));
  }

  @Override
  public Concrete.MaxLevelExpression visitMaxLevelExpr(MaxLevelExprContext ctx) {
    return new Concrete.MaxLevelExpression<>(tokenPosition(ctx.getStart()), visitLevel(ctx.levelAtom(0)), visitLevel(ctx.levelAtom(1)));
  }

  private List<Concrete.TypeParameter<Position>> visitTeles(List<TeleContext> teles) {
    List<Concrete.TypeParameter<Position>> arguments = new ArrayList<>(teles.size());
    for (TeleContext tele : teles) {
      boolean explicit = !(tele instanceof ImplicitContext);
      TypedExprContext typedExpr;
      if (explicit) {
        if (tele instanceof ExplicitContext) {
          typedExpr = ((ExplicitContext) tele).typedExpr();
        } else
        if (tele instanceof TeleLiteralContext) {
          arguments.add(new Concrete.TypeParameter<>(true, visitExpr(((TeleLiteralContext) tele).literal())));
          continue;
        } else
        if (tele instanceof TeleUniverseContext) {
          arguments.add(new Concrete.TypeParameter<>(true, visitExpr(((TeleUniverseContext) tele).universeAtom())));
          continue;
        } else {
          throw new IllegalStateException();
        }
      } else {
        typedExpr = ((ImplicitContext) tele).typedExpr();
      }
      if (typedExpr instanceof TypedContext) {
        arguments.add(new Concrete.TelescopeParameter<>(tokenPosition(tele.getStart()), explicit, getVarList(((TypedContext) typedExpr).expr(0), ((TypedContext) typedExpr).INFIX()), visitExpr(((TypedContext) typedExpr).expr(1))));
      } else
      if (typedExpr instanceof TypedVarsContext) {
        arguments.add(new Concrete.TelescopeParameter<>(tokenPosition(tele.getStart()), explicit, getVars((TypedVarsContext) typedExpr), visitExpr(((TypedVarsContext) typedExpr).expr())));
      } else
      if (typedExpr instanceof NotTypedContext) {
        arguments.add(new Concrete.TypeParameter<>(explicit, visitExpr(((NotTypedContext) typedExpr).expr())));
      } else {
        throw new IllegalStateException();
      }
    }
    return arguments;
  }

  @Override
  public Concrete.Expression visitAtomLiteral(AtomLiteralContext ctx) {
    return visitExpr(ctx.literal());
  }

  @Override
  public Concrete.NumericLiteral visitAtomNumber(AtomNumberContext ctx) {
    return new Concrete.NumericLiteral<>(tokenPosition(ctx.NUMBER().getSymbol()), Integer.parseInt(ctx.NUMBER().getText()));
  }

  @Override
  public Concrete.SigmaExpression visitSigma(SigmaContext ctx) {
    List<Concrete.TypeParameter<Position>> args = visitTeles(ctx.tele());
    for (Concrete.TypeParameter<Position> arg : args) {
      if (!arg.getExplicit()) {
        myErrorReporter.report(new ParserError(arg.getData(), "Fields in sigma types must be explicit"));
      }
    }
    return new Concrete.SigmaExpression<>(tokenPosition(ctx.getStart()), args);
  }

  @Override
  public Concrete.PiExpression visitPi(PiContext ctx) {
    return new Concrete.PiExpression<>(tokenPosition(ctx.getStart()), visitTeles(ctx.tele()), visitExpr(ctx.expr()));
  }

  private Concrete.Expression<Position> visitAtoms(Concrete.Expression<Position> expr, List<ArgumentContext> arguments) {
    for (ArgumentContext argument : arguments) {
      Concrete.Expression<Position> expr1;
      if (argument instanceof ArgumentExplicitContext) {
        expr1 = visitAtomFieldsAcc(((ArgumentExplicitContext) argument).atomFieldsAcc());
      } else
      if (argument instanceof ArgumentUniverseContext) {
        expr1 = visitExpr(((ArgumentUniverseContext) argument).universeAtom());
      } else
      if (argument instanceof ArgumentImplicitContext) {
        expr1 = visitExpr(((ArgumentImplicitContext) argument).expr());
      } else {
        throw new IllegalStateException();
      }
      expr = new Concrete.AppExpression<>(expr.getData(), expr, new Concrete.Argument<>(expr1, !(argument instanceof ArgumentImplicitContext)));
    }
    return expr;
  }

  @Override
  public Concrete.Expression visitBinOp(BinOpContext ctx) {
    //noinspection unchecked
    return parseBinOpSequence(ctx.binOpLeft(), parseImplementations(ctx.maybeNew(), ctx.implementStatements(), ctx.start, (Concrete.Expression<Position>) visit(ctx.binOpArg())), ctx.postfix(), ctx.start);
  }

  private Concrete.Expression parseBinOpSequence(List<BinOpLeftContext> leftCtxs, Concrete.Expression<Position> expression, List<PostfixContext> postfixCtxs, Token token) {
    Concrete.Expression<Position> left = null;
    Concrete.ReferenceExpression binOp = null;
    List<Abstract.BinOpSequenceElem> sequence = new ArrayList<>(leftCtxs.size() + postfixCtxs.size());

    for (BinOpLeftContext leftContext : leftCtxs) {
      String name = visitInfix(leftContext.infix());
      //noinspection unchecked
      Concrete.Expression<Position> expr = parseImplementations(leftContext.maybeNew(), leftContext.implementStatements(), leftContext.start, (Concrete.Expression<Position>) visit(leftContext.binOpArg()));

      if (left == null) {
        left = expr;
      } else {
        sequence.add(new Abstract.BinOpSequenceElem(binOp, expr));
      }

      for (PostfixContext postfixContext : leftContext.postfix()) {
        sequence.add(new Abstract.BinOpSequenceElem(new Concrete.ReferenceExpression<>(tokenPosition(postfixContext.start), null, visitPostfix(postfixContext)), null));
      }

      binOp = new Concrete.ReferenceExpression<>(tokenPosition(leftContext.infix().getStart()), null, name);
    }

    if (left == null) {
      left = expression;
    } else {
      sequence.add(new Abstract.BinOpSequenceElem(binOp, expression));
    }

    for (PostfixContext postfixContext : postfixCtxs) {
      sequence.add(new Abstract.BinOpSequenceElem(new Concrete.ReferenceExpression<>(tokenPosition(postfixContext.start), null, visitPostfix(postfixContext)), null));
    }

    return sequence.isEmpty() ? left : new Concrete.BinOpSequenceExpression<>(tokenPosition(token), left, sequence);
  }

  private Concrete.Expression<Position> visitExpr(Expr0Context ctx) {
    //noinspection unchecked
    return parseBinOpSequence(ctx.binOpLeft(), (Concrete.Expression<Position>) visit(ctx.binOpArg()), ctx.postfix(), ctx.start);
  }

  @Override
  public Concrete.Expression<Position> visitAtomFieldsAcc(AtomFieldsAccContext ctx) {
    Concrete.Expression<Position> expression = visitExpr(ctx.atom());
    for (FieldAccContext fieldAccContext : ctx.fieldAcc()) {
      if (fieldAccContext instanceof ClassFieldAccContext) {
        expression = new Concrete.ReferenceExpression<>(tokenPosition(fieldAccContext.getStart()), expression, visitId(((ClassFieldAccContext) fieldAccContext).id()));
      } else
      if (fieldAccContext instanceof SigmaFieldAccContext) {
        expression = new Concrete.ProjExpression<>(tokenPosition(fieldAccContext.getStart()), expression, Integer.parseInt(((SigmaFieldAccContext) fieldAccContext).NUMBER().getText()) - 1);
      } else {
        throw new IllegalStateException();
      }
    }
    return expression;
  }

  private List<Concrete.FunctionClause<Position>> visitClauses(ClausesContext ctx) {
    List<ClauseContext> clauses = ctx instanceof ClausesWithBracesContext ? ((ClausesWithBracesContext) ctx).clause() : ((ClausesWithoutBracesContext) ctx).clause();
    List<Concrete.FunctionClause<Position>> result = new ArrayList<>(clauses.size());
    for (ClauseContext clause : clauses) {
      result.add(visitClause(clause));
    }
    return result;
  }

  @Override
  public Concrete.FunctionClause<Position> visitClause(ClauseContext clauseCtx) {
    List<Concrete.Pattern<Position>> patterns = new ArrayList<>(clauseCtx.pattern().size());
    for (PatternContext patternCtx : clauseCtx.pattern()) {
      patterns.add(visitPattern(patternCtx));
    }
    return new Concrete.FunctionClause<>(tokenPosition(clauseCtx.start), patterns, clauseCtx.expr() == null ? null : visitExpr(clauseCtx.expr()));
  }

  private List<Concrete.ReferenceExpression<Position>> checkElimExpressions(List<? extends Concrete.Expression<Position>> expressions) {
    List<Concrete.ReferenceExpression<Position>> result = new ArrayList<>(expressions.size());
    for (Concrete.Expression<Position> elimExpr : expressions) {
      if (!(elimExpr instanceof Concrete.ReferenceExpression && ((Concrete.ReferenceExpression) elimExpr).getExpression() == null)) {
        myErrorReporter.report(new ParserError(elimExpr.getData(), "\\elim can be applied only to a local variable"));
        return null;
      }
      result.add((Concrete.ReferenceExpression<Position>) elimExpr);
    }
    return result;
  }

  @Override
  public Concrete.Expression<Position> visitCase(CaseContext ctx) {
    List<Concrete.Expression<Position>> elimExprs = new ArrayList<>(ctx.expr0().size());
    for (Expr0Context exprCtx : ctx.expr0()) {
      elimExprs.add(visitExpr(exprCtx));
    }
    List<Concrete.FunctionClause<Position>> clauses = new ArrayList<>(ctx.clause().size());
    for (ClauseContext clauseCtx : ctx.clause()) {
      clauses.add(visitClause(clauseCtx));
    }
    return new Concrete.CaseExpression<>(tokenPosition(ctx.getStart()), elimExprs, clauses);
  }

  @Override
  public Concrete.LetClause<Position> visitLetClause(LetClauseContext ctx) {
    List<Concrete.Parameter<Position>> arguments = visitLamTeles(ctx.tele());
    Concrete.Expression<Position> resultType = ctx.typeAnnotation() == null ? null : visitExpr(ctx.typeAnnotation().expr());
    return new Concrete.LetClause<>(tokenPosition(ctx.getStart()), visitId(ctx.id()), arguments, resultType, visitExpr(ctx.expr()));
  }

  @Override
  public Concrete.LetExpression visitLet(LetContext ctx) {
    List<Concrete.LetClause<Position>> clauses = new ArrayList<>();
    for (LetClauseContext clauseCtx : ctx.letClause()) {
      clauses.add(visitLetClause(clauseCtx));
    }

    return new Concrete.LetExpression<>(tokenPosition(ctx.getStart()), clauses, visitExpr(ctx.expr()));
  }

  private Position tokenPosition(Token token) {
    return new Position(myModule, token.getLine(), token.getCharPositionInLine());
  }
}