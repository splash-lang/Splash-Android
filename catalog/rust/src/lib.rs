//! Splash DSL -> Material Components Android.
//!
//! The catalog's screens are authored in the Splash DSL, evaluated by the
//! makepad-script VM (via splash-render's re-export), walked into a generic
//! node tree, and serialized once per render into a flat buffer that Java turns
//! into real `com.google.android.material.*` views.
//!
//! Java owns every View; Rust owns ids and the card state.

use splash_render::makepad_script as ms;
use ms::apply::*;
use ms::array::ScriptArrayStorage;
use ms::makepad_live_id::*;
use ms::traits::*;
use ms::*;

use jni::objects::{JClass, JObject, JString};
use jni::sys::jint;
use jni::JNIEnv;
use std::collections::BTreeMap;
use std::sync::Mutex;

mod plan;
mod screens;

// ---------------------------------------------------------------- state ----

static STATE: Mutex<Option<BTreeMap<String, String>>> = Mutex::new(None);
static BUF: Mutex<Option<Vec<u8>>> = Mutex::new(None);
static DIAG: Mutex<Option<String>> = Mutex::new(None);

fn with_state<R>(f: impl FnOnce(&mut BTreeMap<String, String>) -> R) -> R {
    let mut g = STATE.lock().unwrap();
    let m = g.get_or_insert_with(BTreeMap::new);
    f(m)
}

fn state_get(k: &str) -> String {
    with_state(|m| m.get(k).cloned().unwrap_or_default())
}

// ----------------------------------------------------------------- node ----

#[derive(Debug, Clone)]
pub(crate) enum Val {
    F(f64),
    S(String),
}

#[derive(Debug, Clone)]
pub(crate) struct Node {
    pub kind: String,
    pub attrs: Vec<(String, Val)>,
    pub children: Vec<Node>,
}

/// Every attribute name the DSL may use. LiveId keys are one-way hashes, so the
/// vocabulary is explicit rather than discovered — the same choice splash-render
/// makes, just much wider because Material needs it.
const ATTRS_S: &[(&str, LiveId)] = &[
    ("text", live_id!(text)),
    ("label", live_id!(label)),
    ("title", live_id!(title)),
    ("subtitle", live_id!(subtitle)),
    ("supporting", live_id!(supporting)),
    ("overline", live_id!(overline)),
    ("hint", live_id!(hint)),
    ("helper", live_id!(helper)),
    ("error", live_id!(error)),
    ("placeholder", live_id!(placeholder)),
    ("variant", live_id!(variant)),
    ("icon", live_id!(icon)),
    ("icon2", live_id!(icon2)),
    ("items", live_id!(items)),
    ("route", live_id!(route)),
    ("key", live_id!(key)),
    ("action", live_id!(action)),
    ("src", live_id!(src)),
    ("badge", live_id!(badge)),
    ("group", live_id!(group)),
    ("unit", live_id!(unit)),
];

const ATTRS_N: &[(&str, LiveId)] = &[
    ("w", live_id!(w)),
    ("h", live_id!(h)),
    ("size", live_id!(size)),
    ("weight", live_id!(weight)),
    ("color", live_id!(color)),
    ("bg", live_id!(bg)),
    ("stroke", live_id!(stroke)),
    ("radius", live_id!(radius)),
    ("pad", live_id!(pad)),
    ("padx", live_id!(padx)),
    ("pady", live_id!(pady)),
    ("margin", live_id!(margin)),
    ("marginx", live_id!(marginx)),
    ("marginy", live_id!(marginy)),
    ("spacing", live_id!(spacing)),
    ("elevation", live_id!(elevation)),
    ("gravity", live_id!(gravity)),
    ("align", live_id!(align)),
    ("fill", live_id!(fill)),
    ("value", live_id!(value)),
    ("value2", live_id!(value2)),
    ("total", live_id!(total)),
    ("min", live_id!(min)),
    ("max", live_id!(max)),
    ("step", live_id!(step)),
    ("count", live_id!(count)),
    ("on", live_id!(on)),
    ("enabled", live_id!(enabled)),
    ("selected", live_id!(selected)),
    ("indeterminate", live_id!(indeterminate)),
    ("tap", live_id!(tap)),
    ("scrollable", live_id!(scrollable)),
    ("lines", live_id!(lines)),
    ("checkable", live_id!(checkable)),
    ("closeable", live_id!(closeable)),
];

