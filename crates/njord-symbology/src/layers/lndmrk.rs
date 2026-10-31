//! Geometry Primitives: Point, Line, Area
//!
//! Object: Landmark
//!
//! Acronym: LNDMRK
//!
//! Code: 74

use crate::layers::prelude::*;

#[derive(Clone, Copy, Debug, Default)]
pub struct Lndmrk;

const LINE_COLORS: [Color; 2] = [Color::LANDF, Color::CHBLK];

impl Layerable for Lndmrk {
    fn pre_tile_encode(&self, feature: &mut ChartFeature, _cx: &Context) {
        let functn = Functn::of(&feature.props);
        let mut viz = false;
        match Convis::of(&feature.props) {
            Some(Convis::VISUAL_CONSPICUOUS) => {
                viz = true;
                feature.area_color(Color::CHBRN);
                feature.line_color(Color::CHBLK);
            }
            Some(Convis::NOT_VISUAL_CONSPICUOUS) | None => {
                feature.area_color(Color::CHBRN);
                feature.line_color(Color::LANDF);
            }
        }

        let pick = |conspicuous: Sprite, other: Sprite| if viz { conspicuous } else { other };

        if functn
            .iter()
            .any(|f| matches!(f, Functn::CHURCH | Functn::CHAPEL))
        {
            feature.point_symbol(pick(Sprite::BUIREL13, Sprite::BUIREL01));
            return;
        }

        if functn
            .iter()
            .any(|f| matches!(f, Functn::MOSQUE | Functn::MARABOUT))
        {
            feature.point_symbol(pick(Sprite::BUIREL15, Sprite::BUIREL05));
            return;
        }

        let symbol = match Catlmk::of(&feature.props).first() {
            Some(Catlmk::CAIRN) => pick(Sprite::CAIRNS11, Sprite::CAIRNS01),
            Some(Catlmk::CHIMNEY) => pick(Sprite::CHIMNY11, Sprite::CHIMNY01),
            Some(Catlmk::DISH_AERIAL) => Sprite::DSHAER11,
            Some(Catlmk::FLAGSTAFF_FLAGPOLE) => Sprite::FLGSTF01,
            Some(Catlmk::FLARE_STACK) => pick(Sprite::FLASTK11, Sprite::FLASTK01),
            Some(Catlmk::MAST) => pick(Sprite::MSTCON14, Sprite::MSTCON04),
            Some(Catlmk::MONUMENT) => Sprite::MONUMT12,
            Some(Catlmk::COLUMN_PILLAR | Catlmk::OBELISK | Catlmk::STATUE) => Sprite::MONUMT12,
            Some(Catlmk::CROSS) => pick(Sprite::BUIREL13, Sprite::BUIREL01),
            Some(Catlmk::DOME) => pick(Sprite::DOMES011, Sprite::DOMES001),
            Some(Catlmk::RADAR_SCANNER) => pick(Sprite::RASCAN11, Sprite::RASCAN01),
            Some(Catlmk::TOWER) => pick(Sprite::TOWERS12, Sprite::TOWERS02),
            Some(Catlmk::WINDMILL) => Sprite::WNDMIL12,
            Some(Catlmk::WINDMOTOR) => pick(Sprite::WIMCON11, Sprite::WIMCON01),
            Some(
                Catlmk::MEMORIAL_PLAQUE
                | Catlmk::CEMETERY
                | Catlmk::WINDSOCK
                | Catlmk::SPIRE_MINARET,
            )
            | None => pick(Sprite::POSGEN03, Sprite::POSGEN01),
        };
        feature.point_symbol(symbol);
    }

    fn layers(&self, b: &mut LayerBuilder) -> Vec<Layer> {
        vec![
            b.area_layer_with_fill_color(Color::CHBRN, None),
            b.area_layer_with_single_symbol(AreaSingleSymbolOpts::default()),
            b.line_layer_with_colors(
                &LINE_COLORS,
                LineColorsOpts {
                    width: 1.0,
                    ..Default::default()
                },
            ),
            b.point_layer_from_symbol(PointSymbolOpts::default()),
        ]
    }
}
