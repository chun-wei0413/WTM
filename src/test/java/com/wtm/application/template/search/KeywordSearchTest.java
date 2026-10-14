package com.wtm.application.template.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.application.port.out.TemplateSearchPort;
import com.wtm.application.port.out.TemplateSearchPort.IndexedText;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeywordSearchTest {

    private final TemplateSearchPort port = mock(TemplateSearchPort.class);
    private final KeywordSearch search = new KeywordSearch(port);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void theIndexIsBuiltOnceAndKeptWhileTheTextsStayTheSame() {
        when(port.searchTextsVersion()).thenReturn("v1");
        when(port.searchTexts()).thenReturn(List.of(new IndexedText(a, "cat meme")));

        search.search("cat", 5);
        search.search("meme", 5);
        search.search("cat meme", 5);

        verify(port, times(1)).searchTexts();
    }

    @Test
    void theIndexIsBuiltAgainWhenTheTextsChange() {
        when(port.searchTextsVersion()).thenReturn("v1", "v2");
        when(port.searchTexts()).thenReturn(List.of(new IndexedText(a, "cat")), List.of(new IndexedText(b, "dog")));

        assertThat(search.search("cat", 5)).extracting(h -> h.id()).containsExactly(a);
        assertThat(search.search("dog", 5)).extracting(h -> h.id()).containsExactly(b);
        assertThat(search.search("cat", 5)).isEmpty();   // the version stays "v2": the new index is kept, and it has no cat

        verify(port, times(2)).searchTexts();
    }
}
