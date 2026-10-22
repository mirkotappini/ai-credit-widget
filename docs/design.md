# Credito DeepSeek — Design UI

| Campo | Valore |
|---|---|
| Prodotto | Credito DeepSeek, app personale per il saldo API e la spesa giornaliera |
| Package | `com.patchouli.deepseekwidget` |
| Piattaforma | Android nativo, Views XML + Material 3, minSdk 26, target 36 |
| Widget | RemoteViews, dimensioni 2x2 e 4x2 |
| Tema | Dark-first, con tema chiaro derivato completo |
| Lingua | Italiano |
| Versione documento | 1.0, 21 ottobre 2026 |

Questo documento definisce l'interfaccia e le linee guida di implementazione, non il codice. Le scelte sono vincolanti per la v1: dove un valore è indicato, va usato quello. I riferimenti al codice servono solo a mappare il design sull'architettura già presente.

## 0. In sintesi

- Il saldo è l'unico elemento dominante. Tutto il resto è contesto: ripartizione, spesa di oggi, rilevamenti recenti.
- Un solo accento: ottanio profondo. Positivo, warning ed errore sono colori di stato, non decorazione.
- Tipografia IBM Plex Sans per l'interfaccia e IBM Plex Mono per ogni cifra, orario e asse. Il mono rende i numeri allineati e leggibili come su uno strumento.
- I widget sono strumenti, non vetrine: sfondo opaco, bordo sottile, nessuna animazione, nessun colore decorativo. Il tap aggiorna; in caso di chiave mancante o non valida apre le impostazioni.
- Ogni stato è esplicito: cosa è successo, quanto sono vecchi i dati, cosa può fare l'utente. Nessun dato viene inventato o mostrato come fresco quando non lo è.

## 1. Prodotto e principi

### 1.1 Perimetro

Tre schermate: Dashboard, Storico, Impostazioni. Due widget: 2x2 e 4x2. Uso singolo, nessuno store, nessun account, nessuna sincronizzazione. La chiave API e lo storico restano sul dispositivo. Le uniche richieste di rete vanno a `api.deepseek.com`.

Non ci sono onboarding, tour, badge, notifiche di marketing o schermate di benvenuto. Il primo avvio mostra direttamente la Dashboard nello stato "Nessuna chiave API".

### 1.2 Principi

1. **Il numero è l'interfaccia.** Il saldo occupa il centro visivo e usa la dimensione maggiore della gerarchia. Nessun elemento compete con lui.
2. **Densità con ritmo.** Griglia da 4 dp, gruppi separati da 32 dp, card separate da 12 dp. Dentro le card le informazioni sono ravvicinate; fuori respirano.
3. **Un accento, pochi stati.** L'ottanio indica interazione e dati; il verde solo successo o dati freschi; l'ambra degrado o soglia; il corallo errori bloccanti. Il rosso non viene mai usato per la spesa: spendere non è un errore.
4. **Dati onesti.** La spesa è una stima locale calcolata dai cali di saldo. Se i rilevamenti sono pochi, l'interfaccia lo dice. Se i dati sono vecchi, lo dice.
5. **Niente colore da solo.** Ogni stato ha un testo oltre al colore. Ogni tocco ha un'area minima di 48 dp.
6. **Scuro di riferimento.** Il progetto nasce sul tema scuro; il chiaro è una derivazione completa, non un ripensamento.

### 1.3 Dati di esempio

Tutti i mockup e i testi usano questi valori, coerenti tra le schermate.

| Dato | Valore |
|---|---|
| Saldo totale | `$21,40` |
| Credito omaggio | `$2,10` |
| Ricariche | `$19,30` |
| Speso oggi | `$0,42` |
| Ultimo aggiornamento | oggi, `14:32` |
| Valuta | `USD` |
| Soglia avviso | `$10,00` |
| Intervallo refresh | `60 min` |
| Rilevamenti oggi | 3 (il primo alle 09:12) |
| Storico 7 giorni | dal 15 al 21 ottobre, totale speso `$7,12` |

Valori per lo stato "sotto soglia": saldo `$3,12`, omaggio `$0,00`, ricariche `$3,12`, speso oggi `$1,08`.

## 2. Fondamenta

### 2.1 Griglia e spaziatura

Base 4 dp. Larghezza di riferimento 360 dp, contenuto 320 dp. Su schermi più larghi il contenuto è centrato con larghezza massima 600 dp; non ci sono layout a due colonne nella v1.

| Token | Valore | Uso |
|---|---|---|
| `ds_space_2` | 2 dp | Distanza tra segmenti della barra di ripartizione, gap interni minori |
| `ds_space_4` | 4 dp | Gap tra label e valore, padding verticale di contenitori compatti |
| `ds_space_6` | 6 dp | Gap tra valore e caption |
| `ds_space_8` | 8 dp | Gap tra elementi nella stessa card, gap tra pulsanti |
| `ds_space_10` | 10 dp | Gap tra balance e barra di ripartizione |
| `ds_space_12` | 12 dp | Gap tra card, gap tra blocchi interni |
| `ds_space_16` | 16 dp | Padding standard delle card |
| `ds_space_20` | 20 dp | Padding orizzontale schermo, padding hero |
| `ds_space_24` | 24 dp | Gap prima del footer, margini di sezione compatti |
| `ds_space_32` | 32 dp | Gap tra sezioni logiche, padding inferiore schermo |
| `ds_space_40` | 40 dp | Margine superiore degli stati vuoti centrati |

Regole fisse:

- Padding orizzontale schermo: 20 dp. Nessun elemento tocca i bordi.
- Top bar: 56 dp, sfondo uguale al background, nessuna ombra, nessun divider.
- Contenuto sotto la top bar: 8 dp dal bordo inferiore della barra.
- Card: padding 16 dp. Hero: padding 20 dp. Banner: padding 14 dp.
- Gap tra card nello stesso gruppo: 12 dp. Gap tra gruppi: 32 dp.
- Padding inferiore del contenuto: 32 dp più l'inset della navigation bar.
- Lista rilevamenti: riga 48 dp, intestazione giorno 32 dp, hairline 1 dp.
- Su tablet e foldable il contenuto resta a una colonna, centrato, max 600 dp. Non si allargano i grafici oltre 560 dp.

### 2.2 Tipografia

Font: **IBM Plex Sans** (400, 500, 600) per l'interfaccia; **IBM Plex Mono** (400, 500) per saldo, importi, orari, assi e metriche. Plex ha un'origine tecnica, è caratteriale ma sobrio, e non è l'ennesimo Inter o Roboto. Il mono è usato come linguaggio dei dati, non come effetto.

File da includere in `res/font` come TTF statici, non downloadable fonts: `plex_sans_regular`, `plex_sans_medium`, `plex_sans_semibold`, `plex_mono_regular`, `plex_mono_medium`. I widget hanno bisogno dei font già nel pacchetto: un downloadable font può non essere disponibile nel processo del launcher. Licenza OFL inclusa nel progetto.

| Ruolo | Font | Peso | Size / line height | Tracking | Uso |
|---|---|---|---|---|---|
| Balance | Plex Mono | 500 | 48 / 54 sp, autosize 28–48 | 0 | Saldo in Dashboard |
| Display S | Plex Mono | 500 | 28 / 34 sp | 0 | Speso oggi, valore principale di card |
| Title screen | Plex Sans | 600 | 20 / 24 sp | 0 | Titoli delle schermate |
| Title card | Plex Sans | 600 | 15 / 20 sp | 0 | Titoli di card e sezioni interne |
| Body | Plex Sans | 400 | 15 / 22 sp | 0 | Testi degli stati, descrizioni |
| Label | Plex Sans | 500 | 13 / 18 sp | 0 | Label di campi, voci di lista |
| Caption | Plex Sans | 400 | 12 / 16 sp | 0 | Meta, helper, note |
| Overline | Plex Sans | 600 | 11 / 14 sp | 0.08 | Etichette maiuscole: SALDO TOTALE, ACCESSO |
| Mono M | Plex Mono | 500 | 15 / 20 sp | 0 | Importi nelle liste |
| Mono S | Plex Mono | 400 | 12 / 16 sp | 0 | Orari, date, unità |
| Mono XS | Plex Mono | 400 | 10 / 14 sp | 0 | Etichette degli assi |
| Button | Plex Sans | 600 | 15 / 20 sp | 0 | Pulsanti |
| Segment | Plex Sans | 500 | 13 / 18 sp | 0 | Selettore 7/30, 15/30/60 |

Regole:

- Ogni importo usa Plex Mono. Ogni testo che non è un dato usa Plex Sans.
- Il saldo è un'unica TextView con `$` e cifre alla stessa dimensione; il simbolo è colorato con testo secondario tramite Span, senza cambiarne la dimensione. Questo evita problemi di autosize e mantiene il ritmo mono.
- Il saldo non va mai troncato con ellissi. Usa autosize da 48 a 28 sp e, oltre i 10 milioni, il formato compatto definito in 2.5.
- Le overline sono scritte in maiuscolo nel copy e hanno tracking 0.08. Non si usa maiuscolo per altro.
- Le cifre non hanno animazioni di conteggio: cambiano con un fade di 120 ms. Un contatore che sale è ambiguo e distrae.
- Nel widget la tipografia è in dp, non in sp, per garantire che il contenuto resti dentro 110 dp anche con font scale di sistema alto. È un'eccezione deliberata, motivata in 12.5.

### 2.3 Colore

Palette scura di riferimento e chiara derivata. Nessun colore dinamico Material You: l'identità resta stabile.

| Token | Dark | Light | Uso |
|---|---|---|---|
| `ds_color_bg` | `#0A0E10` | `#F3F6F6` | Sfondo schermo, top bar, navigation bar |
| `ds_color_surface` | `#11171A` | `#FFFFFF` | Card, widget, campi |
| `ds_color_surface_high` | `#172024` | `#EAF0F0` | Segmenti non selezionati, skeleton, stati premuti |
| `ds_color_border` | `#243237` | `#DCE5E5` | Bordi 1 dp, hairline, track della barra |
| `ds_color_text_primary` | `#E8EEF0` | `#0B1416` | Testo primario, saldo, importi |
| `ds_color_text_secondary` | `#A7B4B9` | `#46585D` | Label, testi secondari, simbolo del saldo |
| `ds_color_text_tertiary` | `#7E8F95` | `#596B70` | Meta, caption, assi |
| `ds_color_accent` | `#2FD3BE` | `#0A7065` | Interazione, linea del grafico, link, focus |
| `ds_color_accent_container` | `#0E2F2C` | `#D8F1EC` | Sfondo del segmento selezionato, pulsante premuto |
| `ds_color_on_accent` | `#04201D` | `#FFFFFF` | Testo sopra il pieno accento |
| `ds_color_data_muted` | `#3C4C52` | `#B7C4C4` | Segmento "Ricariche", elementi dati non attivi |
| `ds_color_positive` | `#5BD68A` | `#146C43` | Dati freschi, test connessione riuscito |
| `ds_color_warning` | `#E8B04B` | `#8A5200` | Soglia, dati vecchi, degrado non bloccante |
| `ds_color_warning_container` | `#2E2413` | `#FBEED6` | Sfondo banner warning |
| `ds_color_error` | `#F0736A` | `#B3261E` | Chiave non valida, errori bloccanti |
| `ds_color_error_container` | `#2E1715` | `#FBE4E1` | Sfondo banner errore |
| `ds_color_on_error` | `#2A0B09` | `#FFFFFF` | Testo sopra il pieno errore |
| `ds_color_chart_grid` | `#1F2B2F` | `#E3EBEB` | Griglia orizzontale del grafico |
| `ds_color_chart_fill` | `#382FD3BE` | `#260A7065` | Riempimento sotto la linea, alpha 22% / 15% |
| `ds_color_bar_past` | `#592FD3BE` | `#4D0A7065` | Barre della spesa non odierne, alpha 35% / 30% |
| `ds_color_skeleton` | `#1B2529` | `#E3EAEA` | Blocchi di caricamento |
| `ds_color_ripple` | `#332FD3BE` | `#1F0A7065` | Ripple, alpha 20% / 12% |

I colori con alpha sono in formato `#AARRGGBB`; il canale alpha è la prima coppia.

Contrasti verificati (WCAG 2.1, testo piccolo):

| Coppia | Dark | Light |
|---|---|---|
| Primario su surface | 15.43:1 | 18.65:1 |
| Secondario su surface | 8.50:1 | 7.46:1 |
| Terziario su surface | 5.38:1 | 5.58:1 |
| Terziario su surface high | 4.93:1 | 4.84:1 |
| Accento su surface | 9.63:1 | 5.97:1 |
| Errore su surface | 6.34:1 | 6.54:1 |
| Accento su accent container | 7.65:1 | 5.03:1 |
| On accent su accento | 9.09:1 | 5.97:1 |
| Warning su warning container | 7.80:1 | 5.57:1 |
| Errore su error container | 5.89:1 | 5.38:1 |

Regole d'uso:

- L'accento copre al massimo il 5% della superficie visibile: linea del grafico, segmento selezionato, link, focus, pulsante primario. Non si usa come sfondo di card o di grandi aree.
- Il positivo non è decorativo: solo dati freschi e successi. Non colora il saldo.
- Il warning è per degrado e soglia. Il saldo resta primario anche sotto soglia; il warning sta nel dot, nella riga di stato e nel banner.
- L'errore è solo per stati bloccanti o azioni fallite. La spesa non è mai in errore.
- Nessun nero puro e nessun bianco puro nello sfondo: il near-black `#0A0E10` evita l'effetto OLED piatto, il grigio `#F3F6F6` riduce l'abbagliamento.
- Le card non usano ombre. La profondità è data da superficie, bordo 1 dp e, al massimo, dal contenitore `surface_high`. È una scelta da strumento, non da app consumer.
- Il tema chiaro non applica un filtro: usa la sua palette. `android:forceDarkAllowed` è false in entrambi i temi.

### 2.4 Forme, bordi, icone

| Elemento | Raggio | Bordo | Note |
|---|---|---|---|
| Hero card | 20 dp | 1 dp `border` | Contenitore del saldo |
| Card | 16 dp | 1 dp `border` | Storico, impostazioni, rilevamenti |
| Banner | 12 dp | 1 dp colore di stato al 35% | Warning o errore |
| Pulsanti, input, segmenti | 12 dp | 1 dp `border` | Altezza 48 dp, input 56 dp |
| Barra di ripartizione | 3 dp | nessuno | Altezza 6 dp |
| Barre della spesa | 3 dp sopra, 0 sotto | nessuno | Barre con angoli superiori arrotondati |
| Dot di stato | 50% | nessuno | 6 dp nell'app, 5 dp nel widget |
| Widget | 24 dp | 1 dp `border` | Il launcher può applicare il proprio raggio |
| Bottom sheet | 28 dp | nessuno | Non usato nella v1 |

Icone: Material Symbols Outlined, 24 dp, tratto 1.75, colore secondario o di stato. Nel codice sono vector drawable locali, non dipendenze da font di icone. Icone usate: history, tune, arrow_back, visibility, visibility_off, sync, warning, error, cloud_off, key, key_off, check, chevron_right. Nessuna emoji, nessuna illustrazione.

### 2.5 Formattazione di numeri e date

Contratto unico per app e widget. Locale italiano, valuta dell'account (`USD` o `CNY`), simbolo prima della cifra e nessuno spazio: `$1.234,56`, `¥1.234,56`. Separatore delle migliaia `.`, decimale `,`. Il meno è il carattere `−` (U+2212), non il trattino.

| Contesto | Regola | Esempio |
|---|---|---|
| App, saldo | Sempre 2 decimali; autosize; oltre 10.000.000 compatto | `$1.234.567,89`; `$12,3M` |
| App, importi e liste | Sempre 2 decimali | `$21,40`, `−$0,42` |
| 2x2, saldo | Centesimi sotto 100; intero 100–9.999; `k` 10.000–999.999; `M` oltre | `$21,40`, `$999`, `$1.234`, `$12,3k`, `$1,2M` |
| 2x2, speso oggi | Prefisso `oggi `; centesimi sotto 100; intero 100–9.999; `k` oltre | `oggi $0,42`, `oggi $999`, `oggi $12,3k` |
| 4x2, saldo | 2 decimali fino a 999.999,99; compatto oltre | `$21,40`, `$999.999,99`, `$1,2M` |
| 4x2, metriche | 2 decimali sotto 1.000; intero 1.000–9.999; `k` oltre | `$2,10`, `$1.234`, `$12,3k` |
| Asse del grafico | 2 decimali sotto 100; intero sotto 10.000; `k` oltre | `21,40`, `1.235`, `12,3k` |
| Soglia, slider | 2 decimali, stessa valuta | `$10,00` |

Il saldo dell'app non mostra mai un valore arrotondato se non oltre 10 milioni: il valore completo resta disponibile nella clipboard con pressione lunga.

Date e orari:

| Contesto | Formato | Esempio |
|---|---|---|
| Meta dashboard | `Aggiornato alle HH:mm` | `Aggiornato alle 14:32` |
| Oggi / ieri | `oggi HH:mm`, `ieri HH:mm` | `oggi 14:32` |
| Giorni precedenti | `d MMM HH:mm` | `20 ott 18:04` |
| Intestazione giorno | `OGGI · d MMM` | `OGGI · 21 OTT` |
| Asse X | `d MMM` | `15 ott` |
| Impostazioni | `d MMM HH:mm` | `21 ott 14:32` |

I mesi sono abbreviati in minuscolo nei testi correnti (`ott`) e in maiuscolo solo nelle overline (`21 OTT`). L'orario è sempre a 24 ore. La mezzanotte non si scrive `24:00`.

### 2.6 Movimento

Il movimento è raro e breve. Serve a confermare un cambio di stato, non a intrattenere.

| Momento | Animazione | Durata |
|---|---|---|
| Ingresso schermata | Fade-through, nessuno slide orizzontale | 180 ms |
| Aggiornamento valore | Crossfade dell'opacità della TextView | 120 ms |
| Comparsa banner | Fade + 8 dp dal basso | 160 ms |
| Scomparsa banner | Fade | 120 ms |
| Skeleton | Pulsazione alpha 0.35 → 0.6 → 0.35 | 900 ms, loop |
| Grafico, primo bind | Rivelazione della linea da sinistra a destra | 320 ms, ease-out |
| Pull-to-refresh | Spinner Material, colore accento | Standard di sistema |

Regole:

- Nessun conteggio animato dei numeri.
- Nessuna animazione di layout che sposti il contenuto quando compare un banner: il banner entra nel suo spazio, con fade; la lista sotto può scorrere di 12 dp, non saltare.
- Il grafico si anima solo al primo bind o al cambio di intervallo, non a ogni refresh. Se `ANIMATOR_DURATION_SCALE` è 0, nessuna animazione.
- Le transizioni tra schermate non usano swipe orizzontale: il pull-to-refresh e il grafico vivono meglio senza conflitti di gesture.
- Nessun suono, nessuna vibrazione, tranne un tick leggero alla copia del saldo.

