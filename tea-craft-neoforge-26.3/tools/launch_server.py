"""自己拼一个 26.3 NeoForge **专用服务端**跑 `/bubble selftest`（本支唯一的游戏内验证手段）。

<h2>为什么不用 gradlew runServer</h2>

本支 `runServer` / `prepareServerRun` 走不通：ModDevGradle 要下
`neoforge-26.3.0.67-beta-moddev-config.json`，那个站连不上、离线缓存里也没有
（实测报错：`Could not download neoforge-26.3.0.67-beta-moddev-config.json`）。
1.21.1 那支是同样的问题，所以这边的做法照抄它：**自己把 classpath 拼起来**。

<h2>26.3 比 1.21.1 简单在哪</h2>

读 `neoforge-26.3.0.67-beta-userdev.jar` 里的 `config.json`：

* `modules` 是**空数组**（1.21.1 有 8 个模块要解析）；
* `runs.server` 是 `main = net.neoforged.fml.startup.Server`、`args = []`；
* 不需要 `-DlegacyClassPath.file=...`（1.21.1 需要，而且要用 gradle 任务生成那个文件）。

所以这里只要：**classpath（由 gradle 任务 `printRuntimeClasspath` 离线导出）+ 一个系统属性**。

<h2>用法</h2>

    .\\gradlew.bat build printRuntimeClasspath --offline   # 各跑一次即可
    python tools/launch_server.py --selftest --watch 420
"""
import argparse
import json
import os
import socket
import struct
import subprocess
import sys
import time
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
NF = HERE.parent
WS = NF.parent
CACHE = WS / "mcmod" / ".gradle-home" / "caches" / "modules-2" / "files-2.1"
RUN_DIR = WS / "mcmod" / "run-teacraft-nf263-server"
CP_FILE = NF / "build" / "runtime-classpath.txt"

JAVA = WS / "mcmod" / "tools" / "jdk25" / "bin" / "java.exe"
RCON_PORT = 25588
RCON_PASS = "nf263test"
SERVER_PORT = "25568"


def find_userdev():
    hits = list(CACHE.glob("net.neoforged/neoforge/26.3.0.67-beta/*/neoforge-26.3.0.67-beta-userdev.jar"))
    if not hits:
        raise SystemExit("!! 缓存里找不到 neoforge-26.3.0.67-beta-userdev.jar")
    return hits[0]


def build_command(run, classes, resources):
    cp_entries = [e for e in CP_FILE.read_text(encoding="utf-8").split(os.pathsep) if e.strip()]
    # 模组自己的 class 与资源：靠 fml.modFolders 告诉 FML（格式取自
    # InDevFolderLocator：modid%%路径，用 File.pathSeparator 连接）
    mod_folders = os.pathsep.join(["teacraft%%" + str(classes),
                                   "teacraft%%" + str(resources)])
    cmd = [str(JAVA)]
    for a in run["jvmArgs"]:
        cmd.append(a)
    cmd += ["-cp", os.pathsep.join(cp_entries)]
    cmd.append("-Dfml.modFolders=" + mod_folders)
    cmd.append("-Dfml.earlyprogresswindow=false")
    for k, v in (run.get("props") or {}).items():
        cmd.append("-D%s=%s" % (k, v))
    cmd.append(run["main"])
    cmd += [str(a) for a in run.get("args", [])]
    cmd.append("--nogui")
    return cmd


def write_server_props():
    RUN_DIR.mkdir(parents=True, exist_ok=True)
    (RUN_DIR / "eula.txt").write_text("eula=true\n", encoding="ascii")
    p = RUN_DIR / "server.properties"
    if not p.exists():
        lines = {
            "level-name": "world",
            "level-type": "bubbleworld:bubble_world",
            "online-mode": "false",
            "gamemode": "creative",
            "spawn-protection": "0",
            "enable-rcon": "true",
            "rcon.port": str(RCON_PORT),
            "rcon.password": RCON_PASS,
            "server-port": SERVER_PORT,
            "motd": "BubbleWorld nf-26.3 dev server",
            "max-players": "2",
            "view-distance": "6",
            "sync-chunk-writes": "false",
        }
        p.write_text("".join("%s=%s\n" % kv for kv in lines.items()), encoding="ascii")
    return p


