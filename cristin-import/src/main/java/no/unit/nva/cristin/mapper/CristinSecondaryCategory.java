package no.unit.nva.cristin.mapper;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import nva.commons.core.SingletonCollector;

public enum CristinSecondaryCategory {
    ANTHOLOGY("ANTOLOGI", "ANTHOLOGY"),
    MONOGRAPH("MONOGRAFI", "MONOGRAPH"),
    JOURNAL_ARTICLE("ARTIKKEL_FAG", "JOURNAL_ARTICLE"),
    JOURNAL_REVIEW("BOKANMELDELSE", "JOURNAL_REVIEW"),
    RESEARCH_REPORT("RAPPORT", "RESEARCH_REPORT"),
    CHAPTER_ARTICLE("KAPITTEL", "CHAPTER_ARTICLE", "POPVIT_KAPITTEL", "FAGLIG_KAPITTEL"),
    DEGREE_PHD("DRGRADAVH", "DEGREE_PHD"),
    UNMAPPED;

    public static final int DEFAULT_VALUE = 0;
    private final List<String> aliases;

    CristinSecondaryCategory(String... aliases) {
        this.aliases = Arrays.asList(aliases);
    }

    @JsonCreator
    public static CristinSecondaryCategory fromString(String category) {
        return Arrays.stream(values())
                   .filter(enumValue -> enumValue.aliases.contains(category))
                   .collect(SingletonCollector.collectOrElse(UNMAPPED));
    }

    @JsonValue
    public String getValue() {
        if (Objects.nonNull(aliases) && !aliases.isEmpty()) {
            return aliases.get(DEFAULT_VALUE);
        }
        return this.name();
    }

    public boolean isUnknownCategory() {
        return UNMAPPED.equals(this);
    }

}
