package com.idata.service.grammar;

import com.idata.dto.SqlGrammarContext;
import com.idata.dto.SqlKeywordVO;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * SQL 语法状态机服务。
 * 分析 SQL 文本中光标位置的语法上下文，用于前端自动补全。
 */
@Service
public class SqlGrammarService {

    // ---- 关键字定义 ----

    /** 语句起始关键字 */
    private static final List<String> STATEMENT_KEYWORDS = List.of(
            "SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER", "DROP",
            "TRUNCATE", "WITH", "EXPLAIN", "SHOW", "DESCRIBE", "USE", "SET", "CALL"
    );

    /** 单元素关键字 → 下一个状态 */
    private static final Map<String, SqlGrammarState> SINGLE_KEYWORD_TRANSITION = new HashMap<>();

    /** 双元素关键字前缀 → 期待的后缀 */
    private static final Map<String, String> MULTI_KEYWORD_PREFIX = new HashMap<>();

    /** 双元素关键字 → 下一个状态 */
    private static final Map<String, SqlGrammarState> MULTI_KEYWORD_TRANSITION = new HashMap<>();

    /** 每个状态允许的关键字（按语法优先级排列，下标越小越优先） */
    private static final Map<SqlGrammarState, List<String>> VALID_KEYWORDS = new HashMap<>();

    /** 每个状态的期望类型标志 */
    private static final Map<SqlGrammarState, StateFlags> STATE_FLAGS = new HashMap<>();

