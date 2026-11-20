#!/usr/bin/env python3
"""Estimate the Hub preflight's main-code token count per file.

Same metric as runelite-plugin-dev's tools/tokens.py: comments stripped,
whitespace collapsed outside literals, chars/4, scaled by 204000/178162.
Also reports how many of each file's chars sit inside string literals so
data-heavy files can be ranked for a move into resources. Scratch tool.
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
CAL = 204_000 / 178_162


def comment_end(src, i):
    n = len(src)
    if src.startswith("//", i):
        j = src.find("\n", i)
        return n if j < 0 else j
    if src.startswith("/*", i):
        j = src.find("*/", i + 2)
        return n if j < 0 else j + 2
    return None


def literal_end(src, i):
    n = len(src)
    if src.startswith('"""', i):
        j = src.find('"""', i + 3)
        return n if j < 0 else j + 3
    quote = src[i]
    if quote not in "\"'":
        return None
    j = i + 1
    while j < n and src[j] != quote:
        j += 2 if src[j] == "\\" else 1
    return min(j + 1, n)


def strip(src):
    out = []
    lit = 0
    i, n = 0, len(src)
    pending_space = False
    while i < n:
        end = comment_end(src, i)
        if end is not None or src[i].isspace():
            pending_space = True
            i = end if end is not None else i + 1
            continue
        if pending_space and out:
            out.append(" ")
        pending_space = False
        le = literal_end(src, i)
        end = le or i + 1
        if le:
            lit += le - i
        out.append(src[i:end])
        i = end
    return "".join(out), lit


def main():
    rows = []
    for dp, _, fs in os.walk(ROOT):
        for f in fs:
            if f.endswith('.java'):
                s, lit = strip(open(os.path.join(dp, f), encoding='utf-8').read())
                rows.append((f, len(s), lit))
    rows.sort(key=lambda r: -r[1])
    tot = sum(r[1] for r in rows)
    tlit = sum(r[2] for r in rows)
    print(f"{'file':34} {'tokens':>8} {'litchars':>9} {'lit%':>5}")
    for f, c, lit in rows[:40]:
        print(f"{f:34} {c / 4 * CAL:8.0f} {lit:9} {100 * lit / c:5.1f}")
    print(f"{'TOTAL':34} {tot / 4 * CAL:8.0f} {tlit:9} {100 * tlit / tot:5.1f}")


if __name__ == '__main__':
    main()
