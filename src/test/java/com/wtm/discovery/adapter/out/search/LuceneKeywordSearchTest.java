package com.wtm.discovery.adapter.out.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wtm.discovery.entity.Hit;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort;
import com.wtm.discovery.usecase.port.out.search.TemplateSearchPort.IndexedText;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LuceneKeywordSearchTest {

    private final TemplateSearchPort port = mock(TemplateSearchPort.class);
    private final LuceneKeywordSearch search = new LuceneKeywordSearch(port);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    @AfterEach
    void close() throws Exception {
        search.close();
    }

    private void library(String version, IndexedText... texts) {
        when(port.searchTextsVersion()).thenReturn(version);
        when(port.searchTexts()).thenReturn(List.of(texts));
    }

    private List<UUID> ids(String query) {
        return search.search(query, 10).stream().map(Hit::id).toList();
    }

    @Test
    void findsEnglishWordsAndRanksTheBetterMatchFirst() {
        library("v1", new IndexedText(a, "distracted boyfriend looks at another girl"),
                new IndexedText(b, "boyfriend"), new IndexedText(c, "this is fine dog in a burning room"));

        assertThat(ids("boyfriend looks at another girl")).containsExactly(a, b);
        assertThat(ids("burning dog")).containsExactly(c);
    }

    @Test
    void findsChineseByTwoCharacterPiecesWithoutAWordList() {
        library("v1", new IndexedText(a, "老闆又改需求,我只能微笑"), new IndexedText(b, "加班到深夜,同事都下班了"));

        assertThat(ids("老闆改需求")).first().isEqualTo(a);
        assertThat(ids("加班")).containsExactly(b);
    }

    @Test
    void nothingIsFoundWhenNoWordIsShared() {
        library("v1", new IndexedText(a, "cat"), new IndexedText(b, "貓咪"));

        assertThat(ids("dog")).isEmpty();
        assertThat(ids("   ")).isEmpty();
    }

    @Test
    void anEmptyLibraryAnswersNothing() {
        library("v1");

        assertThat(ids("anything")).isEmpty();
    }

    @Test
    void theLibraryIsReadOnceWhileTheVersionStaysTheSame() {
        library("v1", new IndexedText(a, "cat meme"));

        ids("cat");
        ids("meme");
        ids("cat meme");

        verify(port, times(1)).searchTexts();
    }

    @Test
    void anEditedMemeIsFoundByItsNewTextAndNotTheOldOne() {
        library("v1", new IndexedText(a, "cat"), new IndexedText(b, "dog"));
        assertThat(ids("cat")).containsExactly(a);

        library("v2", new IndexedText(a, "bird"), new IndexedText(b, "dog"));

        assertThat(ids("cat")).isEmpty();
        assertThat(ids("bird")).containsExactly(a);
        assertThat(ids("dog")).containsExactly(b);
    }

    @Test
    void aMemeThatLeftTheLibraryIsNoLongerFoundAndANewOneIs() {
        library("v1", new IndexedText(a, "cat"), new IndexedText(b, "dog"));
        assertThat(ids("dog")).containsExactly(b);

        library("v2", new IndexedText(a, "cat"), new IndexedText(c, "fox"));

        assertThat(ids("dog")).isEmpty();
        assertThat(ids("fox")).containsExactly(c);
        assertThat(ids("cat")).containsExactly(a);
    }
}
