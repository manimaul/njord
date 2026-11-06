//! The S-57 object catalogue (`GET /v1/about/s57objects`, `/s57attributes`, `/expectedInput`).

use serde::{Deserialize, Serialize};

use crate::IndexMap;

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct S57Object {
    /// Unique code for the object.
    #[serde(rename = "Code")]
    pub code: i32,
    /// Human-readable description of the object.
    #[serde(rename = "ObjectClass")]
    pub object_class: String,
    /// Six character acronym key for the object.
    #[serde(rename = "Acronym")]
    pub acronym: String,
    /// Attributes defining the individual characteristics of the object.
    #[serde(rename = "Attribute_A")]
    pub attribute_a: Vec<String>,
    /// Attributes relevant to the use of the data, e.g. for presentation.
    #[serde(rename = "Attribute_B")]
    pub attribute_b: Vec<String>,
    /// Administrative attributes about the object and the data describing it.
    #[serde(rename = "Attribute_C")]
    pub attribute_c: Vec<String>,
    #[serde(rename = "Class")]
    pub class: String,
    /// Allowed geometric primitives: P=point L=line A=area N=none.
    #[serde(rename = "Primitives")]
    pub primitives: Vec<String>,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct S57Attribute {
    /// Unique code for the attribute.
    #[serde(rename = "Code")]
    pub code: i32,
    /// Human-readable description of the attribute.
    #[serde(rename = "Attribute")]
    pub attribute: String,
    /// Six character acronym key for the attribute.
    #[serde(rename = "Acronym")]
    pub acronym: String,
    /// One of `E` enumerated, `L` list, `F` float, `I` integer, `A` coded string, `S` free text.
    #[serde(rename = "Attributetype")]
    pub attribute_type: String,
    #[serde(rename = "Class")]
    pub class: String,
}

/// One allowed value of an enumerated or list attribute.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct S57ExpectedInput {
    /// The [`S57Attribute::code`] this value belongs to.
    #[serde(rename = "Code")]
    pub code: i32,
    /// The attribute value, e.g. `27` in `CATSPM: ["27"]`.
    #[serde(rename = "ID")]
    pub id: i32,
    /// Human-readable meaning, e.g. "general warning mark".
    #[serde(rename = "Meaning")]
    pub meaning: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct S57Symbol {
    #[serde(rename = "SY")]
    pub symbol: Option<String>,
    #[serde(rename = "ATT")]
    pub attributes: Vec<IndexMap<String, Vec<String>>>,
}

/// Object class acronym → symbol rules.
pub type S57ObjAcronyms = IndexMap<String, Vec<S57Symbol>>;
