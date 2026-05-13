package com.fulfilment.application.monolith.stores;

public class StoreCreatedEvent {
  private final Store store;

  public StoreCreatedEvent(Store store) {
    this.store = store;
  }

  public Store getStore() {
    return store;
  }
}
