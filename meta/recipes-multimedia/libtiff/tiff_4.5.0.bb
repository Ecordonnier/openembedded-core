SUMMARY = "Provides support for the Tag Image File Format (TIFF)"
DESCRIPTION = "Library provides support for the Tag Image File Format \
(TIFF), a widely used format for storing image data.  This library \
provide means to easily access and create TIFF image files."
HOMEPAGE = "http://www.libtiff.org/"
LICENSE = "BSD-2-Clause"
LIC_FILES_CHKSUM = "file://LICENSE.md;md5=a3e32d664d6db1386b4689c8121531c3"

CVE_PRODUCT = "libtiff"

SRC_URI = "http://download.osgeo.org/libtiff/tiff-${PV}.tar.gz \
           file://CVE-2022-48281.patch \
           file://CVE-2023-2731.patch \
           file://CVE-2023-26965.patch \
"

SRC_URI[sha256sum] = "c7a1d9296649233979fa3eacffef3fa024d73d05d589cb622727b5b08c423464"

# exclude betas
UPSTREAM_CHECK_REGEX = "tiff-(?P<pver>\d+(\.\d+)+).tar"

# Tested with check from https://security-tracker.debian.org/tracker/CVE-2015-7313
# and 4.3.0 doesn't have the issue
CVE_CHECK_IGNORE += "CVE-2015-7313"
# These issues only affect libtiff post-4.3.0 but before 4.4.0,
# caused by 3079627e and fixed by b4e79bfa.
CVE_CHECK_IGNORE += "CVE-2022-1622 CVE-2022-1623"
# Issue is in jbig which we don't enable
CVE_CHECK_IGNORE += "CVE-2022-1210"

inherit autotools multilib_header

CACHED_CONFIGUREVARS = "ax_cv_check_gl_libgl=no"

PACKAGECONFIG ?= "cxx jpeg zlib lzma \
                  strip-chopping extrasample-as-alpha check-ycbcr-subsampling"

PACKAGECONFIG[cxx] = "--enable-cxx,--disable-cxx,,"
PACKAGECONFIG[jbig] = "--enable-jbig,--disable-jbig,jbig,"
PACKAGECONFIG[jpeg] = "--enable-jpeg,--disable-jpeg,jpeg,"
PACKAGECONFIG[zlib] = "--enable-zlib,--disable-zlib,zlib,"
PACKAGECONFIG[lzma] = "--enable-lzma,--disable-lzma,xz,"
PACKAGECONFIG[webp] = "--enable-webp,--disable-webp,libwebp,"
PACKAGECONFIG[zstd] = "--enable-zstd,--disable-zstd,zstd,"
PACKAGECONFIG[libdeflate] = "--enable-libdeflate,--disable-libdeflate,libdeflate,"

# Convert single-strip uncompressed images to multiple strips of specified
# size (default: 8192) to reduce memory usage
PACKAGECONFIG[strip-chopping] = "--enable-strip-chopping,--disable-strip-chopping,,"

# Treat a fourth sample with no EXTRASAMPLE_ value as being ASSOCALPHA
PACKAGECONFIG[extrasample-as-alpha] = "--enable-extrasample-as-alpha,--disable-extrasample-as-alpha,,"

# Control picking up YCbCr subsample info. Disable to support files lacking
# the tag
PACKAGECONFIG[check-ycbcr-subsampling] = "--enable-check-ycbcr-subsampling,--disable-check-ycbcr-subsampling,,"

# Support a mechanism allowing reading large strips (usually one strip files)
# in chunks when using TIFFReadScanline. Experimental 4.0+ feature
PACKAGECONFIG[chunky-strip-read] = "--enable-chunky-strip-read,--disable-chunky-strip-read,,"

PACKAGES =+ "tiffxx tiff-utils"
FILES:tiffxx = "${libdir}/libtiffxx.so.*"
FILES:tiff-utils = "${bindir}/*"

do_install:append() {
    oe_multilib_header tiffconf.h
}

BBCLASSEXTEND = "native nativesdk"
