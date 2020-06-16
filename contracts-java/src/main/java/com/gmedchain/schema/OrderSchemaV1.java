package com.gmedchain.schema;

import com.gmedchain.types.Types;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.UUID;

/**
 * An OrderState schema.
 */
public class OrderSchemaV1 extends MappedSchema {
    public OrderSchemaV1() {
        super(OrderSchemaV1.class, 1, Arrays.asList(PersistentOrder.class));
    }

    @Entity
    @Table(name = "order_states")
    public static class PersistentOrder extends PersistentState {
        @Column(name = "buyer") private final String buyer;
        @Column(name = "seller") private final String seller;
        @Column(name = "shipper") private final String shipper;
        @Column(name = "buyer_address") private final String buyerAddress;
        @Column(name = "seller_address") private final String sellerAddress;
        @Column(name = "product_sku") private final String productSKU;
        @Column(name = "product_name") private final String productName;
        @Column(name = "product_price") private final float productPrice;
        @Column(name = "product_qty") private final int productQty;
        @Column(name = "shipping_cost") private final float shippingCost;
        @Column(name = "status") private final int status;
        @Column(name = "linear_id") private final UUID linearId;

        public PersistentOrder(
                String buyer,
                String seller,
                String shipper,
                String buyerAddress,
                String sellerAddress,
                String productSKU,
                String productName,
                float productPrice,
                Integer productQty,
                float shippingCost,
                int status,
                UUID linearId) {
            this.buyer = buyer;
            this.seller = seller;
            this.shipper = shipper;
            this.buyerAddress = buyerAddress;
            this.sellerAddress = sellerAddress;
            this.productSKU = productSKU;
            this.productName = productName;
            this.productPrice = productPrice;
            this.productQty = productQty;
            this.shippingCost = shippingCost;
            this.status = status;
            this.linearId = linearId;
        }

        // Default constructor required by hibernate.
        public PersistentOrder() {
            this.buyer = null;
            this.seller = null;
            this.shipper = null;
            this.buyerAddress = null;
            this.sellerAddress = null;
            this.productSKU = null;
            this.productName = null;
            this.productPrice = 0;
            this.productQty = 0;
            this.shippingCost = 0;
            this.status = 0;
            this.linearId = null;
        }

        public String getBuyer() {
            return buyer;
        }

        public String getSeller() {
            return seller;
        }

        public String getShipper() {
            return shipper;
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

        public int getProductQty() {
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

        public int getOrderStatus() { return status; }

        public UUID getLinearId() {
            return linearId;
        }
    }
}