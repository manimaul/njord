//! A PNG encoder whose output is byte-identical to libgd's `gdImagePngPtr`.
//!
//! It reproduces libgd 2.3 over libpng 1.6 and zlib for a true-colour image with saved alpha, the
//! only kind the server writes (`/v1/icon/{name}`).
//!
//! The conformance goldens pin icons by sha256, so the encoder reproduces each libpng choice:
//! - chunks `IHDR` (8-bit RGBA, no interlace), `pHYs` (gd's default 96 dpi = 3780 px/m), `IDAT`
//!   split every 8192 bytes (`PNG_ZBUF_SIZE`), `IEND`;
//! - libpng's adaptive row filter: the first filter (None, Sub, Up, Average, Paeth) with the
//!   lowest sum of bytes taken as signed magnitudes, the previous row of the first row being
//!   zeros; Up/Average/Paeth are dropped for a one-row image and Sub/Average/Paeth for a
//!   one-column one;
//! - zlib at the default level with `Z_FILTERED` (`Z_DEFAULT_STRATEGY` when only None remains),
//!   the window shrunk for small images (`png_deflate_claim`) and the zlib header's window size
//!   rewritten to the smallest that covers the data (`optimize_cmf`).
//!
//! Deflate output is zlib's own, so this links zlib (`libz-sys`); no pure-Rust deflate produces
//! the same bytes.

use std::os::raw::{c_int, c_uint, c_void};

const ZBUF_SIZE: usize = 8192;
/// gd's default resolution, 96 dpi, in pixels per metre (`DPI2DPM`).
const PIXELS_PER_METRE: u32 = 3780;

/// Encodes `rgba` (`width * height * 4` bytes, row-major) as gd does.
///
/// # Panics
///
/// Panics when `rgba` is not `width * height * 4` bytes or zlib fails, which only a bug can cause.
pub fn encode_rgba(width: u32, height: u32, rgba: &[u8]) -> Vec<u8> {
    let row_bytes = width as usize * 4;
    assert_eq!(rgba.len(), row_bytes * height as usize, "rgba size");

    let mut filters = Filters::ALL;
    if height == 1 {
        filters.up = false;
        filters.avg = false;
        filters.paeth = false;
    }
    if width == 1 {
        filters.sub = false;
        filters.avg = false;
        filters.paeth = false;
    }
    let filtered = filter_rows(rgba, row_bytes, filters);
    let strategy = if filters.only_none() { 0 } else { 1 }; // Z_DEFAULT_STRATEGY / Z_FILTERED
    let mut zdata = deflate(&filtered, window_bits(filtered.len()), strategy);
    optimize_cmf(&mut zdata, filtered.len());

    let mut out = b"\x89PNG\r\n\x1a\n".to_vec();
    let mut ihdr = Vec::with_capacity(13);
    ihdr.extend_from_slice(&width.to_be_bytes());
    ihdr.extend_from_slice(&height.to_be_bytes());
    ihdr.extend_from_slice(&[8, 6, 0, 0, 0]);
    chunk(&mut out, b"IHDR", &ihdr);
    let mut phys = Vec::with_capacity(9);
    phys.extend_from_slice(&PIXELS_PER_METRE.to_be_bytes());
    phys.extend_from_slice(&PIXELS_PER_METRE.to_be_bytes());
    phys.push(1);
    chunk(&mut out, b"pHYs", &phys);
    for part in zdata.chunks(ZBUF_SIZE) {
        chunk(&mut out, b"IDAT", part);
    }
    chunk(&mut out, b"IEND", &[]);
    out
}

#[derive(Clone, Copy)]
struct Filters {
    sub: bool,
    up: bool,
    avg: bool,
    paeth: bool,
}

impl Filters {
    const ALL: Filters = Filters {
        sub: true,
        up: true,
        avg: true,
        paeth: true,
    };

