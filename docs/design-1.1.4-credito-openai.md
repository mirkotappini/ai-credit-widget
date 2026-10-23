# Credito residuo OpenAI — Design 1.1.4

Estende `docs/design.md`. Per OpenAI **sostituisce** la regola "nessun credito residuo" di §13.3, la riga "OpenAI non espone un saldo" di §13.6.4 e aggiunge una sezione in §13.6.1. Restano validi: mai la parola "saldo" per OpenAI, costi solo giorni UTC, nessuna conversione valuta, nessuna soglia, nessuna notifica, widget DeepSeek-only, nessuna somma con DeepSeek. Storico invariato.

## 1. Principio

Un solo numero, una sola provenienza dichiarata. Il credito residuo OpenAI vive in una card secondaria e ha due sorgenti possibili, mai mostrate insieme:

- **reale**: valore letto dall'endpoint legacy di fatturazione con la Admin key (tag `Da OpenAI`);
- **stima**: importo di riferimento inserito dall'utente meno i costi giornalieri pubblicati dopo quella data (tag `Stima`).

Se il valore reale esiste e ha una forma valida, vince sempre; la stima resta come riserva e non viene mostrata. Se il reale non c'è o non è leggibile, la card passa a `Stima` senza avvisi, senza banner e senza toccare lo stato della Admin key. Se non c'è né reale né stima, la card resta visibile nello stato `Da impostare`: valore `—`, una riga che dice che OpenAI non espone il dato e il pulsante `Imposta riferimento` che porta al campo in Impostazioni. (Fino alla 1.1.4 la card era `GONE`: l'utente non sapeva che la funzione esistesse.)

Cosa non promettiamo: che il valore reale ci sia sempre (endpoint non documentato, può rispondere 401/403/404 o HTML); che la stima sia esatta (i costi arrivano in ritardo ed è per eccesso); che l'app ricarichi o aggiorni il credito sull'account; che il riferimento sia una ricarica; che il credito generi avvisi o soglie.

## 2. Dashboard OpenAI

Ordine dall'alto, invariato tutto il resto: selettore → hero `SPESO OGGI` → banner → **card credito** → card barre → footer. Il costo del giorno resta il numero dominante (48 sp); il credito è 28 sp, quindi secondo livello, visibile senza scroll al posto della card barre che scende sotto la piega.

Anatomia della card (nuova, `Widget.Ds.Card`, non cliccabile, nessun chevron, nessun divisore interno):

| # | Elemento | Geometria | Tipografia | Colore |
|---|---|---|---|---|
| 1 | Card | raggio 16, padding 16, bordo 1, margine top 12 | — | `surface` |
| 2 | Riga label + tag | min 20 dp, label a peso 1, gap 8, label max 2 righe | Overline 11/14 | label `text_secondary` |
| 3 | Tag | pill raggio 12, bordo 1, min 20, padding 8x3, testo su una riga | Overline 11/14 | reale: `text_secondary` su `surface_high`; stima: `warning` su `warning_container` |
| 4 | Valore | margine top 6, maxLines 1, autosize 28–20 | Display S, Mono 500 28/34 | `text_primary` |
| 5 | Caption riferimento | margine top 6, max 2 righe; importo e data in mono via span | Caption 12/16 | `text_tertiary` |
| 6 | Nota | margine top 4, max 3 righe | CaptionSmall 11/14 | `text_tertiary` |

Regole del numero: due decimali sempre, formato unico §2.5, `−` U+2212 se negativo, nessun `~`/`≈` sul numero (il numero è il miglior dato disponibile, la certezza la dichiarano tag e caption). Nessuna barra, nessuna percentuale, nessun colore di allarme sul valore, anche se negativo. Il tag è l'unico segnale cromatico e non è mai l'unico segnale: cambia anche la parola.

I tre stati:

| Stato | Tag | Valore | Caption 1 | Caption 2 |
|---|---|---|---|---|
| Reale disponibile | `Da OpenAI` | reale, esatto | — | `Letto da OpenAI. Non è una stima.` |
| Reale con cache >36 h | `Da OpenAI` | ultimo reale | — | `Ultimo valore letto il 20 ott. Potrebbe non essere aggiornato.` |
| Solo stima | `Stima` | riferimento − costi dopo la data | `Riferimento $100,00 del 12 ott` | `Stima per eccesso: con i costi in ritardo il credito reale può essere più basso.` |
| Stima, riferimento di oggi | `Stima` | uguale al riferimento | `Riferimento di oggi: nessun costo pubblicato dopo.` | come sopra |
| Stima negativa | `Stima` | `−$4,20` | `Riferimento $100,00 del 12 ott` | `I costi pubblicati superano il riferimento di $4,20: il credito risulta esaurito e il reale può essere più basso.` |
| Nessun dato | `Da impostare` | `—` | — | `OpenAI non espone il credito residuo: serve un riferimento tuo. L'app sottrae poi i costi dei giorni successivi.` + pulsante `Imposta riferimento` |

Calcolo della stima: `riferimento − Σ bucket con giorno UTC > data riferimento`, sui soli bucket presenti in locale. I giorni mancanti non sono zero e non vengono inventati: rendono la stima per eccesso, ed è per questo che la caption dice "può essere più basso", non "è". Il giorno del riferimento non viene sottratto (l'importo letto dalla console lo include già). Nessun job separato: si ricalcola a ogni bind con i costi già in cache.

## 3. Impostazioni > OpenAI

Nuova sezione tra `ADMIN KEY` e `COSTI E PERMESSI`: `CREDITO RESIDUO`, overline a 32 dp dalla card precedente, card a 8 dp.

| # | Elemento | Geometria | Tipografia |
|---|---|---|---|
| 1 | Campo importo | `TextInputLayout` 56 dp, raggio 12, prefisso `$`, tastiera numerica decimale, max 12 caratteri | Mono S 13 sp |
| 2 | Riga data | 48 dp, titolo a sinistra, valore `12 ott` a destra, icona chevron 18 dp, apre un date picker | Label 13 + Mono S 12 |
| 3 | Helper | 12 sp, max 3 righe | Caption |
| 4 | `Salva riferimento` | 48 dp pieno, larghezza piena, top 12; disabilitato se importo vuoto/non valido; abilitato anche senza Admin key | Button |
| 5 | `Rimuovi riferimento` | 48 dp text button, `error`, top 4, visibile solo se esiste un riferimento | Button |
| 6 | Riga stato | 32 dp, dot 6, top 8, come la riga test connessione | Caption 12 |
| 7 | Nota stima | 11 sp, top 8, max 3 righe | CaptionSmall |
| 8 | Riga valore reale | 12 sp, top 12, solo se esiste un reale in cache; importo in mono via span | Caption 12 |

Comportamento:

- **Salvataggio**: esplicito, con entrambi i campi validi. Sostituisce il riferimento esistente. Importo > 0 e max 2 decimali; data tra oggi−30 giorni e oggi (giorni UTC, nessuna conversione oraria). Errori inline sotto il campo, non dialog.
- **Ri-ancoraggio dopo una ricarica**: si inserisce il nuovo importo letto dalla console e la data di oggi, poi `Salva riferimento`. Nessun rilevamento automatico della ricarica: l'app non può saperla. Il valore reale in cache non viene cancellato dal ri-ancoraggio.
- **Rimozione**: dialog di conferma; sparisce solo la stima. Se esiste un valore reale, la card in Dashboard resta. Non tocca Admin key, costi o storico.
- **Fallimento legacy**: nessun banner, nessuna riga di errore, nessun `Prefs.setLastError`, nessun cambio di stato della key; resta l'ultimo reale valido (con la sua età) o, in mancanza, la stima. La riga 8 compare solo in caso di successo.
- Un riferimento può essere salvato anche senza Admin key; senza costi locali la stima non è calcolabile e la card resta nascosta.
- Rimozione o sostituzione della Admin key: si cancella il valore reale in cache (l'organizzazione può cambiare), il riferimento resta.

## 4. Copy

Dashboard:

| Risorsa | Testo |
|---|---|
| `openai_credit_title` | `Credito residuo` |
| `openai_credit_tag_real` | `Da OpenAI` |
| `openai_credit_tag_estimate` | `Stima` |
| `openai_credit_ref` | `Riferimento %1$s del %2$s` |
| `openai_credit_ref_today` | `Riferimento di oggi: nessun costo pubblicato dopo.` |
| `openai_credit_note_estimate` | `Stima per eccesso: con i costi in ritardo il credito reale può essere più basso.` |
| `openai_credit_note_real` | `Letto da OpenAI. Non è una stima.` |
| `openai_credit_note_real_stale` | `Ultimo valore letto il %1$s. Potrebbe non essere aggiornato.` |
| `openai_credit_overspent` | `I costi pubblicati superano il riferimento di %1$s: il credito risulta esaurito e il reale può essere più basso.` |
| `openai_credit_cd_real` | `Credito residuo letto da OpenAI: %1$s. Non è una stima.` |
| `openai_credit_cd_real_stale` | `Credito residuo letto da OpenAI il %1$s: %2$s. Potrebbe non essere aggiornato.` |
| `openai_credit_cd_estimate` | `Credito residuo stimato: %1$s. Riferimento %2$s del %3$s. Stima per eccesso: con i costi in ritardo il credito reale può essere più basso.` |
| `openai_credit_cd_estimate_today` | `Credito residuo stimato: %1$s. Riferimento di oggi, nessun costo pubblicato dopo.` |
| `openai_credit_cd_negative` | `Credito residuo stimato: meno %1$s. I costi pubblicati superano il riferimento di %2$s.` |

Impostazioni e card informativa:

| Risorsa | Testo |
|---|---|
| `settings_openai_credit_section` | `Credito residuo` |
| `settings_openai_credit_amount_hint` | `Importo di riferimento` |
| `settings_openai_credit_date` | `Data del riferimento` |
| `settings_openai_credit_date_cd` | `Data del riferimento: %1$s. Apri il calendario.` |
| `settings_openai_credit_helper` | `Leggi l'importo nella pagina Billing di OpenAI. La stima sottrae i costi pubblicati dal giorno successivo.` |
| `settings_openai_credit_note` | `È una stima. I costi arrivano con qualche ora di ritardo, quindi il valore può essere più alto del reale.` |
| `settings_openai_credit_save` | `Salva riferimento` |
| `settings_openai_credit_saved` | `Riferimento salvato.` |
| `settings_openai_credit_remove` | `Rimuovi riferimento` |
| `settings_openai_credit_remove_title` | `Rimuovere il riferimento?` |
| `settings_openai_credit_remove_body` | `Il credito stimato non sarà più mostrato. I costi restano disponibili.` |
| `settings_openai_credit_removed` | `Riferimento rimosso.` |
| `settings_openai_credit_real_available` | `Letto da OpenAI: %1$s. In Dashboard questo valore ha la precedenza sulla stima.` |
| `settings_openai_credit_error_zero` | `Inserisci un importo maggiore di zero.` |
| `settings_openai_credit_error_decimals` | `Usa al massimo due decimali.` |
| `settings_openai_credit_error_future` | `La data non può essere successiva a oggi.` |
| `settings_openai_credit_error_old` | `Scegli una data degli ultimi 30 giorni.` |
| `settings_openai_credit_error_clock` | `La data del riferimento è successiva a oggi. Aggiornala.` |
| `settings_openai_info_5` | `Se la lettura non riesce, il credito residuo è una stima basata sul tuo importo di riferimento.` |
| `settings_openai_info_6` | `Il riferimento non modifica il credito del tuo account: serve solo alla stima.` |

Da modificare: `settings_openai_info_1` → `OpenAI non espone il credito residuo nell'API dei costi. Se l'endpoint di fatturazione risponde, l'app legge il valore reale.` La `contentDescription` del selettore data usa `settings_openai_credit_date_cd`; il calendario usa come titolo `settings_openai_credit_date` e le azioni di sistema. La card credito è un unico nodo TalkBack: `contentDescription` sul contenitore, figli `importantForAccessibility="no"`; usa le stringhe `_cd_` qui sopra, con `%1$s` = importo formattato (`Format.money`), `%2$s`/`%3$s` = importo e data del riferimento (`Format.dayLabel`).

## 5. Stati ed edge case

| Caso | Dashboard | Impostazioni |
|---|---|---|
| Primo avvio, nessuna chiave | nessuna card | sezione visibile, campi vuoti, data = oggi, Salva disabilitato |
| Nessuna Admin key | nessuna card | riferimento salvabile; nessuna stima calcolabile |
| Key non admin (401/403 sui costi) | stato pieno o strip della key; card assente o solo stima se c'è cache costi | invariata |
| Legacy 403/404 | silenzioso: reale invariato se in cache, altrimenti stima, altrimenti niente | nessuna riga di errore |
| Legacy risponde HTML/JSON non valido/valuta ≠ USD | scarta la risposta, nessuna cache, nessun crash | nessuna riga di errore |
| Reale in cache e rete assente | reale visibile; caption di età oltre 36 h | riga 8 invariata |
| Stima senza riferimento | card `Da impostare`, valore `—`, pulsante `Imposta riferimento` | campi vuoti, campo importo a fuoco se si arriva dal pulsante |
| Riferimento nel futuro | mai mostrato | blocco al salvataggio; se arriva da cambio d'ora: card nascosta + errore `_clock` |
| Riferimento più vecchio di 30 giorni | non calcolabile | blocco al salvataggio |
| Residuo negativo | `−$…` in `text_primary`, tag `Stima`, caption di superamento; nessun banner | invariata |
| Riferimento di oggi | valore = riferimento, caption dedicata | invariata |
| Dati costi vecchi >36 h | banner costi esistente; stima invariata, reale con caption di età | invariata |
| Key rimossa/sostituita | reale cancellato, resta la stima | reale rimosso, riferimento intatto |

## 6. Accessibilità e font scale 1.3

- Card credito = un solo nodo TalkBack, frase completa, tag incluso; niente focus fittizio sui figli.
- Tag sempre accompagnato da parola e caption: il colore non è l'unico segnale. Nessun testo sotto 4,5:1 (tag reale 4,84:1 minimo, tag stima 7,80:1).
- Target 48 dp: riga data, Salva, Rimuovi, dialog. Il tag non è interattivo.
- Font scale 1.3: valore autosize 28→20 senza ellissi; label max 2 righe; tag a `wrap_content` con min 20 dp (non altezza fissa, altrimenti clippa); caption max 3 righe e vanno a capo; nessun testo tagliato. La riga label/tag usa peso sulla label, non larghezze fisse.
- Ordine di lettura: hero, banner, card credito, barre. La card non è cliccabile e non ha tooltip; nello stato `Da impostare` l'unico elemento attivo è il pulsante `Imposta riferimento` (48 dp, inset orizzontali a 0 per allinearsi al testo).
- TalkBack: negativo letto come "meno …", mai come trattino; la data è sempre "12 ottobre", non "12/10".

## 7. File e viste da toccare

| File | Intervento |
|---|---|
| `res/layout/fragment_dashboard.xml` | nuova card dentro `openai_data`, dopo `openai_banner` e prima della card barre: `openai_credit_card` (contenitore focusabile), `openai_credit_tag`, `openai_credit_value`, `openai_credit_ref`, `openai_credit_note`, `openai_credit_action`; label statica `@string/openai_credit_title` |
| `ui/DashboardFragment.kt` | binding dei 6 id; `bindOpenAiCredit(...)` chiamata in coda a `bindOpenAi`; stato `Da impostare` con pulsante quando mancano reale e stima; tag, caption e `contentDescription` per i 7 stati; nessuna nuova richiesta di rete qui |
| `res/layout/fragment_settings.xml` | sezione tra la card Admin key e l'overline `COSTI E PERMESSI`: `openai_credit_section`, `openai_credit_amount_layout`, `openai_credit_amount_input`, `openai_credit_date_row`, `openai_credit_date_value`, `openai_credit_helper`, `openai_credit_save_button`, `openai_credit_remove_button`, `openai_credit_status_row`, `openai_credit_status_dot`, `openai_credit_status`, `openai_credit_note`, `openai_credit_real_line` |
| `ui/SettingsFragment.kt` | binding, default data = oggi UTC, date picker con range oggi−30…oggi, validazione inline, salva/rimuovi con dialog, stato, aggiornamento riga reale; nessun toast |
| `res/values/strings.xml` | stringhe §4, modifica `settings_openai_info_1`; aggiungere anche `info_5` e `info_6` alla card esistente |
| `data/Prefs.kt` | chiavi e accessor: importo e data del riferimento, importo e timestamp del reale; cancellare il reale al salvataggio/rimozione della Admin key |
| `net/OpenAiCreditApi.kt` (nuovo) | endpoint legacy, `CreditResult` con solo `Success(amount, currency)` o `Unavailable`; 401/403/404/HTML/JSON strano → `Unavailable`; nessun log del body |
| `repo/OpenAiCreditRepository.kt` (nuovo) | legge solo se c'è la key, dopo un refresh costi riuscito, valida (numerico, ≥ 0, USD, ≤ 10.000.000), salva in Prefs; non scrive mai `setLastError` |
| `logic/CreditEstimator.kt` (nuovo) | funzione pura riferimento+costi+oggi → residuo e flag “riferimento oggi”; testabile senza UI |
| `res/values/dimens.xml` | `ds_credit_tag_min_height` = 20 dp; nessun nuovo colore, font, raggio o stile |

Note: niente modifiche a `fragment_history.xml`, widget, soglia o notifiche. La card non riceve il pull-to-refresh: si aggiorna col refresh esistente di OpenAI. Il valore reale non viene mai scritto nel DB dei costi.

## 8. Revisione 1.1.5 — la card non sparisce più

Motivo: sul telefono l'utente vedeva solo il costo consumato e concludeva che il credito residuo non esistesse. La causa era lo stato senza dati reso con `GONE`, che è indistinguibile da una funzione assente.

- Nuovo stato `Da impostare` (tag neutro come il reale): valore `—`, nota `OpenAI non espone il credito residuo: serve un riferimento tuo. L'app sottrae poi i costi dei giorni successivi.`, pulsante testuale `Imposta riferimento`.
- Il pulsante apre Impostazioni sul provider OpenAI e scorre alla sezione `Credito residuo` mettendo a fuoco il campo importo (`MainActivity.showSettings(provider, focusCredit = true)` → `SettingsFragment` argomento `settings_focus_credit`).
- Invarianti confermati: nessuna richiesta di rete in più, reale > stima, 401/403 silenziosi, nessun banner, nessuna soglia, nessun `$0,00` al posto del `—`.
- Copy: resta vietata la parola "saldo" per OpenAI.
