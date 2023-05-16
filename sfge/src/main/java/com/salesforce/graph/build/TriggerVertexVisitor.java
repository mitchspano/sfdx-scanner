package com.salesforce.graph.build;

import com.salesforce.apex.jorje.ASTConstants.NodeType;
import com.salesforce.exception.UnexpectedException;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;

public class TriggerVertexVisitor {


    public static void apply(GraphTraversalSource g, Vertex triggerVertex) {
        if (!triggerVertex.label().equals(NodeType.USER_TRIGGER)) {
            throw new UnexpectedException(triggerVertex);
        }
    }
}