### 2.7 Accessibilità

- Target minimo 48x48 dp per ogni elemento interattivo. Le icone nella top bar hanno box 48 dp e icona 24 dp. I link testuali hanno altezza 48 dp anche se il testo è 12 sp.
- Il saldo è una TextView con `contentDescription` completa: "Saldo totale: 21 dollari e 40 centesimi. Tieni premuto per copiare." Il valore visivo resta invariato.
- Il grafico espone una descrizione sintetica: "Saldo dal 15 al 21 ottobre: minimo 18,90 dollari, massimo 28,10, ultimo 21,40." Non è esplorabile punto per punto; i dettagli sono nella lista.
- Le barre espongono: "Spesa giornaliera: totale 7,12 dollari, massimo 2,60 il 17 ottobre."
- Ogni stato usa testo oltre al colore. I dot hanno `contentDescription` ("Dati aggiornati", "Dati non aggiornati", "Errore").
- Il font scale dell'app è supportato fino a 1.3. Il saldo usa autosize; le label possono andare a capo; nessun testo è tagliato. I widget usano dp per restare stabili (vedi 12.5).
- L'ordine di lettura è: top bar, banner, saldo, ripartizione, speso oggi, rilevamenti, footer. Gli elementi non interattivi non ricevono focus fittizio.
- I contrasti sono nella tabella 2.3. Nessun testo piccolo scende sotto 4.5:1.
- Le icone di stato non sono mai l'unico segnale: accanto c'è sempre una frase.

## 3. Dashboard

### 3.1 Struttura

```
┌──────────────────────────────────────┐
│ Credito DeepSeek     [storico] [impost.] │ 56 dp
├──────────────────────────────────────┤
│  ╭────────────────────────────────╮  │
│  │ SALDO TOTALE                ●  │  │
│  │                                │  │
│  │ $21,40                         │  │ 48 sp
│  │ Aggiornato alle 14:32 · USD    │  │
│  │                                │  │
│  │ ██████░░░░░░░░░░░░░░░░░░░░░░░  │  │ 6 dp
│  │ ● Omaggio              $2,10   │  │
│  │ ● Ricariche           $19,30   │  │
│  ╰────────────────────────────────╯  │
│  ╭────────────────────────────────╮  │
│  │ SPESO OGGI           7 giorni  │  │
│  │ $0,42                  ▁▂▁▃▂▁▂ │  │
│  │ stima locale · dalle 09:12     │  │
│  ╰────────────────────────────────╯  │
│  ULTIMI RILEVAMENTI        Vedi tutto│
│  ╭────────────────────────────────╮  │
│  │ 14:32                  $21,40  │  │
│  │ 13:32                  $21,58  │  │
│  │ 12:32                  $21,82  │  │
│  ╰────────────────────────────────╯  │
│  Aggiornamento ogni 60 min · dati    │
└──────────────────────────────────────┘
```

Root: `SwipeRefreshLayout` con sfondo `bg`, figlio `NestedScrollView` con `clipToPadding=false`. La top bar è fissa, non collassabile. Il pull-to-refresh parte sotto la top bar.

### 3.2 Anatomia

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Top bar | 56 dp, padding start 20, azioni 48x48 a fine | Title screen | `text_primary`; icone `text_secondary` |
| 2 | Hero card | Match, raggio 20, padding 20, bordo 1 | — | `surface` |
| 3 | Riga label | Altezza 14, label a sinistra, dot 6 a destra | Overline | Label `text_secondary`; dot di stato |
| 4 | Saldo | Altezza 54, larghezza piena, maxLines 1, autosize 28–48 | Balance | `text_primary`; simbolo `text_secondary` |
| 5 | Meta | 16 dp, margine top 4 | Caption | `text_tertiary` |
| 6 | Barra ripartizione | 6 dp, raggio 3, margine top 16, gap 2 tra segmenti | — | `accent` / `data_muted`; track `border` |
| 7 | Legenda | Due righe da 20 dp, gap 6, margine top 10 | Label + Mono M | Label `text_secondary`; valore `text_primary` |
| 8 | Banner | Raggio 12, padding 14, margine top 12 | Title card + Caption | `warning`/`error` + container |
| 9 | Card Oggi | Raggio 16, padding 16, min 104, margine top 12 | — | `surface` |
| 10 | Label Oggi | Riga 16, `7 giorni` a destra | Overline + Caption | `text_secondary` / `text_tertiary` |
| 11 | Valore Oggi | 34 dp, margine top 6 | Display S | `text_primary` |
| 12 | Caption Oggi | 14 dp, margine top 2 | Caption 11 sp | `text_tertiary` |
| 13 | Mini barre | 40 dp di altezza, 60 dp di larghezza, 7 barre | — | `accent` oggi, `bar_past` altri |
| 14 | Header rilevamenti | 48 dp, margine top 32 | Overline + link 12 sp | `text_secondary`; link `accent` |
| 15 | Card rilevamenti | Raggio 16, padding verticale 4, margine top 8 | — | `surface` |
| 16 | Riga rilevamento | 48 dp, padding orizzontale 16, hairline 1 | Mono S / Mono M | Tempo `text_secondary`; importo `text_primary` |
| 17 | Footer | 16 dp, margine top 24 | Caption 11 sp | `text_tertiary` |

Dettagli dell'hero:

- Label e dot sono sulla stessa riga; il dot è allineato al centro ottico della label, non alla baseline.
- Il saldo è allineato a sinistra. Non è centrato: la lettura parte dal numero e prosegue verso il basso.
- La meta combina tempo e valuta: `Aggiornato alle 14:32 · USD`. Se il dato è di ieri: `Aggiornato ieri alle 18:04 · USD`. Se è più vecchio: `Aggiornato il 20 ott, 18:04 · USD`.
- La barra di ripartizione è proporzionale. Se un segmento vale 0, non viene disegnato e l'altro occupa tutto. Se entrambi sono 0, resta il track con bordo 1 dp. Un segmento non nullo ha minimo 3 dp, sottratti al segmento più grande, per restare visibile.
- La legenda è a due righe, non a due colonne: label a sinistra, valore a destra. È più leggibile con importi lunghi e funziona come una piccola tabella.
- Il dot di stato ha quattro valori: positivo (dati freschi, entro 2 intervalli), warning (dati vecchi o sotto soglia), errore (ultimo tentativo fallito), accento (aggiornamento in corso).

Dettagli della card Oggi:

- La caption è onesta: `stima locale · dalle 09:12`, dove l'ora è il primo rilevamento del giorno. Se i rilevamenti coprono dalla mezzanotte, `stima locale · dalle 00:00`. Con un solo rilevamento: `prima stima di oggi`. Senza rilevamenti: `nessun rilevamento oggi`.
- Le sette barre rappresentano gli ultimi sette giorni, dalla più vecchia a oggi. Altezza normalizzata sul massimo; un giorno a zero ha uno stub di 2 dp. Oggi è in accento pieno, gli altri al 35%.
- La card è interamente cliccabile e apre lo Storico con filtro 7 giorni. Il ripple è quello della card; nessun chevron, per non aggiungere rumore.

Dettagli della lista rilevamenti:

- Mostra gli ultimi 3 rilevamenti, dal più recente. Se un rilevamento è di un giorno precedente, il tempo diventa `20 ott 18:04` in Mono S.
- Le righe non sono cliccabili e non hanno ripple. Il contenuto è informativo. La copia è disponibile solo sul saldo grande, con pressione lunga.
- La hairline tra le righe è `border`, inset 16 dp a sinistra e a destra.

### 3.3 Stati della Dashboard

| Stato | Cosa vede l'utente | Trattamento | Testo esatto |
|---|---|---|---|
| Primo avvio, nessuna chiave | Top bar, stato vuoto centrato; nessun dato finto | Icona key 28 dp, titolo, body, pulsante primario 48 dp; pull disabilitato | `Nessuna chiave API` / `Aggiungi la tua chiave DeepSeek per iniziare a monitorare il saldo.` / `Aggiungi chiave` / hint `La chiave si trova nella console DeepSeek.` |
| Caricamento iniziale | Skeleton di hero, card Oggi e 3 righe | Blocchi `skeleton`, pulsazione 900 ms; nessun testo visibile; pull disabilitato | Content description: `Caricamento in corso` |
| Errore rete con cache | Dati precedenti, banner warning, dot warning | Banner `warning_container`, icona warning, azione `Riprova` 48 dp | `Offline. Dati delle 14:32.` oppure `DeepSeek non risponde. Dati delle 14:32.` oppure `Troppe richieste. Dati delle 14:32.` |
| Errore rete senza cache | Stato di errore pieno, nessun dato | Icona cloud_off 28, titolo, body, primario `Riprova`, secondario `Apri impostazioni`; pull attivo | `Impossibile aggiornare` / `Controlla la connessione. I dati compaiono al primo aggiornamento riuscito.` |
| Chiave non valida | Stato di errore pieno; dati nascosti per non sembrare validi | Icona key_off 28, titolo, body, primario `Apri impostazioni`; pull disabilitato | `Chiave non valida` / `DeepSeek ha rifiutato la chiave salvata. Aggiornala nelle impostazioni.` |
| Offline con cache | Dati precedenti, banner warning, meta invariata | Come errore rete con cache; dot warning | `Offline. Dati delle 14:32.` / azione `Riprova` |
| Saldo sotto soglia | Dati normali, banner warning sopra la card Oggi | Container warning, icona warning, nessuna azione; dot warning; il saldo resta primario | `Saldo sotto la soglia` / `Restano $3,12. Soglia: $10,00.` |
| Saldo non disponibile | Saldo `—`, legenda `—`, banner warning | Icona warning, container warning, dot warning | `Saldo non disponibile` / `DeepSeek non riporta un saldo per questo account. Riprova più tardi.` |
| Refresh in corso | Dati precedenti, spinner del pull, meta aggiornata | Dot accento, meta in accento; nessuno skeleton | `Aggiorno… · USD` |
| Spesa oggi senza dati | Valore `$0,00`, caption diversa | Nessun colore di errore | `prima stima di oggi` oppure `nessun rilevamento oggi` |

Priorità dei banner: errore di rete con cache > saldo non disponibile > soglia. Un solo banner alla volta. L'autenticazione e l'assenza di chiave non usano banner: prendono tutta la schermata, perché senza chiave non esiste un dato utile.

### 3.4 Tap e target

| Elemento | Azione | Feedback | Target |
|---|---|---|---|
| Icona storico | Apre Storico, filtro 7 giorni | Ripple bounded | 48x48 |
| Icona impostazioni | Apre Impostazioni | Ripple bounded | 48x48 |
| Saldo | Pressione lunga: copia `USD 21,40` | Tick aptico, Snackbar `Saldo copiato` 2 s | Altezza 54, larghezza piena |
| Card Oggi | Apre Storico con filtro 7 giorni | Ripple card | Intera card, min 104 |
| `Vedi tutto` | Apre Storico, filtro 7 giorni | Ripple + testo accento | 48 di altezza |
| `Riprova` nel banner | Rilancia il refresh | Spinner, banner resta finché non finisce | 48 di altezza |
| `Aggiungi chiave` | Apre Impostazioni con focus sul campo chiave | Ripple, tastiera | 48 di altezza, larghezza piena |

Il pull-to-refresh è attivo solo quando esiste una chiave e non c'è un errore di autenticazione. Durante il refresh manuale lo spinner resta visibile almeno 500 ms per evitare un flash.

## 4. Storico

### 4.1 Struttura

```
┌──────────────────────────────────────┐
│ ←  Storico                            │ 56 dp
├──────────────────────────────────────┤
│ [ 7 giorni ]  [ 30 giorni ]           │ 48 dp
│                                       │
│ ╭ Saldo                   attuale $21,40 ╮
│ │ $28 ┤                                  │
│ │     │        ╱╲                        │
│ │ $24 ┤    ╱╲╱   ╲                       │
│ │     │  ╱        ╲___●                  │
│ │ $21 ┤╱                                 │
│ │     15   18   21 ott                   │
│ │ Min $21,40              Max $28,10    │
│ ╰──────────────────────────────────────╯
│ ╭ Spesa giornaliera        totale $7,12 ╮
│ │        ▂                                │
│ │  ▆  ▅  █  ▃  ▂  ▁  ▂                   │
│ │  15 16 17 18 19 20 21                  │
│ │ Stimata dai cali di saldo. Ricariche   │
│ │ e omaggi non contano.                  │
│ ╰──────────────────────────────────────╯
│ RILEVAMENTI                  42 rilevamenti
│ OGGI · 21 OTT                    −$0,42
│ 14:32                            $21,40
│ 13:32                            $21,58
│ 12:32                            $21,82
│ 20 OTT                           −$1,05
│ 18:04                            $21,82
│ ...                                     │
│ [ Carica altri ]                       │
└──────────────────────────────────────┘
```

Root: `SwipeRefreshLayout` con `RecyclerView`. Filtro, grafico, barre e intestazioni sono item della RecyclerView, non contenuti dentro un `NestedScrollView`: evita lo scrolling annidato e permette liste lunghe senza degradare le prestazioni.

### 4.2 Anatomia

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Top bar | 56 dp, back 48x48, titolo a 20 dp dal bordo | Title screen | `text_primary` |
| 2 | Filtro | Due pulsanti affiancati, 48 dp, gap 8, raggio 12 | Segment | Non selezionato: `surface` + bordo `border`, testo `text_secondary`; selezionato: `accent_container`, testo `accent` |
| 3 | Card saldo | Raggio 16, padding 16, margine top 20 | — | `surface` |
| 4 | Header card | Riga 24: titolo a sinistra, valore attuale a destra | Title card + Mono S | Titolo `text_primary`; valore `text_secondary` |
| 5 | Grafico | 200 dp, margine top 12 | Mono XS per assi | Linea `accent` 2 dp; griglia `chart_grid`; area `chart_fill` |
| 6 | Caption min/max | 16 dp, margine top 8, due colonne | Mono XS 11 sp | `text_tertiary` |
| 7 | Card spesa | Raggio 16, padding 16, margine top 12 | — | `surface` |
| 8 | Header spesa | Riga 24: titolo + totale | Title card + Mono S | `text_primary` / `text_secondary` |
| 9 | Barre | 112 dp comprensivi di label, margine top 12 | Mono XS 9 sp per date | `accent` oggi, `bar_past` altri, stub `border` |
| 10 | Caption stima | Margine top 8, max 2 righe | Caption 11 sp | `text_tertiary` |
| 11 | Header rilevamenti | 48 dp, margine top 32 | Overline + Mono S | `text_secondary` / `text_tertiary` |
| 12 | Intestazione giorno | 32 dp, sfondo `bg`, padding orizzontale 0 | Overline 11 sp | `text_secondary`; spesa `text_secondary` |
| 13 | Riga rilevamento | 48 dp, padding orizzontale 0, hairline 1 | Mono S tempo / Mono M saldo | Tempo `text_secondary`; saldo `text_primary` |
| 14 | Carica altri | 48 dp, raggio 12, margine 12 sopra e 24 sotto | Button | Outline `border`, testo `text_primary` |
| 15 | Strip stato locale | 40 dp, margine top 8, padding 12 | Caption 12 sp | Warning o errore + dot |

Dettagli:

- Il filtro 7/30 è l'unico controllo della schermata. Non ci sono menu, overflow o icone di refresh: il refresh è il pull.
- Il filtro non cambia la schermata, cambia i dati di grafico, barre e lista. La selezione resta in `savedInstanceState`; default 7 giorni.
- La card del saldo mostra il valore attuale a destra, non il valore del primo punto. Il grafico parte dal primo rilevamento disponibile nel periodo.
- Le date sull'asse X sono tre: primo giorno, giorno centrale, ultimo. Se la larghezza utile è inferiore a 220 dp, si mostrano solo primo e ultimo. Le label non si sovrappongono: il custom View le salta se non c'è spazio.
- Il grafico non ha tooltip né scrubber. Il dettaglio è nella lista. Un grafico che si trascina dentro una schermata che si trascina è una fonte di errori.
- Le barre mostrano i giorni senza spesa come stub di 2 dp, non come assenza. Un giorno senza rilevamenti è comunque un giorno a spesa 0 nella stima locale.
- La caption della stima è obbligatoria: `Stimata dai cali di saldo. Ricariche e omaggi non contano.` Non è un disclaimer legale, è il modello di calcolo.
- La lista è raggruppata per giorno. L'intestazione mostra la spesa stimata del giorno con il meno; se la spesa è zero, mostra `—`.
- La lista carica 50 rilevamenti per volta. `Carica altri` è un pulsante outline, non uno scroll infinito: l'utente sa quando sta chiedendo altri dati.

### 4.3 Stati dello Storico

| Stato | Cosa vede l'utente | Trattamento | Testo esatto |
|---|---|---|---|
| Normale | Grafico, barre, lista | Nessun banner | — |
| Dati parziali nel periodo | Grafico e barre con i giorni disponibili | Caption sotto il filtro, 11 sp tertiary | `Dati dal 14 ott.` |
| Nessun dato | Card grafico con placeholder centrato; barre stub; lista vuota | Placeholder 13 sp secondary; CTA outline | `Nessun rilevamento nel periodo.` / `Lo storico inizia al primo aggiornamento riuscito.` / `Torna alla dashboard` |
| Nessun dato con chiave assente | Come sopra, con CTA impostazioni | Nessun colore di errore | `Nessun rilevamento nel periodo.` / `Aggiungi una chiave per iniziare.` / `Aggiungi chiave` |
| Un solo rilevamento | Un dot e una guida orizzontale tratteggiata; nessuna linea | Il grafico resta alto 200 dp; caption sotto | `Serve un secondo rilevamento per la linea.` |
| Offline con dati locali | Dati locali visibili, strip warning sopra il filtro | Strip 40 dp, dot warning, nessuna azione | `Offline. Lo storico locale resta disponibile.` |
| Chiave non valida | Dati locali visibili, strip errore sopra il filtro | Strip 40 dp, dot errore, link `Impostazioni` | `Chiave non valida. Lo storico locale resta disponibile.` |
| Caricamento iniziale | Nessuno spinner: la lettura è locale | Se la query supera 150 ms, skeleton di grafico e 3 righe; altrimenti nessun flash | Content description: `Caricamento dello storico` |
| Caricamento altri | Footer con indicatore 16 dp e testo | Pulsante disabilitato | `Carico…` |
| Lettura locale fallita | Stato di errore nella lista | Icona error, body, `Riprova` | `Impossibile leggere lo storico.` / `Riprova` |

