//! Semantic plans → native Android views.
//!
//! The portability proof. An LLM in octos-one emits a ~600-byte typed **plan**; that
//! runtime lowers it to makepad Splash DSL. This lowers the SAME plan JSON to this
//! backend's node tree, which Java turns into `android.widget.*` /
//! `com.google.android.material.*` views. No DSL is involved on this path at all —
//! plan straight to nodes.
//!
//! If one plan drives both, the plan is genuinely backend-agnostic and the per-backend
//! cost is a lowering table rather than a rewrite.
//!
//! ## Why the plan makes this feasible at all
//!
//! The two dialects share nothing. makepad cards are `Label{…}` / `RoundedView{…}`
//! resolved through a widget registry; this backend wants `{kind:"text"}` /
//! `{kind:"card"}` plain records. Parsing the makepad dialect here would mean growing a
//! compatibility compiler for widget inheritance, resources, closures and shader
//! uniforms. Lowering a typed plan is a match statement.
//!
//! ## Where the data comes from
//!
//! octos-one has ~30 `sys.*` helpers the CARD calls at render time. This backend has
//! none, so the lowering resolves data itself, here, at lowering time. That is the
//! measured porting cost — host capability, not widgets — and it is why every fetch
//! below mirrors a `sys.*` helper's semantics exactly:
//!
//! | here | octos-one |
//! |---|---|
//! | [`geocode`] | `sys.geocode` / `sys.geocodenum` |
//! | [`weather_str`] | `sys.weather` (whole-number rounding included) |
//! | [`week_extent`] | `sys.weekmin` / `sys.weekmax` |
//! | [`weather_cond`] | `sys.weathercond` |
//! | [`weather_word`] | `sys.weatherword` |
//! | [`day_name`] | `sys.dayname` |
//! | [`news_field`] | `sys.news` |
//!
//! ## Honest degradation
//!
//! Where this backend cannot match makepad it says so on screen rather than quietly
//! drawing something else — see [`DEGRADED`]. Silently omitting a section a plan asked
//! for is the one failure mode a generated-UI system must never have: the card looks
//! complete and is missing a feature nobody can see is missing.

use crate::{Node, Val};
use std::collections::BTreeMap;
use std::sync::Mutex;

/// Sections that could not be rendered at full fidelity on this backend, collected
/// during a lowering so the caller can surface them. Read by the `diag` JNI entry.
pub static DEGRADED: Mutex<Vec<String>> = Mutex::new(Vec::new());

fn degrade(what: &str, why: &str) {
    DEGRADED.lock().unwrap().push(format!("{what}: {why}"));
}

// ------------------------------------------------------------------ theme ----

/// Everything the plan does NOT carry. Mirrors `plan/common.rs` in octos-one, so the
/// two backends agree on what a card looks like without the plan describing it.
mod theme {
    pub const DARK_BASE: u32 = 0xFF0B0B0D;
    pub const DARK_PANEL: u32 = 0xFF141821;
    pub const DARK_CARD: u32 = 0x1FFFFFFF;
    pub const HAIRLINE: u32 = 0x1AFFFFFF;
    pub const TEXT: u32 = 0xFFFFFFFF;
    pub const TEXT_SOFT: u32 = 0xE6FFFFFF;
    pub const TEXT_DIM: u32 = 0xB3FFFFFF;
    pub const TEXT_MUTED: u32 = 0x77FFFFFF;
    pub const ACCENT: u32 = 0xFFFF9F0A;
    pub const UP: u32 = 0xFF32D74B;

    /// The seven text ROLES, mapped to this backend's own type scale.
    ///
    /// octos-one resolves the same roles to Roboto weights and pixel sizes; here they
    /// resolve to Material 3 type tokens, so the card gets the platform's typography
    /// rather than a transplant of makepad's. That is the role abstraction earning its
    /// keep: the plan names a role, each backend answers in its own design language.
    pub const HERO: &str = "displayMedium";
    pub const TITLE: &str = "headlineSmall";
    pub const BODY: &str = "titleMedium";
    pub const STAT: &str = "bodyMedium";
    pub const ROW: &str = "bodyLarge";
    pub const CAPTION: &str = "labelMedium";
    pub const VALUE: &str = "headlineSmall";
}

