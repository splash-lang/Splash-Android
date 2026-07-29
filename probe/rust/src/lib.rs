//! Splash DSL -> native android.widget.* validation probe.
//!
//! Validates the design in docs/SPLASH-ANDROID-NATIVE-WIDGETS.md §7c:
//!   * the REAL `splash-render` crate (VM only, no makepad renderer) runs on
//!     the device and evaluates Splash DSL into a `UiNode` tree;
//!   * the tree is serialized ONCE into a flat binary buffer;
//!   * one JNI crossing hands Java a direct ByteBuffer;
//!   * Java owns every `View`; Rust never holds a `jobject`.

use jni::objects::{JClass, JObject};
use jni::JNIEnv;
use splash_render::{Attrs, NodeKind, UiNode};
use std::sync::OnceLock;

/// The card under test. Deliberately *computed* — the `while` loop and the
/// `fn` mean a literal tree could not produce this; the VM really ran.
const CARD: &str = r#"
fn argb(a,r,g,b){ return ((a*256+r)*256+g)*256+b }
let bg    = argb(255, 18, 18, 20)
let card  = argb(255, 32, 32, 38)
let fg    = argb(255, 240, 240, 245)
let dim   = argb(255, 155, 155, 168)
let accnt = argb(255, 120, 160, 255)

let rows = []
let i = 0
while i < 3 {
    rows.push({t:"row", h: 44, pad: 8, bg: card, c: [
        {t:"text", text: "computed row " + i, size: 15, color: fg, w: 200, h: 24}
    ]})
    i = i + 1
}

{t:"scroll", bg: bg, c: [
  {t:"column", bg: bg, pad: 16, spacing: 10, c: [
    {t:"text", text:"Splash -> android.widget", size: 22, weight: 7, color: fg, h: 34},
    {t:"text", text:"real VM, real Views, no makepad", size: 13, color: dim, h: 22},

    {t:"button", label:"Button (framework)", h: 48},
    {t:"checkbox", text:"CheckBox", on: 1, h: 44},
    {t:"radio", text:"RadioButton", on: 1, h: 44},
    {t:"toggle", text:"Switch", on: 1, h: 44},
    {t:"input", placeholder:"EditText - tap for real IME", h: 52},
    {t:"slider", value: 60, total: 100, h: 40},
    {t:"progress", value: 45, total: 100, h: 24},
    {t:"loading", h: 48},
    {t:"textpicker", h: 120},

    {t:"text", text:"computed rows (while-loop in the VM):", size: 13, color: accnt, h: 24},
    {t:"column", spacing: 6, c: rows},

    {t:"text", text:"datepicker below (framework, large):", size: 13, color: accnt, h: 24},
    {t:"datepicker", h: 320},
  ]}
]}
"#;

// ---- wire format ---------------------------------------------------------
// magic u32 | node_count u32 | blob_len u32
// node: id u32 | parent u32 | kind u8 | attr_count u8 | pad u16
//   attr: id u8 | ty u8 | pad u16 | a u32 | b u32       (ty 3 => a=offset b=len)
// then the UTF-8 string blob.

const MAGIC: u32 = 0x5350_4C31;

const T_F32: u8 = 0;
const T_U32: u8 = 1;
const T_I32: u8 = 2;
const T_STR: u8 = 3;

const A_TEXT: u8 = 1;
const A_LABEL: u8 = 2;
const A_PLACEHOLDER: u8 = 3;
const A_W: u8 = 4;
const A_H: u8 = 5;
const A_SIZE: u8 = 6;
const A_WEIGHT: u8 = 7;
const A_COLOR: u8 = 8;
const A_BG: u8 = 9;
const A_PAD: u8 = 10;
const A_VALUE: u8 = 11;
const A_TOTAL: u8 = 12;
const A_ON: u8 = 13;
const A_TAP: u8 = 14;
const A_SPACING: u8 = 15;

fn kind_code(k: NodeKind) -> u8 {
    match k {
        NodeKind::Column => 0,
        NodeKind::Row => 1,
        NodeKind::Stack => 2,
        NodeKind::Scroll => 3,
        NodeKind::List => 4,
        NodeKind::Grid => 5,
        NodeKind::Waterflow => 6,
        NodeKind::Refresh => 7,
        NodeKind::Swiper => 8,
        NodeKind::Text => 9,
        NodeKind::Image => 10,
        NodeKind::Button => 11,
        NodeKind::Toggle => 12,
        NodeKind::Checkbox => 13,
        NodeKind::Radio => 14,
        NodeKind::Slider => 15,
        NodeKind::Progress => 16,
        NodeKind::Loading => 17,
        NodeKind::Input => 18,
        NodeKind::Textarea => 19,
        NodeKind::DatePicker => 20,
        NodeKind::TimePicker => 21,
        NodeKind::TextPicker => 22,
    }
}

