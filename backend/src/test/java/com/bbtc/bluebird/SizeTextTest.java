package com.bbtc.bluebird;

import com.bbtc.bluebird.common.util.SizeText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 上传上限文本解析（/admin storage.max-size 与 application.yml 共用）。 */
class SizeTextTest {

    @Test
    void parsesCommonUnits() {
        assertEquals(50L * 1024 * 1024, SizeText.parseBytesOrInvalid("50MB"));
        assertEquals(50L * 1024 * 1024, SizeText.parseBytesOrInvalid(" 50mb "));
        assertEquals(2L * 1024 * 1024 * 1024, SizeText.parseBytesOrInvalid("2GB"));
        assertEquals(512L * 1024, SizeText.parseBytesOrInvalid("512KB"));
        assertEquals(1024L, SizeText.parseBytesOrInvalid("1KB"));
        assertEquals(100L, SizeText.parseBytesOrInvalid("100B"));
        assertEquals(100L, SizeText.parseBytesOrInvalid("100"));
    }

    @Test
    void rejectsInvalidOrNonPositiveValues() {
        assertEquals(SizeText.INVALID, SizeText.parseBytesOrInvalid(null));
        assertEquals(SizeText.INVALID, SizeText.parseBytesOrInvalid(""));
        assertEquals(SizeText.INVALID, SizeText.parseBytesOrInvalid("0MB"));
        assertEquals(SizeText.INVALID, SizeText.parseBytesOrInvalid("-5MB"));
        assertEquals(SizeText.INVALID, SizeText.parseBytesOrInvalid("abc"));
        assertEquals(SizeText.INVALID, SizeText.parseBytesOrInvalid("1.5GB"));
        // 溢出视为非法，而不是回绕成负数
        assertEquals(SizeText.INVALID, SizeText.parseBytesOrInvalid("99999999999GB"));
    }

    @Test
    void fallsBackWhenInvalid() {
        long fallback = 50L * 1024 * 1024;
        assertEquals(fallback, SizeText.parseBytes("oops", fallback));
        assertEquals(fallback, SizeText.parseBytes(null, fallback));
        assertEquals(1024L * 1024, SizeText.parseBytes("1MB", fallback));
    }
}