// ------------------------------------------------------------- node sugar ----

fn node(kind: &str) -> Node {
    Node {
        kind: kind.to_string(),
        attrs: Vec::new(),
        children: Vec::new(),
    }
}

trait NodeExt {
    fn s(self, k: &str, v: &str) -> Self;
    fn n(self, k: &str, v: f64) -> Self;
    fn kid(self, c: Node) -> Self;
    fn kids(self, c: Vec<Node>) -> Self;
}

impl NodeExt for Node {
    fn s(mut self, k: &str, v: &str) -> Self {
        self.attrs.push((k.to_string(), Val::S(v.to_string())));
        self
    }
    fn n(mut self, k: &str, v: f64) -> Self {
        self.attrs.push((k.to_string(), Val::F(v)));
        self
    }
    fn kid(mut self, c: Node) -> Self {
        self.children.push(c);
        self
    }
    fn kids(mut self, c: Vec<Node>) -> Self {
        self.children.extend(c);
        self
    }
}


/// A card with a single column inside it.
///
/// `card` builds a MaterialCardView, which is a **FrameLayout** — several direct
/// children STACK on top of each other rather than flowing. The first version of this
/// lowering added rows directly to a card and every forecast row drew over the last,
/// which looked like a data bug and was a container bug. Every card gets exactly one
/// `col`.
fn card(pad: f64, radius: f64, bg: u32, kids: Vec<Node>) -> Node {
    node("card")
        .n("bg", bg as f64)
        .n("pad", pad)
        .n("radius", radius)
        .kid(node("col").n("spacing", 6.0).kids(kids))
}

/// A text role. Weight and size come from the theme, never from the plan — the same
/// contract as the makepad `TextHero`/`TextRow`/… prototypes.
fn txt(role: &str, s: &str, _color: u32) -> Node {
    // The colour argument is kept so both backends' builders read alike, but this one
    // deliberately ignores it: Material resolves text colour from the theme, so a card
    // stays legible in light and dark without the plan knowing which it is.
    node("text").s("text", s).s("variant", role)
}

// ------------------------------------------------------------------ fetch ----

static HTTP: Mutex<Option<BTreeMap<String, String>>> = Mutex::new(None);

/// GET with a per-URL cache, so N field reads against one endpoint cost ONE request.
///
/// The cache is keyed by exact URL, which is only a real dedup guarantee because every
/// builder below composes the same URL from the same helper. That is fragile by design
/// and worth replacing with a request broker if this grows.
fn fetch(url: &str) -> Option<String> {
    if let Some(v) = HTTP.lock().unwrap().as_ref().and_then(|m| m.get(url)).cloned() {
        return Some(v);
    }
    let body = ureq::get(url)
        .timeout(std::time::Duration::from_secs(12))
        .call()
        .ok()?
        .into_string()
        .ok()?;
    HTTP.lock()
        .unwrap()
        .get_or_insert_with(BTreeMap::new)
        .insert(url.to_string(), body.clone());
    Some(body)
}

fn json(url: &str) -> Option<serde_json::Value> {
    serde_json::from_str(&fetch(url)?).ok()
}

/// Walk a dotted path; a numeric segment indexes an array.
fn at<'a>(v: &'a serde_json::Value, path: &str) -> Option<&'a serde_json::Value> {
    let mut cur = v;
    for seg in path.split('.') {
        cur = match seg.parse::<usize>() {
            Ok(i) => cur.get(i)?,
            Err(_) => cur.get(seg)?,
        };
    }
    Some(cur)
}

fn pct_encode(s: &str) -> String {
    s.bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => {
                (b as char).to_string()
            }
            _ => format!("%{b:02X}"),
        })
        .collect()
}

/// A fact about a place NAME. The plan never carries coordinates, so this is the only
/// way they enter — and the lookup LANGUAGE follows the script of the query, because
/// open-meteo indexes per language and "上海" with `language=en` returns nothing at all.
fn geocode(name: &str, field: &str) -> Option<String> {
    let cjk = name.chars().any(|c| {
        matches!(c,
            '\u{3040}'..='\u{30FF}' | '\u{3400}'..='\u{4DBF}'
            | '\u{4E00}'..='\u{9FFF}' | '\u{F900}'..='\u{FAFF}')
    });
    let lang = if cjk { "zh" } else { "en" };
    let url = format!(
        "https://geocoding-api.open-meteo.com/v1/search?name={}&count=1&language={lang}&format=json",
        pct_encode(name.trim())
    );
    let v = json(&url)?;
    let key = match field {
        "lat" => "results.0.latitude",
        "lon" => "results.0.longitude",
        "name" => "results.0.name",
        _ => return None,
    };
    let x = at(&v, key)?;
    Some(
        x.as_str()
            .map(str::to_string)
            .unwrap_or_else(|| x.to_string()),
    )
}

