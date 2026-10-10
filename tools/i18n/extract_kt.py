"""Collects every Urdu text in the Android Kotlin sources as translation keys.
String templates ($x, ${...}) become {0}, {1}... in the key."""
import json, os, re, sys
U = re.compile(r'[؀-ۿ]')
ESC = {'n': '\n', 't': '\t', 'r': '\r', '"': '"', "'": "'", '\\': '\\', '$': '$', 'b': '\b'}

def parse_string(s, i, raw):
    """s[i] is just after the opening quote(s). Returns (parts, end) where parts are
    ('t', text) or ('e', exprSource)."""
    parts, buf = [], []
    close = '"""' if raw else '"'
    while True:
        if s.startswith(close, i) and (not raw or not s.startswith('""""', i)):
            parts.append(('t', ''.join(buf)))
            return parts, i + len(close)
        c = s[i]
        if c == '\\' and not raw:
            n = s[i + 1]
            if n == 'u':
                buf.append(chr(int(s[i + 2:i + 6], 16))); i += 6
            else:
                buf.append(ESC.get(n, n)); i += 2
            continue
        if c == '$' and i + 1 < len(s) and (s[i + 1] == '{'):
            parts.append(('t', ''.join(buf))); buf = []
            depth, j = 1, i + 2
            while depth:
                if s[j] == '"':
                    raw2 = s.startswith('"""', j)
                    _, j = parse_string(s, j + (3 if raw2 else 1), raw2); continue
                if s[j] == '{': depth += 1
                elif s[j] == '}': depth -= 1
                j += 1
            parts.append(('e', s[i + 2:j - 1])); i = j; continue
        if c == '$' and i + 1 < len(s) and (s[i + 1].isalpha() or s[i + 1] == '_'):
            m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', s[i + 1:])
            parts.append(('t', ''.join(buf))); buf = []
            parts.append(('e', m.group(0))); i += 1 + len(m.group(0)); continue
        buf.append(c); i += 1

def scan(s, out, where):
    i, n = 0, len(s)
    while i < n:
        if s.startswith('//', i):
            i = s.find('\n', i); i = n if i < 0 else i; continue
        if s.startswith('/*', i):
            i = s.find('*/', i) + 2; continue
        if s[i] == "'":
            j = i + 1
            while s[j] != "'": j += 2 if s[j] == '\\' else 1
            i = j + 1; continue
        if s[i] == '"':
            raw = s.startswith('"""', i)
            parts, end = parse_string(s, i + (3 if raw else 1), raw)
            key, k = '', 0
            for t, v in parts:
                if t == 't': key += v
                else:
                    key += '{%d}' % k; k += 1
                    scan(v, out, where)
            if U.search(key):
                line = s.count('\n', 0, i) + 1
                out.setdefault(key, []).append('%s:%d' % (where, line))
            i = end; continue
        i += 1

out = {}
root = sys.argv[1]
for dp, _, fs in os.walk(root):
    for f in sorted(fs):
        if f.endswith('.kt'):
            p = os.path.join(dp, f)
            scan(open(p, encoding='utf8').read(), out, os.path.relpath(p, root))
json.dump(out, open(sys.argv[2], 'w', encoding='utf8'), ensure_ascii=False, indent=1)
print(len(out), 'keys')
