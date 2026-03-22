package org.leoric.expensetracker.auth.services;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.NavbarFavourite;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.repositories.NavbarFavouriteRepository;
import org.leoric.expensetracker.auth.services.interfaces.NavbarFavouriteService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NavbarFavouriteServiceImpl implements NavbarFavouriteService {

    private final NavbarFavouriteRepository navbarFavouriteRepository;

    @Override
    public List<String> getFavourites(User user) {
        return navbarFavouriteRepository.findByUserId(user.getId())
                .stream()
                .map(NavbarFavourite::getItemKey)
                .toList();
    }

    @Override
    public void addFavourite(User user, String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            throw new IllegalArgumentException("Item key cannot be empty");
        }

        itemKey = itemKey.replace("\"", "");

        if (navbarFavouriteRepository.existsByUserIdAndItemKey(user.getId(), itemKey)) {
            return;
        }

        NavbarFavourite favourite = NavbarFavourite.builder()
                .itemKey(itemKey)
                .user(user)
                .build();

        navbarFavouriteRepository.save(favourite);
    }

    @Override
    @Transactional
    public void removeFavourite(User user, String itemKey) {
        navbarFavouriteRepository.deleteByUserIdAndItemKey(user.getId(), itemKey);
    }

    @Override
    public void replaceFavourites(User user, List<String> newFavourites) {
        navbarFavouriteRepository.deleteAll(navbarFavouriteRepository.findByUserId(user.getId()));

        List<NavbarFavourite> favourites = newFavourites.stream()
                .map(item -> NavbarFavourite.builder()
                        .itemKey(item)
                        .user(user)
                        .build())
                .toList();

        navbarFavouriteRepository.saveAll(favourites);
    }
}