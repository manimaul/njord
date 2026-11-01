//! The registry of layerables in draw order.

use njord_model::{Depth, Layer, Theme};

use crate::layerable::{Context, LayerBuilder, Layerable};
use crate::layers::clearance_label::ClearanceLabel;
use crate::layers::name_label::NameLabel;
use crate::layers::*;
use crate::{ChartFeature, ColorLibrary, SymbologyConfig};

/// A layerable under its key.
///
/// The key is usually the type name upper-cased (`ObstrnArea` is `OBSTRNAREA`), or a custom key
/// such as the lower-case inland twins; [`LayerFactory::pre_tile_encode`] dispatches by matching
/// it to the feature's layer, and helper-made layer ids start with it.
pub struct Entry {
    pub key: String,
    pub layerable: Box<dyn Layerable>,
}

impl Entry {
    pub fn new(key: impl Into<String>, layerable: impl Layerable + 'static) -> Self {
        Entry {
            key: key.into(),
            layerable: Box::new(layerable),
        }
    }

    /// The layerable's own source layer, else its key.
    pub fn source_layer(&self) -> &str {
        self.layerable.source_layer().unwrap_or(&self.key)
    }

    /// This entry's style layers for one request, ids numbered from 1.
    pub fn layers(&self, cx: &Context, depth: Depth, theme: &Theme) -> Vec<Layer> {
        let mut b = LayerBuilder::new(cx, &self.key, self.source_layer(), depth, theme);
        self.layerable.layers(&mut b)
    }
}

/// The Natural Earth layers under the charts.
pub fn base_layers() -> Vec<Entry> {
    vec![
        Entry::new("BACKGROUND", background::Background),
        Entry::new("ocean", base::ocean::Ocean),
        Entry::new("bathymetry", base::bathymetry::Bathymetry),
        Entry::new("glaciated_areas", base::glaciated_areas::GlaciatedAreas),
        Entry::new("reefs", base::reefs::Reefs),
        Entry::new("LAND", base::land::Land),
        Entry::new("playas", base::playas::Playas),
        Entry::new(
            "RIVERSLAKECENTERLINES",
            base::rivers_lake_centerlines::RiversLakeCenterlines,
        ),
        Entry::new("LAKES", base::lakes::Lakes),
        Entry::new(
            "antarctic_ice_shelves_polys",
            base::antarctic_ice_shelves::AntarcticIceShelves,
        ),
        Entry::new("MINORISLANDS", base::minor_islands::MinorIslands),
        Entry::new("coastline", base::coastline::Coastline),
    ]
}

