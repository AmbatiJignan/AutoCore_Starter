package com.autocore.model;

import java.io.Serializable;
import java.util.Date;

public class StockMovement implements Serializable {
    private static final long serialVersionUID = 1L;

    private int movementId;
    private int productId;
    private String partNumber;
    private String productName;
    private String movementType; // OPENING_STOCK, PURCHASE_RECEIPT, SALE, SALE_CANCEL, MANUAL_ADJUSTMENT
    private int quantityChange;
    private int previousStock;
    private int resultingStock;
    private String referenceId;
    private String notes;
    private Integer userId;
    private String userName;
    private Date createdAt;

    public StockMovement() {}

    public int getMovementId() {
        return movementId;
    }

    public void setMovementId(int movementId) {
        this.movementId = movementId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(String partNumber) {
        this.partNumber = partNumber;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getMovementType() {
        return movementType;
    }

    public void setMovementType(String movementType) {
        this.movementType = movementType;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public int getPreviousStock() {
        return previousStock;
    }

    public void setPreviousStock(int previousStock) {
        this.previousStock = previousStock;
    }

    public int getResultingStock() {
        return resultingStock;
    }

    public void setResultingStock(int resultingStock) {
        this.resultingStock = resultingStock;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getTypeBadgeClass() {
        if ("PURCHASE_RECEIPT".equals(movementType)) return "pill success";
        if ("SALE".equals(movementType)) return "pill info";
        if ("SALE_CANCEL".equals(movementType)) return "pill warning";
        if ("OPENING_STOCK".equals(movementType)) return "pill neutral";
        return "pill default";
    }

    public String getFormattedType() {
        if ("PURCHASE_RECEIPT".equals(movementType)) return "Purchase Receipt";
        if ("SALE".equals(movementType)) return "Sale / Invoice";
        if ("SALE_CANCEL".equals(movementType)) return "Sale Cancellation";
        if ("OPENING_STOCK".equals(movementType)) return "Opening Stock";
        if ("MANUAL_ADJUSTMENT".equals(movementType)) return "Manual Adjustment";
        return movementType;
    }
}
