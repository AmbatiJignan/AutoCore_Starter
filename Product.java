package com.autocore.model;

import java.io.Serializable;
import java.math.BigDecimal;

public class Product implements Serializable {
    private static final long serialVersionUID = 1L;

    private int productId;
    private int stockQuantity;
    private int minimumStock;
    private String partNumber;
    private String productName;
    private String brand;
    private String vehicleModel;
    private String category;
    private BigDecimal purchasePrice;
    private BigDecimal sellingPrice;

    public Product() {
        this.purchasePrice = BigDecimal.ZERO;
        this.sellingPrice = BigDecimal.ZERO;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int v) {
        productId = v;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int v) {
        stockQuantity = v;
    }

    public int getMinimumStock() {
        return minimumStock;
    }

    public void setMinimumStock(int v) {
        minimumStock = v;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String v) {
        partNumber = v;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String v) {
        productName = v;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String v) {
        brand = v;
    }

    public String getVehicleModel() {
        return vehicleModel;
    }

    public void setVehicleModel(String v) {
        vehicleModel = v;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String v) {
        category = v;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal v) {
        purchasePrice = v;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal v) {
        sellingPrice = v;
    }

    public boolean isLowStock() {
        return stockQuantity <= minimumStock;
    }

    public boolean isOutOfStock() {
        return stockQuantity <= 0;
    }

    public BigDecimal getPurchaseValuation() {
        return (purchasePrice != null) ? purchasePrice.multiply(BigDecimal.valueOf(stockQuantity)) : BigDecimal.ZERO;
    }

    public BigDecimal getSellingValuation() {
        return (sellingPrice != null) ? sellingPrice.multiply(BigDecimal.valueOf(stockQuantity)) : BigDecimal.ZERO;
    }

    public BigDecimal getGrossMargin() {
        return (sellingPrice != null && purchasePrice != null) ? sellingPrice.subtract(purchasePrice) : BigDecimal.ZERO;
    }
}
