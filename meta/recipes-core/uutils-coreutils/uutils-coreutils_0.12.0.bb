# Copyright (c) 2022, Snap Inc.
# Released under the MIT license (see COPYING.MIT for the terms)
SUMMARY = "coreutils ~ GNU coreutils (updated); implemented as universal (cross-platform) utils, written in Rust"
HOMEPAGE = "https://github.com/uutils/coreutils"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://LICENSE;md5=e74349878141b240070458d414ab3b64"

inherit cargo cargo-update-recipe-crates ptest-cargo

SRC_URI = " \
    git://github.com/uutils/coreutils.git;protocol=https;branch=main;tag=${PV} \
    file://run-ptest \
"

SRCREV = "dc1efd89948a9ca4c78c3a4b9a6ac891019a8c69"

require ${BPN}-crates.inc

PROVIDES = "coreutils"
RPROVIDES:${PN} = "coreutils"

# This recipe is a drop-in replacement for coreutils and cannot coexist with it,
# so only build it in world/universe when it is the selected provider. Otherwise
# both recipes end up in the same task graph as explicit targets (PREFERRED_PROVIDER
# only disambiguates dependencies, not targets) and bitbake warns that multiple
# .bb files provide coreutils.
EXCLUDE_FROM_WORLD = "${@'0' if d.getVar('PREFERRED_PROVIDER_coreutils') == 'uutils-coreutils' else '1'}"

PACKAGECONFIG ?= "${@bb.utils.filter('DISTRO_FEATURES', 'selinux systemd', d)}"
PACKAGECONFIG[selinux] = "--features feat_selinux,,clang-native libselinux-native libselinux"
PACKAGECONFIG[systemd] = "--features feat_systemd_logind,,systemd"

CARGO_BUILD_FLAGS += "--features unix --features feat_external_libstdbuf"

# Location of libstdbuf.so, built into the binary. Exported for all tasks so that
# the ptest build does not rebuild it with the default path.
export LIBSTDBUF_DIR = "${libdir}/coreutils"

# The code which follows is strongly inspired from the GNU coreutils bitbake recipe:

# df mktemp nice printenv base32 base64 get a special treatment and are not included in this
bindir_progs = "[ arch basename cksum comm csplit cut dir dircolors dirname du \
                env expand expr factor fmt fold groups head hostid id install \
                join link logname md5sum mkfifo nl nohup nproc od paste pathchk \
                pinky pr printf ptx readlink realpath seq sha1sum sha224sum sha256sum \
                sha384sum sha512sum shred shuf sort split stdbuf sum tac tail tee test timeout \
                tr truncate tsort tty unexpand uniq unlink uptime users vdir wc who whoami yes"

bindir_progs += "${@bb.utils.contains('PACKAGECONFIG', 'selinux', 'chcon runcon', '', d)}"

base_bindir_progs = "cat chgrp chmod chown cp date dd echo false hostname kill ln ls mkdir \
                     mknod mv pwd rm rmdir sleep stty sync touch true uname stat"

sbindir_progs = "chroot"

FILES:${PN}:append = " ${libdir}/coreutils/libstdbuf.so"
inherit update-alternatives

# Higher than busybox (which uses 50)
ALTERNATIVE_PRIORITY = "100"

# Higher than net-tools (which uses 100)
ALTERNATIVE_PRIORITY[hostname] = "110"

ALTERNATIVE:${PN} = "${bindir_progs} ${base_bindir_progs} ${sbindir_progs} base32 base64 nice printenv mktemp df"

# Use the multicall binary named "coreutils" for symlinks
ALTERNATIVE_TARGET = "${bindir}/coreutils"

python __anonymous() {
    for prog in d.getVar('base_bindir_progs').split():
        d.setVarFlag('ALTERNATIVE_LINK_NAME', prog, '%s/%s' % (d.getVar('base_bindir'), prog))

    for prog in d.getVar('sbindir_progs').split():
        d.setVarFlag('ALTERNATIVE_LINK_NAME', prog, '%s/%s' % (d.getVar('sbindir'), prog))
}

BBCLASSEXTEND = "native nativesdk"

do_configure:prepend() {
    rm -f ${S}/.cargo/config.toml
}

