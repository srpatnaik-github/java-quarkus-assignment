package com.fulfilment.application.monolith.warehouses.domain.models;

import java.util.List;

public class WarehouseSearchPage<T> {

    private final List<T> data;
    private final int page;
    private final int pageSize;
    private final long totalElements;
    private final int totalPages;

    public WarehouseSearchPage(List<T> data, int page, int pageSize, long totalElements) {
        this.data          = data;
        this.page          = page;
        this.pageSize      = pageSize;
        this.totalElements = totalElements;
        this.totalPages    = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
    }

    public List<T> getData()          { return data; }
    public int getPage()              { return page; }
    public int getPageSize()          { return pageSize; }
    public long getTotalElements()    { return totalElements; }
    public int getTotalPages()        { return totalPages; }
}