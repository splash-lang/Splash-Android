use splash_catalog::{debug_route, routes};
fn main() {
    let mut bad = 0;
    for (r, _t) in routes() {
        let d = debug_route(r);
        let n: usize = d.split(' ').next().unwrap_or("0").parse().unwrap_or(0);
        if n < 400 { bad += 1; println!("{:<20} SMALL {}", r, d); }
        else { println!("{:<20} {}", r, d); }
    }
    println!("\n{} routes, {} suspicious", routes().len(), bad);
}
