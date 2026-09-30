package org.idubinov.termfind.service;

import org.idubinov.termfind.models.User;
import org.idubinov.termfind.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Апсерт пользователей: повторный апдейт не должен создавать дубликат.
 */
class UserServiceTest {

    @Test
    void firstCallCreates_secondCallReturnsExisting() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        User saved = new User(42L);
        when(repo.findByTelegramId(42L))
                .thenReturn(Optional.empty())          // первый вызов
                .thenReturn(Optional.of(saved));       // второй вызов
        when(repo.save(any(User.class))).thenReturn(saved);

        UserService service = new UserService(repo);
        User first = service.getOrCreate(42L);
        User second = service.getOrCreate(42L);

        assertEquals(saved, first);
        assertSame(saved, second);
        verify(repo, times(1)).save(any(User.class)); // сохранение только один раз
    }

    @Test
    void differentTelegramIdsGetSeparateUsers() {
        UserRepository repo = Mockito.mock(UserRepository.class);
        when(repo.findByTelegramId(anyLong())).thenReturn(Optional.empty());
        when(repo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserService service = new UserService(repo);
        User a = service.getOrCreate(1L);
        User b = service.getOrCreate(2L);

        assertNotEquals(a.getTelegramId(), b.getTelegramId());
        verify(repo, times(2)).save(any(User.class));
    }
}
