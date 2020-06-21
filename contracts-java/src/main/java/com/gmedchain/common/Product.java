package com.gmedchain.common;

import net.corda.core.serialization.CordaSerializable;

@CordaSerializable
public class Product {
    public String SKU;
    public String Name;
    public float Price;

    public Product(String SKU, String name, float price) {
        this.SKU = SKU;
        Name = name;
        Price = price;
    }
}