fn wx_url(lat: f64, lon: f64) -> String {
    format!(
        "https://api.open-meteo.com/v1/forecast?latitude={lat:.4}&longitude={lon:.4}\
&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m\
&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max\
&timezone=auto&forecast_days=7"
    )
}

/// A weather field as display text. Temperature, UV and wind round to whole numbers —
/// the same scoping as `sys.weather`, so "8.45" never reaches a tile.
fn weather_str(lat: f64, lon: f64, path: &str) -> String {
    let Some(v) = json(&wx_url(lat, lon)).as_ref().and_then(|j| at(j, path).cloned()) else {
        return "—".to_string();
    };
    if let Some(s) = v.as_str() {
        // open-meteo ISO datetime → HH:MM (sunrise/sunset).
        if s.len() >= 16 && s.as_bytes().get(10) == Some(&b'T') {
            return s[11..16].to_string();
        }
        return s.to_string();
    }
    let Some(n) = v.as_f64() else {
        return "—".to_string();
    };
    let rounds = path.contains("temperature") || path.contains("uv_index") || path.contains("wind_speed");
    if rounds {
        format!("{}", n.round() as i64)
    } else {
        format!("{n}")
    }
}

fn weather_num(lat: f64, lon: f64, path: &str) -> Option<f64> {
    at(&json(&wx_url(lat, lon))?, path)?.as_f64()
}

/// The week's lowest low or highest high. A plan cannot state this — the values are a
/// live fetch, so a model asked for the range guesses at numbers it has never seen and
/// clamps every gradient to one end.
fn week_extent(lat: f64, lon: f64, path: &str, want_max: bool) -> Option<f64> {
    let v = json(&wx_url(lat, lon))?;
    let mut acc: Option<f64> = None;
    for i in 0..7 {
        let Some(n) = at(&v, &format!("{path}.{i}")).and_then(|x| x.as_f64()) else {
            continue;
        };
        acc = Some(match acc {
            None => n,
            Some(a) if want_max => a.max(n),
            Some(a) => a.min(n),
        });
    }
    acc
}

/// WMO code → the WeatherIcon index this backend's `WeatherIconView` draws.
fn weather_cond(lat: f64, lon: f64, path: &str) -> f64 {
    let code = weather_num(lat, lon, path).map(|n| n as i64);
    match code {
        Some(0) => 0.0,
        Some(1) | Some(2) => 1.0,
        Some(3) => 2.0,
        Some(45) | Some(48) => 7.0,
        Some(51..=57) | Some(61..=67) | Some(80..=82) => 3.0,
        Some(71..=77) | Some(85) | Some(86) => 5.0,
        Some(95..=99) => 4.0,
        _ => 1.0,
    }
}

/// The condition as words. Icon and word come from the SAME live code, so they cannot
/// disagree with each other or with the sky.
fn weather_word(lat: f64, lon: f64, path: &str, zh: bool) -> String {
    let code = weather_num(lat, lon, path).map(|n| n as i64);
    let (en, cn) = match code {
        Some(0) => ("Clear", "晴"),
        Some(1) => ("Mainly Clear", "晴间多云"),
        Some(2) => ("Partly Cloudy", "局部多云"),
        Some(3) => ("Overcast", "阴"),
        Some(45) | Some(48) => ("Fog", "雾"),
        Some(51..=57) => ("Drizzle", "小雨"),
        Some(61..=67) => ("Rain", "雨"),
        Some(71..=77) => ("Snow", "雪"),
        Some(80..=82) => ("Showers", "阵雨"),
        Some(85) | Some(86) => ("Snow Showers", "阵雪"),
        Some(95..=99) => ("Thunderstorm", "雷暴"),
        _ => ("—", "—"),
    };
    (if zh { cn } else { en }).to_string()
}

