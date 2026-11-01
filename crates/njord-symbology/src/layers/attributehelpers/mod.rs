//! S-57 attribute decoders: an enumerated attribute's ID to a named value.
//!
//! Variant names are upper-case constants (`FISHING_STAKE`); [`name`](Watlev::name) answers them
//! verbatim, and they are the names the attribute-helper fixture in `testdata/` records.
//!
//! `Watlev::of(&feature.props)` reads a feature's attribute: a single-valued attribute answers
//! `Option<Self>` (`None` when absent or unknown), a list attribute answers the known values in
//! order, dropping unknown IDs.

/// Declares an attribute enum: `ALL`, `ATTRIBUTE`, `from_id`, `id`, `name`.
macro_rules! attribute_enum {
    ($(#[$meta:meta])* pub enum $name:ident = $attribute:literal { $($variant:ident = $id:literal),+ $(,)? }) => {
        $(#[$meta])*
        #[allow(non_camel_case_types, clippy::upper_case_acronyms)]
        #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
        pub enum $name { $($variant),+ }

        impl $name {
            /// The S-57 attribute acronym this decodes.
            pub const ATTRIBUTE: &'static str = $attribute;

            /// Every value, in declaration order.
            pub const ALL: &'static [$name] = &[$($name::$variant),+];

            /// The value for an S-57 expected-input ID.
            pub fn from_id(id: i32) -> Option<$name> {
                match id {
                    $($id => Some($name::$variant),)+
                    _ => None,
                }
            }

            /// The S-57 expected-input ID.
            pub fn id(self) -> i32 {
                match self { $($name::$variant => $id),+ }
            }

            /// The constant name, as the variant is spelled.
            pub fn name(self) -> &'static str {
                match self { $($name::$variant => stringify!($variant)),+ }
            }
        }
    };
}

/// An enumerated (`E`) attribute: `of(props) -> Option<Self>`.
macro_rules! single_attribute {
    ($(#[$meta:meta])* pub enum $name:ident = $attribute:literal { $($body:tt)+ }) => {
        $crate::layers::attributehelpers::attribute_enum! {
            $(#[$meta])* pub enum $name = $attribute { $($body)+ }
        }

        impl $name {
            pub fn of(props: &njord_model::JsonObject) -> Option<$name> {
                $crate::props::int_value(props, $attribute).and_then($name::from_id)
            }
        }
    };
}

/// A list (`L`) attribute: `of(props) -> Vec<Self>`.
macro_rules! list_attribute {
    ($(#[$meta:meta])* pub enum $name:ident = $attribute:literal { $($body:tt)+ }) => {
        $crate::layers::attributehelpers::attribute_enum! {
            $(#[$meta])* pub enum $name = $attribute { $($body)+ }
        }

        impl $name {
            pub fn of(props: &njord_model::JsonObject) -> Vec<$name> {
                $crate::props::int_values(props, $attribute)
                    .into_iter()
                    .filter_map($name::from_id)
                    .collect()
            }
        }
    };
}

pub(crate) use {attribute_enum, list_attribute, single_attribute};

mod bcnshp;
mod boyshp;
mod catbrg;
mod catcam;
mod catdam;
mod catfif;
mod catgat;
mod catlam;
mod catlmk;
mod catlnd;
mod catmor;
mod catobs;
mod catpip;
mod catpra;
mod catrea;
mod catsea;
mod catsil;
mod catspm;
mod cattrk;
mod catwrk;
mod conrad;
mod convis;
mod depth_color;
mod functn;
mod prodct;
mod quasou;
mod restrn;
mod topshp;
mod trafic;
mod watlev;

pub use bcnshp::Bcnshp;
pub use boyshp::Boyshp;
pub use catbrg::Catbrg;
pub use catcam::Catcam;
pub use catdam::Catdam;
pub use catfif::Catfif;
pub use catgat::Catgat;
pub use catlam::Catlam;
pub use catlmk::Catlmk;
pub use catlnd::Catlnd;
pub use catmor::Catmor;
pub use catobs::Catobs;
pub use catpip::Catpip;
pub use catpra::Catpra;
pub use catrea::Catrea;
pub use catsea::Catsea;
pub use catsil::Catsil;
pub use catspm::Catspm;
pub use cattrk::Cattrk;
pub use catwrk::Catwrk;
pub use conrad::Conrad;
pub use convis::Convis;
pub use depth_color::DepthColor;
pub use functn::Functn;
pub use prodct::Prodct;
pub use quasou::Quasou;
pub use restrn::Restrn;
pub use topshp::Topshp;
pub use trafic::Trafic;
pub use watlev::Watlev;