/// The S-57 object classes drawn on every chart.
///
/// Some classes are not drawn yet (`TSFEB`, `WATFAL`, `RDOSTA`, `M_COVR`, …, and `MAGVAR`); those
/// have no module.
pub fn standard_layers() -> Vec<Entry> {
    let mut e = vec![
        // land / base layers
        Entry::new("DEPARE", depare::Depare),
        Entry::new("LNDARE", lndare::Lndare::default()),
        Entry::new("SLOGRD", slogrd::Slogrd),
        Entry::new("LAKARE", lakare::Lakare),
        Entry::new("LAKSHR", lakshr::Lakshr),
        Entry::new("BUISGL", buisgl::Buisgl),
        Entry::new("BUAARE", buaare::Buaare),
        Entry::new("OBSTRNAREA", obstrn::ObstrnArea),
        Entry::new("SLCONS", slcons::Slcons),
        Entry::new("LNDRGN", lndrgn::Lndrgn),
        Entry::new("DRGARE", drgare::Drgare),
        Entry::new("ICEARE", iceare::Iceare),
        Entry::new("COALNE", coalne::Coalne),
        Entry::new("CAUSWY", causwy::Causwy),
        Entry::new("CANALS", canals::Canals::default()),
        Entry::new("RIVERS", rivers::Rivers),
        Entry::new("AIRARE", airare::Airare),
        Entry::new("RUNWAY", runway::Runway),
        Entry::new("DYKCON", dykcon::Dykcon),
        Entry::new("SILTNK", siltnk::Siltnk),
        Entry::new("CTRPNT", ctrpnt::Ctrpnt),
        Entry::new("LNDELV", lndelv::Lndelv),
        Entry::new("CGUSTA", cgusta::Cgusta),
        // water boundary layers
        Entry::new("UNSARE", unsare::Unsare),
        Entry::new("LOKBSN", lokbsn::Lokbsn),
        Entry::new("TSSLPT", tsslpt::Tsslpt),
        Entry::new("TSSBND", tssbnd::Tssbnd),
        Entry::new("TSELNE", tselne::Tselne),
        Entry::new("TWRTPT", twrtpt::Twrtpt),
        Entry::new("TSEZNE", tsezne::Tsezne),
        Entry::new("TCTLPT", tctlpt::Tctlpt),
        Entry::new("TSSCRS", tsscrs::Tsscrs),
        Entry::new("TSSRON", tssron::Tssron),
        Entry::new("DWRTPT", dwrtpt::Dwrtpt),
        Entry::new("DWRTCL", dwrtcl::Dwrtcl),
        Entry::new("MIPARE", mipare::Mipare),
        Entry::new("LOGPON", logpon::Logpon),
        Entry::new("ICNARE", icnare::Icnare),
        Entry::new("ACHBRT", achbrt::Achbrt),
        Entry::new("ACHARE", achare::Achare),
        Entry::new("CBLARE", cblare::Cblare),
        Entry::new("CBLSUB", cblsub::Cblsub),
        Entry::new("CBLOHD", cblohd::Cblohd),
        Entry::new("PIPOHD", pipohd::Pipohd),
        Entry::new("RESARE", resare::Resare),
        Entry::new("CTNARE", ctnare::Ctnare),
        Entry::new("CTSARE", ctsare::Ctsare),
        Entry::new("WEDKLP", wedklp::Wedklp),
        Entry::new("FAIRWY", fairwy::Fairwy),
        Entry::new("FNCLNE", fnclne::Fnclne),
        Entry::new("FERYRT", feryrt::Feryrt),
        Entry::new("FSHGRD", fshgrd::Fshgrd),
        Entry::new("FSHFAC", fshfac::Fshfac),
        Entry::new("SPLARE", splare::Splare),
        Entry::new("MARCUL", marcul::Marcul),
        Entry::new("FORSTC", forstc::Forstc),
        Entry::new("DMPGRD", dmpgrd::Dmpgrd),
        Entry::new("PIPARE", pipare::Pipare),
        Entry::new("SUBTLN", subtln::Subtln),
        Entry::new("ISTZNE", istzne::Istzne),
        Entry::new("RECTRC", rectrc::Rectrc),
        Entry::new("RCTLPT", rctlpt::Rctlpt),
        Entry::new("NAVLNE", navlne::Navlne),
        Entry::new("RCRTCL", rcrtcl::Rcrtcl),
        Entry::new("RADLNE", radlne::Radlne),
        Entry::new("RADRNG", radrng::Radrng),
        Entry::new("RADRFL", radrfl::Radrfl),
        Entry::new("RADSTA", radsta::Radsta),
        Entry::new("RTPBCN", rtpbcn::Rtpbcn),
        Entry::new("RDOCAL", rdocal::Rdocal),
        Entry::new("DISMAR", dismar::Dismar),
        Entry::new("SWPARE", swpare::Swpare),
        Entry::new("TUNNEL", tunnel::Tunnel),
        Entry::new("CHKPNT", chkpnt::Chkpnt),
        Entry::new("PRCARE", prcare::Prcare),
        Entry::new("SNDWAV", sndwav::Sndwav),
        Entry::new("SBDARE", sbdare::Sbdare),
        Entry::new("TIDEWY", tidewy::Tidewy),
        Entry::new("BERTHS", berths::Berths),
        // obstructions
        Entry::new("HULKES", hulkes::Hulkes),
        Entry::new("PONTON", ponton::Ponton),
        Entry::new("MORFAC", morfac::Morfac),
        Entry::new("BRIDGE", bridge::Bridge),
        Entry::new("CONVYR", convyr::Convyr),
        Entry::new("DAMCON", damcon::Damcon),
        Entry::new("DOCARE", docare::Docare),
        Entry::new("FLODOC", flodoc::Flodoc),
        Entry::new("GATCON", gatcon::Gatcon),
        Entry::new("OFSPLF", ofsplf::Ofsplf),
        Entry::new("OILBAR", oilbar::Oilbar),
        Entry::new("CRANES", cranes::Cranes),
        Entry::new("PYLONS", pylons::Pylons),
        Entry::new("DAYMAR", daymar::Daymar),
        Entry::new("PILBOP", pilbop::Pilbop),
        Entry::new("PILPNT", pilpnt::Pilpnt),
        Entry::new("OBSTRN", obstrn::Obstrn),
        Entry::new("WRECKS", wrecks::Wrecks),
        Entry::new("UWTROC", uwtroc::Uwtroc),
        Entry::new("OSPARE", ospare::Ospare),
        Entry::new("SISTAT", sistat::Sistat),
        Entry::new("SISTAW", sistaw::Sistaw),
        // low pri labels
        Entry::new("LNDARELABEL", lndare_label::LndareLabel),
        Entry::new("SEAARE", seaare::Seaare),
        // buoys and markers
        Entry::new("NEWOBJ", newobj::Newobj),
        Entry::new("FOGSIG", fogsig::Fogsig),
        Entry::new("BCNCAR", bcncar::Bcncar),
        Entry::new("BCNISD", bcnisd::Bcnisd),
        Entry::new("BCNLAT", bcnlat::Bcnlat),
        Entry::new("BCNSAW", bcnsaw::Bcnsaw),
        Entry::new("BCNSPP", bcnspp::Bcnspp),
        Entry::new("BOYCAR", boycar::Boycar),
        Entry::new("BOYINB", boyinb::Boyinb),
        Entry::new("BOYISD", boyisd::Boyisd),
        Entry::new("BOYLAT", boylat::Boylat),
        Entry::new("BOYSAW", boysaw::Boysaw),
        Entry::new("BOYSPP", boyspp::Boyspp),
        Entry::new("LNDMRK", lndmrk::Lndmrk),
        Entry::new("ACHPNT", achpnt::Achpnt),
        Entry::new("RETRFL", retrfl::Retrfl),
        Entry::new("TOPMAR", topmar::Topmar),
        // high pri labels
        Entry::new("SOUNDG", soundg::Soundg),
    ];
    // clearance text is S-52 display-base, so it goes above the symbology of the objects it
    // annotates - see clearance_label
    for class in ["BRIDGE", "CBLOHD", "PIPOHD", "CONVYR", "CRANES", "GATCON"] {
        let label = ClearanceLabel::new(class);
        e.push(Entry::new(label.key(), label));
    }
    e.extend([
        Entry::new("CURENT", curent::Curent),
        // lights
        Entry::new("LITFLT", litflt::Litflt),
        Entry::new("LITVES", litves::Litves),
        Entry::new("LIGHTS", lights::Lights),
        Entry::new("PRDARE", prdare::Prdare),
    ]);
    // S-52 text group 21, names for position reporting - after every mark symbol and the
    // light descriptions so the names win placement - see name_label
    for label in NameLabel::all() {
        e.push(Entry::new(label.key(), label));
    }
    e
}

