package com.jetbrains.jetpad.vclang.frontend.resolving.visitor;

import com.jetbrains.jetpad.vclang.error.ErrorReporter;
import com.jetbrains.jetpad.vclang.error.GeneralError;
import com.jetbrains.jetpad.vclang.frontend.resolving.NamespaceProviders;
import com.jetbrains.jetpad.vclang.naming.NameResolver;
import com.jetbrains.jetpad.vclang.naming.namespace.ModuleNamespace;
import com.jetbrains.jetpad.vclang.frontend.resolving.ResolveListener;
import com.jetbrains.jetpad.vclang.naming.scope.FilteredScope;
import com.jetbrains.jetpad.vclang.naming.scope.OverridingScope;
import com.jetbrains.jetpad.vclang.naming.scope.Scope;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.term.AbstractStatementVisitor;

import java.util.HashSet;
import java.util.List;

public class StatementResolveNameVisitor implements AbstractStatementVisitor<DefinitionResolveNameVisitor.Flag, Object> {
  private final NamespaceProviders myNsProviders;
  private final List<String> myContext;
  private final NameResolver myNameResolver;
  private final ErrorReporter myErrorReporter;
  private final ResolveListener myResolveListener;
  private Scope myScope;

  public StatementResolveNameVisitor(NamespaceProviders nsProviders, NameResolver nameResolver, ErrorReporter errorReporter, Scope parentScope, List<String> context, ResolveListener resolveListener) {
    myNsProviders = nsProviders;
    myNameResolver = nameResolver;
    myErrorReporter = errorReporter;
    myScope = parentScope;
    myContext = context;
    myResolveListener = resolveListener;
  }

  @Override
  public Void visitDefine(Abstract.DefineStatement stat, DefinitionResolveNameVisitor.Flag flag) {
    DefinitionResolveNameVisitor visitor = new DefinitionResolveNameVisitor(myNsProviders, myScope, myContext, myNameResolver, myErrorReporter, myResolveListener);
    stat.getDefinition().accept(visitor, true);
    return null;
  }

  @Override
  public Void visitNamespaceCommand(Abstract.NamespaceCommandStatement stat, DefinitionResolveNameVisitor.Flag flag) {
    if (flag == DefinitionResolveNameVisitor.Flag.MUST_BE_DYNAMIC) {
      myErrorReporter.report(new GeneralError("Namespace commands are not allowed in this context", stat));
      return null;
    }

    if (stat.getResolvedClass() == null) {
      final Abstract.Definition referredClass;
      if (stat.getModulePath() == null) {
        if (stat.getPath().isEmpty()) {
          myErrorReporter.report(new GeneralError("Structure error: empty namespace command", stat));
          return null;
        }
        referredClass = myNameResolver.resolveDefinition(myScope, stat.getPath(), myNsProviders.statics);
      } else {
        ModuleNamespace moduleNamespace = myNameResolver.resolveModuleNamespace(stat.getModulePath(), myNsProviders.modules);
        Abstract.ClassDefinition moduleClass = moduleNamespace != null ? moduleNamespace.getRegisteredClass() : null;
        if (moduleClass == null) {
          myErrorReporter.report(new GeneralError("Module not found: " + stat.getModulePath(), stat));
          return null;
        }
        if (stat.getPath().isEmpty()) {
          referredClass = moduleNamespace.getRegisteredClass();
        } else {
          referredClass = myNameResolver.resolveDefinition(myNsProviders.statics.forDefinition(moduleClass), stat.getPath(), myNsProviders.statics);
        }
      }

      if (referredClass == null) {
        myErrorReporter.report(new GeneralError("Class not found", stat));
        return null;
      }
      myResolveListener.nsCmdResolved(stat, referredClass);
    }

    if (stat.getKind().equals(Abstract.NamespaceCommandStatement.Kind.OPEN)) {
      Scope scope = myNsProviders.statics.forDefinition(stat.getResolvedClass());
      if (stat.getNames() != null) {
        scope = new FilteredScope(scope, new HashSet<>(stat.getNames()), !stat.isHiding());
      }
      myScope = OverridingScope.merge(myScope, scope, myErrorReporter);
    }

    return null;
  }

  public Scope getCurrentScope() {
    return myScope;
  }
}