# General Java struktur og regler

## Teknologi

Dette er en enkel Java-konsolapplikation.

- Brug Java 21.
- Brug ikke database.
- Brug ikke Spring Boot eller andre frameworks.
- Hold løsningen enkel og forståelig
- Skal bruge en lav delt arkitektur som fx OOP (Object Oriented Programming) med services, repositories og entities.

## Kvalitet og konventioner

- AI-genereret kode er ikke automatisk korrekt.
- Koden skal kunne forklares, testes og reviewes af udvikleren.
- Navngivning:
    - Klasser: `PascalCase` (f.eks. `CardService`, `ScryfallService`)
      - Derudover skal klasserne i projektet Chatprogram kun være med disse navne:
        - `ChatServer` starter serveren og accepterer forbindelser.
        - `ClientHandler` håndterer kommunikationen med én klient.
        - `ChatClient` forbinder klienten og sender brugerens beskeder.
        - `ServerListener` modtager beskeder fra serveren.
        - `Message` repræsenterer en besked.
        - `MessageParser` opbygger og parser protokolbeskeder.
        - `ClientRegistry` holder styr på tilsluttede brugere.
        - `ChatRoomManager` holder styr på chatrum og medlemmer.
    - Metoder og variabler: `camelCase` (f.eks. `fetchImageUrl`, `testCard`)
    - Konstanter: `SNAKE_CASE_UPPER` (f.eks. `MAX_RETRIES`, `NORMAL_IMAGE_JSON`)
## GitHub Workflow (Gælder kun hvis der arbejdes i Issues)

Hvis du specifikt har bedt om at arbejde ud fra et GitHub Issue:

1. Brug et rigtigt GitHub Issue som kilde til opgaven (hentes igennem GitHub MCP).
2. Brug ikke README eller lokale markdown-filer som erstatning for et GitHub Issue.
3. Arbejd kun med Issues, der findes i GitHub Project og er assigned i Sprint Backlog.
4. Læs hele Issue og alle acceptkriterier før du planlægger.
5. Fortæl altid Issue-nummer og titel, før du foreslår en plan.
6. Lav en kort implementeringsplan før kode ændres, og vent på godkendelse af planen.
7. Flyt Issue til *In progress*, når implementeringen starter.
8. Opret en separat branch til Issue.
9. Implementer kun det valgte Issue.
10. Kør relevante test efter implementering og kontroller alle acceptkriterier.
11. Referer Issue-nummeret i pull requesten og flyt Issue til *Review*.
12. Merge ikke og flyt ikke selv et Issue til *Done* uden menneskelig godkendelse.

# Unit Test Guide

## Navngivning
`metode_scenarie_forventetResultat`

Eksempel: `getById_idIsZero_throwsIllegalArgument`

## AAA: Arrange, Act, Assert
Strukturen hver test skal følge.

```java
@Test
void getById_validId_returnsCard() {
    // Arrange - sæt data op
    when(cardRepository.findById(1)).thenReturn(testCard);

    // Act - kør metoden
    Card result = cardService.getById(1);

    // Assert - tjek resultatet
    assertEquals(testCard, result);
}
```

## TDD: Test Driven Development
Skriv testen før koden.

1. Skriv en test der fejler (rød)
2. Skriv den mindste kode der får testen til at bestå (grøn)
3. Refaktorer koden uden at bryde testen (refaktor)

Også kaldet Red, Green, Refactor.

## Uden mock (ren logik)
Bruges når metoden ikke har eksterne afhængigheder.

```java
@Test
void fetchImageUrlByScryfallLink_invalidPrefix_returnsNull() {
    // Arrange
    String ugyldigtLink = "https://evil.com/card/tla/4/aang";

    // Act
    String result = scryfallService.fetchImageUrlByScryfallLink(ugyldigtLink);

    // Assert
    assertNull(result);
}
```

## Med mock (ekstern afhængighed)
Bruges når metoden kalder database, netværk eller filsystem.

```java
@Test
void fetchImageUrlBySetAndNumber_normalCard_returnsImageUrl() throws Exception {
    // Arrange
    URI uri = new URI("https", "api.scryfall.com", "/cards/tla/4", null);
    when(restTemplate.getForObject(uri, String.class)).thenReturn(NORMAL_IMAGE_JSON);

    // Act
    String result = scryfallService.fetchImageUrlBySetAndNumber("tla", "4");

    // Assert
    assertEquals("https://cards.scryfall.io/normal/front/a/b/abc.jpg", result);
}
```

## Tommelfingerregler

- En ting per test. Hvis testen fejler skal du vide præcis hvad der gik galt.
- Test grænser. Nul, negativt, null, tom string, ugyldige tegn. Ikke kun happy path.
- Test fejlscenarier. Kaster metoden den rigtige exception når noget går galt?
- Mock til afhængigheder. Alt der kalder database, netværk eller filsystem mockes.
- Ingen logik i tests. Ingen if, ingen loops. Tests skal være dumme og direkte.
- Test opførslen, ikke implementationen. Test hvad metoden gør, ikke hvordan den gør det indeni.
