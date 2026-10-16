package com.wtm.discovery.usecase.port.in.query;

public interface PickMemeUseCase {

    /**
     * @param userKey who is asking, for the rate limit
     * @throws TooManyRequestsException when this person has asked too often in the last minute
     */
    PickResult handle(String userKey, String situation);
}
