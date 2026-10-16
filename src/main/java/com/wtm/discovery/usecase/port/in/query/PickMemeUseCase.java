package com.wtm.discovery.usecase.port.in.query;

public interface PickMemeUseCase {

    /**
     * @throws TooManyRequestsException when this person has asked too often in the last minute
     */
    PickResult handle(Input input);

    /** @param userKey who is asking, for the rate limit */
    record Input(String userKey, String situation) {
    }
}
