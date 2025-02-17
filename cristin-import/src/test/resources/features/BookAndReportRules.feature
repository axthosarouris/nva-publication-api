Feature: Rules that apply for both Books and Reports


  #TODO: replace the examples with a List of Cristin Results and the Scenario Outlines with
  # simple Scenarios.


  Scenario Outline: Mapping creates a reference to an NSD publisher when a Cristin Result mentions
  a Publisher with an NSD code.
    Given a valid Cristin Result with secondary category "<secondaryCategory>"
    And the Cristin Result mentions a Publisher with NSD code 12345
    And the Cristin Result was reported in NVI the year 2005
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource contains a Publisher reference that is a URI pointing to the NVA NSD proxy
    And the Publisher URI contains the NSD code 12345 and the publication year 2005
    Examples:
      | secondaryCategory |
      | MONOGRAFI         |
      | ANTOLOGI          |
      | FAGBOK            |
      | LÆREBOK           |
      | LEKSIKON          |
      | POPVIT_BOK        |
      | OPPSLAGSVERK      |
      | ANTOLOGI          |
      | RAPPORT           |
      | DRGRADAVH         |
      | MASTERGRADSOPPG   |
      | HOVEDFAGSOPPGAVE  |
      | FORSKERLINJEOPPG  |

  Scenario Outline: Mapping creates a mention to an Unconfirmed Publisher when a Cristin Result mentions
  a Publisher only by name
    Given a valid Cristin Result with secondary category "<secondaryCategory>"
    And the Cristin Result mentions a Publisher with name "SomePublishingHouse" and without an NSD code
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource mentions an Unconfirmed Publisher with name "SomePublishingHouse"
    Examples:
      | secondaryCategory |
      | MONOGRAFI         |
      | ANTOLOGI          |
      | FAGBOK            |
      | LÆREBOK           |
      | LEKSIKON          |
      | POPVIT_BOK        |
      | OPPSLAGSVERK      |
      | ANTOLOGI          |
      | RAPPORT           |
      | DRGRADAVH         |
      | MASTERGRADSOPPG   |
      | HOVEDFAGSOPPGAVE  |
      | FORSKERLINJEOPPG  |


  Scenario Outline: Mapping creates a mention to an Unconfirmed Publisher when a Cristin Result does
  not have a Publisher in the default field but has a Publisher name in the alternative field.
    Given a valid Cristin Result with secondary category "<secondaryCategory>"
    And the Cristin Result does not a primary Publisher entry
    But the Cristin Results has an alternative mention to a Publisher Name with value "SomeUnconfirmedPublishingHouse"
    When the Cristin Result is converted to an NVA Resource
    Then the NVA Resource mentions an Unconfirmed Publisher with name "SomeUnconfirmedPublishingHouse"
    Examples:
      | secondaryCategory |
      | MONOGRAFI         |
      | ANTOLOGI          |
      | FAGBOK            |
      | LÆREBOK           |
      | LEKSIKON          |
      | POPVIT_BOK        |
      | OPPSLAGSVERK      |
      | ANTOLOGI          |
      | RAPPORT           |
      | DRGRADAVH         |
      | MASTERGRADSOPPG   |
      | HOVEDFAGSOPPGAVE  |
      | FORSKERLINJEOPPG  |

  Scenario Outline: Mapping fails when a Cristin Entry does not have a Publisher mention at all
    Given a valid Cristin Result with secondary category "<secondaryCategory>"
    And the Cristin Result does not a primary Publisher entry
    And the Cristin Result does not mention a publisher in the alternative field
    When the Cristin Result is converted to an NVA Resource
    Then an error is reported.
    Examples:
      | secondaryCategory |
      | MONOGRAFI         |
      | ANTOLOGI          |
      | FAGBOK            |
      | LÆREBOK           |
      | LEKSIKON          |
      | POPVIT_BOK        |
      | OPPSLAGSVERK      |
      | ANTOLOGI          |
      | RAPPORT           |
      | DRGRADAVH         |
      | MASTERGRADSOPPG   |
      | HOVEDFAGSOPPGAVE  |
      | FORSKERLINJEOPPG  |