La strip di stato non nasconde i dati: lo storico è locale e resta valido anche se la chiave è stata rifiutata o la rete manca. È una differenza deliberata rispetto alla Dashboard, dove il saldo attuale non è affidabile senza chiave.

### 4.4 Tap e target

| Elemento | Azione | Feedback | Target |
|---|---|---|---|
| Back | Torna alla Dashboard | Ripple | 48x48 |
| 7 giorni / 30 giorni | Cambia periodo e ricarica | Ripple, selezione accento | 48 di altezza ciascuno |
| `Carica altri` | Aggiunge 50 rilevamenti | Spinner nel pulsante | 48 di altezza |
| `Riprova` | Rilegge il DB | Ripple | 48 di altezza |
| Pull-to-refresh | Aggiorna il saldo e ricarica la lista | Spinner accento | Gesto su tutta la lista |
| Righe rilevamento | Nessuna azione | Nessun ripple | 48 di altezza, non interattive |

Il grafico e le barre non sono interattivi. Non hanno ripple e non ricevono focus. La loro descrizione accessibile è l'unico contenuto non visivo necessario.

## 5. Impostazioni

### 5.1 Struttura

```
┌──────────────────────────────────────┐
│ ←  Impostazioni                       │ 56 dp
├──────────────────────────────────────┤
│ ACCESSO                               │
│ ╭──────────────────────────────────╮ │
│ │ [ Chiave API           occhio ]   │ │ 56 dp
│ │ Resta sul dispositivo, cifrata    │ │
│ │ con Android Keystore.             │ │
│ │ [        Salva chiave        ]    │ │ 48 dp
│ │ [     Testa connessione      ]    │ │ 48 dp
│ │ ● Connessione riuscita. Saldo ... │ │
│ ╰──────────────────────────────────╯ │
│ AVVISI                                │
│ ╭──────────────────────────────────╮ │
│ │ Soglia di avviso         $10,00  │ │
│ │ ────────●───────────────         │ │
│ │ $0                         $100  │ │
│ │ Il banner e la notifica...        │ │
│ ╰──────────────────────────────────╯ │
│ AGGIORNAMENTO                         │
│ ╭──────────────────────────────────╮ │
│ │ Intervallo                        │ │
│ │ [15 min] [30 min] [60 min]        │ │
│ │ Android può posticipare...        │ │
│ ╰──────────────────────────────────╯ │
│ INFORMAZIONI                          │
│ ╭──────────────────────────────────╮ │
│ │ Versione              1.0.0 (1)  │ │
│ │ Chiave e storico restano...      │ │
│ ╰──────────────────────────────────╯ │
└──────────────────────────────────────┘
```

Root: `ScrollView` con `adjustResize`, padding orizzontale 20, top 8, bottom 32 più inset. Le sezioni sono separate da 32 dp; ogni sezione ha una overline e una card.

### 5.2 Anatomia

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Overline sezione | 14 dp, margine bottom 8 | Overline | `text_secondary` |
| 2 | Card | Raggio 16, padding 16, bordo 1 | — | `surface` |
| 3 | Campo chiave | 56 dp, raggio 12, icona occhio 48 a fine | Mono S 13 sp | Testo `text_primary`, hint `text_tertiary`, stroke `border`, focus `accent` |
| 4 | Helper | 12 sp, margine top 6, max 2 righe | Caption | `text_tertiary` |
| 5 | Pulsante primario | 48 dp, raggio 12, larghezza piena, margine top 12 | Button | `accent` / `on_accent`; disabled alpha 38% |
| 6 | Pulsante outline | 48 dp, raggio 12, margine top 8, icona 18 | Button | Bordo `border`, testo `text_primary` |
| 7 | Riga stato test | 32 dp, margine top 8, dot 6 + testo | Caption 12 sp | Positivo / errore / warning |
| 8 | Riga soglia | 24 dp: titolo a sinistra, valore a destra | Title card + Mono M | `text_primary` |
| 9 | Slider | 48 dp di area, margine top 12 | — | Track `border`, attivo `accent`, thumb `accent` |
| 10 | Range slider | 14 dp, margine top 4, due estremi | Caption 11 sp | `text_tertiary` |
| 11 | Helper avvisi | 12 sp, margine top 8 | Caption | `text_tertiary` |
| 12 | Segmenti intervallo | Tre pulsanti, 48 dp, gap 8 | Segment | Come il filtro dello Storico |
| 13 | Riga versione | 48 dp, due colonne | Label + Mono S | `text_primary` / `text_secondary` |
| 14 | Nota privacy | 12 sp, margine top 12, max 3 righe | Caption | `text_secondary` |

Dettagli:

- Il campo chiave non mostra mai la chiave salvata. Dopo il salvataggio il campo è vuoto, l'hint diventa `Nuova chiave API` e l'helper spiega che la chiave è già salvata. Per sostituirla si incolla una nuova chiave e si salva.
- L'occhio mostra o nasconde il contenuto del campo. Non mostra mai la chiave salvata: non esiste un percorso per rileggerla in chiaro dall'interfaccia.
- Il salvataggio è esplicito: `Salva chiave`. Non si salva a ogni carattere. Dopo il salvataggio parte automaticamente un test di connessione.
- `Testa connessione` usa la chiave salvata. Se il campo è stato modificato, il pulsante è disabilitato finché non si salva: evita di testare una chiave che non è quella memorizzata.
- La soglia e l'intervallo si salvano al rilascio del controllo, senza pulsante Salva. Non c'è un pulsante "Annulla".
- Lo slider soglia va da 0 a 100, step 1, nella valuta dell'account. `0` disattiva di fatto l'avviso. Il valore è sempre visibile a destra in Mono M.
- L'intervallo è 15/30/60 minuti. Il default è 60. La modifica vale dal prossimo aggiornamento; non forza un refresh immediato.
- La versione è `1.0.0 (1)`, allineata a `versionName` e `versionCode`.
- La nota privacy dice la verità: chiave e storico restano sul dispositivo; le richieste di saldo vanno solo a `api.deepseek.com`.

### 5.3 Stati delle Impostazioni

| Stato | Cosa vede l'utente | Trattamento | Testo esatto |
|---|---|---|---|
| Nessuna chiave | Campo vuoto, helper, `Salva chiave` disabilitato, test disabilitato | Nessun errore | Hint `Chiave API`; helper `La chiave resta sul dispositivo, cifrata con Android Keystore.` |
| Chiave salvata | Campo vuoto, hint nuova chiave, test attivo | Riga stato positiva con dot | `Chiave salvata` / `Inserisci una nuova chiave solo per sostituirla.` |
| Campo modificato | Test disabilitato, salva attivo | Nessun errore | Helper `Salva per verificare la nuova chiave.` |
| Salvataggio in corso | Pulsante primario disabilitato con indicatore 16 dp | `Salvo…` | `Salvo…` |
| Test in corso | Pulsante outline disabilitato con indicatore 16 dp | `Verifica in corso…` | `Verifica in corso…` |
| Test riuscito | Riga stato positiva | Dot positivo | `Connessione riuscita. Saldo $21,40.` |
| Chiave rifiutata | Campo in errore, bordo errore 2 dp | Dot errore | `Chiave rifiutata. Controlla di averla copiata per intero.` |
| Errore di rete nel test | Riga stato errore, campo invariato | Dot errore | `Connessione non riuscita. Controlla la rete.` |
| Risposta non leggibile | Riga stato warning | Dot warning | `Risposta non leggibile. Riprova.` |
| Salvataggio fallito | Riga stato errore | Dot errore | `Impossibile salvare la chiave. Riprova.` |
| Notifiche disattivate | Riga sotto la soglia, con pulsante | Dot warning, pulsante testo 48 | `Notifiche disattivate. L'avviso resta visibile nell'app e nel widget.` / `Apri impostazioni notifiche` |
| Saldo già sotto soglia | Riga warning sotto lo slider | Dot warning | `Il saldo è sotto questa soglia.` |

La riga di stato del test compare sotto i pulsanti, non al posto dell'helper. L'helper di sicurezza resta sempre visibile; lo stato è un'informazione aggiuntiva.

### 5.4 Tap e target

| Elemento | Azione | Feedback | Target |
|---|---|---|---|
| Back | Torna alla Dashboard | Ripple | 48x48 |
| Campo chiave | Focus, tastiera, selezione tutto il testo | Bordo accento 2 dp | 56 di altezza |
| Occhio | Mostra/nasconde il testo del campo | Icona cambia, content description aggiornata | 48x48 |
| `Salva chiave` | Cifra, salva, testa | Spinner, riga stato | 48 di altezza |
| `Testa connessione` | Verifica la chiave salvata | Spinner, riga stato | 48 di altezza |
| Slider soglia | Cambia il valore, salva al rilascio | Valore aggiornato live; tick aptico leggero allo step | Area 48 di altezza |
| Segmenti intervallo | Cambia intervallo e rischedula | Ripple, selezione accento | 48x? larghezza terzo |
| `Apri impostazioni notifiche` | Apre le impostazioni di sistema dell'app | Ripple | 48 di altezza |

Il pulsante `Salva chiave` non è mai a larghezza parziale: due azioni affiancate sarebbero troppo strette a 360 dp e con testo italiano. La gerarchia è chiara: salvataggio pieno, test outline.

### 5.5 Avviso di soglia e notifica

La soglia produce due segnali: un banner in Dashboard e una notifica di sistema, se il permesso è concesso. La notifica è un avviso, non un flusso da gestire.

- Canale: `Avvisi di saldo`, descrizione `Un avviso quando il saldo scende sotto la soglia.`, importance default.
- Titolo: `Saldo sotto la soglia`.
- Testo: `Restano $3,12. Soglia: $10,00.`
- Tap: apre la Dashboard. La Dashboard rivalida il saldo.
- Una sola notifica per attraversamento: si ripete solo dopo che il saldo è risalito sopra la soglia o la soglia è cambiata. È coerente con `AlertNotifier` e `Prefs.isBelowThreshold`.
- Nessuna notifica per errori di rete: il degrado è visibile nell'app e nel widget, non merita una notifica.
- Nessuna richiesta automatica del permesso all'avvio. In Impostazioni, se le notifiche sono disattivate, compare la riga con `Apri impostazioni notifiche`; il permesso viene chiesto solo su azione dell'utente.
- Se il permesso è negato, il banner e lo stato del widget restano: la funzionalità non si perde, cambia il canale.

La notifica usa l'icona monocromatica `ic_notification`, sfondo del sistema, nessuna action, nessun chronometer. Non usa il colore accento: le notifiche di sistema hanno già la loro identità.

## 6. Widget 2x2

### 6.1 Specifiche

| Voce | Valore |
|---|---|
| Celle | 2x2 |
| minWidth x minHeight | 110 x 110 dp |
| targetCellWidth / Height | 2 / 2 (API 31+) |
| Padding interno | 12 dp su tutti i lati |
| Raggio | 24 dp, con bordo 1 dp |
| Sfondo | `surface` opaco, bordo `border` |
| Font | Plex Mono per i numeri, Plex Sans per label e stato |
| Tipografia | Label 9 dp, saldo 22 dp, oggi 11 dp, stato 9 dp |
| Tap | Refresh del widget; apre l'app solo negli stati di chiave mancante o non valida |
| Refresh | Broadcast `ACTION_REFRESH`, stesso WorkManager dell'app |

```
┌──────────────────────┐  110 x 110 dp
│ SALDO                │  12 dp padding
│                      │
│ $21,40               │  22 dp
│ oggi $0,42           │  11 dp
│                      │
└──────────────────────┘
```

### 6.2 Anatomia

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Root | 110x110, padding 12, raggio 24, bordo 1 | — | `surface` / `border` |
| 2 | Riga label | 12 dp: label a sinistra, dot 5 dp a destra solo se non normale | Label 9 dp, tracking 0.08, maiuscolo | `text_secondary`; dot di stato |
| 3 | Saldo | 30 dp di altezza, maxLines 1, nessuna ellissi | Mono 22 dp, peso 500 | `text_primary` |
| 4 | Oggi | 14 dp, maxLines 1 | Mono 11 dp | `text_secondary` |
| 5 | Stato | 12 dp, visibile solo se non normale; maxLines 1, ellissi end | Sans 9 dp | Warning, errore o accento |

Il widget non ha timestamp: a 110 dp il tempo toglierebbe spazio al saldo e al valore di oggi. La freschezza è comunicata dal dot e, quando serve, dalla riga di stato.

Il saldo e "oggi" sono separati da 2 dp. La riga di stato, quando presente, sta sotto "oggi" con 2 dp. Il layout usa `gravity="top"` e uno spacer flessibile: con valori corti il contenuto resta in alto, non si centra a caso.

### 6.3 Formattazione e troncamento

Il budget utile è 86 dp. A 22 dp il mono ha un advance di circa 13,2 dp per glifo, quindi il saldo può avere al massimo 6 glifi. La formattazione garantisce il budget; non si usa ellissi sul saldo.

| Valore | Saldo mostrato | Note |
|---|---|---|
| `$21,40` | `$21,40` | 6 glifi; centesimi sotto 100 |
| `$99,99` | `$99,99` | 6 glifi; caso limite con centesimi |
| `$100,00` | `$100` | Sopra 100 si perdono i centesimi |
| `$999,99` | `$999` | 4 glifi |
| `$1.234,50` | `$1.234` | 6 glifi; separatore migliaia |
| `$9.999,99` | `$9.999` | 6 glifi |
| `$12.345,67` | `$12,3k` | 6 glifi; un decimale fino a 99.999 |
| `$123.456,78` | `$123k` | 5 glifi; intero sopra 100.000 |
| `$1.234.567,89` | `$1,2M` | 5 glifi |
| Nessun dato | `—` | Em dash, centrato otticamente |

"Oggi" ha 13 glifi disponibili (86 dp a 11 dp). Usa `oggi ` più il valore: centesimi sotto 100, intero da 100 a 9.999, `k` oltre. Se il valore supera il budget, si passa a `oggi $12,3k`; se anche questo non entra per un font di sistema alterato, si mostra `oggi —` e la descrizione accessibile conserva il valore completo. Non si tronca mai con puntini.

La riga di stato è l'unica che può usare ellissi. I testi sono già brevi: `offline`, `sotto soglia`, `chiave non valida`, `aggiungi chiave`, `aggiorno…`, `dati non leggibili`, `tocca per aggiornare`. A 9 dp sans entrano in 86 dp; l'ellissi è una rete di sicurezza.

### 6.4 Stati del widget 2x2

| Stato | Saldo | Riga oggi | Riga stato | Dot |
|---|---|---|---|---|
| Normale, fresco | `$21,40` | `oggi $0,42` | nascosta | nascosto |
| Refresh in corso | valore precedente | invariata | `aggiorno…` | accento |
| Offline con cache | valore cache | invariata | `offline` | ambra |
| Offline senza cache | `—` | `oggi —` | `tocca per aggiornare` | ambra |
| Chiave mancante | `—` | `oggi —` | `aggiungi chiave` | ambra |
| Chiave non valida | `—` | `oggi —` | `chiave non valida` | corallo |
| Sotto soglia | `$3,12` | `oggi $1,08` | `sotto soglia` | ambra |
| Dati non leggibili | valore cache | invariata | `dati non leggibili` | corallo |
| Dati vecchi, nessun errore | valore cache | invariata | `dati di ieri` se il giorno è cambiato, altrimenti `dati vecchi` | ambra |
| Nessun dato, mai aggiornato | `—` | `oggi —` | `tocca per aggiornare` | ambra |

I valori non si nascondono in caso di errore di rete: sono l'ultimo dato valido e la riga di stato dice che è vecchio. Si nascondono solo per chiave mancante o non valida, perché in quel caso l'app non può garantire che il dato rifletta l'account.

## 7. Widget 4x2

### 7.1 Specifiche

| Voce | Valore |
|---|---|
| Celle | 4x2 |
| minWidth x minHeight | 250 x 110 dp |
| targetCellWidth / Height | 4 / 2 (API 31+) |
| Padding interno | 14 dp orizzontale, 12 dp verticale |
| Raggio | 24 dp, con bordo 1 dp |
| Ridimensionamento | Orizzontale; il contenuto si adatta in larghezza |
| Tipografia | Label 9 dp, saldo 30 dp, label metriche 8 dp, valori metriche 12 dp, timestamp 9 dp, stato 9 dp |

```
┌────────────────────────────────────────┐  250 x 110 dp
│ SALDO                    agg. 14:32    │  12 dp
│ $21,40                                 │  30 dp
│ OGGI        OMAGGIO       RICARICHE    │  8 dp
│ $0,42       $2,10         $19,30       │  12 dp
└────────────────────────────────────────┘
```

### 7.2 Anatomia

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Root | 250x110, padding 14/12, raggio 24, bordo 1 | — | `surface` / `border` |
| 2 | Riga label | 12 dp: `SALDO` a sinistra, timestamp a destra | Label 9 dp / Mono 9 dp | `text_secondary` / `text_tertiary` |
| 3 | Saldo | 38 dp, maxLines 1, nessuna ellissi | Mono 30 dp, peso 500 | `text_primary` |
| 4 | Riga metriche | 28 dp, tre colonne con peso 1, gap 8 | Label 8 dp + Mono 12 dp | Label `text_secondary`; valori `text_primary` |
| 5 | Dot | 5 dp, accanto a `SALDO`, solo se non normale | — | Warning, errore o accento |
| 6 | Timestamp | Allineato a destra, maxLines 1 | Mono 9 dp | `text_tertiary`; warning/errore quando serve |

Le tre metriche sono `OGGI`, `OMAGGIO`, `RICARICHE`, nell'ordine. Oggi è la spesa del giorno; omaggio e ricariche sono le due parti del saldo. Non ci sono separatori verticali: il ritmo è dato dal gap di 8 dp e dall'allineamento a sinistra di ogni colonna.

Il saldo può usare i centesimi perché a 30 dp e 222 dp di larghezza entrano 11 glifi (`$999.999,99`). Oltre 999.999,99 si passa al compatto (`$1,2M`) per non sforare. Non si tronca.

### 7.3 Formattazione e troncamento

| Contesto | Budget | Regola | Esempio |
|---|---|---|---|
| Saldo | 11 glifi | 2 decimali fino a 999.999,99; poi `M` | `$21,40`, `$1.234,56`, `$1,2M` |
| Oggi | 7 glifi | 2 decimali sotto 1.000; intero 1.000–9.999; `k` oltre | `$0,42`, `$999,99`, `$1.234`, `$12,3k` |
| Omaggio | 7 glifi | Come oggi | `$2,10`, `$999`, `$12,3k` |
| Ricariche | 7 glifi | Come oggi | `$19,30`, `$999`, `$12,3k` |
| Timestamp | 16 glifi | `agg. HH:mm`; se non oggi `agg. ieri HH:mm` | `agg. 14:32`, `agg. ieri 18:04` |
| Stato | 20 glifi | Sostituisce il timestamp quando c'è un problema | `offline · 14:32`, `sotto soglia · 14:32`, `chiave non valida` |

