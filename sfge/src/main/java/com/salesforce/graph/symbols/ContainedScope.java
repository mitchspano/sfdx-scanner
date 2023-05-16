package com.salesforce.graph.symbols;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

public abstract class ContainedScope extends PathScopeVisitor {
    protected ContainedScope(GraphTraversalSource g) {
        super(g);
    }

    public ContainedScope(GraphTraversalSource g, PathScopeVisitor inheritedScope) {
        super(g, inheritedScope);
    }

    protected ContainedScope(PathScopeVisitor other) {
        super(other);
    }
}
