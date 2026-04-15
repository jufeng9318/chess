#!/usr/bin/env python3
"""
中国象棋 AI 自我测试框架

用法：
  python test/ucci_benchmark.py                          # 引擎自我对弈基准测试
  python test/ucci_benchmark.py --engine elephanteye.exe # 我们的AI vs ElephantEye
  python test/ucci_benchmark.py --our-depth 4 --opp-depth 6  # 不同深度对比
  python test/ucci_benchmark.py --games 50                # 跑50局
"""

import subprocess
import sys
import os
import time
import argparse
import random
import threading
from dataclasses import dataclass
from typing import Optional, Tuple, List

INITIAL_FEN = (
    "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/"
    "P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"
)

PIECE_MAP = {
    'k': ('BLACK', 'KING'),     'K': ('RED',   'KING'),
    'r': ('BLACK', 'CHARIOT'), 'R': ('RED',   'CHARIOT'),
    'n': ('BLACK', 'HORSE'),   'N': ('RED',   'HORSE'),
    'b': ('BLACK', 'ELEPHANT'),'B': ('RED',   'ELEPHANT'),
    'a': ('BLACK', 'ADVISOR'), 'A': ('RED',   'ADVISOR'),
    'c': ('BLACK', 'CANNON'),  'C': ('RED',   'CANNON'),
    'p': ('BLACK', 'PAWN'),    'P': ('RED',   'PAWN'),
}


@dataclass
class EngineResult:
    move: Optional[str]
    error: Optional[str]
    time_ms: float


class Engine:
    def name(self) -> str:
        raise NotImplementedError

    def close(self) -> None:
        raise NotImplementedError

    def go(self, depth: int = 6) -> EngineResult:
        raise NotImplementedError


class OurEngine(Engine):
    def __init__(self, depth: int = 6, classpath: str = None):
        self.depth = depth
        if classpath is None:
            classpath = os.path.abspath("target/classes").replace(os.sep, "/")
        self.classpath = classpath
        self.proc: Optional[subprocess.Popen] = None
        self._reader_thread: Optional[threading.Thread] = None
        self._output_buffer: List[str] = []
        self._buf_lock = threading.Lock()
        self._started = False
        self._start()

    def _start(self):
        java = os.environ.get("JAVA_HOME", "") + "/bin/java"
        if not os.path.exists(java):
            java = "java"
        cmd = [java, "-cp", self.classpath, "com.chess.ai.EngineCLI"]
        self.proc = subprocess.Popen(
            cmd, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, bufsize=1
        )
        self._reader_thread = threading.Thread(target=self._read_output, daemon=True)
        self._reader_thread.start()
        time.sleep(2.0)
        self.send("ucci")
        self._wait_for("ucciok", timeout=8.0)
        self._started = True

    def _read_output(self):
        while self.proc and self.proc.stdout:
            line = self.proc.stdout.readline()
            if not line:
                break
            with self._buf_lock:
                self._output_buffer.append(line.rstrip())

    def _wait_for(self, marker: str, timeout: float = 10.0) -> str:
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._buf_lock:
                for i, line in enumerate(self._output_buffer):
                    if marker in line:
                        self._output_buffer.pop(i)
                        return line
            time.sleep(0.05)
        raise TimeoutError(f"等待 '{marker}' 超时")

    def _drain(self) -> None:
        """Clear any stale lines left from previous commands."""
        with self._buf_lock:
            self._output_buffer.clear()

    def name(self) -> str:
        return "OurEngine"

    def send(self, cmd: str) -> None:
        if self.proc and self.proc.stdin:
            self.proc.stdin.write(cmd + "\n")
            self.proc.stdin.flush()

    def recv(self, timeout: float = 10.0) -> str:
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._buf_lock:
                for i, line in enumerate(self._output_buffer):
                    if line.startswith("bestmove ") or line.startswith("error ") or \
                       line == "readyok" or line == "ok":
                        self._output_buffer.pop(i)
                        return line
            time.sleep(0.05)
        raise TimeoutError("recv 超时")

    def setboard(self, fen: str) -> None:
        self._drain()
        self.send(f"setboard {fen}")
        try:
            self.recv(timeout=3.0)
        except TimeoutError:
            pass

    def setside(self, side: str) -> None:
        self._drain()
        self.send(f"setside {side}")
        try:
            self.recv(timeout=2.0)
        except TimeoutError:
            pass

    def go(self, depth: int = None) -> EngineResult:
        if depth is None:
            depth = self.depth
        t0 = time.time()
        self._drain()
        self.send(f"go depth {depth}")
        try:
            resp = self.recv(timeout=30.0)
            elapsed = (time.time() - t0) * 1000
            if resp.startswith("bestmove "):
                return EngineResult(move=resp.split()[1], error=None, time_ms=elapsed)
            else:
                return EngineResult(move=None, error=resp, time_ms=elapsed)
        except TimeoutError as e:
            return EngineResult(move=None, error=str(e), time_ms=(time.time()-t0)*1000)

    def close(self) -> None:
        if self.proc:
            try:
                self.send("quit")
                self.proc.terminate()
                self.proc.wait(timeout=2.0)
            except Exception:
                self.proc.kill()


