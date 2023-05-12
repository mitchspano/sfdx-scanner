package com.salesforce.graph.build;

import com.salesforce.apex.jorje.ASTConstants;
import com.salesforce.apex.jorje.JorjeNode;
import com.salesforce.collections.CollectionUtil;
import com.salesforce.exception.UnexpectedException;
import com.salesforce.graph.Schema;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;

/**
 * Base class used by subclasses to import Apex Standard Classes and Custom supplied classes into
 * the graph database.
 */
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
abstract class AbstractApexVertexBuilder {
    private static final Logger LOGGER = LogManager.getLogger(AbstractApexVertexBuilder.class);
    protected final GraphTraversalSource g;

    protected AbstractApexVertexBuilder(GraphTraversalSource g) {
        this.g = g;
    }

    void buildVertices(JorjeNode node, String fileName) {
        buildVertices(node, null, fileName);
    }

    private void buildVertices(JorjeNode node, Vertex vNodeParam, String fileName) {
        // Keep track when this is the initial root node for the file. #afterFileInsert is only
        // called
        // for this node.
        final Vertex rootVNode;
        final Vertex vNode;

        // It's possible for this method to be called on vertices that don't exist yet. If that's
        // the case, create a
        // corresponding vertex.
        if (vNodeParam == null) {
            vNode = g.addV(node.getLabel()).next();
            rootVNode = vNode;
        } else {
            rootVNode = null;
            vNode = vNodeParam;
        }

        addProperties(g, node, vNode);

        // If the source name is available, use it to populate the corresponding property.
        if (!StringUtils.isEmpty(fileName)) {
            // The engine assumes that only certain types of nodes can be roots. We need to enforce
            // that assumption and
            // fail noisily if it's violated.
            if (!GremlinUtil.ROOT_VERTICES.contains(vNode.label())) {
                throw new UnexpectedException("Unexpected root vertex of type " + vNode.label());
            }
            vNode.property(Schema.FILE_NAME, fileName);
        }

        final List<JorjeNode> children = node.getChildren();
        processChildren(node, vNode, children);
        afterInsert(g, node, vNode);
        if (rootVNode != null) {
            // Only call this for the root node
            afterFileInsert(g, rootVNode);
        }
    }

    private final void processChildren(JorjeNode node, Vertex vNode, List<JorjeNode> children) {
        Set<Vertex> verticesAddressed = new HashSet<>();
        Vertex vPreviousSibling = null;
        for (int i = 0; i < children.size(); i++) {
            JorjeNode child = children.get(i);
            // It's possible that synthetic node creation made the child effectively
            // the first/last child. So update that information here.
            child.setFirstChild(i == 0);
            child.setLastChild(i == children.size() - 1);
            Vertex vChild = g.addV(child.getLabel()).next();
            addProperties(g, child, vChild);
            // Handle static block if we are looking at a <clinit> method that has a block
            // statement. See {@linkplain StaticBlockUtil} on why this is needed and how we handle
            // it.
            if (node != null && StaticBlockUtil.isStaticBlockStatement(node, child)) {
                Vertex syntheticParent =
                        StaticBlockUtil.createSyntheticStaticBlockMethod(g, vNode, i);
                GremlinVertexUtil.addParentChildRelationship(g, syntheticParent, vChild);
                verticesAddressed.add(syntheticParent);
            } else if (node != null
                    && BlockStatementUtil.requiresSyntheticBlockStatement(node, child)) {
                // If there ought to be a BlockStatement enclosing this child, then we should create
                // one and use a recursive call to process all remaining children as though they
                // were children of the synthesized BlockStatement.
                // ASSUMPTION: All subsequent children belong in this block statement.
                //             If this assumption is invalidated, we'll need logic
                //             to identify which children should be re-parented.
                Vertex syntheticParent =
                        BlockStatementUtil.createSyntheticBlockStatement(g, vNode, i);
                if (vPreviousSibling != null) {
                    g.addE(Schema.NEXT_SIBLING)
                            .from(vPreviousSibling)
                            .to(syntheticParent)
                            .iterate();
                }
                processChildren(null, syntheticParent, children.subList(i, children.size() - 1));
                verticesAddressed.add(syntheticParent);
                break;
            } else {
                GremlinVertexUtil.addParentChildRelationship(g, vNode, vChild);
            }
            if (vPreviousSibling != null) {
                g.addE(Schema.NEXT_SIBLING).from(vPreviousSibling).to(vChild).iterate();
            }
            vPreviousSibling = vChild;

            // To save memory in the graph, don't pass the source name into recursive calls.
            buildVertices(child, vChild, null);
        }
        // Execute afterInsert() on each vertex we addressed.
        for (Vertex vertexAddressed : verticesAddressed) {
            afterInsert(g, node, vertexAddressed);
        }
    }

    /**
     * Adds edges to Method vertices after they are inserted. TODO: Replace this with a listener or
     * visitor pattern for more general purpose solutions
     */
    private final void afterInsert(GraphTraversalSource g, JorjeNode node, Vertex vNode) {
        if (node.getLabel().equals(ASTConstants.NodeType.METHOD)
                && vNode.label().equals(ASTConstants.NodeType.METHOD)) {
            // If we just added a method, create forward and
            // backward code flow for the contents of the method
            MethodPathBuilderVisitor.apply(g, vNode);
        }
    }

    /**
     * Invoked after a file is completely processed
     *
     * @param vNode root node that corresponds to the file
     */
    protected void afterFileInsert(GraphTraversalSource g, Vertex vNode) {
        // If the root (class/trigger/etc) contained any static blocks,
        // create an invoker method to invoke the static blocks
        StaticBlockUtil.createSyntheticStaticBlockInvocation(g, vNode);
    }

    protected void addProperties(GraphTraversalSource g, JorjeNode node, Vertex vNode) {
        GraphTraversal<Vertex, Vertex> traversal = g.V(vNode.id());

        TreeSet<String> previouslyInsertedKeys = CollectionUtil.newTreeSet();
        for (Map.Entry<String, Object> entry : node.getProperties().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            value = adjustPropertyValue(node, key, value);
            GremlinVertexUtil.addProperty(previouslyInsertedKeys, traversal, key, value);
        }

        for (Map.Entry<String, Object> entry : getAdditionalProperties(node).entrySet()) {
            GremlinVertexUtil.addProperty(
                    previouslyInsertedKeys, traversal, entry.getKey(), entry.getValue());
        }

        // Commit the changes.
        traversal.next();
    }

    /** Allow subclasses to modify the stored value */
    protected Object adjustPropertyValue(JorjeNode node, String key, Object value) {
        return value;
    }

    /** Add additional properties to the node that aren't present in the orginal AST */
    protected Map<String, Object> getAdditionalProperties(JorjeNode node) {
        return new HashMap<>();
    }
}
