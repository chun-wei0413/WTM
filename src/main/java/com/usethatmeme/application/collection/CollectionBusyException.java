package com.usethatmeme.application.collection;

public class CollectionBusyException extends RuntimeException {

    public CollectionBusyException() {
        super("A collection is already running; wait for it to finish");
    }
}