Se una metrica non entra nel budget, si riduce a intero, poi a `k`, poi a `—`. Non si usa ellissi. Le label non si troncano mai: sono parole fisse e corte.

Con larghezze superiori a 250 dp le tre colonne si allargano, il saldo resta a sinistra e il timestamp resta a destra. Non si aggiungono elementi: il widget non diventa una dashboard.

### 7.4 Stati del widget 4x2

| Stato | Saldo | Metriche | Timestamp / stato | Dot |
|---|---|---|---|---|
| Normale, fresco | `$21,40` | `$0,42`, `$2,10`, `$19,30` | `agg. 14:32` | nascosto |
| Refresh in corso | valore precedente | invariate | `aggiorno…` | accento |
| Offline con cache | valore cache | invariate | `offline · 14:32` | ambra |
| Offline senza cache | `—` | `—`, `—`, `—` | `offline · senza dati` | ambra |
| Chiave mancante | `—` | `—`, `—`, `—` | `aggiungi chiave` | ambra |
| Chiave non valida | `—` | `—`, `—`, `—` | `chiave non valida` | corallo |
| Sotto soglia | `$3,12` | `$1,08`, `$0,00`, `$3,12` | `sotto soglia · 14:32` | ambra |
| Dati non leggibili | valore cache | invariate | `dati non leggibili` | corallo |
| Dati vecchi | valore cache | invariate | `agg. ieri 18:04` oppure `dati vecchi · 14:32` | ambra |
| Nessun dato | `—` | `—`, `—`, `—` | `tocca per aggiornare` | ambra |

`offline · senza dati` è l'unico caso in cui il timestamp non è disponibile: non esiste un ultimo successo. Non si mostra `agg. —`.

### 7.5 Tap, refresh e accessibilità dei widget

- Tap sul widget: refresh in place. Il tap non apre l'app nel caso normale, perché il widget è uno strumento di consultazione rapida e l'aggiornamento è l'azione più utile. Un secondo tap durante il refresh non accoda un secondo lavoro: WorkManager usa un lavoro unico.
- Tap con chiave mancante o non valida: apre l'app su Impostazioni. La riga di stato lo rende coerente: l'utente sta per risolvere quel problema, non per aggiornare.
- Feedback del tap: il widget passa subito a `aggiorno…` con dot accento. Non c'è ripple nelle RemoteViews; il cambio di testo è la conferma.
- Timeout: 20 secondi. Dopo, si torna allo stato offline con i valori cache; `aggiorno…` non resta mai appeso.
- Accessibilità: la root ha una descrizione completa, ad esempio `Saldo DeepSeek: 21 dollari e 40 centesimi. Speso oggi: 42 centesimi. Aggiornato alle 14:32.` Nel 4x2 include anche omaggio e ricariche. I valori sono quelli completi, non quelli compatti.
- I widget seguono il tema di sistema (night mode), non un'impostazione interna dell'app. Se il launcher è in chiaro, il widget è chiaro.
- I font sono inclusi nel pacchetto. Non si usano downloadable fonts nei widget.
- I testi sono in dp: con font scale 1.3 il widget non cresce e non clippa. La descrizione accessibile compensa l'eventuale perdita di dettaglio visivo.

## 8. Tema chiaro e tema scuro

L'app segue il tema di sistema. Il tema scuro è il riferimento di design; il chiaro è completo e non è un'inversione automatica. Non c'è un toggle interno nella v1.

| Cosa cambia | Dark | Light |
|---|---|---|
| Sfondo e superfici | near-black tinted, surface più chiara | off-white, card bianche |
| Testo | chiaro su scuro | scuro su chiaro |
| Accento | ottanio luminoso `#2FD3BE` | ottanio profondo `#0A7065` |
| Container di stato | warning/error scuri, testo chiaro | warning/error pastello, testo scuro |
| Bordi | `#243237`, visibili sul near-black | `#DCE5E5`, visibili sul bianco |
| Riempimento grafico | accento al 22% | accento al 15% |
| Barre passate | accento al 35% | accento al 30% |
| Ripple | accento al 20% | accento al 12% |
| Icone di sistema | chiare | scure |
| Widget | sfondo `#11171A` | sfondo `#FFFFFF` |

| Cosa non cambia | Nota |
|---|---|
| Spaziature, raggi, altezze | Gli stessi token dp |
| Tipografia e dimensioni | Gli stessi ruoli sp (dp nei widget) |
| Gerarchia e ordine | Nessun elemento si sposta |
| Copy | Gli stessi testi |
| Semantica dei colori | Accento, positivo, warning, errore mantengono il ruolo |
| Geometria del grafico | Assi, griglia, punti, animazione |
| Soglia, intervallo, formati | Nessuna differenza |

Regole tecniche:

- `Theme.Material3.DayNight.NoActionBar` come base; `values-night` per i colori. Se si vuole forzare lo scuro, si usa il parent Dark e si rimuove `values-night`: i token restano identici. I widget seguono comunque il sistema.
- `android:forceDarkAllowed=false` in entrambi i temi. Il force dark automatico romperebbe i contrasti e i container.
- Nessun dynamic color. `DynamicColors.applyToActivitiesIfAvailable` non va chiamato.
- Status bar trasparente; icone chiare nello scuro, scure nel chiaro. Navigation bar con sfondo `bg` e icone coerenti. Su API 35+ l'edge-to-edge è obbligatorio: il contenuto applica gli inset, non disegna sotto le barre.
- Lo splash screen usa lo sfondo `bg` e l'icona dell'app, senza animazioni aggiuntive.

## 9. Micro-copy definitiva

Regole: frasi brevi, concreto, nessun punto esclamativo, nessun intercalare, nessun "Congratulazioni". Il tono è quello di uno strumento: dice cosa è successo e cosa fare. "Tu" implicito, mai "Lei".

### 9.1 Dashboard

| Contesto | Testo |
|---|---|
| Titolo app / top bar | `Credito DeepSeek` |
| Label saldo | `SALDO TOTALE` |
| Meta aggiornamento | `Aggiornato alle {14:32} · {USD}` |
| Meta ieri | `Aggiornato ieri alle {18:04} · {USD}` |
| Meta precedente | `Aggiornato il {20 ott}, {18:04} · {USD}` |
| Refresh in corso | `Aggiorno… · {USD}` |
| Omaggio | `Omaggio` |
| Ricariche | `Ricariche` |
| Speso oggi | `SPESO OGGI` |
| Caption spesa | `stima locale · dalle {09:12}` |
| Caption prima stima | `prima stima di oggi` |
| Caption nessun dato | `nessun rilevamento oggi` |
| Mini barre | `7 giorni` |
| Sezione rilevamenti | `ULTIMI RILEVAMENTI` |
| Link | `Vedi tutto` |
| Footer | `Aggiornamento ogni {60} min · dati locali` |
| Copia saldo | `Saldo copiato` |
| Saldo accessibile | `Saldo totale: 21 dollari e 40 centesimi. Tieni premuto per copiare.` |

### 9.2 Storico

| Contesto | Testo |
|---|---|
| Titolo | `Storico` |
| Filtri | `7 giorni`, `30 giorni` |
| Card saldo | `Saldo`, `attuale { $21,40 }` |
| Min / Max | `Min { $21,40 }`, `Max { $28,10 }` |
| Card spesa | `Spesa giornaliera`, `totale { $7,12 }` |
| Caption stima | `Stimata dai cali di saldo. Ricariche e omaggi non contano.` |
| Sezione lista | `RILEVAMENTI` |
| Conteggio | `{42} rilevamenti` / `{1} rilevamento` |
| Intestazione oggi | `OGGI · {21 OTT}` |
| Intestazione ieri | `IERI · {20 OTT}` |
| Intestazione giorno | `{19 OTT}` |
| Spesa del giorno | `−{ $0,42 }` |
| Spesa zero | `—` |
| Carica altri | `Carica altri` |
| Caricamento | `Carico…` |
| Vuoto | `Nessun rilevamento nel periodo.` |
| Vuoto, spiegazione | `Lo storico inizia al primo aggiornamento riuscito.` |
| Vuoto, senza chiave | `Aggiungi una chiave per iniziare.` |
| CTA vuoto | `Torna alla dashboard` / `Aggiungi chiave` |
| Un punto | `Serve un secondo rilevamento per la linea.` |
| Dati parziali | `Dati dal {14 ott}.` |
| Offline | `Offline. Lo storico locale resta disponibile.` |
| Chiave non valida | `Chiave non valida. Lo storico locale resta disponibile.` |
| Errore locale | `Impossibile leggere lo storico.` |

### 9.3 Impostazioni e stati

| Contesto | Testo |
|---|---|
| Titolo | `Impostazioni` |
| Sezione accesso | `ACCESSO` |
| Hint campo | `Chiave API` |
| Hint nuova chiave | `Nuova chiave API` |
| Helper sicurezza | `La chiave resta sul dispositivo, cifrata con Android Keystore.` |
| Chiave salvata | `Chiave salvata` |
| Helper sostituzione | `Inserisci una nuova chiave solo per sostituirla.` |
| Helper modifica | `Salva per verificare la nuova chiave.` |
| Salva | `Salva chiave` |
| Salvataggio | `Salvo…` |
| Test | `Testa connessione` |
| Test in corso | `Verifica in corso…` |
| Test ok | `Connessione riuscita. Saldo { $21,40 }.` |
| Test auth | `Chiave rifiutata. Controlla di averla copiata per intero.` |
| Test rete | `Connessione non riuscita. Controlla la rete.` |
| Test risposta | `Risposta non leggibile. Riprova.` |
| Salvataggio fallito | `Impossibile salvare la chiave. Riprova.` |
| Sezione avvisi | `AVVISI` |
| Soglia | `Soglia di avviso` |
| Helper soglia | `Il banner e la notifica compaiono quando il saldo scende sotto questa cifra. 0 disattiva l'avviso.` |
| Sotto soglia ora | `Il saldo è sotto questa soglia.` |
| Notifiche off | `Notifiche disattivate. L'avviso resta visibile nell'app e nel widget.` |
| CTA notifiche | `Apri impostazioni notifiche` |
| Sezione intervallo | `AGGIORNAMENTO` |
| Intervallo | `Intervallo` |
| Opzioni | `15 min`, `30 min`, `60 min` |
| Helper intervallo | `Android può posticipare gli aggiornamenti per risparmiare batteria. Il widget usa lo stesso intervallo.` |
| Sezione info | `INFORMAZIONI` |
| Versione | `Versione` |
| Valore versione | `1.0.0 (1)` |
| Privacy | `Chiave e storico restano sul dispositivo. Le richieste di saldo vanno solo a api.deepseek.com.` |

### 9.4 Errori e stati vuoti della Dashboard

| Contesto | Testo |
|---|---|
| Nessuna chiave, titolo | `Nessuna chiave API` |
| Nessuna chiave, body | `Aggiungi la tua chiave DeepSeek per iniziare a monitorare il saldo.` |
| Nessuna chiave, CTA | `Aggiungi chiave` |
| Nessuna chiave, hint | `La chiave si trova nella console DeepSeek.` |
| Errore rete senza cache, titolo | `Impossibile aggiornare` |
| Errore rete senza cache, body | `Controlla la connessione. I dati compaiono al primo aggiornamento riuscito.` |
| Chiave non valida, titolo | `Chiave non valida` |
| Chiave non valida, body | `DeepSeek ha rifiutato la chiave salvata. Aggiornala nelle impostazioni.` |
| Offline con cache | `Offline. Dati delle {14:32}.` |
| Server non raggiungibile | `DeepSeek non risponde. Dati delle {14:32}.` |
| Troppe richieste | `Troppe richieste. Dati delle {14:32}.` |
| Retry | `Riprova` |
| Apri impostazioni | `Apri impostazioni` |
| Soglia, titolo | `Saldo sotto la soglia` |
| Soglia, body | `Restano { $3,12 }. Soglia: { $10,00 }.` |
| Saldo non disponibile, titolo | `Saldo non disponibile` |
| Saldo non disponibile, body | `DeepSeek non riporta un saldo per questo account. Riprova più tardi.` |
| Loading accessibile | `Caricamento in corso` |

### 9.5 Widget

| Contesto | Testo |
|---|---|
| Label saldo | `SALDO` |
| Oggi | `OGGI` |
| Omaggio | `OMAGGIO` |
| Ricariche | `RICARICHE` |
| Valore oggi 2x2 | `oggi { $0,42 }` |
| Aggiornato | `agg. {14:32}` |
| Ieri | `agg. ieri {18:04}` |
| Dati vecchi 4x2 | `dati vecchi · {14:32}` |
| Refresh | `aggiorno…` |
| Offline 2x2 | `offline` |
| Offline 4x2 | `offline · {14:32}` |
| Offline senza dati | `offline · senza dati` |
| Chiave mancante | `aggiungi chiave` |
| Chiave non valida | `chiave non valida` |
| Sotto soglia 2x2 | `sotto soglia` |
| Sotto soglia 4x2 | `sotto soglia · {14:32}` |
| Dati non leggibili | `dati non leggibili` |
| Dati vecchi 2x2 | `dati di ieri` / `dati vecchi` |
| Nessun dato | `tocca per aggiornare` |
| Valore assente | `—` |
| Descrizione 2x2 | `Saldo DeepSeek: {21,40} dollari. Speso oggi: {0,42} dollari. {Stato}.` |
| Descrizione 4x2 | `Saldo DeepSeek: {21,40} dollari. Omaggio: {2,10}. Ricariche: {19,30}. Speso oggi: {0,42}. Aggiornato alle {14:32}.` |

### 9.6 Notifica

| Contesto | Testo |
|---|---|
| Canale | `Avvisi di saldo` |
| Descrizione canale | `Un avviso quando il saldo scende sotto la soglia.` |
| Titolo | `Saldo sotto la soglia` |
| Testo | `Restano { $3,12 }. Soglia: { $10,00 }.` |

## 10. Token di design

### 10.1 Colori

Tabella pronta per `res/values/colors.xml` e `res/values-night/colors.xml`. I nomi sono già quelli da usare nelle risorse. I colori con alpha sono `#AARRGGBB`.

| Risorsa | Dark | Light | Uso |
|---|---|---|---|
| `ds_color_bg` | `#0A0E10` | `#F3F6F6` | Sfondo schermo, top bar, navigation bar |
| `ds_color_surface` | `#11171A` | `#FFFFFF` | Card, widget, campi |
| `ds_color_surface_high` | `#172024` | `#EAF0F0` | Segmenti non selezionati, skeleton, pressed |
| `ds_color_border` | `#243237` | `#DCE5E5` | Bordi, hairline, track |
| `ds_color_text_primary` | `#E8EEF0` | `#0B1416` | Testo primario e importi |
| `ds_color_text_secondary` | `#A7B4B9` | `#46585D` | Label, simbolo del saldo |
| `ds_color_text_tertiary` | `#7E8F95` | `#596B70` | Meta, caption, assi |
| `ds_color_accent` | `#2FD3BE` | `#0A7065` | Interazione, linea, link, focus |
| `ds_color_accent_container` | `#0E2F2C` | `#D8F1EC` | Segmento selezionato, pressed |
| `ds_color_on_accent` | `#04201D` | `#FFFFFF` | Testo su accento pieno |
| `ds_color_data_muted` | `#3C4C52` | `#B7C4C4` | Segmento ricariche, dati non attivi |
| `ds_color_positive` | `#5BD68A` | `#146C43` | Dati freschi, successo |
| `ds_color_warning` | `#E8B04B` | `#8A5200` | Soglia, dati vecchi, degrado |
| `ds_color_warning_container` | `#2E2413` | `#FBEED6` | Banner warning |
| `ds_color_error` | `#F0736A` | `#B3261E` | Errori bloccanti, chiave non valida |
| `ds_color_error_container` | `#2E1715` | `#FBE4E1` | Banner errore |
| `ds_color_on_error` | `#2A0B09` | `#FFFFFF` | Testo su errore pieno |
| `ds_color_chart_grid` | `#1F2B2F` | `#E3EBEB` | Griglia del grafico |
| `ds_color_chart_fill` | `#382FD3BE` | `#260A7065` | Area sotto la linea, 22% / 15% |
| `ds_color_bar_past` | `#592FD3BE` | `#4D0A7065` | Barre non odierne, 35% / 30% |
| `ds_color_skeleton` | `#1B2529` | `#E3EAEA` | Blocchi di caricamento |
| `ds_color_ripple` | `#332FD3BE` | `#1F0A7065` | Ripple, 20% / 12% |
| `ds_color_widget_bg` | `#11171A` | `#FFFFFF` | Sfondo widget, alias di surface |
| `ds_color_widget_border` | `#243237` | `#DCE5E5` | Bordo widget, alias di border |

Note di traduzione:

- `ds_color_widget_bg` e `ds_color_widget_border` possono essere alias (`@color/ds_color_surface`, `@color/ds_color_border`); esistono per rendere esplicito il contratto dei widget. In `values-night` vanno comunque dichiarati o ereditati correttamente, perché i widget leggono direttamente le risorse del tema di sistema.
- `ds_color_on_accent` non è bianco nel tema scuro: il testo su ottanio luminoso deve essere quasi nero per avere 9:1 e non abbagliare.
- `ds_color_data_muted` è decorativo e non va usato per testo.
- `ds_color_chart_fill` e `ds_color_bar_past` contengono già l'alpha: non applicarne un altro nel codice.

### 10.2 Tipografia

Tabella pronta per `res/values/styles.xml` come `TextAppearance.Ds.*`. I font sono risorse statiche in `res/font`.

| Stile | Font | Size | Line height | Peso | Tracking | Colore |
|---|---|---|---|---|---|---|
| `TextAppearance.Ds.Balance` | `plex_mono_medium` | 48 sp (autosize 28–48) | 54 sp | 500 | 0 | `text_primary` |
| `TextAppearance.Ds.DisplayS` | `plex_mono_medium` | 28 sp | 34 sp | 500 | 0 | `text_primary` |
| `TextAppearance.Ds.ScreenTitle` | `plex_sans_semibold` | 20 sp | 24 sp | 600 | 0 | `text_primary` |
| `TextAppearance.Ds.CardTitle` | `plex_sans_semibold` | 15 sp | 20 sp | 600 | 0 | `text_primary` |
| `TextAppearance.Ds.Body` | `plex_sans_regular` | 15 sp | 22 sp | 400 | 0 | `text_primary` |
| `TextAppearance.Ds.Label` | `plex_sans_medium` | 13 sp | 18 sp | 500 | 0 | `text_secondary` |
| `TextAppearance.Ds.Caption` | `plex_sans_regular` | 12 sp | 16 sp | 400 | 0 | `text_tertiary` |
| `TextAppearance.Ds.Overline` | `plex_sans_semibold` | 11 sp | 14 sp | 600 | 0.08 | `text_secondary` |
| `TextAppearance.Ds.MonoM` | `plex_mono_medium` | 15 sp | 20 sp | 500 | 0 | `text_primary` |
| `TextAppearance.Ds.MonoS` | `plex_mono_regular` | 12 sp | 16 sp | 400 | 0 | `text_secondary` |
| `TextAppearance.Ds.MonoXS` | `plex_mono_regular` | 10 sp | 14 sp | 400 | 0 | `text_tertiary` |
| `TextAppearance.Ds.Button` | `plex_sans_semibold` | 15 sp | 20 sp | 600 | 0 | `text_primary` (il primario lo sostituisce con `on_accent`) |
| `TextAppearance.Ds.Segment` | `plex_sans_medium` | 13 sp | 18 sp | 500 | 0 | `text_secondary` |

