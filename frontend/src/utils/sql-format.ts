import { format as sqlFormat } from 'sql-formatter'

const CLAUSE_KEYWORDS = new Set([
  'SELECT', 'FROM', 'WHERE', 'ON',
  'GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT',
  'INSERT', 'INTO', 'VALUES', 'UPDATE', 'SET', 'DELETE',
  'UNION', 'UNION ALL',
])

function isJoinLine(t: string): boolean {
  return /^\s*((LEFT|RIGHT|INNER|CROSS|FULL)\s+)?JOIN\b/i.test(t)
}

function isKeywordLine(t: string): boolean {
  const u = t.trim().toUpperCase()
  if (CLAUSE_KEYWORDS.has(u)) return true
  if (isJoinLine(t)) return true
  return false
}

function isClauseStarter(t: string): boolean {
  const u = t.trim().toUpperCase()
  if (u === 'ON') return true
  if (isJoinLine(t)) return true
  return CLAUSE_KEYWORDS.has(u)
}

const COL = 8

function padKw(kw: string): string {
  if (kw.length >= COL) return kw + ' '
  return kw + ' '.repeat(COL - kw.length)
}

export function formatAlibaba(sql: string, dialect: 'MYSQL' | 'HIVE' = 'MYSQL'): string {
  const base = sqlFormat(sql, {
    language: dialect === 'HIVE' ? 'hive' : 'mysql',
    keywordCase: 'upper',
    tabWidth: 4,
  })
  const lines = base.split('\n')
  const out = processLines(lines, 0)
  return out.join('\n')
}

/**
 * Recursively process a list of lines at a given indent level.
 * Lines come from sql-formatter; we restructure them into Alibaba style.
 */
