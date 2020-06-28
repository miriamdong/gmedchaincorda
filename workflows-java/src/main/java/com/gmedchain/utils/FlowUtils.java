package com.gmedchain.utils;

import com.gmedchain.schema.OrderSchemaV1;
import com.gmedchain.state.OrderState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.VaultService;
import net.corda.core.node.services.vault.Builder;
import net.corda.core.node.services.vault.CriteriaExpression;
import net.corda.core.node.services.vault.FieldInfo;
import net.corda.core.node.services.vault.QueryCriteria;

import static net.corda.core.node.services.vault.QueryCriteriaUtils.getField;

public class FlowUtils {
    /**
     * Retrieves state with the provided linearId from the local vault.
     */
    public static StateAndRef<OrderState> retrieveOrderState(UniqueIdentifier linearId, VaultService vaultService) throws NoSuchFieldException {
        QueryCriteria.VaultQueryCriteria generalCriteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL);
        FieldInfo attributeLinearId = getField("linearId", OrderSchemaV1.PersistentOrder.class);
        CriteriaExpression orderIndex = Builder.equal(attributeLinearId, linearId.getId());
        QueryCriteria customCriteria = new QueryCriteria.VaultCustomQueryCriteria(orderIndex);
        QueryCriteria criteria = generalCriteria.and(customCriteria);
        Vault.Page<OrderState> results = vaultService.queryBy(OrderState.class, criteria);
        return results.getStates().get(0);
    }
}