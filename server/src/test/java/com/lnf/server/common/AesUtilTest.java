package com.lnf.server.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AesUtil 单元测试：隐藏特征答案的应用层加解密
 */
class AesUtilTest {

    private static final String KEY_16 = "lnf-aes-key-2026"; // 16 字节

    @Test
    @DisplayName("加密后可正确解密还原（含中文）")
    void encryptDecryptRoundTrip() {
        AesUtil aes = new AesUtil(KEY_16);
        String plain = "钱包内有一张校园卡，姓名何伟铭";
        String cipher = aes.encrypt(plain);
        assertEquals(plain, aes.decrypt(cipher));
    }

    @Test
    @DisplayName("密文为 Base64 且不等于明文")
    void cipherTextIsBase64AndDiffers() {
        AesUtil aes = new AesUtil(KEY_16);
        String plain = "serial-no-12345";
        String cipher = aes.encrypt(plain);
        assertNotEquals(plain, cipher);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(cipher));
    }

    @Test
    @DisplayName("同一明文加密结果确定（ECB 模式），可支撑一致性比对")
    void encryptIsDeterministic() {
        AesUtil aes = new AesUtil(KEY_16);
        assertEquals(aes.encrypt("蓝色"), aes.encrypt("蓝色"));
    }

    @Test
    @DisplayName("24 / 32 字节密钥同样可用")
    void keyLength24And32Work() {
        AesUtil aes24 = new AesUtil("lnf-aes-key-202612345678");   // 24 字节
        AesUtil aes32 = new AesUtil("lnf-aes-key-20260123456789ABCDEF"); // 32 字节
        assertEquals("ok", aes24.decrypt(aes24.encrypt("ok")));
        assertEquals("ok", aes32.decrypt(aes32.encrypt("ok")));
    }

    @Test
    @DisplayName("非法密钥长度启动即失败")
    void invalidKeyLengthThrows() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new AesUtil("short"));
        assertTrue(e.getMessage().contains("16/24/32"));
    }

    @Test
    @DisplayName("密文被篡改时解密抛出业务异常而不是泄露明文")
    void tamperedCipherThrowsBizException() {
        AesUtil aes = new AesUtil(KEY_16);
        BizException e = assertThrows(BizException.class, () -> aes.decrypt("!!!!not-base64!!!!"));
        assertEquals(500, e.getCode());
    }
}