function processLines(lines: string[], baseIndent: number): string[] {
  const result: string[] = []
  let i = 0

  while (i < lines.length) {
    const raw = lines[i]
    const trimmed = raw.trim()
    const indent = ' '.repeat(baseIndent)

    // ── Subquery: standalone '(' ──
    if (trimmed === '(') {
      const subLines: string[] = []
      let depth = 1
      i++
      while (i < lines.length && depth > 0) {
        const t = lines[i].trim()
        if (t === '(') depth++
        // Check for closing paren (possibly with alias after, like ") b")
        const closeMatch = t.match(/^\)/)
        if (closeMatch) {
          depth--
          if (depth === 0) break
        }
        subLines.push(lines[i])
        i++
      }

      // Line with the closing ") alias"
      const closeRaw = i < lines.length ? lines[i] : ''
      const closeTrimmed = closeRaw.trim()

      // Recursively format subquery content at +4 indent
      const formattedSub = processLines(subLines, baseIndent + 4)

      result.push(indent + '(')
      for (const s of formattedSub) result.push(s)
      // closing line at same indent as the opening
      if (closeTrimmed) result.push(indent + closeTrimmed)
      i++
      continue
    }

    // ── SELECT ──
    if (trimmed === 'SELECT') {
      // Collect all lines until next clause keyword, tracking paren depth
      // to handle CASE ... END, function calls, and subqueries in SELECT
      const rawLines: string[] = []
      let j = i + 1
      let parenDepth = 0
      while (j < lines.length) {
        const nt = lines[j].trim()
        if (parenDepth === 0 && isKeywordLine(nt)) break
        for (const ch of nt) {
          if (ch === '(') parenDepth++
          if (ch === ')') parenDepth--
        }
        rawLines.push(lines[j])
        j++
        // Statement boundary: ; at depth 0 means the current SELECT is done
        if (parenDepth === 0 && nt.endsWith(';')) break
      }
      // Group lines into columns by trailing comma at depth 0
      // This keeps multi-line expressions (CASE ... END, CONCAT(...)) as single columns
      const cols: string[] = []
      let cur: string[] = []
      parenDepth = 0
      for (const line of rawLines) {
        const t = line.trim()
        for (const ch of t) {
          if (ch === '(') parenDepth++
          if (ch === ')') parenDepth--
        }
        cur.push(t)
        if (t.endsWith(',') && parenDepth === 0) {
          cols.push(cur.join(' ').replace(/,$/, '').trim())
          cur = []
        }
      }
      if (cur.length > 0) cols.push(cur.join(' ').trim())
      if (cols.length > 0) {
        result.push(indent + padKw('SELECT') + cols[0])
        for (let k = 1; k < cols.length; k++) {
          result.push(indent + ' '.repeat(COL - 1) + ', ' + cols[k])
        }
        i = j
        continue
      }
    }

    // ── FROM (handles both regular and subquery) ──
    if (trimmed === 'FROM') {
      // Check if next non-empty line starts a subquery
      let nextLineIsSubquery = false
      for (let scan = i + 1; scan < lines.length; scan++) {
        const st = lines[scan].trim()
        if (st === '') continue
        if (st === '(') { nextLineIsSubquery = true }
        break
      }
      if (nextLineIsSubquery) {
        result.push(indent + 'FROM')
        i++
        continue
      }
      const r = collectParts(lines, i + 1)
      if (r && r.length > 0) {
        result.push(indent + padKw('FROM') + r.join(' '))
        i += r.length + 1
        continue
      }
    }

    // ── WHERE (first cond on WHERE line, rest with leading AND/OR) ──
    if (trimmed === 'WHERE') {
      const r = collectParts(lines, i + 1)
      if (r && r.length > 0) {
        result.push(indent + padKw('WHERE') + r[0])
        for (let k = 1; k < r.length; k++) {
          const c = r[k].trim()
          if (/^(AND|OR)\b/i.test(c)) {
            const conn = c.match(/^(AND|OR)/i)![1].toUpperCase()
            const rest = c.slice(conn.length).trim()
            result.push(indent + padKw(conn) + rest)
          } else {
            result.push(indent + ' '.repeat(COL) + c)
          }
        }
        i += r.length + 1
        continue
      }
    }

    // ── JOIN keywords (standalone line from sql-formatter) ──
    if (/^((LEFT|RIGHT|INNER|CROSS|FULL)\s+)?JOIN$/i.test(trimmed)) {
      const joinKw = trimmed.toUpperCase()
      const r = collectJoinParts(lines, i + 1)
      if (r) {
        result.push(indent + padKw(joinKw) + r.table)
        if (r.on) {
          result.push(indent + padKw('ON') + r.on)
        }
        i = r.nextIndex
        continue
      }
    }

    // ── JOIN inline with table/ON on same line ──
    if (isJoinLine(raw)) {
      const jm = raw.trim().match(/^((LEFT|RIGHT|INNER|CROSS|FULL\s+)?JOIN)\s+(.*)/i)
      if (jm) {
        const joinKw = jm[1].toUpperCase()
        let rest = jm[3]
        let onPart = ''
        const onIdx = rest.search(/\s+ON\s+/i)
        if (onIdx !== -1) {
          onPart = rest.slice(onIdx + 4).trim()
          rest = rest.slice(0, onIdx)
        }
        result.push(indent + padKw(joinKw) + rest)
        if (onPart) result.push(indent + padKw('ON') + onPart)
        i++
        continue
      }
    }

    // ── ON (standalone) ──
    if (trimmed === 'ON') {
      const r = collectParts(lines, i + 1)
      if (r && r.length > 0) {
        result.push(indent + padKw('ON') + r.join(' '))
        i += r.length + 1
        continue
      }
    }

    // ── GROUP BY / ORDER BY / HAVING / LIMIT ──
    if (['GROUP BY', 'ORDER BY', 'HAVING', 'LIMIT'].includes(trimmed)) {
      const r = collectParts(lines, i + 1)
      if (r && r.length > 0) {
        result.push(indent + padKw(trimmed) + r.join(' '))
        i += r.length + 1
        continue
      }
    }

    // ── Other clause keywords ──
    if (CLAUSE_KEYWORDS.has(trimmed)) {
      const r = collectParts(lines, i + 1)
      if (r && r.length > 0) {
        result.push(indent + padKw(trimmed) + r.join(' '))
        i += r.length + 1
        continue
      }
    }

    // Default: pass-through (keeps content like subquery inner lines)
    result.push(raw)
    i++
  }

  return result
}

/** Collect content lines until a clause-starter keyword or subquery paren */
function collectParts(lines: string[], startIdx: number): string[] | null {
  if (startIdx >= lines.length) return null
  const parts: string[] = []
  let i = startIdx
  while (i < lines.length) {
    const t = lines[i].trim()
    if (t === '(') break
    if (isClauseStarter(t)) break
    parts.push(t)
    i++
  }
  return parts.length > 0 ? parts : null
}

/** Collect table name and ON condition after a standalone JOIN keyword line */
function collectJoinParts(
  lines: string[],
  startIdx: number
): { table: string; on: string; nextIndex: number } | null {
  if (startIdx >= lines.length) return null
  const tableParts: string[] = []
  let i = startIdx
  while (i < lines.length) {
    const t = lines[i].trim()
    if (t === '(') break
    const u = t.toUpperCase()
    if (u === 'ON') {
      i++
      const onParts: string[] = []
      while (i < lines.length) {
        const ot = lines[i].trim()
        if (ot === '(') break
        if (isClauseStarter(ot) && ot.toUpperCase() !== 'ON') break
        onParts.push(ot)
        i++
      }
      return { table: tableParts.join(' '), on: onParts.join(' '), nextIndex: i }
    }
    if (isClauseStarter(t)) break
    tableParts.push(t)
    i++
  }
  if (tableParts.length === 0) return null
  return { table: tableParts.join(' '), on: '', nextIndex: i }
}