fn prop(vm: &mut ScriptVm, obj: ScriptValue, key: LiveId) -> Option<ScriptValue> {
    vm.bx.heap.value_for_apply(obj, key.into(), &Apply::Eval)
}

fn sprop(vm: &mut ScriptVm, obj: ScriptValue, key: LiveId) -> Option<String> {
    let v = prop(vm, obj, key)?;
    if v.is_nil() {
        return None;
    }
    vm.string_with(v, |_v, s| s.to_string())
}

fn nprop(vm: &mut ScriptVm, obj: ScriptValue, key: LiveId) -> Option<f64> {
    let v = prop(vm, obj, key)?;
    if v.is_nil() {
        return None;
    }
    let mut out: f64 = 0.0;
    <f64 as ScriptApply>::script_apply(&mut out, vm, &Apply::Eval, &mut Scope::default(), v);
    Some(out)
}

fn children_of(vm: &mut ScriptVm, value: ScriptValue) -> Vec<ScriptValue> {
    let Some(c) = prop(vm, value, live_id!(c)) else {
        return Vec::new();
    };
    let Some(arr) = c.as_array() else {
        return Vec::new();
    };
    match vm.bx.heap.array_storage(arr) {
        ScriptArrayStorage::ScriptValue(v) => v.iter().copied().collect(),
        _ => Vec::new(),
    }
}

fn walk(vm: &mut ScriptVm, value: ScriptValue, depth: usize) -> Option<Node> {
    if depth > 48 {
        return None;
    }
    let kind = sprop(vm, value, live_id!(t))?;
    let mut attrs = Vec::new();
    for (name, id) in ATTRS_S {
        if let Some(s) = sprop(vm, value, *id) {
            attrs.push((name.to_string(), Val::S(s)));
        }
    }
    for (name, id) in ATTRS_N {
        if let Some(n) = nprop(vm, value, *id) {
            attrs.push((name.to_string(), Val::F(n)));
        }
    }
    let mut children = Vec::new();
    for kid in children_of(vm, value) {
        if let Some(n) = walk(vm, kid, depth + 1) {
            children.push(n);
        }
    }
    Some(Node {
        kind,
        attrs,
        children,
    })
}

// ------------------------------------------------------------- encoding ----

const MAGIC: u32 = 0x5350_4332;
const T_F64: u8 = 0;
const T_STR: u8 = 1;

struct Enc {
    out: Vec<u8>,
    blob: Vec<u8>,
    n: u32,
    next: u32,
}

impl Enc {
    fn s(&mut self, v: &str) -> (u32, u32) {
        let o = self.blob.len() as u32;
        self.blob.extend_from_slice(v.as_bytes());
        (o, v.len() as u32)
    }
    fn walk(&mut self, n: &Node, parent: u32) {
        let id = self.next;
        self.next += 1;
        let (ko, kl) = self.s(&n.kind);
        let mut body = Vec::new();
        let mut cnt = 0u32;
        for (name, v) in &n.attrs {
            let (no, nl) = self.s(name);
            body.extend_from_slice(&no.to_le_bytes());
            body.extend_from_slice(&nl.to_le_bytes());
            match v {
                Val::F(f) => {
                    body.push(T_F64);
                    body.extend_from_slice(&[0u8; 3]);
                    body.extend_from_slice(&f.to_bits().to_le_bytes());
                }
                Val::S(s) => {
                    let (o, l) = self.s(s);
                    body.push(T_STR);
                    body.extend_from_slice(&[0u8; 3]);
                    body.extend_from_slice(&o.to_le_bytes());
                    body.extend_from_slice(&l.to_le_bytes());
                }
            }
            cnt += 1;
        }
        self.out.extend_from_slice(&id.to_le_bytes());
        self.out.extend_from_slice(&parent.to_le_bytes());
        self.out.extend_from_slice(&ko.to_le_bytes());
        self.out.extend_from_slice(&kl.to_le_bytes());
        self.out.extend_from_slice(&cnt.to_le_bytes());
        self.out.extend_from_slice(&body);
        self.n += 1;
        for c in &n.children {
            self.walk(c, id);
        }
    }
}

