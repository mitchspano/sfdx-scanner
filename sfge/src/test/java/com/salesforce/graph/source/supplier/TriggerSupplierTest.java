package com.salesforce.graph.source.supplier;

import com.salesforce.collections.CollectionUtil;
import org.junit.jupiter.api.Test;

public class TriggerSupplierTest extends BaseSourceSupplierTest {
    /** A Trigger's {@code invoke()} method should count as a source. */
    @Test
    public void supplierLoadsTriggerMethods() {
        // spotless:off
        String sourceCode =
            "trigger AccountBefore on Account (before insert) {\n"
          + "    if (Trigger.new.size() > 5) {\n"
          + "        System.debug('asdf');\n"
          + "    }\n"
          + "}\n";
        // spotless:on
        testSupplier_positive(
                new String[] {sourceCode},
                new TriggerSupplier(),
                CollectionUtil.newTreeSetOf("AccountBefore#invoke@1"));
    }
}
