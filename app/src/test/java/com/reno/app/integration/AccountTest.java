package com.reno.app.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteConstraintException;

import com.reno.app.R;
import com.reno.app.TestEnv;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.data.repository.UserRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicReference;

/** Đặc tả 3.1 – 3.4 và các tình huống kiểm thử về tài khoản (11.2, TC01–TC05). */
@RunWith(RobolectricTestRunner.class)
public class AccountTest {
    private UserRepository users;

    @Before
    public void setUp() throws Exception {
        TestEnv.reset();
        users = UserRepository.get(TestEnv.context());
    }

    private UserRepository.RegisterResult register(String contact, String username, String pw,
                                                   String confirm) throws Exception {
        AtomicReference<UserRepository.RegisterResult> out = new AtomicReference<>();
        users.register(contact, username, pw, confirm, out::set);
        TestEnv.drain();
        assertNotNull(out.get());
        return out.get();
    }

    private Long login(String username, String pw) throws Exception {
        AtomicReference<Long> out = new AtomicReference<>(-99L);
        users.login(username, pw, out::set);
        TestEnv.drain();
        return out.get();
    }

    @Test
    public void register_thenLogin_caseInsensitiveUsername() throws Exception {
        UserRepository.RegisterResult r = register("Huy@Example.com", "  Huy ", "matkhau1",
                "matkhau1");
        assertTrue(r.isOk());
        assertEquals("Huy", r.username);
        UserEntity u = RenoDatabase.get(TestEnv.context()).userDao().findByUsername("huy");
        assertNotNull(u);
        assertEquals("huy@example.com", u.email);
        assertNull(u.phone);
        assertNull(u.avatarPath);
        assertTrue(u.passwordHash.startsWith("pbkdf2$100000$"));
        assertTrue(u.reviewTime.matches("\\d\\d:\\d\\d"));

        assertEquals(Long.valueOf(u.id), login("HUY", "matkhau1"));
        assertNull(login("huy", "sai-mat-khau"));
        assertNull(login("khongco", "matkhau1"));
    }

    @Test
    public void duplicates_areBlocked() throws Exception {
        assertTrue(register("0912345678", "huy", "matkhau1", "matkhau1").isOk());
        // Username chỉ khác hoa thường
        UserRepository.RegisterResult dupName = register("a@b.com", "Huy", "matkhau1",
                "matkhau1");
        assertEquals(R.string.error_username_taken, dupName.usernameError);
        // Số điện thoại đã dùng, viết dạng +84
        UserRepository.RegisterResult dupPhone = register("+84912345678", "khac", "matkhau1",
                "matkhau1");
        assertEquals(R.string.error_contact_taken, dupPhone.contactError);
    }

    @Test
    public void vietnameseUsername_duplicateIgnoringCase() throws Exception {
        assertTrue(register("x@y.com", "Đức Anh", "matkhau1", "matkhau1").isOk());
        assertEquals(R.string.error_username_taken,
                register("z@y.com", "đỨC aNH", "matkhau1", "matkhau1").usernameError);
        assertNotNull(login("đức anh", "matkhau1"));
    }

    @Test
    public void validation_errorsPerField() throws Exception {
        UserRepository.RegisterResult r = register("abc", "ab", "123", "124");
        assertEquals(R.string.error_contact_invalid, r.contactError);
        assertEquals(R.string.error_username_length, r.usernameError);
        assertEquals(R.string.error_password_short, r.passwordError);
        assertEquals(R.string.error_password_mismatch, r.confirmError);
    }

    @Test(expected = SQLiteConstraintException.class)
    public void checkConstraint_needsEmailOrPhone() {
        UserEntity u = new UserEntity();
        u.username = "x";
        u.passwordHash = "h";
        RenoDatabase.get(TestEnv.context()).userDao().insert(u);
    }

    @Test
    public void profile_cannotClearBothContacts_andChecksDuplicates() throws Exception {
        register("a@b.com", "motte", "matkhau1", "matkhau1");
        register("c@d.com", "haite", "matkhau1", "matkhau1");
        long id = RenoDatabase.get(TestEnv.context()).userDao().findByUsername("motte").id;

        AtomicReference<UserRepository.ProfileResult> out = new AtomicReference<>();
        users.updateProfile(id, "motte", "", "", "07:30", null, false, out::set);
        TestEnv.drain();
        assertEquals(R.string.error_contact_both_empty, out.get().emailError);

        users.updateProfile(id, "HAITE", "a@b.com", "", "07:30", null, false, out::set);
        TestEnv.drain();
        assertEquals(R.string.error_username_taken, out.get().usernameError);

        users.updateProfile(id, "mot", "a@b.com", "0987654321", "07:30", null, false, out::set);
        TestEnv.drain();
        assertTrue(out.get().isOk());
        UserEntity u = RenoDatabase.get(TestEnv.context()).userDao().findById(id);
        assertEquals("mot", u.username);
        assertEquals("0987654321", u.phone);
        assertEquals("07:30", u.reviewTime);
    }
}
