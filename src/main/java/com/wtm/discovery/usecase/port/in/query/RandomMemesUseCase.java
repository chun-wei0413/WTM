package com.wtm.discovery.usecase.port.in.query;

import java.util.List;

public interface RandomMemesUseCase {

    List<LibraryItem> handle(int limit);
}
