Feature: Mapping of "Feature article" entries

  Background:
    Given a valid Cristin Result with secondary category "KRONIKK"

  Scenario: Cristin Result of type "Feature article" maps to "FeatureArticle"
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a Publication Instance of type "FeatureArticle"

  Scenario Outline: Cristin Entry's Journal Publication "pagesBegin" is copied as is with in NVA's field  "pagesBegin".
    Given that the Cristin Result has a non empty Journal Publication
    And the Journal Publication has a "pagesBegin" entry equal to "<pagesEnd>"
    When the Cristin Result is converted to an NVA Resource
    Then the Nva Resource, FeatureArticle, has a PublicationContext with pagesBegin equal to "<pagesEnd>"
    Examples:
      | pagesEnd  |
      | XI        |
      | 123       |
      | some page |

  Scenario Outline: Cristin Entry's Journal Publication "pagesEnd" is copied as is with in NVA's field "pagesEnd".
    Given that the Cristin Result has a non empty Journal Publication
    And the Journal Publication has a "pagesEnd" entry equal to "<pagesBegin>"
    When the Cristin Result is converted to an NVA Resource
    Then the Nva Resource, FeatureArticle, has a PublicationContext with pagesEnd equal to "<pagesBegin>"
    Examples:
      | pagesBegin |
      | XI         |
      | 123        |
      | some page  |

  Scenario Outline: Cristin Entry's Journal Publication "volume" entry is copied to Journal Article's  "volume" field.
    Given that the Cristin Result has a non empty Journal Publication
    And the Journal Publication has a "volume" entry equal to "<volume>"
    When the Cristin Result is converted to an NVA Resource
    Then the Nva Resource, FeatureArticle, has a PublicationContext with volume equal to "<volume>"
    Examples:
      | volume      |
      | VI          |
      | 123         |
      | some volume |

  Scenario Outline: Cristin Entry's Journal Publication "issue" entry is copied to Feature Article's  "issue" field.
    Given that the Cristin Result has a non empty Journal Publication
    And the Journal Publication has a "issue" entry equal to "<issue>"
    When the Cristin Result is converted to an NVA Resource
    Then the Nva Resource, FeatureArticle, has a PublicationContext with issue equal to "<issue>"
    Examples:
      | issue       |
      | VI          |
      | 123         |
      | some volume |

  Scenario: When the the Cristin entry has a reference to an NSD journal then the
  NVA Entry contains a URI that is a reference to that NSD journal.
    Given the Journal Publication has a reference to an NSD journal or publisher with identifier 12345
    And the Journal Publication has publishing year equal to 2003
    And the year the Cristin Result was published is equal to 2003
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a Reference object with a journal URI that points to NVAs NSD proxy
    And the Journal URI specifies the Journal by the NSD ID 12345 and the year 2003.