Note:

- `android:lineHeight` è disponibile da API 28. Con minSdk 26, per 26–27 si usa `lineSpacingExtra` calcolato, oppure si accetta il line height di default: la differenza è di 1–2 dp e non rompe il layout. Non si abbassa la minSdk per questo.
- Il saldo usa `app:autoSizeTextType="uniform"`, `min 28sp`, `max 48sp`, `maxLines=1`, `includeFontPadding=false`.
- Le TextView degli importi hanno `textDirection="ltr"` per evitare inversioni in locale RTL.
- Le overline sono maiuscole nel testo, non con `textAllCaps` globale. Il tracking è in em (`letterSpacing=0.08`).
- Non si usano pesi inferiori a 400 per testi piccoli, né corsivi.

### 10.3 Dimensioni e spaziature

Tabella pronta per `res/values/dimens.xml`.

| Risorsa | Valore | Uso |
|---|---|---|
| `ds_space_2` | 2 dp | Gap barra, gap minimi |
| `ds_space_4` | 4 dp | Gap label/valore |
| `ds_space_6` | 6 dp | Gap valore/caption |
| `ds_space_8` | 8 dp | Gap interno, gap pulsanti |
| `ds_space_10` | 10 dp | Gap saldo/barra |
| `ds_space_12` | 12 dp | Gap card, padding banner orizzontale |
| `ds_space_14` | 14 dp | Padding banner |
| `ds_space_16` | 16 dp | Padding card |
| `ds_space_20` | 20 dp | Padding schermo, padding hero |
| `ds_space_24` | 24 dp | Gap footer |
| `ds_space_32` | 32 dp | Gap sezioni, padding bottom |
| `ds_space_40` | 40 dp | Margine stati vuoti |
| `ds_toolbar_height` | 56 dp | Top bar |
| `ds_touch_target` | 48 dp | Minimo tocco |
| `ds_button_height` | 48 dp | Pulsanti |
| `ds_input_height` | 56 dp | Campo chiave |
| `ds_icon` | 24 dp | Icone standard |
| `ds_icon_small` | 18 dp | Icone dentro banner e pulsanti |
| `ds_dot` | 6 dp | Dot di stato app |
| `ds_widget_dot` | 5 dp | Dot di stato widget |
| `ds_radius_card` | 16 dp | Card |
| `ds_radius_hero` | 20 dp | Hero |
| `ds_radius_control` | 12 dp | Pulsanti, input, banner, segmenti |
| `ds_radius_bar` | 3 dp | Barra di ripartizione e barre |
| `ds_radius_widget` | 24 dp | Widget |
| `ds_border` | 1 dp | Bordi e hairline |
| `ds_chart_height` | 200 dp | Grafico a linea |
| `ds_chart_gutter_min` | 40 dp | Gutter minimo etichette Y |
| `ds_chart_gutter_max` | 64 dp | Gutter massimo etichette Y |
| `ds_bars_height` | 112 dp | Barre spesa con label |
| `ds_row_snapshot` | 48 dp | Riga rilevamento |
| `ds_row_recent` | 48 dp | Riga rilevamento in Dashboard |
| `ds_day_header` | 32 dp | Intestazione giorno |
| `ds_section_header` | 48 dp | Header sezione con azione |
| `ds_today_min_height` | 104 dp | Card Oggi |
| `ds_widget_2x2_min_width` | 110 dp | Widget 2x2 |
| `ds_widget_2x2_min_height` | 110 dp | Widget 2x2 |
| `ds_widget_4x2_min_width` | 250 dp | Widget 4x2 |
| `ds_widget_4x2_min_height` | 110 dp | Widget 4x2 |

### 10.4 Mappatura Material 3

| Ruolo M3 | Token |
|---|---|
| `colorPrimary` | `ds_color_accent` |
| `colorOnPrimary` | `ds_color_on_accent` |
| `colorPrimaryContainer` | `ds_color_accent_container` |
| `colorOnPrimaryContainer` | `ds_color_accent` |
| `colorSecondary` | `ds_color_text_secondary` |
| `colorOnSecondary` | `ds_color_bg` |
| `colorSecondaryContainer` | `ds_color_surface_high` |
| `colorOnSecondaryContainer` | `ds_color_text_primary` |
| `colorSurface` | `ds_color_surface` |
| `colorOnSurface` | `ds_color_text_primary` |
| `colorSurfaceVariant` | `ds_color_surface_high` |
| `colorOnSurfaceVariant` | `ds_color_text_secondary` |
| `colorOutline` | `ds_color_border` |
| `colorOutlineVariant` | `ds_color_chart_grid` |
| `colorError` | `ds_color_error` |
| `colorOnError` | `ds_color_on_error` |
| `colorErrorContainer` | `ds_color_error_container` |
| `colorOnErrorContainer` | `ds_color_error` |
| `android:colorBackground` | `ds_color_bg` |
| `colorOnBackground` | `ds_color_text_primary` |
| `colorSurfaceContainerLowest` | `ds_color_bg` |
| `colorSurfaceContainerLow` | `ds_color_surface` |
| `colorSurfaceContainer` | `ds_color_surface` |
| `colorSurfaceContainerHigh` | `ds_color_surface_high` |
| `colorSurfaceContainerHighest` | `ds_color_surface_high` |
| `colorControlHighlight` | `ds_color_ripple` |
| `colorAccent` (AppCompat) | `ds_color_accent` |
| `android:forceDarkAllowed` | `false` |
| `android:windowLightStatusBar` | `false` dark / `true` light |
| `android:windowLightNavigationBar` | `false` dark / `true` light |

### 10.5 Stili di componente

| Componente | Normale | Selezionato / pressed | Disabilitato |
|---|---|---|---|
| Pulsante primario | `accent` / `on_accent` | `accent` al 90%, ripple | alpha 38% |
| Pulsante outline | `surface` / `text_primary`, bordo `border` | `surface_high`, bordo `accent` | alpha 38% |
| Segmento | `surface` / `text_secondary`, bordo `border` | `accent_container` / `accent`, bordo `accent` | alpha 38% |
| Campo input | `surface` / `text_primary`, bordo `border` | focus: bordo `accent` 2 dp | errore: bordo `error` 2 dp |
| Link testuale | `accent` | ripple, sottolineatura solo al focus | alpha 38% |
| Card cliccabile | `surface`, bordo `border` | ripple `ds_color_ripple` | — |

I bordi dei segmenti selezionati sono 1 dp accento, non 2: il controllo non deve sembrare un errore. Il focus da tastiera usa un ring accento di 2 dp esterno, non un cambio di sfondo.

### 10.6 Token dei widget

| Risorsa | 2x2 | 4x2 |
|---|---|---|
| Larghezza minima | 110 dp | 250 dp |
| Altezza minima | 110 dp | 110 dp |
| Padding orizzontale | 12 dp | 14 dp |
| Padding verticale | 12 dp | 12 dp |
| Raggio | 24 dp | 24 dp |
| Label | 9 dp | 9 dp |
| Saldo | 22 dp | 30 dp |
| Oggi | 11 dp | 12 dp (valore metrica) |
| Label metriche | — | 8 dp |
| Timestamp / stato | 9 dp | 9 dp |
| Gap label/saldo | 4 dp | 2 dp |
| Gap saldo/riga | 2 dp | 4 dp |
| Gap riga metriche | — | 8 dp tra colonne |
| Dot | 5 dp | 5 dp |

I widget usano dp, non sp: è l'unica eccezione alla regola sp dell'app, motivata dalla necessità di non clippare dentro 110 dp con font scale alto.

## 11. Implementazione consigliata

### 11.1 Struttura

Una sola Activity, tre Fragment, nessun ViewPager2. Le tre schermate non sono in swipe orizzontale: il gesto confliggerebbe con il pull-to-refresh e con la lettura del grafico. La navigazione è a stack: Dashboard → Storico o Impostazioni, back alla Dashboard.

| File | Ruolo |
|---|---|
| `MainActivity.kt` (package root, come da manifest) | Host, tema, insets, transizioni |
| `ui/DashboardFragment.kt` | Hero, oggi, rilevamenti, banner |
| `ui/HistoryFragment.kt` | Filtro, grafico, barre, lista |
| `ui/SettingsFragment.kt` | Chiave, soglia, intervallo, informazioni |
| `ui/SnapshotAdapter.kt` | RecyclerView con view type per header, giorno, riga, load more |
| `view/BalanceChartView.kt` | Grafico a linea Canvas |
| `view/SpendBarsView.kt` | Barre della spesa, modalità compatta e completa |
| `res/layout/fragment_dashboard.xml` | Dashboard |
| `res/layout/fragment_history.xml` | Storico (RecyclerView + top bar) |
| `res/layout/fragment_settings.xml` | Impostazioni |
| `res/layout/item_snapshot.xml` | Riga rilevamento |
| `res/layout/item_day_header.xml` | Intestazione giorno |
| `res/layout/widget_2x2.xml` | Widget 2x2 |
| `res/layout/widget_4x2.xml` | Widget 4x2 |
| `res/values/colors.xml`, `values-night/colors.xml` | Palette |
| `res/values/dimens.xml` | Spaziature e raggi |
| `res/values/styles.xml` | TextAppearance, temi, stili widget |
| `res/font/*` | Plex Sans e Plex Mono statici |

`ViewPager2` è già tra le dipendenze ma non serve: si può rimuovere. Non aggiungere Navigation Component: tre transazioni manuali sono sufficienti e riducono il peso.

### 11.2 Mappatura Views

| Schermata | Componente | View |
|---|---|---|
| Dashboard | Contenitore scrollabile | `SwipeRefreshLayout` > `NestedScrollView` > `LinearLayout` verticale |
| Dashboard | Top bar | `MaterialToolbar`, menu con due `ActionMenuItemView` 48 dp |
| Dashboard | Hero e card | `MaterialCardView` con `cardElevation=0`, `strokeWidth=1dp`, `cardBackgroundColor=surface` |
| Dashboard | Saldo | `TextView` con autosize, `maxLines=1`, `includeFontPadding=false` |
| Dashboard | Barra ripartizione | `View` con `GradientDrawable` o `LinearLayout` a due pesi; meglio un `View` custom leggero per il minimo 3 dp |
| Dashboard | Mini barre 7 giorni | `SpendBarsView` in modalità compatta, 60x40 dp |
| Dashboard | Rilevamenti | 3 righe statiche in un `LinearLayout` (non una RecyclerView annidata) |
| Storico | Lista | `RecyclerView` con `LinearLayoutManager`; filtro, grafico, barre e header sono item |
| Storico | Filtro | `MaterialButtonToggleGroup` con due `MaterialButton`, `singleSelection=true`, `selectionRequired=true` |
| Storico | Grafico | `BalanceChartView` 200 dp |
| Storico | Barre | `SpendBarsView` 112 dp |
| Storico | Riga | `item_snapshot.xml`, 48 dp |
| Impostazioni | Contenitore | `ScrollView` con `adjustResize` |
| Impostazioni | Chiave | `TextInputLayout` outlined + `TextInputEditText`, end icon occhio |
| Impostazioni | Soglia | `Slider` Material con `valueFrom=0`, `valueTo=100`, `stepSize=1` |
| Impostazioni | Intervallo | `MaterialButtonToggleGroup` con tre pulsanti |
| Impostazioni | Pulsanti | `MaterialButton` 48 dp, `cornerRadius=12dp`, `textAllCaps=false` |
| Impostazioni | Notifiche | Pulsante testuale che apre le impostazioni di sistema |

Componenti da usare: `MaterialToolbar`, `MaterialCardView`, `MaterialButton`, `MaterialButtonToggleGroup`, `TextInputLayout`, `Slider`, `SwipeRefreshLayout`, `RecyclerView`, `NestedScrollView`, `CircularProgressIndicator` (16 dp) o `ProgressBar` tinto accento per i test, `Snackbar` solo per la copia del saldo.

Da evitare, con motivo:

| Scelta | Perché no |
|---|---|
| Jetpack Compose | Vincolo di prodotto: Views XML |
| MPAndroidChart o simili | Peso e stile fuori controllo; il grafico è semplice e va disegnato |
| `BottomNavigationView` | Due destinazioni secondarie; la top bar basta |
| FAB | Non c'è un'azione primaria di creazione |
| `Snackbar` per errori persistenti | Deve restare visibile; si usa un banner |
| `Toast` per errori | Non è accessibile e sparisce; solo per la copia |
| ViewPager2 per le tre schermate | Conflitto con pull-to-refresh e grafico |
| `RecyclerView` dentro `NestedScrollView` | Scroll annidato, altezza instabile, performance |
| Auto dark / dynamic color | Rompe la palette e i contrasti |
| Contatori animati | Rende ambiguo il valore |
| Ellissi sul saldo | Un saldo troncato è un dato sbagliato |
| `ConstraintLayout` nei widget | Non è una view supportata ufficialmente da RemoteViews |
| Custom View nei widget | RemoteViews non le istanzia |
| ProgressBar nei widget | Vincolo del prodotto e rischio di animazioni non fluide |
| Downloadable fonts nei widget | Il launcher può non risolverle |

### 11.3 Grafico a linea: `BalanceChartView`

Il grafico è un `View` custom, non una libreria. Contenitore 200 dp, padding interno: top 12 dp, bottom 22 dp, right 6 dp; il gutter sinistro è dinamico tra 40 e 64 dp in base alla larghezza delle etichette Y.

**Dati.** Una serie di punti `(giorno, saldo)`: l'ultimo rilevamento di ogni giorno nel periodo, più il valore attuale se oggi non è già presente. Il grafico non usa tutti i rilevamenti intraday: sarebbe rumoroso e il dettaglio è nella lista. I giorni senza rilevamenti non diventano punti a zero; la linea collega i giorni disponibili con spaziatura uniforme e la caption `Dati dal …` chiarisce la copertura. La valuta e il locale sono quelli dell'account.

**Assi e scala.**

- Y: min e max della serie. Se la serie è piatta, si apre un intervallo di `max(0.5, 5% del valore)` per evitare una linea schiacciata. Non si forza lo zero: il grafico del credito perde risoluzione e le etichette dichiarano i valori reali.
- Tick: 4 linee orizzontali, step "nice" (1, 2, 5 per potenza di 10). Min e max arrotondati allo step. Etichette formattate con `moneyAxis`: 2 decimali sotto 100, intero sotto 10.000, `k` oltre. Gutter = larghezza massima dell'etichetta + 8 dp, clamp 40–64.
- X: una posizione per punto, spaziatura uniforme. Etichette a primo, centro, ultimo; se la larghezza del plot è sotto 220 dp, solo primo e ultimo. Prima di disegnare, misurare le etichette e saltare quella centrale se si sovrappone.
- Griglia: solo orizzontale, 1 dp `chart_grid`, alle 4 linee. Nessuna griglia verticale: pulizia e meno rumore.
- Etichette: Mono XS 10 sp, `text_tertiary`; Y allineate a destra nel gutter, con baseline centrata sulla linea; X centrate sul punto, con clamp ai bordi del plot.

**Disegno, nell'ordine.**

1. Calcolare il rettangolo del plot e le posizioni.
2. Griglia orizzontale e etichette Y.
3. Etichette X.
4. Area: Path chiuso sulla baseline, gradiente verticale da `chart_fill` a trasparente. Nessun gradiente orizzontale.
5. Linea: 2 dp `accent`, `strokeJoin=ROUND`, `strokeCap=ROUND`, segmenti rettilinei. Niente Bezier: la curva inventerebbe valori tra i punti.
6. Ultimo punto: cerchio 3.5 dp `accent` con anello 1.5 dp `surface` per staccarlo dalla linea.
7. Nessun punto sugli altri nodi: 7 o 30 punti diventrebbero rumore.

**Casi limite.**

- Serie vuota: il View non disegna assi né griglia. Il Fragment mostra al centro `Nessun rilevamento nel periodo.` con caption sotto. L'altezza resta 200 dp per non far saltare il layout.
- Un solo punto: nessuna linea. Si disegna una guida orizzontale tratteggiata (4/4) al valore, un punto a destra del plot e nessuna etichetta X multipla. Caption sotto: `Serve un secondo rilevamento per la linea.`
- Due punti: linea retta; l'area si disegna comunque, è coerente.
- Valori uguali: intervallo artificiale, linea centrale, etichette con lo stesso valore arrotondato; accettabile e onesto.
- Larghezza 0 o altezza 0: `onDraw` esce senza disegnare; nessuna divisione per zero.
- Valori negativi: non attesi dal saldo; il calcolo li gestisce e le etichette mostrano il meno. Non si nasconde il dato.
- Cambio tema: leggere i colori da attributi del tema a ogni `onSizeChanged`/`onAttachedToWindow` o usare `ColorStateList`; invalidare.
- Font scale: le etichette usano `scaledDensity` con cap a 1.3; oltre, il gutter si allarga fino a 64 e le etichette centrali vengono saltate. La descrizione accessibile resta completa.

**Animazione.** Al primo bind, rivelare la linea con `PathMeasure` in 320 ms, ease-out. L'area appare in fade insieme. Se `Settings.Global.ANIMATOR_DURATION_SCALE` è 0 o la serie ha meno di 2 punti, disegnare subito. Cancellare l'animator in `onDetachedFromWindow`. Non animare a ogni refresh: il grafico è un riferimento, non un gioco.

**Prestazioni.** Niente allocazioni in `onDraw`: Path, Paint, Rect e label precalcolate in `onSizeChanged` o al set dei dati. `setLayerType` non serve. `contentDescription` aggiornata al set dei dati. Nessun `onTouchEvent`: il grafico non è interattivo.

**Attributi.** Definire attrs `dsLineColor`, `dsGridColor`, `dsLabelColor`, `dsFillColor`, `dsPointColor` con default dai token, così il tema chiaro/scuro funziona senza codice condizionale.