class ElephantEyeEngine(Engine):
    def __init__(self, exe_path: str = "elephanteye.exe", depth: int = 8):
        self.depth = depth
        self.proc: Optional[subprocess.Popen] = None
        self._output_buffer: List[str] = []
        self._buf_lock = threading.Lock()
        self._started = False
        self._start(exe_path)

    def _start(self, exe_path: str):
        self.proc = subprocess.Popen(
            [exe_path], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, text=True, bufsize=1
        )
        t = threading.Thread(target=self._read_output, daemon=True)
        t.start()
        time.sleep(0.2)
        self.send("ucci")
        self._wait_for("ucciok", timeout=5.0)
        self._started = True

    def _read_output(self):
        while self.proc and self.proc.stdout:
            line = self.proc.stdout.readline()
            if not line:
                break
            with self._buf_lock:
                self._output_buffer.append(line.rstrip())

    def _wait_for(self, marker: str, timeout: float = 10.0) -> str:
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._buf_lock:
                for i, line in enumerate(self._output_buffer):
                    if marker in line:
                        self._output_buffer.pop(i)
                        return line
            time.sleep(0.05)
        raise TimeoutError(f"等待 '{marker}' 超时")

    def _drain(self) -> None:
        with self._buf_lock:
            self._output_buffer.clear()

    def name(self) -> str:
        return "ElephantEye"

    def send(self, cmd: str) -> None:
        if self.proc and self.proc.stdin:
            self.proc.stdin.write(cmd + "\n")
            self.proc.stdin.flush()

    def recv(self, timeout: float = 30.0) -> str:
        deadline = time.time() + timeout
        while time.time() < deadline:
            with self._buf_lock:
                for i, line in enumerate(self._output_buffer):
                    if line.startswith("bestmove ") or line.startswith("nobestmove") or \
                       "error" in line.lower():
                        self._output_buffer.pop(i)
                        return line
            time.sleep(0.05)
        raise TimeoutError("recv 超时")

    def setboard(self, fen: str) -> None:
        self._drain()
        self.send(f"position fen {fen}")
        time.sleep(0.1)

    def go(self, depth: int = None) -> EngineResult:
        if depth is None:
            depth = self.depth
        t0 = time.time()
        self._drain()
        self.send(f"go depth {depth}")
        try:
            resp = self.recv(timeout=30.0)
            elapsed = (time.time() - t0) * 1000
            if resp.startswith("bestmove "):
                return EngineResult(move=resp.split()[1], error=None, time_ms=elapsed)
            else:
                return EngineResult(move=None, error=resp, time_ms=elapsed)
        except TimeoutError as e:
            return EngineResult(move=None, error=str(e), time_ms=(time.time()-t0)*1000)

    def close(self) -> None:
        if self.proc:
            try:
                self.send("quit")
                self.proc.terminate()
                self.proc.wait(timeout=2.0)
            except Exception:
                self.proc.kill()


class RandomEngine(Engine):
    def name(self) -> str:
        return "Random"

    def close(self) -> None:
        pass

    def setboard(self, fen: str) -> None:
        self._fen = fen

    def setside(self, side: str) -> None:
        pass

    def go(self, depth: int = None) -> EngineResult:
        cols = list("abcdefghi")
        rows = list("0123456789")
        move = random.choice(cols) + random.choice(rows) + \
               random.choice(cols) + random.choice(rows)
        return EngineResult(move=move, error=None, time_ms=1.0)


def iccs_to_row_col(iccs: str) -> Tuple[int, int, int, int]:
    if len(iccs) != 4:
        raise ValueError(f"无效 ICCS: {iccs}")
    fc, fr, tc, tr = iccs[0], iccs[1], iccs[2], iccs[3]
    return int(fr), ord(fc) - ord('a'), int(tr), ord(tc) - ord('a')


def apply_move(fen: str, iccs_move: str, side_to_move: str) -> Tuple[str, str]:
    ranks = fen.split()[0].split("/")
    pieces = {}
    for ri, rank in enumerate(ranks):
        c = 8
        for ch in rank:
            if ch.isdigit():
                c -= int(ch)
            else:
                color, ptype = PIECE_MAP[ch]
                pieces[(ri, c)] = (color, ptype)
                c -= 1

    fr, fc, tr, tc = iccs_to_row_col(iccs_move)

    if (fr, fc) not in pieces:
        raise ValueError(f"着法起点无棋子: {iccs_move}")

    moving = pieces.pop((fr, fc))
    pieces[(tr, tc)] = moving

    new_ranks = []
    for ri in range(10):
        rank_str = ""
        c = 0
        while c < 9:
            if (ri, c) in pieces:
                color, ptype = pieces[(ri, c)]
                ch = {'KING': 'k', 'CHARIOT': 'r', 'HORSE': 'n',
                      'ELEPHANT': 'b', 'ADVISOR': 'a', 'CANNON': 'c', 'PAWN': 'p'}[ptype]
                if color == 'RED':
                    ch = ch.upper()
                rank_str += ch
                c += 1
            else:
                skip = 1
                while c + skip < 9 and (ri, c + skip) not in pieces:
                    skip += 1
                rank_str += str(skip)
                c += skip
        new_ranks.append(rank_str)

    new_side = "b" if side_to_move == "w" else "w"
    return "/".join(new_ranks) + f" {new_side} - 0 1", new_side