struct Enc {
    nodes: Vec<u8>,
    blob: Vec<u8>,
    count: u32,
    next_id: u32,
}

impl Enc {
    fn str_ref(&mut self, s: &str) -> (u32, u32) {
        let off = self.blob.len() as u32;
        self.blob.extend_from_slice(s.as_bytes());
        (off, s.len() as u32)
    }

    fn attrs_of(&mut self, a: &Attrs) -> Vec<(u8, u8, u32, u32)> {
        let mut out: Vec<(u8, u8, u32, u32)> = Vec::new();
        let mut s = |out: &mut Vec<(u8, u8, u32, u32)>, id, v: &Option<String>, me: &mut Self| {
            if let Some(v) = v {
                let (o, l) = me.str_ref(v);
                out.push((id, T_STR, o, l));
            }
        };
        s(&mut out, A_TEXT, &a.text, self);
        s(&mut out, A_LABEL, &a.label, self);
        s(&mut out, A_PLACEHOLDER, &a.placeholder, self);
        for (id, v) in [
            (A_W, a.w),
            (A_H, a.h),
            (A_SIZE, a.size),
            (A_PAD, a.pad),
            (A_VALUE, a.value),
            (A_TOTAL, a.total),
            (A_SPACING, a.spacing),
        ] {
            if let Some(v) = v {
                out.push((id, T_F32, v.to_bits(), 0));
            }
        }
        for (id, v) in [(A_COLOR, a.color), (A_BG, a.bg)] {
            if let Some(v) = v {
                out.push((id, T_U32, v, 0));
            }
        }
        for (id, v) in [(A_WEIGHT, a.weight), (A_ON, a.on), (A_TAP, a.tap)] {
            if let Some(v) = v {
                out.push((id, T_I32, v as u32, 0));
            }
        }
        out
    }

    fn walk(&mut self, n: &UiNode, parent: u32) {
        let id = self.next_id;
        self.next_id += 1;
        let attrs = self.attrs_of(&n.attrs);
        self.nodes.extend_from_slice(&id.to_le_bytes());
        self.nodes.extend_from_slice(&parent.to_le_bytes());
        self.nodes.push(kind_code(n.kind));
        self.nodes.push(attrs.len() as u8);
        self.nodes.extend_from_slice(&0u16.to_le_bytes());
        for (aid, ty, a, b) in attrs {
            self.nodes.push(aid);
            self.nodes.push(ty);
            self.nodes.extend_from_slice(&0u16.to_le_bytes());
            self.nodes.extend_from_slice(&a.to_le_bytes());
            self.nodes.extend_from_slice(&b.to_le_bytes());
        }
        self.count += 1;
        for c in &n.children {
            self.walk(c, id);
        }
    }
}

fn encode(tree: &UiNode) -> Vec<u8> {
    let mut e = Enc {
        nodes: Vec::new(),
        blob: Vec::new(),
        count: 0,
        next_id: 0,
    };
    e.walk(tree, u32::MAX);
    let mut out = Vec::with_capacity(12 + e.nodes.len() + e.blob.len());
    out.extend_from_slice(&MAGIC.to_le_bytes());
    out.extend_from_slice(&e.count.to_le_bytes());
    out.extend_from_slice(&(e.blob.len() as u32).to_le_bytes());
    out.extend_from_slice(&e.nodes);
    out.extend_from_slice(&e.blob);
    out
}

/// The op buffer, kept alive for the process — Java reads it directly, so it
/// must outlive the JNI call that hands over the pointer.
static BUF: OnceLock<Vec<u8>> = OnceLock::new();
static DIAG: OnceLock<String> = OnceLock::new();

fn build_buf() -> &'static Vec<u8> {
    BUF.get_or_init(|| match splash_render::build(CARD, |_vm| {}) {
        Some(tree) => {
            let _ = DIAG.set(format!(
                "splash-render OK: {} nodes, root={:?}",
                tree.count(),
                tree.kind
            ));
            encode(&tree)
        }
        None => {
            let _ = DIAG.set("splash-render FAILED: build() returned None".to_string());
            Vec::new()
        }
    })
}

#[no_mangle]
pub extern "system" fn Java_dev_splash_probe_Native_buildOps<'l>(
    mut env: JNIEnv<'l>,
    _c: JClass<'l>,
) -> JObject<'l> {
    let b = build_buf();
    if b.is_empty() {
        return JObject::null();
    }
    match unsafe { env.new_direct_byte_buffer(b.as_ptr() as *mut u8, b.len()) } {
        Ok(bb) => bb.into(),
        Err(_) => JObject::null(),
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_splash_probe_Native_diag<'l>(
    env: JNIEnv<'l>,
    _c: JClass<'l>,
) -> JObject<'l> {
    build_buf();
    let s = DIAG.get().map(|s| s.as_str()).unwrap_or("no diag");
    match env.new_string(s) {
        Ok(v) => v.into(),
        Err(_) => JObject::null(),
    }
}
