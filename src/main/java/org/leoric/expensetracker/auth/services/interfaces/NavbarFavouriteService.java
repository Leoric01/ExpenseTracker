package org.leoric.expensetracker.auth.services.interfaces;

import org.leoric.expensetracker.auth.models.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface NavbarFavouriteService {

    List<String> getFavourites(User currentUser);

    void addFavourite(User currentUser, String itemKey);

    void removeFavourite(User currentUser, String itemKey);

    void replaceFavourites(User currentUser, List<String> favourites);
}