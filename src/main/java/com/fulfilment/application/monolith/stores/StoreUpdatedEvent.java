package com.fulfilment.application.monolith.stores;

public class StoreUpdatedEvent {
  private final Store store;

  public StoreUpdatedEvent(Store store) {
    this.store = store;
  }

  public Store getStore() {
    return store;
  }
}