const DAY_EN: [&str; 7] = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const DAY_ZH: [&str; 7] = ["周日", "周一", "周二", "周三", "周四", "周五", "周六"];

fn days_from_civil(y: i64, m: u64, d: u64) -> i64 {
    let y = if m <= 2 { y - 1 } else { y };
    let era = if y >= 0 { y } else { y - 399 } / 400;
    let yoe = (y - era * 400) as u64;
    let mp = if m > 2 { m - 3 } else { m + 9 };
    let doy = (153 * mp + 2) / 5 + d - 1;
    let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    era * 146_097 + doe as i64 - 719_468
}

/// The weekday label for forecast row `n`, read from the FORECAST'S own dates.
///
/// Not from this device's clock: the labels belong to the place being shown. Rendering
/// a Kyoto forecast from a phone on US time, Kyoto is already a day ahead — and the
/// model got this wrong in every card it ever wrote, invisibly, because a wrong weekday
/// looks exactly like a right one.
fn day_name(lat: f64, lon: f64, n: usize, zh: bool) -> String {
    if n == 0 {
        return (if zh { "今天" } else { "Today" }).to_string();
    }
    let fallback = if zh { "—" } else { "—" };
    let Some(v) = json(&wx_url(lat, lon)) else {
        return fallback.to_string();
    };
    let Some(date) = at(&v, &format!("daily.time.{n}")).and_then(|x| x.as_str()) else {
        return fallback.to_string();
    };
    let mut it = date.split('-');
    let (Some(y), Some(m), Some(d)) = (it.next(), it.next(), it.next()) else {
        return fallback.to_string();
    };
    let (Ok(y), Ok(m), Ok(d)) = (y.parse::<i64>(), m.parse::<u64>(), d.parse::<u64>()) else {
        return fallback.to_string();
    };
    let wd = (((days_from_civil(y, m, d) + 4) % 7 + 7) % 7) as usize;
    (if zh { DAY_ZH[wd] } else { DAY_EN[wd] }).to_string()
}

/// A Hacker News front-page field. Mirrors `sys.news`.
fn news_field(i: usize, key: &str) -> String {
    let ids = json("https://hacker-news.firebaseio.com/v0/topstories.json");
    let Some(id) = ids.as_ref().and_then(|v| v.get(i)).and_then(|v| v.as_i64()) else {
        return "—".to_string();
    };
    let url = format!("https://hacker-news.firebaseio.com/v0/item/{id}.json");
    let Some(item) = json(&url) else {
        return "—".to_string();
    };
    let k = match key {
        "title" => "title",
        "author" => "by",
        "points" => "score",
        "comments" => "descendants",
        "url" => "url",
        _ => return "—".to_string(),
    };
    match item.get(k) {
        Some(v) if v.is_string() => v.as_str().unwrap_or("—").to_string(),
        Some(v) if v.is_number() => v.to_string(),
        _ => "—".to_string(),
    }
}

// ------------------------------------------------------------------ plans ----

/// Lower a plan to a node tree, or return an error node explaining why not.
///
/// A rejection renders VISIBLY. Returning nothing would leave a blank screen, which is
/// indistinguishable from a network failure or a crash.
pub fn lower(plan_json: &str) -> Node {
    DEGRADED.lock().unwrap().clear();
    let v: serde_json::Value = match serde_json::from_str(plan_json) {
        Ok(v) => v,
        Err(e) => return error_card(&format!("plan is not valid JSON: {e}")),
    };
    let kind = v.get("plan").and_then(|k| k.as_str()).unwrap_or("");
    let zh = v
        .get("locale")
        .and_then(|l| l.as_str())
        .unwrap_or("en")
        .starts_with("zh");
    let sections = v.get("sections").and_then(|s| s.as_array());
    let Some(sections) = sections else {
        return error_card("plan has no sections");
    };

    let body = match kind {
        "weather" => {
            let place = v
                .get("place")
                .and_then(|p| p.get("query"))
                .and_then(|q| q.as_str())
                .unwrap_or("");
            if place.is_empty() {
                return error_card("weather plan has no place.query");
            }
            let (Some(lat), Some(lon)) = (
                geocode(place, "lat").and_then(|s| s.parse::<f64>().ok()),
                geocode(place, "lon").and_then(|s| s.parse::<f64>().ok()),
            ) else {
                return error_card(&format!("could not resolve place {place:?}"));
            };
            weather_body(sections, place, lat, lon, zh)
        }
        "news" => news_body(sections, zh),
        "stock" => {
            degrade(
                "stock",
                "no market data source on this backend — needs the sys.stock/sys.movers equivalents",
            );
            vec![unsupported_card(if zh {
                "此后端暂不支持股票数据"
            } else {
                "Market data unavailable on this backend"
            })]
        }
        other => return error_card(&format!("unknown plan kind {other:?}")),
    };

    node("col")
        .n("bg", theme::DARK_BASE as f64)
        .n("pad", 18.0)
        .n("spacing", 12.0)
        .kids(body)
}

