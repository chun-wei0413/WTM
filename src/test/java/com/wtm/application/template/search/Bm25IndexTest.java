package com.wtm.application.template.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.wtm.application.port.out.TemplateSearchPort.Hit;
import com.wtm.application.port.out.TemplateSearchPort.IndexedText;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class Bm25IndexTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    private static IndexedText text(UUID id, String text) {
        return new IndexedText(id, text);
    }

    @Test
    void chineseIsCutIntoOverlappingTwoCharacterPieces() {
        assertThat(Bm25Index.tokens("老闆又改需求")).containsExactly("老闆", "闆又", "又改", "改需", "需求");
        assertThat(Bm25Index.tokens("我")).containsExactly("我");   // a single character stays whole
    }

    @Test
    void wordsAreLowerCasedAndMixedTextIsCutBothWays() {
        assertThat(Bm25Index.tokens("Drake 不要這個")).containsExactly("drake", "不要", "要這", "這個");
        assertThat(Bm25Index.tokens("I'm The Captain Now")).containsExactly("i'm", "the", "captain", "now");
        assertThat(Bm25Index.tokens(null)).isEmpty();
    }

    @Test
    void aTextThatSharesTheQueryWordsRanksFirstAndOthersAreLeftOut() {
        Bm25Index index = new Bm25Index(List.of(text(a, "老闆又改需求 只能微笑硬撐"), text(b, "皮卡丘 驚訝 表情"), text(c, "等了很久 變成骷髏")));

        List<Hit> hits = index.search("老闆又改需求", 10);

        assertThat(hits).extracting(Hit::id).containsExactly(a);
        assertThat(index.search("完全沒有出現過的詞", 10)).isEmpty();
    }

    @Test
    void aRareWordCountsMoreThanACommonOne() {
        // "meme" is in every text, "pikachu" in one: a query with both finds the pikachu text first
        Bm25Index index = new Bm25Index(List.of(text(a, "meme cat"), text(b, "meme pikachu"), text(c, "meme dog")));

        assertThat(index.search("meme pikachu", 10)).extracting(Hit::id).first().isEqualTo(b);
        assertThat(index.search("meme pikachu", 10).get(0).score()).isGreaterThan(index.search("meme pikachu", 10).get(1).score());
    }

    @Test
    void repeatingAWordHelpsLessAndLess() {
        Bm25Index index = new Bm25Index(List.of(text(a, "cat"), text(b, "cat cat"), text(c, "cat cat cat cat cat cat cat cat")));
        double one = score(index, "cat", a), two = score(index, "cat", b);

        assertThat(two).isGreaterThan(one);
        // the second occurrence adds more than all the later ones together would add per occurrence: it saturates below K1 + 1
        assertThat(two / one).isLessThan(2.0);
    }

    @Test
    void aLongTextIsHeldAgainstItsLength() {
        Bm25Index index = new Bm25Index(List.of(text(a, "cat"), text(b, "cat dog bird fish horse sheep goat cow pig duck")));

        assertThat(score(index, "cat", a)).isGreaterThan(score(index, "cat", b));
    }

    @Test
    void anEmptyLibraryAndANonPositiveLimitFindNothing() {
        assertThat(new Bm25Index(List.of()).search("cat", 5)).isEmpty();
        assertThat(new Bm25Index(List.of(text(a, "cat"))).search("cat", 0)).isEmpty();
    }

    @Test
    void theLimitKeepsTheBestAndEqualScoresKeepTheOrderOfTheLibrary() {
        Bm25Index index = new Bm25Index(List.of(text(a, "cat"), text(b, "cat"), text(c, "cat")));

        assertThat(index.search("cat", 2)).extracting(Hit::id).containsExactly(a, b);
    }

    private static double score(Bm25Index index, String query, UUID id) {
        return index.search(query, 10).stream().filter(h -> h.id().equals(id)).findFirst().orElseThrow().score();
    }
}
