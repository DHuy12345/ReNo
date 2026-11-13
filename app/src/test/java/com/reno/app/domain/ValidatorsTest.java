package com.reno.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValidatorsTest {
    @Test
    public void email_isLowercased() {
        Validators.Contact c = Validators.parseContact("  Huy.Nguyen@Example.COM ");
        assertNotNull(c);
        assertEquals("huy.nguyen@example.com", c.email);
        assertNull(c.phone);
        assertNull(Validators.parseContact("huy@"));
        assertNull(Validators.parseContact("@example.com"));
    }

    @Test
    public void phone_vietnamese10Digits() {
        assertEquals("0912345678", Validators.parseContact("0912345678").phone);
        assertEquals("0912345678", Validators.parseContact("+84912345678").phone);
        assertEquals("0912345678", Validators.parseContact("0912 345 678").phone);
        assertNull(Validators.parseContact("912345678"));
        assertNull(Validators.parseContact("09123456789"));
        assertNull(Validators.parseContact("1912345678"));
        assertNull(Validators.parseContact(""));
        assertNull(Validators.parseContact(null));
    }

    @Test
    public void username_3to30_noAt() {
        assertTrue(Validators.isValidUsername("Huy"));
        assertTrue(Validators.isValidUsername("  Huy  "));
        assertFalse(Validators.isValidUsername("ab"));
        assertFalse(Validators.isValidUsername("a@bc"));
        assertFalse(Validators.isValidUsername("x".repeat(31)));
        assertTrue(Validators.isValidUsername("x".repeat(30)));
    }

    @Test
    public void password_6to64() {
        assertFalse(Validators.isValidPassword("12345"));
        assertTrue(Validators.isValidPassword("123456"));
        assertTrue(Validators.isValidPassword("x".repeat(64)));
        assertFalse(Validators.isValidPassword("x".repeat(65)));
    }

    @Test
    public void passwordHasher_roundTrip() throws Exception {
        String stored = PasswordHasher.hash("matkhau123".toCharArray());
        assertTrue(stored.startsWith("pbkdf2$100000$"));
        assertEquals(4, stored.split("\\$").length);
        assertTrue(PasswordHasher.verify("matkhau123".toCharArray(), stored));
        assertFalse(PasswordHasher.verify("matkhau124".toCharArray(), stored));
        // mỗi tài khoản một salt: cùng mật khẩu vẫn ra chuỗi khác nhau
        assertNotEquals(stored, PasswordHasher.hash("matkhau123".toCharArray()));
        assertFalse(PasswordHasher.verify("x".toCharArray(), "khong-dung-dinh-dang"));
    }
}
