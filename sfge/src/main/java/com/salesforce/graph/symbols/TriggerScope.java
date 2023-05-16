package com.salesforce.graph.symbols;

import com.salesforce.apex.jorje.ASTConstants;
import com.salesforce.exception.UnexpectedException;
import com.salesforce.graph.Schema;
import com.salesforce.graph.build.CaseSafePropertyUtil;
import com.salesforce.graph.vertex.SFVertexFactory;
import com.salesforce.graph.vertex.UserTriggerVertex;
import java.util.Optional;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

public class TriggerScope extends ContainedScope {
    private UserTriggerVertex userTrigger;

    private TriggerScope(GraphTraversalSource g, UserTriggerVertex userTrigger) {
        super(g);
        this.userTrigger = userTrigger;
    }

    protected TriggerScope(TriggerScope other) {
        super(other);
        this.userTrigger = other.userTrigger;
    }

    public static Optional<TriggerScope> getOptional(GraphTraversalSource g, String triggerName) {
        UserTriggerVertex userTrigger =
                SFVertexFactory.loadSingleOrNull(
                        g,
                        g.V()
                                .where(
                                        CaseSafePropertyUtil.H.has(
                                                ASTConstants.NodeType.USER_TRIGGER,
                                                Schema.DEFINING_TYPE,
                                                triggerName)));
        if (userTrigger != null) {
            return Optional.of(new TriggerScope(g, userTrigger));
        } else {
            return Optional.empty();
        }
    }

    public static TriggerScope get(GraphTraversalSource g, String triggerName) {
        return getOptional(g, triggerName).orElseThrow(() -> new UnexpectedException(triggerName));
    }
}
