package com.wtm.adapter.in.web;

import com.wtm.application.template.command.ApproveTemplateHandler;
import com.wtm.application.template.command.DefineSlotHandler;
import com.wtm.application.template.command.DraftTemplateHandler;
import com.wtm.application.template.command.RedefineSlotHandler;
import com.wtm.application.template.command.RemoveSlotHandler;
import com.wtm.application.template.command.RetireTemplateHandler;
import com.wtm.application.template.command.ReviseProfileHandler;
import com.wtm.application.template.query.GetTemplateHandler;
import com.wtm.application.template.query.ListTemplatesHandler;
import com.wtm.application.template.query.TemplateSummary;
import com.wtm.application.template.query.TemplateView;
import com.wtm.domain.template.MemeProfile;
import com.wtm.domain.template.Slot;
import com.wtm.domain.template.TemplateId;
import com.wtm.domain.template.TemplateStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Administrator operations on meme templates. Access is restricted to ROLE_ADMIN
 * by the security filter chain.
 */
@RestController
@RequestMapping("/api/admin/templates")
class AdminTemplateController {

    private final DraftTemplateHandler draft;
    private final DefineSlotHandler defineSlot;
    private final RedefineSlotHandler redefineSlot;
    private final RemoveSlotHandler removeSlot;
    private final ReviseProfileHandler reviseProfile;
    private final ApproveTemplateHandler approve;
    private final RetireTemplateHandler retire;
    private final GetTemplateHandler get;
    private final ListTemplatesHandler list;

    AdminTemplateController(DraftTemplateHandler draft, DefineSlotHandler defineSlot,
                            RedefineSlotHandler redefineSlot, RemoveSlotHandler removeSlot,
                            ReviseProfileHandler reviseProfile, ApproveTemplateHandler approve,
                            RetireTemplateHandler retire, GetTemplateHandler get,
                            ListTemplatesHandler list) {
        this.draft = draft;
        this.defineSlot = defineSlot;
        this.redefineSlot = redefineSlot;
        this.removeSlot = removeSlot;
        this.reviseProfile = reviseProfile;
        this.approve = approve;
        this.retire = retire;
        this.get = get;
        this.list = list;
    }

    @PostMapping(consumes = "multipart/form-data")
    ResponseEntity<CreatedResponse> draft(@RequestParam @NotBlank String name,
                                          @RequestParam("file") MultipartFile file) throws IOException {
        TemplateId id = draft.handle(name, file.getBytes());
        return ResponseEntity.created(URI.create("/api/admin/templates/" + id.value()))
                .body(new CreatedResponse(id.value()));
    }

    @GetMapping
    List<TemplateSummary> list(@RequestParam(required = false) String status) {
        String normalized = status == null ? null : TemplateStatus.valueOf(status.toUpperCase()).name();
        return list.handle(normalized);
    }

    @GetMapping("/{id}")
    TemplateView get(@PathVariable UUID id) {
        return get.handle(id);
    }

    @PutMapping("/{id}/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reviseProfile(@PathVariable UUID id, @RequestBody ProfileRequest request) {
        reviseProfile.handle(new TemplateId(id), new MemeProfile(request.meaning(),
                request.usageExamples(), request.emotions(), request.aliases(),
                request.imageText(), request.tags()));
    }

    @PostMapping("/{id}/slots")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void defineSlot(@PathVariable UUID id, @Valid @RequestBody SlotRequest request) {
        defineSlot.handle(new TemplateId(id), request.toSlot(request.slotNo()));
    }

    @PutMapping("/{id}/slots/{slotNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void redefineSlot(@PathVariable UUID id, @PathVariable int slotNo,
                      @Valid @RequestBody SlotRequest request) {
        redefineSlot.handle(new TemplateId(id), request.toSlot(slotNo));
    }

    @DeleteMapping("/{id}/slots/{slotNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeSlot(@PathVariable UUID id, @PathVariable int slotNo) {
        removeSlot.handle(new TemplateId(id), slotNo);
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@PathVariable UUID id) {
        approve.handle(new TemplateId(id));
    }

    @PostMapping("/{id}/retire")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retire(@PathVariable UUID id) {
        retire.handle(new TemplateId(id));
    }

    record CreatedResponse(UUID id) {
    }

    record ProfileRequest(String meaning, List<String> usageExamples, List<String> emotions,
                          List<String> aliases, String imageText, List<String> tags) {
    }

    record SlotRequest(@Min(1) int slotNo, @NotBlank String role, @Min(1) int maxChars,
                       Boolean required, @Min(0) int x, @Min(0) int y,
                       @Min(1) int width, @Min(1) int height) {

        Slot toSlot(int number) {
            return new Slot(number, role, maxChars, required == null || required, x, y, width, height);
        }
    }
}