    fn only_none(self) -> bool {
        !(self.sub || self.up || self.avg || self.paeth)
    }
}

/// Each row prefixed by its filter type, filtered as `png_write_find_filter` chooses.
fn filter_rows(rgba: &[u8], row_bytes: usize, filters: Filters) -> Vec<u8> {
    const BPP: usize = 4;
    let zeros = vec![0u8; row_bytes];
    let mut out = Vec::with_capacity(rgba.len() + rgba.len() / row_bytes.max(1));
    let mut candidate = vec![0u8; row_bytes];
    let mut best = vec![0u8; row_bytes];
    for (i, row) in rgba.chunks_exact(row_bytes).enumerate() {
        let prev = if i == 0 {
            &zeros[..]
        } else {
            &rgba[(i - 1) * row_bytes..i * row_bytes]
        };
        best.copy_from_slice(row);
        let mut best_type = 0u8;
        let mut min = if filters.only_none() { 0 } else { sum(row) };
        let mut try_filter = |kind: u8, f: &dyn Fn(usize) -> u8| {
            for (j, b) in candidate.iter_mut().enumerate() {
                *b = f(j);
            }
            let s = sum(&candidate);
            if s < min {
                min = s;
                best_type = kind;
                best.copy_from_slice(&candidate);
            }
        };
        let left = |j: usize| if j >= BPP { row[j - BPP] } else { 0 };
        let up_left = |j: usize| if j >= BPP { prev[j - BPP] } else { 0 };
        if filters.sub {
            try_filter(1, &|j| row[j].wrapping_sub(left(j)));
        }
        if filters.up {
            try_filter(2, &|j| row[j].wrapping_sub(prev[j]));
        }
        if filters.avg {
            try_filter(3, &|j| {
                row[j].wrapping_sub(((left(j) as u16 + prev[j] as u16) / 2) as u8)
            });
        }
        if filters.paeth {
            try_filter(4, &|j| {
                row[j].wrapping_sub(paeth(left(j), prev[j], up_left(j)))
            });
        }
        out.push(best_type);
        out.extend_from_slice(&best);
    }
    out
}

/// libpng's filter heuristic: each byte as a signed magnitude.
fn sum(bytes: &[u8]) -> usize {
    bytes
        .iter()
        .map(|&v| {
            if v < 128 {
                v as usize
            } else {
                256 - v as usize
            }
        })
        .sum()
}

fn paeth(a: u8, b: u8, c: u8) -> u8 {
    let (ia, ib, ic) = (a as i16, b as i16, c as i16);
    let p = ib - ic;
    let pc = ia - ic;
    let (pa, pb, pc) = (p.abs(), pc.abs(), (p + pc).abs());
    if pb < pa {
        if pc < pb { c } else { b }
    } else if pc < pa {
        c
    } else {
        a
    }
}

/// `png_deflate_claim`: 15, shrunk while the data plus 262 bytes fits in half the window, for
/// data up to 16 KiB; zlib refuses 8, so 9.
fn window_bits(data_size: usize) -> c_int {
    let mut bits = 15;
    if data_size <= 16384 {
        let mut half = 1usize << (bits - 1);
        while data_size + 262 <= half {
            half >>= 1;
            bits -= 1;
        }
    }
    if bits == 8 { 9 } else { bits }
}

/// libpng's `optimize_cmf`: the zlib header's window size, lowered to the smallest power of two
/// not below the data size, with the check bits recomputed.
fn optimize_cmf(data: &mut [u8], data_size: usize) {
    if data_size > 16384 || data.len() < 2 {
        return;
    }
    let mut cmf = data[0] as u32;
    if cmf & 0x0f != 8 || cmf & 0xf0 > 0x70 {
        return;
    }
    let mut cinfo = cmf >> 4;
    let mut half = 1usize << (cinfo + 7);
    if data_size <= half {
        loop {
            half >>= 1;
            cinfo -= 1;
            if !(cinfo > 0 && data_size <= half) {
                break;
            }
        }
        cmf = (cmf & 0x0f) | (cinfo << 4);
        data[0] = cmf as u8;
        let mut flg = data[1] as u32 & 0xe0;
        flg += 0x1f - ((cmf << 8) + flg) % 0x1f;
        data[1] = flg as u8;
    }
}

