# Marsbase Monitoring

Overvågningssystem til en marskoloni. Autonome sensorenheder måler kritiske livsdata
og sender dem til HQ, som logger alt og slår alarm hvis en værdi er farlig for
kolonisterne.

Ren Java uden frameworks og uden database. Kommunikationen sker over TCP-sockets,
og HQ håndterer flere sensorer samtidig via en trådpool.

```
 +------------------+  +------------------+  +------------------+  +------------------+
 | Sensor: TEMP     |  | Sensor: O2       |  | Sensor: PRESSURE |  | Sensor: CO2      |
 | SENSOR-01        |  | SENSOR-02        |  | SENSOR-03        |  | SENSOR-04        |
 +------------------+  +------------------+  +------------------+  +------------------+
           \                   |                     |                    /
            \                  |                     |                   /
             +-----------------+---------------------+------------------+
                                        |
                                        v
                       +--------------------------------+
                       |         Mars HQ Server         |
                       |  ServerSocket + trådpool (5)   |
                       +--------------------------------+
                                        |
                                        v
                       +--------------------------------+
                       |        Logfil: mars.log        |
                       +--------------------------------+
```

## Sådan kører du systemet

Kræver Java 21 eller nyere (`pom.xml` er sat til 24). Ingen dependencies.

**I IntelliJ** — opret to run-configurations:

1. `MarsServer` — ingen program arguments. **Start denne først.**
2. `SensorClient` — ingen program arguments. Start den én gang per sensor.

Sensoren bestemmer ikke selv hvad den måler: HQ uddeler den første ledige type.
Så første klient bliver TEMP, næste O2, så PRESSURE, så CO2. En femte klient
bliver afvist, fordi alle typer allerede er dækket.

**Fra kommandolinjen** — kompilér først:

```powershell
# PowerShell
javac -d target\classes (Get-ChildItem -Recurse src\main\java -Filter *.java).FullName
```

```bash
# bash / macOS / Linux
javac -d target/classes $(find src/main/java -name "*.java")
```

Start derefter serveren i én terminal og en klient i hver af de øvrige:

```
java -cp target/classes org.example.mars.server.MarsServer
java -cp target/classes org.example.mars.client.SensorClient
```

Logfilen `mars.log` oprettes i projektroden og appendes til, så tidligere
kørsler bevares. Den er med i `.gitignore`.

## Tærskelværdier

Alle grænser er defineret i `SensorType` og **kun** dér. Skal en grænse ændres,
sker det på én linje.

| Type       | Måler         | Sikkert interval  | Alarm ved              |
|------------|---------------|-------------------|------------------------|
| `TEMP`     | Temperatur    | -15 til 35 °C     | under -15 eller over 35 |
| `O2`       | Iltindhold    | 19 til 23 %       | under 19 eller over 23  |
| `PRESSURE` | Lufttryk      | 800 til 1100 hPa  | under 800 eller over 1100 |
| `CO2`      | CO₂-niveau    | under 2000 ppm    | over 2000              |

CO₂ har ingen nedre grænse. Det er modelleret med `Double.NEGATIVE_INFINITY`,
så den samme sammenligning kan bruges for alle fire typer uden særtilfælde.

## Protokol

Al kommunikation er tekstlinjer. Formatet står samlet i `SensorProtocol`, så
klient og server ikke kan komme til at være uenige om det.

**1. Sensoren forbinder, og HQ tildeler en type:**

```
HQ     -> ASSIGNED:O2;SENSOR-02
HQ     -> REJECTED:all sensor types are already in use
```

**2. Derefter rapporterer sensoren hvert 5. sekund:**

```
Sensor -> SENSOR-02;O2:21.4
```

**3. HQ svarer kun hvis værdien er kritisk:**

```
HQ     -> ALARM: O2 value out of range! (value = 23.5)
```

Værdier sendes altid med punktum som decimalseparator (`Locale.US` i klienten).
På en dansk maskine ville `String.format` ellers producere `21,4`, og serverens
`Double.parseDouble` ville kaste `NumberFormatException`.

## Arkitektur

```
org.example.mars
├── model/     SensorType, SensorReading          data + grænseværdier
├── protocol/  SensorProtocol, ReadingParser      beskedformatet, delt af begge sider
├── service/   AlarmService, MarsLogger           logik uden netværk
├── server/    MarsServer, SensorHandler,         HQ
│              SensorRegistry
└── client/    SensorClient                       sensoren
```

Afhængighederne peger kun indad: `server` og `client` → `protocol`/`service` → `model`.
`model` kender ingen andre pakker, og `server` og `client` kender ikke hinanden.
Skulle sensorrobotterne udrulles som en selvstændig jar, skal de kun have
`client`, `protocol` og `model` med.

