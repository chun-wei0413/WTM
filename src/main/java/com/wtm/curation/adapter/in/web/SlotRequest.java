package com.wtm.curation.adapter.in.web;

import com.wtm.shared.Slot;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** The body of the requests that define or redefine a slot. */
record SlotRequest(@Min(1) int slotNo, @NotBlank String role, @Min(1) int maxChars,
                   Boolean required, @Min(0) int x, @Min(0) int y,
                   @Min(1) int width, @Min(1) int height) {

    Slot toSlot(int number) {
        return new Slot(number, role, maxChars, required == null || required, x, y, width, height);
    }
}
