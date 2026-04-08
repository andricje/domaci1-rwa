# Quotes Management (domaći 1 — RWA)

Dva procesa na `localhost`: **glavni** (browser) i **pomoćni** (samo ga zove glavni za citat dana).

---

## Pokretanje (korak po korak)

1. **Preduslov:** instaliran **JDK** (preporuka 17+). U korenu repozitorijuma mora postojati **`gson-2.8.2.jar`** (Gson biblioteka za JSON).

2. **Redosled:** prvo pokreni **pomoćni** (`8081`), pa **glavni** (`8080`). Ako glavni krene bez pomoćnog, stranica `/quotes` i dalje radi, ali blok “citat dana” prikaže da pomoćni nije dostupan.

3. **Dva terminala** — iz korena repozitorijuma:

### Varijanta A — IntelliJ (ako već builduješ u `out/production/...`)

```bash
# terminal 1 — pomoćni servis
java -cp "out/production/HTTP_primer:gson-2.8.2.jar" http.AuxiliaryServer
```

```bash
# terminal 2 — glavni servis
java -cp "out/production/HTTP_primer:gson-2.8.2.jar" http.Server
```

U IntelliJ-u: **Build → Build Project**, pa pokreni iste klase (`http.AuxiliaryServer` i `http.Server`) sa **classpath-om koji uključuje Gson JAR**.

### Varijanta B — samo terminal (`javac` + `java`)

```bash
cd /put/do/domaci1-rwa
mkdir -p target/classes
javac -encoding UTF-8 -d target/classes -cp gson-2.8.2.jar $(find src -name "*.java")
```

```bash
# terminal 1
java -cp "target/classes:gson-2.8.2.jar" http.AuxiliaryServer
```

```bash
# terminal 2
java -cp "target/classes:gson-2.8.2.jar" http.Server
```

4. U browseru otvori: **http://localhost:8080/quotes**

---

## Rute (šta testirati)

| Gde | Šta | Odgovor |
|-----|-----|--------|
| Glavni `:8080` | `GET /quotes` | HTML: forma, lista citata, citat dana |
| Glavni `:8080` | `POST /save-quote` | **302** na `/quotes` (čuva iz forme) |
| Pomoćni `:8081` | `GET /qod` | JSON: `quote`, `author` |

---

## Šta je dodato u kodu i kako radi

### Integracija u postojeći HTTP “primer”

- **`src/app/RequestHandler.java`** — dodate rute `/quotes` (GET) i `/save-quote` (POST); ostaje i stari primer (`/newsletter`, `/apply`).
- **`src/app/QuotesController.java`** (novo) — gradi HTML za `/quotes`, na POST čuva citat, poziva pomoćni za citat dana.
- **`src/http/AuxiliaryServer.java`** (novo) — drugi `main`, sluša **8081**, jedna smislena ruta **`GET /qod`** → JSON.
- **`src/http/Server.java`** — port glavnog servisa postavljen na **8080** (umesto starog porta iz primera).
- **`src/http/Request.java`** + **`src/http/ServerThread.java`** — POST telo se čita po **`Content-Length`** i prosleđuje u `Request` (ranije je POST bio “zaglavljen” na fiksni buffer); bez toga forma ne bi pouzdano radila.

### Tok zahteva (jedna rečenica po koraku)

1. Korisnik otvori **`GET /quotes`** → `QuotesController` napravi HTML.
2. Pre toga, `QuotesController` otvori **`Socket` na `localhost:8081`**, pošalje ručno napisan **`GET /qod HTTP/1.1`**, pročita odgovor, izvuče telo posle `\r\n\r\n`, parsira JSON **Gson-om** — **nema** Apache/ Java 11 `HttpClient` itd.
3. Korisnik pošalje formu → **`POST /save-quote`** → citat ide u listu u memoriji i u fajl **`data/quotes.jsonl`**.
4. Pomoćni na **`GET /qod`** nasumično bira: prvo iz **`data/quotes.jsonl`** (ako ima linija), inače iz ugrađenog malog skupa citata (fallback).

### Pool fajl

- **`data/quotes.jsonl`**: svaka linija je jedan JSON objekat `{"quote":"...","author":"..."}`.
- Folder **`data/`** je u **`.gitignore`** — lokalni podaci ne idu na git.

---

## Gde je šta u fajlovima

- `src/http/Server.java`, `ServerThread.java` — glavni server
- `src/app/RequestHandler.java` — rutiranje
- `src/app/QuotesController.java` — quotes + Socket ka pomoćnom
- `src/http/AuxiliaryServer.java` — pomoćni servis + čitanje pool fajla

Rad za kurs **RWA** — domaći „Quotes Management”.
