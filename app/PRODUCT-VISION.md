### 1. Product vision & constraints

#### 1.1. Goal

Build a **free Android app** that helps Brazilians struggling with betting addiction to:

1. Track how much money they *didn’t* lose (“saved”) by **logging the amount whenever they feel like betting**.
2. Get **gentle reminders** when they:
    - Visit betting sites / betting apps.
    - Go through the day without logging anything (daily nudges).
3. Optionally, provide **soft blocking / friction** (e.g., “Are you sure?” notification when accessing known betting domains).

You want it **on Google Play**, and you prefer:
- **Notifications + open app** as the main protection.
- **Blocking betting sites is optional**, not required.

#### 1.2. Platform reality (2026, Android + Google Play)

Based on current Google Play policies (2026):

- Apps using `VpnService` must:
    - Fit a permitted category (e.g., *parental control* or *app usage tracking*).
    - Submit a **VpnService declaration form** in Play Console.
    - Provide **prominent in‑app disclosure + explicit consent** for VPN usage and any data collected.
    - Not use traffic for monetization or ad fraud, and must encrypt traffic to VPN endpoint if present.  
      [Google Play VpnService policy](https://support.google.com/googleplay/android-developer/answer/12564964?hl=en).

- `VpnService` **cannot** be used to:
    - Collect web browsing history or other sensitive data **without very clear disclosure and consent**.
    - Redirect or manipulate traffic for monetization.  
      [Google Play sensitive permissions & APIs](https://support.google.com/googleplay/android-developer/answer/16585319?hl=en).

- In 2025, Google introduced a **“verified VPN” badge** for VPN apps that pass MASA Level 2 etc., but your app doesn’t need that; you’re not a consumer VPN provider.  
  [Tom’s Guide / The Verge, 2025 badge program](https://www.theverge.com/news/599214/google-play-vpn-verification-badges).

Conclusion:  
Your idea is **possible and Play‑Store‑compatible** if you:

- Use `VpnService` as *parental control / device protection / app usage tracking*.
- Use **local-only traffic inspection** (no remote logging of browsing).
- Only minimally inspect traffic to detect **domains from a predefined betting list**.
- Are very transparent with users about **what you inspect** and **why**.


### 2. High‑level product design

#### 2.1. Core flows

1. **Money saved logging**
    - User opens the app instead of a betting platform.
    - Enters: amount (R$), optional note (e.g., “Jogo do Flamengo”), optional category.
    - App:
        - Adds to lifetime “Total saved”.
        - Shows charts (per day, week, month).
        - Shows “What if you had lost this?” scenarios.

2. **“Trigger” detection via VPN**
    - A lightweight **local VPN** runs as a foreground service.
    - It inspects **DNS queries** and/or HTTP(S) SNI/hostnames to see if they match:
        - A known list of **betting domains** (Brazilian sites + big international ones).
    - On match:
        - Shows a full-screen or high‑priority notification:
            - “Você está tentando acessar [nome do site]? Quer registrar esse valor na sua economia em vez de apostar?”
        - Tap opens your app on a special screen:
            - Pre-filled: “You almost bet on [site] – how much would you deposit?”

    - Optional: You can **block** the domain (return NXDOMAIN locally) or delay the connection for a few seconds to add friction.

3. **Routine notifications**
    - Daily/weekly **check‑in reminders**, e.g.:
        - “Sem apostas hoje? Registre quanto você *teria* apostado.”
    - Streak reminders:
        - “10 dias sem apostar – você economizou R$ 480!”

#### 2.2. Key features list

- **Money tracking**
    - Add/edit/delete “non-bets”.
    - Graphs: daily, weekly, monthly, category breakdown, life‑time total.
    - “What if compounding?” (e.g., if invested with simple/compound interest).

- **Triggers & protection**
    - VPN-based domain detection.
    - List of **known betting & casino domains**.
    - Optional: user can **add custom domains** or app package names (for local bookies’ apps).

- **Behavioral aids**
    - Streaks (days without logging “I actually bet”).
    - Motivational messages, optional CBT-style prompts (“O que você estava sentindo antes de querer apostar?”).
    - Shareable summary image (but **no sensitive details**).

- **Privacy & control**
    - Clear explanation: app looks only for **betting domains**, doesn’t log full browsing.
    - Data stored **locally** by default.
    - Optional encrypted backup (Google Drive / manual export) – if you want.


### 3. Architecture overview

#### 3.1. Main components

1. **Android app (Kotlin)**
    - Presentation: Jetpack Compose UI.
    - Navigation: Navigation Compose.
    - State: ViewModels + use-cases.

2. **Local database**
    - Room (SQLite) for:
        - `SavedBet` entries.
        - `SiteTrigger` records (when user tried to access betting sites).
        - Settings/configs.
        - Domain lists metadata.

3. **VPN module**
    - Android `VpnService` + custom service that:
        - Creates a TUN interface.
        - Reads packets and/or intercepts DNS queries.
        - Matches domains against betting list.
        - Posts notifications and broadcasts intents to app.

4. **Domain list provider**
    - Static list packaged in app.
    - Optional: remote JSON from your backend for updated domains (with strong privacy guarantees and no per‑user browsing data).

5. **Notifications & background**
    - Foreground notification for VPN.
    - Daily scheduled notifications (WorkManager / AlarmManager).
    - BroadcastReceiver to react to VPN “betting domain hit” events.


### 4. Detailed design by area


#### 4.1. Data model

##### 4.1.1. Saved bets (“non-bets”)

```kotlin
@Entity(tableName = "saved_bets")
data class SavedBet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,               // epoch millis (UTC)
    val amountCents: Long,             // store in cents to avoid float
    val currency: String = "BRL",
    val note: String? = null,
    val category: String? = null,      // e.g., "football", "slots"
    val source: String? = null,        // e.g., "site:bet365.com", "app:com.bookmaker"
)
```

##### 4.1.2. Trigger events (detected attempts)

```kotlin
@Entity(tableName = "site_triggers")
data class SiteTrigger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val domain: String,
    val appPackage: String?,      // best effort (foreground app/package if detectable)
    val action: String,           // "warned", "blocked", "ignored"
)
```

##### 4.1.3. Settings

```kotlin
@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String
)
```

Store flags like:
- `vpn_enabled`
- `notifications_daily_enabled`
- `block_betting_domains`
- `streak_start_date` / `last_bet_date` etc.


#### 4.2. VPN-based detection module

**Goal:** detect access to betting domains with minimal intrusion.

##### 4.2.1. How to detect betting sites

Two main practical strategies:

1. **DNS‑only interception** (simpler)
    - Parse DNS queries going through the VPN.
    - If a query domain (e.g. `sports.bet365.com`) matches `*.bet365.com`, record a trigger.
    - Pros:
        - Much simpler than parsing full TCP streams.
        - Works regardless of HTTPS.
    - Cons:
        - If the user uses encrypted DNS outside your VPN (DoH/DoT in browser), you may not see it.

2. **DNS + SNI / TLS ClientHello**
    - Inspect the first few packets of TLS connections to read SNI (server name).
    - More complex (requires parsing TLS handshake).
    - More reliable if DNS is obscured inside a tunnel.

For a v1, **DNS-only is acceptable**. Many on‑device firewalls (like Protectstar’s Firewall AI) do this locally without server logging, which is consistent with Play’s allowed use cases for VPN‑based device protection [example disclosure](https://www.protectstar.com/en/vpnservice-disclosure).

##### 4.2.2. Android `VpnService` usage

- Create `AntiBetVpnService` that extends `VpnService`.
- Use a **foreground service** with persistent notification (“Anti‑Bet protection is on”).
- Call `Builder()` to:
    - Add allowed routes: typically `0.0.0.0/0` for all IPv4 (and IPv6 if you support it).
    - Add DNS servers (e.g., 1.1.1.1 or user’s).
    - Set MTU.
- Acquire TUN interface via `establish()`.
- Run a worker thread to:
    - Continually read from the TUN `FileDescriptor`.
    - Inspect outbound packets:
        - If UDP/53 -> parse DNS query.
        - Optionally track flows for TCP/UDP.

##### 4.2.3. Matching logic

- Maintain an in-memory **Trie / suffix tree** or a normalized set for betting domains:
    - Example normalized list:
        - `bet365.com`
        - `betano.com`
        - `pixbet.com`
        - `blaze.com` (common in Brazil)
        - `*.betfair.com` etc.
- For a DNS query `www.betano.com`:
    - Lowercase.
    - Strip known prefixes (`www.`).
    - Walk suffixes to match (or simple `endsWith` against list).

When a match is found:

1. Log a `SiteTrigger`.
2. Post a notification:
    - Title: “Você está acessando um site de aposta?”
    - Body: “Abra o Anti‑Bet para registrar quanto você *não* quer perder.”
    - Action: PendingIntent that opens your activity with:
        - `EXTRA_DOMAIN = "betano.com"`

3. If **blocking** is enabled:
    - Do **not** forward the DNS request; instead respond with NXDOMAIN or 0.0.0.0 locally.
    - Or drop the packet.

**Compliance note:**  
Per Google’s VpnService policy, you must document that you:
- Use VpnService for **parental control / app usage tracking / device security**.
- Do **not** redirect or monetize traffic.
- Do not collect web history beyond what is necessary (just matching betting domains) and only locally, unless you clearly declare otherwise.  
  [Google Play VpnService policy](https://support.google.com/googleplay/android-developer/answer/12564964?hl=en).


#### 4.3. Notifications & reminder logic

Use **WorkManager** for periodic jobs (daily):

1. **Daily check-in worker**
    - Runs once per day.
    - Checks if user logged at least one `SavedBet` today.
    - If none:
        - Notification: “Hoje você não registrou nenhuma economia. Quer registrar quanto teria apostado?”

2. **Streak worker**
    - Compute days since `last_real_bet` (if you allow user to log “I relapsed”).
    - Show achievement-type notifications.

3. **VPN status**
    - If user enabled VPN but service is not running:
        - Show a notification: “Proteção Anti‑Bet está desligada. Toque para ativar.”


#### 4.4. UX & flows (screen level)

##### 4.4.1. Onboarding

1. **Goal explanation**
    - “Este app ajuda você a economizar o dinheiro que iria para apostas.”
    - “Você registra o valor que *quase* apostou, e nós mostramos quanto está economizando.”

2. **Optional VPN feature explanation (separate screen)**
    - “Se você quiser, podemos te avisar sempre que você tentar abrir um site de aposta.”
    - Explain clearly:
        - We use Android’s on‑device VPN.
        - Traffic is not routed to our servers.
        - We only check domains against a betting list.
        - We do not keep full browsing history.

3. **Explicit consent**
    - Checkbox + button:
        - “Li e concordo que o app use o VpnService apenas para detectar sites de apostas e me enviar alertas.”
    - Tap “Ativar proteção” -> triggers system VPN permission dialog.

This fulfils Play’s **prominent disclosure & consent** requirements [User Data / sensitive APIs policy](https://support.google.com/googleplay/android-developer/answer/16585319?hl=en) and [VpnService article](https://support.google.com/googleplay/android-developer/answer/12564964?hl=en).

##### 4.4.2. Home screen

- Top:
    - “Total economizado”: big number in R$.
    - “Este mês”: R$ value.
    - Streak: e.g., “12 dias sem apostar”.

- Center:
    - Button: “Registrar valor que *quase* apostei”.
    - Graph: bar chart / line chart.

- Bottom tabs:
    - “Início” | “Histórico” | “Proteção” | “Configurações”.

##### 4.4.3. “New saved amount” screen

Fields:
- Amount input (R$, masked).
- Note (optional).
- Category (dropdown/tags).
- Source:
    - Pre‑filled if came from VPN event: `“Site detectado: betano.com”`.

Save:
- Insert into Room.
- Show updated total.

##### 4.4.4. Protection screen

- Switch: **“Ativar alertas para sites de aposta (VPN)”**
- Status:
    - Running / Not running.
- Option: **“Tentar bloquear sites de aposta”** (beta).
- List:
    - “Sites monitorados” — link to domain list (scrollable, read‑only).
    - “Adicionar site manualmente” (for small/local domains).


### 5. Google Play policy & compliance plan

To stay safe with Google Play (2026):

#### 5.1. VpnService declaration

In Play Console, for the **VpnService declaration** [Play’s VpnService help page](https://support.google.com/googleplay/android-developer/answer/12564964?hl=en):

- Q1: Is VPN the core functionality?
    - Likely **No** (core is addiction-help & tracking; VPN is supporting but still essential).
- Q2: Permitted functionality:
    - Check:
        - “Parental control”
        - “App usage tracking”
        - Potentially “Device security (firewall-style)” if justified.
- Provide explanation:
    - Describe clearly: you use a local VPN to detect and optionally block access to gambling/betting domains to support addiction recovery.
    - Emphasize **on-device only** packet inspection.
    - State you do **not** monetize traffic or collect general browsing history.

You must also provide:
- A short video (≤90s) showing:
    - How VPN is activated.
    - Prominent disclosure & consent.
    - What happens when user accepts/declines.

#### 5.2. Data Safety form

In the Play Console **Data Safety** section, you’ll declare:

- If you store **web browsing history / web domain info**:
    - If you keep only *matched betting domains* as `SiteTrigger` events, this likely falls under “Web browsing history” / “App activity”.
    - You must mark:
        - That you collect “Web browsing history / domains” in the context of VPN, but:
            - They’re processed **on-device only** and not shared, OR
            - If you sync them to a server for backup, declare retention and security.

From the sensitive APIs article: anything from VPN is treated as **personal and sensitive data** and must be declared [Permissions & sensitive APIs](https://support.google.com/googleplay/android-developer/answer/16585319?hl=en).

- Purpose:
    - “App functionality” (helping user avoid betting).
    - Not ads / analytics.

- Sharing:
    - Ideally: **No sharing**.

#### 5.3. AccessibilityService (optional alternative)

You *could* additionally use an `AccessibilityService` to detect:

- Foreground app.
- Text containing known betting site names.

However:
- Google has **strict policies** around Accessibility for monitoring user behavior and may consider it “spyware” if misused [Spyware policy is in same policy center as above](https://support.google.com/googleplay/android-developer/answer/16585319?hl=en) (linked from “Spyware” / “Device abuse” section).
- If you already have VPN-based solution, I’d avoid Accessibility to reduce risk of rejection.

Recommendation:  
**Only use `VpnService`**, do not use Accessibility for v1.


### 6. Backend (optional)

You can ship v1 completely **offline** (local DB only). Optional backend capabilities:

1. **Domain list updates**
    - HTTPS endpoint returns:
      ```json
      {
        "version": 5,
        "updated_at": "2026-02-15T00:00:00Z",
        "domains": [
          "betano.com",
          "pixbet.com",
          "... more ..."
        ]
      }
      ```
    - App periodically checks for new version.
    - No user identifiers or browsing data are sent in these requests.

2. **Anonymous stats (only if you really want)**
    - Number of installs, number of anonymized triggers etc.
    - But **avoid** sending detailed trigger logs to keep trust high.

From a policy & trust perspective, simpler is safer: **no user traffic or trigger logs to server**.


### 7. Implementation plan (phased)

#### Phase 1 – Core tracker (no VPN yet)

- Implement:
    - Room DB + basic entities.
    - UI for logging saved amounts.
    - Charts, totals.
    - Local notifications (daily reminder).
- Release as **“Anti‑Bet: Rastreador de Economia”** without VpnService.
- Get early user feedback (friends, testers).

#### Phase 2 – Add VPN protection (beta)

- Implement `AntiBetVpnService`:
    - Start/stop from UI.
    - Foreground notification.
    - DNS parsing + domain match.
- When hitting betting domain:
    - Raise notification.
    - Deep-link into “add saved amount” screen with domain pre-filled.
- Add VpnService declaration & Data Safety entries in Play Console.
- Test heavily:
    - Battery consumption.
    - CPU when heavily browsing.
    - Behavior under different networks (Wi‑Fi, 4G/5G).

#### Phase 3 – Optional blocking & custom domains

- Toggle to **block** betting domains:
    - Respond with NXDOMAIN locally or simply drop DNS packets for matching domains.
- UI to:
    - Show “Blocked attempts today”.
    - Let user add custom domains.

#### Phase 4 – Polishing & advanced features

- Better visualizations.
- Motivational content (maybe partner with addiction therapists).
- Export data (CSV, PDF summary).
- Optional secure cloud backup.


### 8. Security & privacy best practices

- **Store all sensitive data locally encrypted**:
    - Use SQLCipher or Android’s EncryptedSharedPreferences / EncryptedFile if you want stronger protection.
- Don’t log raw packet contents or non‑betting domains.
- In logging frameworks (Crashlytics, etc.), ensure no domain/URL is logged.
- Create a **clear privacy policy** and a **VPN disclosure page**, similar in spirit to Protectstar’s VPN disclosure but customized for your use case [example disclosure structure](https://www.protectstar.com/en/vpnservice-disclosure).


### 9. Feasibility summary

Technically:

- Android `VpnService` + DNS inspection is **well within reach** for an experienced Android dev.
- Room, WorkManager, Compose are standard.
- No special hardware or OS modification is required.

From Google Play policy perspective (2026):

- Your use case aligns with approved categories for VpnService (parental control / app usage tracking / security) as described in the official VpnService policy help article [Google Play VpnService help](https://support.google.com/googleplay/android-developer/answer/12564964?hl=en).
- As long as:
    - You are **transparent**.
    - You **do not monetize traffic**.
    - You limit data collection to what’s necessary and declare it properly.  
      …your app should be acceptable.


If you want next, I can:

- Sketch a **concrete Kotlin skeleton** (`VpnService`, Room DAO, basic Compose screens).
- Design the **exact disclosure text** to use inside the app and for the Play Store listing (in Portuguese).
- Propose an initial **Brazilian betting domain seed list** structure (not the actual full list, but how to manage and update it safely).

