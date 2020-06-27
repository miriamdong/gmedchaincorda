package com.gmedchain.state;

import com.gmedchain.common.Order;
import com.gmedchain.contract.OrderContract;
import com.gmedchain.schema.OrderSchemaV1;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;

import java.util.Arrays;
import java.util.List;

import static com.gmedchain.schema.OrderSchemaV1.*;

/**
 * The state object recording Order agreements between associated parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 */
@BelongsToContract(OrderContract.class)
public class OrderState implements LinearState, QueryableState {
    private final Order order;
    private final Party buyer;
    private final Party seller;
    private final Party shipper;
    private  Party owner;
    private final UniqueIdentifier linearId;

    /**
     * @param order the order including the order info.
     * @param buyer the party issuing the Order.
     * @param seller the party receiving the Order.
     * @param shipper the party receiving the Order.
     * @param owner
     * @param linearId the unique Id shared by all OrderState states throughout history within the vaults of all parties.
     */
    public OrderState(Order order, Party buyer, Party seller, Party shipper, Party owner, UniqueIdentifier linearId)
    {
        this.order = order;
        this.buyer = buyer;
        this.seller = seller;
        this.shipper = shipper;
        this.owner = owner;
        this.linearId = linearId;
    }

    public Order getOrder() { return order; }
    public Party getBuyer() { return buyer; }
    public Party getSeller() { return seller; }
    public Party getShipper() { return shipper; }
    public Party getOwner() { return owner; }
    public void setOwner(Party owner){ this.owner = owner;}

    @Override public UniqueIdentifier getLinearId() { return linearId; }
    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList(buyer, seller, shipper);
    }

    @Override public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof OrderSchemaV1) {
            return new PersistentOrder(
                    this.buyer.getName().toString(),
                    this.seller.getName().toString(),
                    this.shipper.getName().toString(),
                    this.owner.getName().toString(),
                    this.order.getBuyerAddress(),
                    this.order.getSellerAddress(),
                    this.order.getProductSku(),
                    this.order.getProductName(),
                    this.order.getProductPrice(),
                    this.order.getQty(),
                    this.order.getShippingCost(),
                    this.order.getStatus(),
                    this.linearId.getId());
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override public Iterable<MappedSchema> supportedSchemas() {
        return Arrays.asList(new OrderSchemaV1());
    }

    @Override
    public String toString() {
        return String.format("OrderState(lender=%s, borrower=%s, shipper=%s, linearId=%s)", buyer, seller, shipper, linearId);
    }
}