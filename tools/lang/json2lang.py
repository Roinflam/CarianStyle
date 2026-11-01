#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
从 1.20.1 分支的 en_us.json / zh_cn.json 生成 1.12.2 的 en_us.lang / zh_cn.lang。

用法（在仓库根目录）：
    python tools/lang/json2lang.py                 # 从本仓库的 1.20.1 分支读 json（git show）
    python tools/lang/json2lang.py --ref origin/1.20.1
    python tools/lang/json2lang.py --src <放 en_us.json / zh_cn.json 的目录>
    python tools/lang/json2lang.py --check         # 只检查现有的 .lang 是否和生成结果一致，不写文件

规则：
- 跳过 _comment 开头的键，其余键全部保留；
- 第一行写 #PARSE_ESCAPES，FML 会用 Properties 读（支持转义），文件不写 BOM；
- 值里的换行写成 \\n，开头的空格写成 "\\ "，反斜杠写成 \\\\；
- 1.12.2 的 I18n.format 一律走 String.format：只保留严格的占位符 %s、%d、%.Nf（可带 n$），
  其它 % 一律写成 %%（原文里已经写成 %% 的照旧）；
- 1.12.2 才有的键：item / entity 的 .name 键从 1.20.1 同名键派生，其余写在 overrides-1.12.2.json；
- 最后套用 overrides-1.12.2.json：{ "键": { "en_us": "...", "zh_cn": "..." } }，值按原文写（单个 %），同样经过上面的转换。
脚本最后自检：每个 % 要么是 %%、要么是白名单占位符；键里没有 : = 空白；否则报错退出。
"""
import argparse
import json
import os
import re
import subprocess
import sys
from collections import OrderedDict

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
OUT_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'carianstyle', 'lang')
OVERRIDES = os.path.join(HERE, 'overrides-1.12.2.json')
JSON_PATH_IN_REF = 'forge/src/main/resources/assets/carianstyle/lang/%s.json'
LOCALES = ('en_us', 'zh_cn')

# 严格占位符：%s、%d、%.2f，可带 1$ 这类位置参数；不带标志、不带宽度
PLACEHOLDER = re.compile(r'%(\d+\$)?(s|d|\.\d+f)')
# 1.12.2 才有、从 1.20.1 同名键派生的键
DERIVED = (
    ('item.carianstyle.glintblades', 'item.carianstyle.glintblades.name'),
    ('entity.carianstyle.glintblades', 'entity.carianstyle.glintblades.name'),
)


def load_json_text(text):
    return json.loads(text, object_pairs_hook=OrderedDict)


def read_source(locale, src, ref):
    if src:
        with open(os.path.join(src, locale + '.json'), encoding='utf-8') as f:
            return load_json_text(f.read())
    out = subprocess.check_output(['git', '-C', ROOT, 'show', '%s:%s' % (ref, JSON_PATH_IN_REF % locale)])
    return load_json_text(out.decode('utf-8'))


def escape_percent(value, key):
    """把不是严格占位符的 % 写成 %%"""
    out = []
    i = 0
    n = len(value)
    while i < n:
        c = value[i]
        if c != '%':
            out.append(c)
            i += 1
            continue
        if i + 1 < n and value[i + 1] == '%':
            out.append('%%')
            i += 2
            continue
        m = PLACEHOLDER.match(value, i)
        if m:
            out.append(m.group(0))
            i = m.end()
        else:
            out.append('%%')
            i += 1
    return ''.join(out)


def escape_properties(value):
    """Properties 格式的值转义：反斜杠、换行、回车、制表、开头的空格"""
    v = value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    if v.startswith(' '):
        v = '\\' + v
    return v


def check_value(key, value):
    """自检：转换后每个 % 要么是 %%、要么是白名单占位符"""
    i = 0
    n = len(value)
    while i < n:
        if value[i] == '%':
            if i + 1 < n and value[i + 1] == '%':
                i += 2
                continue
            m = PLACEHOLDER.match(value, i)
            if not m:
                raise SystemExit('自检失败：%s 的值里有不合规的 %%：%r' % (key, value))
            i = m.end()
            continue
        i += 1


def check_key(key):
    if re.search(r'[:=\s]', key):
        raise SystemExit('自检失败：键里不能有 : = 空白：%r' % key)


def build(locale, source, overrides):
    entries = OrderedDict()
    for k, v in source.items():
        if k.startswith('_comment'):
            continue
        if not isinstance(v, str):
            raise SystemExit('%s 的 %s 不是字符串' % (locale, k))
        entries[k] = v
    for src_key, new_key in DERIVED:
        if src_key not in entries:
            raise SystemExit('%s 缺少派生用的键 %s' % (locale, src_key))
        entries[new_key] = entries[src_key]
    for k, per_locale in overrides.items():
        if k.startswith('_comment'):
            continue
        if locale not in per_locale:
            raise SystemExit('覆盖文件里 %s 缺少 %s' % (k, locale))
        entries[k] = per_locale[locale]
    lines = ['#PARSE_ESCAPES']
    for k, v in entries.items():
        check_key(k)
        v2 = escape_percent(v, k)
        check_value(k, v2)
        lines.append('%s=%s' % (k, escape_properties(v2)))
    return '\n'.join(lines) + '\n', len(entries)


def main():
    ap = argparse.ArgumentParser(description='1.20.1 json 语言文件 → 1.12.2 .lang')
    ap.add_argument('--src', help='放 en_us.json / zh_cn.json 的目录（不给就从 git 分支读）')
    ap.add_argument('--ref', default='1.20.1', help='读 json 的分支或提交，默认 1.20.1')
    ap.add_argument('--check', action='store_true', help='只核对现有 .lang 是否和生成结果一致')
    args = ap.parse_args()

    with open(OVERRIDES, encoding='utf-8') as f:
        overrides = load_json_text(f.read())

    counts = {}
    sources = {}
    for locale in LOCALES:
        sources[locale] = read_source(locale, args.src, args.ref)
    keysets = {loc: set(k for k in src if not k.startswith('_comment')) for loc, src in sources.items()}
    if keysets['en_us'] != keysets['zh_cn']:
        only_en = sorted(keysets['en_us'] - keysets['zh_cn'])
        only_zh = sorted(keysets['zh_cn'] - keysets['en_us'])
        print('提示：两种语言的键不完全一致；只在英文里的 %d 个，只在中文里的 %d 个' % (len(only_en), len(only_zh)))

    failed = False
    for locale in LOCALES:
        text, count = build(locale, sources[locale], overrides)
        counts[locale] = count
        path = os.path.join(OUT_DIR, locale + '.lang')
        if args.check:
            try:
                with open(path, 'rb') as f:
                    # core.autocrlf 检出时会换成 CRLF，比对前统一成 LF
                    current = f.read().decode('utf-8').replace('\r\n', '\n')
            except IOError:
                current = None
            if current != text:
                print('不一致：%s（重新跑一次脚本）' % os.path.relpath(path, ROOT))
                failed = True
        else:
            os.makedirs(OUT_DIR, exist_ok=True)
            with open(path, 'wb') as f:
                f.write(text.encode('utf-8'))
    for locale in LOCALES:
        print('%s：%d 条' % (locale, counts[locale]))
    if failed:
        sys.exit(1)
    print('自检通过')


if __name__ == '__main__':
    main()
