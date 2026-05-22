package com.idata.service.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParameterServiceTest {

    @Test
    void testNowKeyword() {
        String result = ParameterService.evalDynamic("now");
        System.out.println("now          → " + result);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void testNowFunction() {
        String result = ParameterService.evalDynamic("now()");
        System.out.println("now()        → " + result);
        assertNotNull(result);
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void testToday() {
        String result = ParameterService.evalDynamic("today");
        System.out.println("today        → " + result);
        assertEquals("2026-05-11", result);
    }

    @Test
    void testTodayFunction() {
        String result = ParameterService.evalDynamic("today()");
        System.out.println("today()      → " + result);
        assertEquals("2026-05-11", result);
    }

    @Test
    void testYesterday() {
        String result = ParameterService.evalDynamic("yesterday");
        System.out.println("yesterday    → " + result);
        assertEquals("2026-05-10", result);
    }

    @Test
    void testDateAddMinus7() {
        String result = ParameterService.evalDynamic("date_add(today, -7)");
        System.out.println("date_add(today, -7) → " + result);
        assertEquals("2026-05-04", result);
    }

    @Test
    void testDateAddPlus30() {
        String result = ParameterService.evalDynamic("date_add(today, 30)");
        System.out.println("date_add(today, 30) → " + result);
        assertEquals("2026-06-10", result);
    }

    @Test
    void testDateFormat() {
        String result = ParameterService.evalDynamic("date_format(today, 'yyyyMM')");
        System.out.println("date_format(today, 'yyyyMM') → " + result);
        assertEquals("202605", result);
    }

    @Test
    void testDateFormatWithCustom() {
        String result = ParameterService.evalDynamic("date_format(today, 'yyyy-MM-dd HH:mm:ss')");
        System.out.println("date_format(today, 'yyyy-MM-dd HH:mm:ss') → " + result);
        assertEquals("2026-05-11 00:00:00", result);
    }

    @Test
    void testNestedDateAddAndFormat() {
        String result = ParameterService.evalDynamic("date_format(date_add(today, -1), 'yyyy-MM-dd')");
        System.out.println("date_format(date_add(today, -1), 'yyyy-MM-dd') → " + result);
        assertEquals("2026-05-10", result);
    }

    @Test
    void testConcat() {
        String result = ParameterService.evalDynamic("concat('ods_', today)");
        System.out.println("concat('ods_', today) → " + result);
        assertEquals("ods_2026-05-11", result);
    }

    @Test
    void testConcatMonth() {
        String result = ParameterService.evalDynamic("concat('month_', date_format(today, 'yyyyMM'))");
        System.out.println("concat('month_', date_format(today, 'yyyyMM')) → " + result);
        assertEquals("month_202605", result);
    }

    @Test
    void testCoalesce() {
        String result = ParameterService.evalDynamic("coalesce('', today)");
        System.out.println("coalesce('', today) → " + result);
        assertEquals("2026-05-11", result);
    }

    @Test
    void testThisMonthStart() {
        String result = ParameterService.evalDynamic("this_month_start");
        System.out.println("this_month_start → " + result);
        assertEquals("2026-05-01", result);
    }

    @Test
    void testThisMonthEnd() {
        String result = ParameterService.evalDynamic("this_month_end");
        System.out.println("this_month_end → " + result);
        assertEquals("2026-05-31", result);
    }

    @Test
    void testLastMonthStart() {
        String result = ParameterService.evalDynamic("last_month_start");
        System.out.println("last_month_start → " + result);
        assertEquals("2026-04-01", result);
    }

    @Test
    void testLastMonthEnd() {
        String result = ParameterService.evalDynamic("last_month_end");
        System.out.println("last_month_end → " + result);
        assertEquals("2026-04-30", result);
    }

    @Test
    void testParseArgs() {
        assertEquals("[today]", ParameterService.parseArgs("today").toString());
        assertEquals("[today, -7]", ParameterService.parseArgs("today, -7").toString());
        assertEquals("[date_add(today, -1), 'yyyy-MM-dd']",
                ParameterService.parseArgs("date_add(today, -1), 'yyyy-MM-dd'").toString());
    }

    @Test
    void testLiteralDateFormat() {
        String result = ParameterService.evalDynamic("yyyy-MM-dd");
        System.out.println("yyyy-MM-dd  → " + result);
        assertEquals("2026-05-11", result);
    }

    @Test
    void testLiteralYearMonth() {
        String result = ParameterService.evalDynamic("yyyyMM");
        System.out.println("yyyyMM      → " + result);
        assertEquals("202605", result);
    }
}
