package com.gmedchain.common;

import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public class Order {
    private final String productSku;
    private final String productName;
    private final float productPrice;
    private final int qty;
    private final float shippingCost;
    private final int status;
    private final String buyerAddress;
    private final String sellerAddress;

    public Order() {
        productSku = null;
        productName = null;
        productPrice = 0;
        this.qty = 0;
        this.shippingCost = 0;
        this.status = 0;
        this.buyerAddress = null;
        this.sellerAddress = null;
    }

    @ConstructorForDeserialization
    public Order(String productSku, String productName, float productPrice, int qty, float shippingCost, int status, String buyerAddress, String sellerAddress) {
        this.productSku = productSku;
        this.productName = productName;
        this.productPrice = productPrice;
        this.qty = qty;
        this.shippingCost = shippingCost;
        this.status = status;
        this.buyerAddress = buyerAddress;
        this.sellerAddress = sellerAddress;
    }

    public String getProductSku() {
        return productSku;
    }

    public String getProductName() {
        return productName;
    }

    public float getProductPrice() {
        return productPrice;
    }

    public int getQty() {
        return qty;
    }

    public float getShippingCost() {
        return shippingCost;
    }

    public int getStatus() {
        return status;
    }

    public String getBuyerAddress() {
        return buyerAddress;
    }

    public String getSellerAddress() {
        return sellerAddress;
    }
}