| Klasse           | Ansvar |
|------------------|--------|
| `MarsServer`     | Åbner `ServerSocket` på port 5000, ejer trådpoolen, afleverer hver forbindelse til den |
| `SensorHandler`  | Håndterer **én** sensorforbindelse: tildeler type, læser målinger, alarmerer, logger |
| `SensorRegistry` | Holder styr på hvilke sensortyper er dækket, og uddeler den næste ledige |
| `SensorClient`   | Simuleret sensorrobot: får sin type af HQ, sender en tilfældig værdi hvert 5. sekund |
| `SensorProtocol` | Beskedformatet: præfikser, separatorer og opbygning af linjer |
| `ReadingParser`  | Oversætter en modtaget linje til et `SensorReading`, eller `null` hvis den er ugyldig |
| `AlarmService`   | Afgør om en måling ligger uden for sin types grænser |
| `MarsLogger`     | Trådsikker skrivning af målinger og hændelser til `mars.log` |
| `SensorType`     | Enum med de fire typer, deres enheder og deres grænseværdier |
| `SensorReading`  | Entity: sensor-ID, type, værdi og tidspunkt |

## Trådmodellen

`MarsServer` opretter aldrig selv tråde. Den har en `ExecutorService` med 5 tråde,
og for hver accepteret forbindelse afleverer den én `SensorHandler` til poolen:

```java
Socket sensorSocket = serverSocket.accept();
pool.submit(new SensorHandler(sensorSocket, logger, registry));
```

Det afgørende er at accept-løkken bliver fri med det samme. Læste serveren selv
målingerne, ville sensor nr. 2 stå i kø indtil sensor nr. 1 koblede fra. Én
forbindelse optager én pool-tråd så længe sensoren er forbundet.

**To ressourcer deles mellem trådene, og begge er beskyttet:**

- `SensorRegistry` — hvis to sensorer forbinder i samme millisekund, kører
  `claimFreeType()` samtidig på to pool-tråde. Uden `synchronized` kunne begge
  se at O₂ er fri, og begge få den tildelt.
- `MarsLogger` — alle tråde skriver i samme `BufferedWriter`. `write()` er ikke
  atomisk, så uden låsen kunne én tråd skrive midt i en andens linje. Derfor er
  den private skrivemetode `synchronized`, og der flushes hver gang.

## Fejlhåndtering

| Situation | Hvad der sker |
|-----------|---------------|
| Sensoren lukker pænt | `readLine()` returnerer `null` → "disconnected" logges, typen frigives, tråden går tilbage i poolen |
| Sensoren dør brat | `IOException: Connection reset` fanges → fejl logges, typen frigives |
| Sensoren bliver tavs uden at lukke | `setSoTimeout(15000)` (3 × sendeintervallet) → `SocketTimeoutException` efter 15 sek., så en frossen sensor ikke kan beholde en pool-tråd for evigt |
| Ugyldig måling | `ReadingParser` returnerer `null` → linjen rapporteres som fejl og springes over. Én dårlig besked må ikke vælte tråden |
| Værdi er ikke et tal | `NumberFormatException` fanges i parseren |
| HQ kører ikke | Klienten fanger `ConnectException` og skriver en læsbar fejl i stedet for et stacktrace |
| Logfilen kan ikke åbnes | HQ starter slet ikke — overvågning uden log er ikke acceptabel |
| Alle sensortyper er taget | Den nye sensor får `REJECTED:` og forbindelsen lukkes |

Alle sockets og streams er i `try-with-resources`, og `registry.release()` ligger
i en `finally`-blok, så en sensortype altid gives tilbage — også efter en fejl.

`SocketTimeoutException` og `ConnectException` fanges **før** `IOException`, fordi
de nedarver fra den.

## Eksempel på kørsel

**Server:**

```
[HQ] Mars HQ starting up...
[HQ] Listening for sensors on port 5000 with a pool of 5 threads
[HQ][pool-1-thread-1] Sensor connected from /127.0.0.1:59944
[HQ][pool-1-thread-1] Assigned TEMP to SENSOR-01 (1 of 4 types covered)
[HQ][pool-1-thread-2] Assigned O2 to SENSOR-02 (2 of 4 types covered)
[HQ][pool-1-thread-3] Assigned PRESSURE to SENSOR-03 (3 of 4 types covered)
[HQ][pool-1-thread-4] Assigned CO2 to SENSOR-04 (4 of 4 types covered)
[HQ][pool-1-thread-1] SENSOR-01 ALARM: TEMP value out of range! (value = -15.2)
[HQ][pool-1-thread-2] SENSOR-02 O2: 19.9 % (ok)
[HQ][pool-1-thread-3] SENSOR-03 PRESSURE: 876.0 hPa (ok)
[HQ][pool-1-thread-4] SENSOR-04 CO2: 1344.0 ppm (ok)
[ERROR] Sensor at /127.0.0.1:59948 was turned away: no free sensor type.
```

Trådnavnene viser at de fire sensorer betjenes af fire forskellige pool-tråde.

**Klient:**

