package com.wtm.application.port.out;

import java.util.List;

/**
 * Outbound port for a language model that reads a situation and one meme's description and says why the meme
 * fits (or does not fit) that situation. It explains the meme it is given; it does not choose among memes.
 */
public interface MemeExplainerPort {

    /**
     * @return the reason, in a sentence or two
     * @throws LlmUnavailableException when the model cannot be reached or does not answer with a usable reason
     */
    String explain(String situation, Meme meme);

    /** One meme as the model reads it: only words, taken from the library's description of the picture. */
    record Meme(String name, String meaning, List<String> usageExamples, List<String> emotions,
                List<String> tags, String imageText) {
    }
}
