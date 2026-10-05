package com.wtm.application.port.out;

import java.util.List;

/**
 * Outbound port for a language model that reads a situation and a short list of memes and says which meme fits
 * best, and why. It only ever chooses among the candidates it is given.
 */
public interface MemePickerPort {

    /**
     * @throws LlmUnavailableException when the model cannot be reached or does not answer with a usable choice
     */
    Pick pick(String situation, List<Candidate> candidates);

    /** One meme as the model reads it: only words, taken from the library's description of the picture. */
    record Candidate(String name, String meaning, List<String> usageExamples, List<String> emotions,
                     List<String> tags, String imageText) {
    }

    /**
     * @param index  the chosen candidate's position in the list that was given, counting from 0
     * @param reason why it fits, in a sentence or two; may be blank
     */
    record Pick(int index, String reason) {
    }
}
