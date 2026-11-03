package com.jarves.mh.access

import java.io.File

/**
 * Installs the `mh-screen` helper into the guest runtime.
 *
 * The agent lives inside PRoot and cannot call Android APIs, so it talks to the app through
 * the mailbox in [ScreenActionFiles]. This helper is the only thing the guest needs, and it
 * is plain text: the user can read exactly what it sends.
 */
object ScreenBridgeInstaller {

    private const val VERSION = "2"
    private const val WRAPPER_PATH = "usr/local/bin/mh-screen"
    private const val SCRIPT_PATH = "usr/local/lib/mh-screen.js"
    private const val MARKER_PATH = "usr/local/lib/.mh-screen-version"

    /** Host side of `/pocket-bridge`; the guest sees the same files under that path. */
    const val GUEST_MAILBOX = "/pocket-bridge/screen"
    const val HOST_MAILBOX = "runtime-bridge/screen"

    private val SCRIPT = """
        #!/usr/local/bin/node
        'use strict';
        const fs = require('fs');
        const path = require('path');
        const crypto = require('crypto');

        // Fixed mailbox, independent of the current directory: the agent may `cd` anywhere
        // (a subfolder, /tmp, a git worktree) and a ${'$'}PWD-relative mailbox silently stranded
        // every request made from outside the project root.
        const ROOT = (process.env.MH_SCREEN_DIR || '').trim() || '/pocket-bridge/screen';

        const [kindRaw, ...rest] = process.argv.slice(2);
        if (!kindRaw) {
          console.error('用法: mh-screen <tap|swipe|back|home|recents|type|scroll-forward|scroll-backward|wait> [参数]');
          process.exit(2);
        }

        const kind = kindRaw.trim().toUpperCase().replace(/-/g, '_');
        const body = {id: 'req-' + Date.now() + '-' + crypto.randomBytes(3).toString('hex'), kind};
        const num = (v, d) => { const n = parseInt(v, 10); return Number.isFinite(n) ? n : d; };

        switch (kind) {
          case 'TAP':
            body.x = num(rest[0], -1); body.y = num(rest[1], -1);
            if (body.x < 0 || body.y < 0) { console.error('用法: mh-screen tap <x> <y> [reason]'); process.exit(2); }
            body.reason = rest.slice(2).join(' ');
            break;
          case 'SWIPE':
            body.x = num(rest[0], -1); body.y = num(rest[1], -1);
            body.x2 = num(rest[2], -1); body.y2 = num(rest[3], -1);
            body.durationMs = num(rest[4], 220);
            if ([body.x, body.y, body.x2, body.y2].some(v => v < 0)) {
              console.error('用法: mh-screen swipe <x1> <y1> <x2> <y2> [毫秒]');
              process.exit(2);
            }
            break;
          case 'TYPE':
            body.text = rest.join(' ');
            if (!body.text) { console.error('用法: mh-screen type <文字>'); process.exit(2); }
            break;
          case 'WAIT':
            body.durationMs = num(rest[0], 500);
            break;
          case 'BACK': case 'HOME': case 'RECENTS':
          case 'SCROLL_FORWARD': case 'SCROLL_BACKWARD':
            body.reason = rest.join(' ');
            break;
          default:
            console.error('未知动作: ' + kindRaw);
            process.exit(2);
        }

        if (!fs.existsSync('/pocket-bridge')) {
          console.error('运行时桥没有挂载（找不到 /pocket-bridge），请重新打开这个应用');
          process.exit(4);
        }

        const reqDir = path.join(ROOT, 'requests');
        const resDir = path.join(ROOT, 'results');
        try {
          fs.mkdirSync(reqDir, {recursive: true});
          fs.mkdirSync(resDir, {recursive: true});
        } catch (e) {
          console.error('无法创建邮箱目录 ' + reqDir + ': ' + e.message);
          process.exit(4);
        }

        const reqFile = path.join(reqDir, body.id + '.json');
        const tmpFile = reqFile + '.tmp';
        fs.writeFileSync(tmpFile, JSON.stringify(body, null, 2));
        fs.renameSync(tmpFile, reqFile);

        // Blocking sleep without spawning a process: the old version forked `sleep` five
        // times a second for up to two minutes.
        const sleeper = new Int32Array(new SharedArrayBuffer(4));
        const sleep = (ms) => { try { Atomics.wait(sleeper, 0, 0, ms); } catch (e) {} };

        const resFile = path.join(resDir, body.id + '.json');
        const deadline = Date.now() + 120000;
        while (Date.now() < deadline) {
          if (fs.existsSync(resFile)) {
            const result = JSON.parse(fs.readFileSync(resFile, 'utf8'));
            try { fs.unlinkSync(resFile); } catch (e) {}
            console.log(JSON.stringify(result, null, 2));
            process.exit(result.status === 'OK' ? 0 : 1);
          }
          sleep(200);
        }
        console.error('等待用户确认超时（120 秒），邮箱: ' + ROOT);
        try { fs.unlinkSync(reqFile); } catch (e) {}
        process.exit(3);
    """.trimIndent()

    private val WRAPPER = """
        #!/usr/bin/env bash
        if [ ! -x /usr/local/bin/node ]; then
          echo "mh-screen: 运行时里找不到 node" >&2
          exit 4
        fi
        exec /usr/local/bin/node /usr/local/lib/mh-screen.js "${'$'}@"
    """.trimIndent()

    /** Returns true when the helper is present and current. */
    fun isInstalled(rootfs: File): Boolean {
        val marker = File(rootfs, MARKER_PATH)
        return marker.isFile && marker.readTextOrNull() == VERSION &&
            File(rootfs, SCRIPT_PATH).isFile && File(rootfs, WRAPPER_PATH).isFile
    }

    /** Writes the helper into the guest filesystem. Safe to call repeatedly. */
    fun install(rootfs: File): Boolean = runCatching {
        File(rootfs, "usr/local/lib").mkdirs()
        File(rootfs, "usr/local/bin").mkdirs()
        val script = File(rootfs, SCRIPT_PATH)
        script.writeText(SCRIPT)
        script.setExecutable(true, false)
        val wrapper = File(rootfs, WRAPPER_PATH)
        wrapper.writeText(WRAPPER)
        wrapper.setExecutable(true, false)
        File(rootfs, MARKER_PATH).writeText(VERSION)
        true
    }.getOrDefault(false)

    private fun File.readTextOrNull(): String? = runCatching { readText().trim() }.getOrNull()

    /** Shown in settings so the user knows what to tell the agent. */
    const val USAGE_HINT = "mh-screen tap 540 1200 · swipe 540 1600 540 600 · type \"文字\" · back · home"
}