    static {
        // ---- 单关键字状态转移 ----
        SINGLE_KEYWORD_TRANSITION.put("SELECT", SqlGrammarState.SELECT_LIST);
        SINGLE_KEYWORD_TRANSITION.put("INSERT", SqlGrammarState.INTO_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("UPDATE", SqlGrammarState.EXPRESSION);
        SINGLE_KEYWORD_TRANSITION.put("DELETE", SqlGrammarState.EXPRESSION);
        SINGLE_KEYWORD_TRANSITION.put("CREATE", SqlGrammarState.TABLE_DEF);
        SINGLE_KEYWORD_TRANSITION.put("ALTER", SqlGrammarState.TABLE_DEF);
        SINGLE_KEYWORD_TRANSITION.put("DROP", SqlGrammarState.TABLE_DEF);
        SINGLE_KEYWORD_TRANSITION.put("TRUNCATE", SqlGrammarState.TABLE_DEF);
        SINGLE_KEYWORD_TRANSITION.put("WITH", SqlGrammarState.SELECT_LIST);
        SINGLE_KEYWORD_TRANSITION.put("EXPLAIN", SqlGrammarState.STATEMENT_START);
        SINGLE_KEYWORD_TRANSITION.put("SHOW", SqlGrammarState.EXPRESSION);
        SINGLE_KEYWORD_TRANSITION.put("DESCRIBE", SqlGrammarState.EXPRESSION);
        SINGLE_KEYWORD_TRANSITION.put("USE", SqlGrammarState.EXPRESSION);
        SINGLE_KEYWORD_TRANSITION.put("CALL", SqlGrammarState.EXPRESSION);

        // 非 STATEMENT_START 中的关键字转移
        SINGLE_KEYWORD_TRANSITION.put("FROM", SqlGrammarState.FROM_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("WHERE", SqlGrammarState.WHERE_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("SET", SqlGrammarState.SET_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("ON", SqlGrammarState.ON_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("VALUES", SqlGrammarState.VALUES_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("LIMIT", SqlGrammarState.LIMIT_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("HAVING", SqlGrammarState.HAVING_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("JOIN", SqlGrammarState.JOIN_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("INTO", SqlGrammarState.INTO_CLAUSE);
        SINGLE_KEYWORD_TRANSITION.put("UNION", SqlGrammarState.STATEMENT_START);

        // ---- 多词关键字 ----
        MULTI_KEYWORD_PREFIX.put("LEFT", "JOIN");
        MULTI_KEYWORD_PREFIX.put("RIGHT", "JOIN");
        MULTI_KEYWORD_PREFIX.put("INNER", "JOIN");
        MULTI_KEYWORD_PREFIX.put("CROSS", "JOIN");
        MULTI_KEYWORD_PREFIX.put("NATURAL", "JOIN");
        MULTI_KEYWORD_PREFIX.put("GROUP", "BY");
        MULTI_KEYWORD_PREFIX.put("ORDER", "BY");
        MULTI_KEYWORD_PREFIX.put("UNION", "ALL");
        MULTI_KEYWORD_PREFIX.put("IS", "NOT");
        MULTI_KEYWORD_PREFIX.put("DELETE", "FROM");
        MULTI_KEYWORD_PREFIX.put("INSERT", "INTO");

        MULTI_KEYWORD_TRANSITION.put("LEFT JOIN", SqlGrammarState.JOIN_CLAUSE);
        MULTI_KEYWORD_TRANSITION.put("RIGHT JOIN", SqlGrammarState.JOIN_CLAUSE);
        MULTI_KEYWORD_TRANSITION.put("INNER JOIN", SqlGrammarState.JOIN_CLAUSE);
        MULTI_KEYWORD_TRANSITION.put("CROSS JOIN", SqlGrammarState.JOIN_CLAUSE);
        MULTI_KEYWORD_TRANSITION.put("NATURAL JOIN", SqlGrammarState.JOIN_CLAUSE);
        MULTI_KEYWORD_TRANSITION.put("GROUP BY", SqlGrammarState.GROUP_BY_CLAUSE);
        MULTI_KEYWORD_TRANSITION.put("ORDER BY", SqlGrammarState.ORDER_BY_CLAUSE);
        MULTI_KEYWORD_TRANSITION.put("UNION ALL", SqlGrammarState.STATEMENT_START);
        MULTI_KEYWORD_TRANSITION.put("INSERT INTO", SqlGrammarState.INTO_CLAUSE);
        MULTI_KEYWORD_TRANSITION.put("DELETE FROM", SqlGrammarState.EXPRESSION);
        // IS NOT stays in WHERE_CLAUSE (handled in state machine)

        // ---- 每个状态的有效关键字 ----
        VALID_KEYWORDS.put(SqlGrammarState.STATEMENT_START, STATEMENT_KEYWORDS);

        VALID_KEYWORDS.put(SqlGrammarState.SELECT_LIST, List.of(
                // ── 必选子句 ──
                "FROM",
                // ── 可选子句 ──
                "WHERE", "GROUP BY", "HAVING", "ORDER BY", "LIMIT",
                // ── 组合查询 ──
                "UNION", "UNION ALL",
                // ── 表连接 ──
                "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN",
                "CROSS JOIN", "NATURAL JOIN",
                // ── 其他修饰符 ──
                "INTO", "DISTINCT", "AS",
                // ── 表达式关键字 ──
                "CASE", "WHEN", "THEN", "ELSE", "END"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.FROM_CLAUSE, List.of(
                // ── 表连接 ──
                "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN",
                "CROSS JOIN", "NATURAL JOIN",
                // ── 连接条件 ──
                "ON",
                // ── 可选子句 ──
                "WHERE", "GROUP BY", "ORDER BY", "LIMIT",
                // ── 别名及其他 ──
                "AS", "SET"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.JOIN_CLAUSE, List.of(
                "ON", "AS", "WHERE"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.ON_CLAUSE, List.of(
                // ── 逻辑运算 ──
                "AND", "OR",
                // ── 比较/条件 ──
                "IS", "NOT", "NULL", "LIKE", "IN", "BETWEEN", "EXISTS",
                // ── 更多连接 ──
                "JOIN", "LEFT JOIN", "RIGHT JOIN",
                // ── 可选子句 ──
                "WHERE"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.WHERE_CLAUSE, List.of(
                // ── 逻辑运算 ──
                "AND", "OR",
                // ── 可选子句 ──
                "GROUP BY", "ORDER BY", "LIMIT",
                // ── 比较/条件 ──
                "IS", "NOT", "NULL", "LIKE", "IN", "BETWEEN", "EXISTS",
                // ── 表连接 ──
                "JOIN", "LEFT JOIN", "RIGHT JOIN"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.GROUP_BY_CLAUSE, List.of(
                "HAVING", "ORDER BY", "LIMIT", "ASC", "DESC"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.HAVING_CLAUSE, List.of(
                "AND", "OR", "ORDER BY", "LIMIT",
                "IS", "NOT", "NULL", "LIKE", "IN", "BETWEEN", "EXISTS"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.ORDER_BY_CLAUSE, List.of(
                "LIMIT", "ASC", "DESC"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.LIMIT_CLAUSE, List.of("OFFSET"));

        VALID_KEYWORDS.put(SqlGrammarState.SET_CLAUSE, List.of("WHERE"));

        VALID_KEYWORDS.put(SqlGrammarState.INTO_CLAUSE, List.of("VALUES", "SELECT"));

        VALID_KEYWORDS.put(SqlGrammarState.VALUES_CLAUSE, Collections.emptyList());

        VALID_KEYWORDS.put(SqlGrammarState.TABLE_DEF, Collections.emptyList());

        VALID_KEYWORDS.put(SqlGrammarState.AFTER_DOT, Collections.emptyList());

        VALID_KEYWORDS.put(SqlGrammarState.INSIDE_FUNCTION, List.of(
                "AND", "OR", "CASE", "WHEN", "THEN", "ELSE", "END"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.EXPRESSION, List.of(
                // ── 子句级关键字（最优先） ──
                "WHERE", "ORDER BY", "LIMIT", "SET",
                // ── 逻辑运算 ──
                "AND", "OR",
                // ── 比较/条件 ──
                "IS", "NOT", "NULL", "LIKE", "IN", "BETWEEN", "EXISTS",
                // ── 表达式关键字 ──
                "CASE", "WHEN", "THEN", "ELSE", "END"
        ));

        VALID_KEYWORDS.put(SqlGrammarState.UNKNOWN, STATEMENT_KEYWORDS);

        // ---- 期望类型标志 ----
        STATE_FLAGS.put(SqlGrammarState.STATEMENT_START, new StateFlags(false, false, false, false, false));
        STATE_FLAGS.put(SqlGrammarState.SELECT_LIST, new StateFlags(false, true, false, true, true));
        STATE_FLAGS.put(SqlGrammarState.FROM_CLAUSE, new StateFlags(true, false, true, false, false));
        STATE_FLAGS.put(SqlGrammarState.JOIN_CLAUSE, new StateFlags(true, false, true, false, false));
        STATE_FLAGS.put(SqlGrammarState.ON_CLAUSE, new StateFlags(false, true, false, true, true));
        STATE_FLAGS.put(SqlGrammarState.WHERE_CLAUSE, new StateFlags(false, true, false, true, true));
        STATE_FLAGS.put(SqlGrammarState.GROUP_BY_CLAUSE, new StateFlags(false, true, false, false, true));
        STATE_FLAGS.put(SqlGrammarState.HAVING_CLAUSE, new StateFlags(false, true, false, true, true));
        STATE_FLAGS.put(SqlGrammarState.ORDER_BY_CLAUSE, new StateFlags(false, true, false, false, true));
        STATE_FLAGS.put(SqlGrammarState.LIMIT_CLAUSE, new StateFlags(false, false, false, false, true));
        STATE_FLAGS.put(SqlGrammarState.SET_CLAUSE, new StateFlags(false, true, false, true, true));
        STATE_FLAGS.put(SqlGrammarState.INTO_CLAUSE, new StateFlags(true, false, true, false, false));
        STATE_FLAGS.put(SqlGrammarState.VALUES_CLAUSE, new StateFlags(false, false, false, true, true));
        STATE_FLAGS.put(SqlGrammarState.TABLE_DEF, new StateFlags(true, false, true, false, false));
        STATE_FLAGS.put(SqlGrammarState.AFTER_DOT, new StateFlags(false, false, false, false, false));
        STATE_FLAGS.put(SqlGrammarState.INSIDE_FUNCTION, new StateFlags(false, true, false, true, true));
        STATE_FLAGS.put(SqlGrammarState.EXPRESSION, new StateFlags(true, true, false, true, true));
        STATE_FLAGS.put(SqlGrammarState.UNKNOWN, new StateFlags(false, false, false, false, false));
    }

    // ---- 内部数据结构 ----

    private record StateFlags(boolean table, boolean column, boolean database, boolean function, boolean value) {
    }

    /**
     * 检测光标所在位置的 SQL 语法上下文。
     *
     * @param sql            完整的 SQL 文本
     * @param cursorPosition 光标位置（字符偏移）
     * @return 语法上下文
     */
    public SqlGrammarContext detectContext(String sql, int cursorPosition) {
        // 处理空或无效输入
        if (sql == null || sql.isEmpty() || cursorPosition <= 0) {
            return createContext(SqlGrammarState.STATEMENT_START, "OTHER");
        }

        int len = Math.min(cursorPosition, sql.length());
        List<SqlToken> tokens = SqlTokenizer.tokenize(sql, len);

        return processStateMachine(tokens);
    }

    /**
     * 状态机主循环。
     */
    private SqlGrammarContext processStateMachine(List<SqlToken> tokens) {
        SqlGrammarState state = SqlGrammarState.STATEMENT_START;
        String queryType = "OTHER";
        int parenDepth = 0;
        Deque<SqlGrammarState> parenStack = new ArrayDeque<>();
        boolean insideSubquery = false;

        // 点号上下文跟踪
        String dotPrefix = null;
        String dotPrefixType = null;
        boolean lastTokenWasDot = false;
        SqlGrammarState stateBeforeDot = null;
        String lastIdentifier = null;
        String lastKeyword = null;

        // 多词关键字跟踪
        String pendingPrefix = null; // e.g., "LEFT" waiting for "JOIN"
        boolean processedPending = false;

        // BETWEEN ... AND ... 跟踪
        boolean betweenMode = false;

        List<SqlToken> nonWhitespaceTokens = tokens.stream()
                .filter(t -> t.getType() != SqlToken.Type.WHITESPACE
                        && t.getType() != SqlToken.Type.COMMENT)
                .toList();

        for (int idx = 0; idx < nonWhitespaceTokens.size(); idx++) {
            SqlToken token = nonWhitespaceTokens.get(idx);
            String upper = token.getUpperValue();
            boolean isKeyword = isKeyword(upper);

            // 如果前一个 token 是点号，处理标识符
            if (lastTokenWasDot) {
                if (token.getType() == SqlToken.Type.IDENTIFIER) {
                    // 点号后的标识符已经消费，恢复点号前的状态
                    lastIdentifier = upper;
                    lastTokenWasDot = false;
                    if (stateBeforeDot != null) {
                        state = stateBeforeDot;
                        stateBeforeDot = null;
                    } else {
                        state = SqlGrammarState.EXPRESSION;
                    }
                    continue;
                }
                // 点号后不是标识符，保持 AFTER_DOT
            }

            // 如果上一个 token 触发多词等待
            if (!processedPending && pendingPrefix != null && isKeyword) {
                String fullKw = pendingPrefix + " " + upper;
                if (MULTI_KEYWORD_TRANSITION.containsKey(fullKw)) {
                    state = MULTI_KEYWORD_TRANSITION.get(fullKw);
                    queryType = determineQueryType(queryType, upper);
                    lastKeyword = upper;
                    pendingPrefix = null;
                    processedPending = true;
                    lastIdentifier = null;
                    continue;
                }
                // 不匹配：回退，pendingPrefix 被忽略
                pendingPrefix = null;
            }
            processedPending = false;

            // 操作符处理
            if (token.getType() == SqlToken.Type.OPERATOR) {
                String op = token.getValue();

                if (".".equals(op)) {
                    dotPrefix = lastIdentifier;
                    dotPrefixType = determineDotPrefixType(state, lastIdentifier);
                    stateBeforeDot = state;
                    state = SqlGrammarState.AFTER_DOT;
                    lastTokenWasDot = true;
                    continue;
                }

                if ("(".equals(op)) {
                    parenStack.push(state);
                    parenDepth++;

                    // 判断是函数还是子查询
                    if (lastKeyword != null && isFunctionKeyword(lastKeyword)) {
                        insideSubquery = false; // 函数参数
                    } else {
                        insideSubquery = true; // 子查询
                    }
                    state = SqlGrammarState.STATEMENT_START;
                    // 子查询内不继承 queryType
                    lastIdentifier = null;
                    continue;
                }

                if (")".equals(op)) {
                    if (!parenStack.isEmpty()) {
                        state = parenStack.pop();
                        parenDepth--;
                        if (parenDepth == 0) {
                            insideSubquery = false;
                        }
                    }
                    continue;
                }

                if (",".equals(op)) {
                    // 逗号不改变当前状态
                    continue;
                }

                if (";".equals(op)) {
                    state = SqlGrammarState.STATEMENT_START;
                    queryType = "OTHER";
                    dotPrefix = null;
                    dotPrefixType = null;
                    stateBeforeDot = null;
                    lastIdentifier = null;
                    lastKeyword = null;
                    pendingPrefix = null;
                    betweenMode = false;
                    parenStack.clear();
                    parenDepth = 0;
                    insideSubquery = false;
                    continue;
                }

                // 其他操作符（=, <, >, != 等）：保持在当前表达式状态
                continue;
            }

            // 数字和字符串：保持当前状态
            if (token.getType() == SqlToken.Type.NUMBER || token.getType() == SqlToken.Type.STRING) {
                lastIdentifier = token.getValue();
                if (betweenMode && ",".equals(upper)) {
                    // 在 BETWEEN ... AND ... 中，值之间用 AND 分隔
                }
                continue;
            }

            // 标识符处理
            if (token.getType() == SqlToken.Type.IDENTIFIER && !isKeyword) {
                lastIdentifier = upper;

                // 如果是子查询内的标识符，处理别名
                if (state == SqlGrammarState.FROM_CLAUSE || state == SqlGrammarState.JOIN_CLAUSE) {
                    // FROM table alias 或 FROM table AS alias 的情况
                    // 标识符后的下一个关键字会处理状态转移
                }

                // INTO_CLAUSE 后跟标识符保持在 INTO_CLAUSE
                // TABLE_DEF 后跟标识符 → EXPRESSION
                if (state == SqlGrammarState.TABLE_DEF) {
                    state = SqlGrammarState.EXPRESSION;
                }

                continue;
            }

            // 关键字处理
            if (isKeyword) {
                lastKeyword = upper;

                // 多词关键字前缀检测
                if (MULTI_KEYWORD_PREFIX.containsKey(upper)) {
                    // 检查下一个 token
                    String expectedSuffix = MULTI_KEYWORD_PREFIX.get(upper);
                    SqlToken nextToken = (idx + 1 < nonWhitespaceTokens.size()) ? nonWhitespaceTokens.get(idx + 1) : null;
                    boolean nextMatch = nextToken != null && expectedSuffix.equals(nextToken.getUpperValue());

                    // 多词 JOIN 前缀（LEFT/RIGHT/INNER/CROSS/NATURAL）只在可 JOIN 上下文中等待
                    boolean isJoinPrefix = "LEFT".equals(upper) || "RIGHT".equals(upper)
                            || "INNER".equals(upper) || "CROSS".equals(upper) || "NATURAL".equals(upper);

                    if (nextMatch) {
                        // 完整多词关键字
                        String fullKw = upper + " " + expectedSuffix;
                        SqlGrammarState newState = MULTI_KEYWORD_TRANSITION.get(fullKw);
                        if (newState != null) {
                            state = newState;
                        }
                        queryType = determineQueryType(queryType, upper);
                        // 跳过下一个 token
                        idx++;
                        lastIdentifier = null;
                        continue;
                    } else if (isJoinPrefix && canHaveJoin(state, upper)) {
                        // JOIN 前缀等待下一个 token
                        pendingPrefix = upper;
                        continue;
                    }
                    // 非 JOIN 前缀或无匹配：降级为单关键字处理（走下面的标准转移）
                }

                // AND/OR 上下文感知：保留当前子句状态，不跳到 WHERE_CLAUSE
                if ("AND".equals(upper) || "OR".equals(upper)) {
                    if (state == SqlGrammarState.ON_CLAUSE
                            || state == SqlGrammarState.HAVING_CLAUSE
                            || state == SqlGrammarState.WHERE_CLAUSE
                            || state == SqlGrammarState.INSIDE_FUNCTION) {
                        // AND/OR 保持当前状态（ON/HAVING/WHERE 等）
                        lastIdentifier = null;
                        continue;
                    }
                    if (state == SqlGrammarState.EXPRESSION
                            || state == SqlGrammarState.SET_CLAUSE) {
                        // 表达式中 AND/OR 进入 WHERE 上下文
                        state = SqlGrammarState.WHERE_CLAUSE;
                        lastIdentifier = null;
                        continue;
                    }
                }

                // 处理 BETWEEN ... AND: 如果在 WHERE/ON 中遇到 AND 且前一个是 BETWEEN 的值
                if ("BETWEEN".equals(upper)) {
                    betweenMode = true;
                }
                if (betweenMode && "AND".equals(upper)) {
                    betweenMode = false;
                    // 不改变状态，AND 在这里是 BETWEEN 的一部分
                    continue;
                }

                // 标准状态转移
                SqlGrammarState transition = SINGLE_KEYWORD_TRANSITION.get(upper);
                if (transition != null) {
                    // 只在适当的上下文中应用转移
                    if (isTransitionValid(state, upper, transition)) {
                        state = transition;
                        queryType = determineQueryType(queryType, upper);
                    }
                }

                // SET 在非 UPDATE 上下文中不是转移关键字
                if ("SET".equals(upper) && state != SqlGrammarState.EXPRESSION) {
                    queryType = determineQueryType(queryType, upper);
                }

                lastIdentifier = null;
            }
        }

        // 构建结果
        SqlGrammarContext context = createContext(state, queryType);
        context.setDotPrefix(dotPrefix);
        context.setDotPrefixType(dotPrefixType);
        context.setInsideSubquery(parenDepth > 0);
        context.setParenthesisDepth(parenDepth);
        context.setDotPrefix(dotPrefix);

        // AFTER_DOT 特殊处理：根据前缀类型设置期望
        if (state == SqlGrammarState.AFTER_DOT) {
            if ("DATABASE".equals(dotPrefixType)) {
                context.setExpectsTable(true);
            } else if ("TABLE".equals(dotPrefixType)) {
                context.setExpectsColumn(true);
            } else {
                context.setExpectsTable(true);
                context.setExpectsColumn(true);
            }
        }

        return context;
    }

    /**
     * 判断关键字是否可以在当前上下文中触发转移。
     */
    private boolean isTransitionValid(SqlGrammarState currentState, String keyword, SqlGrammarState targetState) {
        // STATEMENT_START 状态下任何 STATEMENT_KEYWORDS 都有效
        if (currentState == SqlGrammarState.STATEMENT_START) {
            return STATEMENT_KEYWORDS.contains(keyword);
        }

        // WHERE 关键字：EXPRESSION、FROM_CLAUSE、SELECT_LIST 中都有效
        if ("WHERE".equals(keyword)) {
            return currentState == SqlGrammarState.EXPRESSION
                    || currentState == SqlGrammarState.FROM_CLAUSE
                    || currentState == SqlGrammarState.SELECT_LIST
                    || currentState == SqlGrammarState.SET_CLAUSE
                    || currentState == SqlGrammarState.ON_CLAUSE;
        }

        // FROM 关键字：SELECT_LIST 中有效
        if ("FROM".equals(keyword)) {
            return currentState == SqlGrammarState.SELECT_LIST;
        }

        // JOIN 关键字：SELECT_LIST、FROM_CLAUSE、WHERE_CLAUSE 中有效
        if ("JOIN".equals(keyword)) {
            return currentState == SqlGrammarState.SELECT_LIST
                    || currentState == SqlGrammarState.FROM_CLAUSE
                    || currentState == SqlGrammarState.WHERE_CLAUSE
                    || currentState == SqlGrammarState.ON_CLAUSE;
        }

        // ON 关键字：FROM_CLAUSE、JOIN_CLAUSE 中有效
        if ("ON".equals(keyword)) {
            return currentState == SqlGrammarState.FROM_CLAUSE
                    || currentState == SqlGrammarState.JOIN_CLAUSE;
        }

        // AND/OR 关键字：WHERE_CLAUSE、ON_CLAUSE、HAVING_CLAUSE、EXPRESSION 中有效
        if ("AND".equals(keyword) || "OR".equals(keyword)) {
            return currentState == SqlGrammarState.WHERE_CLAUSE
                    || currentState == SqlGrammarState.ON_CLAUSE
                    || currentState == SqlGrammarState.HAVING_CLAUSE
                    || currentState == SqlGrammarState.EXPRESSION
                    || currentState == SqlGrammarState.INSIDE_FUNCTION;
        }

        // SET 关键字：EXPRESSION（UPDATE 后的上下文）有效
        if ("SET".equals(keyword)) {
            return currentState == SqlGrammarState.EXPRESSION;
        }

        // VALUES 关键字：INTO_CLAUSE 有效
        if ("VALUES".equals(keyword)) {
            return currentState == SqlGrammarState.INTO_CLAUSE;
        }

        // LIMIT 关键字
        if ("LIMIT".equals(keyword)) {
            return currentState == SqlGrammarState.SELECT_LIST
                    || currentState == SqlGrammarState.FROM_CLAUSE
                    || currentState == SqlGrammarState.WHERE_CLAUSE
                    || currentState == SqlGrammarState.ORDER_BY_CLAUSE
                    || currentState == SqlGrammarState.HAVING_CLAUSE
                    || currentState == SqlGrammarState.EXPRESSION;
        }

        // HAVING 关键字
        if ("HAVING".equals(keyword)) {
            return currentState == SqlGrammarState.GROUP_BY_CLAUSE;
        }

        // INTO 关键字
        if ("INTO".equals(keyword)) {
            return currentState == SqlGrammarState.SELECT_LIST
                    || currentState == SqlGrammarState.EXPRESSION;
        }

        // GROUP BY / ORDER BY 前缀
        if ("GROUP".equals(keyword) || "ORDER".equals(keyword)) {
            return canHaveGroupOrOrder(currentState);
        }

        // 默认：如果目标状态和当前状态不同，允许转移
        return targetState != currentState;
    }

    private boolean canHaveJoin(SqlGrammarState state, String keyword) {
        return state == SqlGrammarState.SELECT_LIST
                || state == SqlGrammarState.FROM_CLAUSE
                || state == SqlGrammarState.WHERE_CLAUSE
                || state == SqlGrammarState.ON_CLAUSE;
    }

    private boolean canHaveGroupOrOrder(SqlGrammarState state) {
        return state == SqlGrammarState.SELECT_LIST
                || state == SqlGrammarState.FROM_CLAUSE
                || state == SqlGrammarState.WHERE_CLAUSE
                || state == SqlGrammarState.EXPRESSION;
    }

    private boolean isFunctionKeyword(String keyword) {
        // 常见的函数名
        return List.of("COUNT", "SUM", "AVG", "MAX", "MIN", "NOW", "CONCAT",
                "COALESCE", "IFNULL", "CAST", "DATE_FORMAT", "SUBSTRING",
                "REPLACE", "LENGTH", "UPPER", "LOWER", "TRIM", "IF",
                "GROUP_CONCAT", "UNIX_TIMESTAMP", "FROM_UNIXTIME",
                "DATE_ADD", "DATEDIFF", "ROW_NUMBER", "RANK",
                "DENSE_RANK", "LEAD", "LAG", "EXISTS", "NVL",
                "SUBSTR", "SPLIT", "REGEXP_REPLACE", "COLLECT_LIST",
                "COLLECT_SET", "EXPLODE", "GET_JSON_OBJECT", "TO_DATE",
                "DISTINCT").contains(keyword);
    }

    private String determineQueryType(String current, String keyword) {
        if ("OTHER".equals(current) && STATEMENT_KEYWORDS.contains(keyword)) {
            return keyword;
        }
        return current;
    }

    /**
     * 判断点号前的标识符类型。
     */
    private String determineDotPrefixType(SqlGrammarState state, String identifier) {
        if (identifier == null) return "UNKNOWN";
        // 在 FROM/JOIN/TABLE_DEF 上下文中，点号前的标识符通常是数据库名
        if (state == SqlGrammarState.FROM_CLAUSE
                || state == SqlGrammarState.JOIN_CLAUSE
                || state == SqlGrammarState.TABLE_DEF) {
            return "DATABASE";
        }
        // 默认视为表名（alias.column）
        return "TABLE";
    }

    /**
     * 判断标识符是否是已知的关键字。
     */
    private boolean isKeyword(String upper) {
        // 使用 STATEMENT_KEYWORDS + 所有转移关键字 + 多词前缀 + 其他 SQL 关键字
        return STATEMENT_KEYWORDS.contains(upper)
                || SINGLE_KEYWORD_TRANSITION.containsKey(upper)
                || MULTI_KEYWORD_PREFIX.containsKey(upper)
                || "ASC".equals(upper) || "DESC".equals(upper)
                || "AS".equals(upper) || "DISTINCT".equals(upper)
                || "NULL".equals(upper) || "NOT".equals(upper)
                || "EXISTS".equals(upper) || "LIKE".equals(upper)
                || "IN".equals(upper) || "BETWEEN".equals(upper)
                || "IS".equals(upper) || "CASE".equals(upper)
                || "WHEN".equals(upper) || "THEN".equals(upper)
                || "ELSE".equals(upper) || "END".equals(upper)
                || "UNION".equals(upper) || "OFFSET".equals(upper)
                || "ALL".equals(upper) || "BY".equals(upper)
                || "AND".equals(upper) || "OR".equals(upper)
                || "TABLE".equals(upper);
    }

    // ---- SQL 模板 ----

    private static final Map<String, SqlKeywordVO> KEYWORDS = new LinkedHashMap<>();

    static {
        // ========== MySQL 关键词 ==========
        SqlKeywordVO mysql = new SqlKeywordVO();
        mysql.setStatements(List.of(
                "SELECT", "INSERT", "UPDATE", "DELETE", "REPLACE", "CREATE TABLE", "CREATE DATABASE",
                "CREATE INDEX", "ALTER TABLE", "ALTER DATABASE", "DROP TABLE", "DROP DATABASE",
                "DROP INDEX", "TRUNCATE TABLE", "SHOW TABLES", "SHOW DATABASES", "SHOW CREATE TABLE",
                "SHOW INDEX", "SHOW PROCESSLIST", "SHOW VARIABLES", "SHOW STATUS", "DESCRIBE",
                "EXPLAIN", "SET", "CALL", "LOCK TABLES", "UNLOCK TABLES", "WITH"
        ));
        mysql.setFunctions(List.of(
                "COUNT()", "SUM()", "AVG()", "MIN()", "MAX()", "GROUP_CONCAT()",
                "NOW()", "CURDATE()", "CURTIME()", "DATE_FORMAT()", "STR_TO_DATE()",
                "DATE_ADD()", "DATE_SUB()", "DATEDIFF()", "UNIX_TIMESTAMP()", "FROM_UNIXTIME()",
                "CONCAT()", "CONCAT_WS()", "SUBSTRING()", "SUBSTRING_INDEX()",
                "REPLACE()", "TRIM()", "UPPER()", "LOWER()", "LENGTH()", "CHAR_LENGTH()",
                "LOCATE()", "LEFT()", "RIGHT()", "LPAD()", "RPAD()", "REVERSE()",
                "COALESCE()", "IFNULL()", "NULLIF()", "IF()", "CASE",
                "CAST()", "CONVERT()",
                "ROUND()", "CEIL()", "FLOOR()", "ABS()", "MOD()", "POWER()", "SQRT()", "RAND()",
                "JSON_EXTRACT()", "JSON_UNQUOTE()", "JSON_KEYS()",
                "DATABASE()", "USER()", "VERSION()", "LAST_INSERT_ID()", "FOUND_ROWS()",
                "ROW_NUMBER()", "RANK()", "DENSE_RANK()", "LEAD()", "LAG()"
        ));
        mysql.setTypes(List.of(
                "TINYINT", "SMALLINT", "MEDIUMINT", "INT", "INTEGER", "BIGINT",
                "FLOAT", "DOUBLE", "DECIMAL(10,2)", "NUMERIC",
                "CHAR", "VARCHAR(255)", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT",
                "BINARY", "VARBINARY", "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB",
                "DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR",
                "BOOLEAN", "TINYINT(1)",
                "ENUM('')", "SET('')", "JSON", "GEOMETRY", "POINT"
        ));
        mysql.setClauses(List.of(
                "FROM", "WHERE", "INTO", "VALUES",
                "AUTO_INCREMENT", "ENGINE = InnoDB", "DEFAULT CHARSET = utf8mb4",
                "COLLATE = utf8mb4_unicode_ci", "CHARACTER SET",
                "UNSIGNED", "ZEROFILL", "CURRENT_TIMESTAMP",
                "ON UPDATE CURRENT_TIMESTAMP", "ON DELETE CASCADE",
                "ON DELETE SET NULL", "ON DELETE RESTRICT",
                "NOT NULL", "DEFAULT", "PRIMARY KEY", "UNIQUE KEY", "FOREIGN KEY",
                "REFERENCES", "INDEX", "KEY", "FULLTEXT", "SPATIAL",
                "LOCK IN SHARE MODE", "FOR UPDATE",
                "GROUP BY", "ORDER BY", "ASC", "DESC",
                "LIMIT", "OFFSET", "HAVING",
                "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "CROSS JOIN", "NATURAL JOIN",
                "OUTER JOIN", "LEFT OUTER JOIN", "RIGHT OUTER JOIN",
                "UNION", "UNION ALL", "DISTINCT",
                "IS NULL", "IS NOT NULL", "LIKE", "IN", "BETWEEN", "EXISTS",
                "AND", "OR", "NOT", "AS", "ON",
                "CASE WHEN", "THEN", "ELSE", "END",
                "DELAYED", "HIGH_PRIORITY", "LOW_PRIORITY", "IGNORE",
                "SQL_CALC_FOUND_ROWS", "SQL_NO_CACHE"
        ));

        // ========== Hive 关键词 ==========
        SqlKeywordVO hive = new SqlKeywordVO();
        hive.setStatements(List.of(
                "SELECT", "INSERT OVERWRITE TABLE", "INSERT INTO TABLE",
                "CREATE TABLE", "CREATE TABLE AS", "CREATE DATABASE",
                "ALTER TABLE", "DROP TABLE", "DROP DATABASE",
                "TRUNCATE TABLE", "SHOW TABLES", "SHOW DATABASES",
                "SHOW PARTITIONS", "SHOW CREATE TABLE", "SHOW FUNCTIONS",
                "DESCRIBE", "DESCRIBE EXTENDED", "DESCRIBE FORMATTED",
                "MSCK REPAIR TABLE", "LOAD DATA INPATH", "EXPORT TABLE",
                "IMPORT TABLE", "ANALYZE TABLE", "COMPUTE STATISTICS",
                "EXPLAIN", "SET", "RESET", "ADD", "WITH"
        ));
        hive.setFunctions(List.of(
                "COUNT()", "SUM()", "AVG()", "MIN()", "MAX()",
                "COLLECT_LIST()", "COLLECT_SET()", "EXPLODE()", "POSEXPLODE()",
                "GET_JSON_OBJECT()", "FROM_JSON()", "TO_JSON()",
                "CONCAT()", "CONCAT_WS()", "SUBSTRING()", "SPLIT()",
                "REGEXP_REPLACE()", "REGEXP_EXTRACT()", "PARSE_URL()",
                "UPPER()", "LOWER()", "TRIM()", "LENGTH()", "REVERSE()",
                "LPAD()", "RPAD()", "REPLACE()",
                "CAST()", "IF()", "COALESCE()", "NVL()", "NULLIF()", "CASE",
                "ROUND()", "FLOOR()", "CEIL()", "RAND()", "ABS()", "MOD()",
                "POW()", "SQRT()", "EXP()", "LN()", "LOG()",
                "DATEDIFF()", "DATE_ADD()", "DATE_SUB()",
                "FROM_UNIXTIME()", "UNIX_TIMESTAMP()", "TO_DATE()",
                "YEAR()", "MONTH()", "DAY()", "HOUR()", "MINUTE()", "SECOND()",
                "CURRENT_DATE", "CURRENT_TIMESTAMP",
                "ROW_NUMBER()", "RANK()", "DENSE_RANK()", "NTILE()",
                "LEAD()", "LAG()", "FIRST_VALUE()", "LAST_VALUE()",
                "CUME_DIST()", "PERCENT_RANK()",
                "PERCENTILE()", "PERCENTILE_APPROX()",
                "WIDTH_BUCKET()", "HISTOGRAM_NUMERIC()",
                "SIZE()", "SORT_ARRAY()", "ARRAY_CONTAINS()",
                "MAP_KEYS()", "MAP_VALUES()", "NAMED_STRUCT()"
        ));
        hive.setTypes(List.of(
                "TINYINT", "SMALLINT", "INT", "INTEGER", "BIGINT",
                "FLOAT", "DOUBLE", "DECIMAL", "NUMERIC",
                "STRING", "VARCHAR", "CHAR",
                "BOOLEAN", "BINARY",
                "TIMESTAMP", "DATE", "INTERVAL",
                "ARRAY<STRING>", "MAP<STRING,STRING>",
                "STRUCT<name:STRING,age:INT>",
                "UNIONTYPE<INT,STRING>"
        ));
        hive.setClauses(List.of(
                "FROM", "INTO", "VALUES",
                "ROW FORMAT DELIMITED", "ROW FORMAT SERDE",
                "FIELDS TERMINATED BY", "LINES TERMINATED BY",
                "STORED AS TEXTFILE", "STORED AS PARQUET",
                "STORED AS ORC", "STORED AS AVRO",
                "STORED AS SEQUENCEFILE", "STORED AS RCFILE",
                "INPUTFORMAT", "OUTPUTFORMAT",
                "LOCATION '/user/hive/warehouse/'",
                "TBLPROPERTIES ('skip.header.line.count'='1')",
                "PARTITIONED BY", "CLUSTERED BY", "SORTED BY", "INTO BUCKETS",
                "SKEWED BY", "STORED AS DIRECTORIES",
                "WITH SERDEPROPERTIES",
                "ESCAPED BY", "NULL DEFINED AS",
                "COLLECTION ITEMS TERMINATED BY",
                "MAP KEYS TERMINATED BY",
                "COMMENT 'table comment'",
                "IF NOT EXISTS", "OR REPLACE",
                "LATERAL VIEW", "LATERAL VIEW OUTER",
                "OVER (PARTITION BY)", "OVER (ORDER BY)",
                "GROUP BY", "ORDER BY", "SORT BY", "DISTRIBUTE BY",
                "CLUSTER BY", "ASC", "DESC",
                "LIMIT", "HAVING", "WHERE",
                "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "FULL OUTER JOIN",
                "LEFT SEMI JOIN", "CROSS JOIN",
                "UNION", "UNION ALL", "UNION DISTINCT",
                "DISTINCT", "ALL",
                "IS NULL", "IS NOT NULL", "LIKE", "RLIKE", "REGEXP",
                "IN", "BETWEEN", "EXISTS", "NOT EXISTS",
                "AND", "OR", "NOT", "AS", "ON",
                "CASE WHEN", "THEN", "ELSE", "END",
                "SET hive.exec.dynamic.partition=true",
                "SET hive.exec.dynamic.partition.mode=nonstrict"
        ));

        KEYWORDS.put("MYSQL", mysql);
        KEYWORDS.put("HIVE", hive);
    }

    /**
     * 根据数据库类型获取 SQL 关键词提示数据。
     */
    public SqlKeywordVO getKeywords(String dbType) {
        String type = (dbType == null || dbType.isBlank()) ? "MYSQL" : dbType.toUpperCase();
        return KEYWORDS.getOrDefault(type, KEYWORDS.get("MYSQL"));
    }

    /**
     * 创建基本上下文结果。
     */
    private SqlGrammarContext createContext(SqlGrammarState state, String queryType) {
        SqlGrammarContext ctx = new SqlGrammarContext();
        ctx.setState(state);
        ctx.setQueryType(queryType);

        StateFlags flags = STATE_FLAGS.getOrDefault(state, STATE_FLAGS.get(SqlGrammarState.UNKNOWN));
        ctx.setExpectsTable(flags.table());
        ctx.setExpectsColumn(flags.column());
        ctx.setExpectsDatabase(flags.database());
        ctx.setExpectsFunction(flags.function());
        ctx.setExpectsValue(flags.value());

        List<String> keywords = VALID_KEYWORDS.getOrDefault(state, Collections.emptyList());
        // 使用定义顺序作为优先级：List 下标越小（定义越靠前），优先级越高
        ctx.setValidKeywords(new ArrayList<>(keywords));

        return ctx;
    }
}
