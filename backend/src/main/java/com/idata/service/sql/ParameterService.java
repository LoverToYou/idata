package com.idata.service.sql;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.idata.dto.ParameterRequest;
import com.idata.dto.ParameterVO;
import com.idata.entity.Parameter;
import com.idata.mapper.ParameterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ParameterService {

    private static final Logger log = LoggerFactory.getLogger(ParameterService.class);
    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$\\{(\\w+)}");

    /** Built-in dynamic keywords that work without a DB parameter entry. */
    private static final Set<String> BUILTIN_KEYWORDS = Set.of(
            "now", "today", "yesterday", "tomorrow",
            "this_month", "last_month", "this_year",
            "yyyyMMdd", "yyyy-MM-dd", "yyyyMM", "yyyy-MM", "yyyy",
            "this_month_start", "this_month_end",
            "last_month_start", "last_month_end"
    );

    private final ParameterMapper parameterMapper;

    public ParameterService(ParameterMapper parameterMapper) {
        this.parameterMapper = parameterMapper;
    }

    // ---- CRUD ----

    public List<ParameterVO> listAll() {
        return parameterMapper.selectList(
                new LambdaQueryWrapper<Parameter>()
                        .orderByDesc(Parameter::getUpdatedAt)
                )
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    public ParameterVO getById(Long id) {
        Parameter p = parameterMapper.selectById(id);
        if (p == null) {
            throw new IllegalArgumentException("参数不存在: " + id);
        }
        return toVO(p);
    }

    public ParameterVO create(ParameterRequest req) {
        // check duplicate name
        Long count = parameterMapper.selectCount(
                new LambdaQueryWrapper<Parameter>().eq(Parameter::getParamName, req.getParamName())
        );
        if (count > 0) {
            throw new IllegalArgumentException("参数名已存在: " + req.getParamName());
        }

        Parameter p = new Parameter();
        p.setParamName(req.getParamName());
        p.setParamValue(req.getParamValue());
        p.setParamType(req.getParamType());
        p.setExpression(req.getExpression());
        p.setDescription(req.getDescription());
        p.setEnabled(req.getEnabled() != null ? req.getEnabled() : true);
        parameterMapper.insert(p);
        return toVO(p);
    }

    public ParameterVO update(ParameterRequest req) {
        Parameter p = parameterMapper.selectById(req.getId());
        if (p == null) {
            throw new IllegalArgumentException("参数不存在: " + req.getId());
        }
        // check duplicate name if name changed
        if (req.getParamName() != null && !req.getParamName().equals(p.getParamName())) {
            Long count = parameterMapper.selectCount(
                    new LambdaQueryWrapper<Parameter>()
                            .eq(Parameter::getParamName, req.getParamName())
                            .ne(Parameter::getId, req.getId())
            );
            if (count > 0) {
                throw new IllegalArgumentException("参数名已存在: " + req.getParamName());
            }
            p.setParamName(req.getParamName());
        }
        if (req.getParamValue() != null) p.setParamValue(req.getParamValue());
        if (req.getParamType() != null) p.setParamType(req.getParamType());
        if (req.getExpression() != null) p.setExpression(req.getExpression());
        if (req.getDescription() != null) p.setDescription(req.getDescription());
        if (req.getEnabled() != null) p.setEnabled(req.getEnabled());
        parameterMapper.updateById(p);
        return toVO(parameterMapper.selectById(req.getId()));
    }

    public void delete(Long id) {
        if (parameterMapper.selectById(id) == null) {
            throw new IllegalArgumentException("参数不存在: " + id);
        }
        parameterMapper.deleteById(id);
    }

    // ---- Parameter Resolution ----

    /**
     * Resolve all ${paramName} placeholders in the given SQL string.
     * Only enabled parameters are resolved.
     * Returns the SQL with all known placeholders replaced.
     * Unknown placeholders are left as-is.
     */
    public String resolveParams(String sql) {
        if (sql == null || sql.isBlank()) return sql;

        List<Parameter> enabledParams = parameterMapper.selectList(
                new LambdaQueryWrapper<Parameter>().eq(Parameter::getEnabled, true)
        );
        if (enabledParams.isEmpty()) return sql;

        Map<String, String> resolved = buildResolvedMap(enabledParams);
        StringBuffer sb = new StringBuffer();
        Matcher m = PARAM_PATTERN.matcher(sql);
        while (m.find()) {
            String name = m.group(1);
            String value = resolved.get(name);
            if (value != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolve parameters and return both the resolved SQL and the mapping.
     */
    public Map<String, Object> resolveAndGetMap(String sql) {
        Map<String, Object> result = new HashMap<>();
        if (sql == null || sql.isBlank()) {
            result.put("resolvedSql", sql);
            result.put("resolvedParams", Collections.emptyMap());
            return result;
        }

        List<Parameter> enabledParams = parameterMapper.selectList(
                new LambdaQueryWrapper<Parameter>().eq(Parameter::getEnabled, true)
        );

        Map<String, String> resolved = buildResolvedMap(enabledParams);
        StringBuffer sb = new StringBuffer();
        Matcher m = PARAM_PATTERN.matcher(sql);
        while (m.find()) {
            String name = m.group(1);
            String value = resolved.get(name);
            if (value != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
        }
        m.appendTail(sb);

        // filter mapping to only include params actually referenced in the SQL
        Map<String, String> usedParams = new LinkedHashMap<>();
        Matcher refMatcher = PARAM_PATTERN.matcher(sql);
        Set<String> usedNames = new LinkedHashSet<>();
        while (refMatcher.find()) {
            usedNames.add(refMatcher.group(1));
        }
        for (String name : usedNames) {
            String value = resolved.get(name);
            if (value != null) {
                usedParams.put(name, value);
            }
        }

        result.put("resolvedSql", sb.toString());
        result.put("resolvedParams", usedParams);
        return result;
    }

    /**
     * Build a map of paramName → resolved value from the list of enabled parameters.
     */
    private Map<String, String> buildResolvedMap(List<Parameter> params) {
        Map<String, String> map = new HashMap<>();
        // 1. User-defined DB params are loaded first
        for (Parameter p : params) {
            if ("STATIC".equals(p.getParamType())) {
                map.put(p.getParamName(), p.getParamValue() != null ? p.getParamValue() : "");
            } else if ("DYNAMIC".equals(p.getParamType())) {
                map.put(p.getParamName(), evalDynamic(p.getExpression()));
            }
        }
        // 2. Built-in keywords ALWAYS override (today → date, yesterday → date-1, etc.)
        //    This ensures ${today} works even if the DB has a misconfigured "today" entry.
        for (String kw : BUILTIN_KEYWORDS) {
            map.put(kw, evalDynamic(kw));
        }
        return map;
    }

    // ──────────────────────────────────────────────
    // Simple function-call parser for expressions like:
    //   date_add(today, -7)
    //   date_format(date_add(today, -1), 'yyyy-MM-dd')
    //   concat('prefix_', today)
    // ──────────────────────────────────────────────

    /** Regex to tokenise function-call expressions. */
    private static final Pattern FUNC_TOKEN = Pattern.compile(
            "\"[^\"]*\"|'[^']*'|[(),]|[\\w.]+|\\s+"
    );

    /**
     * Evaluate a dynamic parameter expression to its current value.
     * Supports:<ul>
     *   <li>Predefined keywords (today, yesterday, etc.)
     *   <li>DateTimeFormatter patterns (yyyy-MM-dd, yyyyMM, …)
     *   <li>Function-calls: now(), date_add(), date_format(), concat(), coalesce()
     * </ul>
     */
    static String evalDynamic(String expression) {
        if (expression == null || expression.isBlank()) return "";
        String expr = expression.trim();

        // Handle string literals: '' → "", 'hello' → hello
        if ((expr.startsWith("'") && expr.endsWith("'")) || (expr.startsWith("\"") && expr.endsWith("\""))) {
            return expr.substring(1, expr.length() - 1);
        }

        // SQL statements: return as-is without evaluation
        if (looksLikeSqlStatement(expr)) {
            return expr;
        }

        // Try function-call first
        if (looksLikeFunctionCall(expr)) {
            try {
                return evalFunctionCall(expr);
            } catch (Exception e) {
                // fall through to legacy handling
            }
        }

        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);

        return switch (expr) {
            case "now" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            case "today" -> today.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "yesterday" -> today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "tomorrow" -> today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "this_month" -> thisMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "last_month" -> thisMonth.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "this_year" -> String.valueOf(today.getYear());
            case "yyyyMMdd" -> today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            case "yyyy-MM-dd" -> today.format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "yyyyMM" -> thisMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));
            case "yyyy-MM" -> thisMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            case "yyyy" -> String.valueOf(today.getYear());
            case "this_month_start" -> today.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "this_month_end" -> today.withDayOfMonth(today.lengthOfMonth()).format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "last_month_start" -> thisMonth.minusMonths(1).atDay(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "last_month_end" -> thisMonth.minusMonths(1).atEndOfMonth().format(DateTimeFormatter.ISO_LOCAL_DATE);
            default -> {
                // Try as a DateTimeFormatter pattern (supports yyyy-MM-dd HH:mm:ss etc.)
                try {
                    yield LocalDateTime.now().format(DateTimeFormatter.ofPattern(expr));
                } catch (Exception e) {
                    yield expr;
                }
            }
        };
    }

    // ---- Function-call parser ----

    private static boolean looksLikeFunctionCall(String expr) {
        return expr.matches("[a-zA-Z_]+\\(.*\\)");
    }

    /** Detect if the expression is a complete SQL statement that should be returned as-is. */
    private static boolean looksLikeSqlStatement(String expr) {
        String upper = expr.trim().toUpperCase();
        return upper.startsWith("SELECT ")
            || upper.startsWith("SELECT\n")
            || upper.startsWith("WITH ")
            || upper.startsWith("WITH\n")
            || upper.startsWith("SHOW ")
            || upper.startsWith("SHOW\n")
            || upper.startsWith("DESC ")
            || upper.startsWith("DESC\n")
            || upper.startsWith("DESCRIBE ")
            || upper.startsWith("EXPLAIN ")
            || upper.startsWith("CALL ")
            || upper.startsWith("SET ")
            || upper.startsWith("USE ");
    }

    /** Parse and evaluate a function call: fnName(arg1, arg2, ...) */
    private static String evalFunctionCall(String expr) {
        // split on the first '('
        int parenIdx = expr.indexOf('(');
        if (parenIdx == -1) throw new IllegalArgumentException("not a function call: " + expr);
        String name = expr.substring(0, parenIdx).trim();
        String argsBody = expr.substring(parenIdx + 1, expr.lastIndexOf(')')).trim();

        List<String> args = parseArgs(argsBody);

        return switch (name) {
            case "now" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            case "today" -> LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "date_add" -> evalDateAdd(args);
            case "date_sub" -> evalDateSub(args); // date_sub(date, n) same as date_add(date, -n)
            case "date_format" -> evalDateFormat(args);
            case "datediff" -> evalDateDiff(args);
            case "date_trunc" -> evalDateTrunc(args);
            case "concat" -> args.stream().map(a -> evalDynamic(a)).collect(Collectors.joining());
            case "coalesce" -> evalCoalesce(args);
            case "if" -> evalIf(args);
            case "trim" -> evalDynamic(args.isEmpty() ? "" : args.get(0)).trim();
            case "upper" -> evalDynamic(args.isEmpty() ? "" : args.get(0)).toUpperCase();
            case "lower" -> evalDynamic(args.isEmpty() ? "" : args.get(0)).toLowerCase();
            case "length" -> String.valueOf(evalDynamic(args.isEmpty() ? "" : args.get(0)).length());
            case "replace" -> evalReplace(args);
            case "substring" -> evalSubstring(args);
            case "regexp_extract" -> evalRegexpExtract(args);
            default -> throw new IllegalArgumentException("未知函数: " + name);
        };
    }

    /** Split a comma-separated argument list, respecting nested parens and quotes. */
    static List<String> parseArgs(String body) {
        List<String> args = new ArrayList<>();
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inSingle) {
                if (c == '\'') inSingle = false;
                current.append(c);
            } else if (inDouble) {
                if (c == '"') inDouble = false;
                current.append(c);
            } else if (c == '\'') {
                inSingle = true;
                current.append(c);
            } else if (c == '"') {
                inDouble = true;
                current.append(c);
            } else if (c == '(') {
                depth++;
                current.append(c);
            } else if (c == ')') {
                depth--;
                current.append(c);
            } else if (c == ',' && depth == 0) {
                args.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) args.add(last);
        return args;
    }

    /** Strip surrounding quotes from a string literal. */
    static String unquote(String s) {
        s = s.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /** date_sub(date_expr, days) — subtract days (same as date_add with negated days). */
    private static String evalDateSub(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("date_sub 需要 2 个参数");
        String dateStr = evalDynamic(args.get(0));
        long days = -Long.parseLong(evalDynamic(args.get(1))); // negate
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        return date.plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /** date_add(date_expr, days) — days can be negative. */
    private static String evalDateAdd(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("date_add 需要 2 个参数");
        String dateStr = evalDynamic(args.get(0));
        long days = Long.parseLong(evalDynamic(args.get(1)));
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        return date.plusDays(days).format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    /** date_format(date_expr, pattern) */
    private static String evalDateFormat(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("date_format 需要 2 个参数");
        String dateStr = evalDynamic(args.get(0));
        String pattern = unquote(args.get(1));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
        // Try as LocalDateTime first; fall back to LocalDate padded to midnight
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(fmt);
        } catch (Exception e) {
            return LocalDate.parse(dateStr).atStartOfDay().format(fmt);
        }
    }

    /** coalesce(val, default_val) — return first non-blank value. */
    private static String evalCoalesce(List<String> args) {
        for (String arg : args) {
            String val = evalDynamic(arg);
            if (val != null && !val.isBlank()) return val;
        }
        return "";
    }

    /** if(condition, true_val, false_val) — simple conditional. condition is truthy if non-blank and not "false". */
    private static String evalIf(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("if 至少需要 2 个参数");
        String cond = evalDynamic(args.get(0));
        boolean truthy = cond != null && !cond.isBlank() && !"false".equalsIgnoreCase(cond.trim()) && !"0".equals(cond.trim());
        if (truthy) {
            return evalDynamic(args.get(1));
        }
        if (args.size() >= 3) {
            return evalDynamic(args.get(2));
        }
        return "";
    }

    /** datediff(end_date, start_date) — days between two dates. */
    private static String evalDateDiff(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("datediff 需要 2 个参数");
        String endStr = evalDynamic(args.get(0));
        String startStr = evalDynamic(args.get(1));
        LocalDate end = LocalDate.parse(endStr, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate start = LocalDate.parse(startStr, DateTimeFormatter.ISO_LOCAL_DATE);
        return String.valueOf(ChronoUnit.DAYS.between(start, end));
    }

    /** date_trunc(date_expr, unit) — truncate date to month/quarter/year. */
    private static String evalDateTrunc(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("date_trunc 需要 2 个参数");
        String dateStr = evalDynamic(args.get(0));
        String unit = unquote(args.get(1)).toLowerCase();
        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        return switch (unit) {
            case "month" -> date.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            case "quarter" -> {
                int qMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
                yield date.withMonth(qMonth).withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            }
            case "year" -> date.withDayOfYear(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
            default -> throw new IllegalArgumentException("不支持的 date_trunc 单位: " + unit);
        };
    }

    /** replace(str, from, to) — string replace. */
    private static String evalReplace(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("replace 需要 2~3 个参数");
        String str = evalDynamic(args.get(0));
        String from = unquote(args.get(1));
        String to = args.size() >= 3 ? unquote(args.get(2)) : "";
        return str.replace(from, to);
    }

    /** substring(str, start[, length]) — extract substring (1-based start). */
    private static String evalSubstring(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("substring 需要 2~3 个参数");
        String str = evalDynamic(args.get(0));
        int start = Integer.parseInt(evalDynamic(args.get(1))) - 1;
        if (start < 0) start = 0;
        if (args.size() >= 3) {
            int len = Integer.parseInt(evalDynamic(args.get(2)));
            if (start + len > str.length()) len = str.length() - start;
            return str.substring(start, start + len);
        }
        return str.substring(Math.min(start, str.length()));
    }

    /** regexp_extract(str, pattern[, group_idx]) — regex extraction. */
    private static String evalRegexpExtract(List<String> args) {
        if (args.size() < 2) throw new IllegalArgumentException("regexp_extract 需要 2~3 个参数");
        String str = evalDynamic(args.get(0));
        String pattern = unquote(args.get(1));
        int group = args.size() >= 3 ? Integer.parseInt(evalDynamic(args.get(2))) : 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(str);
        if (m.find()) {
            return m.group(group);
        }
        return "";
    }

    private ParameterVO toVO(Parameter p) {
        ParameterVO vo = new ParameterVO();
        vo.setId(p.getId());
        vo.setParamName(p.getParamName());
        vo.setParamValue(p.getParamValue());
        vo.setParamType(p.getParamType());
        vo.setExpression(p.getExpression());
        vo.setDescription(p.getDescription());
        vo.setEnabled(p.getEnabled());
        vo.setCreatedAt(p.getCreatedAt());
        vo.setUpdatedAt(p.getUpdatedAt());
        return vo;
    }
}
