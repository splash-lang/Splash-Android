//! The catalog's screens, mirroring material-components-android's own list.
//! Each is Splash DSL, evaluated on device.

pub const KIT: &str = include_str!("../splash/kit.splash");

macro_rules! screens {
    ($($route:literal => $title:literal , $file:literal);* $(;)?) => {
        /// (route, display title) — drives the table of contents.
        pub const ROUTES: &[(&str, &str)] = &[ $(($route, $title)),* ];

        pub fn body(route: &str) -> &'static str {
            match route {
                $($route => include_str!(concat!("../splash/", $file)),)*
                _ => "{t:\"col\", pad: 24, c:[{t:\"text\", variant:\"titleMedium\", text:\"Unknown route\"}]}",
            }
        }
    };
}

screens! {
    "allcomponents"     => "All components",        "allcomponents.splash";
    "adaptive"          => "Adaptive layouts",      "adaptive.splash";
    "badge"             => "Badge",                 "badge.splash";
    "bottomappbar"      => "Bottom app bar",        "bottomappbar.splash";
    "bottomnav"         => "Bottom navigation",     "bottomnav.splash";
    "bottomsheet"       => "Bottom sheet",          "bottomsheet.splash";
    "button"            => "Button",                "button.splash";
    "card"              => "Card",                  "card.splash";
    "carousel"          => "Carousel",              "carousel.splash";
    "checkbox"          => "Checkbox",              "checkbox.splash";
    "chip"              => "Chip",                  "chip.splash";
    "color"             => "Color palette",         "color.splash";
    "datepicker"        => "Date picker",           "datepicker.splash";
    "dialog"            => "Dialog",                "dialog.splash";
    "divider"           => "Divider",               "divider.splash";
    "dockedtoolbar"     => "Docked toolbar",        "dockedtoolbar.splash";
    "elevation"         => "Elevation",             "elevation.splash";
    "fab"               => "Floating action button","fab.splash";
    "floatingtoolbar"   => "Floating toolbar",      "floatingtoolbar.splash";
    "font"              => "Typography",            "font.splash";
    "imageview"         => "Image view",            "imageview.splash";
    "listitem"          => "List item",             "listitem.splash";
    "loadingindicator"  => "Loading indicator",     "loadingindicator.splash";
    "materialswitch"    => "Switch",                "materialswitch.splash";
    "menu"              => "Menu",                  "menu.splash";
    "musicplayer"       => "Music player",          "musicplayer.splash";
    "navigationdrawer"  => "Navigation drawer",     "navigationdrawer.splash";
    "octoswidgets"      => "octos-one widgets",     "octoswidgets.splash";
    "navigationrail"    => "Navigation rail",       "navigationrail.splash";
    "preferences"       => "Preferences",           "preferences.splash";
    "progressindicator" => "Progress indicator",    "progressindicator.splash";
    "radiobutton"       => "Radio button",          "radiobutton.splash";
    "search"            => "Search",                "search.splash";
    "shapetheming"      => "Shape theming",         "shapetheming.splash";
    "sidesheet"         => "Side sheet",            "sidesheet.splash";
    "slider"            => "Slider",                "slider.splash";
    "snackbar"          => "Snackbar",              "snackbar.splash";
    "tabs"              => "Tabs",                  "tabs.splash";
    "textfield"         => "Text field",            "textfield.splash";
    "timepicker"        => "Time picker",           "timepicker.splash";
    "topappbar"         => "Top app bar",           "topappbar.splash";
    "transition"        => "Transition",            "transition.splash";
}
