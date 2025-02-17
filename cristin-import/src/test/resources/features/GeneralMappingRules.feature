Feature: Mappings that hold for all types of Cristin Results

  Background:
    Given a valid Cristin Result

  Scenario: Cristin entry id is saved as additional identifier
    Given the Cristin Result has id equal to 12345
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has an additional identifier with key "Cristin" and value 12345

  Scenario: NVA Resource gets the single Cristin title which is annotated as
  "Original Title" as Main Title. (i.e., the Cristin entry has no more titles except for the original title).
    Given the Cristin Result has an non null array of CristinTitles
    Given the Cristin Result has an array of CristinTitles with values:
      | Title Text         | Abstract Text                 | Status Original | Language Code |
      | This is some title | This is the original abstract | J               | en            |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has an EntityDescription with mainTitle "This is some title"

  Scenario: When there are many titles but only one annotated as Original,
  the NVA Resource gets the Cristin title annotated as Original Title as Main Title.
    Given the Cristin Result has an array of CristinTitles with values:
      | Title Text                 | Abstract Text                 | Status Original | Language Code |
      | This is the original title | This is the original abstract | J               | en            |
      | This is translated title   | This is some other abstract   | N               | en            |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has an EntityDescription with mainTitle "This is the original title"

  Scenario: When there are two titles both annotated as Original the NVA Resource gets any
  of the Cristin Titles annotated as Original Title as Main Title.
    Given the Cristin Result has an array of CristinTitles with values:
      | Title Text                     | Abstract Text                     | Status Original | Language Code |
      | This is the original title     | This is the original abstract     | J               | en            |
      | This is another original title | This is another original abstract | J               | en            |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has an EntityDescription with mainTitle "This is the original title"


  Scenario Outline: The language of the entry is set as Lexvo URI equivalent of the
  Cristin language code of the title annotated as ORIGINAL
    Given the Cristin Result has an array of CristinTitles with values:
      | Title Text                 | Abstract Text                 | Status Original | Language Code       |
      | This is the original title | This is the original abstract | J               | <OriginalTitleCode> |
      | This is some other title   | This is some other abstract   | N               | ru                  |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has an EntityDescription with language "<NvaLanguage>"
    Examples:
      | OriginalTitleCode | NvaLanguage                      |
      | en                | http://lexvo.org/id/iso639-3/eng |
      | EN                | http://lexvo.org/id/iso639-3/eng |
      | NO                | http://lexvo.org/id/iso639-3/nor |
      | NB                | http://lexvo.org/id/iso639-3/nob |
      | NN                | http://lexvo.org/id/iso639-3/nno |
      | garbage           | http://lexvo.org/id/iso639-3/und |
      |                   | http://lexvo.org/id/iso639-3/und |


  Scenario: The Resources Publication Date is set  the Cristin Result's Publication Year
    Given the Cristin Result has publication year 1996
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a Publication Date with year equal to 1996, month equal to null and day equal to null


  Scenario:The NVA Resource Creation Date is set to be the Cristin entry's creation date
    Given that Cristin Result has created date equal to the local date "2011-12-03"
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a Creation Date equal to "2011-12-03T00:00:00Z"

  Scenario: The NVA Resource Published Date is set to be the Cristin entry's creation date
    Given that Cristin Result has created date equal to the local date "2011-12-03"
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a Published Date equal to "2011-12-03T00:00:00Z"

  Scenario: The NVA Resource Modified Date is set to be the Cristin entry's last modified date
    Given that Cristin Result has modified date equal to the local date "2011-12-03"
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a Modified Date equal to "2011-12-03T00:00:00Z"

  Scenario: When the Cristin Result has no last modified date the NVA resource's modified date will be copied
  from the Cristin Results created date
    Given that Cristin Result has created date equal to the local date "2011-12-03"
    And that the Cristin Result has no last modified value.
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a Modified Date equal to "2011-12-03T00:00:00Z"

  Scenario: The NVA Contributor names are concatenations of Cristin's Cristin First and Family names.
    Given that the Cristin Result has Contributors with names:
      | Given Name  | Family Name |
      | John        | Adams       |
      | C.J.B.      | Loremius    |
      | Have, Comma | Surname     |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a List of NVA Contributors:
      | Name                 |
      | Adams, John          |
      | Loremius, C.J.B.     |
      | Surname, Have, Comma |

  Scenario: The NVA Contributor sequence is the same as the Cristin Contributor Sequence
    Given that the Cristin Result has the Contributors with names and sequence:
      | Given Name  | Family Name  | Ordinal Number |
      | FirstGiven  | FirstFamily  | 1              |
      | SecondGiven | SecondFamily | 2              |
      | ThirdGiven  | ThirdFamily  | 3              |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has a List of NVA Contributors with the following sequences:
      | Name                      | Ordinal Number |
      | FirstFamily, FirstGiven   | 1              |
      | SecondFamily, SecondGiven | 2              |
      | ThirdFamily, ThirdGiven   | 3              |

  Scenario: Map returns NVA Resource with Contributors that have Affiliations With URIs
  created based on Cristin Contributor's Reference URI and Unit numbers.
    Given that the Cristin Result has the Contributors with names and sequence:
      | Given Name  | Family Name  | Ordinal Number |
      | FirstGiven  | FirstFamily  | 1              |
      | SecondGiven | SecondFamily | 2              |
      | ThirdGiven  | ThirdFamily  | 3              |
    And the Contributors are affiliated with the following Cristin Institution respectively:
      | institusjonsnr | avdnr | undavdnr | gruppenr |
      | 194            | 66    | 32       | 15       |
      | 194            | 66    | 32       | 15       |
      | 0              | 0     | 0        | 0        |

    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource Contributors have the following names, sequences and affiliation URIs
      | Name                      | Ordinal Number | Affiliation URI                              |
      | FirstFamily, FirstGiven   | 1              | https://api.cristin.no/v2/units/194.66.32.15 |
      | SecondFamily, SecondGiven | 2              | https://api.cristin.no/v2/units/194.66.32.15 |
      | ThirdFamily, ThirdGiven   | 3              | https://api.cristin.no/v2/units/0.0.0.0      |

  Scenario Outline: Mapping of Cristin Contributor roles is done based on hard-coded rules described here.
    Given that the Cristin Result has a Contributor with role "<CristinRole>"
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Contributor has the role "<NvaRole>"
    Examples:
      | CristinRole      | NvaRole                |
      | REDAKTØR         | EDITOR                 |
      | FORFATTER        | CREATOR                |
      | PROGRAMDELTAGER  | PROGRAMME_PARTICIPANT  |
      | PROGRAMLEDER     | PROGRAMME_LEADER       |
      | OPPHAVSMANN      | RIGHTS_HOLDER          |
      | JOURNALIST       | JOURNALIST             |
      | REDAKSJONSKOM    | EDITORIAL_BOARD_MEMBER |
      | INTERVJUOBJEKT   | INTERVIEW_SUBJECT      |
      | FAGLIG_ANSVARLIG | ACADEMIC_COORDINATOR   |

  Scenario: The abstract is copied from the the Cristin Result's title entry when there
  one title entry and it is annotated as original.
    Given the Cristin Result has an array of CristinTitles with values:
      | Abstract Text                 | Title Text                 | Status Original | Language Code |
      | This is the original abstract | This is the original Title | J               | NO            |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has the following abstract "This is the original abstract"

  Scenario: The abstract is copied form the Cristin Result's title entry that is annotated as original
  when there are many titles but only one Original Title
    Given the Cristin Result has an array of CristinTitles with values:
      | Abstract Text                   | Title Text                 | Status Original | Language Code |
      | This is the some other abstract | This is some other Title   | N               | NO            |
      | This is the original abstract   | This is the original Title | J               | NO            |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has the following abstract "This is the original abstract"

  Scenario: Mapping does not fail when there is no abstract
    Given the Cristin Result has an array of CristinTitles with values:
      | Abstract Text                   | Title Text               | Status Original | Language Code |
      | This is the some other abstract | This is some other Title | J               | NO            |
    And the cristin title abstract is sett to null
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has no abstract


  Scenario:All tags are copied as keywords and language of the keywords is ignored.
    Given that the Cristin Result has a CristinTag object with the values:
      | Bokmal    | English | Nynorsk    |
      | kirke     | church  | kyrkje     |
      | skole     |         | skule      |
      | hus       | house   |            |
      |           |         | nynorskOrd |
      | bokmalOrd |         |            |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has the tags:
      | kirke      |
      | church     |
      | kyrkje     |
      | skole      |
      | skule      |
      | hus        |
      | house      |
      | nynorskOrd |
      | bokmalOrd  |


  Scenario: Cristin entry's project id is transformed to NVA project URI
    Given that the Cristin Result has a PresentationalWork object that is not null
    And that the Cristin Result has PresentationalWork objects with the values:
      | Type     | Identifier |
      | PROSJEKT | 1234       |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has Research projects with the id values:
      | https://api.test.nva.aws.unit.no/project/1234 |

  Scenario: Other PresentationWork metadata is ignored
    Given that the Cristin Result has PresentationalWork objects with the values:
      | Type     | Identifier |
      | PROSJEKT | 1234       |
      | PROSJEKT | 5678       |
      | PERSON   | 1111       |
      | GRUPPE   | 0000       |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has Research projects with the id values:
      | https://api.test.nva.aws.unit.no/project/1234 |
      | https://api.test.nva.aws.unit.no/project/5678 |

  Scenario: Mapping does not fail when there is no ResearchProject
    Given that the Cristin Result has a ResearchProject set to null
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has no projects

  Scenario: The Cristin Result's HRCS values are used to generate the URIs for the NVA Resource
    Given a valid Cristin Result
    And the Cristin Result has the HRCS values:
      | helsekategorikode | aktivitetskode |
      | 4                 | 6.4            |
      | 13                | 4              |
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource has the following subjects:
      | https://nva.unit.no/hrcs/category/hrcs_hc_12mus |
      | https://nva.unit.no/hrcs/category/hrcs_hc_20gen |
      | https://nva.unit.no/hrcs/activity/hrcs_ra_6_4   |
      | https://nva.unit.no/hrcs/activity/hrcs_rag_4    |

  Scenario Outline: The Cristin Result's HRCS values are used to generate the URIs for the NVA Resource
    Given a valid Cristin Result
    And the Cristin Result has the HRCS values "<helsekategorikode>" and "<aktivitetskode>"
    When the Cristin Result is converted to an NVA Resource
    Then an error is reported.
    Examples:
      | helsekategorikode | aktivitetskode |
      | 4                 | 0.0            |
      | notANumber        | 1.1            |
      | 100               | 1.3            |
      | 7                 | 1.12           |
      | 8                 | NotANumber     |

  Scenario: Mapping a Cristin Result to an NVA Resource creates a publisher id based on environment
  and a hardcoded organization id.
    Given a valid Cristin Result
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource Publishers id is "https://api.test.nva.aws.unit.no/customer/f54c8aa9-073a-46a1-8f7c-dde66c853934"

  Scenario: Mapping reports error when Cristin affiliation has no role
    Given that the Cristin Result has a Contributor with no role
    When the Cristin Result is converted to an NVA Resource
    Then an error is reported.

  Scenario: Mapping reports error when Cristin Contributor has no name
    Given that the Cristin Result has a Contributor with no family and no given name
    When the Cristin Result is converted to an NVA Resource
    Then an error is reported.











