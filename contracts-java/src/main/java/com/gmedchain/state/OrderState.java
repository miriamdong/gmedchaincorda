package com.gmedchain.state;

import com.gmedchain.contract.OrderContract;
import com.gmedchain.schema.OrderSchemaV1;
import com.gmedchain.types.Types;
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

/**
 * The state object recording Order agreements between associated parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 */
@BelongsToContract(OrderContract.class)
public class OrderState implements LinearState, QueryableState {
    private final Party buyer;
    private final Party seller;
    private final Party shipper;
    private final String productSKU;
    private final String productName;
    private final float productPrice;
    private final Integer productQty;
    private final String buyerAddress;
    private final String sellerAddress;
    private final float shippingCost;
    private final Types.OrderTypes status;
    private final UniqueIdentifier linearId;

    /**
     * @param productSKU the SKU of the product.
     * @param productName the name of the product.
     * @param productPrice the price of the product.
     * @param productQty the quantity of the product.
     * @param buyerAddress the address of the buyer.
     * @param sellerAddress the address of the seller.
     * @param shippingCost the shipment price.
     * @param buyer the party issuing the Order.
     * @param seller the party receiving the Order.
     * @param status
     */
    public OrderState(String productSKU,
                      String productName,
                      float productPrice,
                      int productQty,
                      float shippingCost,
                      Party buyer,
                      Party seller,
                      Party shipper,
                      String buyerAddress,
                      String sellerAddress,
                      UniqueIdentifier linearId)
    {
        this.productSKU = productSKU;
        this.productName = productName;
        this.productPrice = productPrice;
        this.productQty = productQty;
        this.shippingCost = shippingCost;
        this.buyer = buyer;
        this.seller = seller;
        this.shipper = shipper;
        this.buyerAddress = buyerAddress;
        this.sellerAddress = sellerAddress;
        this.status = Types.OrderTypes.Ordered;
        this.linearId = linearId;
    }

    public String getProductSKU() {
        return productSKU;
    }
    public String getProductName() {
        return productName;
    }
    public float getProductPrice() {
        return productPrice;
    }
    public Integer getProductQty() {
        return productQty;
    }
    public String getBuyerAddress() {
        return buyerAddress;
    }
    public String getSellerAddress() {
        return sellerAddress;
    }
    public float getShippingCost() {
        return shippingCost;
    }
    public Party getBuyer() { return buyer; }
    public Party getSeller() { return seller; }
    public Party getShipper() { return shipper; }
    public Types.OrderTypes getStatus() { return status; }

    @Override public UniqueIdentifier getLinearId() { return linearId; }
    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList(buyer, seller, shipper);
    }

    @Override public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof OrderSchemaV1) {
            return new OrderSchemaV1.PersistentOrder(
                    this.buyer.getName().toString(),
                    this.seller.getName().toString(),
                    this.shipper.getName().toString(),
                    this.buyerAddress,
                    this.sellerAddress,
                    this.productSKU,
                    this.productName,
                    this.productPrice,
                    this.productQty,
                    this.shippingCost,
                    this.status.ordinal(),
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