```
[SENSOR-??] Sensor booting up, contacting HQ at localhost:5000...
[SENSOR-02] Assigned by HQ: Oxygen, reporting every 5 seconds, safe range 19,0 - 23,0 %
[SENSOR-02] Sent: SENSOR-02;O2:19.9
[SENSOR-02] Sent: SENSOR-02;O2:23.5
[SENSOR-02] HQ says: ALARM: O2 value out of range! (value = 23.5)
```

**mars.log:**

```
[2026-09-22 11:57:17] Mars HQ started on port 5000
[2026-09-22 11:57:18] Sensor connected from /127.0.0.1:61733
[2026-09-22 11:57:18] Assigned O2 to SENSOR-02 (1 of 4 types covered)
[2026-09-22 11:57:18] SENSOR-02 O2: 22.5 %
[2026-09-22 11:57:18] SENSOR-04 CO2: 2100.0 ppm -> ALARM!
[2026-09-22 11:57:19] [ERROR] Sensor SENSOR-02 sent invalid data: SENSOR-02;O2:nonsense
[2026-09-22 11:57:25] [ERROR] Sensor SENSOR-03 lost connection (silent for 15 seconds).
```

## Test

### Unit tests

65 JUnit 5-tests dækker al logik der kan testes uden netværk. Kør dem med
`mvn test`, eller højreklik på `src/test/java` i IntelliJ → *Run All Tests*.

| Testklasse | Dækker |
|------------|--------|
| `ReadingParserTest` (16) | Gyldige linjer, ugyldigt format, ukendt type, manglende ID, dansk decimalkomma, `null` |
| `AlarmServiceTest` (16) | Grænseværdier for alle fire typer, inkl. værdier præcis på grænsen, og alarmbeskedens format |
| `SensorRegistryTest` (11) | Uddeling og frigivelse af typer, alle typer optaget, og 20 tråde der kæmper om typerne samtidig |
| `SensorTypeTest` (8) | At tærskelværdierne matcher opgavebeskrivelsen |
| `SensorProtocolTest` (5) | Beskedformatet i begge retninger |
| `MarsLoggerTest` (5) | Tidsstempler, append-tilstand, og at 5 tråde kan skrive 250 linjer uden at flette dem sammen |
| `SensorReadingTest` (4) | At intet går tabt i entity'en, og tekstformen brugt i loggen |

Testene følger navngivningen `metode_scenarie_forventetResultat` og
Arrange-Act-Assert fra `instructions.md`.

## Kendte begrænsninger

- Der kan være **maks. én sensor per type**, altså fire i alt. Trådpoolen på 5 er
  derfor ikke længere den bindende grænse — `SensorRegistry` er.
- Stopper man serveren med stop-knappen, kører oprydningen ikke. Det er
  uproblematisk, fordi hver loglinje flushes med det samme og styresystemet
  lukker sockets.
- Værdier logges som `double`, så CO₂ vises som `2100.0` og ikke `2100`.
- Selve netværkslaget (`MarsServer`, `SensorHandler`, `SensorClient`) er kun
  testet manuelt. Unit tests af det ville kræve enten rigtige sockets eller en
  abstraktion over dem, og det er fravalgt for at holde løsningen enkel.

---

## AI-agent

**En afgrænset opgave vi gav agenten**

Da vi lavede opgaven ville vi gerne logge beskeder til en tekstfil

**Hvorfor den var afgrænset sådan**

Logning er den eneste klasse alle threads tilgår, vi valgte derfor at sige til vores Agent at den kun måtte oprette en ny klasse
som vores `SensorHandler` kunne tilgå. Dette er med til at gøre programmet simpelt.

**Et forslag vi accepterede**

Agenten insisterede på at vi brugte `Locale.US` når værdierne i klienten formateres. Agenten forklarede at vores maskiner 
kører dansk locale. Så `String.format("%.1f", 27.4)` giver `27,4` og ville herefter kaste en
NumberFormatException pga Double.parseDouble vores Protokol har derfor dette men ikke i konsol output da vi gerne vil have dansk format

**Forslag vi afviste eller ændrede**

Agenten startede ikke med at indsætte flere forskellige typer sensorer. Da jeg bad den om det, gjorde den der med en 
SensorFleet klasse. Dette var en klasse som startede 4 threads i sin egen client og arbejde på det der.
Vi havde altså ikke brug for at connecte flere clienter til en server forbindelse. Det var noget rod og derfor afviste vi det
vi lavede derefter et tjek som sikre at alle sensorer bliver brugt.

**Hvordan vi testede den AI-genererede kode**

Vi bad agenten om at lave en plan over hvordan den ville lave programmet. Den lavede en 8 trins plan.
Vi sikrede at vi efter hvert trin testede manuelt om den havde lavet det rigtigt og bad den forklarer i grundige træk
hvordan den selv lavede kvalitets sikring efter hver gang.