fn error_card(msg: &str) -> Node {
    node("col")
        .n("bg", theme::DARK_BASE as f64)
        .n("pad", 20.0)
        .kid(txt(theme::TITLE, "Plan rejected", 0xFFFF453A))
        .kid(txt(theme::STAT, msg, theme::TEXT_DIM))
}

fn unsupported_card(msg: &str) -> Node {
    card(16.0, 14.0, theme::DARK_PANEL, vec![txt(theme::BODY, msg, theme::TEXT_DIM)])
}

fn weather_body(
    sections: &[serde_json::Value],
    place: &str,
    lat: f64,
    lon: f64,
    zh: bool,
) -> Vec<Node> {
    let mut out = Vec::new();
    for sec in sections {
        let block = sec.get("block").and_then(|b| b.as_str()).unwrap_or("");
        let args = sec.get("args");
        match block {
            "CurrentConditions" => {
                let name = geocode(place, "name").unwrap_or_else(|| place.to_string());
                out.push(
                    node("col")
                        .n("spacing", 2.0)
                        .kid(txt(theme::TITLE, &name, theme::TEXT_SOFT))
                        .kid(txt(
                            theme::HERO,
                            &format!("{}°", weather_str(lat, lon, "current.temperature_2m")),
                            theme::TEXT,
                        ))
                        .kid(
                            node("row")
                                .n("spacing", 8.0)
                                .kid(
                                    node("weathericon")
                                        .n("cond", weather_cond(lat, lon, "current.weather_code"))
                                        .n("w", 44.0)
                                        .n("h", 44.0),
                                )
                                .kid(txt(
                                    theme::BODY,
                                    &weather_word(lat, lon, "current.weather_code", zh),
                                    theme::TEXT_SOFT,
                                )),
                        )
                        .kid(txt(
                            theme::STAT,
                            &format!(
                                "↑{}°   ↓{}°   ≈{}°",
                                weather_str(lat, lon, "daily.temperature_2m_max.0"),
                                weather_str(lat, lon, "daily.temperature_2m_min.0"),
                                weather_str(lat, lon, "current.apparent_temperature")
                            ),
                            theme::TEXT_DIM,
                        )),
                );
            }
            "Forecast" => {
                let days = args
                    .and_then(|a| a.get("days"))
                    .and_then(|d| d.as_u64())
                    .unwrap_or(7)
                    .clamp(1, 7) as usize;
                let wmin = week_extent(lat, lon, "daily.temperature_2m_min", false).unwrap_or(0.0);
                let wmax = week_extent(lat, lon, "daily.temperature_2m_max", true).unwrap_or(30.0);
                let mut rows = Vec::new();
                for d in 0..days {
                    let lo = weather_num(lat, lon, &format!("daily.temperature_2m_min.{d}"))
                        .unwrap_or(0.0);
                    let hi = weather_num(lat, lon, &format!("daily.temperature_2m_max.{d}"))
                        .unwrap_or(0.0);
                    rows.push(
                        node("row")
                            .n("h", 44.0)
                            .n("spacing", 8.0)
                            .kid(txt(theme::ROW, &day_name(lat, lon, d, zh), theme::TEXT_SOFT).n("w", 74.0))
                            .kid(
                                node("weathericon")
                                    .n("cond", weather_cond(lat, lon, &format!("daily.weather_code.{d}")))
                                    .n("w", 26.0)
                                    .n("h", 26.0),
                            )
                            .kid(txt(theme::ROW, &format!("{}°", lo.round() as i64), theme::TEXT_MUTED).n("w", 40.0))
                            // The gradient bar is a shader on makepad. Here it is a solid
                            // bar coloured by the day's high — announced, not hidden.
                            .kid(
                                node("box")
                                    .n("w", 84.0)
                                    .n("h", 6.0)
                                    .n("radius", 3.0)
                                    .n("bg", bar_color(hi, wmin, wmax) as f64),
                            )
                            .kid(txt(theme::ROW, &format!("{}°", hi.round() as i64), theme::TEXT).n("w", 40.0)),
                    );
                }
                degrade(
                    "TempBar",
                    "no shader surface here — a solid bar coloured at the day's high, \
                     not a cool→warm gradient",
                );
                out.push(
                    card(14.0, 18.0, theme::DARK_PANEL, rows),
                );
            }
            "AirQualityField" => {
                degrade(
                    "AirQualityField",
                    "no contour surface — the AQI number only, no field map",
                );
                let aqi = json(&format!(
                    "https://air-quality-api.open-meteo.com/v1/air-quality?latitude={lat:.4}\
&longitude={lon:.4}&current=us_aqi&timezone=auto"
                ))
                .as_ref()
                .and_then(|j| at(j, "current.us_aqi").cloned())
                .and_then(|v| v.as_f64());
                out.push(
                    card(14.0, 18.0, theme::DARK_PANEL, vec![
                        txt(theme::CAPTION, if zh { "空气质量" } else { "AIR QUALITY" }, theme::TEXT_MUTED),
                        txt(
                            theme::VALUE,
                            &aqi.map(|n| format!("{}", n.round() as i64))
                                .unwrap_or_else(|| "—".to_string()),
                            theme::TEXT,
                        ),
                    ]),
                );
            }
            "SunMoon" => {
                degrade(
                    "SunMoon",
                    "no SunArc/MoonPhase shaders — sunrise and sunset times only",
                );
                out.push(
                    card(14.0, 18.0, theme::DARK_PANEL, vec![
                        txt(theme::CAPTION, if zh { "日出 / 日落" } else { "SUNRISE / SUNSET" }, theme::TEXT_MUTED),
                        txt(
                            theme::BODY,
                            &format!(
                                "{}   {}",
                                weather_str(lat, lon, "daily.sunrise.0"),
                                weather_str(lat, lon, "daily.sunset.0")
                            ),
                            theme::TEXT,
                        ),
                    ]),
                );
            }
            "Details" => {
                let tiles: Vec<String> = args
                    .and_then(|a| a.get("tiles"))
                    .and_then(|t| t.as_array())
                    .map(|a| {
                        a.iter()
                            .filter_map(|v| v.as_str().map(str::to_string))
                            .collect()
                    })
                    .unwrap_or_default();
                let mut cells = Vec::new();
                for k in &tiles {
                    let (cap, value) = match k.as_str() {
                        "uv" => (
                            if zh { "紫外线" } else { "UV INDEX" },
                            weather_str(lat, lon, "daily.uv_index_max.0"),
                        ),
                        "humidity" => (
                            if zh { "湿度" } else { "HUMIDITY" },
                            format!("{}%", weather_str(lat, lon, "current.relative_humidity_2m")),
                        ),
                        "wind" => (
                            if zh { "风速" } else { "WIND" },
                            format!("{} km/h", weather_str(lat, lon, "current.wind_speed_10m")),
                        ),
                        // `aqi` is served by AirQualityField above on this backend.
                        _ => continue,
                    };
                    cells.push(
                        card(12.0, 14.0, theme::DARK_CARD, vec![
                            txt(theme::CAPTION, cap, theme::TEXT_MUTED),
                            txt(theme::VALUE, &value, theme::TEXT),
                        ])
                        .n("w", 150.0),
                    );
                }
                for pair in cells.chunks(2) {
                    out.push(node("row").n("spacing", 10.0).kids(pair.to_vec()));
                }
            }
            other => out.push(unsupported_card(&format!("unknown block {other:?}"))),
        }
    }
    out
}