### 11.4 Barre della spesa: `SpendBarsView`

- Altezza 112 dp: 16 dp in alto per la label del massimo (solo 7 giorni), 80 dp di plot, 16 dp per le date.
- 7 giorni: gap 8 dp, larghezza = (plot - 6*gap)/7, massimo 38 dp. Label sopra la barra più alta: `$2,60`, Mono XS 10 sp.
- 30 giorni: gap 3 dp, larghezza = (plot - 29*3)/30, minimo 3 dp. Nessuna label sopra le barre; caption `max $2,60 · 17 ott` sotto, 11 sp.
- Barra di oggi: `accent` pieno. Barre passate: `bar_past`. Giorno a zero: stub di 2 dp `border`.
- Baseline: 1 dp `chart_grid` a fondo plot.
- Angoli superiori 3 dp, inferiori 0.
- Date: 7 giorni tutte le date (`15 … 21`), 30 giorni primo/centrale/ultimo (`15 ott`, `18 ott`, `21 ott`), Mono XS 9 sp, tertiary.
- Nessun tap, nessun tooltip. `contentDescription` con totale, massimo e giorno del massimo.
- Stessa view in Dashboard in modalità compatta: 60x40 dp, 7 barre, nessuna label, nessuna baseline, nessun gap extra.

### 11.5 Widget e RemoteViews

**Vincoli.** Solo `LinearLayout`, `TextView`, `ImageView`. Niente `FrameLayout`, `ConstraintLayout`, custom view, `ProgressBar`, animazioni, font scaricabili. Le dimensioni sono fisse in dp; i testi non crescono con il font scale. Il layout viene inflazionato dal launcher: ogni risorsa deve essere nel pacchetto.

**Layout 2x2.**

- Root `LinearLayout` verticale, padding 12, `background` shape con solid `widget_bg`, stroke 1 dp `widget_border`, corners 24.
- Riga 1: `LinearLayout` orizzontale, 12 dp. `TextView` `SALDO` 9 dp + spacer con peso 1 + `ImageView` dot 5 dp. Il dot è `GONE` nello stato normale.
- Spacer 4 dp.
- `TextView` saldo 22 dp, maxLines 1, `ellipsize=none`, Mono 500.
- Spacer 2 dp.
- `TextView` oggi 11 dp, maxLines 1.
- Spacer 2 dp.
- `TextView` stato 9 dp, maxLines 1, `ellipsize=end`, `GONE` se normale.
- Altezza totale: 12 + 4 + 30 + 2 + 14 + 2 + 12 = 76, dentro 86 di contenuto. Il resto è spazio in basso.

**Layout 4x2.**

- Root `LinearLayout` verticale, padding 14/12, stesso background.
- Riga 1: 12 dp, `SALDO` + spacer + timestamp/stato a destra, Mono 9 dp.
- Spacer 2 dp.
- Saldo 30 dp, altezza 38, maxLines 1.
- Spacer 4 dp.
- Riga metriche: `LinearLayout` orizzontale, 28 dp. Tre colonne con `layout_weight=1`, gap 8. Ogni colonna: label 8 dp + valore 12 dp, due righe.
- Totale: 12 + 2 + 38 + 4 + 28 = 84, dentro 86. Il padding verticale 12 porta l'esterno a 108; i 2 dp restanti sono margine.

**ID.** Mantenere quelli già usati da `WidgetUpdater`: `widget_root`, `widget_total`, `widget_granted`, `widget_topped`, `widget_today`, `widget_updated`, `widget_status`. Aggiungere `widget_dot` per il dot di stato. Se non si aggiunge, il testo di stato è sufficiente: nessuno stato è comunicato solo dal colore.

**Aggiornamento.**

- `updatePeriodMillis=0`; il refresh è guidato da WorkManager con l'intervallo scelto e dal tap.
- Il tap sul root usa un `PendingIntent` contestuale: refresh nello stato normale/offline/soglia; apertura dell'app in Impostazioni per chiave mancante o non valida. `WidgetRefreshReceiver` già esiste; il provider deve scegliere il `PendingIntent` in base allo stato.
- Prima di accodare il lavoro, aggiornare subito il widget con `aggiorno…` e dot accento. A lavoro finito, aggiornare i valori. Timeout 20 s: il worker scrive l'errore, il widget mostra offline con i valori cache.
- WorkManager con nome unico: tap ripetuti non accodano duplicati.
- `WidgetUpdater.updateAll` va chiamato anche dopo un refresh riuscito dall'app, così app e widget non divergono.

**Formattazione.** Servono quattro funzioni separate, non una sola: `moneyApp`, `moneyWidget2x2`, `moneyWidget4x2`, `moneyWidgetMetric`. I budget sono in 6.3 e 7.3. `Format.money` attuale produce `$21.40` con il punto decimale: va adeguato al contratto italiano prima di usarlo nei widget. La stringa `$—` non va composta a mano: usare una stringa dedicata `widget_no_data`.

**Font e colori.** Font Plex statici in `res/font`; colori via `values-night`. Lo sfondo è opaco, non translucido: deve restare leggibile su qualsiasi wallpaper. Il bordo 1 dp è parte del contrasto nel tema chiaro. Il launcher può applicare un proprio raggio o padding: testare Pixel Launcher e One UI, senza compensare con padding negativi.

**Preview.** `previewLayout` con dati di esempio reali (`$21,40`, `oggi $0,42`), non `—`. La preview non deve sembrare un widget rotto nel picker.

**Aggiornamento configurazione.** `widget_2x2_info` e `widget_4x2_info`: `minWidth`/`minHeight` come in 6.1 e 7.1, `targetCellWidth`/`targetCellHeight` su API 31+, `resizeMode="horizontal"` per il 4x2, `widgetCategory="home_screen"`, `description` breve. Il 2x2 non è ridimensionabile.

### 11.6 Tema, inset e font

- `Theme.DeepSeekWidget` con parent `Theme.Material3.DayNight.NoActionBar`; override dei ruoli M3 come in 10.4.
- `android:forceDarkAllowed=false` in entrambi i valori. `values-night` per i colori; nessun `values-night` per dimens o layout, perché nulla cambia.
- Edge-to-edge: `WindowCompat.setDecorFitsSystemWindows(window, false)`. Top bar con padding top = status bar + display cutout. Contenuto scrollabile con padding bottom = navigation bar + 32 dp. In Impostazioni applicare anche l'inset IME per non coprire i pulsanti.
- Status bar trasparente; icone chiare/scure in base al tema. Navigation bar `bg`, icone coerenti. Su API 29+ disabilitare i contrasti forzati di sistema se interferiscono.
- Font: cinque file statici, licenza OFL. Non usare `google_fonts` o downloadable fonts, per i widget e per evitare dipendenze di rete.
- `android:lineHeight` solo da API 28; per 26–27 usare spacing equivalente. Non alzare minSdk.
- Splash: sfondo `bg`, icona app, nessuna animazione. Non aggiungere un logo testuale.

### 11.7 Mappatura errori e dati

| Origine | Stato UI | Copy |
|---|---|---|
| `ApiResult.MissingKey` / `Prefs.ERROR_MISSING_KEY` | Dashboard vuota; widget chiave mancante | `Nessuna chiave API` / `aggiungi chiave` |
| HTTP 401/403 / `Prefs.ERROR_AUTH` | Dashboard errore pieno; widget valori nascosti | `Chiave non valida` / `chiave non valida` |
| `IOException`, timeout / `Prefs.ERROR_NETWORK` | Banner offline con cache; stato pieno senza cache | `Offline. Dati delle …` / `Impossibile aggiornare` |
| HTTP 429 | Banner warning con cache | `Troppe richieste. Dati delle …` |
| HTTP 5xx | Banner warning con cache | `DeepSeek non risponde. Dati delle …` |
| `ApiResult.Unexpected` / `Prefs.ERROR_UNEXPECTED` | Banner errore con cache | `Risposta non leggibile. Riprova.` |
| `Balance.isAvailable=false` | Banner warning, saldo `—` | `Saldo non disponibile` |
| Nessun errore, dati vecchi | Dot warning, meta invariata | Nessun banner; il tempo parla |

`Format.money` va allineato a questi vettori:

| Input | App | 2x2 | 4x2 saldo | 4x2 metrica |
|---|---|---|---|---|
| `21.40` | `$21,40` | `$21,40` | `$21,40` | `$21,40` |
| `0.42` | `$0,42` | `$0,42` | `$0,42` | `$0,42` |
| `99.99` | `$99,99` | `$99,99` | `$99,99` | `$99,99` |
| `100.00` | `$100,00` | `$100` | `$100,00` | `$100,00` |
| `999.99` | `$999,99` | `$999` | `$999,99` | `$999,99` |
| `1234.50` | `$1.234,50` | `$1.234` | `$1.234,50` | `$1.234` |
| `12345.67` | `$12.345,67` | `$12,3k` | `$12.345,67` | `$12,3k` |
| `123456.78` | `$123.456,78` | `$123k` | `$123.456,78` | `$123k` |
| `1234567.89` | `$1.234.567,89` | `$1,2M` | `$1,2M` | `$1,2M` |
| `0` | `$0,00` | `$0,00` | `$0,00` | `$0,00` |
| negativo | `−$0,42` | `−$0,42` | `−$0,42` | `−$0,42` |

La spesa usa `SpendCalculator` senza modifiche: somma i cali di saldo tra rilevamenti consecutivi dello stesso giorno, ignora le ricariche, restituisce 0 con meno di 2 rilevamenti. Il design non cambia il modello: lo rende visibile con la caption della stima e con `prima stima di oggi`.

## 12. Checklist di verifica

Prima di considerare la UI finita:

- [ ] 320 dp di larghezza, font scale 1.0 e 1.3: il saldo non è mai troncato, le label non si sovrappongono, i pulsanti restano 48 dp.
- [ ] Dark e light: tutti i contrasti della tabella 2.3 rispettati; nessun testo tertiary su surfaceHigh sotto 4.5:1.
- [ ] Primo avvio senza chiave: stato vuoto, CTA, pull disabilitato.
- [ ] Loading iniziale: skeleton senza salto di layout; nessun testo finto.
- [ ] Offline con cache, offline senza cache, 401, 429, 5xx, risposta malformata: copy e trattamento coerenti con 3.3.
- [ ] Sotto soglia: banner, dot, notifica una sola volta per attraversamento.
- [ ] Saldo non disponibile: `—` e banner, nessun numero inventato.
- [ ] Storico: 7/30, dati parziali, vuoto, un solo punto, caricamento altri.
- [ ] Widget 2x2 e 4x2: 110/250 dp, valori lunghi, refresh, offline, chiave non valida, tema chiaro/scuro, launcher con padding.
- [ ] Widget: nessun testo tagliato, nessuna ellissi sul saldo, stato leggibile senza colore.
- [ ] Tap: tutte le aree interattive ≥48 dp, inclusi i link testuali; i dot non sono interattivi.
- [ ] TalkBack: saldo, grafico, barre, widget e stati hanno descrizioni complete.
- [ ] Notifiche: canale, copy, permesso richiesto solo su azione, nessuna notifica per errori di rete.
- [ ] Nessun colore dinamico, nessun auto dark, nessuna ombra pesante, nessuna emoji.
- [ ] `Format.money` produce i vettori di 11.7; il widget non usa il formato dell'app.
- [ ] Il tap del widget apre Impostazioni solo per chiave mancante/non valida; altrimenti aggiorna.
- [ ] `aggiorno…` non resta appeso oltre 20 s.

## 13. Multi-provider (DeepSeek + OpenAI)

> Sezione di estensione per la v2 multi-provider, aggiunta in coda alla spec.

> **Nota di implementazione (rettifica).** L'API Costs di OpenAI accetta **solo**
> `bucket_width=1d` (con `1h` risponde 400): non esistono bucket orari. Tutte le
> metriche OpenAI — "Speso oggi", "7 giorni", mese corrente e grafico — usano quindi
> giorni **UTC**, come descritto dalla spec. Il giorno UTC corrente è etichettato
> `SPESO OGGI` e la meta `Oggi in corso · costi fino alle HH:MM UTC` finché il bucket
> non è chiuso. La conversione nel giorno locale resta solo per DeepSeek.

### 13.1 Modello di prodotto

L'app può mostrare un provider alla volta nella Dashboard e nello Storico. Non esiste una somma tra provider: i dati DeepSeek e OpenAI non vengono mai mescolati, confrontati o convertiti. Il provider attivo è una scelta locale, persistita sul dispositivo.

| Provider | Cosa espone | Numero dominante | Fonte | Vincolo |
|---|---|---|---|---|
| DeepSeek | Saldo residuo, omaggio, ricariche, spesa stimata localmente | Saldo totale | `/user/balance` + `SnapshotDb` locale | Nessun cambio rispetto alla v1 |
| OpenAI | Solo costi giornalieri dell'organizzazione | Speso oggi | Costi per organizzazione | Richiede una Admin key; nessun saldo disponibile |
| Claude | Fuori perimetro v2 | — | — | Nessuna UI, nessuna chiave, nessun widget |

Regole:

- Il selettore è l'unico punto di cambio provider nella Dashboard e nello Storico. Il widget non ha selettore e resta DeepSeek-only.
- Non si usa la parola "saldo" per OpenAI. Le etichette sono "Speso", "Costi", "Spesa". "Saldo" resta un termine esclusivo DeepSeek.
- OpenAI non espone un credito residuo: l'interfaccia non mostra mai un numero di saldo, una barra omaggio/ricariche o un saldo stimato.
- I costi OpenAI sono bucket giornalieri in UTC. L'app non li converte in giorni locali: una conversione sarebbe una stima inventata. La UI dichiara "UTC" dove serve.
- Il titolo della Dashboard diventa `Credito`, senza nome provider: il contesto è nel selettore. Il nome dell'app resta invariato per non cambiare identità e widget.
- Il provider attivo è indipendente dal tab scelto in Impostazioni. In Impostazioni si modifica un provider; non si cambia quello attivo.
- La soglia di avviso e la notifica restano concetti DeepSeek. OpenAI non ha una soglia di saldo e non produce notifiche di soglia.
- Non c'è conversione valuta. DeepSeek usa l'entry USD preferita dal client; OpenAI restituisce USD. Il simbolo resta `$`.

### 13.2 Selettore provider

Pattern: segmented control a piena larghezza, con gli stessi token del filtro 7/30 e dell'intervallo. Due segmenti oggi, tre quando arriverà un terzo provider. Nessuna icona, nessun logo provider: l'accento resta l'ottanio dell'app, non il brand OpenAI.

```
[  DeepSeek  ][  OpenAI  ]
```

Posizione e geometria:

| Contesto | Posizione | Margini | Dimensioni |
|---|---|---|---|
| Dashboard | Primo elemento del contenuto, sotto la top bar | Top 8 dp, orizzontale 20 dp, 12 dp al hero | 48 dp di altezza, piena larghezza |
| Storico | Primo elemento del contenuto, sopra la strip di stato e il filtro | Top 8 dp, orizzontale 20 dp, 12 dp al filtro | 48 dp di altezza, piena larghezza |
| Impostazioni | Primo elemento del contenuto, sotto la top bar | Top 8 dp, orizzontale 20 dp, 20 dp alla prima sezione | 48 dp di altezza, piena larghezza |

- Due segmenti: gap 8 dp, ciascuno `(320 - 8) / 2 = 156 dp` a 360 dp di schermo.
- Tre segmenti: gap 8 dp, ciascuno `(320 - 16) / 3 = 101,3 dp`. tre etichette fino a 8 caratteri entrano a 13 sp con padding 8 dp.
- Quattro o più provider: non previsti in v2. Se arriveranno, il selettore diventa una riga orizzontale scrollabile con snap e indicatore di overflow; non si comprimono le etichette sotto 13 sp.
- Raggio 12 dp, bordo 1 dp, testo 13 sp/18, peso 500, `letterSpacing=0`, altezza 48 dp. Tap target 48 dp per segmento.
- Il selettore scorre con il contenuto, non è sticky. La top bar resta fissa e non ospita il selettore.
- Il selettore non ha un'ombra propria e non usa un container esterno: i due/tre segmenti sono affiancati sul background.

Stati:

| Stato | Sfondo | Testo | Bordo | Dot | Comportamento |
|---|---|---|---|---|---|
| Attivo | `accent_container` | `accent` | 1 dp `accent` | nessuno | Provider corrente; tap non fa nulla |
| Inattivo, configurato | `surface` | `text_secondary` | 1 dp `border` | nessuno | Tap seleziona il provider |
| Disabilitato, senza key | `surface` | `text_tertiary` | 1 dp `border` | 4 dp `warning` | Non diventa attivo; tap apre le Impostazioni di quel provider |
| Non disponibile, futuro | `surface_high` | `text_tertiary` | 1 dp `border` | nessuno | Disabilitato e inerte; non usato in v2 |

Lo stato "disabilitato, senza key" è volutamente ambiguo solo in apparenza: non è selezionabile come provider attivo, ma resta un target da 48 dp che porta alla configurazione. La content description lo dice: `OpenAI, non configurato. Apri le impostazioni OpenAI.` Non è un controllo inerte.

Comportamento:

| Evento | Comportamento |
|---|---|
| Primo avvio, nessun provider configurato | Nessun provider attivo. Entrambi i segmenti sono nello stato "senza key". Dashboard con stato neutro e due CTA: `Configura DeepSeek` e `Configura OpenAI` |
| Un solo provider configurato | Quel provider è attivo all'avvio. L'altro segmento resta "senza key" e porta alle impostazioni |
| Entrambi configurati | Si ripristina l'ultimo provider attivo. Al primo avvio con entrambi, il default è DeepSeek |
| Tap su provider configurato | Cambio immediato: crossfade 120 ms del contenuto, scroll in cima, dati in cache mostrati subito; refresh se i dati sono più vecchi di 2 intervalli o assenti |
| Tap su provider senza key | Apre Impostazioni sul tab di quel provider. Il provider attivo non cambia |
| Key del provider attivo rimossa | Fallback all'altro provider configurato, con Snackbar `Provider attivo: OpenAI` o `Provider attivo: DeepSeek` per 2 s. Se nessuno è configurato, stato neutro |
| Provider futuro non disponibile | Segmento inerte, nessuna navigazione, nessuna chiave richiesta |
| Cambio provider nello Storico | Ricarica i dati del provider, mantiene 7/30, scroll in cima |
| Cambio provider in Impostazioni | Cambia solo il pannello di configurazione visibile; non cambia il provider attivo |

La selezione attiva è persistita. La selezione del tab in Impostazioni non è persistita come provider attivo: al rientro in Impostazioni si apre sul provider attivo.

Content description:

- Attivo: `Provider DeepSeek, attivo.`
- Configurato inattivo: `Provider OpenAI. Attiva per passare a OpenAI.`
- Senza key: `Provider OpenAI, non configurato. Apri le impostazioni OpenAI.`

