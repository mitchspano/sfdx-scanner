package com.salesforce.graph.source.supplier;

import com.salesforce.apex.jorje.ASTConstants;
import com.salesforce.graph.Schema;
import com.salesforce.graph.build.CaseSafePropertyUtil;
import com.salesforce.graph.vertex.MethodVertex;
import com.salesforce.graph.vertex.SFVertexFactory;
import java.util.List;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;

public class TriggerSupplier extends AbstractSourceSupplier {

    @Override
    public List<MethodVertex> getVertices(GraphTraversalSource g, List<String> targetFiles) {
        return SFVertexFactory.loadVertices(
                g,
                rootMethodTraversal(
                                g, new String[] {ASTConstants.NodeType.USER_TRIGGER}, targetFiles)
                        .where(
                                CaseSafePropertyUtil.H.has(
                                        ASTConstants.NodeType.METHOD,
                                        Schema.NAME,
                                        Schema.TRIGGER_INVOKE_METHOD)));
    }

    @Override
    public boolean isPotentialSource(MethodVertex methodVertex) {
        return methodVertex.getName().equalsIgnoreCase(Schema.TRIGGER_INVOKE_METHOD)
                && methodVertex.getParentTrigger().isPresent();
    }
}