fn encode(root: &Node) -> Vec<u8> {
    let mut e = Enc {
        out: Vec::new(),
        blob: Vec::new(),
        n: 0,
        next: 0,
    };
    e.walk(root, u32::MAX);
    let mut v = Vec::with_capacity(12 + e.out.len() + e.blob.len());
    v.extend_from_slice(&MAGIC.to_le_bytes());
    v.extend_from_slice(&e.n.to_le_bytes());
    v.extend_from_slice(&(e.blob.len() as u32).to_le_bytes());
    v.extend_from_slice(&e.out);
    v.extend_from_slice(&e.blob);
    v
}

// ------------------------------------------------------------ rendering ----

fn esc(s: &str) -> String {
    s.replace('\\', "\\\\").replace('"', "\\\"").replace('\n', "\\n")
}

/// Build the source for `route`: a state preamble (so the DSL reads live state
/// with `st.<key>`) plus the shared kit and the screen body.
fn source_for(route: &str) -> String {
    // State is read through injected host functions rather than a generated
    // `st` object: a missing key on a plain object is a hard VM error, while a
    // host function can simply return the default.
    format!(
        "let route = \"{}\"\n{}{}",
        esc(route),
        screens::KIT,
        screens::body(route)
    )
}

/// `S(key)` -> the state string ("" when unset); `N(key, dflt)` -> its number.
fn register_state(vm: &mut ScriptVm) {
    let f_s = splash_render::add_global_fn(vm, &[(live_id!(k), ScriptValue::NIL)], |vm, a| {
        let k = splash_render::string_prop(vm, a, live_id!(k)).unwrap_or_default();
        let v = state_get(&k);
        vm.bx.heap.new_string_from_str(&v)
    });
    vm.set_injected_global(live_id!(S), f_s);

    let f_n = splash_render::add_global_fn(
        vm,
        &[(live_id!(k), ScriptValue::NIL), (live_id!(d), ScriptValue::NIL)],
        |vm, a| {
            let k = splash_render::string_prop(vm, a, live_id!(k)).unwrap_or_default();
            let d = splash_render::num_prop(vm, a, live_id!(d)).unwrap_or(0.0);
            let v = state_get(&k);
            ScriptValue::from_f64(v.trim().parse::<f64>().unwrap_or(d))
        },
    );
    vm.set_injected_global(live_id!(N), f_n);
}

fn render(route: &str) -> Vec<u8> {
    let src = source_for(route);
    let mut std_slot = 0;
    let mut host = 0;
    let vm = &mut ScriptVm {
        host: &mut host,
        std: &mut std_slot,
        bx: Box::new(ScriptVmBase::new()),
    };
    register_state(vm);
    let value = vm.eval(ScriptMod {
        cargo_manifest_path: String::new(),
        module_path: String::from("catalog"),
        file: format!("{route}.splash"),
        line: 0,
        column: 0,
        code: src.clone(),
        values: Vec::new(),
    });
    if value.is_err() || value.is_nil() {
        let mut d = format!("route {route}: evaluated to nil/err");
        if let Ok(r) = splash_core_check(&src) {
            d.push_str(&r);
        }
        *DIAG.lock().unwrap() = Some(d);
        return Vec::new();
    }
    match walk(vm, value, 0) {
        Some(tree) => {
            *DIAG.lock().unwrap() = Some(format!("route {route}: ok"));
            encode(&tree)
        }
        None => {
            *DIAG.lock().unwrap() = Some(format!("route {route}: root has no `t`"));
            Vec::new()
        }
    }
}

fn splash_core_check(_src: &str) -> Result<String, ()> {
    Err(())
}

// ------------------------------------------------------------------ JNI ----

fn jstr(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(|v| v.into()).unwrap_or_default()
}

#[no_mangle]
pub extern "system" fn Java_dev_splash_catalog_Native_render<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    route: JString<'l>,
) -> JObject<'l> {
    let r = jstr(&mut env, &route);
    let buf = render(&r);
    if buf.is_empty() {
        return JObject::null();
    }
    let mut g = BUF.lock().unwrap();
    *g = Some(buf);
    let b = g.as_ref().unwrap();
    match unsafe { env.new_direct_byte_buffer(b.as_ptr() as *mut u8, b.len()) } {
        Ok(v) => v.into(),
        Err(_) => JObject::null(),
    }
}