### 13.3 Dashboard OpenAI — variante "solo costi"

La Dashboard OpenAI non ha un hero di saldo. Il numero dominante è il costo del giorno. Non ci sono barra di ripartizione, legenda omaggio/ricariche, saldo, soglia o banner di soglia.

```
[  DeepSeek  ][  OpenAI  ]

╭──────────────────────────────────────╮
│ SPESO OGGI                           │
│                                      │
│ $12,84                               │  48 sp
│ [orologio] Costi fino alle 09:00 UTC │
│ OpenAI pubblica i costi con qualche  │
│ ora di ritardo.                      │
│ ──────────────────────────────────── │
│ 7 GIORNI            MESE CORRENTE    │
│ $84,20              $312,40          │  20 sp
│ Dati parziali · 5 giorni su 7        │
╰──────────────────────────────────────╯

╭ Spesa giornaliera          14 giorni ╮
│  ▂  ▅  ▁  █  ▃  ▂  ▄  ▂  ▃  ▁  ▅  ▂ │
│  8 ott            15 ott        21 ott│
│ Giorni UTC. Le barre mancanti non    │
│ sono giorni a zero.                  │
╰──────────────────────────────────────╯

Dati OpenAI · aggiornamento ogni 60 min
```

Anatomia:

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Selettore provider | 48 dp, gap 8, raggio 12 | Segment 13 sp | Vedi 8.2 |
| 2 | Hero costi | Raggio 20, padding 20, bordo 1 dp, margine top 12 | — | `surface` |
| 3 | Label giorno | Altezza 14, overline | Overline 11 sp | `text_secondary` |
| 4 | Speso | Altezza 54, autosize 28–48, maxLines 1 | Balance 48 sp, Mono 500 | `text_primary` |
| 5 | Meta dati | 20 dp, icona orologio 14 dp, gap 6 | Caption 12 sp | `text_tertiary`; warning se vecchio |
| 6 | Nota ritardo | 2 righe max, margine top 4 | Caption 11 sp | `text_tertiary` |
| 7 | Divisore | 1 dp, margine top 16 | — | `border` |
| 8 | Metriche secondarie | Riga 44, due colonne, gap 16, divisore verticale 1x36 | Overline + `MetricM` 20 sp | Label `text_secondary`; valore `text_primary` |
| 9 | Caption parzialità | 11 sp, margine top 4 | Caption 11 sp | `text_tertiary` |
| 10 | Card barre | Raggio 16, padding 16, margine top 12 | — | `surface` |
| 11 | Header barre | Riga 24, titolo + `14 giorni` | CardTitle + Caption | `text_primary` / `text_tertiary` |
| 12 | Barre | 112 dp, margine top 12 | Mono XS 9–10 sp | `accent` oggi, `bar_past` altri |
| 13 | Caption barre | 2 righe max, margine top 8 | Caption 11 sp | `text_tertiary` |
| 14 | Footer | 16 dp, margine top 24 | Caption 11 sp | `text_tertiary` |

Regole del numero dominante:

- Il label è `SPESO OGGI` solo quando esiste il bucket del giorno UTC corrente. Se il bucket di oggi non è ancora disponibile, il label diventa `SPESO IL 20 OTT` e il numero è l'ultimo bucket disponibile. Non si mostra un falso "oggi".
- Se il bucket di oggi è presente ma parziale, il label resta `SPESO OGGI` e la meta dice `Oggi in corso · costi fino alle 09:00 UTC`. Il numero è dominante ma non viene presentato come definitivo.
- La meta usa sempre `UTC` per l'ora dei costi. Non si converte in ora locale.
- La nota `OpenAI pubblica i costi con qualche ora di ritardo.` è sempre visibile, 11 sp, tertiary. Spiega il ritardo una volta, senza banner e senza colore di allarme.
- "7 giorni" e "mese corrente" sommano solo i bucket disponibili. Se mancano giorni, la caption dice `Dati parziali · 5 giorni su 7` oppure `Dati parziali · dal 15 ott`. Non si stima il missing.
- La media non compare nella Dashboard OpenAI: c'è solo nello Storico, dove è più utile. Non si aggiungono metriche non richieste.

Grafico a barre della Dashboard:

- 14 giorni UTC, dal più vecchio a oggi. Altezza 112 dp, stesso `DailySpendChartView` della v1.
- Barra di oggi in `accent`; giorni passati in `bar_past`; zero esplicito con stub 2 dp `border`.
- Un giorno senza bucket non è zero: non si disegna la barra. La caption lo dice: `Giorni UTC. Le barre mancanti non sono giorni a zero.`
- Etichette X: primo, centrale, ultimo (`8 ott`, `15 ott`, `21 ott`), Mono XS 9 sp, tertiary. Nessuna label sopra le barre: a 14 giorni sarebbe rumore.
- Tap sulla card: nessuna azione. L'accesso allo Storico resta l'icona in top bar.
- Il selettore resta sopra il hero; se compare un banner di dati vecchi, sta tra hero e card barre, con margine 12 dp.

Ritardo dati, senza allarme:

| Età dell'ultimo bucket | Trattamento | Copy |
|---|---|---|
| Fino a 12 h | Normale, nessun dot | `Costi fino alle 09:00 UTC` |
| 12–36 h | Dot warning, meta in warning, nessun banner | `Costi non recenti · ultimo dato 20 ott, 09:00 UTC` |
| Oltre 36 h | Banner warning, dot warning, azione `Riprova` | `Costi non recenti` / `Ultimo dato: 20 ott, 09:00 UTC.` |
| Bucket di oggi parziale | Normale, nessun warning | `Oggi in corso · costi fino alle 09:00 UTC` |
| Ultimo bucket non oggi, età ≤12 h | Normale | `SPESO IL 20 OTT` + `Costi fino alle 23:59 UTC` |
| Nessun bucket | Stato "Nessun costo disponibile", non `$0,00` | Vedi 8.4 |

La soglia di 36 h è deliberata: OpenAI può pubblicare i costi con molte ore di ritardo, e un banner a ogni ritardo normale sarebbe rumore. Il banner compare solo quando il dato non è più utilizzabile per decisioni quotidiane.

La Dashboard OpenAI non mostra:

- saldo, saldo stimato, credito residuo;
- barra omaggio/ricariche;
- soglia o notifica;
- confronto con DeepSeek;
- stima di costi futuri o proiezioni;
- conversione UTC → locale.

### 13.4 Stati OpenAI

| Stato | Dashboard | Storico | Selettore / widget | Testo esatto |
|---|---|---|---|---|
| Admin key mancante, provider attivo | Stato pieno, nessun dato finto | Lista vuota con CTA | OpenAI "senza key"; widget DeepSeek invariato | `OpenAI non configurato` / `Aggiungi una Admin key per leggere i costi dell'organizzazione.` / `Aggiungi Admin key` / hint `Serve una Admin key, non una key utente.` |
| Admin key presente, 403 permessi | Stato pieno se non c'è cache; banner errore se c'è cache | Strip errore sopra il riepilogo, dati locali visibili | OpenAI configurato | `Permessi insufficienti` / `OpenAI ha rifiutato la chiave. Per i costi serve una Admin key dell'organizzazione, non una key utente.` / `Apri impostazioni OpenAI` / `Riprova` |
| Admin key non valida, 401 | Stato pieno | Strip errore, dati locali visibili | OpenAI configurato | `Admin key non valida` / `La Admin key salvata non è valida. Aggiornala nelle impostazioni.` / `Apri impostazioni OpenAI` |
| Nessun dato ancora | Stato pieno con icona costi | Empty state | OpenAI configurato | `Nessun costo disponibile` / `OpenAI non ha ancora restituito costi per questa organizzazione. I dati compaiono al primo aggiornamento.` / `Riprova` |
| Nessun costo nel periodo | Non applicabile | Empty state | OpenAI configurato | `Nessun costo nel periodo.` / `OpenAI non ha restituito costi per i giorni selezionati.` / `Riprova` |
| Dati presenti ma vecchi | Dati visibili, banner warning oltre 36 h | Strip warning, dati visibili | OpenAI configurato | `Costi non recenti` / `Ultimo dato: 20 ott, 09:00 UTC.` / `Riprova` |
| Offline con cache | Dati visibili, banner warning | Strip warning, dati visibili | OpenAI configurato | `Offline. Costi fino al 20 ott, 09:00 UTC.` / `Riprova` |
| Offline senza cache | Stato pieno | Empty state | OpenAI configurato | `Impossibile aggiornare` / `Controlla la connessione. I costi compaiono al primo aggiornamento riuscito.` / `Riprova` |
| OpenAI non risponde (5xx) | Banner warning con cache; stato pieno senza | Strip warning | OpenAI configurato | `OpenAI non risponde` / `Costi fino al 20 ott, 09:00 UTC.` / `Riprova` |
| Troppe richieste (429) | Banner warning con cache | Strip warning | OpenAI configurato | `Troppe richieste. Costi fino al 20 ott, 09:00 UTC.` / `Riprova` |
| Caricamento iniziale | Skeleton hero + barre | Skeleton riepilogo + barre + 3 righe | OpenAI configurato | Nessun testo visibile; content description `Caricamento dei costi OpenAI` |
| Cambio provider | Crossfade 120 ms; cache immediata | Crossfade, scroll in cima | Selettore aggiornato | `Provider attivo: OpenAI` solo nel fallback automatico |
| Nessun provider configurato | Stato neutro con due CTA | Empty state neutro | Nessun segmento attivo | `Nessun provider configurato` / `Aggiungi una chiave DeepSeek o una Admin key OpenAI per iniziare.` / `Configura DeepSeek` / `Configura OpenAI` |

Priorità dei messaggi: permessi/autenticazione > rete > dati vecchi > parzialità. Un solo banner alla volta. Il banner non copre mai il selettore provider.

Lo stato "admin key mancante" con provider attivo è transitorio: si verifica se la chiave viene rimossa mentre OpenAI è attivo o se il dato persistito è incoerente. In condizioni normali un provider senza key non è attivabile e si passa dalle impostazioni.

La 403 non viene trattata come "chiave scaduta": è un problema di ruolo. Il testo non dice "riautenticati", dice che serve una Admin key dell'organizzazione. La 401 invece è una chiave non valida.

Se esiste cache e arriva 403, i dati restano visibili ma la strip dice `Permessi insufficienti. I costi locali restano disponibili.` e l'azione apre le impostazioni. Se non esiste cache, si usa lo stato pieno. La stessa regola vale per 401: con cache si mostra la strip, senza cache lo stato pieno.

### 13.5 Storico per provider

Lo Storico condivide selettore provider, filtro 7/30 e strip di stato. Cambia il corpo:

| Provider | Corpo dello Storico |
|---|---|
| DeepSeek | Invariato: card saldo con linea, min/max, card spesa con barre, lista snapshot con orari |
| OpenAI | Nessuna linea di saldo: riepilogo spesa, barre giornaliere, lista costi per giorno |
| Claude | Non previsto |

Il filtro 7/30 resta nello stesso punto e mantiene la selezione quando si cambia provider. Lo scroll torna in cima al cambio provider. Il selettore provider resta sopra la strip di stato; con 3 provider occupa 101 dp per segmento, come in 8.2.

#### 13.5.1 Struttura OpenAI

```
[  DeepSeek  ][  OpenAI  ]

[ 7 giorni ]  [ 30 giorni ]

╭──────────────────────────────────────╮
│ SPESA NEL PERIODO   MEDIA GIORNALIERA│
│ $84,20              $12,03           │
│ Dati parziali · 24 giorni su 30      │
│ Giorni UTC · media sui giorni con dati│
╰──────────────────────────────────────╯

╭ Spesa giornaliera             30 giorni╮
│  ▂  ▅  ▁  █  ▃  ▂  ▄  ▂  ▃  ▁  ▅  ▂   │
│  15 ott            18 ott        21 ott│
│ Giorni UTC. Le barre mancanti non     │
│ sono giorni a zero.                   │
╰──────────────────────────────────────╯

COSTI PER GIORNO                 30 giorni
OGGI · 21 OTT        in corso       $12,84
20 OTT                               $9,40
19 OTT                               $7,12
18 OTT                              $11,20
...
```

#### 13.5.2 Anatomia

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Selettore provider | 48 dp, gap 8, top 8, orizzontale 20 | Segment 13 sp | Vedi 8.2 |
| 2 | Strip stato | 40–56 dp, margine top 8 | Caption 12 sp | Warning/error + dot |
| 3 | Filtro 7/30 | 48 dp, gap 8, margine top 12 | Segment 13 sp | Token esistenti |
| 4 | Card riepilogo | Raggio 16, padding 16, margine top 20 | — | `surface` |
| 5 | Totale periodo | 28 sp, autosize 24–28, maxLines 1 | Display S, Mono 500 | `text_primary` |
| 6 | Media giornaliera | 15 sp, allineata a destra | Mono M, Mono 500 | `text_secondary` |
| 7 | Caption parzialità | 11 sp, margine top 6, max 1 riga | Caption 11 sp | `text_tertiary` |
| 8 | Caption media | 11 sp, margine top 2, max 1 riga | Caption 11 sp | `text_tertiary` |
| 9 | Card barre | Raggio 16, padding 16, margine top 12 | — | `surface` |
| 10 | Header barre | Riga 24: titolo + `30 giorni` | CardTitle + Caption | `text_primary` / `text_tertiary` |
| 11 | Barre | 112 dp, margine top 12 | Mono XS 9–10 sp | `accent` oggi, `bar_past` altri |
| 12 | Caption barre | 2 righe max, margine top 8 | Caption 11 sp | `text_tertiary` |
| 13 | Header lista | 48 dp, margine top 32 | Overline + Caption | `text_secondary` / `text_tertiary` |
| 14 | Riga giorno | 48 dp; oggi 56 dp; padding orizzontale 0 | Label 13 + Mono M 15 | Testo `text_primary`; importo `text_primary` |
| 15 | Riga oggi | Seconda riga `in corso` 11 sp, tertiary | Caption 11 sp | `text_tertiary` |
| 16 | Divisore | 1 dp `border` tra le righe | — | `border` |

Regole:

- La lista è ordinata dal giorno più recente al più vecchio. Le barre sono ordinate dal più vecchio a oggi.
- Una riga = un giorno. Non ci sono orari o snapshot intraday: OpenAI restituisce bucket giornalieri, non eventi.
- La riga di oggi è alta 56 dp perché contiene `in corso`; le altre 48 dp. Se oggi non ha un bucket, non c'è riga "in corso".
- `OGGI · 21 OTT` usa il giorno UTC. Non si converte in giorno locale.
- La media è `totale / numero di giorni con almeno un costo restituito`. Non divide per 7 o 30, non tratta i giorni mancanti come zero.
- Se il periodo è parziale, la caption `Dati parziali · 5 giorni su 7` è obbligatoria. Se manca l'inizio del mese, `Dati parziali · dal 15 ott`.
- Il totale del periodo somma solo i bucket disponibili. Nessuna proiezione, nessun riempimento a zero.
- Il grafico a barre usa gli stessi token della v1. I giorni senza bucket non hanno barra; uno zero esplicito ha stub 2 dp `border`.
- Lo stato vuoto non mostra `$0,00`: mostra `Nessun costo nel periodo.` e la spiegazione. `$0,00` compare solo quando OpenAI restituisce esplicitamente zero costi per un giorno.
- Con 30 giorni la lista può contenere 30 righe: non serve paginazione. Se in futuro il range supererà 30 giorni, si pagina a 50 righe con `Carica altri`, come nello Storico DeepSeek.
- La strip di stato per 403/401 resta sopra il riepilogo, con i dati locali visibili; l'azione apre Impostazioni OpenAI.
- La card riepilogo non è cliccabile. Le righe giorno non sono cliccabili. Nessun tooltip, nessuno scrubber.

#### 13.5.3 Stati dello Storico OpenAI

| Stato | Cosa vede l'utente | Testo esatto |
|---|---|---|
| Normale | Riepilogo, barre, lista | — |
| Oggi parziale | Riga oggi con seconda linea | `in corso` |
| Periodo parziale | Caption sotto il totale | `Dati parziali · 5 giorni su 7` |
| Nessun costo nel periodo | Empty state sotto le card | `Nessun costo nel periodo.` / `OpenAI non ha restituito costi per i giorni selezionati.` / `Riprova` |
| Nessun dato ancora | Empty state, card con placeholder | `Nessun costo disponibile` / `I costi compaiono al primo aggiornamento riuscito.` / `Riprova` |
| 403 con cache | Strip error, dati visibili | `Permessi insufficienti. I costi locali restano disponibili.` |
| 401 con cache | Strip error, dati visibili | `Admin key non valida. I costi locali restano disponibili.` |
| Offline con cache | Strip warning, dati visibili | `Offline. Costi fino al 20 ott, 09:00 UTC.` |
| Dati vecchi | Strip warning, dati visibili | `Costi non recenti. Ultimo dato: 20 ott, 09:00 UTC.` |
| Caricamento iniziale | Skeleton riepilogo + barre + 3 righe | Content description `Caricamento dei costi OpenAI` |

### 13.6 Impostazioni con due provider

Obiettivo: due provider senza due schermate duplicate e senza far crescere la pagina. Il selettore provider in cima sostituisce il contenuto del provider scelto; le sezioni comuni restano una sola volta.

#### 13.6.1 Ordine e raggruppamento

```
Impostazioni
[  DeepSeek  ][  OpenAI  ]
Stai modificando OpenAI. Il provider attivo resta DeepSeek.

ADMIN KEY
╭──────────────────────────────────────╮
│ [ Admin key OpenAI        occhio ]   │
│ Serve una Admin key dell'organizzazione.│
│ Una key utente normale non può leggere│
│ i costi.                             │
│ [ Salva Admin key ]                  │
│ [ Testa connessione ]                │
│ Rimuovi Admin key                    │
│ ● Connessione riuscita...            │
╰──────────────────────────────────────╯

COSTI E PERMESSI
╭──────────────────────────────────────╮
│ OpenAI non espone un saldo...        │
│ I costi sono per giorno UTC.         │
│ Il widget della home resta DeepSeek. │
╰──────────────────────────────────────╯

AGGIORNAMENTO
╭──────────────────────────────────────╮
│ Intervallo                           │
│ [15 min] [30 min] [60 min]           │
╰──────────────────────────────────────╯

INFORMAZIONI
╭──────────────────────────────────────╮
│ Versione                   1.0.0 (1) │
│ Chiavi e dati restano sul dispositivo│
╰──────────────────────────────────────╯
```

Ordine definitivo:

