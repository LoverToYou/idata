package com.idata.service.grammar;

import com.idata.dto.SqlGrammarContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SqlGrammarService 单元测试。
 * 覆盖所有语法状态和关键字转移规则。
 */
class SqlGrammarServiceTest {

    private SqlGrammarService service;

    @BeforeEach
    void setUp() {
        service = new SqlGrammarService();
    }

    // =============== STATEMENT_START ===============

    @Test
    void emptyInput() {
        SqlGrammarContext ctx = service.detectContext("", 0);
        assertEquals(SqlGrammarState.STATEMENT_START, ctx.getState());
        assertEquals("OTHER", ctx.getQueryType());
    }

    @Test
    void nullInput() {
        SqlGrammarContext ctx = service.detectContext(null, 0);
        assertEquals(SqlGrammarState.STATEMENT_START, ctx.getState());
    }

    @Test
    void cursorAtStart() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users", 0);
        assertEquals(SqlGrammarState.STATEMENT_START, ctx.getState());
    }

    @Test
    void statementStartHasCorrectKeywords() {
        SqlGrammarContext ctx = service.detectContext("", 0);
        assertTrue(ctx.getValidKeywords().contains("SELECT"));
        assertTrue(ctx.getValidKeywords().contains("INSERT"));
        assertTrue(ctx.getValidKeywords().contains("UPDATE"));
        assertTrue(ctx.getValidKeywords().contains("DELETE"));
        assertTrue(ctx.getValidKeywords().contains("CREATE"));
        assertTrue(ctx.getValidKeywords().contains("WITH"));
        assertFalse(ctx.isExpectsTable());
        assertFalse(ctx.isExpectsColumn());
    }

    // =============== SELECT ===============

    @Test
    void selectStartsWithSelectList() {
        SqlGrammarContext ctx = service.detectContext("SELECT ", 7);
        assertEquals(SqlGrammarState.SELECT_LIST, ctx.getState());
        assertEquals("SELECT", ctx.getQueryType());
        assertTrue(ctx.isExpectsColumn());
        assertTrue(ctx.isExpectsFunction());
        assertFalse(ctx.isExpectsTable());
    }

    @Test
    void selectListHasFromKeyword() {
        SqlGrammarContext ctx = service.detectContext("SELECT col1, col2 ", 18);
        assertEquals(SqlGrammarState.SELECT_LIST, ctx.getState());
        assertTrue(ctx.getValidKeywords().contains("FROM"));
        assertTrue(ctx.getValidKeywords().contains("WHERE"));
    }

    @Test
    void selectStarThenFrom() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM ", 14);
        assertEquals(SqlGrammarState.FROM_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsTable());
        assertTrue(ctx.isExpectsDatabase());
    }

    @Test
    void fromClauseAfterTable() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users ", 20);
        assertEquals(SqlGrammarState.FROM_CLAUSE, ctx.getState());
        assertTrue(ctx.getValidKeywords().contains("WHERE"));
        assertTrue(ctx.getValidKeywords().contains("JOIN"));
    }

    @Test
    void selectWithWhere() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users WHERE ", 26);
        assertEquals(SqlGrammarState.WHERE_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsColumn());
        assertTrue(ctx.isExpectsFunction());
    }

    @Test
    void whereAndOrStaysInWhere() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM t WHERE a = 1 AND ", 32);
        assertEquals(SqlGrammarState.WHERE_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsColumn());
    }

    @Test
    void whereWithOr() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM t WHERE a = 1 OR ", 31);
        assertEquals(SqlGrammarState.WHERE_CLAUSE, ctx.getState());
    }

    @Test
    void selectWithGroupBy() {
        SqlGrammarContext ctx = service.detectContext("SELECT COUNT(*) FROM t GROUP BY ", 33);
        assertEquals(SqlGrammarState.GROUP_BY_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsColumn());
    }

    @Test
    void groupByWithHaving() {
        SqlGrammarContext ctx = service.detectContext("SELECT COUNT(*) FROM t GROUP BY dept HAVING ", 45);
        assertEquals(SqlGrammarState.HAVING_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsColumn());
        assertTrue(ctx.isExpectsFunction());
    }

    @Test
    void selectWithOrderBy() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM t ORDER BY ", 25);
        assertEquals(SqlGrammarState.ORDER_BY_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsColumn());
    }

    @Test
    void orderByWithLimit() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM t ORDER BY id LIMIT ", 35);
        assertEquals(SqlGrammarState.LIMIT_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsValue());
    }

    @Test
    void limitWithOffset() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM t LIMIT 10 OFFSET ", 32);
        assertEquals(SqlGrammarState.LIMIT_CLAUSE, ctx.getState());
    }

    // =============== JOIN ===============

    @Test
    void joinKeyword() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users u JOIN ", 28);
        assertEquals(SqlGrammarState.JOIN_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsTable());
    }

    @Test
    void leftJoin() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users LEFT JOIN ", 30);
        assertEquals(SqlGrammarState.JOIN_CLAUSE, ctx.getState());
    }

    @Test
    void innerJoin() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users INNER JOIN ", 31);
        assertEquals(SqlGrammarState.JOIN_CLAUSE, ctx.getState());
    }

    @Test
    void joinWithOn() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users u JOIN orders o ON ", 39);
        assertEquals(SqlGrammarState.ON_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsColumn());
    }

    @Test
    void onWithAnd() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM u JOIN o ON u.id = o.user_id AND ", 47);
        assertEquals(SqlGrammarState.ON_CLAUSE, ctx.getState());
    }

    // =============== INSERT ===============

    @Test
    void insertInto() {
        SqlGrammarContext ctx = service.detectContext("INSERT INTO ", 12);
        assertEquals(SqlGrammarState.INTO_CLAUSE, ctx.getState());
        assertEquals("INSERT", ctx.getQueryType());
        assertTrue(ctx.isExpectsTable());
    }

    @Test
    void insertWithValues() {
        SqlGrammarContext ctx = service.detectContext("INSERT INTO users VALUES ", 25);
        assertEquals(SqlGrammarState.VALUES_CLAUSE, ctx.getState());
    }

    // =============== UPDATE ===============

    @Test
    void updateKeyword() {
        SqlGrammarContext ctx = service.detectContext("UPDATE ", 7);
        assertEquals("UPDATE", ctx.getQueryType());
    }

    @Test
    void updateSet() {
        SqlGrammarContext ctx = service.detectContext("UPDATE users SET ", 17);
        assertEquals(SqlGrammarState.SET_CLAUSE, ctx.getState());
        assertTrue(ctx.isExpectsColumn());
    }

    @Test
    void updateSetWhere() {
        SqlGrammarContext ctx = service.detectContext("UPDATE users SET name = 'test' WHERE ", 37);
        assertEquals(SqlGrammarState.WHERE_CLAUSE, ctx.getState());
    }

    // =============== DELETE ===============

    @Test
    void deleteFrom() {
        SqlGrammarContext ctx = service.detectContext("DELETE FROM ", 12);
        assertEquals("DELETE", ctx.getQueryType());
    }

    @Test
    void deleteFromWhere() {
        SqlGrammarContext ctx = service.detectContext("DELETE FROM users WHERE ", 24);
        assertEquals(SqlGrammarState.WHERE_CLAUSE, ctx.getState());
    }

    // =============== DDL ===============

    @Test
    void createTable() {
        SqlGrammarContext ctx = service.detectContext("CREATE TABLE ", 13);
        assertEquals(SqlGrammarState.TABLE_DEF, ctx.getState());
        assertTrue(ctx.isExpectsTable());
    }

    @Test
    void alterTable() {
        SqlGrammarContext ctx = service.detectContext("ALTER TABLE ", 12);
        assertEquals(SqlGrammarState.TABLE_DEF, ctx.getState());
    }

    @Test
    void dropTable() {
        SqlGrammarContext ctx = service.detectContext("DROP TABLE ", 11);
        assertEquals(SqlGrammarState.TABLE_DEF, ctx.getState());
    }

    // =============== DOT CONTEXT ===============

    @Test
    void dotAfterTableInFromExpectsDatabase() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM db.", 18);
        assertEquals(SqlGrammarState.AFTER_DOT, ctx.getState());
        assertEquals("DB", ctx.getDotPrefix());
        assertEquals("DATABASE", ctx.getDotPrefixType());
        assertTrue(ctx.isExpectsTable());
    }

    @Test
    void dotAfterAliasInWhereExpectsColumn() {
        SqlGrammarContext ctx = service.detectContext("SELECT u.id FROM users u WHERE u.", 35);
        // After u., the dot state is entered, then the dot is processed
        assertEquals(SqlGrammarState.AFTER_DOT, ctx.getState());
        assertEquals("U", ctx.getDotPrefix());
        assertEquals("TABLE", ctx.getDotPrefixType());
        assertTrue(ctx.isExpectsColumn());
    }

    // =============== PARENTHESIS / SUBQUERY ===============

    @Test
    void subqueryInsideFrom() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM (SELECT ", 22);
        assertEquals(SqlGrammarState.SELECT_LIST, ctx.getState());
    }

    @Test
    void subqueryInsideWhere() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM t WHERE id IN (SELECT ", 38);
        assertEquals(SqlGrammarState.SELECT_LIST, ctx.getState());
    }

    // =============== OTHER STATEMENTS ===============

    @Test
    void withKeyword() {
        SqlGrammarContext ctx = service.detectContext("WITH ", 5);
        assertEquals("WITH", ctx.getQueryType());
    }

    @Test
    void explainKeyword() {
        SqlGrammarContext ctx = service.detectContext("EXPLAIN ", 8);
        assertEquals(SqlGrammarState.STATEMENT_START, ctx.getState());
    }

    @Test
    void showKeyword() {
        SqlGrammarContext ctx = service.detectContext("SHOW ", 5);
        assertEquals(SqlGrammarState.EXPRESSION, ctx.getState());
    }

    @Test
    void describeKeyword() {
        SqlGrammarContext ctx = service.detectContext("DESCRIBE ", 9);
        assertEquals(SqlGrammarState.EXPRESSION, ctx.getState());
    }

    @Test
    void useKeyword() {
        SqlGrammarContext ctx = service.detectContext("USE ", 4);
        assertEquals(SqlGrammarState.EXPRESSION, ctx.getState());
    }

    // =============== COMMENTS ===============

    @Test
    void lineCommentDoesNotAffectState() {
        SqlGrammarContext ctx = service.detectContext("SELECT * -- comment\nFROM ", 26);
        assertEquals(SqlGrammarState.FROM_CLAUSE, ctx.getState());
    }

    @Test
    void blockCommentDoesNotAffectState() {
        SqlGrammarContext ctx = service.detectContext("SELECT * /* block */ FROM ", 27);
        assertEquals(SqlGrammarState.FROM_CLAUSE, ctx.getState());
    }

    // =============== SEMICOLON ===============

    @Test
    void semicolonResetsState() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users; ", 21);
        assertEquals(SqlGrammarState.STATEMENT_START, ctx.getState());
        assertEquals("OTHER", ctx.getQueryType());
    }

    // =============== BACKTICK IDENTIFIERS ===============

    @Test
    void backtickIdentifiers() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM `users` ", 22);
        assertEquals(SqlGrammarState.FROM_CLAUSE, ctx.getState());
    }

    @Test
    void backtickDbDotTable() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM `db`.`table` ", 27);
        // After `table`, we're back in FROM_CLAUSE
        assertEquals(SqlGrammarState.FROM_CLAUSE, ctx.getState());
    }

    // =============== UNION ===============

    @Test
    void unionResetsToStatementStart() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users UNION ", 27);
        assertEquals(SqlGrammarState.STATEMENT_START, ctx.getState());
    }

    // =============== CASE EXPRESSION ===============

    @Test
    void caseInSelectList() {
        SqlGrammarContext ctx = service.detectContext("SELECT CASE WHEN ", 17);
        // CASE WHEN keeps us in SELECT_LIST
        assertEquals(SqlGrammarState.SELECT_LIST, ctx.getState());
    }

    // =============== EDGE CASES ===============

    @Test
    void cursorInMiddleOfLine() {
        // Only text before cursor is processed
        SqlGrammarContext ctx = service.detectContext("SELECT *\nFROM users\nWHERE id = 1", 18);
        // Cursor is in the middle: "SELECT *\nFROM "
        assertEquals(SqlGrammarState.FROM_CLAUSE, ctx.getState());
    }

    @Test
    void identifierAfterKeywordDoesNotChangeState() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM users ", 20);
        assertEquals(SqlGrammarState.FROM_CLAUSE, ctx.getState());
    }

    @Test
    void multipleJoins() {
        SqlGrammarContext ctx = service.detectContext("SELECT * FROM a JOIN b ON a.id = b.id JOIN ", 43);
        assertEquals(SqlGrammarState.JOIN_CLAUSE, ctx.getState());
    }
}