/// Inland ENC classes, and the inland twins of three standard classes under
/// lower-case keys.
pub fn inland_layers() -> Vec<Entry> {
    vec![
        Entry::new("lokbsn", lokbsn::Lokbsn),
        Entry::new("HRBBSN", hrbbsn::Hrbbsn),
        Entry::new("NOTMRK", notmrk::Notmrk),
        Entry::new("LKBSPT", lkbspt::Lkbspt),
        Entry::new("EXCNST", excnst::Excnst),
        Entry::new("achbrt", achbrt::Achbrt),
        Entry::new("achare", achare::Achare),
    ]
}

/// Layers outside S-57: the `PLY` coverage outline.
pub fn extra_layers() -> Vec<Entry> {
    vec![Entry::new("PLY", ply::Ply)]
}

/// Every registered layerable in draw order, with the config and colours the rules read.
pub struct LayerFactory {
    pub cx: Context,
    entries: Vec<Entry>,
}

impl LayerFactory {
    /// Base, standard, inland and extra layers, then `Debug` when `config.debug_tile`.
    ///
    /// # Panics
    ///
    /// When two entries share a key; dispatch by key needs each to be unique.
    pub fn new(config: SymbologyConfig, colors: ColorLibrary) -> Self {
        let mut entries = base_layers();
        entries.extend(standard_layers());
        entries.extend(inland_layers());
        entries.extend(extra_layers());
        if config.debug_tile {
            entries.push(Entry::new("DEBUG", debug::Debug));
        }
        for (i, e) in entries.iter().enumerate() {
            assert!(
                entries[..i].iter().all(|o| o.key != e.key),
                "duplicate layerable key {}",
                e.key
            );
        }
        LayerFactory {
            cx: Context::new(config, colors),
            entries,
        }
    }

    /// Every entry, in draw order.
    pub fn entries(&self) -> &[Entry] {
        &self.entries
    }

    pub fn get(&self, key: &str) -> Option<&Entry> {
        self.entries.iter().find(|e| e.key == key)
    }

    /// The complete layer list of one style.
    pub fn layers(&self, depth: Depth, theme: &Theme) -> Vec<Layer> {
        self.entries
            .iter()
            .flat_map(|e| e.layers(&self.cx, depth, theme))
            .collect()
    }

    /// Runs the layerable registered under the feature's layer, if any; features of other
    /// layers pass through unchanged.
    pub fn pre_tile_encode(&self, feature: &mut ChartFeature) {
        if let Some(e) = self.get(&feature.layer) {
            e.layerable.pre_tile_encode(feature, &self.cx);
        }
    }
}