1. Selettore provider, 48 dp, top 8, orizzontale 20.
2. Caption "stai modificando", 16 dp, solo se il provider del tab è diverso da quello attivo.
3. Sezione del provider:
   - DeepSeek: `ACCESSO` + `AVVISI`, invariata.
   - OpenAI: `ADMIN KEY` + `COSTI E PERMESSI`.
4. `AGGIORNAMENTO`, comune, una sola volta.
5. `INFORMAZIONI`, comune, una sola volta.

Non si mostrano due card chiave complete una sotto l'altra. Non si usa un accordion con due pannelli aperti: il selettore è più compatto e coerente con Dashboard e Storico. La scelta del tab in Impostazioni non cambia il provider attivo; la caption lo dice quando serve.

#### 13.6.2 Scheda Admin key OpenAI

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Card | Raggio 16, padding 16, bordo 1 | — | `surface` |
| 2 | Campo | 56 dp, raggio 12, icona occhio 48 | Mono S 13 sp | Testo `text_primary`; hint `text_tertiary`; focus `accent` |
| 3 | Helper | 12 sp, margine top 6, max 3 righe | Caption | `text_tertiary` |
| 4 | Warning key utente | 12 sp, margine top 6, max 2 righe | Caption | `warning` |
| 5 | Salva | 48 dp, larghezza piena, margine top 12 | Button | Primario |
| 6 | Test | 48 dp outline, margine top 8, icona sync 18 | Button | Outline |
| 7 | Rimuovi | 48 dp text button, margine top 4 | Button | `error` |
| 8 | Riga stato | 32 dp, margine top 8, dot 6 | Caption 12 | Positivo/warning/errore/accento |

Testi:

- Hint campo: `Admin key OpenAI`.
- Hint dopo salvataggio: `Nuova Admin key`.
- Helper: `Serve una Admin key dell'organizzazione. Una key utente normale non può leggere i costi.`
- Helper dopo salvataggio: `Admin key salvata. Inseriscine una nuova solo per sostituirla.`
- Salva: `Salva Admin key`.
- Test: `Testa connessione`.
- Test in corso: `Verifica in corso…`.
- Successo: `Connessione riuscita. Costi leggibili per l'organizzazione.`
- 403: `Permessi insufficienti. Serve una Admin key dell'organizzazione.`
- 401: `Admin key non valida.`
- Rete: `Connessione non riuscita. Controlla la rete.`
- Warning: `Questa sembra una key utente. Per i costi serve una Admin key.`
- Rimuovi: `Rimuovi Admin key`.
- Dialog: `Rimuovere la Admin key?` / `I costi OpenAI smetteranno di aggiornarsi. Lo storico locale resta disponibile.` / `Rimuovi` / `Annulla`.

#### 13.6.3 Prevenire l'incolla di una key utente

Il controllo locale è volutamente conservativo e non blocca definitivamente:

- Se il testo inizia con `sk-admin-`, si salva senza attrito.
- Se il testo inizia con `sk-proj-` o con `sk-` ma non con `sk-admin-`, compare il warning `Questa sembra una key utente. Per i costi serve una Admin key.` e il pulsante Salva apre una conferma.
- Dialog: titolo `Salvare comunque?`, corpo `Una key utente non può leggere i costi. La verifica con OpenAI fallirà.`, azioni `Salva comunque` / `Annulla`.
- Se l'utente conferma, la chiave viene salvata e testata; un eventuale 403 produce il messaggio di permessi insufficienti.
- Il prefisso è un'euristica locale, non una garanzia: OpenAI resta la fonte di verità. Non si usa una regex rigida che possa rifiutare formati futuri validi.
- Il campo non mostra mai la chiave salvata; dopo il salvataggio è vuoto. L'occhio mostra solo il testo appena digitato.

#### 13.6.4 Scheda Costi e permessi

Card informativa, non editabile, padding 16, raggio 16, testo 12 sp secondary, titoli 15 sp:

- `OpenAI non espone un saldo. L'app legge solo i costi giornalieri.`
- `I costi sono per giorno UTC.`
- `Il widget della home resta collegato a DeepSeek.`
- `La soglia di avviso è disponibile solo per DeepSeek.`

La card non contiene un pulsante "Ricarica" o "Aggiungi credito": non esiste un saldo OpenAI da ricaricare. Non si inventa un flusso di top-up.

#### 13.6.5 Sezioni comuni

`AGGIORNAMENTO`:

- Resta una sola, con 15/30/60 min.
- Si applica a entrambi i provider.
- Helper: `Vale per entrambi i provider. OpenAI può pubblicare i costi con qualche ora di ritardo.`
- Il cambio intervallo non forza un refresh immediato, come nella v1.

`INFORMAZIONI`:

- Versione invariata.
- Privacy: `Chiavi e dati restano sul dispositivo. Le richieste vanno a api.deepseek.com e api.openai.com.`
- Nessun riferimento a un backend o a un account dell'app.

La schermata cresce solo di una card informativa rispetto alla v1; la chiave OpenAI e la soglia DeepSeek non convivono mai nello stesso stato visibile. Il selettore è 48 dp, la caption di contesto 16 dp, quindi il costo fisso del multi-provider è 72 dp più i margini.

### 13.7 Widget con provider OpenAI attivo

Il widget resta DeepSeek-only e non cambia struttura, dati, dimensioni o token. Non riceve il provider attivo dell'app e non legge mai i costi OpenAI. La sorgente resta lo snapshot DeepSeek.

Regola unica: **se il provider attivo è OpenAI ma la chiave DeepSeek manca, il widget mostra lo stato "chiave DeepSeek mancante", non i dati OpenAI.**

| Widget | Cosa mostra | Copy |
|---|---|---|
| 2x2 | Label `DEEPSEEK` al posto di `SALDO`; saldo `—`; oggi `oggi —`; dot warning | `chiave mancante` |
| 4x2 | `SALDO`; saldo `—`; OGGI/OMAGGIO/RICARICHE `—`; dot warning; riga timestamp sostituita | `chiave DeepSeek mancante` |
| Tap | Apre Impostazioni sul tab DeepSeek | — |
| Content description 2x2 | `Chiave DeepSeek mancante. Apri le impostazioni DeepSeek.` | — |
| Content description 4x2 | `Chiave DeepSeek mancante. Il widget mostra solo DeepSeek. Apri le impostazioni DeepSeek.` | — |

Dettagli:

- La label `DEEPSEEK` è 8 caratteri e entra a 9 dp con tracking 0.08 nei 86 dp utili del 2x2; `chiave mancante` è 15 caratteri e entra a 9 dp. Non serve cambiare l'altezza del widget.
- Nel 4x2 `chiave DeepSeek mancante` è 23 caratteri a 9 dp mono: circa 124 dp, dentro i 195 dp disponibili nella riga. Nessuna ellissi.
- Se DeepSeek è configurato e OpenAI è attivo, il widget continua a mostrare il saldo DeepSeek e il suo timestamp. Non aggiunge un badge "OpenAI" e non cambia colore.
- Se mancano entrambe le chiavi, il widget mostra lo stesso stato DeepSeek mancante. Non esiste un fallback a OpenAI.
- Se la chiave DeepSeek esiste ma i dati sono vecchi, valgono gli stati stale già definiti nella v1; il provider attivo non influisce.
- Il tap apre sempre le impostazioni DeepSeek, non OpenAI. La content description lo dice.
- Il refresh del widget resta il refresh DeepSeek. Non chiama l'endpoint OpenAI.
- Nessun nuovo colore, nessuna nuova dimensione, nessun nuovo layout. L'unica stringa nuova visibile è la label `DEEPSEEK`; il resto usa gli stati esistenti.

### 13.8 Micro-copy definitiva multi-provider

Regole: stesso tono della v1, nessun punto esclamativo, nessun "Oops", nessuna emoji. "Admin key" con la A maiuscola. `UTC` sempre maiuscolo. I tempi dei costi OpenAI non si convertono in ora locale.

#### 13.8.1 Selettore e provider

| Contesto | Testo |
|---|---|
| Provider 1 | `DeepSeek` |
| Provider 2 | `OpenAI` |
| Attivo | `Provider {DeepSeek}, attivo.` |
| Configurato inattivo | `Provider {OpenAI}. Attiva per passare a OpenAI.` |
| Senza key | `Provider {OpenAI}, non configurato. Apri le impostazioni OpenAI.` |
| Fallback automatico | `Provider attivo: {OpenAI}` / `Provider attivo: {DeepSeek}` |
| Nessun provider | `Nessun provider configurato` / `Aggiungi una chiave DeepSeek o una Admin key OpenAI per iniziare.` / `Configura DeepSeek` / `Configura OpenAI` |
| Titolo Dashboard | `Credito` |

#### 13.8.2 Dashboard OpenAI

| Contesto | Testo |
|---|---|
| Label giorno corrente | `SPESO OGGI` |
| Label ultimo bucket non oggi | `SPESO IL 20 OTT` |
| Meta normale | `Costi fino alle 09:00 UTC` |
| Meta oggi parziale | `Oggi in corso · costi fino alle 09:00 UTC` |
| Meta dati vecchi | `Costi non recenti · ultimo dato 20 ott, 09:00 UTC` |
| Nota ritardo | `OpenAI pubblica i costi con qualche ora di ritardo.` |
| Metrica secondaria 1 | `7 GIORNI` |
| Metrica secondaria 2 | `MESE CORRENTE` |
| Parzialità 7 giorni | `Dati parziali · 5 giorni su 7` |
| Parzialità mese | `Dati parziali · dal 15 ott` |
| Card barre | `Spesa giornaliera` |
| Periodo barre | `14 giorni` |
| Caption barre | `Giorni UTC. Le barre mancanti non sono giorni a zero.` |
| Footer | `Dati OpenAI · aggiornamento ogni 60 min` |
| Caricamento | `Caricamento dei costi OpenAI` |

#### 13.8.3 Storico OpenAI

| Contesto | Testo |
|---|---|
| Riepilogo | `SPESA NEL PERIODO` |
| Media | `MEDIA GIORNALIERA` |
| Caption media | `Giorni UTC · media sui giorni con dati.` |
| Lista | `COSTI PER GIORNO` |
| Conteggio | `7 giorni` / `30 giorni` |
| Riga oggi | `OGGI · 21 OTT` |
| Riga ieri | `IERI · 20 OTT` |
| Riga giorno | `19 OTT` |
| Oggi parziale | `in corso` |
| Vuoto periodo | `Nessun costo nel periodo.` / `OpenAI non ha restituito costi per i giorni selezionati.` |
| Vuoto senza dati | `Nessun costo disponibile` / `I costi compaiono al primo aggiornamento riuscito.` |
| 403 con cache | `Permessi insufficienti. I costi locali restano disponibili.` |
| 401 con cache | `Admin key non valida. I costi locali restano disponibili.` |
| Offline con cache | `Offline. Costi fino al 20 ott, 09:00 UTC.` |
| Dati vecchi | `Costi non recenti. Ultimo dato: 20 ott, 09:00 UTC.` |
| Retry | `Riprova` |
| Apri impostazioni | `Apri impostazioni OpenAI` |

#### 13.8.4 Impostazioni OpenAI

| Contesto | Testo |
|---|---|
| Sezione | `ADMIN KEY` |
| Campo | `Admin key OpenAI` |
| Campo dopo salvataggio | `Nuova Admin key` |
| Helper | `Serve una Admin key dell'organizzazione. Una key utente normale non può leggere i costi.` |
| Helper salvata | `Admin key salvata. Inseriscine una nuova solo per sostituirla.` |
| Warning key utente | `Questa sembra una key utente. Per i costi serve una Admin key.` |
| Dialog conferma titolo | `Salvare comunque?` |
| Dialog conferma corpo | `Una key utente non può leggere i costi. La verifica con OpenAI fallirà.` |
| Dialog conferma azioni | `Salva comunque` / `Annulla` |
| Salva | `Salva Admin key` |
| Test | `Testa connessione` |
| Test in corso | `Verifica in corso…` |
| Successo | `Connessione riuscita. Costi leggibili per l'organizzazione.` |
| 403 | `Permessi insufficienti. Serve una Admin key dell'organizzazione.` |
| 401 | `Admin key non valida.` |
| Rete | `Connessione non riuscita. Controlla la rete.` |
| Rimuovi | `Rimuovi Admin key` |
| Dialog rimozione | `Rimuovere la Admin key?` / `I costi OpenAI smetteranno di aggiornarsi. Lo storico locale resta disponibile.` / `Rimuovi` / `Annulla` |
| Sezione info | `COSTI E PERMESSI` |
| Info 1 | `OpenAI non espone un saldo. L'app legge solo i costi giornalieri.` |
| Info 2 | `I costi sono per giorno UTC.` |
| Info 3 | `Il widget della home resta collegato a DeepSeek.` |
| Info 4 | `La soglia di avviso è disponibile solo per DeepSeek.` |
| Contesto tab | `Stai modificando OpenAI. Il provider attivo resta DeepSeek.` / `Stai modificando DeepSeek. Il provider attivo resta OpenAI.` |
| Intervallo | `Vale per entrambi i provider. OpenAI può pubblicare i costi con qualche ora di ritardo.` |
| Privacy | `Chiavi e dati restano sul dispositivo. Le richieste vanno a api.deepseek.com e api.openai.com.` |

#### 13.8.5 Widget DeepSeek-only

| Contesto | Testo |
|---|---|
| Label 2x2 senza key | `DEEPSEEK` |
| Stato 2x2 senza key | `chiave mancante` |
| Riga 4x2 senza key | `chiave DeepSeek mancante` |
| Content description 2x2 | `Chiave DeepSeek mancante. Apri le impostazioni DeepSeek.` |
| Content description 4x2 | `Chiave DeepSeek mancante. Il widget mostra solo DeepSeek. Apri le impostazioni DeepSeek.` |
| Tap | Apre Impostazioni, tab DeepSeek |

#### 13.8.6 Mappatura errori OpenAI

| Origine | UI | Copy |
|---|---|---|
| Nessuna Admin key | Stato pieno / Open "senza key" | `OpenAI non configurato` / `Aggiungi Admin key` |
| 401 | Stato pieno o strip con cache | `Admin key non valida` |
| 403 | Stato pieno o strip con cache | `Permessi insufficienti` / `Serve una Admin key dell'organizzazione.` |
| 429 | Banner warning con cache | `Troppe richieste. Costi fino al 20 ott, 09:00 UTC.` |
| 5xx | Banner warning con cache | `OpenAI non risponde` / `Costi fino al 20 ott, 09:00 UTC.` |
| Timeout / rete assente | Banner warning con cache; stato pieno senza | `Offline. Costi fino al 20 ott, 09:00 UTC.` / `Impossibile aggiornare` |
| Nessun bucket | Stato pieno / empty | `Nessun costo disponibile` |
| Bucket a zero | `$0,00` | Nessun errore |
| Dati vecchi >36 h | Banner warning | `Costi non recenti` / `Ultimo dato: 20 ott, 09:00 UTC.` |

### 13.9 Token aggiuntivi

La palette non cambia. Il multi-provider usa gli stessi colori semantici della v1; non ci sono hex nuovi. OpenAI non porta il verde del brand: l'accento resta l'ottanio dell'app.

| Token | Valore | Uso | Nota |
|---|---|---|---|
| `ds_provider_height` | 48 dp | Altezza selettore provider | Alias di `ds_touch_target` / `ds_button_height` |
| `ds_provider_gap` | 8 dp | Gap tra segmenti | Alias di `ds_space_8` |
| `ds_provider_dot` | 4 dp | Dot warning "senza key" | Nuovo; più piccolo del dot di stato 6 dp |
| `ds_latency_icon` | 14 dp | Icona orologio nella meta costi | Nuovo |
| `ds_cost_row` | 48 dp | Riga giorno OpenAI | Alias di `ds_row_snapshot` |
| `ds_cost_row_today` | 56 dp | Riga oggi con `in corso` | Nuovo |
| `ds_cost_period` | 28 sp | Totale periodo nello Storico | Alias di `TextAppearance.Ds.DisplayS` |
| `ds_cost_hero` | 48 sp, autosize 28–48 | Speso oggi nella Dashboard | Alias di `TextAppearance.Ds.Balance` |
| `ds_cost_metric` | 20 sp / 26 sp | 7 giorni e mese corrente | Nuovo `TextAppearance.Ds.MetricM` |
| `ds_provider_note` | 16 dp | Caption "stai modificando" in Impostazioni | Nuovo |
| `ds_openai_bars` | 112 dp | Barre giornaliere | Alias di `ds_bars_height` |
| `ds_provider_active_bg` | `ds_color_accent_container` | Sfondo segmento attivo | Alias semantico |
| `ds_provider_active_text` | `ds_color_accent` | Testo segmento attivo | Alias semantico |
| `ds_provider_idle_text` | `ds_color_text_secondary` | Testo provider configurato inattivo | Alias semantico |
| `ds_provider_disabled_text` | `ds_color_text_tertiary` | Testo provider senza key | Alias semantico |
| `ds_provider_disabled_dot` | `ds_color_warning` | Dot provider senza key | Alias semantico |
| `ds_cost_stale` | `ds_color_warning` | Meta costi vecchi, dot e banner | Alias semantico |

Hex invariati usati dagli alias: `accent_container` `#0E2F2C` dark / `#D8F1EC` light; `accent` `#2FD3BE` / `#0A7065`; `warning` `#E8B04B` / `#8A5200`; `text_tertiary` `#7E8F95` / `#596B70`; `bar_past` `#592FD3BE` / `#4D0A7065`.

Tipografia aggiuntiva:

| Stile | Font | Size / line height | Peso | Uso |
|---|---|---|---|---|
| `TextAppearance.Ds.MetricM` | Plex Mono | 20 sp / 26 sp | 500 | 7 giorni, mese corrente, media |

Regole:

- Il selettore usa `Widget.Ds.Button.Toggle` e `TextAppearance.Ds.Segment` senza modifiche.
- Il selettore non usa `ds_color_positive` né `ds_color_error`; l'unico stato cromatico è l'accento per l'attivo e il warning per il dot "senza key".
- I costi usano `accent`, `bar_past`, `chart_grid`, `text_primary`, `text_secondary`, `text_tertiary`, `warning`, `warning_container`. Nessun colore OpenAI.
- Il widget non cambia token: usa `ds_widget_2x2_min_*`, `ds_widget_4x2_min_*`, `ds_widget_dot`, `ds_color_warning`, `ds_color_text_tertiary` già esistenti.
- La tabella token della sezione 10 resta valida; questa tabella aggiunge solo alias semantici e i quattro valori nuovi (`ds_provider_dot`, `ds_latency_icon`, `ds_cost_row_today`, `ds_provider_note`) più lo stile `MetricM`.
- Nessun nuovo font file, nessun nuovo hex, nessuna nuova ombra, nessun nuovo raggio oltre a quelli già in uso (12 dp controlli, 16 dp card, 20 dp hero).