do_install:append() {
    mkdir -p ${D}${libdir}/coreutils
    cp ${B}/target/${CARGO_TARGET_SUBDIR}/deps/libstdbuf.so ${D}${libdir}/coreutils/libstdbuf.so
}

RDEPENDS:${PN}-ptest += "shadow tzdata"

# ptest-cargo also installs the coreutils binary itself, and runs it as a test.
# The tests in these test binaries run the coreutils binary from its location in
# the cargo build directory, so they cannot work on the target.
UUTILS_PTEST_REMOVE = "coreutils test_util_name-* test_localization_and_colors-*"

# The test binaries embed the path of the build directory (CARGO_BIN_EXE_*,
# CARGO_TARGET_TMPDIR) and the PATH used to compile them, which are not
# reproducible. Replace them with strings of the same length which are valid on
# the target, as the length of the strings is also part of the binaries. The
# tests get the binary to test from UUTESTS_BINARY_PATH.
python uutils_ptest_cleanup() {
    import glob
    import re
    ptest_path = d.getVar('PTEST_PATH')
    ptest_dir = d.getVar('D') + ptest_path
    script = os.path.join(ptest_dir, 'run-ptest')
    with open(script) as f:
        content = f.read()

    for pattern in d.getVar('UUTILS_PTEST_REMOVE').split():
        for path in glob.glob(os.path.join(ptest_dir, pattern)):
            name = os.path.basename(path)
            os.unlink(path)
            content = re.sub(r'if ! %s/%s( [^\n]*)?\nthen\n.*?\nfi\n' % (re.escape(ptest_path), re.escape(name)),
                             '', content, flags=re.S)
    with open(script, 'w') as f:
        f.write(content)

    # The PATH can only be replaced by a PATH with at least the same length
    search_path = b'/usr/bin:/bin'
    replacements = {}
    build_dir = d.getVar('B').encode()
    replacements[build_dir] = ptest_path.encode().ljust(len(build_dir), b'/')
    compile_path = d.getVar('PATH').encode()
    count, remainder = divmod(len(compile_path) - len(search_path), len(b':/usr/bin'))
    replacements[compile_path] = search_path + b':/usr/bin' * count + b'/' * remainder

    for name in os.listdir(ptest_dir):
        path = os.path.join(ptest_dir, name)
        if name == 'run-ptest' or not os.path.isfile(path):
            continue
        with open(path, 'rb') as f:
            data = f.read()
        new_data = data
        for old, new in replacements.items():
            assert len(old) == len(new)
            new_data = new_data.replace(old, new)
        if new_data != data:
            with open(path, 'wb') as f:
                f.write(new_data)
}
do_install_ptest_cargo[postfuncs] += "uutils_ptest_cleanup"

# Tests that cannot pass when the tests are run on the target:
# - they expect the coreutils binary to be in the cargo build directory
# - the common localization strings are only found when the locale files are
#   in the layout of the source tree, so translated errors are not available
# - they replace argv[0] of commands, which are links to the multicall binary
# - they need a login session, or a file system supporting reflink
# - they expect the shell to be killed by SIGQUIT, but busybox sh is not
# - they are racy: they look at temporary files while they are being deleted
RUST_TEST_ARGS = "\
    --skip test_stdbuf_search_order_exe_dir_first \
    --skip test_env_french \
    --skip test_localized_possible_values \
    --skip test_chmod_colored_output \
    --skip test_argument_suggestion \
    --skip test_clap_localization \
    --skip test_error_colors \
    --skip test_french_translations \
    --skip test_help_colors \
    --skip test_env_arg_argv0_overwrite \
    --skip test_env_overwrite_arg0 \
    --skip test_logname \
    --skip test_cp_reflink_always_override \
    --skip test_cp_reflink_never \
    --skip test_stdbuf_reports_signalled_command \
    --skip test_tmp_files_are_private \
"

do_install_ptest() {
    install -d ${D}${PTEST_PATH}/tests
    cp -r ${S}/tests/fixtures ${D}${PTEST_PATH}/tests/
    # The scripts are POSIX shell and are run as "sh script", so avoid depending on bash
    sed -i '1s|^#!/bin/bash$|#!/bin/sh|' ${D}${PTEST_PATH}/tests/fixtures/*/is_a_tty.sh
}

