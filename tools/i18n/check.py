"""Lists Urdu texts in the code that have no English translation in i18n/en.json.
Prints GitHub warnings (does not fail the build): missing ones still show, just in Urdu."""
import json, sys
keys = set()
for f in sys.argv[1:-1]:
    for k, where in json.load(open(f, encoding='utf8')).items():
        # the translator itself, and the language buttons (shown in both languages on purpose)
        if all('i18n' in w.lower() for w in where) or k in ('اردو', 'Language / زبان'):
            continue
        keys.add(k)
en = json.load(open(sys.argv[-1], encoding='utf8'))
known = set(en) | {k.strip() for k in en}
skip = ('<!doctype',)
missing = sorted(k for k in keys if k not in known and k.strip() not in known and not k.startswith(skip))
for k in missing:
    print('::warning title=Missing English translation::' + k.replace('\n', '\\n'))
print(len(missing), 'texts without English translation')
