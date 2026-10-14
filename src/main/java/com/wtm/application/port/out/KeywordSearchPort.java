package com.wtm.application.port.out;

import com.wtm.application.port.out.TemplateSearchPort.Hit;
import java.util.List;

/**
 * Keyword ranking of the published memes: how well the words of the query match the searchable text of each meme.
 */
@FunctionalInterface
public interface KeywordSearchPort {

    /** The memes that share words with the query, best first, with a score where higher is better; none when nothing is shared. */
    List<Hit> search(String query, int limit);
}
