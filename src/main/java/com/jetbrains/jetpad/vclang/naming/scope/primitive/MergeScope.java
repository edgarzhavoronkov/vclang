package com.jetbrains.jetpad.vclang.naming.scope.primitive;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.jetbrains.jetpad.vclang.error.GeneralError;
import com.jetbrains.jetpad.vclang.naming.error.DuplicateDefinitionError;
import com.jetbrains.jetpad.vclang.term.Abstract;
import com.jetbrains.jetpad.vclang.util.Pair;

import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MergeScope implements MergingScope {
  private final Iterable<Scope> myScopes;

  public MergeScope(Iterable<Scope> scopes) {
    myScopes = scopes;
  }

  @Override
  public Set<String> getNames() {
    return StreamSupport.stream(myScopes.spliterator(), false).flatMap(s -> s.getNames().stream()).collect(Collectors.toSet());
  }

  private InvalidScopeException createException(final Abstract.Definition ref1, final Abstract.Definition ref2) {
    return new InvalidScopeException() {
      @Override
      public GeneralError toError() {
        return new DuplicateDefinitionError(ref1, ref2);
      }
    };

  }

  @Override
  public Abstract.Definition resolveName(String name) {
    Abstract.Definition resolved = null;
    for (Scope scope : myScopes) {
      Abstract.Definition ref = scope.resolveName(name);
      if (ref != null) {
        if (resolved == null) {
          resolved = ref;
        } else {
          throw createException(resolved, ref);
        }
      }
    }
    return resolved;
  }

  @Override
  public void findIntroducedDuplicateNames(BiConsumer<Abstract.Definition, Abstract.Definition> reporter) {
    Multimap<String, Abstract.Definition> known = HashMultimap.create();
    for (Scope scope : myScopes) {
      for (String name : scope.getNames()) {
        try {
          Abstract.Definition ref = scope.resolveName(name);
          Collection<Abstract.Definition> prevs = known.get(name);
          for (Abstract.Definition prev : prevs) {
            if (prev != ref) {
              reporter.accept(prev, ref);
            }
          }
          known.put(name, ref);
        } catch (InvalidScopeException ignored) {
        }
      }
    }
  }

  @Override
  public void findIntroducedDuplicateInstances(BiConsumer<Abstract.ClassViewInstance, Abstract.ClassViewInstance> reporter) {
    Multimap<Pair<Abstract.ReferableSourceNode, Abstract.ReferableSourceNode>, Abstract.ClassViewInstance> known = HashMultimap.create();
    for (Scope scope : myScopes) {
      for (Abstract.ClassViewInstance instance : scope.getInstances()) {
        Pair<Abstract.ReferableSourceNode, Abstract.ReferableSourceNode> pair = new Pair<>(instance.getClassView().getReferent(), instance.getClassifyingDefinition());
        Collection<Abstract.ClassViewInstance> prevs = known.get(pair);
        for (Abstract.ClassViewInstance prev : prevs) {
          if (prev != instance) {
            reporter.accept(prev, instance);
          }
        }
        known.put(pair, instance);
      }
    }
  }

  @Override
  public Collection<? extends Abstract.ClassViewInstance> getInstances() {
    return StreamSupport.stream(myScopes.spliterator(), false).flatMap(s -> s.getInstances().stream()).collect(Collectors.toSet());
  }
}