/// Cool→warm by position in the week. Keyed to POSITION, not absolute degrees: a week
/// spanning 28–39 °C keyed absolutely sits entirely in the warm half, so every bar
/// draws the same colour.
fn bar_color(t: f64, wmin: f64, wmax: f64) -> u32 {
    let span = (wmax - wmin).max(0.001);
    let p = ((t - wmin) / span).clamp(0.0, 1.0);
    const STOPS: [(u8, u8, u8); 6] = [
        (30, 92, 255),
        (0, 217, 192),
        (63, 191, 82),
        (255, 196, 0),
        (255, 138, 0),
        (224, 27, 27),
    ];
    let x = p * (STOPS.len() - 1) as f64;
    let i = (x.floor() as usize).min(STOPS.len() - 2);
    let f = x - i as f64;
    let (r0, g0, b0) = STOPS[i];
    let (r1, g1, b1) = STOPS[i + 1];
    let lerp = |a: u8, b: u8| (a as f64 + (b as f64 - a as f64) * f).round() as u32;
    0xFF00_0000 | (lerp(r0, r1) << 16) | (lerp(g0, g1) << 8) | lerp(b0, b1)
}

fn news_body(sections: &[serde_json::Value], zh: bool) -> Vec<Node> {
    let mut out = Vec::new();
    for sec in sections {
        let block = sec.get("block").and_then(|b| b.as_str()).unwrap_or("");
        let args = sec.get("args");
        let arg_s = |k: &str| {
            args.and_then(|a| a.get(k))
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string()
        };
        match block {
            "Masthead" => {
                let label = if arg_s("label").is_empty() {
                    (if zh { "头条" } else { "TOP STORIES" }).to_string()
                } else {
                    arg_s("label")
                };
                let title = if arg_s("title").is_empty() {
                    (if zh { "新闻" } else { "News" }).to_string()
                } else {
                    arg_s("title")
                };
                out.push(
                    node("col")
                        .n("spacing", 2.0)
                        .kid(txt(theme::CAPTION, &label, theme::ACCENT))
                        .kid(txt(theme::HERO, &title, theme::TEXT)),
                );
            }
            "LeadStory" => {
                out.push(
                    card(16.0, 14.0, theme::DARK_CARD, vec![
                        txt(theme::CAPTION, if zh { "焦点" } else { "LEAD" }, theme::ACCENT),
                        txt(theme::BODY, &news_field(0, "title"), theme::TEXT),
                        txt(
                            theme::CAPTION,
                            &format!(
                                "{} {} · {} {} · {} {}",
                                news_field(0, "points"),
                                if zh { "分" } else { "pts" },
                                news_field(0, "comments"),
                                if zh { "评论" } else { "comments" },
                                if zh { "作者" } else { "by" },
                                news_field(0, "author")
                            ),
                            theme::TEXT_MUTED,
                        ),
                    ]),
                );
            }
            "StoryFeed" => {
                let n = args
                    .and_then(|a| a.get("count"))
                    .and_then(|c| c.as_u64())
                    .unwrap_or(7)
                    .clamp(1, 20) as usize;
                let mut rows = Vec::new();
                for r in 1..=n {
                    rows.push(
                        node("row")
                            .n("spacing", 10.0)
                            .kid(txt(theme::ROW, &r.to_string(), theme::ACCENT).n("w", 26.0))
                            .kid(
                                node("col")
                                    .n("spacing", 2.0)
                                    .kid(txt(theme::ROW, &news_field(r, "title"), theme::TEXT_SOFT))
                                    .kid(txt(
                                        theme::CAPTION,
                                        &format!(
                                            "{} {} · {}",
                                            news_field(r, "points"),
                                            if zh { "分" } else { "pts" },
                                            news_field(r, "author")
                                        ),
                                        theme::TEXT_MUTED,
                                    )),
                            ),
                    );
                    rows.push(node("divider").n("color", theme::HAIRLINE as f64));
                }
                let label = if arg_s("label").is_empty() {
                    (if zh { "最新" } else { "LATEST" }).to_string()
                } else {
                    arg_s("label")
                };
                out.push(txt(theme::CAPTION, &label, theme::ACCENT));
                out.push(
                    card(14.0, 14.0, theme::DARK_PANEL, rows),
                );
            }
            other => out.push(unsupported_card(&format!("unknown block {other:?}"))),
        }
    }
    out
}
