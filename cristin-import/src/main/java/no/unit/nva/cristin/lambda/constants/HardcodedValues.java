package no.unit.nva.cristin.lambda.constants;

import java.net.URI;

import no.unit.nva.model.Level;

public final class HardcodedValues {

    //COMMON
    public static final String HARDCODED_NPI_SUBJECT = "1007";
    public static final URI HARDCODED_NVA_CUSTOMER =
        URI.create("https://api.dev.nva.aws.unit.no/customer/f54c8aa9-073a-46a1-8f7c-dde66c853934");
    public static final String HARDCODED_PUBLICATIONS_OWNER = "someone@unit.no";
    public static final URI HARDCODED_SAMPLE_DOI = URI.create("https://doi.org/10.1145/1132956.1132959");

    //BOOK
    public static final Level HARDCODED_BOOK_LEVEL = Level.LEVEL_0;
    public static final URI HARDCODED_BOOK_URI = URI.create("https://www.example.com/");
    public static final String HARDCODED_BOOK_PAGE = "1";
    public static final boolean HARDCODED_BOOK_ILLUSTRATED = false;
    public static final boolean HARDCODED_BOOK_PEER_REVIEWED = false;
    public static final boolean HARDCODED_BOOK_TEXTBOOK_CONTENT = false;

    //JOURNAL
    public static final Level HARDCODED_JOURNAL_LEVEL = Level.LEVEL_0;
    public static final URI HARDCODED_JOURNAL_URI = URI.create("https://www.example.com/");
    public static final String HARDCODED_JOURNAL_PAGE = "1";
    public static final String HARDCODED_JOURNAL_ISSN = "0317-8471";
    public static final String HARDCODED_JOURNAL_TITLE = "Some title";
    public static final String HARDCODED_JOURNAL_ARTICLE_NUMBER = "1234567";
    public static final boolean HARDCODED_OPEN_JOURNAL_ACCESS = false;
    public static final boolean HARDCODED_JOURNAL_PEER_REVIEWED = false;


    private HardcodedValues() {

    }
}
