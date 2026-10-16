package com.wtm.discovery.adapter.in.web;

import com.wtm.discovery.usecase.port.in.query.LibraryItem;
import com.wtm.discovery.usecase.port.in.query.RandomMemesUseCase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RandomMemesController {

    private final RandomMemesUseCase random;

    RandomMemesController(RandomMemesUseCase random) {
        this.random = random;
    }

    @GetMapping("/api/library/random")
    List<LibraryItem> random(@RequestParam(defaultValue = "12") @Min(1) @Max(50) int limit) {
        return random.handle(limit);
    }
}