class Rcon:
    """最小 RCON 客户端（本支独立实现）。"""

    def __init__(self, port=RCON_PORT, password=RCON_PASS, timeout=180):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
        self.rid = 0
        self._send(3, password)
        self._recv()

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            c = self.sock.recv(n - len(buf))
            if not c:
                raise EOFError("rcon closed")
            buf += c
        return buf

    def _send(self, typ, body):
        self.rid += 1
        d = struct.pack("<ii", self.rid, typ) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(d)) + d)

    def _recv(self):
        original = self.sock.gettimeout()
        chunks = []
        try:
            while True:
                try:
                    (n,) = struct.unpack("<i", self._read(4))
                except (socket.timeout, TimeoutError, EOFError):
                    if not chunks:
                        raise
                    break
                chunks.append(self._read(n)[8:-2].decode("utf-8", "replace"))
                self.sock.settimeout(0.5)
        finally:
            self.sock.settimeout(original)
        return "".join(chunks)

    def cmd(self, c):
        self._send(2, c)
        return self._recv()

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def wait_for(marker, log, limit):
    t0 = time.time()
    while time.time() - t0 < limit:
        try:
            if marker in log.read_text(encoding="utf-8", errors="replace"):
                return time.time() - t0
        except FileNotFoundError:
            pass
        time.sleep(2)
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--watch", type=int, default=420)
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("--keep", action="store_true")
    args = ap.parse_args()

    if not CP_FILE.exists():
        raise SystemExit("!! 缺 %s\n   先跑: .\\gradlew.bat printRuntimeClasspath --offline" % CP_FILE)
    with zipfile.ZipFile(find_userdev()) as z:
        cfg = json.loads(z.read("config.json").decode("utf-8"))
    run = cfg["runs"]["server"]

    classes = NF / "build" / "classes" / "java" / "main"
    resources = NF / "build" / "resources" / "main"
    for p in (classes, resources):
        if not p.exists():
            raise SystemExit("!! 缺 %s，先跑 gradlew build" % p)

    write_server_props()
    cmd = build_command(run, classes, resources)
    log = RUN_DIR / "server-out.txt"
    if log.exists():
        try:
            log.unlink()
        except PermissionError:
            # 上一轮的服务端还占着这个文件（真踩过：进程没退干净 → 第二次启动直接崩，
            # 报的却是 PermissionError，看着像环境问题）。换个带时间戳的名字继续。
            log = RUN_DIR / ("server-out-%d.txt" % time.time())
    print("=== run dir: %s ===" % RUN_DIR)
    print("=== 命令长度 %d ===" % len(" ".join(cmd)))
    print("=== 启动 ===")
    with open(log, "w", encoding="utf-8", errors="replace") as fh:
        proc = subprocess.Popen(cmd, cwd=str(RUN_DIR), stdout=fh, stderr=subprocess.STDOUT)
        print("  pid=%d  日志=%s" % (proc.pid, log))
        t = wait_for("RCON running", log, args.watch)
        if t is None:
            print("!! 服务端没起（没看到 'RCON running'）。日志尾部：")
            print(log.read_text(encoding="utf-8", errors="replace")[-5000:])
            if not args.keep:
                proc.terminate()
            return 1
        print("  服务端就绪，用时 %.0fs" % t)
        if args.selftest:
            # 超时给宽：自检最后一条 nether_chest_world 会真的生成 1000+ 区块
            #（新起一片没生成过的下界格子），默认 180s 会被它拖爆。
            r = Rcon(timeout=1800)
            try:
                for c in ("bubble worldtype", "bubble selftest", "bubble lang"):
                    print("\n======== /%s ========" % c)
                    print(r.cmd(c))
            finally:
                r.close()
        if not args.keep:
            print("\n=== 停服 ===")
            try:
                Rcon().cmd("stop")
            except Exception as e:                      # noqa: BLE001
                print("  rcon stop 失败(%r)，直接结束进程" % (e,))
                proc.terminate()
            try:
                proc.wait(timeout=90)
            except subprocess.TimeoutExpired:
                proc.kill()
    return 0


if __name__ == "__main__":
    sys.exit(main())
