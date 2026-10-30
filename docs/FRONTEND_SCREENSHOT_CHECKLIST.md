# Frontend screenshot checklist

_Phase 0 §3.10 ([RUST_PORT_PLAN.md §7](RUST_PORT_PLAN.md#7-frontend-leptos)): the practical safety net
for the Leptos rewrite is a page-by-page visual comparison against the Compose HTML app. Take the
screenshots from the Kotlin web app before Phase 6 starts and again from the Leptos app at the end
of it, same server, same data, same browser window size._

Routes are those of `shared_fe/src/commonMain/kotlin/io/madrona/njord/routing/NjordRoute.kt`; the
control panel tabs are the `tabKey`s in `shared_fe/src/jsMain/kotlin/io/madrona/njord/ui/ControlPanel.js.kt`.
The route list and query-parameter behaviour are pinned separately by `RouteMatcherTest` and the
`frontend` fixture (checklist §3.10).

## Setup

- Same backend for both captures: a local Kotlin server seeded with `testdata/fixtures/` (the
  conformance suite's `seeded` fixture does this), `webStaticContent` pointing at the build under
  test. Record the `/v1/about/version` body next to the screenshots.
- Browser: Chromium, 1440×900 and 390×844 (phone), light colour scheme, default zoom, no
  extensions. Disable animations (`prefers-reduced-motion`).
- Name files `<page>_<variant>_<width>.png`, e.g. `map_day_meters_1440.png`.

## Pages

| Page | Route | Capture | Compare |
|---|---|---|---|
| Home / about | `/` (`NjordRoute.About`) | Full page | Nav bar entries (About, ENC, Control Panel) and order; headline, version text, links; footer |
| Map | `/enc` (`NjordRoute.Enc`) | (a) initial view, (b) zoomed to US5WA22M at z14 for each `Depth × ThemeMode` (9) and the two custom themes, (c) after clicking a LIGHTS, a SOUNDG and a DEPARE feature (feature popup / info panel) | Map controls present and positioned; the style actually applied (compare against `GET /v1/style/{depth}/{theme}`: depth-band colours, sounding labels with tenths subscript and drying underline, light sector icons); the popup's layer name and property table; URL/query parameters that persist the selection (depth, theme, position) |
| Chart catalog | `/control/charts_catalog/` | First page, then after following the page cursor | Row count per page (100), chart names, `nextName` paging control, search / filter box if any |
| Chart installer / admin | `/control/charts_installer/` | (a) logged out, (b) after basic-auth login (signature in local storage), (c) during an upload (Extracting, Info progress), (d) CompletionReport, (e) after `DELETE /v1/enc_save` | Login form; upload control; the live `WsMsg` rendering of each state; error rendering (`WsMsg.Error`) |
| Symbols | `/control/symbols/` and `/control/symbols/<OBJL>` (e.g. `BOYLAT`, `LIGHTS`, `SOUNDG`) | Index and three object classes | S-57 object list order and names; per-class attribute table and expected-input meanings (`/v1/about/s57objects`, `s57attributes`, `expectedInput`) |
| Sprites | `/control/sprites/` | Full page per theme | Every sprite rendered from the sheet with its name; count equals the `Sprite` enum |
| Colors | `/control/colors/` | Full page | Every `Color` with its hex per `ThemeMode` and the custom themes (`/v1/about/colors`, `/colors/custom`) |
| Chart info | `/chart/US5WA22M.000` (`NjordRoute.Chart`) | Full page | Name, scale, zoom, updated/issued, layer list, feature count, bounds, `dsid_props` and `chart_txt` rendering; delete action visible only when logged in |
| Layer | `/layer/BOYLAT` and `/layer/TOPMAR` (`NjordRoute.Layer`) | First page, then after "next" | Items per page (5), lat/lng, chart name, geometry type, props; TOPMAR rows show `_PTFM`/`_ASOC` |
| Not found | `/404` and an unknown path | Full page | The not-found page renders (routing falls back to `NotFound`) |

## What counts as a difference

- Layout within a few pixels, fonts, colours and icons must match; text content must be identical
  for the same backend data.
- Interactive state that survives navigation (selected depth/theme, catalog cursor, login) must
  behave the same.
- Network: the Leptos build must issue the same `/v1/*` requests for each page (compare the
  browser's network log, not just the pixels), typed through `njord-model`.
