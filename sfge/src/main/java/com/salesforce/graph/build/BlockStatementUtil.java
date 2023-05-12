package com.salesforce.graph.build;

import com.salesforce.apex.jorje.ASTConstants.NodeType;
import com.salesforce.apex.jorje.JorjeNode;
import com.salesforce.collections.CollectionUtil;
import com.salesforce.graph.Schema;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;

/**
 * Handles creation of synthetic vertices to gracefully handle missing BlockStatements.
 *
 * <p>Consider this example: <code>
 * trigger AccountBefore on Account (before insert) {
 *     System.debug('asdf');
 * }
 * </code> In Jorje's compilation structure, this is represented as follows:<code>
 * <UserTrigger>
 *     <ModifierNode/>
 *     <Method>
 *         <ModifierNode/>
 *         <ExpressionStatement>
 *             ...
 *         </ExpressionStatement>
 *     </Method>
 * </UserTrigger>
 * </code>
 *
 * <p>An ordinary method would enclose its body in a {@code <BlockStatement>}, and our path
 * expansion assumes the existence of such a node. So we need to manually create one. Hence, the
 * existence of this class.
 */
public final class BlockStatementUtil {
    private static final Map<Object, Vertex> SYNTHESIZED_BLOCKS_BY_METHOD = new HashMap<>();

    /**
     * Create a synthetic BlockStatement to be nested beneath the provided Method, or return one
     * that has been already created.
     */
    public static Vertex createSyntheticBlockStatement(
            GraphTraversalSource g, Vertex methodVertex, int index) {
        if (!SYNTHESIZED_BLOCKS_BY_METHOD.containsKey(methodVertex.id())) {
            Vertex blockStatementVertex = g.addV(NodeType.BLOCK_STATEMENT).next();
            String definingType = methodVertex.value(Schema.DEFINING_TYPE);
            Map<String, Object> properties = new HashMap<>();
            properties.put(Schema.IS_SYNTHETIC, true);
            properties.put(Schema.DEFINING_TYPE, definingType);
            properties.put(Schema.CHILD_INDEX, index);
            properties.put(Schema.FIRST_CHILD, index == 0);
            // ASSUMPTION: By the time we need to synthesize a block statement, all parameters, etc
            // have been processed.
            properties.put(Schema.LAST_CHILD, true);

            TreeSet<String> previouslyInsertedKeys = CollectionUtil.newTreeSet();
            GraphTraversal<Vertex, Vertex> traversal = g.V(blockStatementVertex.id());

            for (Map.Entry<String, Object> entry : properties.entrySet()) {
                GremlinVertexUtil.addProperty(
                        previouslyInsertedKeys, traversal, entry.getKey(), entry.getValue());
                previouslyInsertedKeys.add(entry.getKey());
            }

            // Commit the changes.
            traversal.next();
            GremlinVertexUtil.addParentChildRelationship(g, methodVertex, blockStatementVertex);
            SYNTHESIZED_BLOCKS_BY_METHOD.put(methodVertex.id(), blockStatementVertex);
        }
        return SYNTHESIZED_BLOCKS_BY_METHOD.get(methodVertex.id());
    }

    /**
     * Determine whether the provided parent and child nodes require an intermediary BlockStatement.
     */
    public static boolean requiresSyntheticBlockStatement(JorjeNode node, JorjeNode child) {
        // A synthetic block statement is required when a method vertex has vertex that is neither
        // a parameter, a modifier, nor a block statement.
        String parentLabel = node.getLabel();
        String childLabel = child.getLabel();
        if (!parentLabel.equalsIgnoreCase(NodeType.METHOD)) {
            return false;
        }
        return !childLabel.equalsIgnoreCase(NodeType.MODIFIER_NODE)
                && !childLabel.equalsIgnoreCase(NodeType.PARAMETER)
                && !childLabel.equalsIgnoreCase(NodeType.BLOCK_STATEMENT);
    }

    private BlockStatementUtil() {}
}
