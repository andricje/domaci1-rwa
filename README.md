# Quotes Management (domaći 1 — RWA)

Ovo su **dva lokalna servisa** (glavni + pomoćni) koji omogućavaju unos i prikaz citata, uz “citat dana”.

## Pokretanje

Pokreni **oba** servisa (u dva terminala):

```bash
# terminal 1 (pomoćni)
java -cp "out/production/HTTP_primer:gson-2.8.2.jar" http.AuxiliaryServer
```

```bash
# terminal 2 (glavni)
java -cp "out/production/HTTP_primer:gson-2.8.2.jar" http.Server
```

Otvori u browseru: [http://localhost:8080/quotes](http://localhost:8080/quotes).

## Rute (šta testirati)

- **Glavni servis** (port **8080**):
  - `GET /quotes` → HTML strana (forma + lista citata + citat dana)
  - `POST /save-quote` → čuva citat i vraća **302** na `/quotes`

- **Pomoćni servis** (port **8081**):
  - `GET /qod` → JSON `{ "quote": "...", "author": "..." }`

## Kako radi “citat dana” (bitno za zadatak)

- Browser komunicira **samo** sa glavnim servisom.
- Glavni servis, dok generiše `/quotes`, poziva pomoćni servis na `/qod` **preko `java.net.Socket`** (ručno sastavljen HTTP zahtev/odgovor, bez gotovih HTTP klijenata).
- JSON se parsira/generiše pomoću **Gson** (`com.google.gson`).

## Pool (da svaki uneti quote ulazi u citat dana)

- Svaki uneti citat se čuva u memoriji (za listu na `/quotes`) i dodatno se upisuje u **`data/quotes.jsonl`** (jedan JSON po liniji).
- Pomoćni servis za `/qod` **prvo bira nasumično** iz `data/quotes.jsonl`; ako fajl ne postoji ili je prazan, koristi ugrađeni fallback skup citata.
- `data/` je u `.gitignore` (ne komituje se).

## Gde je šta u kodu

- `src/http/Server.java` / `src/http/ServerThread.java` → socket HTTP server (port 8080)
- `src/app/RequestHandler.java` → rutiranje (`/quotes`, `/save-quote`)
- `src/app/QuotesController.java` → HTML + čuvanje citata + Socket poziv pomoćnog
- `src/http/AuxiliaryServer.java` → pomoćni servis (port 8081, `GET /qod`)

Napomena: ako ne koristiš IntelliJ build output (`out/production/...`), možeš kompajlirati ručno (`javac`) uz `gson` na classpath-u.
