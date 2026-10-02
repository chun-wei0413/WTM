package com.memehub.adapter.out.source;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memehub.application.collection.FetchRefusedException;
import com.memehub.application.port.out.MemeSourcePort;
import com.memehub.application.port.out.RemoteFetchPort;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Imgflip's public API lists about a hundred of the most used meme templates, with their names.
 * It is meant to be used by other programs.
 */
@Component
class ImgflipSource implements MemeSourcePort {

    static final String LIST_URL = "https://api.imgflip.com/get_memes";

    private final RemoteFetchPort fetcher;
    private final ObjectMapper json;

    ImgflipSource(RemoteFetchPort fetcher, ObjectMapper json) {
        this.fetcher = fetcher;
        this.json = json;
    }

    @Override
    public String id() {
        return "IMGFLIP";
    }

    @Override
    public String displayName() {
        return "Imgflip";
    }

    @Override
    public String description() {
        return "約一百張最常被使用的經典梗圖模板(英文為主),官方提供的公開 API。";
    }

    @Override
    public List<SourceOption> options() {
        return List.of();
    }

    @Override
    public Iterator<RemoteMeme> discover(Map<String, String> options) {
        JsonNode root;
        try {
            root = json.readTree(fetcher.fetchText(LIST_URL));
        } catch (JsonProcessingException e) {
            throw new FetchRefusedException("Imgflip's answer could not be read", e);
        }
        if (!root.path("success").asBoolean(false)) {
            throw new FetchRefusedException("Imgflip did not return its list: " + root.path("error_message").asText(""));
        }
        List<RemoteMeme> memes = new ArrayList<>();
        for (JsonNode meme : root.path("data").path("memes")) {
            String url = meme.path("url").asText("");
            if (url.isBlank()) {
                continue;
            }
            memes.add(new RemoteMeme(url, "https://imgflip.com/memetemplate/" + meme.path("id").asText(),
                    meme.path("name").asText(null), "Imgflip",
                    "Listed by the Imgflip API; the rights belong to the original creators"));
        }
        return memes.iterator();
    }
}
