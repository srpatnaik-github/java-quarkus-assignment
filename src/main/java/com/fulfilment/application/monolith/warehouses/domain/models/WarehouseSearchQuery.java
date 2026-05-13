package com.fulfilment.application.monolith.warehouses.domain.models;

public class WarehouseSearchQuery {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final String DEFAULT_SORT_BY = "createdAt";
    public static final String DEFAULT_SORT_ORDER = "asc";

    private final String location;
    private final Integer minCapacity;
    private final Integer maxCapacity;
    private final String sortBy;
    private final String sortOrder;
    private final int page;
    private final int pageSize;

    private WarehouseSearchQuery(Builder builder) {
        this.location    = builder.location;
        this.minCapacity = builder.minCapacity;
        this.maxCapacity = builder.maxCapacity;
        this.sortBy      = builder.sortBy;
        this.sortOrder   = builder.sortOrder;
        this.page        = builder.page;
        this.pageSize    = builder.pageSize;
    }

    public String getLocation()       { return location; }
    public Integer getMinCapacity()   { return minCapacity; }
    public Integer getMaxCapacity()   { return maxCapacity; }
    public String getSortBy()         { return sortBy; }
    public String getSortOrder()      { return sortOrder; }
    public int getPage()              { return page; }
    public int getPageSize()          { return pageSize; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String location;
        private Integer minCapacity;
        private Integer maxCapacity;
        private String sortBy    = DEFAULT_SORT_BY;
        private String sortOrder = DEFAULT_SORT_ORDER;
        private int page         = 0;
        private int pageSize     = DEFAULT_PAGE_SIZE;

        public Builder location(String location)          { this.location = location; return this; }
        public Builder minCapacity(Integer minCapacity)   { this.minCapacity = minCapacity; return this; }
        public Builder maxCapacity(Integer maxCapacity)   { this.maxCapacity = maxCapacity; return this; }
        public Builder sortBy(String sortBy)              { this.sortBy = sortBy != null ? sortBy : DEFAULT_SORT_BY; return this; }
        public Builder sortOrder(String sortOrder)        { this.sortOrder = sortOrder != null ? sortOrder : DEFAULT_SORT_ORDER; return this; }
        public Builder page(Integer page)                 { this.page = page != null ? page : 0; return this; }
        public Builder pageSize(Integer pageSize)         { this.pageSize = pageSize != null ? Math.min(pageSize, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE; return this; }

        public WarehouseSearchQuery build() { return new WarehouseSearchQuery(this); }
    }
}