/// Render a semantic PLAN (typed JSON from the generating model) to the flat node
/// buffer. The DSL is not involved on this path at all: `plan::lower` builds the node
/// tree directly from the plan.
///
/// This is the portability seam. octos-one lowers the SAME plan JSON to makepad Splash
/// DSL; this lowers it to native Android views. If one plan drives both, the plan is
/// genuinely backend-agnostic and the per-backend cost is a lowering table.
///
/// Never returns null for a bad plan — `plan::lower` renders a visible rejection
/// instead, because a blank screen is indistinguishable from a crash.
#[no_mangle]
pub extern "system" fn Java_dev_splash_catalog_Native_renderPlan<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    plan: JString<'l>,
) -> JObject<'l> {
    let json = jstr(&mut env, &plan);
    let root = plan::lower(&json);
    let buf = encode(&root);
    let degraded = plan::DEGRADED.lock().unwrap().join(" | ");
    if !degraded.is_empty() {
        *DIAG.lock().unwrap() = Some(format!("DEGRADED {degraded}"));
    }
    let mut g = BUF.lock().unwrap();
    *g = Some(buf);
    let b = g.as_ref().unwrap();
    match unsafe { env.new_direct_byte_buffer(b.as_ptr() as *mut u8, b.len()) } {
        Ok(v) => v.into(),
        Err(_) => JObject::null(),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_splash_catalog_Native_set<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    k: JString<'l>,
    v: JString<'l>,
) {
    let k = jstr(&mut env, &k);
    let v = jstr(&mut env, &v);
    with_state(|m| {
        m.insert(k, v);
    });
}

#[no_mangle]
pub extern "system" fn Java_dev_splash_catalog_Native_get<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
    k: JString<'l>,
) -> JObject<'l> {
    let k = jstr(&mut env, &k);
    let v = state_get(&k);
    match env.new_string(v) {
        Ok(s) => s.into(),
        Err(_) => JObject::null(),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_splash_catalog_Native_diag<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
) -> JObject<'l> {
    let d = DIAG.lock().unwrap().clone().unwrap_or_default();
    match env.new_string(d) {
        Ok(s) => s.into(),
        Err(_) => JObject::null(),
    }
}

/// Number of routes, so Java can build the table of contents from Rust's list.
#[no_mangle]
pub extern "system" fn Java_dev_splash_catalog_Native_routeCount<'l>(
    _env: JNIEnv<'l>,
    _c: JClass<'l>,
) -> jint {
    screens::ROUTES.len() as jint
}

#[no_mangle]
pub extern "system" fn Java_dev_splash_catalog_Native_routeAt<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
    i: jint,
) -> JObject<'l> {
    let r = screens::ROUTES
        .get(i as usize)
        .map(|(a, b)| format!("{a}|{b}"))
        .unwrap_or_default();
    match env.new_string(r) {
        Ok(s) => s.into(),
        Err(_) => JObject::null(),
    }
}

// ------------------------------------------------------------- testing ----

/// Host-side introspection for the example probe: evaluate arbitrary source
/// through the SAME walker the JNI path uses.
pub fn debug_walk(src: &str) -> String {
    let mut std_slot = 0;
    let mut host = 0;
    let vm = &mut ScriptVm {
        host: &mut host,
        std: &mut std_slot,
        bx: Box::new(ScriptVmBase::new()),
    };
    let value = vm.eval(ScriptMod {
        cargo_manifest_path: String::new(),
        module_path: String::from("t"),
        file: String::from("t.splash"),
        line: 0,
        column: 0,
        code: src.to_string(),
        values: Vec::new(),
    });
    if value.is_err() { return "ERR".into(); }
    if value.is_nil() { return "NIL".into(); }
    match walk(vm, value, 0) {
        Some(n) => format!("OK kind={} kids={}", n.kind, n.children.len()),
        None => "NO_TAG".into(),
    }
}

/// Render a named route host-side.
pub fn debug_route(route: &str) -> String {
    let b = render(route);
    format!("{} bytes; {}", b.len(), DIAG.lock().unwrap().clone().unwrap_or_default())
}

/// The route table, for host-side probing.
pub fn routes() -> &'static [(&'static str, &'static str)] { screens::ROUTES }
