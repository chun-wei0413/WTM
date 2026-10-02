package com.usethatmeme.application.generation;

import com.usethatmeme.application.port.out.GenerationJobStore;
import com.usethatmeme.application.port.out.GenerationJobStore.ClaimedJob;
import com.usethatmeme.application.port.out.MemeAssistantPort;
import com.usethatmeme.application.port.out.MemeAssistantPort.CaptionBrief;
import com.usethatmeme.application.port.out.MemeRendererPort;
import com.usethatmeme.application.port.out.MemeRendererPort.RenderedImage;
import com.usethatmeme.application.port.out.MemeRepository;
import com.usethatmeme.application.port.out.ObjectStoragePort;
import com.usethatmeme.application.port.out.TemplateReadPort;
import com.usethatmeme.application.template.query.TemplateView;
import com.usethatmeme.application.template.search.SearchResult;
import com.usethatmeme.application.template.search.SearchTemplatesHandler;
import com.usethatmeme.domain.meme.Meme;
import com.usethatmeme.domain.meme.MemeId;
import com.usethatmeme.domain.meme.TemplateRef;
import com.usethatmeme.domain.template.Slot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a situation into candidate memes: find the best templates, write captions for
 * each, draw them, and store the result. One candidate failing does not spoil the others.
 */
public class RunGenerationHandler {

    private static final Logger log = LoggerFactory.getLogger(RunGenerationHandler.class);

    private final SearchTemplatesHandler search;
    private final TemplateReadPort templates;
    private final MemeAssistantPort assistant;
    private final MemeRendererPort renderer;
    private final ObjectStoragePort storage;
    private final MemeRepository memes;
    private final GenerationJobStore jobs;
    private final int candidateCount;

    public RunGenerationHandler(SearchTemplatesHandler search, TemplateReadPort templates,
                                MemeAssistantPort assistant, MemeRendererPort renderer,
                                ObjectStoragePort storage, MemeRepository memes,
                                GenerationJobStore jobs, int candidateCount) {
        this.search = search;
        this.templates = templates;
        this.assistant = assistant;
        this.renderer = renderer;
        this.storage = storage;
        this.memes = memes;
        this.jobs = jobs;
        this.candidateCount = candidateCount;
    }

    public void handle(ClaimedJob job) {
        try {
            List<UUID> memeIds = generate(job);
            if (memeIds.isEmpty()) {
                jobs.fail(job.id(), "No meme could be made for this description");
            } else {
                jobs.complete(job.id(), memeIds);
            }
        } catch (RuntimeException e) {
            log.error("Generation {} failed", job.id(), e);
            jobs.fail(job.id(), "Generation failed unexpectedly");
        }
    }

    private List<UUID> generate(ClaimedJob job) {
        List<UUID> memeIds = new ArrayList<>();
        for (SearchResult hit : search.handle(job.situation(), candidateCount)) {
            try {
                memeIds.add(composeOne(job, hit.templateId()));
            } catch (RuntimeException e) {
                log.warn("Generation {}: candidate template {} failed: {}", job.id(), hit.templateId(), e.getMessage());
            }
        }
        return memeIds;
    }

    private UUID composeOne(ClaimedJob job, UUID templateId) {
        TemplateView template = templates.findById(templateId)
                .filter(t -> "APPROVED".equals(t.status()))
                .orElseThrow(() -> new IllegalStateException("Template is no longer available"));
        List<Slot> slots = template.slots();

        Map<Integer, String> captions = slots.isEmpty()
                ? Map.of()
                : fitToSlots(slots, assistant.writeCaptions(job.situation(),
                        new CaptionBrief(template.name(), template.profile().meaning(),
                                template.profile().usageExamples(), slots)));

        Meme meme = Meme.compose(MemeId.newId(), job.requesterId(),
                new TemplateRef(template.id(), template.version(), slots), captions);

        byte[] original = storage.get(template.imageKey());
        RenderedImage image = slots.isEmpty() ? passThrough(template.imageKey(), original)
                : renderer.render(original, slots, meme.captions());

        String key = "memes/" + meme.id().value() + "." + image.extension();
        storage.put(key, image.content(), image.contentType());
        try {
            meme.attachImage(key);
            memes.save(meme);
        } catch (RuntimeException e) {
            deleteQuietly(key, e);
            throw e;
        }
        return meme.id().value();
    }

    /**
     * Models do not always count characters well. Rather than throw a candidate away for
     * being one character too long, cut it to what the slot allows.
     */
    private static Map<Integer, String> fitToSlots(List<Slot> slots, Map<Integer, String> proposed) {
        Map<Integer, String> fitted = new LinkedHashMap<>();
        for (Slot slot : slots) {
            String text = proposed.get(slot.slotNo());
            if (text == null || text.isBlank()) {
                continue;
            }
            text = text.strip();
            if (!slot.accepts(text)) {
                text = text.substring(0, text.offsetByCodePoints(0, slot.maxChars()));
            }
            fitted.put(slot.slotNo(), text);
        }
        return fitted;
    }

    private static RenderedImage passThrough(String imageKey, byte[] original) {
        boolean jpeg = imageKey.endsWith(".jpg") || imageKey.endsWith(".jpeg");
        return new RenderedImage(original, jpeg ? "jpg" : "png", jpeg ? "image/jpeg" : "image/png");
    }

    private void deleteQuietly(String key, RuntimeException cause) {
        try {
            storage.delete(key);
        } catch (RuntimeException cleanupFailure) {
            cause.addSuppressed(cleanupFailure);
        }
    }
}
