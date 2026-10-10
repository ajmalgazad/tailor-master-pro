// Collects every Urdu text in the web sources as translation keys.
// Template literals become {0}, {1}...; JSX text with {expr} children becomes one key too.
const ts = require(process.argv[4] || '../../web/node_modules/typescript')
const fs = require('fs'), path = require('path')
const U = /[؀-ۿ]/
const out = {}
const add = (k, where) => { if (U.test(k)) (out[k] ||= []).push(where) }
const norm = s => s.replace(/\s+/g, ' ').trim()
function walkDir(d) { for (const f of fs.readdirSync(d)) { const p = path.join(d, f); fs.statSync(p).isDirectory() ? walkDir(p) : /\.tsx?$/.test(f) && file(p) } }
function file(p) {
  const src = fs.readFileSync(p, 'utf8')
  const sf = ts.createSourceFile(p, src, ts.ScriptTarget.Latest, true, p.endsWith('x') ? ts.ScriptKind.TSX : ts.ScriptKind.TS)
  const where = n => path.relative(process.argv[2], p) + ':' + (sf.getLineAndCharacterOfPosition(n.getStart()).line + 1)
  function visit(n) {
    if (ts.isStringLiteral(n) || ts.isNoSubstitutionTemplateLiteral(n)) add(n.text, where(n))
    else if (ts.isTemplateExpression(n)) {
      let k = n.head.text
      n.templateSpans.forEach((sp, i) => { k += `{${i}}` + sp.literal.text })
      add(k, where(n))
    } else if (ts.isJsxElement(n) || ts.isJsxFragment(n)) {
      // whole run of text + {expr} children -> one key (what the person sees as one sentence)
      let k = '', i = 0, has = false
      const flush = () => { if (has && U.test(k)) add(norm(k), where(n)); k = ''; i = 0; has = false }
      for (const c of n.children) {
        if (ts.isJsxText(c)) { if (U.test(c.text)) has = true; k += c.text }
        else if (ts.isJsxExpression(c) && c.expression && !ts.isStringLiteral(c.expression)) { k += `{${i++}}` }
        else flush()
      }
      flush()
      n.children.forEach(c => { if (ts.isJsxText(c) && U.test(c.text)) add(norm(c.text), where(c)) })
    }
    ts.forEachChild(n, visit)
  }
  visit(sf)
}
walkDir(process.argv[2])
fs.writeFileSync(process.argv[3], JSON.stringify(out, null, 1))
console.log(Object.keys(out).length, 'keys')
