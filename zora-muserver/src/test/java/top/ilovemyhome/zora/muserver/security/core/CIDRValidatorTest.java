package top.ilovemyhome.zora.muserver.security.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CIDRValidatorTest {

    @Test
    public void testConstructorWithValidCIDR() {
        assertDoesNotThrow(() -> new CIDRValidator("192.168.1.0/24"));
        assertDoesNotThrow(() -> new CIDRValidator("10.0.0.0/8"));
        assertDoesNotThrow(() -> new CIDRValidator("2001:db8::/32"));
        assertDoesNotThrow(() -> new CIDRValidator("192.168.1.1")); // 无掩码单IP
    }

    @Test
    public void testConstructorWithInvalidCIDR() {
        assertThrows(IllegalArgumentException.class, () -> new CIDRValidator("256.1.1.1/24"));
        assertThrows(IllegalArgumentException.class, () -> new CIDRValidator("invalid-ip/24"));
        assertThrows(NumberFormatException.class, () -> new CIDRValidator("192.168.1.1/abc"));
    }

    @Test
    public void testIPv4CIDRValidation() {
        // 测试 IPv4 CIDR 验证功能
        CIDRValidator validator = new CIDRValidator("192.168.1.0/24");
        // 同一子网内的地址应该通过验证
        assertTrue(validator.isValid("192.168.1.1"));
        assertTrue(validator.isValid("192.168.1.254"));
        assertTrue(validator.isValid("192.168.1.0"));

        // 不同子网的地址应该验证失败
        assertFalse(validator.isValid("192.168.2.1"));
        assertFalse(validator.isValid("10.0.0.1"));
    }

    @Test
    public void testIPv4DifferentMasks() {
        // 测试不同掩码长度的 IPv4 CIDR
        CIDRValidator validator8 = new CIDRValidator("10.0.0.0/8");
        assertTrue(validator8.isValid("10.0.0.1"));
        assertTrue(validator8.isValid("10.255.255.255"));
        assertFalse(validator8.isValid("11.0.0.1"));

        CIDRValidator validator32 = new CIDRValidator("192.168.1.100/32");
        assertTrue(validator32.isValid("192.168.1.100"));
        assertFalse(validator32.isValid("192.168.1.101"));
    }

    @Test
    public void testIPv6CIDRValidation() {
        // 测试 IPv6 CIDR 验证功能
        CIDRValidator validator = new CIDRValidator("2001:db8::/32");

        assertTrue(validator.isValid("2001:db8::1"));
        assertTrue(validator.isValid("2001:db8:0:0::1"));
        assertFalse(validator.isValid("2001:dc8::1"));
    }

    @Test
    public void testIPv6FullAddress() {
        CIDRValidator validator = new CIDRValidator("2001:db8::1/128");
        assertTrue(validator.isValid("2001:db8::1"));
        assertFalse(validator.isValid("2001:db8::2"));
    }

    @Test
    public void testInvalidIPAddresses() {
        CIDRValidator validator = new CIDRValidator("192.168.1.0/24");

        assertFalse(validator.isValid("invalid-ip"));
        assertFalse(validator.isValid(""));
        assertFalse(validator.isValid(null));
    }

    @Test
    public void testCrossProtocolValidation() {
        // 测试跨协议验证（IPv4 CIDR 验证 IPv6 地址等）
        CIDRValidator ipv4Validator = new CIDRValidator("192.168.1.0/24");
        CIDRValidator ipv6Validator = new CIDRValidator("2001:db8::/32");

        assertFalse(ipv4Validator.isValid("2001:db8::1"));
        assertFalse(ipv6Validator.isValid("192.168.1.1"));
    }

    @Test
    public void testSpecialMaskValues() {
        // 测试特殊掩码值
        CIDRValidator validator0 = new CIDRValidator("0.0.0.0/0");
        assertTrue(validator0.isValid("192.168.1.1"));  // /0 应该匹配所有 IPv4

        CIDRValidator validator128 = new CIDRValidator("2001:db8::/128");
        assertTrue(validator128.isValid("2001:db8::"));
        assertFalse(validator128.isValid("2001:db8::1"));
    }

    @Test
    public void testSingleIPWithoutMask() {
        // 测试没有掩码的单个 IP
        CIDRValidator validator = new CIDRValidator("192.168.1.1");
        assertTrue(validator.isValid("192.168.1.1"));
        assertFalse(validator.isValid("192.168.1.2"));
    }
}
