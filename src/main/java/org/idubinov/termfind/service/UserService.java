package org.idubinov.termfind.service;

import org.idubinov.termfind.models.User;
import org.idubinov.termfind.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Апдейты приходят из одного polling-потока, гонок нет;
     * уникальный индекс на telegram_id страхует от дублей на будущее.
     */
    @Transactional
    public User getOrCreate(long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseGet(() -> userRepository.save(new User(telegramId)));
    }
}