def play_game(red_engine: Engine, black_engine: Engine,
              red_depth: int, black_depth: int,
              max_moves: int = 200) -> Tuple[str, int, str]:
    fen = INITIAL_FEN
    side = "w"

    engines = {"w": red_engine, "b": black_engine}
    depths  = {"w": red_depth,  "b": black_depth}

    move_count = 0

    for _ in range(max_moves):
        engine = engines[side]

        if hasattr(engine, 'setboard'):
            engine.setboard(fen)
        else:
            engine.send(f"setboard {fen}")

        result = engine.go(depth=depths[side])

        if result.error or not result.move:
            winner = "BLACK" if side == "w" else "RED"
            return winner, move_count, f"engine error: {result.error}"

        try:
            fen, side = apply_move(fen, result.move, side)
            side = "b" if side == "b" else "w"
            move_count += 1
        except Exception as e:
            winner = "BLACK" if side == "w" else "RED"
            return winner, move_count, f"invalid move {result.move}: {e}"

        ranks = fen.split()[0]
        kings = sum(1 for r in ranks for ch in r if ch in 'kK')
        if kings == 0 or kings == 1:
            winner = "BLACK" if side == "w" else "RED"
            return winner, move_count, "king captured"

    return "DRAW", move_count, "max moves reached"


def benchmark(red_engine: Engine, black_engine: Engine,
             red_depth: int, black_depth: int,
             games: int = 20,
             verbose: bool = True) -> dict:
    results = {"RED_WIN": 0, "BLACK_WIN": 0, "DRAW": 0}
    total_moves = 0

    for i in range(1, games + 1):
        try:
            result, moves, desc = play_game(red_engine, black_engine, red_depth, black_depth)
            results[result] += 1
            total_moves += moves
            if verbose:
                print(f"第{i:3d}/{games}局: {result:10s}  {moves:3d}步  {desc}")
        except Exception as e:
            if verbose:
                print(f"第{i:3d}/{games}局: ERROR  {e}")
            results["DRAW"] += 1

    n = games
    print("\n" + "=" * 50)
    print(f"  {red_engine.name()} (红方, depth={red_depth})  vs  {black_engine.name()} (黑方, depth={black_depth})")
    print(f"  总局数: {n}")
    print(f"  红方胜: {results['RED_WIN']:3d} ({results['RED_WIN']*100/n:5.1f}%)")
    print(f"  黑方胜: {results['BLACK_WIN']:3d} ({results['BLACK_WIN']*100/n:5.1f}%)")
    print(f"  和  棋: {results['DRAW']:3d} ({results['DRAW']*100/n:5.1f}%)")
    print(f"  平均步数: {total_moves/max(n,1):.1f}")
    print("=" * 50)
    return results


def main():
    parser = argparse.ArgumentParser(description="中国象棋 AI 测试框架")
    parser.add_argument("--games",   type=int, default=20,  help="对弈局数")
    parser.add_argument("--our-depth",  type=int, default=6,  help="我们AI的搜索深度")
    parser.add_argument("--opp-depth",  type=int, default=None, help="对手搜索深度（默认同 ours）")
    parser.add_argument("--engine",  type=str, default="",   help="对手引擎路径（elephanteye.exe）")
    parser.add_argument("--classpath", type=str, default=None, help="Java classpath")
    parser.add_argument("--verbose",  action="store_true",  help="详细输出每局结果")
    args = parser.parse_args()

    opp_depth = args.opp_depth or args.our_depth

    print(f"[启动] 红方: OurEngine depth={args.our_depth}")
    red = OurEngine(depth=args.our_depth, classpath=args.classpath)

    if args.engine:
        if not os.path.exists(args.engine):
            print(f"错误: 找不到 ElephantEye: {args.engine}")
            sys.exit(1)
        print(f"[启动] 黑方: ElephantEye depth={opp_depth}")
        black = ElephantEyeEngine(exe_path=args.engine, depth=opp_depth)
    else:
        print(f"[启动] 黑方: OurEngine depth={opp_depth} (自我对弈)")
        black = OurEngine(depth=opp_depth, classpath=args.classpath)

    try:
        benchmark(red, black, args.our_depth, opp_depth,
                 games=args.games, verbose=True)
    finally:
        red.close()
        black.close()


if __name__ == "__main__":
    main()
