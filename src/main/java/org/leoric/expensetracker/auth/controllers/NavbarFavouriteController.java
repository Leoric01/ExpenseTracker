package org.leoric.expensetracker.auth.controllers;

import lombok.RequiredArgsConstructor;
import org.leoric.expensetracker.auth.models.User;
import org.leoric.expensetracker.auth.services.NavbarFavouriteServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users/favourites")
@RequiredArgsConstructor
public class NavbarFavouriteController {
    //TODO CREATE DTOS, EXCEPTION FOR EDGE CASES, VALIDATION, BETTER RESPONSES THAN VOID
// THIS IS JUST FAST SOLUTION
    private final NavbarFavouriteServiceImpl navbarFavouriteService;

    @GetMapping
    public ResponseEntity<List<String>> navbarFavouriteGet(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(navbarFavouriteService.getFavourites(currentUser));
    }

    @PostMapping
    public ResponseEntity<Void> navbarFavouriteAdd(@AuthenticationPrincipal User currentUser,
                                                   @RequestBody Map<String, String> body) {
        String itemKey = body.get("itemKey");
        navbarFavouriteService.addFavourite(currentUser, itemKey);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> navbarFavouriteRemove(@AuthenticationPrincipal User currentUser,
                                                      @RequestParam String itemKey) {
        navbarFavouriteService.removeFavourite(currentUser, itemKey);
        return ResponseEntity.noContent().build();
    }

    @PutMapping
    public ResponseEntity<Void> navbarFavouriteReplace(@AuthenticationPrincipal User currentUser,
                                                       @RequestBody List<String> favourites) {
        navbarFavouriteService.replaceFavourites(currentUser, favourites);
        return ResponseEntity.ok().build();
    }
}