fn chunk(out: &mut Vec<u8>, kind: &[u8; 4], data: &[u8]) {
    out.extend_from_slice(&(data.len() as u32).to_be_bytes());
    let start = out.len();
    out.extend_from_slice(kind);
    out.extend_from_slice(data);
    // SAFETY: `crc32` only reads `len` bytes from the pointer, which `out[start..]` holds.
    let crc = unsafe {
        let bytes = &out[start..];
        libz_sys::crc32(0, bytes.as_ptr(), bytes.len() as c_uint)
    };
    out.extend_from_slice(&(crc as u32).to_be_bytes());
}

unsafe extern "C" fn zalloc(_: *mut c_void, items: c_uint, size: c_uint) -> *mut c_void {
    // SAFETY: plain C allocation, released by `zfree`.
    unsafe { libc::calloc(items as usize, size as usize) }
}

unsafe extern "C" fn zfree(_: *mut c_void, address: *mut c_void) {
    // SAFETY: `address` came from `zalloc`.
    unsafe { libc::free(address) }
}

/// One-shot zlib compression at `Z_DEFAULT_COMPRESSION`, memory level 8.
fn deflate(data: &[u8], window_bits: c_int, strategy: c_int) -> Vec<u8> {
    use libz_sys::*;
    // SAFETY: the stream is initialised by `deflateInit2_` before use and ended on every path;
    // `next_in`/`next_out` point into `data` and `out`, which outlive the calls, with matching
    // `avail_*` lengths. zlib never writes through `next_in`.
    unsafe {
        let mut stream = z_stream {
            next_in: data.as_ptr() as *mut Bytef,
            avail_in: data.len() as uInt,
            total_in: 0,
            next_out: std::ptr::null_mut(),
            avail_out: 0,
            total_out: 0,
            msg: std::ptr::null_mut(),
            state: std::ptr::null_mut(),
            zalloc,
            zfree,
            opaque: std::ptr::null_mut(),
            data_type: 0,
            adler: 0,
            reserved: 0,
        };
        let rc = deflateInit2_(
            &mut stream,
            Z_DEFAULT_COMPRESSION,
            Z_DEFLATED,
            window_bits,
            8,
            strategy,
            zlibVersion(),
            std::mem::size_of::<z_stream>() as c_int,
        );
        assert_eq!(rc, Z_OK, "deflateInit2");
        let bound = deflateBound(&mut stream, data.len() as uLong) as usize;
        let mut out = vec![0u8; bound];
        stream.next_out = out.as_mut_ptr();
        stream.avail_out = bound as uInt;
        let rc = deflate(&mut stream, Z_FINISH);
        let written = stream.total_out as usize;
        deflateEnd(&mut stream);
        assert_eq!(rc, Z_STREAM_END, "deflate");
        out.truncate(written);
        out
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn window_bits_follow_libpng() {
        assert_eq!(window_bits(5076), 13);
        assert_eq!(window_bits(100), 9);
        assert_eq!(window_bits(20000), 15);
    }

    #[test]
    fn output_decodes() {
        let rgba: Vec<u8> = (0..3 * 2 * 4).map(|i| (i * 37) as u8).collect();
        let png = encode_rgba(3, 2, &rgba);
        let decoder = png::Decoder::new(std::io::Cursor::new(png));
        let mut reader = decoder.read_info().unwrap();
        let mut buf = vec![0; reader.output_buffer_size()];
        reader.next_frame(&mut buf).unwrap();
        assert_eq!(buf, rgba);
    }
}
